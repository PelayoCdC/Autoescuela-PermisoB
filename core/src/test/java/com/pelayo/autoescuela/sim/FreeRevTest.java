package com.pelayo.autoescuela.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.utils.Array;
import com.pelayo.autoescuela.data.VehicleSpec;
import com.pelayo.autoescuela.input.DriverInputState;
import com.pelayo.autoescuela.sim.vehicle.Drivetrain;
import com.pelayo.autoescuela.sim.vehicle.VehicleEvent;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.pelayo.autoescuela.HeadlessTestApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Motor sin carga (punto muerto): con poco acelerador se estabiliza en pocas
 * vueltas, como un diésel real, en lugar de subir hasta el corte de inyección.
 */
class FreeRevTest {

    private static HeadlessApplication app;

    @BeforeAll
    static void boot() {
        app = HeadlessTestApp.start();
    }

    @AfterAll
    static void shutdown() {
        app.exit();
    }

    private static float revsAt(float throttle) {
        VehicleSpec spec = VehicleSpec.loadDefault();
        Drivetrain d = new Drivetrain(spec);
        d.resetRunning();
        DriverInputState in = new DriverInputState();
        in.throttle = throttle;
        Array<VehicleEvent> events = new Array<>();
        for (int i = 0; i < 120 * 4; i++) {
            in.clearPulses();
            d.step(in, 0f, PhysicsWorld.DT, events);
        }
        return d.engine.getRpm();
    }

    @Test
    void lightThrottleInNeutralSettlesAtModestRevs() {
        float idle = revsAt(0f);
        float light = revsAt(0.1f);
        float quarter = revsAt(0.25f);
        float half = revsAt(0.5f);
        float full = revsAt(1f);
        System.out.printf("Sin carga: 0 %% %.0f, 10 %% %.0f, 25 %% %.0f, 50 %% %.0f, 100 %% %.0f rpm%n",
            idle, light, quarter, half, full);
        assertTrue(Math.abs(idle - 800f) < 80f, "ralentí: " + idle);
        assertTrue(light > 1000f && light < 1700f, "10 % de pedal: " + light);
        assertTrue(quarter > 1500f && quarter < 2400f, "25 % de pedal: " + quarter);
        assertTrue(half > quarter && half < 3400f, "50 % de pedal: " + half);
        assertTrue(full > 4300f, "a fondo llega cerca del corte: " + full);
    }

    @Test
    void halfClutchWithoutThrottleCreepsLikeADiesel() {
        com.badlogic.gdx.physics.bullet.Bullet.init();
        com.pelayo.autoescuela.data.ScenarioDef def = com.pelayo.autoescuela.data.ScenarioDef.load("test_ground");
        PhysicsWorld physics = new PhysicsWorld();
        com.pelayo.autoescuela.sim.world.Scenario sc = com.pelayo.autoescuela.sim.world.ScenarioLoader.load(def, physics, null);
        com.pelayo.autoescuela.sim.vehicle.Vehicle v =
            new com.pelayo.autoescuela.sim.vehicle.Vehicle(physics, VehicleSpec.loadDefault(), sc.startPose);
        v.settle(1f);
        // Embrague a fondo, primera, quitar freno de mano y dejar el embrague en el punto de fricción, sin gas
        com.pelayo.autoescuela.input.ScriptedDriverInput in = new com.pelayo.autoescuela.input.ScriptedDriverInput(
            "0:clutch=1,gear=1|0.5:handbrake|1:clutch=0.55");
        for (int i = 0; i < 120 * 6; i++) {
            v.step(in, PhysicsWorld.DT);
        }
        float kmh = v.getState().speedKmh;
        System.out.printf("Medio embrague sin gas: %.1f km/h a %.0f rpm%n", kmh, v.getState().rpm);
        assertTrue(v.getState().engineRunning, "no debe calarse");
        assertTrue(kmh > 1.5f && kmh < 9f, "debe avanzar despacio: " + kmh);
        v.dispose();
        sc.dispose();
        physics.dispose();
    }
}
