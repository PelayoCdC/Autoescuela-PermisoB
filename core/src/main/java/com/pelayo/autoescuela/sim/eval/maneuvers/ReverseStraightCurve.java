package com.pelayo.autoescuela.sim.eval.maneuvers;

import com.pelayo.autoescuela.sim.eval.FaultSeverity;
import com.pelayo.autoescuela.sim.eval.Maneuver;
import com.pelayo.autoescuela.sim.eval.probes.MovementCounter;
import com.pelayo.autoescuela.sim.eval.probes.SpeedStats;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import com.pelayo.autoescuela.sim.world.Marking;
import com.pelayo.autoescuela.sim.world.Zone;

/**
 * Maniobra G de la DGT: marcha atrás en recta y curva. Se avanza por la
 * recta y la curva hasta la zona "far_end", se para a ≥ 10 m del final de la
 * curva, y se retrocede en un solo movimiento, a velocidad uniforme y centrado
 * en el carril (guía "axis"), hasta la zona "reverse_done".
 *
 * Zonas: far_end, reverse_done. Guía: axis. Zona "curve_end": punto de
 * referencia para medir la distancia de parada (su centro).
 */
public class ReverseStraightCurve extends Maneuver {

    private enum Phase { APPROACH, STOPPED, REVERSING, DONE }

    private Phase phase;
    private Zone farEnd;
    private Zone reverseDone;
    private Zone curveEnd;
    private Marking axis;
    private final MovementCounter movements = new MovementCounter();
    private final SpeedStats speed = new SpeedStats();
    private float stopTimer;
    private float maxOffset;

    @Override
    protected void onStart() {
        phase = Phase.APPROACH;
        farEnd = ctx.geometry.zone("far_end");
        reverseDone = ctx.geometry.zone("reverse_done");
        curveEnd = ctx.geometry.zone("curve_end");
        axis = ctx.geometry.guide("axis"); // comprueba que existe; el centrado usa todas las guías
        movements.reset();
        speed.reset();
        stopTimer = 0f;
        maxOffset = 0f;
    }

    @Override
    protected void onUpdate(VehicleState s, float dt) {
        switch (phase) {
            case APPROACH:
                if (farEnd.contains(s.position) && isStopped(s)) {
                    stopTimer += dt;
                    if (stopTimer >= 0.5f) {
                        float d = (float) Math.hypot(s.position.x - curveEnd.cx, s.position.z - curveEnd.cz);
                        metric("stopDistance", String.format("%.1f m (mín. %.0f)", d, ctx.geometry.def.params.stopDistance));
                        if (d < ctx.geometry.def.params.stopDistance) {
                            fault("stopDistance", FaultSeverity.LEVE, (int) ctx.geometry.def.params.stopDistance);
                        }
                        phase = Phase.STOPPED;
                    }
                } else {
                    stopTimer = 0f;
                }
                break;
            case STOPPED:
                if (s.forwardSpeed < -0.1f) {
                    phase = Phase.REVERSING;
                    movements.reset();
                    speed.reset();
                    movements.update(s.forwardSpeed, dt);
                }
                break;
            case REVERSING:
                movements.update(s.forwardSpeed, dt);
                speed.update(s.forwardSpeed);
                if (movements.getMovements() > 1) {
                    faultOnce("singleReverse", FaultSeverity.DEFICIENTE);
                }
                float offset = Float.MAX_VALUE;
                for (Marking guide : ctx.geometry.guides.values()) {
                    offset = Math.min(offset, guide.distanceToLine(s.position.x, s.position.z));
                }
                maxOffset = Math.max(maxOffset, offset);
                if (offset > ctx.geometry.def.params.maxCentering) {
                    faultOnce("centering", FaultSeverity.LEVE);
                }
                for (int i = 0; i < 4; i++) {
                    if (ctx.geometry.isOnLineMarking(s.wheelContact[i].x, s.wheelContact[i].z)) {
                        faultOnce("lineCrossed", FaultSeverity.DEFICIENTE);
                    }
                }
                if (reverseDone.contains(s.position) && isStopped(s)) {
                    stopTimer += dt;
                    if (stopTimer >= 0.5f) {
                        metric("speedUniformity", String.format("±%.2f m/s (media %.1f)", speed.stdDev(), speed.mean()));
                        metric("centering", String.format("%.2f m", maxOffset));
                        if (speed.stdDev() > ctx.geometry.def.params.maxSpeedStdDev) {
                            fault("uniformSpeed", FaultSeverity.LEVE);
                        }
                        phase = Phase.DONE;
                        finish();
                    }
                } else {
                    stopTimer = 0f;
                }
                break;
            default:
                break;
        }
    }

    @Override
    public String getInstruction() {
        switch (phase) {
            case APPROACH: return ctx.i18n.t("maneuver.reverseCurve.approach", (int) ctx.geometry.def.params.stopDistance);
            case STOPPED: return ctx.i18n.t("maneuver.reverseCurve.stopped");
            case REVERSING: return ctx.i18n.t("maneuver.reverseCurve.reversing");
            default: return ctx.i18n.t("maneuver.done");
        }
    }

    @Override
    public String getStatus() {
        return phase == Phase.REVERSING ? ctx.i18n.t("maneuver.movements", movements.getMovements(), 1) : "";
    }
}
