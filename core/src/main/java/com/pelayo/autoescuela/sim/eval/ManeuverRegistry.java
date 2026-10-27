package com.pelayo.autoescuela.sim.eval;

import com.pelayo.autoescuela.sim.eval.maneuvers.BayParking;
import com.pelayo.autoescuela.sim.eval.maneuvers.Crosswalk;
import com.pelayo.autoescuela.sim.eval.maneuvers.FreeDrive;
import com.pelayo.autoescuela.sim.eval.maneuvers.HillStart;
import com.pelayo.autoescuela.sim.eval.maneuvers.ParallelParking;
import com.pelayo.autoescuela.sim.eval.maneuvers.ReverseStraightCurve;
import com.pelayo.autoescuela.sim.eval.maneuvers.Roundabout;
import com.pelayo.autoescuela.sim.eval.maneuvers.StartNoStall;
import com.pelayo.autoescuela.sim.eval.maneuvers.StopSign;
import com.pelayo.autoescuela.sim.eval.maneuvers.TurnAroundDeadEnd;
import com.pelayo.autoescuela.sim.eval.maneuvers.Tutorial;

/** Crea el evaluador que pide un escenario por su nombre de maniobra. */
public final class ManeuverRegistry {

    private ManeuverRegistry() {
    }

    public static Maneuver create(String name) {
        switch (name) {
            case "FREE_DRIVE": return new FreeDrive();
            case "PARALLEL_PARKING": return new ParallelParking();
            case "BAY_PARKING": return new BayParking();
            case "REVERSE_CURVE": return new ReverseStraightCurve();
            case "TURN_AROUND": return new TurnAroundDeadEnd();
            case "HILL_START": return new HillStart();
            case "START_NO_STALL": return new StartNoStall();
            case "STOP_SIGN": return new StopSign();
            case "ROUNDABOUT": return new Roundabout();
            case "CROSSWALK": return new Crosswalk();
            case "ROUTE": return new com.pelayo.autoescuela.sim.eval.maneuvers.Route();
            case "TUTORIAL": return new Tutorial();
            default: throw new IllegalArgumentException("Maniobra desconocida: " + name);
        }
    }
}
