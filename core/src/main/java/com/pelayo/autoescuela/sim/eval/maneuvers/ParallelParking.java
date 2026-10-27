package com.pelayo.autoescuela.sim.eval.maneuvers;

import com.pelayo.autoescuela.sim.eval.FaultSeverity;
import com.pelayo.autoescuela.sim.eval.Maneuver;
import com.pelayo.autoescuela.sim.eval.probes.MovementCounter;
import com.pelayo.autoescuela.sim.vehicle.BulletVehicle;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import com.pelayo.autoescuela.sim.world.Marking;
import com.pelayo.autoescuela.sim.world.Zone;

/**
 * Maniobra I.1 de la DGT: estacionamiento en línea marcha atrás y salida.
 * Fases: aproximación (hasta empezar a retroceder), aparcamiento (≤ 3
 * movimientos, parado dentro de la plaza con freno de mano, ruedas a ≤ 0,30 m
 * del bordillo y paralelo) y salida (≤ 3 movimientos hasta la zona de salida).
 *
 * Zonas y marcas requeridas en el escenario: plaza "bay" (BAY), zona "finish"
 * (plaza con margen), zona "exit_done" (destino tras salir).
 */
public class ParallelParking extends Maneuver {

    private enum Phase { APPROACH, PARKING, EXIT, DONE }

    /** Tiempo parado dentro de la plaza con el freno de mano para dar por aparcado (s). */
    private static final float PARKED_HOLD = 1.0f;

    private Phase phase;
    private final MovementCounter movements = new MovementCounter();
    private Marking bay;
    private Zone finish;
    private Zone exitDone;
    private float holdTimer;
    private int parkMovements;

    @Override
    protected void onStart() {
        phase = Phase.APPROACH;
        bay = ctx.geometry.bay("bay");
        finish = ctx.geometry.zone("finish");
        exitDone = ctx.geometry.zone("exit_done");
        movements.reset();
        holdTimer = 0f;
    }

    @Override
    protected void onUpdate(VehicleState s, float dt) {
        switch (phase) {
            case APPROACH:
                if (s.forwardSpeed < -0.1f) {
                    phase = Phase.PARKING;
                    movements.reset();
                    movements.update(s.forwardSpeed, dt);
                }
                break;
            case PARKING:
                movements.update(s.forwardSpeed, dt);
                checkMovements(movements.getMovements());
                if (finish.containsAll(s.wheelContact) && isStopped(s) && s.handbrake) {
                    holdTimer += dt;
                    if (holdTimer >= PARKED_HOLD) {
                        evaluateParkedPosition(s);
                        parkMovements = movements.getMovements();
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
                checkMovements(movements.getMovements());
                if (exitDone.contains(s.position) && !finish.containsAny(s.wheelContact)) {
                    metric("exitMovements", String.valueOf(movements.getMovements()));
                    phase = Phase.DONE;
                    finish();
                }
                break;
            default:
                break;
        }
    }

    private void checkMovements(int n) {
        int max = ctx.geometry.def.params.maxMovements;
        if (n > max) {
            faultOnce("movements", FaultSeverity.DEFICIENTE, max);
        }
    }

    /** Distancia al bordillo de las ruedas del lado del bordillo y alineación con la plaza. */
    private void evaluateParkedPosition(VehicleState s) {
        // Las ruedas derechas (lado del bordillo al aparcar a la derecha): FR y RR
        float front = curbDistance(s, BulletVehicle.WHEEL_FR);
        float rear = curbDistance(s, BulletVehicle.WHEEL_RR);
        float worst = Math.max(front, rear);
        float max = ctx.geometry.def.params.maxCurbDistance;
        metric("curbDistance", String.format("%.0f cm (máx. %.0f)", worst * 100f, max * 100f));
        if (worst > max + 0.20f) {
            fault("curbDistanceFar", FaultSeverity.DEFICIENTE, (int) (worst * 100f));
        } else if (worst > max) {
            fault("curbDistance", FaultSeverity.LEVE, (int) (worst * 100f));
        }
        float align = Math.abs(yawDelta(s.yawDeg, bay.bay.yawDeg));
        metric("alignment", String.format("%.1f°", align));
        if (align > ctx.geometry.def.params.maxAlignmentDeg) {
            fault("alignment", FaultSeverity.LEVE, (int) align);
        }
        metric("parkMovements", String.valueOf(movements.getMovements()));
    }

    /** Distancia del flanco exterior del neumático al bordillo más cercano. */
    private float curbDistance(VehicleState s, int wheel) {
        float d = ctx.geometry.nearestCurbDistance(s.wheelContact[wheel].x, s.wheelContact[wheel].z);
        return Math.max(0f, d - ctx.spec.wheelWidth / 2f);
    }

    @Override
    public String getInstruction() {
        switch (phase) {
            case APPROACH: return ctx.i18n.t("maneuver.parallel.approach");
            case PARKING: return ctx.i18n.t("maneuver.parallel.parking", ctx.geometry.def.params.maxMovements);
            case EXIT: return ctx.i18n.t("maneuver.parallel.exit", ctx.geometry.def.params.maxMovements);
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

    public int getParkMovements() {
        return parkMovements;
    }
}
