package com.pelayo.autoescuela.sim.world;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.pelayo.autoescuela.data.ScenarioDef;
import com.pelayo.autoescuela.sim.PhysicsWorld;

/**
 * Un coche o un peatón que recorre un camino fijo (polilínea) a su velocidad
 * de crucero. Frena para no chocar con lo que tenga delante en su camino (el
 * coche del jugador u otro agente) y se para si hace falta. Su cuerpo físico es
 * cinemático: empuja al coche del jugador pero no lo empujan a él.
 */
public class TrafficAgent {

    public enum Type { CAR, PEDESTRIAN }

    public final Type type;
    final ScenarioDef.TrafficDef def;
    private final float[] xs, zs, cum;
    final float length;
    final float cruise;
    final float halfLength;
    final float halfWidth;
    private final float startS;

    /** Posición en el camino (m), velocidad (m/s) y sentido (+1/-1, para los peatones que van y vuelven). */
    float s, v;
    int dir = 1;
    private float pauseTimer;
    private float delayTimer;
    boolean started;
    /** Pose actual. */
    public float x, z, yawDeg;

    btRigidBody body;
    PhysicsWorld.DefaultMotionState motion;
    public ModelInstance instance;
    float bodyY;
    /** Altura a la que se dibuja el modelo (sus ruedas o pies en el suelo). */
    float renderY;

    /** Frenada máxima que le ha hecho falta por el jugador en este acercamiento (m/s²) y si ya se anotó. */
    float playerBrake;
    /** Tiempo parado esperando a otro agente y tiempo que, tras eso, pasa sin mirar a los demás. */
    float blockedTimer;
    float ignoreAgentsTimer;
    boolean forcedFlagged;
    boolean crossingFlagged;
    int crossings;

    private final Matrix4 tmp = new Matrix4();

    TrafficAgent(ScenarioDef.TrafficDef def, float[][] path, float offset) {
        this.def = def;
        this.type = "pedestrian".equals(def.type) ? Type.PEDESTRIAN : Type.CAR;
        int n = path.length;
        xs = new float[n];
        zs = new float[n];
        cum = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = path[i][0];
            zs[i] = path[i][1];
            if (i > 0) {
                cum[i] = cum[i - 1] + (float) Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
            }
        }
        length = cum[n - 1];
        cruise = def.speedKmh / 3.6f;
        halfLength = type == Type.CAR ? 2.0f : 0.25f;
        halfWidth = type == Type.CAR ? 0.86f : 0.3f;
        startS = def.loop ? ((offset % length) + length) % length : MathUtils.clamp(offset, 0f, length);
        reset();
    }

    void reset() {
        s = startS;
        v = def.triggerDistance > 0f ? 0f : cruise;
        dir = 1;
        pauseTimer = 0f;
        delayTimer = def.delay;
        started = def.triggerDistance <= 0f;
        playerBrake = 0f;
        forcedFlagged = false;
        crossingFlagged = false;
        crossings = 0;
        updatePose();
    }

    /** Punto del camino a la distancia d (m) desde el inicio, en out[0..1]; da la vuelta si es circular. */
    void pointAt(float d, float[] out) {
        if (def.loop) {
            d = ((d % length) + length) % length;
        } else {
            d = MathUtils.clamp(d, 0f, length);
        }
        int i = 1;
        while (i < cum.length - 1 && cum[i] < d) {
            i++;
        }
        float seg = cum[i] - cum[i - 1];
        float t = seg > 1e-4f ? (d - cum[i - 1]) / seg : 0f;
        out[0] = MathUtils.lerp(xs[i - 1], xs[i], t);
        out[1] = MathUtils.lerp(zs[i - 1], zs[i], t);
    }

    private final float[] p0 = new float[2];
    private final float[] p1 = new float[2];

    void updatePose() {
        pointAt(s, p0);
        pointAt(s + dir * 0.5f, p1);
        float dx = p1[0] - p0[0], dz = p1[1] - p0[1];
        if (Math.abs(dx) + Math.abs(dz) > 1e-5f) {
            yawDeg = MathUtils.atan2(dx, dz) * MathUtils.radiansToDegrees;
        }
        x = p0[0];
        z = p0[1];
        if (motion != null) {
            motion.transform.setToTranslation(x, bodyY, z).rotate(Vector3.Y, yawDeg);
            body.setWorldTransform(motion.transform);
        }
    }

    /** ¿Está el peatón sobre la calzada? (la parte central de su camino; los extremos son las aceras). */
    boolean onRoad() {
        return type == Type.PEDESTRIAN && started && s > length * 0.12f && s < length * 0.88f;
    }

    /** Distancia del punto (px, pz) al camino del agente (para saber si el jugador está en el paso). */
    float distanceToPath(float px, float pz) {
        float best = Float.MAX_VALUE;
        for (int i = 1; i < xs.length; i++) {
            float ax = xs[i - 1], az = zs[i - 1], bx = xs[i], bz = zs[i];
            float vx = bx - ax, vz = bz - az;
            float len2 = vx * vx + vz * vz;
            float t = len2 < 1e-6f ? 0f : MathUtils.clamp(((px - ax) * vx + (pz - az) * vz) / len2, 0f, 1f);
            best = Math.min(best, (float) Math.hypot(px - (ax + vx * t), pz - (az + vz * t)));
        }
        return best;
    }

    /**
     * Distancia libre por delante en el camino hasta el obstáculo más cercano (el jugador u otro agente),
     * o +inf. {@code byPlayer[0]} dice si el más cercano es el jugador.
     */
    float gapAhead(float lookahead, float playerX, float playerZ, float playerFx, float playerFz, boolean hasPlayer,
                   Iterable<TrafficAgent> others, boolean[] byPlayer) {
        float best = Float.MAX_VALUE;
        byPlayer[0] = false;
        float step = type == Type.CAR ? 1f : 0.3f;
        for (float d = 0f; d <= lookahead; d += step) {
            pointAt(s + dir * d, p0);
            if (!def.loop && (s + dir * d < 0f || s + dir * d > length)) {
                break;
            }
            // El coche del jugador como dos círculos de 1 m (mitad delantera y trasera)
            float front = (float) Math.hypot(playerX + playerFx * 1.1f - p0[0], playerZ + playerFz * 1.1f - p0[1]);
            float rear = (float) Math.hypot(playerX - playerFx * 1.1f - p0[0], playerZ - playerFz * 1.1f - p0[1]);
            if (hasPlayer && Math.min(front, rear) < 1.0f + halfWidth + 0.25f) {
                float gap = d - halfLength - 1.0f;
                if (gap < best) {
                    best = gap;
                    byPlayer[0] = true;
                }
                break;
            }
            boolean hit = false;
            for (TrafficAgent o : ignoreAgentsTimer > 0f ? java.util.Collections.<TrafficAgent>emptyList() : others) {
                if (o == this) {
                    continue;
                }
                if (Math.hypot(o.x - p0[0], o.z - p0[1]) < o.halfWidth + halfWidth + 0.3f && d > 0.01f) {
                    float gap = d - halfLength - o.halfLength;
                    if (gap < best) {
                        best = gap;
                        byPlayer[0] = false;
                    }
                    hit = true;
                    break;
                }
            }
            if (hit) {
                break;
            }
        }
        return best;
    }

    /** Avanza un paso: arranque, velocidad según lo que tenga delante, recorrido y pose. */
    void step(float dt, float desired, float decel, float accel) {
        if (!started) {
            return;
        }
        if (delayTimer > 0f) {
            delayTimer -= dt;
            return;
        }
        if (pauseTimer > 0f) {
            pauseTimer -= dt;
            v = 0f;
            return;
        }
        if (v > desired) {
            v = Math.max(desired, v - decel * dt);
        } else {
            v = Math.min(desired, v + accel * dt);
        }
        s += dir * v * dt;
        if (def.loop) {
            s = ((s % length) + length) % length;
        } else if (s >= length || s <= 0f) {
            s = MathUtils.clamp(s, 0f, length);
            if (def.pingPong) {
                dir = -dir;
                pauseTimer = def.pause;
                crossings++;
                crossingFlagged = false;
            } else {
                s = 0f; // vuelve a empezar (fuera de la vista)
            }
        }
        updatePose();
    }

    float startX() {
        return xs[0];
    }

    float startZ() {
        return zs[0];
    }
}
