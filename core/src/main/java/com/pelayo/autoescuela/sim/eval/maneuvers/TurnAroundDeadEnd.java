package com.pelayo.autoescuela.sim.eval.maneuvers;

import com.pelayo.autoescuela.sim.eval.FaultSeverity;
import com.pelayo.autoescuela.sim.eval.Maneuver;
import com.pelayo.autoescuela.sim.eval.probes.MovementCounter;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import com.pelayo.autoescuela.sim.world.Zone;

/**
 * Maniobra H de la DGT: cambio de sentido en una calle sin salida. Se entra
 * por la derecha (zona "street", mitad derecha = X local negativa), se gira a
 * la izquierda, se hace una sola marcha atrás y se sale en sentido contrario
 * hasta la zona "exit_done" con el rumbo invertido.
 */
public class TurnAroundDeadEnd extends Maneuver {

    private enum Phase { APPROACH, TURNING, DONE }

    private Phase phase;
    private Zone street;
    private Zone exitDone;
    private final MovementCounter movements = new MovementCounter();
    private int reverses;
    private int lastSign;
    private float initialYaw;
    private boolean entryChecked;

    @Override
    protected void onStart() {
        phase = Phase.APPROACH;
        street = ctx.geometry.zone("street");
        exitDone = ctx.geometry.zone("exit_done");
        movements.reset();
        reverses = 0;
        lastSign = 0;
        entryChecked = false;
    }

    @Override
    protected void onUpdate(VehicleState s, float dt) {
        if (phase == Phase.APPROACH) {
            initialYaw = s.yawDeg;
            if (street.containsAll(s.wheelContact)) {
                phase = Phase.TURNING;
                movements.reset();
            }
            return;
        }
        if (phase != Phase.TURNING) {
            return;
        }
        if (!entryChecked) {
            // Nada más entrar, el coche debe ir por la mitad derecha de la calle
            entryChecked = true;
            if (street.localX(s.position.x, s.position.z) > 0f) {
                fault("keepRight", FaultSeverity.LEVE);
            }
        }
        movements.update(s.forwardSpeed, dt);
        int sign = movements.getCurrentSign();
        if (sign == -1 && lastSign != -1) {
            reverses++;
            if (reverses > 1) {
                faultOnce("singleReverse", FaultSeverity.DEFICIENTE);
            }
        }
        lastSign = sign;
        if (exitDone.contains(s.position)) {
            float delta = Math.abs(yawDelta(s.yawDeg, initialYaw + 180f));
            metric("reverses", String.valueOf(reverses));
            if (delta > 25f) {
                fault("wrongDirection", FaultSeverity.DEFICIENTE);
            }
            if (reverses == 0) {
                fault("noReverse", FaultSeverity.LEVE);
            }
            phase = Phase.DONE;
            finish();
        }
    }

    @Override
    public String getInstruction() {
        switch (phase) {
            case APPROACH: return ctx.i18n.t("maneuver.turnAround.approach");
            case TURNING: return ctx.i18n.t("maneuver.turnAround.turning");
            default: return ctx.i18n.t("maneuver.done");
        }
    }

    @Override
    public String getStatus() {
        return phase == Phase.TURNING ? ctx.i18n.t("maneuver.reverses", reverses, 1) : "";
    }
}
