package com.pelayo.autoescuela.sim.world;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/** Rectángulo con nombre en el plano XZ, posiblemente girado. */
public class Zone {

    public final String name;
    public final float cx, cz;
    public final float halfX, halfZ;
    public final float yawDeg;
    private final float cos, sin;

    public Zone(String name, float cx, float cz, float sizeX, float sizeZ, float yawDeg) {
        this.name = name;
        this.cx = cx;
        this.cz = cz;
        this.halfX = sizeX / 2f;
        this.halfZ = sizeZ / 2f;
        this.yawDeg = yawDeg;
        this.cos = MathUtils.cosDeg(yawDeg);
        this.sin = MathUtils.sinDeg(yawDeg);
    }

    /** Coordenada X local (lateral) del punto; 0 en el eje de la zona, + a la izquierda. */
    public float localX(float x, float z) {
        float dx = x - cx;
        float dz = z - cz;
        return dx * cos - dz * sin;
    }

    /** Coordenada Z local (longitudinal) del punto; 0 en el centro, + hacia delante. */
    public float localZ(float x, float z) {
        float dx = x - cx;
        float dz = z - cz;
        return dx * sin + dz * cos;
    }

    public boolean contains(float x, float z) {
        return Math.abs(localX(x, z)) <= halfX && Math.abs(localZ(x, z)) <= halfZ;
    }

    public boolean contains(Vector3 p) {
        return contains(p.x, p.z);
    }

    /** Todos los puntos dentro (p.ej. las cuatro ruedas). */
    public boolean containsAll(Vector3[] points) {
        for (Vector3 p : points) {
            if (!contains(p.x, p.z)) {
                return false;
            }
        }
        return true;
    }

    /** Alguno de los puntos dentro. */
    public boolean containsAny(Vector3[] points) {
        for (Vector3 p : points) {
            if (contains(p.x, p.z)) {
                return true;
            }
        }
        return false;
    }
}
