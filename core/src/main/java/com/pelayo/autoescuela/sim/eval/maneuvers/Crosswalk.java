package com.pelayo.autoescuela.sim.eval.maneuvers;

import com.pelayo.autoescuela.sim.eval.Maneuver;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import com.pelayo.autoescuela.sim.world.Zone;

/**
 * Paso de peatones con peatones cruzando y tráfico de frente: hay que llegar
 * al final de la calle cediendo el paso a quien cruce. Las faltas (no ceder el
 * paso a un peatón, atropello, choque) las anota la comprobación común del
 * tráfico; esta maniobra solo marca el objetivo y cuándo se ha llegado.
 */
public class Crosswalk extends Maneuver {

    private Zone finishZone;

    @Override
    protected void onStart() {
        finishZone = ctx.geometry.zone("finish");
    }

    @Override
    protected void onUpdate(VehicleState s, float dt) {
        if (finishZone.contains(s.position)) {
            finish();
        }
    }

    @Override
    public String getInstruction() {
        return ctx.i18n.t("maneuver.crosswalk.instruction");
    }
}
