package com.pelayo.autoescuela.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.pelayo.autoescuela.data.ScenarioDef;
import com.pelayo.autoescuela.sim.world.Marking;
import com.pelayo.autoescuela.sim.world.ScenarioGeometry;
import com.pelayo.autoescuela.sim.world.Zone;

/**
 * Dibuja un escenario visto desde arriba (calzadas, aceras, marcas, coches
 * aparcados y bloques) en un rectángulo de pantalla, con el centro, el rumbo
 * que queda hacia arriba y la escala que se le den. Lo usan el minimapa (gira
 * con el coche) y la repetición de los resultados (norte arriba). Solo
 * necesita la definición del escenario, no el mundo físico.
 */
public class MapPainter {

    public static final Color ROAD = new Color(0.30f, 0.32f, 0.36f, 1f);
    public static final Color SIDEWALK = new Color(0.62f, 0.62f, 0.60f, 1f);
    public static final Color GRASS = new Color(0.20f, 0.30f, 0.18f, 0.9f);
    public static final Color LINE = new Color(0.95f, 0.95f, 0.92f, 0.9f);
    public static final Color PARKED = new Color(0.55f, 0.62f, 0.75f, 1f);
    public static final Color CONE = new Color(1f, 0.5f, 0.1f, 1f);

    public final ScenarioDef def;
    public final ScenarioGeometry geometry;
    private final TextureRegion white;
    private final TextureRegion circle;

    private float ox, oy, w, h;
    /** Píxeles por metro. */
    private float scale = 3.4f;
    // Base de la vista: "arriba" (f) e "izquierda" (l) en el plano XZ, y el punto del centro
    private float fx, fz = 1f, lx = 1f, lz, cx, cz;

    public MapPainter(Skin skin, ScenarioDef def, ScenarioGeometry geometry) {
        this.def = def;
        this.geometry = geometry;
        this.white = skin.getRegion("white");
        this.circle = skin.getRegion("circle");
    }

    /** Rectángulo de pantalla, punto del mundo en su centro, rumbo que queda arriba y escala (px/m). */
    public void setView(float x, float y, float width, float height, float centerX, float centerZ, float upYawDeg,
                        float pixelsPerMeter) {
        ox = x;
        oy = y;
        w = width;
        h = height;
        cx = centerX;
        cz = centerZ;
        fx = MathUtils.sinDeg(upYawDeg);
        fz = MathUtils.cosDeg(upYawDeg);
        lx = fz;
        lz = -fx;
        scale = pixelsPerMeter;
    }

    public float getScale() {
        return scale;
    }

    /** Fondo de hierba, calzadas, aceras, marcas, coches aparcados y bloques. */
    public void drawScenery(Batch batch) {
        batch.setColor(GRASS);
        batch.draw(white, ox, oy, w, h);
        // Calzadas primero, aceras encima, luego marcas y objetos
        for (int pass = 0; pass < 2; pass++) {
            for (ScenarioDef.ModuleDef m : def.modules) {
                boolean road = m.type.equals("road") || m.type.equals("ground") || m.type.equals("slope")
                    || m.type.equals("road_arc");
                boolean side = m.type.equals("sidewalk") || m.type.equals("sidewalk_arc") || m.type.equals("island");
                if ((pass == 0 && !road) || (pass == 1 && !side)) {
                    continue;
                }
                batch.setColor(road ? ROAD : m.type.equals("island") ? GRASS : SIDEWALK);
                if (m.type.endsWith("_arc") && m.arc != null) {
                    float[][] p = m.arc.points();
                    for (int i = 1; i < p.length; i++) {
                        segment(batch, p[i - 1][0], p[i - 1][1], p[i][0], p[i][1], m.width);
                    }
                } else if (m.type.equals("island")) {
                    disc(batch, m.x, m.z, m.radius);
                } else if (m.type.equals("slope")) {
                    float s = MathUtils.sinDeg(m.yawDeg), c = MathUtils.cosDeg(m.yawDeg);
                    rect(batch, m.x + s * m.length / 2f, m.z + c * m.length / 2f, m.width, m.length, m.yawDeg);
                } else {
                    rect(batch, m.x, m.z, m.width, m.length, m.yawDeg);
                }
            }
        }
        batch.setColor(LINE);
        for (Marking mk : geometry.markings) {
            if (mk.kind == Marking.Kind.GUIDE) {
                continue;
            }
            if (mk.kind == Marking.Kind.BAY && mk.bay != null) {
                outline(batch, mk.bay, 0.2f);
                continue;
            }
            float lw = Math.max(mk.width, 0.25f);
            for (int i = 1; i < mk.points.length; i++) {
                segment(batch, mk.points[i - 1][0], mk.points[i - 1][1], mk.points[i][0], mk.points[i][1], lw);
            }
        }
        batch.setColor(PARKED);
        for (ScenarioDef.ModuleDef m : def.modules) {
            if (m.type.equals("parked_car")) {
                rect(batch, m.x, m.z, 1.75f, 4.0f, m.yawDeg);
            } else if (m.type.equals("block") || m.type.equals("wall")) {
                rect(batch, m.x, m.z, m.type.equals("wall") ? 0.3f : m.width, m.length, m.yawDeg);
            }
        }
    }

    /** Conos en su sitio de salida (sin el mundo físico). */
    public void drawConesFromDef(Batch batch) {
        batch.setColor(CONE);
        for (ScenarioDef.ModuleDef m : def.modules) {
            if (m.type.equals("cone")) {
                disc(batch, m.x, m.z, 0.45f);
            }
        }
    }

    /** Punto del mundo (x, z) a coordenadas de pantalla. */
    public float sx(float x, float z) {
        float dx = x - cx, dz = z - cz;
        return ox + w / 2f - (dx * lx + dz * lz) * scale;
    }

    public float sy(float x, float z) {
        float dx = x - cx, dz = z - cz;
        return oy + h / 2f + (dx * fx + dz * fz) * scale;
    }

    /** Rectángulo de ancho w (X local) y largo l (Z local) con ese rumbo. */
    public void rect(Batch batch, float x, float z, float width, float length, float yawDeg) {
        // Dirección del eje Z local del módulo en pantalla
        float ax = MathUtils.sinDeg(yawDeg), az = MathUtils.cosDeg(yawDeg);
        float dirX = -(ax * lx + az * lz);
        float dirY = ax * fx + az * fz;
        float rot = MathUtils.atan2(dirY, dirX) * MathUtils.radiansToDegrees;
        float len = length * scale;
        float wid = width * scale;
        float px = sx(x, z), py = sy(x, z);
        batch.draw(white, px - len / 2f, py - wid / 2f, len / 2f, wid / 2f, len, wid, 1f, 1f, rot);
    }

    public void segment(Batch batch, float x0, float z0, float x1, float z1, float width) {
        float dx = x1 - x0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-3f) {
            return;
        }
        float yaw = MathUtils.atan2(dx, dz) * MathUtils.radiansToDegrees;
        rect(batch, (x0 + x1) / 2f, (z0 + z1) / 2f, width, len + width * 0.5f, yaw);
    }

    public void outline(Batch batch, Zone z, float lineWidth) {
        float s = MathUtils.sinDeg(z.yawDeg), c = MathUtils.cosDeg(z.yawDeg);
        float[][] corners = new float[4][2];
        for (int i = 0; i < 4; i++) {
            float lxl = (i == 0 || i == 3 ? -1f : 1f) * z.halfX;
            float lzl = (i < 2 ? -1f : 1f) * z.halfZ;
            corners[i][0] = z.cx + lxl * c + lzl * s;
            corners[i][1] = z.cz - lxl * s + lzl * c;
        }
        for (int i = 0; i < 4; i++) {
            float[] a = corners[i], b = corners[(i + 1) % 4];
            segment(batch, a[0], a[1], b[0], b[1], lineWidth);
        }
    }

    public void disc(Batch batch, float x, float z, float r) {
        float px = sx(x, z), py = sy(x, z), rr = Math.max(r * scale, 2.5f);
        batch.draw(circle, px - rr, py - rr, rr * 2f, rr * 2f);
    }
}
