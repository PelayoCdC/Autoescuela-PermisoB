package com.pelayo.autoescuela.sim.eval.maneuvers;

import com.pelayo.autoescuela.sim.eval.Maneuver;
import com.pelayo.autoescuela.sim.vehicle.VehicleEvent;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import com.pelayo.autoescuela.sim.world.Zone;

/**
 * Ejercicio de principiante: con el motor apagado, arrancar (embrague a
 * fondo, Intro), meter primera y avanzar hasta la zona "finish" sin calar.
 */
public class StartNoStall extends Maneuver {

    private Zone finishZone;
    private boolean engineStarted;
    private boolean moved;

    @Override
    protected void onStart() {
        finishZone = ctx.geometry.zone("finish");
        engineStarted = false;
        moved = false;
    }

    @Override
    protected void onUpdate(VehicleState s, float dt) {
        if (s.events.contains(VehicleEvent.ENGINE_START, true)) {
            engineStarted = true;
        }
        if (s.engineRunning) {
            engineStarted = true;
        }
        if (s.forwardSpeed > 0.5f) {
            moved = true;
        }
        if (finishZone.contains(s.position)) {
            metric("stalls", String.valueOf(stalls));
            finish();
        }
    }

    @Override
    public String getInstruction() {
        if (!engineStarted) {
            return ctx.i18n.t("maneuver.startNoStall.start");
        }
        if (!moved) {
            return ctx.i18n.t("maneuver.startNoStall.moveOff");
        }
        return ctx.i18n.t("maneuver.startNoStall.drive");
    }

    @Override
    public String getStatus() {
        return stalls > 0 ? ctx.i18n.t("maneuver.stalls", stalls) : "";
    }
}
