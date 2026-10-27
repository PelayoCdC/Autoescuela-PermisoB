package com.pelayo.autoescuela.sim.eval.maneuvers;

import com.badlogic.gdx.utils.Array;
import com.pelayo.autoescuela.data.ScenarioDef;
import com.pelayo.autoescuela.sim.eval.FaultSeverity;
import com.pelayo.autoescuela.sim.eval.Maneuver;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import com.pelayo.autoescuela.sim.world.Zone;

/**
 * Examen práctico por un recorrido: el examinador va dando indicaciones
 * ({@code route} del escenario) y se pasa a la siguiente al llegar a la zona
 * de cada una. En cada tramo se vigila la velocidad máxima y, si el paso lo
 * pide, detenerse en un STOP, poner el intermitente antes de girar o salir y
 * no meterse en una calle que no es. El último paso termina al parar en su
 * zona. Las faltas de tráfico (ceder el paso, peatones, choques) son las comunes.
 */
public class Route extends Maneuver {

    /** Margen sobre la velocidad máxima antes de anotar exceso (km/h) y exceso grave. */
    private static final float SPEED_MARGIN = 5f;
    private static final float SPEED_SEVERE = 20f;
    private static final float SPEED_HOLD = 2f;
    private static final float STOP_HOLD = 1f;
    private static final float PARK_HOLD = 2f;

    private Array<ScenarioDef.RouteStepDef> steps;
    private int step;
    private float overTimer;
    private float stopTimer;
    private boolean stopDone;
    private boolean stopChecked;
    private boolean inSignalZone;
    private boolean signalSeen;
    private boolean signalChecked;
    private float parkTimer;
    private final Array<Zone> targets = new Array<>();

    @Override
    protected void onStart() {
        steps = ctx.geometry.def.route;
        step = 0;
        enterStep();
    }

    private ScenarioDef.RouteStepDef current() {
        return steps.get(step);
    }

    private void enterStep() {
        overTimer = 0f;
        stopTimer = 0f;
        stopDone = false;
        stopChecked = false;
        inSignalZone = false;
        signalSeen = false;
        signalChecked = false;
        parkTimer = 0f;
        resetOnce("speeding");
        targets.clear();
        targets.add(ctx.geometry.zone(current().zone));
    }

    @Override
    protected void onUpdate(VehicleState s, float dt) {
        ScenarioDef.RouteStepDef st = current();

        // Velocidad máxima del tramo
        if (s.speedKmh > st.speedLimit + SPEED_SEVERE) {
            faultOnce("speedingSevere", FaultSeverity.ELIMINATORIA, Math.round(st.speedLimit));
        }
        if (s.speedKmh > st.speedLimit + SPEED_MARGIN) {
            overTimer += dt;
            if (overTimer > SPEED_HOLD) {
                faultOnce("speeding", FaultSeverity.DEFICIENTE, Math.round(st.speedLimit));
            }
        } else {
            overTimer = 0f;
        }

        // STOP: parar del todo dentro de la zona antes de pasar su borde delantero
        if (st.stopZone != null && !stopChecked) {
            Zone z = ctx.geometry.zone(st.stopZone);
            if (z.contains(s.position) && isStopped(s)) {
                stopTimer += dt;
                stopDone |= stopTimer >= STOP_HOLD;
            } else {
                stopTimer = 0f;
            }
            if (z.localZ(s.position.x, s.position.z) > z.halfZ) {
                stopChecked = true;
                if (!stopDone) {
                    fault("stopNotRespected", FaultSeverity.ELIMINATORIA);
                }
            }
        }

        // Intermitente puesto en algún momento dentro de la zona donde hay que señalizar
        if (st.signal != null && st.signalZone != null && !signalChecked) {
            boolean inside = ctx.geometry.zone(st.signalZone).contains(s.position);
            if (inside) {
                inSignalZone = true;
                signalSeen |= "right".equals(st.signal) ? s.indicatorRight : s.indicatorLeft;
            } else if (inSignalZone) {
                signalChecked = true;
                if (!signalSeen) {
                    fault("routeSignal", FaultSeverity.DEFICIENTE);
                }
            }
        }

        // Calles que no son las del itinerario
        if (st.wrong != null) {
            for (String name : st.wrong) {
                if (ctx.geometry.zone(name).contains(s.position)) {
                    faultOnce("wrongRoute", FaultSeverity.LEVE);
                }
            }
        }

        // Llegar a la zona del paso: el siguiente (o, en el último, parar dentro para terminar)
        if (ctx.geometry.zone(st.zone).contains(s.position)) {
            if (st.park) {
                if (isStopped(s)) {
                    parkTimer += dt;
                    if (parkTimer >= PARK_HOLD) {
                        metric("routeSteps", String.valueOf(steps.size));
                        finish();
                    }
                } else {
                    parkTimer = 0f;
                }
            } else if (step < steps.size - 1) {
                // Si no llegó a salir de la zona del intermitente, se mira ahora
                if (st.signal != null && inSignalZone && !signalChecked && !signalSeen) {
                    fault("routeSignal", FaultSeverity.DEFICIENTE);
                }
                step++;
                enterStep();
            }
        }
    }

    @Override
    public String getInstruction() {
        return steps.get(step).say;
    }

    @Override
    public String getStatus() {
        return ctx.i18n.t("maneuver.route.status", step + 1, steps.size, Math.round(current().speedLimit));
    }

    @Override
    public Array<Zone> getTargetZones() {
        return targets;
    }
}
