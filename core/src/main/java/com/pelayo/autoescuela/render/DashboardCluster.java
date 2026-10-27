package com.pelayo.autoescuela.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;

/**
 * Cuadro de instrumentos del salpicadero en vivo: sustituye la textura del
 * material "Compteur" del Clio por una que se dibuja cada frame (en un
 * framebuffer) con el cuentarrevoluciones a la izquierda, el velocímetro a la
 * derecha, una pantalla central con la marcha y los testigos (intermitentes,
 * freno de mano, motor) y los relojes de combustible y temperatura. Las UV
 * del modelo cubren la textura entera con proporción 4:1.
 */
public class DashboardCluster implements Disposable {

    private static final int W = 1024;
    private static final int H = 256;
    private static final float TACHO_X = 362f, SPEEDO_X = 662f, DIAL_Y = 128f, DIAL_R = 112f;
    private static final Color FACE = new Color(0.05f, 0.055f, 0.065f, 1f);
    private static final Color MARK = new Color(0.92f, 0.94f, 0.96f, 1f);
    private static final Color AMBER = new Color(1f, 0.62f, 0.15f, 1f);
    private static final Color NEEDLE = new Color(1f, 0.35f, 0.12f, 1f);

    private final Material material;
    private final FrameBuffer fbo;
    private final SpriteBatch batch;
    private final OrthographicCamera camera = new OrthographicCamera();
    private final TextureRegion white;
    private final TextureRegion circle;
    private final TextureRegion ring;
    private final TextureRegion needle;
    private final TextureRegion arrow;
    private final BitmapFont font;
    private final BitmapFont fontBig;
    private final GlyphLayout layout = new GlyphLayout();
    private final float redline;
    private float blink;
    private float temp;

    /** @return el cuadro, o null si el coche no tiene el material "Compteur" (placeholder). */
    public static DashboardCluster attach(CarView car, Skin skin, float redlineRpm) {
        Material m = car.chassis.getMaterial("Compteur");
        return m == null ? null : new DashboardCluster(m, skin, redlineRpm);
    }

    private DashboardCluster(Material material, Skin skin, float redlineRpm) {
        this.material = material;
        this.redline = redlineRpm;
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, W, H, false);
        Texture tex = fbo.getColorBufferTexture();
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        // Las UV del modelo van de -1 a 0 en V: hace falta repetir
        tex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        batch = new SpriteBatch(400);
        // Y hacia abajo (el framebuffer se lee al revés que una imagen) y X en espejo (las UV del
        // modelo recorren la esfera de derecha a izquierda)
        camera.combined.setToOrtho(W, 0f, H, 0f, -1f, 1f);
        white = skin.getRegion("white");
        circle = skin.getRegion("circle");
        ring = skin.getRegion("ring-bold");
        needle = skin.getRegion("needle");
        arrow = skin.getRegion("arrow");
        font = skin.getFont("font-bold");
        fontBig = skin.getFont("font-huge");
        material.set(PBRTextureAttribute.createBaseColorTexture(tex));
        material.set(PBRColorAttribute.createBaseColorFactor(new Color(0.25f, 0.25f, 0.25f, 1f)));
        material.set(PBRTextureAttribute.createEmissiveTexture(tex));
        material.set(PBRColorAttribute.createEmissive(Color.WHITE));
        material.set(PBRFloatAttribute.createEmissiveIntensity(1.1f));
        material.set(PBRFloatAttribute.createRoughness(0.35f));
    }

    /** Redibuja el cuadro con el estado del coche. */
    public void update(VehicleState s, float delta) {
        blink += delta;
        // Temperatura: sube hasta la de servicio en un par de minutos con el motor en marcha
        temp = MathUtils.clamp(temp + delta * (s.engineRunning ? 0.008f : -0.002f), 0.25f, 0.5f);

        fbo.begin();
        Gdx.gl.glClearColor(0.02f, 0.02f, 0.025f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        // Esferas
        dial(TACHO_X);
        dial(SPEEDO_X);
        // Cuentarrevoluciones: 0 a 6 (x1000), zona roja desde el corte
        for (int i = 0; i <= 12; i++) {
            float v = i * 500f;
            float a = angle(v / 6000f);
            boolean major = i % 2 == 0;
            batch.setColor(v >= redline ? new Color(1f, 0.2f, 0.15f, 1f) : MARK);
            radial(TACHO_X, a, major ? 20f : 10f, major ? 5f : 3f);
            if (major) {
                text(font, String.valueOf(i / 2), TACHO_X + MathUtils.cosDeg(a) * (DIAL_R - 42f),
                    DIAL_Y - MathUtils.sinDeg(a) * (DIAL_R - 42f), 1.25f, v >= redline ? AMBER : MARK);
            }
        }
        // Velocímetro: 0 a 220 km/h
        for (int v = 0; v <= 220; v += 10) {
            float a = angle(v / 220f);
            boolean major = v % 20 == 0;
            batch.setColor(MARK);
            radial(SPEEDO_X, a, major ? 18f : 9f, major ? 4f : 2.5f);
            if (major && v % 40 == 0) {
                text(font, String.valueOf(v), SPEEDO_X + MathUtils.cosDeg(a) * (DIAL_R - 44f),
                    DIAL_Y - MathUtils.sinDeg(a) * (DIAL_R - 44f), 1.0f, MARK);
            }
        }
        text(font, "x1000", TACHO_X, DIAL_Y + 84f, 0.7f, new Color(0.7f, 0.72f, 0.75f, 1f));
        text(font, "km/h", SPEEDO_X, DIAL_Y + 84f, 0.7f, new Color(0.7f, 0.72f, 0.75f, 1f));
        // Agujas
        needle(TACHO_X, angle(MathUtils.clamp(s.rpm, 0f, 6000f) / 6000f));
        needle(SPEEDO_X, angle(MathUtils.clamp(s.speedKmh, 0f, 220f) / 220f));

        // Pantalla central: marcha y testigos
        float cx = (TACHO_X + SPEEDO_X) / 2f;
        batch.setColor(0.08f, 0.12f, 0.14f, 1f);
        batch.draw(white, cx - 36f, DIAL_Y - 70f, 72f, 140f);
        String gear = s.gear == -1 ? "R" : s.gear == 0 ? "N" : String.valueOf(s.gear);
        text(fontBig, gear, cx, DIAL_Y - 14f, 0.62f, s.gear == -1 ? AMBER : new Color(0.75f, 0.95f, 1f, 1f));
        text(font, String.valueOf((int) (s.speedKmh + 0.5f)), cx, DIAL_Y + 48f, 1.0f,
            new Color(0.75f, 0.95f, 1f, 1f));
        boolean on = ((int) (blink * 2.5f)) % 2 == 0;
        batch.setColor(s.indicatorLeft && on ? new Color(0.25f, 1f, 0.35f, 1f) : new Color(0.12f, 0.16f, 0.13f, 1f));
        batch.draw(arrow, cx - 34f - 34f, 14f, 30f, 30f);
        batch.setColor(s.indicatorRight && on ? new Color(0.25f, 1f, 0.35f, 1f) : new Color(0.12f, 0.16f, 0.13f, 1f));
        batch.draw(arrow, cx + 38f, 14f, 15f, 15f, 30f, 30f, 1f, 1f, 180f);
        if (s.handbrake) {
            batch.setColor(1f, 0.15f, 0.1f, 1f);
            batch.draw(ring, cx - 92f - 16f, H - 52f, 34f, 34f);
            text(font, "P", cx - 92f + 1f, H - 35f, 0.9f, new Color(1f, 0.15f, 0.1f, 1f));
        }
        if (!s.engineRunning) {
            // Testigo de batería/motor: rectángulo ámbar
            batch.setColor(AMBER);
            batch.draw(white, cx + 74f, H - 48f, 28f, 18f);
        }

        // Combustible (izquierda) y temperatura (derecha)
        smallGauge(120f, 0.72f, false);
        smallGauge(W - 120f, temp, true);

        boolean powered = s.engineRunning || s.engineStarting;
        if (!powered) {
            // Motor apagado: sin retroiluminación; solo los testigos de batería y aceite (contacto dado)
            batch.setColor(0f, 0f, 0f, 0.86f);
            batch.draw(white, 0f, 0f, W, H);
            float cx2 = (TACHO_X + SPEEDO_X) / 2f;
            batch.setColor(1f, 0.12f, 0.08f, 1f);
            batch.draw(white, cx2 - 28f, DIAL_Y - 30f, 56f, 30f);   // batería
            batch.setColor(0f, 0f, 0f, 1f);
            batch.draw(white, cx2 - 18f, DIAL_Y - 36f, 8f, 6f);
            batch.draw(white, cx2 + 10f, DIAL_Y - 36f, 8f, 6f);
            batch.setColor(1f, 0.12f, 0.08f, 1f);
            batch.draw(circle, cx2 - 18f, DIAL_Y + 18f, 36f, 26f); // aceite
            batch.draw(white, cx2 + 14f, DIAL_Y + 26f, 16f, 6f);
        }
        material.set(PBRFloatAttribute.createEmissiveIntensity(powered ? 1.1f : 0.6f));
        batch.end();
        batch.setColor(Color.WHITE);
        fbo.end();
    }

    private void dial(float x) {
        batch.setColor(0.35f, 0.36f, 0.38f, 1f);
        batch.draw(circle, x - DIAL_R - 6f, DIAL_Y - DIAL_R - 6f, (DIAL_R + 6f) * 2f, (DIAL_R + 6f) * 2f);
        batch.setColor(FACE);
        batch.draw(circle, x - DIAL_R, DIAL_Y - DIAL_R, DIAL_R * 2f, DIAL_R * 2f);
    }

    /** Ángulo (grados, 0 = derecha, antihorario en pantalla) para una fracción del recorrido de la aguja. */
    private static float angle(float fraction) {
        return 225f - 270f * MathUtils.clamp(fraction, 0f, 1f);
    }

    /** Marca radial: con Y hacia abajo, el ángulo se invierte para que el giro siga siendo horario. */
    private void radial(float x, float deg, float len, float t) {
        float r0 = DIAL_R - 6f - len;
        float px = x + MathUtils.cosDeg(deg) * r0;
        float py = DIAL_Y - MathUtils.sinDeg(deg) * r0;
        batch.draw(white, px, py - t / 2f, 0f, t / 2f, len, t, 1f, 1f, -deg);
    }

    private void needle(float x, float deg) {
        float len = DIAL_R - 14f;
        batch.setColor(NEEDLE);
        batch.draw(needle, x, DIAL_Y - 9f, 0f, 9f, len, 18f, 1f, 1f, -deg);
        batch.setColor(0.25f, 0.25f, 0.28f, 1f);
        batch.draw(circle, x - 14f, DIAL_Y - 14f, 28f, 28f);
    }

    private void smallGauge(float x, float fraction, boolean hot) {
        float r = 72f;
        float cy = DIAL_Y + 30f;
        batch.setColor(FACE);
        batch.draw(circle, x - r, cy - r, r * 2f, r * 2f);
        for (int i = 0; i <= 4; i++) {
            float a = 150f - 120f * i / 4f;
            batch.setColor(hot && i == 4 ? new Color(1f, 0.2f, 0.15f, 1f) : MARK);
            float px = x + MathUtils.cosDeg(a) * (r - 18f);
            float py = cy - MathUtils.sinDeg(a) * (r - 18f);
            batch.draw(white, px, py - 2f, 0f, 2f, 12f, 4f, 1f, 1f, -a);
        }
        float a = 150f - 120f * fraction;
        batch.setColor(NEEDLE);
        batch.draw(needle, x, cy - 6f, 0f, 6f, r - 20f, 12f, 1f, 1f, -a);
        text(font, hot ? "°C" : "E   F", x, cy + 30f, 0.75f, new Color(0.7f, 0.72f, 0.75f, 1f));
    }

    /** Texto centrado en (x, y) con la fuente a ese tamaño relativo. */
    private void text(BitmapFont f, String s, float x, float y, float scale, Color color) {
        float sx = f.getData().scaleX;
        float sy = f.getData().scaleY;
        // Tamaño en píxeles del framebuffer, independiente de la densidad con que se generó la fuente
        f.getData().setScale(sx * scale * 1.6f, -sy * scale * 1.6f); // glifos al derecho con la Y hacia abajo
        layout.setText(f, s, color, 0f, Align.center, false);
        f.draw(batch, layout, x, y - layout.height / 2f);
        f.getData().setScale(sx, sy);
    }

    /** Guarda la textura del cuadro en cache/cluster.png (capturas de desarrollo). */
    public void dump() {
        fbo.begin();
        Pixmap p = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(0, 0, W, H);
        fbo.end();
        com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.local("cache/cluster.png"), p);
        p.dispose();
    }

    @Override
    public void dispose() {
        fbo.dispose();
        batch.dispose();
    }
}
