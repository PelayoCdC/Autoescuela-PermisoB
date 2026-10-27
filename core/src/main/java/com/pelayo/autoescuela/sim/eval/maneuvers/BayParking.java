package com.pelayo.autoescuela.sim.eval.maneuvers;

import com.pelayo.autoescuela.sim.eval.FaultSeverity;
import com.pelayo.autoescuela.sim.eval.Maneuver;
import com.pelayo.autoescuela.sim.eval.probes.MovementCounter;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import com.pelayo.autoescuela.sim.world.Marking;
import com.pelayo.autoescuela.sim.world.Zone;

/**
 * Maniobras I.2 (oblicuo) e I.3 (perpendicular): estacionar en una plaza en
 * batería (BAY "bay"), centrado y paralelo a sus límites, en ≤ 3 movimientos,
 * y salir en ≤ 3 movimientos hasta "exit_done". Pisar las líneas de la plaza
 * es falta leve. Vale entrar de frente o marcha atrás.
 */
public class BayParking extends Maneuver {

    private enum Phase { APPROACH, PARKING, EXIT, DONE }

    private static final float PARKED_HOLD = 1.0f;

    private Phase phase;
    private Marking bay;
    private Zone exitDone;
    private final MovementCounter movements = new MovementCounter();
    private float holdTimer;
    private boolean started;

    @Override
    protected void onStart() {
        phase = Phase.APPROACH;
        bay = ctx.geometry.bay("bay");
        exitDone = ctx.geometry.zone("exit_done");
        movements.reset();
        holdTimer = 0f;
        started = false;
    }

    @Override
    protected void onUpdate(VehicleState s, float dt) {
        switch (phase) {
            case APPROACH:
                // La maniobra empieza cuando alguna rueda entra en la plaza o se retrocede
                if (bay.bay.containsAny(s.wheelContact) || s.forwardSpeed < -0.1f) {
                    phase = Phase.PARKING;
                    movements.reset();
                    movements.update(s.forwardSpeed, dt);
                }
                break;
            case PARKING:
                movements.update(s.forwardSpeed, dt);
                checkMovements();
                if (bay.bay.containsAll(s.wheelContact) && isStopped(s) && s.handbrake) {
                    holdTimer += dt;
                    if (holdTimer >= PARKED_HOLD) {
                        evaluateParked(s);
                        phase = Phase.EXIT;
                        movements.reset();
                        resetOnce("movements");
                    }
                } else {
                    holdTimer = 0f;
                }
                break;
            case EXIT:
                movements.update(s.forwardSpeed, dt);
                checkMovements();
                if (exitDone.contains(s.position) && !bay.bay.containsAny(s.wheelContact)) {
                    metric("exitMovements", String.valueOf(movements.getMovements()));
                    phase = Phase.DONE;
                    finish();
                }
                break;
            default:
                break;
        }
    }

    private void checkMovements() {
        if (movements.getMovements() > ctx.geometry.def.params.maxMovements) {
            faultOnce("movements", FaultSeverity.DEFICIENTE, ctx.geometry.def.params.maxMovements);
        }
    }

    private void evaluateParked(VehicleState s) {
        Zone z = bay.bay;
        float offset = Math.abs(z.localX(s.position.x, s.position.z));
        float align = Math.abs(yawDelta(s.yawDeg, z.yawDeg));
        if (align > 90f) {
            align = Math.abs(180f - align); // entrar de frente o marcha atrás vale igual
        }
        metric("bayOffset", String.format("%.0f cm (máx. %.0f)", offset * 100f, ctx.geometry.def.params.maxBayOffset * 100f));
        metric("alignment", String.format("%.1f°", align));
        metric("parkMovements", String.valueOf(movements.getMovements()));
        if (offset > ctx.geometry.def.params.maxBayOffset) {
            fault("bayOffset", FaultSeverity.LEVE, (int) (offset * 100f));
        }
        if (align > ctx.geometry.def.params.maxAlignmentDeg) {
            fault("alignment", FaultSeverity.LEVE, (int) align);
        }
        for (int i = 0; i < 4; i++) {
            if (bay.isOn(s.wheelContact[i].x, s.wheelContact[i].z)) {
                fault("bayLine", FaultSeverity.LEVE);
                break;
            }
        }
    }

    @Override
    public String getInstruction() {
        int max = ctx.geometry.def.params.maxMovements;
        switch (phase) {
            case APPROACH: return ctx.i18n.t("maneuver.bay.approach");
            case PARKING: return ctx.i18n.t("maneuver.bay.parking", max);
            case EXIT: return ctx.i18n.t("maneuver.bay.exit", max);
            default: return ctx.i18n.t("maneuver.done");
        }
    }

    @Override
    public String getStatus() {
        if (phase == Phase.PARKING || phase == Phase.EXIT) {
            return ctx.i18n.t("maneuver.movements", movements.getMovements(), ctx.geometry.def.params.maxMovements);
        }
        return "";
    }
}
