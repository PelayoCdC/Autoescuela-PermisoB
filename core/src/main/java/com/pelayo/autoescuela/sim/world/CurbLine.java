package com.pelayo.autoescuela.sim.world;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;

/** Polilínea del borde de un bordillo a nivel de calzada, en el plano XZ. */
public class CurbLine {

    public final String name;
    private final float[][] points;
    private final Vector2 a = new Vector2();
    private final Vector2 b = new Vector2();
    private final Vector2 p = new Vector2();

    public CurbLine(String name, float[][] points) {
        this.name = name;
        this.points = points;
    }

    /** Distancia horizontal mínima del punto al borde (m). */
    public float distanceTo(float x, float z) {
        p.set(x, z);
        float best = Float.MAX_VALUE;
        if (points.length == 1) {
            return p.dst(points[0][0], points[0][1]);
        }
        for (int i = 1; i < points.length; i++) {
            a.set(points[i - 1][0], points[i - 1][1]);
            b.set(points[i][0], points[i][1]);
            best = Math.min(best, Intersector.distanceSegmentPoint(a, b, p));
        }
        return best;
    }

    public float[][] getPoints() {
        return points;
    }
}
