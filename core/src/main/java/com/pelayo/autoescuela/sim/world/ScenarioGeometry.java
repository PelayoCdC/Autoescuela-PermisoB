package com.pelayo.autoescuela.sim.world;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.pelayo.autoescuela.data.ScenarioDef;

/**
 * La parte puramente geométrica de un escenario (zonas, bordillos y marcas),
 * sin bullet ni render. Es lo único que necesitan los evaluadores, así se
 * pueden probar con telemetría sintética.
 */
public class ScenarioGeometry {

    public final ScenarioDef def;
    public final ObjectMap<String, Zone> zones = new ObjectMap<>();
    public final Array<CurbLine> curbs = new Array<>();
    public final Array<Marking> markings = new Array<>();
    public final ObjectMap<String, Marking> bays = new ObjectMap<>();

    public ScenarioGeometry(ScenarioDef def) {
        this.def = def;
        for (ScenarioDef.ZoneDef z : def.zones) {
            zones.put(z.name, new Zone(z.name, z.center[0], z.center[1], z.size[0], z.size[1], z.yawDeg));
        }
        for (ScenarioDef.CurbDef c : def.curbs) {
            curbs.add(new CurbLine(c.name, c.resolvedPoints()));
        }
        for (ScenarioDef.MarkingDef m : def.markings) {
            Marking marking = new Marking(m);
            markings.add(marking);
            if (marking.kind == Marking.Kind.BAY && m.name != null) {
                bays.put(m.name, marking);
            }
            if (marking.kind == Marking.Kind.GUIDE && m.name != null) {
                guides.put(m.name, marking);
            }
        }
    }

    /** Ejes guía invisibles (p.ej. el centro del carril en la maniobra G). */
    public final ObjectMap<String, Marking> guides = new ObjectMap<>();

    public Marking guide(String name) {
        Marking m = guides.get(name);
        if (m == null) {
            throw new IllegalStateException("El escenario " + def.id + " no tiene la guía '" + name + "'");
        }
        return m;
    }

    /** Zona por nombre; lanza si falta (el JSON está mal). */
    public Zone zone(String name) {
        Zone z = zones.get(name);
        if (z == null) {
            throw new IllegalStateException("El escenario " + def.id + " no tiene la zona '" + name + "'");
        }
        return z;
    }

    public Marking bay(String name) {
        Marking m = bays.get(name);
        if (m == null) {
            throw new IllegalStateException("El escenario " + def.id + " no tiene la plaza '" + name + "'");
        }
        return m;
    }

    /** Distancia al bordillo más cercano (m), o +inf si no hay bordillos. */
    public float nearestCurbDistance(float x, float z) {
        float best = Float.MAX_VALUE;
        for (CurbLine c : curbs) {
            best = Math.min(best, c.distanceTo(x, z));
        }
        return best;
    }

    /** Alguna marca de línea pintada (LANE o STOP) bajo el punto. */
    public boolean isOnLineMarking(float x, float z) {
        for (Marking m : markings) {
            if ((m.kind == Marking.Kind.LANE || m.kind == Marking.Kind.STOP) && m.isOn(x, z)) {
                return true;
            }
        }
        return false;
    }
}
