package com.pelayo.autoescuela.sim.world;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;

/**
 * Tráfico de un escenario: coches y peatones que siguen sus caminos. Se mueve
 * antes de cada paso de física y anota lo que importa para la evaluación:
 * obligar a un coche con preferencia a frenar fuerte (no ceder el paso) y
 * pasar por un paso de peatones mientras alguien cruza.
 */
public class TrafficSystem {

    /** Algo que ha pasado este paso y que el evaluador convierte en falta. */
    public enum Event { FORCED_BRAKE, PEDESTRIAN_NOT_YIELDED }

    /** Frenada (m/s²) a partir de la cual se considera que el jugador le ha quitado la preferencia. */
    static final float FORCED_BRAKE = 3.5f;
    private static final float CAR_ACCEL = 1.8f;
    private static final float CAR_COMFORT_DECEL = 2.5f;
    private static final float CAR_MAX_DECEL = 8f;
    private static final float CAR_STOP_GAP = 2.0f;
    private static final float PED_STOP_GAP = 0.5f;

    public final Array<TrafficAgent> agents = new Array<>();
    /** Eventos del último paso (se vacía al empezar cada uno). */
    public final Array<Event> events = new Array<>();
    private VehicleState player;
    private final boolean[] byPlayer = new boolean[1];

    public void setPlayer(VehicleState player) {
        this.player = player;
    }

    public void reset() {
        for (TrafficAgent a : agents) {
            a.reset();
        }
        events.clear();
    }

    public void step(float dt) {
        events.clear();
        boolean hasPlayer = player != null;
        float px = hasPlayer ? player.position.x : 0f;
        float pz = hasPlayer ? player.position.z : 0f;
        float pfx = hasPlayer ? player.forward.x : 0f;
        float pfz = hasPlayer ? player.forward.z : 1f;
        float flat = (float) Math.hypot(pfx, pfz);
        if (flat > 1e-4f) {
            pfx /= flat;
            pfz /= flat;
        }
        float playerSpeed = hasPlayer ? Math.abs(player.forwardSpeed) : 0f;
        // Por índice: gapAhead recorre la misma lista (los iteradores de Array no se pueden anidar)
        for (int ai = 0; ai < agents.size; ai++) {
            TrafficAgent a = agents.get(ai);
            // Los que esperan a que el jugador se acerque (peatones junto al paso)
            if (!a.started && hasPlayer && a.def.triggerDistance > 0f
                && Math.hypot(px - a.startX(), pz - a.startZ()) < a.def.triggerDistance) {
                a.started = true;
            }
            boolean car = a.type == TrafficAgent.Type.CAR;
            float lookahead = car ? 6f + a.v * 2.2f : 2.0f;
            float stopGap = car ? CAR_STOP_GAP : PED_STOP_GAP;
            float gap = a.gapAhead(lookahead, px, pz, pfx, pfz, hasPlayer, agents, byPlayer);
            float desired = a.cruise;
            float decel = car ? CAR_COMFORT_DECEL : 3f;
            if (gap < Float.MAX_VALUE) {
                float room = Math.max(0f, gap - stopGap);
                // Velocidad que permite parar en el hueco con una frenada cómoda; si no da, la necesaria
                desired = Math.min(desired, (float) Math.sqrt(2f * CAR_COMFORT_DECEL * room));
                float needed = a.v * a.v / (2f * Math.max(0.3f, room));
                decel = MathUtils.clamp(Math.max(decel, needed), 0f, CAR_MAX_DECEL);
                if (car && byPlayer[0]) {
                    a.playerBrake = Math.max(a.playerBrake, needed);
                    // Le ha cortado el paso un jugador en marcha: no ha cedido la preferencia
                    if (!a.forcedFlagged && needed > FORCED_BRAKE && a.v > 2f && playerSpeed > 0.8f) {
                        a.forcedFlagged = true;
                        events.add(Event.FORCED_BRAKE);
                    }
                }
            } else if (car && a.forcedFlagged) {
                a.forcedFlagged = false; // acercamiento terminado
                a.playerBrake = 0f;
            }
            // Bloqueo mutuo en un cruce: tras 2,5 s parado por otro agente, pasa sin mirarlos un momento
            if (gap < Float.MAX_VALUE && !byPlayer[0] && a.v < 0.2f && a.started) {
                a.blockedTimer += dt;
                if (a.blockedTimer > 2.5f + (ai % 3) * 0.7f) {
                    a.ignoreAgentsTimer = 2.5f;
                    a.blockedTimer = 0f;
                }
            } else {
                a.blockedTimer = 0f;
            }
            if (a.ignoreAgentsTimer > 0f) {
                a.ignoreAgentsTimer -= dt;
            }
            a.step(dt, desired, decel, car ? CAR_ACCEL : 1.2f);

            // Peatón cruzando y el jugador pasa por el paso sin pararse
            if (!car && hasPlayer && !a.crossingFlagged && a.onRoad() && playerSpeed > 1.0f
                && a.distanceToPath(px, pz) < 2.6f) {
                a.crossingFlagged = true;
                events.add(Event.PEDESTRIAN_NOT_YIELDED);
            }
        }
    }

    /** Copia las poses al render (modelos de coches y peatones). */
    public void syncRender() {
        for (TrafficAgent a : agents) {
            if (a.instance != null) {
                float bob = a.type == TrafficAgent.Type.PEDESTRIAN && a.v > 0.1f
                    ? Math.abs(MathUtils.sin(a.s * 5f)) * 0.04f : 0f;
                a.instance.transform.setToTranslation(a.x, a.renderY + bob, a.z).rotate(com.badlogic.gdx.math.Vector3.Y, a.yawDeg);
            }
        }
    }
}
