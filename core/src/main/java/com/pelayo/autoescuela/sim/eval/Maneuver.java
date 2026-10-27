package com.pelayo.autoescuela.sim.eval;

import com.badlogic.gdx.utils.ObjectSet;
import com.pelayo.autoescuela.sim.BodyTag;
import com.pelayo.autoescuela.sim.vehicle.VehicleEvent;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;

/**
 * Evaluador de una maniobra: máquina de fases que observa el estado del coche
 * en cada paso, anota faltas y decide cuándo ha terminado. Las subclases
 * implementan {@link #onUpdate}; aquí están las comprobaciones comunes
 * (choques, bordillos, calados, tiempo, salirse de la calzada).
 */
public abstract class Maneuver {

    protected ManeuverContext ctx;
    protected final EvaluationResult result = new EvaluationResult();
    private final ObjectSet<String> onceCodes = new ObjectSet<>();
    protected float time;
    protected boolean finished;
    protected int stalls;
    private boolean timeFaulted;
    private boolean movableFaulted;
    /** Muestreo del recorrido para la repetición. */
    private static final float TRACE_PERIOD = 0.1f;
    private float lastSample = -1f;
    private float lastX = Float.NaN, lastZ = Float.NaN;

    public void start(ManeuverContext ctx) {
        this.ctx = ctx;
        time = 0f;
        finished = false;
        onStart();
    }

    /** Un paso de simulación. */
    public final void update(VehicleState state, float dt) {
        if (finished) {
            return;
        }
        time += dt;
        lastX = state.position.x;
        lastZ = state.position.z;
        if (time - lastSample >= TRACE_PERIOD || result.trace.size == 0) {
            lastSample = time;
            result.trace.addAll(time, state.position.x, state.position.z, state.yawDeg, state.speedKmh);
        }
        checkCommon(state);
        onUpdate(state, dt);
        if (finished) {
            result.duration = time;
            result.completed = true;
            result.passed = Verdict.passes(result);
        }
    }

    protected abstract void onStart();

    protected abstract void onUpdate(VehicleState state, float dt);

    /** Instrucción actual para el conductor (ya traducida). */
    public abstract String getInstruction();

    /** Línea corta de estado para el HUD (p.ej. movimientos), o "" si nada. */
    public String getStatus() {
        return "";
    }

    public boolean isFinished() {
        return finished;
    }

    /** Termina la maniobra ahora (p.ej. el usuario abandona): resultado parcial. */
    public EvaluationResult abort() {
        if (!finished) {
            finished = true;
            result.duration = time;
            result.completed = false;
            result.passed = false;
        }
        return result;
    }

    public EvaluationResult getResult() {
        return result;
    }

    // --- Faltas -----------------------------------------------------------

    /** Anota una falta (siempre). */
    protected void fault(String code, FaultSeverity severity, Object... args) {
        Fault f = new Fault(code, severity, time, ctx.i18n.t("fault." + code, args));
        f.x = lastX;
        f.z = lastZ;
        result.faults.add(f);
    }

    /** Anota la falta solo la primera vez que ocurre en toda la maniobra. */
    protected boolean faultOnce(String code, FaultSeverity severity, Object... args) {
        if (onceCodes.add(code)) {
            fault(code, severity, args);
            return true;
        }
        return false;
    }

    /** Permite volver a anotar una falta "once" (p.ej. al cambiar de fase). */
    protected void resetOnce(String code) {
        onceCodes.remove(code);
    }

    protected void metric(String key, String value) {
        result.metrics.put(ctx.i18n.t("metric." + key), value);
    }

    protected void finish() {
        finished = true;
    }

    // --- Comprobaciones comunes --------------------------------------------

    private void checkCommon(VehicleState state) {
        // Tráfico: no ceder el paso a un coche con preferencia o a un peatón que cruza
        if (ctx.scenario != null) {
            for (com.pelayo.autoescuela.sim.world.TrafficSystem.Event e : ctx.scenario.traffic.events) {
                if (e == com.pelayo.autoescuela.sim.world.TrafficSystem.Event.FORCED_BRAKE) {
                    fault("noYield", FaultSeverity.ELIMINATORIA);
                } else {
                    fault("pedestrianNotYielded", FaultSeverity.ELIMINATORIA);
                }
            }
        }
        for (BodyTag tag : state.contacts) {
            switch (tag) {
                case CURB: faultOnce("curbTouch", FaultSeverity.DEFICIENTE); break;
                case CONE: faultOnce("cone", FaultSeverity.DEFICIENTE); break;
                case TRAFFIC_CAR: faultOnce("collision", FaultSeverity.ELIMINATORIA); break;
                case PEDESTRIAN: faultOnce("pedestrianHit", FaultSeverity.ELIMINATORIA); break;
                case PARKED_CAR:
                case BARRIER: faultOnce("collision", FaultSeverity.ELIMINATORIA); break;
                default: break;
            }
        }
        for (int i = 0; i < 4; i++) {
            if (state.wheelGround[i] == BodyTag.CURB) {
                faultOnce("curbClimb", FaultSeverity.ELIMINATORIA);
            } else if (state.wheelInContact[i] && state.wheelGround[i] == BodyTag.OTHER) {
                faultOnce("offRoad", FaultSeverity.DEFICIENTE);
            }
        }
        if (!movableFaulted && ctx.anyMovableDisplaced()) {
            movableFaulted = true;
            faultOnce("cone", FaultSeverity.DEFICIENTE);
        }
        if (state.events.contains(VehicleEvent.STALL, true)) {
            stalls++;
            if (stalls == 3) {
                fault("stallRepeated", FaultSeverity.DEFICIENTE);
            } else if (stalls < 3) {
                fault("stall", FaultSeverity.LEVE);
            }
        }
        if (!timeFaulted && time > ctx.geometry.def.params.timeLimitSec) {
            timeFaulted = true;
            fault("time", FaultSeverity.LEVE, (int) ctx.geometry.def.params.timeLimitSec);
        }
    }

    /** El coche está parado (velocidad despreciable). */
    protected static boolean isStopped(VehicleState s) {
        return Math.abs(s.forwardSpeed) < 0.05f;
    }

    /** Zonas que el conductor debe alcanzar ahora (se resaltan en el minimapa); vacío si no hay. */
    public com.badlogic.gdx.utils.Array<com.pelayo.autoescuela.sim.world.Zone> getTargetZones() {
        if (defaultTargets == null) {
            defaultTargets = new com.badlogic.gdx.utils.Array<>();
            for (String name : TARGET_NAMES) {
                com.pelayo.autoescuela.sim.world.Zone z = ctx.geometry.zones.get(name);
                if (z != null) {
                    defaultTargets.add(z);
                }
            }
        }
        return defaultTargets;
    }

    /** Zonas que, si existen en el escenario, son la meta de la práctica. */
    private static final String[] TARGET_NAMES = {"finish", "top", "target"};
    private com.badlogic.gdx.utils.Array<com.pelayo.autoescuela.sim.world.Zone> defaultTargets;

    /** Diferencia de rumbo normalizada a (-180, 180]. */
    protected static float yawDelta(float yawA, float yawB) {
        float d = (yawA - yawB) % 360f;
        if (d > 180f) {
            d -= 360f;
        } else if (d <= -180f) {
            d += 360f;
        }
        return d;
    }
}
