package com.pelayo.autoescuela.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.pelayo.autoescuela.HeadlessTestApp;
import com.pelayo.autoescuela.sim.eval.EvaluationResult;
import com.pelayo.autoescuela.sim.eval.Fault;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Examen por recorrido (exam_route) con el coche sintético: indicaciones, STOP, intermitente, velocidad e itinerario. */
class RouteTest {

    private static HeadlessApplication app;

    @BeforeAll
    static void boot() {
        app = HeadlessTestApp.start();
    }

    @AfterAll
    static void shutdown() {
        app.exit();
    }

    private static boolean has(EvaluationResult r, String code) {
        for (Fault f : r.faults) {
            if (f.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    /** Hasta la zona del STOP a ritmo legal, parada de 1,5 s y cruce. */
    private void throughStop(SyntheticCar car) {
        car.drive(7f, (70f - car.z) / 7f);
        car.stop(1.5f);
        car.drive(6f, (100f - car.z) / 6f);
    }

    @Test
    void cleanRunFollowsAllInstructionsAndFinishesWithoutFaults() {
        SyntheticCar car = new SyntheticCar("exam_route");
        assertTrue(car.maneuver.getInstruction().startsWith("Inicie la marcha"));
        throughStop(car);
        assertTrue(car.maneuver.getInstruction().contains("glorieta"), car.maneuver.getInstruction());
        // Por la glorieta con el intermitente derecho antes de salir
        car.s.indicatorRight = true;
        car.teleport(7f, 167f, 90f);
        car.teleport(26f, 161.75f, 90f);
        car.s.indicatorRight = false;
        assertTrue(car.maneuver.getInstruction().contains("deténgase"), car.maneuver.getInstruction());
        car.teleport(70f, 161.75f, 90f);
        car.stop(2.5f);
        assertTrue(car.maneuver.isFinished());
        EvaluationResult r = car.maneuver.getResult();
        assertTrue(r.passed, "sin faltas: " + r.faults);
        assertEquals(0, r.faults.size, r.faults.toString());
    }

    @Test
    void goingThroughTheStopWithoutStoppingIsEliminatory() {
        SyntheticCar car = new SyntheticCar("exam_route");
        car.drive(7f, (100f - car.z) / 7f);
        assertTrue(has(car.maneuver.getResult(), "stopNotRespected"));
    }

    @Test
    void speedingOverTheLimitIsAFault() {
        SyntheticCar car = new SyntheticCar("exam_route");
        car.drive(12f, 3f); // 43 km/h con máximo de 30
        assertTrue(has(car.maneuver.getResult(), "speeding"));
        assertFalse(has(car.maneuver.getResult(), "speedingSevere"), "43 km/h no es exceso grave (>50)");
    }

    @Test
    void exitingTheRoundaboutWithoutIndicatorAndWrongStreetsAreFaults() {
        SyntheticCar car = new SyntheticCar("exam_route");
        car.drive(7f, (70f - car.z) / 7f);
        car.stop(1.5f);
        car.teleport(30f, 80f, 90f); // se mete en la calle B: no es el itinerario
        assertTrue(has(car.maneuver.getResult(), "wrongRoute"));
        car.teleport(-1.75f, 100f, 0f);
        car.teleport(7f, 167f, 90f);
        car.teleport(26f, 161.75f, 90f);
        assertTrue(has(car.maneuver.getResult(), "routeSignal"), "salir sin intermitente");
    }
}
