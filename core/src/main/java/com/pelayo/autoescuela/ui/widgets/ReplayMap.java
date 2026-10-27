package com.pelayo.autoescuela.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.FloatArray;
import com.pelayo.autoescuela.data.ScenarioDef;
import com.pelayo.autoescuela.sim.eval.EvaluationResult;
import com.pelayo.autoescuela.sim.eval.Fault;
import com.pelayo.autoescuela.sim.eval.FaultSeverity;
import com.pelayo.autoescuela.sim.world.ScenarioGeometry;
import com.pelayo.autoescuela.sim.world.Zone;

/**
 * Repetición de la maniobra vista desde arriba (norte arriba): el escenario,
 * el recorrido hecho, las faltas numeradas donde ocurrieron y el coche
 * moviéndose por el recorrido. Se reproduce, se pausa y se salta a un
 * instante (p.ej. al tocar una falta en la lista).
 */
public class ReplayMap extends Actor {

    private static final Color PATH = new Color(0.35f, 0.7f, 1f, 0.85f);
    private static final Color CAR = new Color(0.3f, 0.65f, 1f, 1f);
    private static final Color ZONE = new Color(0.95f, 0.69f, 0.23f, 0.35f);
    private static final float SPEED = 1.5f;

    private final MapPainter painter;
    private final EvaluationResult result;
    private final FloatArray trace;
    private final TextureRegion white;
    private final TextureRegion circle;
    private final Drawable frame;
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private final Rectangle scissor = new Rectangle();
    private final Rectangle area = new Rectangle();
    private final float centerX, centerZ, spanX, spanZ;
    private final float duration;
    private float time;
    private boolean playing;
    /** Se llama cuando cambia el instante (para mover la barra de tiempo). */
    public Runnable onTime;

    public ReplayMap(Skin skin, ScenarioDef def, EvaluationResult result, float width, float height) {
        this.painter = new MapPainter(skin, def, new ScenarioGeometry(def));
        this.result = result;
        this.trace = result.trace;
        this.white = skin.getRegion("white");
        this.circle = skin.getRegion("circle");
        this.frame = skin.getDrawable("hud-box");
        this.font = skin.getFont("font-small");
        setSize(width, height);
        // Encuadre: el recorrido con margen, y nunca menos de 30 m
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < trace.size; i += EvaluationResult.TRACE_STRIDE) {
            minX = Math.min(minX, trace.get(i + 1));
            maxX = Math.max(maxX, trace.get(i + 1));
            minZ = Math.min(minZ, trace.get(i + 2));
            maxZ = Math.max(maxZ, trace.get(i + 2));
        }
        if (trace.size == 0) {
            minX = def.startPose.x;
            maxX = def.startPose.x;
            minZ = def.startPose.z;
            maxZ = def.startPose.z;
        }
        centerX = (minX + maxX) / 2f;
        centerZ = (minZ + maxZ) / 2f;
        spanX = Math.max(30f, maxX - minX + 12f);
        spanZ = Math.max(30f, maxZ - minZ + 12f);
        duration = trace.size == 0 ? 0f : trace.get(trace.size - EvaluationResult.TRACE_STRIDE);
    }

    public float getDuration() {
        return duration;
    }

    public float getTime() {
        return time;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean play) {
        if (play && time >= duration) {
            time = 0f; // al final, vuelve a empezar
        }
        playing = play;
    }

    /** Salta a ese instante (un poco antes, para ver qué llevó a la falta) y se para. */
    public void seek(float t) {
        time = MathUtils.clamp(t, 0f, duration);
        playing = false;
        if (onTime != null) {
            onTime.run();
        }
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (playing) {
            time += delta * SPEED;
            if (time >= duration) {
                time = duration;
                playing = false;
            }
            if (onTime != null) {
                onTime.run();
            }
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX(), y = getY(), w = getWidth(), h = getHeight();
        batch.setColor(Color.WHITE);
        frame.draw(batch, x - 6f, y - 6f, w + 12f, h + 12f);
        batch.flush();
        area.set(x, y, w, h);
        getStage().calculateScissors(area, scissor);
        if (!ScissorStack.pushScissors(scissor)) {
            return;
        }
        float scale = Math.min(w / spanX, h / spanZ);
        painter.setView(x, y, w, h, centerX, centerZ, 0f, scale);
        painter.drawScenery(batch);
        painter.drawConesFromDef(batch);
        batch.setColor(ZONE);
        for (Zone z : painter.geometry.zones.values()) {
            painter.rect(batch, z.cx, z.cz, z.halfX * 2f, z.halfZ * 2f, z.yawDeg);
        }

        // Recorrido hecho hasta el instante actual (lo que falta, tenue)
        int s = EvaluationResult.TRACE_STRIDE;
        for (int i = s; i < trace.size; i += s) {
            boolean done = trace.get(i) <= time;
            batch.setColor(PATH.r, PATH.g, PATH.b, done ? PATH.a : 0.25f);
            painter.segment(batch, trace.get(i - s + 1), trace.get(i - s + 2), trace.get(i + 1), trace.get(i + 2),
                0.35f);
        }

        // Faltas numeradas donde ocurrieron
        int n = 0;
        for (Fault f : result.faults) {
            n++;
            if (Float.isNaN(f.x)) {
                continue;
            }
            float px = painter.sx(f.x, f.z), py = painter.sy(f.x, f.z);
            float r = 11f;
            batch.setColor(0f, 0f, 0f, 0.6f);
            batch.draw(circle, px - r - 1f, py - r - 2f, (r + 1f) * 2f, (r + 1f) * 2f);
            batch.setColor(color(f.severity, f.time <= time ? 1f : 0.45f));
            batch.draw(circle, px - r, py - r, r * 2f, r * 2f);
            layout.setText(font, String.valueOf(n), Color.WHITE, 0f, Align.left, false);
            font.draw(batch, layout, px - layout.width / 2f, py + layout.height / 2f);
        }

        // El coche en el instante actual
        if (trace.size >= s) {
            int i = 0;
            while (i + s < trace.size && trace.get(i + s) <= time) {
                i += s;
            }
            float carX = trace.get(i + 1), carZ = trace.get(i + 2), yaw = trace.get(i + 3);
            if (i + s < trace.size) {
                float t0 = trace.get(i), t1 = trace.get(i + s);
                float k = t1 > t0 ? MathUtils.clamp((time - t0) / (t1 - t0), 0f, 1f) : 0f;
                carX = MathUtils.lerp(carX, trace.get(i + s + 1), k);
                carZ = MathUtils.lerp(carZ, trace.get(i + s + 2), k);
                yaw = yaw + MathUtils.clamp(((trace.get(i + s + 3) - yaw + 540f) % 360f) - 180f, -180f, 180f) * k;
            }
            batch.setColor(0f, 0f, 0f, 0.5f);
            painter.rect(batch, carX, carZ, 2.0f, 4.3f, yaw);
            batch.setColor(CAR);
            painter.rect(batch, carX, carZ, 1.7f, 4.0f, yaw);
            batch.setColor(Color.WHITE);
            float fx = MathUtils.sinDeg(yaw) * 1.4f, fz = MathUtils.cosDeg(yaw) * 1.4f;
            painter.disc(batch, carX + fx, carZ + fz, 0.35f);
            // Velocidad en ese momento
            float speed = trace.get(i + 4);
            layout.setText(font, String.format("%d km/h", Math.round(speed)), Color.WHITE, 0f, Align.left, false);
            batch.setColor(0f, 0f, 0f, 0.55f);
            batch.draw(white, x + 6f, y + 6f, layout.width + 12f, layout.height + 12f);
            font.draw(batch, layout, x + 12f, y + 12f + layout.height);
        }
        batch.flush();
        ScissorStack.popScissors();
        batch.setColor(Color.WHITE);
    }

    public static Color color(FaultSeverity severity, float alpha) {
        switch (severity) {
            case ELIMINATORIA: return new Color(0.85f, 0.2f, 0.18f, alpha);
            case DEFICIENTE: return new Color(0.9f, 0.5f, 0.12f, alpha);
            default: return new Color(0.9f, 0.75f, 0.2f, alpha);
        }
    }
}
