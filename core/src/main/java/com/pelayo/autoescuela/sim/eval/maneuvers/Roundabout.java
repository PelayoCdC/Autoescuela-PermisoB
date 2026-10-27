package com.pelayo.autoescuela.sim.eval.maneuvers;

import com.pelayo.autoescuela.sim.eval.FaultSeverity;
import com.pelayo.autoescuela.sim.eval.Maneuver;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import com.pelayo.autoescuela.sim.world.Zone;

/**
 * Rotonda: entrar, circular con la isleta a la izquierda y tomar la salida
 * indicada ("exit_target"), señalizando con el intermitente derecho antes de
 * salir. Salir por otra salida ("exit_wrong*") es deficiente; no señalizar,
 * leve. La zona "ring" (círculo aproximado por su radio en size[0]/2) marca
 * la calzada anular.
 */
public class Roundabout extends Maneuver {

    private Zone ring;
    private Zone target;
    private final com.badlogic.gdx.utils.Array<Zone> wrong = new com.badlogic.gdx.utils.Array<>();
    private boolean inRing;
    private boolean everInRing;
    private boolean indicatorSeen;
    private boolean exitEvaluated;
    private float outerRadius;
    private float innerRadius;

    @Override
    protected void onStart() {
        ring = ctx.geometry.zone("ring");
        target = ctx.geometry.zone("exit_target");
        wrong.clear();
        for (Zone z : ctx.geometry.zones.values()) {
            if (z.name.startsWith("exit_wrong")) {
                wrong.add(z);
            }
        }
        outerRadius = ring.halfX;
        innerRadius = ring.halfZ; // size: [diámetro exterior, diámetro interior]
        inRing = false;
        everInRing = false;
        indicatorSeen = false;
        exitEvaluated = false;
    }

    @Override
    protected void onUpdate(VehicleState s, float dt) {
        float d = (float) Math.hypot(s.position.x - ring.cx, s.position.z - ring.cz);
        boolean nowInRing = d < outerRadius && d > innerRadius;
        if (nowInRing) {
            everInRing = true;
            if (s.indicatorRight) {
                indicatorSeen = true;
            }
        }
        if (everInRing && !exitEvaluated && d >= outerRadius) {
            // Saliendo de la rotonda: ¿iba el intermitente derecho?
            exitEvaluated = true;
            if (!indicatorSeen && !s.indicatorRight) {
                faultOnce("indicator", FaultSeverity.LEVE);
            }
        }
        inRing = nowInRing;
        for (Zone z : wrong) {
            if (z.contains(s.position)) {
                fault("wrongExit", FaultSeverity.DEFICIENTE);
                finish();
                return;
            }
        }
        if (target.contains(s.position)) {
            if (!everInRing) {
                fault("skippedRing", FaultSeverity.ELIMINATORIA);
            }
            finish();
        }
    }

    @Override
    public String getInstruction() {
        if (!everInRing) {
            return ctx.i18n.t("maneuver.roundabout.enter");
        }
        return ctx.i18n.t("maneuver.roundabout.exit");
    }
}
