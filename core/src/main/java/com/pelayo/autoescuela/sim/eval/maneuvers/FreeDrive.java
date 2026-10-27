package com.pelayo.autoescuela.sim.eval.maneuvers;

import com.pelayo.autoescuela.sim.eval.Maneuver;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;

/** Conducción libre: no evalúa nada ni termina nunca. */
public class FreeDrive extends Maneuver {

    @Override
    protected void onStart() {
    }

    @Override
    protected void onUpdate(VehicleState state, float dt) {
    }

    @Override
    public String getInstruction() {
        return ctx.i18n.t("maneuver.free.instruction");
    }
}
