package com.pelayo.autoescuela.sim.eval.maneuvers;

import com.pelayo.autoescuela.sim.eval.FaultSeverity;
import com.pelayo.autoescuela.sim.eval.Maneuver;
import com.pelayo.autoescuela.sim.vehicle.BulletVehicle;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import com.pelayo.autoescuela.sim.world.Zone;

/**
 * Detención en un STOP: hay que parar del todo (≥ 1 s) con las ruedas
 * delanteras antes de la línea (zona "before_stop": su borde delantero es la
 * línea) y continuar hasta "finish". Cruzar sin parar es eliminatoria; parar
 * con las ruedas sobre la línea, deficiente.
 */
public class StopSign extends Maneuver {

    private static final float HOLD = 1.0f;

    private Zone beforeStop;
    private Zone finishZone;
    private float holdTimer;
    private boolean stoppedProperly;
    private boolean crossed;

    @Override
    protected void onStart() {
        beforeStop = ctx.geometry.zone("before_stop");
        finishZone = ctx.geometry.zone("finish");
        holdTimer = 0f;
        stoppedProperly = false;
        crossed = false;
    }

    @Override
    protected void onUpdate(VehicleState s, float dt) {
        float frontL = beforeStop.localZ(s.wheelContact[BulletVehicle.WHEEL_FL].x, s.wheelContact[BulletVehicle.WHEEL_FL].z);
        float frontR = beforeStop.localZ(s.wheelContact[BulletVehicle.WHEEL_FR].x, s.wheelContact[BulletVehicle.WHEEL_FR].z);
        float front = Math.max(frontL, frontR);
        boolean beforeLine = front <= beforeStop.halfZ;
        boolean nearLine = front > -beforeStop.halfZ; // ya dentro de la zona de detención

        if (!crossed && !stoppedProperly && nearLine && isStopped(s)) {
            holdTimer += dt;
            if (holdTimer >= HOLD) {
                if (beforeLine) {
                    stoppedProperly = true;
                    metric("stopGap", String.format("%.1f m", beforeStop.halfZ - front));
                } else {
                    stoppedProperly = true;
                    fault("stopOverLine", FaultSeverity.DEFICIENTE);
                }
            }
        } else if (!isStopped(s)) {
            holdTimer = 0f;
        }
        // Un metro más allá de la línea sin haber parado: el STOP no se ha respetado
        if (!crossed && front > beforeStop.halfZ + 1.0f) {
            crossed = true;
            if (!stoppedProperly) {
                fault("stopNotRespected", FaultSeverity.ELIMINATORIA);
            }
        }
        if (finishZone.contains(s.position)) {
            finish();
        }
    }

    @Override
    public String getInstruction() {
        if (!crossed) {
            return ctx.i18n.t("maneuver.stop.approach");
        }
        return ctx.i18n.t("maneuver.stop.continue");
    }
}
