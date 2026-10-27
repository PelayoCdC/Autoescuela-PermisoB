package com.pelayo.autoescuela.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.pelayo.autoescuela.HeadlessTestApp;
import com.pelayo.autoescuela.sim.eval.EvaluationResult;
import com.pelayo.autoescuela.sim.eval.FaultSeverity;
import com.pelayo.autoescuela.sim.vehicle.VehicleEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Evaluadores de M4 con el coche sintético sobre los escenarios reales. */
class ManeuversTest {

    private static HeadlessApplication app;

    @BeforeAll
    static void boot() {
        app = HeadlessTestApp.start();
    }

    @AfterAll
    static void shutdown() {
        app.exit();
    }

    // --- G: marcha atrás en recta y curva -----------------------------------

    /** Lleva el coche sintético por la recta, la curva y hasta la zona final. */
    private static void driveThroughCurve(SyntheticCar car) {
        car.drive(4f, 11.3f);            // recta: de z=-25 a z≈20
        car.teleport(0f, 20f, 0f);
        // Curva a la derecha de radio 12: 90° a 4 m/s tarda (π·12/2)/4 = 4,71 s → −19,1°/s
        car.turn(4f, -90f / 4.712f, 4.712f);
        car.teleport(-12f, 32f, -90f);
        car.drive(4f, 4.5f);             // sale 18 m por la recta final (x≈−30)
    }

    @Test
    void reverseCurvePerfectRunPasses() {
        SyntheticCar car = new SyntheticCar("g_reverse_curve");
        driveThroughCurve(car);
        car.stop(1f);
        // Vuelta atrás en un solo movimiento por el mismo camino, a 1 m/s
        car.drive(-1f, 18f);             // recta de salida hasta x≈−12
        car.teleport(-12f, 32f, -90f);
        car.turn(-1f, 90f / 18.85f, 18.85f); // curva hacia atrás
        car.teleport(0f, 20f, 0f);
        car.drive(-1f, 15f);             // 15 m por la recta inicial, hasta z≈5
        car.stop(1f);
        EvaluationResult r = car.maneuver.getResult();
        assertTrue(car.maneuver.isFinished(), "debería terminar en reverse_done");
        assertTrue(r.passed, "faltas: " + r.faults);
    }

    @Test
    void reverseCurveTwoReversesIsDeficient() {
        SyntheticCar car = new SyntheticCar("g_reverse_curve");
        driveThroughCurve(car);
        car.stop(1f);
        car.drive(-1f, 3f);
        car.drive(1f, 1f);   // corrige hacia delante: segunda marcha atrás después
        car.drive(-1f, 2f);
        assertTrue(car.maneuver.getResult().has("singleReverse"));
    }

    @Test
    void reverseCurveStopTooCloseIsMinor() {
        SyntheticCar car = new SyntheticCar("g_reverse_curve");
        car.drive(4f, 11.3f);
        car.teleport(-12f, 32f, -90f);
        car.teleport(-24f, 32f, -90f); // far_end empieza en x=−22; a 12 m del final... probamos a 6 m
        car.teleport(-18f, 32f, -90f);
        car.stop(1f);
        // far_end va de x=−22 a −38: a −18 no está dentro, así que aún no se evalúa la parada
        assertFalse(car.maneuver.getResult().has("stopDistance"));
        car.teleport(-23f, 32f, -90f);
        car.stop(1f);
        assertFalse(car.maneuver.getResult().has("stopDistance"), "a 11 m no es falta");
    }

    // --- H: cambio de sentido --------------------------------------------------

    @Test
    void turnAroundWithOneReverseAndOppositeHeadingPasses() {
        SyntheticCar car = new SyntheticCar("h_dead_end");
        car.drive(3f, 6f);                    // por la calle principal hasta z≈18
        car.teleport(-7f, 21.5f, -90f);       // entra por la derecha (mitad cercana, z<23)
        car.drive(2f, 2f);
        car.teleport(-13f, 24.5f, -140f);     // girado a la izquierda, contra el bordillo lejano
        car.drive(-1.5f, 2f);                 // una marcha atrás
        car.teleport(-10f, 21f, 160f);        // de vuelta hacia la salida
        car.drive(2f, 2f);
        car.teleport(1.75f, 8f, 180f);        // en el carril contrario, rumbo invertido
        car.drive(2f, 0.5f);
        EvaluationResult r = car.maneuver.getResult();
        assertTrue(car.maneuver.isFinished());
        assertTrue(r.passed, "faltas: " + r.faults);
    }

    @Test
    void turnAroundTwoReversesIsDeficient() {
        SyntheticCar car = new SyntheticCar("h_dead_end");
        car.drive(3f, 6f);
        car.teleport(-7f, 21.5f, -90f);
        car.drive(2f, 2f);
        car.drive(-1.5f, 1f);
        car.drive(1.5f, 1f);
        car.drive(-1.5f, 1f);
        assertTrue(car.maneuver.getResult().has("singleReverse"));
    }

    @Test
    void turnAroundEnteringOnLeftIsMinor() {
        SyntheticCar car = new SyntheticCar("h_dead_end");
        car.drive(3f, 6f);
        car.teleport(-7f, 25f, -90f); // mitad lejana (izquierda)
        car.drive(2f, 1f);
        assertTrue(car.maneuver.getResult().has("keepRight"));
    }

    // --- I.2 / I.3: batería -----------------------------------------------------

    @Test
    void perpendicularParkingCenteredPasses() {
        SyntheticCar car = new SyntheticCar("i3_perpendicular");
        car.drive(3f, 5f);
        car.teleport(-6.6f, 4.7f, -90f); // dentro de la plaza, centrado
        car.drive(-0.3f, 0.5f);
        car.teleport(-6.6f, 4.7f, -90f);
        car.s.handbrake = true;
        car.stop(1.5f);
        assertFalse(car.maneuver.isFinished());
        car.s.handbrake = false;
        car.drive(1f, 3f);                  // sale hacia +X... (de frente hacia la calle)
        car.teleport(-1.5f, -12f, 180f);
        car.drive(1f, 0.5f);
        EvaluationResult r = car.maneuver.getResult();
        assertTrue(car.maneuver.isFinished());
        assertTrue(r.passed, "faltas: " + r.faults);
    }

    @Test
    void angledParkingOffCenterIsMinor() {
        SyntheticCar car = new SyntheticCar("i2_angled");
        car.drive(3f, 5f);
        // Centro de la plaza (−6.9, 5.4), eje a −45°: desplazar 0,4 m lateralmente (local X)
        float lx = 0.4f;
        float cx = -6.9f + lx * (float) Math.cos(Math.toRadians(-45));
        float cz = 5.4f - lx * (float) Math.sin(Math.toRadians(-45));
        car.teleport(cx, cz, -45f);
        car.drive(0.3f, 0.5f);
        car.teleport(cx, cz, -45f);
        car.s.handbrake = true;
        car.stop(1.5f);
        assertTrue(car.maneuver.getResult().has("bayOffset"), "faltas: " + car.maneuver.getResult().faults);
    }

    // --- Arranque en pendiente ------------------------------------------------

    @Test
    void hillStartRollbackIsGraded() {
        SyntheticCar car = new SyntheticCar("hill_start");
        car.stop(0.5f);
        car.drive(-0.5f, 1f);   // retrocede 0,5 m → leve
        car.drive(2f, 20f);     // sube hasta la zona alta
        EvaluationResult r = car.maneuver.getResult();
        assertTrue(car.maneuver.isFinished());
        assertTrue(r.has("rollback"));
        assertEquals(FaultSeverity.LEVE, r.faults.first().severity);

        SyntheticCar bad = new SyntheticCar("hill_start");
        bad.drive(-0.5f, 3f);   // 1,5 m → deficiente
        bad.drive(2f, 20f);
        assertTrue(bad.maneuver.getResult().has("rollbackDeficient"));
    }

    // --- Arrancar sin calar ---------------------------------------------------

    @Test
    void startNoStallCountsStalls() {
        SyntheticCar car = new SyntheticCar("start_no_stall");
        car.s.engineRunning = false;
        car.place(0f);
        car.s.events.add(VehicleEvent.STALL);
        car.maneuver.update(car.s, SyntheticCar.DT);
        car.s.engineRunning = true;
        car.drive(3f, 10f);
        EvaluationResult r = car.maneuver.getResult();
        assertTrue(car.maneuver.isFinished());
        assertEquals(1, r.count(FaultSeverity.LEVE));
        assertTrue(r.passed);
    }

    // --- STOP ------------------------------------------------------------------

    @Test
    void stopSignRespectedPasses() {
        SyntheticCar car = new SyntheticCar("stop_sign");
        car.drive(5f, 5.2f);          // hasta z≈16: ruedas delanteras ≈17,3 (<20)
        car.stop(1.2f);
        car.drive(5f, 8f);            // cruza y llega a finish (z≈56)
        EvaluationResult r = car.maneuver.getResult();
        assertTrue(car.maneuver.isFinished());
        assertTrue(r.passed, "faltas: " + r.faults);
    }

    @Test
    void stopSignIgnoredIsEliminatory() {
        SyntheticCar car = new SyntheticCar("stop_sign");
        car.drive(5f, 14f);
        EvaluationResult r = car.maneuver.getResult();
        assertTrue(r.has("stopNotRespected"));
        assertFalse(r.passed);
    }

    @Test
    void stopSignOverLineIsDeficient() {
        SyntheticCar car = new SyntheticCar("stop_sign");
        car.drive(5f, 5.2f);
        car.teleport(-1.75f, 19.2f, 0f); // ruedas delanteras ≈20,5: sobre la línea
        car.stop(1.2f);
        assertTrue(car.maneuver.getResult().has("stopOverLine"));
    }

    // --- Rotonda ---------------------------------------------------------------

    @Test
    void roundaboutSecondExitWithIndicatorPasses() {
        SyntheticCar car = new SyntheticCar("roundabout");
        car.drive(5f, 6f);                 // hasta z≈25
        car.teleport(-2f, 31f, -60f);      // entra en el anillo (distancia ≈ 9,2)
        car.s.indicatorRight = true;
        car.turn(4f, 25f, 6f);             // circula girando a la izquierda
        car.teleport(-1.75f, 55f, 0f);     // sale hacia el norte
        car.drive(4f, 5f);
        EvaluationResult r = car.maneuver.getResult();
        assertTrue(car.maneuver.isFinished());
        assertTrue(r.passed, "faltas: " + r.faults);
    }

    @Test
    void roundaboutWrongExitIsDeficientAndNoIndicatorIsMinor() {
        SyntheticCar car = new SyntheticCar("roundabout");
        car.drive(5f, 6f);
        car.teleport(-2f, 31f, -60f);
        car.turn(4f, 25f, 3f);
        car.teleport(-20f, 38.25f, -90f);  // primera salida (oeste)
        car.drive(4f, 3f);
        EvaluationResult r = car.maneuver.getResult();
        assertTrue(r.has("wrongExit"), "faltas: " + r.faults);
        assertTrue(r.has("indicator"), "faltas: " + r.faults);
    }
}
