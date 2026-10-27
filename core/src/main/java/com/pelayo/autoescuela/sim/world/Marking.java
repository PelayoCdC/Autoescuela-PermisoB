package com.pelayo.autoescuela.sim.world;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import com.pelayo.autoescuela.data.ScenarioDef;

/**
 * Marca vial geométrica: línea (LANE/STOP, polilínea con anchura) o plaza
 * (BAY, rectángulo). Sirve para saber si una rueda la pisa y para dibujarla.
 */
public class Marking {

    /** ZEBRA: franja de un paso de peatones (se pinta, pero pisarla no es pisar una línea). */
    public enum Kind { LANE, STOP, BAY, GUIDE, ZEBRA }

    public final Kind kind;
    public final String name;
    public final float[][] points;
    public final float width;
    /** Solo para BAY. */
    public final Zone bay;

    private final Vector2 a = new Vector2();
    private final Vector2 b = new Vector2();
    private final Vector2 p = new Vector2();

    public Marking(ScenarioDef.MarkingDef def) {
        this.kind = Kind.valueOf(def.kind);
        this.name = def.name;
        this.width = def.width;
        if (kind == Kind.BAY) {
            bay = new Zone(def.name, def.center[0], def.center[1], def.size[0], def.size[1], def.yawDeg);
            points = bayOutline(bay);
        } else {
            bay = null;
            points = def.resolvedPoints();
        }
    }

    /** Las cuatro esquinas de la plaza, cerradas (5 puntos). */
    private static float[][] bayOutline(Zone z) {
        float[][] local = {{-z.halfX, -z.halfZ}, {z.halfX, -z.halfZ}, {z.halfX, z.halfZ}, {-z.halfX, z.halfZ},
            {-z.halfX, -z.halfZ}};
        float[][] out = new float[5][2];
        float cos = (float) Math.cos(Math.toRadians(z.yawDeg));
        float sin = (float) Math.sin(Math.toRadians(z.yawDeg));
        for (int i = 0; i < 5; i++) {
            float lx = local[i][0];
            float lz = local[i][1];
            out[i][0] = z.cx + lx * cos + lz * sin;
            out[i][1] = z.cz - lx * sin + lz * cos;
        }
        return out;
    }

    /** Distancia del punto al trazo de la marca (0 si está sobre la línea). */
    public float distanceToLine(float x, float z) {
        p.set(x, z);
        float best = Float.MAX_VALUE;
        for (int i = 1; i < points.length; i++) {
            a.set(points[i - 1][0], points[i - 1][1]);
            b.set(points[i][0], points[i][1]);
            best = Math.min(best, Intersector.distanceSegmentPoint(a, b, p));
        }
        return best;
    }

    /** El punto está sobre la pintura de la línea. */
    public boolean isOn(float x, float z) {
        return distanceToLine(x, z) <= width / 2f;
    }
}
