package com.pelayo.autoescuela.sim.eval.maneuvers;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import com.pelayo.autoescuela.data.ScenarioDef.GoalDef;
import com.pelayo.autoescuela.data.ScenarioDef.StepDef;
import com.pelayo.autoescuela.sim.eval.Maneuver;
import com.pelayo.autoescuela.sim.vehicle.VehicleEvent;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import com.pelayo.autoescuela.sim.world.Zone;

/**
 * Lección guiada ("Aprende a conducir"): recorre los pasos de
 * {@code ScenarioDef.lesson}. Cada paso se supera cuando todos sus objetivos
 * se cumplen a la vez durante {@code hold} segundos, o al pulsar Continuar
 * si es de lectura. No da veredicto: al terminar el último paso la lección
 * queda completada. Las faltas comunes se siguen anotando, pero la pantalla
 * de resultados de una lección no las juzga.
 */
public class Tutorial extends Maneuver {

    private Array<StepDef> steps;
    private int index;
    private float stepTime;
    private float holdTime;
    private float distance;
    private boolean acknowledged;
    private boolean[] met = new boolean[0];
    private final ObjectSet<VehicleEvent> seen = new ObjectSet<>();
    /** Último evento que merece una pista inmediata (calado, rascada...), o null. */
    private VehicleEvent lastProblem;
    private float problemTime;
    private float speedKmh;
    /** Paso más avanzado al que se ha llegado (se puede volver atrás y avanzar hasta él). */
    private int maxIndex;
    /** Paso revisitado: no se supera por sus objetivos (ya se cumplieron), sino con Continuar. */
    private boolean reviewing;

    @Override
    protected void onStart() {
        steps = ctx.geometry.def.lesson != null ? ctx.geometry.def.lesson.steps : new Array<>();
        enter(0);
    }

    private void enter(int i) {
        index = i;
        maxIndex = Math.max(maxIndex, i);
        reviewing = i < maxIndex;
        stepTime = 0f;
        holdTime = 0f;
        distance = 0f;
        acknowledged = false;
        seen.clear();
        lastProblem = null;
        met = new boolean[i < steps.size ? steps.get(i).goals.size : 0];
        if (i >= steps.size) {
            metric("stalls", String.valueOf(stalls));
            finish();
        }
    }

    @Override
    protected void onUpdate(VehicleState s, float dt) {
        StepDef step = steps.get(index);
        stepTime += dt;
        distance += s.forwardSpeed * dt;
        speedKmh = s.speedKmh;
        for (VehicleEvent e : s.events) {
            seen.add(e);
            if (e == VehicleEvent.STALL || e == VehicleEvent.GRIND || e == VehicleEvent.START_IN_GEAR
                || e == VehicleEvent.REVERSE_REFUSED) {
                lastProblem = e;
                problemTime = stepTime;
            }
        }

        boolean all = true;
        for (int g = 0; g < step.goals.size; g++) {
            met[g] = check(step.goals.get(g), s);
            all &= met[g];
        }
        if (step.ack) {
            all &= acknowledged;
        }
        if (reviewing) {
            all = acknowledged;
        }
        holdTime = all ? holdTime + dt : 0f;
        if (all && holdTime >= (step.ack || reviewing ? 0f : step.hold)) {
            enter(index + 1);
        }
    }

    private boolean check(GoalDef g, VehicleState s) {
        switch (g.type) {
            case "clutch": return in(s.clutch, g);
            case "brake": return in(s.brake, g);
            case "throttle": return in(s.throttle, g);
            case "steer": return in(s.steer, g);
            case "rpm": return s.engineRunning && in(s.rpm, g);
            case "speed": return in(s.speedKmh, g);
            case "gear": return s.gear == g.value;
            case "handbrake": return s.handbrake == (g.value != 0);
            case "engine": return (s.engineRunning && !s.engineStarting) == (g.value != 0);
            case "stopped": return isStopped(s);
            case "moving": return Math.abs(s.forwardSpeed) > 0.3f;
            case "distance": return g.meters >= 0f ? distance >= g.meters : distance <= g.meters;
            case "zone": {
                Zone zone = ctx.geometry.zone(g.zone);
                return zone != null && zone.contains(s.position);
            }
            case "indicator":
                return g.value < 0 ? s.indicatorLeft : g.value > 0 ? s.indicatorRight
                    : !s.indicatorLeft && !s.indicatorRight;
            case "look": return s.lookYaw >= (g.min > -1e8f ? g.min : 0.8f);
            case "event": return seen.contains(VehicleEvent.valueOf(g.event));
            default: throw new IllegalArgumentException("Objetivo desconocido en la lección: " + g.type);
        }
    }

    private static boolean in(float v, GoalDef g) {
        return v >= g.min && v <= g.max;
    }

    /** Pulsado Continuar: supera el paso si es de lectura o si se está repasando. */
    public void acknowledge() {
        if (!finished && index < steps.size && (steps.get(index).ack || reviewing)) {
            acknowledged = true;
        }
    }

    /** Vuelve al paso anterior para repasarlo. */
    public void previous() {
        if (!finished && index > 0) {
            enter(index - 1);
        }
    }

    /** Avanza al siguiente paso si ya se había llegado a él. */
    public void next() {
        if (!finished && index < maxIndex) {
            enter(index + 1);
        }
    }

    public boolean canGoBack() {
        return !finished && index > 0;
    }

    public boolean canGoForward() {
        return !finished && index < maxIndex;
    }

    /** El paso actual se está repasando (se sigue con Continuar). */
    public boolean isReviewing() {
        return reviewing;
    }

    public int getStepIndex() {
        return index;
    }

    public int getStepCount() {
        return steps.size;
    }

    /** Paso actual, o null si la lección ha terminado. */
    public StepDef getStep() {
        return index < steps.size ? steps.get(index) : null;
    }

    public boolean isGoalMet(int goal) {
        return goal < met.length && met[goal];
    }

    /** Metros recorridos en el paso actual (negativo marcha atrás). */
    public float getDistance() {
        return distance;
    }

    public float getSpeedKmh() {
        return speedKmh;
    }

    public float getStepTime() {
        return stepTime;
    }

    /** Problema reciente (en los últimos 8 s del paso) para explicar qué ha pasado, o null. */
    public VehicleEvent getRecentProblem() {
        return lastProblem != null && stepTime - problemTime < 8f ? lastProblem : null;
    }

    public int getStalls() {
        return stalls;
    }

    private final Array<Zone> targetZones = new Array<>();

    @Override
    public Array<Zone> getTargetZones() {
        targetZones.clear();
        StepDef step = getStep();
        if (step != null) {
            for (GoalDef g : step.goals) {
                if ("zone".equals(g.type)) {
                    targetZones.add(ctx.geometry.zone(g.zone));
                }
            }
        }
        return targetZones;
    }

    @Override
    public String getInstruction() {
        return ""; // el panel de la lección sustituye a la instrucción del HUD
    }
}
