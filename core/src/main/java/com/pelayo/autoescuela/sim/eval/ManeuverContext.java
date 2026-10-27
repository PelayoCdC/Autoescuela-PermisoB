package com.pelayo.autoescuela.sim.eval;

import com.pelayo.autoescuela.app.I18n;
import com.pelayo.autoescuela.data.VehicleSpec;
import com.pelayo.autoescuela.sim.world.Scenario;
import com.pelayo.autoescuela.sim.world.ScenarioGeometry;

/** Lo que necesita una maniobra para evaluar: geometría, coche y textos. */
public class ManeuverContext {

    public final ScenarioGeometry geometry;
    /** Escenario con física (null en tests con telemetría sintética). */
    public final Scenario scenario;
    public final VehicleSpec spec;
    public final I18n i18n;

    public ManeuverContext(ScenarioGeometry geometry, Scenario scenario, VehicleSpec spec, I18n i18n) {
        this.geometry = geometry;
        this.scenario = scenario;
        this.spec = spec;
        this.i18n = i18n;
    }

    /** Algún cono u objeto móvil desplazado (false si no hay escenario físico). */
    public boolean anyMovableDisplaced() {
        return scenario != null && scenario.anyMovableDisplaced();
    }
}
