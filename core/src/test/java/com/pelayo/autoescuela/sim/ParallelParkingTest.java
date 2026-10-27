package com.pelayo.autoescuela.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.math.MathUtils;
import com.pelayo.autoescuela.HeadlessTestApp;
import com.pelayo.autoescuela.app.I18n;
import com.pelayo.autoescuela.data.ScenarioDef;
import com.pelayo.autoescuela.data.VehicleSpec;
import com.pelayo.autoescuela.sim.eval.EvaluationResult;
import com.pelayo.autoescuela.sim.eval.FaultSeverity;
import com.pelayo.autoescuela.sim.eval.ManeuverContext;
import com.pelayo.autoescuela.sim.eval.maneuvers.ParallelParking;
import com.pelayo.autoescuela.sim.vehicle.VehicleEvent;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import com.pelayo.autoescuela.sim.world.ScenarioGeometry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Evaluador de I.1 con telemetría sintética: se teletransporta un coche
 * ideal (sin física) por el escenario real y se comprueban las faltas.
 */
class ParallelParkingTest {

    private static final float DT = PhysicsWorld.DT;
    private static HeadlessApplication app;

    private ScenarioDef def;
    private ParallelParking maneuver;
    private VehicleSpec spec;
    private final VehicleState s = new VehicleState();
    private float x, z, yaw;

    @BeforeAll
    static void boot() {
        app = HeadlessTestApp.start();
    }

    @AfterAll
    static void shutdown() {
        app.exit();
    }

    @BeforeEach
    void setUp() {
        def = ScenarioDef.load("i1_parallel_basic");
        spec = VehicleSpec.loadDefault();
        maneuver = new ParallelParking();
        maneuver.start(new ManeuverContext(new ScenarioGeometry(def), null, spec, new I18n("es")));
        x = def.startPose.x;
        z = def.startPose.z;
        yaw = 0f;
        s.handbrake = false;
        s.engineRunning = true;
        for (int i = 0; i < 4; i++) {
            s.wheelInContact[i] = true;
            s.wheelGround[i] = BodyTag.ROAD;
        }
    }

    /** Coloca el coche ideal y rellena el estado como lo haría la física. */
    private void place(float speed) {
        s.position.set(x, 0.3f, z);
        s.yawDeg = yaw;
        s.forwardSpeed = speed;
        s.speedKmh = Math.abs(speed) * 3.6f;
        float cos = MathUtils.cosDeg(yaw);
        float sin = MathUtils.sinDeg(yaw);
        float halfTrack = spec.track / 2f;
        float front = spec.wheelBase / 2f - spec.comOffsetZ;
        float rear = -spec.wheelBase / 2f - spec.comOffsetZ;
        float[][] local = {{halfTrack, front}, {-halfTrack, front}, {halfTrack, rear}, {-halfTrack, rear}};
        for (int i = 0; i < 4; i++) {
            float lx = local[i][0];
            float lz = local[i][1];
            s.wheelContact[i].set(x + lx * cos + lz * sin, 0f, z - lx * sin + lz * cos);
        }
        s.events.clear();
        s.contacts.clear();
    }

    /** Avanza en línea recta a esa velocidad durante un tiempo (negativa = atrás). */
    private void drive(float speed, float seconds) {
        int steps = Math.round(seconds / DT);
        for (int i = 0; i < steps; i++) {
            z += speed * MathUtils.cosDeg(yaw) * DT;
            x += speed * MathUtils.sinDeg(yaw) * DT;
            place(speed);
            maneuver.update(s, DT);
        }
    }

    private void stop(float seconds) {
        drive(0f, seconds);
    }

    /** Teletransporta el coche a la posición final de aparcamiento, lo para y pone el freno de mano. */
    private void parkAt(float px, float pz, float pyaw) {
        x = px;
        z = pz;
        yaw = pyaw;
        drive(-0.5f, 0.5f); // último tramo marcha atrás hasta la posición
        x = px;
        z = pz;
        s.handbrake = true;
        stop(1.5f);
    }

    @Test
    void perfectParkingPasses() {
        drive(3f, 8f);          // aproximación: pasa el hueco
        drive(-1f, 1f);         // empieza a retroceder (movimiento 1)
        parkAt(-3.95f, 6.25f, 0f); // ruedas derechas a ~18 cm del bordillo (x = -5)
        assertFalse(maneuver.isFinished());
        s.handbrake = false;
        drive(2f, 12f);         // sale hacia delante hasta exit_done
        assertTrue(maneuver.isFinished(), "debería terminar al llegar a la zona de salida");
        EvaluationResult r = maneuver.getResult();
        assertTrue(r.passed, "faltas: " + r.faults);
        assertEquals(0, r.faults.size);
    }

    @Test
    void tooManyMovementsIsDeficient() {
        drive(3f, 8f);
        drive(-1f, 1f);  // 1
        drive(1f, 0.5f); // 2
        drive(-1f, 0.5f); // 3
        drive(1f, 0.5f); // 4 → falta
        parkAt(-3.95f, 6.25f, 0f);
        assertTrue(maneuver.getResult().has("movements"));
        assertEquals(FaultSeverity.DEFICIENTE, maneuver.getResult().faults.first().severity);
    }

    @Test
    void farFromCurbIsMinorOrDeficient() {
        drive(3f, 8f);
        drive(-1f, 1f);
        parkAt(-3.6f, 6.25f, 0f); // ruedas a ~53 cm
        EvaluationResult r = maneuver.getResult();
        assertTrue(r.has("curbDistanceFar") || r.has("curbDistance"), "faltas: " + r.faults);
    }

    @Test
    void notParallelIsMinor() {
        drive(3f, 8f);
        drive(-1f, 1f);
        parkAt(-3.95f, 6.25f, 8f);
        assertTrue(maneuver.getResult().has("alignment"), "faltas: " + maneuver.getResult().faults);
    }

    @Test
    void touchingCurbIsDeficientAndClimbingIsEliminatory() {
        drive(3f, 8f);
        drive(-1f, 1f);
        s.contacts.add(BodyTag.CURB);
        maneuver.update(s, DT);
        assertTrue(maneuver.getResult().has("curbTouch"));
        assertEquals(FaultSeverity.DEFICIENTE, maneuver.getResult().faults.first().severity);
        s.contacts.clear();
        s.wheelGround[3] = BodyTag.CURB;
        maneuver.update(s, DT);
        assertTrue(maneuver.getResult().has("curbClimb"));
        assertFalse(com.pelayo.autoescuela.sim.eval.Verdict.passes(maneuver.getResult()));
    }

    @Test
    void stallsAreMinorThenDeficient() {
        drive(3f, 2f);
        for (int i = 0; i < 3; i++) {
            place(0f);
            s.events.add(VehicleEvent.STALL);
            maneuver.update(s, DT);
        }
        EvaluationResult r = maneuver.getResult();
        assertEquals(2, r.count(FaultSeverity.LEVE));
        assertTrue(r.has("stallRepeated"));
    }

    @Test
    void abortGivesPartialResult() {
        drive(3f, 2f);
        EvaluationResult r = maneuver.abort();
        assertFalse(r.completed);
        assertFalse(r.passed);
    }
}
