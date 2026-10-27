package com.pelayo.autoescuela.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.utils.Array;
import com.pelayo.autoescuela.HeadlessTestApp;
import com.pelayo.autoescuela.data.VehicleSpec;
import com.pelayo.autoescuela.input.DriverInputState;
import com.pelayo.autoescuela.sim.vehicle.Drivetrain;
import com.pelayo.autoescuela.sim.vehicle.Gearbox;
import com.pelayo.autoescuela.sim.vehicle.VehicleEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Motor, embrague y caja sobre un modelo longitudinal mínimo del coche (masa
 * puntual con resistencia a la rodadura), sin bullet.
 */
class DrivetrainTest {

    private static final float DT = PhysicsWorld.DT;
    private static HeadlessApplication app;

    private VehicleSpec spec;
    private Drivetrain dt;
    private final DriverInputState in = new DriverInputState();
    private final Array<VehicleEvent> events = new Array<>();
    private float speed;
    private float slopeGrade;
    /** Freno de mano echado: sujeta el coche mientras la fuerza no supere su agarre. */
    private boolean handbrake;

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
        spec = VehicleSpec.loadDefault();
        dt = new Drivetrain(spec);
        dt.resetRunning();
        speed = 0f;
        slopeGrade = 0f;
        handbrake = false;
        in.clearPulses();
        in.throttle = in.brake = in.clutch = 0f;
    }

    /** Un paso: transmisión + masa puntual con rodadura y pendiente. */
    private void step() {
        float wheelTorque = dt.step(in, speed, DT, events);
        in.clearPulses();
        float force = wheelTorque / spec.wheelRadius;
        float rolling = spec.brakes.rollingResistance * spec.mass * 9.81f;
        if (Math.abs(speed) > 0.01f) {
            force -= Math.signum(speed) * rolling;
        } else if (Math.abs(force) < rolling) {
            force = 0f;
        }
        force -= spec.mass * 9.81f * slopeGrade;
        // Freno de pedal: hasta 8 m/s² de deceleración; freno de mano: sujeta hasta 0,3 g
        float brakeForce = in.brake * 8f * spec.mass;
        if (handbrake) {
            brakeForce += 0.3f * 9.81f * spec.mass;
        }
        float next = speed + force / spec.mass * DT;
        float brakeDv = brakeForce / spec.mass * DT;
        if (Math.abs(next) <= brakeDv) {
            speed = 0f;
        } else {
            speed = next - Math.signum(next) * brakeDv;
        }
    }

    /** Mete la marcha con el embrague pisado y espera a que entre. */
    private void engage(int gear) {
        in.clutch = 1f;
        in.gearSelect = gear;
        step();
        run(0.5f);
        assertEquals(gear, dt.gearbox.getGear());
    }

    /** Suelta el embrague de 1 a 0 en ese tiempo. */
    private void releaseClutch(float seconds) {
        int steps = Math.round(seconds / DT);
        for (int i = 0; i < steps; i++) {
            in.clutch = 1f - (i + 1f) / steps;
            step();
        }
    }

    // --- Cuándo se cala y cuándo no ------------------------------------------------------

    @Test
    void jumpingToTheMiddleOfTheBiteBandWithSomeGasMovesOff() {
        engage(1);
        in.throttle = 0.35f;
        // Mitad de la franja de fricción, de una vez: el par entra progresivo y el coche arranca sin calarse
        in.clutch = 1f - (spec.clutch.bitePointLow + spec.clutch.bitePointHigh) / 2f;
        run(2.5f);
        assertFalse(happened(VehicleEvent.STALL), "en la mitad de la franja con algo de gas no debería calar");
        assertTrue(speed > 0.5f, "avanza: v=" + speed);
    }

    @Test
    void jumpingToTheEndOfTheBiteBandAtIdleStalls() {
        engage(1);
        in.clutch = 1f - spec.clutch.bitePointHigh - 0.02f; // casi suelto del todo, sin gas
        run(1.5f);
        assertTrue(happened(VehicleEvent.STALL), "soltar casi del todo de golpe y sin gas cala");
    }

    @Test
    void brakingToAStopInGearWithoutClutchStalls() {
        dt.gearbox.forceGear(2);
        speed = 8f;
        run(0.3f);
        in.brake = 0.6f;
        run(4f);
        assertEquals(0f, speed, 1e-3f);
        assertTrue(happened(VehicleEvent.STALL), "parar con la marcha metida sin pisar el embrague cala");
    }

    @Test
    void brakingToAStopWithClutchPressedKeepsRunning() {
        dt.gearbox.forceGear(2);
        speed = 8f;
        run(0.3f);
        in.brake = 0.6f;
        run(1f);
        in.clutch = 1f; // se pisa el embrague antes de que baje de vueltas
        run(3f);
        assertEquals(0f, speed, 1e-3f);
        assertFalse(happened(VehicleEvent.STALL));
        assertEquals(spec.engine.idleRpm, dt.engine.getRpm(), 60f);
    }

    @Test
    void releasingClutchAgainstHandbrakeStalls() {
        handbrake = true;
        engage(1);
        releaseClutch(2f);
        run(1f);
        assertTrue(happened(VehicleEvent.STALL), "con el freno de mano echado y sin gas, soltar el embrague cala");
        assertEquals(0f, speed, 1e-3f);
    }

    @Test
    void holdingTheBitePointAgainstTheBrakeWithThrottleDoesNotStall() {
        handbrake = true;
        engage(1);
        // Como se enseña: primero un poco de gas para subir de vueltas y luego buscar la fricción
        in.throttle = 0.3f;
        run(0.6f);
        in.clutch = 0.55f; // en la franja de fricción, sujetando el coche como en una cuesta
        run(3f);
        assertFalse(happened(VehicleEvent.STALL), "en el punto de fricción con gas el motor aguanta");
        assertTrue(dt.engine.getRpm() > 1200f, "rpm: " + dt.engine.getRpm());
        // Sin subir antes de vueltas el motor se queda bajo (unas 700 rpm), pero el regulador evita que se cale
        in.throttle = 0f;
        in.clutch = 1f;
        run(1f);
        in.throttle = 0.3f;
        in.clutch = 0.55f;
        run(3f);
        assertFalse(happened(VehicleEvent.STALL));
        assertTrue(dt.engine.getRpm() > spec.engine.stallRpm + 100f, "rpm: " + dt.engine.getRpm());
    }

    @Test
    void startingInThirdStalls() {
        engage(3);
        in.throttle = 0.2f;
        releaseClutch(1.2f);
        run(1.5f);
        assertTrue(happened(VehicleEvent.STALL), "salir en 3ª soltando a ritmo normal cala");
    }

    @Test
    void tooHighGearAtLowSpeedStalls() {
        dt.gearbox.forceGear(4);
        speed = 3f; // 11 km/h en 4ª: unas 330 rpm
        run(1.5f);
        assertTrue(happened(VehicleEvent.STALL), "11 km/h en 4ª no da vueltas suficientes");
    }

    @Test
    void secondGearAtTwentyKmhPullsWithoutStalling() {
        dt.gearbox.forceGear(2);
        speed = 5.5f; // 20 km/h en 2ª: unas 1300 rpm
        in.throttle = 0.3f;
        run(3f);
        assertFalse(happened(VehicleEvent.STALL));
        assertTrue(speed > 5.5f, "acelera: v=" + speed);
    }

    @Test
    void rollingBackwardsInFirstWithClutchReleasedStalls() {
        slopeGrade = 0.10f;
        engage(1);
        run(0.8f); // se va hacia atrás con el embrague pisado
        assertTrue(speed < -0.3f);
        in.clutch = 0f; // soltar sin gas mientras retrocede
        run(1f);
        assertTrue(happened(VehicleEvent.STALL), "soltar el embrague yendo hacia atrás en 1ª cala");
    }

    @Test
    void clutchFullyPressedTransmitsNothing() {
        engage(1);
        in.throttle = 0.6f;
        run(2f);
        assertEquals(0f, speed, 1e-4f);
        assertEquals(0f, dt.clutch.getTorque(), 1e-4f);
        // Por encima de la franja de fricción tampoco: el pedal apenas levantado no mueve el coche
        in.clutch = 1f - spec.clutch.bitePointLow + 0.02f;
        run(1f);
        assertEquals(0f, speed, 1e-4f);
    }

    @Test
    void neutralWithClutchReleasedNeverStalls() {
        handbrake = true;
        in.clutch = 0f;
        run(3f);
        assertFalse(happened(VehicleEvent.STALL));
        assertTrue(dt.engine.isRunning());
    }


    private void run(float seconds) {
        int steps = Math.round(seconds / DT);
        for (int i = 0; i < steps; i++) {
            step();
        }
    }

    private boolean happened(VehicleEvent e) {
        return events.contains(e, true);
    }

    @Test
    void torqueCurveInterpolates() {
        assertEquals(200f, dt.engine.torqueCurve(1900f), 0.01f);
        assertEquals(175f, dt.engine.torqueCurve(1575f), 0.5f);
        assertEquals(0f, dt.engine.torqueCurve(6000f), 0.01f);
    }

    @Test
    void idleSettlesNearIdleRpm() {
        run(2f);
        assertEquals(spec.engine.idleRpm, dt.engine.getRpm(), 40f);
        assertTrue(dt.engine.isRunning());
        assertEquals(0f, speed, 1e-4f);
    }

    @Test
    void throttleInNeutralRevsAndRevLimits() {
        in.throttle = 1f;
        run(3f);
        assertTrue(dt.engine.getRpm() > spec.engine.redlineRpm - 200f, "rpm: " + dt.engine.getRpm());
        assertTrue(dt.engine.getRpm() < spec.engine.revLimitRpm + 300f, "rpm: " + dt.engine.getRpm());
    }

    @Test
    void dumpingClutchInFirstWithoutThrottleStalls() {
        in.clutch = 1f;
        in.gearSelect = 1;
        step();
        run(0.5f); // que termine el cambio
        assertEquals(1, dt.gearbox.getGear());
        in.clutch = 0f; // soltar de golpe
        run(1f);
        assertTrue(happened(VehicleEvent.STALL), "debería calar");
        assertFalse(dt.engine.isRunning());
        assertTrue(speed < 1.5f, "el coche solo da un tirón: v=" + speed);
    }

    @Test
    void progressiveClutchWithSomeThrottleMovesOffWithoutStalling() {
        in.clutch = 1f;
        in.gearSelect = 1;
        step();
        run(0.5f);
        in.throttle = 0.25f;
        float release = 2.0f; // soltar el pedal en 2 s
        int steps = Math.round(release / DT);
        for (int i = 0; i < steps; i++) {
            in.clutch = 1f - (i + 1f) / steps;
            step();
        }
        run(1f);
        assertFalse(happened(VehicleEvent.STALL), "no debería calar");
        assertTrue(dt.engine.isRunning());
        assertTrue(speed > 1f, "debería avanzar: v=" + speed);
        assertTrue(dt.clutch.isLocked(), "el embrague debería acabar bloqueado");
    }

    @Test
    void creepsAtIdleOnFlatWithVerySlowRelease() {
        in.clutch = 1f;
        in.gearSelect = 1;
        step();
        run(0.5f);
        int steps = Math.round(4f / DT);
        for (int i = 0; i < steps; i++) {
            in.clutch = 1f - (i + 1f) / steps;
            step();
        }
        run(2f);
        assertFalse(happened(VehicleEvent.STALL), "a ralentí y soltando muy despacio no cala en llano");
        assertTrue(speed > 0.3f, "avanza a paso de ralentí: v=" + speed);
    }

    @Test
    void shiftingWithoutClutchGrinds() {
        in.clutch = 0.3f;
        in.gearSelect = 1;
        step();
        assertTrue(happened(VehicleEvent.GRIND));
        assertEquals(Gearbox.NEUTRAL, dt.gearbox.getGear());
    }

    @Test
    void reverseRefusedWhileMoving() {
        speed = 2f;
        in.clutch = 1f;
        in.gearSelect = Gearbox.REVERSE;
        step();
        assertTrue(happened(VehicleEvent.REVERSE_REFUSED));
        assertEquals(Gearbox.NEUTRAL, dt.gearbox.getGear());
        speed = 0f;
        in.gearSelect = Gearbox.REVERSE;
        step();
        assertEquals(Gearbox.REVERSE, dt.gearbox.getGear());
    }

    @Test
    void reverseGearDrivesBackwards() {
        in.clutch = 1f;
        in.gearSelect = Gearbox.REVERSE;
        step();
        run(0.5f);
        in.throttle = 0.3f;
        int steps = Math.round(2f / DT);
        for (int i = 0; i < steps; i++) {
            in.clutch = 1f - (i + 1f) / steps;
            step();
        }
        run(1f);
        assertTrue(speed < -0.5f, "marcha atrás: v=" + speed);
        assertFalse(happened(VehicleEvent.STALL));
    }

    @Test
    void engineBrakingSlowsCarAndRpmFollowWheels() {
        dt.gearbox.forceGear(2);
        speed = 8f; // ~29 km/h en 2ª
        in.clutch = 0f;
        in.throttle = 0f;
        run(0.5f);
        float ratio = spec.gearbox.ratios[1] * spec.gearbox.finalDrive;
        float expectedRpm = speed / spec.wheelRadius * ratio * 60f / (float) (2 * Math.PI);
        assertEquals(expectedRpm, dt.engine.getRpm(), 60f);
        float before = speed;
        run(2f);
        assertTrue(speed < before - 0.5f, "el freno motor reduce la velocidad: " + before + " → " + speed);
        assertTrue(dt.engine.isRunning());
    }

    @Test
    void hillStartRollsBackWithoutHandbrakeAndClimbsWithClutchControl() {
        slopeGrade = 0.10f;
        in.clutch = 1f;
        in.gearSelect = 1;
        step();
        run(1f);
        assertTrue(speed < -0.5f, "con el embrague pisado se va hacia atrás: v=" + speed);
        // Ahora gas y soltar el embrague en 1 s: debe frenar el retroceso y subir
        in.throttle = 0.5f;
        int steps = Math.round(1f / DT);
        for (int i = 0; i < steps; i++) {
            in.clutch = 1f - (i + 1f) / steps;
            step();
        }
        run(2f);
        assertFalse(happened(VehicleEvent.STALL), "no debería calar con 50 % de gas");
        assertTrue(speed > 1f, "debería subir la cuesta: v=" + speed);
    }

    @Test
    void ignitionRestartsAfterStallOnlyWithClutch() {
        dt.resetOff();
        in.gearSelect = 1;
        in.clutch = 1f;
        step();
        in.ignition = true;
        step();
        run(1f);
        assertTrue(happened(VehicleEvent.ENGINE_START));
        assertTrue(dt.engine.isRunning());
        assertEquals(spec.engine.idleRpm, dt.engine.getRpm(), 80f);
    }

    @Test
    void ignitionInGearWithoutClutchLurchesAndWarns() {
        dt.resetOff();
        dt.gearbox.forceGear(1);
        in.clutch = 0f;
        in.ignition = true;
        step();
        assertTrue(happened(VehicleEvent.START_IN_GEAR));
        run(0.8f);
        assertTrue(Math.abs(speed) > 0.05f, "el motor de arranque empuja el coche: v=" + speed);
    }
}
