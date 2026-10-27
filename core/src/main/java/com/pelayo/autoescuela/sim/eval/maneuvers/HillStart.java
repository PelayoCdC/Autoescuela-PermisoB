package com.pelayo.autoescuela.sim.eval.maneuvers;

import com.pelayo.autoescuela.sim.eval.FaultSeverity;
import com.pelayo.autoescuela.sim.eval.Maneuver;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import com.pelayo.autoescuela.sim.world.Zone;

/**
 * Arranque en pendiente: el coche empieza parado cuesta arriba con el freno de
 * mano. Hay que arrancar sin calar y sin retroceder (más de 30 cm leve, más
 * de 1 m deficiente) hasta la zona "top". La zona "track" da el eje de la
 * cuesta (Z local hacia arriba).
 */
public class HillStart extends Maneuver {

    private Zone track;
    private Zone top;
    private float startZ;
    private float minZ;
    private boolean started;

    @Override
    protected void onStart() {
        track = ctx.geometry.zone("track");
        top = ctx.geometry.zone("top");
        started = false;
    }

    @Override
    protected void onUpdate(VehicleState s, float dt) {
        float z = track.localZ(s.position.x, s.position.z);
        if (!started) {
            startZ = z;
            minZ = z;
            started = true;
        }
        minZ = Math.min(minZ, z);
        float rollback = startZ - minZ;
        if (rollback > ctx.geometry.def.params.rollbackDeficient) {
            faultOnce("rollbackDeficient", FaultSeverity.DEFICIENTE, (int) (rollback * 100f));
        } else if (rollback > ctx.geometry.def.params.rollbackMinor) {
            faultOnce("rollback", FaultSeverity.LEVE, (int) (rollback * 100f));
        }
        if (top.contains(s.position)) {
            metric("rollback", String.format("%.0f cm", rollback * 100f));
            finish();
        }
    }

    @Override
    public String getInstruction() {
        return ctx.i18n.t("maneuver.hill.instruction");
    }

    @Override
    public String getStatus() {
        if (!started) {
            return "";
        }
        return ctx.i18n.t("maneuver.hill.rollback", (int) ((startZ - minZ) * 100f));
    }
}
