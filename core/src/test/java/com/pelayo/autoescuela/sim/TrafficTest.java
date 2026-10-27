package com.pelayo.autoescuela.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.pelayo.autoescuela.HeadlessTestApp;
import com.pelayo.autoescuela.data.ScenarioDef;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import com.pelayo.autoescuela.sim.world.Scenario;
import com.pelayo.autoescuela.sim.world.ScenarioLoader;
import com.pelayo.autoescuela.sim.world.TrafficAgent;
import com.pelayo.autoescuela.sim.world.TrafficSystem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tráfico con un "jugador" colocado a mano (sin coche físico): los coches paran
 * ante él, le apuntan no ceder el paso si les corta, y los peatones arrancan al
 * acercarse y le apuntan no cederles el paso si cruza por encima.
 */
class TrafficTest {

    private static HeadlessApplication app;
    private PhysicsWorld physics;
    private Scenario scenario;
    private final VehicleState player = new VehicleState();
    private int forced;
    private int pedestrian;

    @BeforeAll
    static void boot() {
        app = HeadlessTestApp.start();
        Bullet.init();
    }

    @AfterAll
    static void shutdown() {
        app.exit();
    }

    @AfterEach
    void tearDown() {
        if (scenario != null) {
            scenario.dispose();
        }
        if (physics != null) {
            physics.dispose();
        }
    }

    private TrafficSystem load(String id) {
        physics = new PhysicsWorld();
        scenario = ScenarioLoader.load(ScenarioDef.load(id), physics, null);
        scenario.traffic.setPlayer(player);
        forced = 0;
        pedestrian = 0;
        return scenario.traffic;
    }

    /** El jugador quieto o moviéndose en línea recta a esa velocidad (m/s) por el eje z. */
    private void place(float x, float z, float yawDeg, float speed) {
        player.position.set(x, 0.6f, z);
        player.forward.set((float) Math.sin(Math.toRadians(yawDeg)), 0f, (float) Math.cos(Math.toRadians(yawDeg)));
        player.forwardSpeed = speed;
    }

    private void run(float seconds, boolean movePlayer) {
        int steps = Math.round(seconds / PhysicsWorld.DT);
        for (int i = 0; i < steps; i++) {
            if (movePlayer) {
                player.position.mulAdd(player.forward, player.forwardSpeed * PhysicsWorld.DT);
            }
            physics.step();
            for (TrafficSystem.Event e : scenario.traffic.events) {
                if (e == TrafficSystem.Event.FORCED_BRAKE) {
                    forced++;
                } else {
                    pedestrian++;
                }
            }
        }
    }

    @Test
    void roundaboutCarsKeepCirclingOnTheRing() {
        TrafficSystem t = load("roundabout");
        place(-1.75f, -5f, 0f, 0f); // en la entrada sur, lejos
        assertEquals(2, t.agents.size);
        run(12f, false);
        for (TrafficAgent a : t.agents) {
            float r = (float) Math.hypot(a.x, a.z - 40f);
            assertEquals(10f, r, 0.6f, "sigue en el anillo");
        }
        assertEquals(0, forced);
    }

    @Test
    void crossTrafficStopsForACarStoppedInItsLaneWithoutFault() {
        TrafficSystem t = load("stop_sign");
        // El jugador parado en medio del cruce, ocupando los dos carriles
        place(-1.75f, 24.5f, 0f, 0f);
        run(25f, false);
        for (TrafficAgent a : t.agents) {
            float dx = Math.abs(a.x - (-1.75f));
            assertTrue(dx > 2.5f, "ningún coche atraviesa al jugador: x=" + a.x);
        }
        assertEquals(0, forced, "parado no obliga a nadie a frenar fuerte (le esperan)");
    }

    @Test
    void cuttingInFrontOfAnApproachingCarIsNotYielding() {
        TrafficSystem t = load("stop_sign");
        place(-1.75f, 10f, 0f, 0f);
        // Esperar a que un coche del carril norte (z = 26,25, hacia +x) esté a unos 12 m del cruce y meterse
        TrafficAgent target = null;
        for (int i = 0; i < 4000 && target == null; i++) {
            run(PhysicsWorld.DT, false);
            for (TrafficAgent a : t.agents) {
                if (a.z > 25f && a.x > -16f && a.x < -12f) {
                    target = a;
                }
            }
        }
        assertTrue(target != null, "llega un coche");
        place(-1.75f, 21f, 0f, 5f); // el jugador se mete en el cruce a 18 km/h
        run(1.5f, true);
        assertTrue(forced >= 1, "el coche ha tenido que frenar fuerte: no ha cedido el paso");
    }

    @Test
    void pedestriansStartWhenTheCarApproachesAndPassingThemIsAFault() {
        TrafficSystem t = load("crosswalk");
        place(-1.75f, -10f, 0f, 0f);
        run(3f, false);
        for (TrafficAgent a : t.agents) {
            if (a.type == TrafficAgent.Type.PEDESTRIAN) {
                assertTrue(Math.abs(a.x) > 4.5f, "esperan en la acera mientras el coche está lejos");
            }
        }
        // El coche se acerca y para antes del paso: empiezan a cruzar
        place(-1.75f, 30f, 0f, 0f);
        run(4f, false);
        boolean crossing = false;
        for (TrafficAgent a : t.agents) {
            crossing |= a.type == TrafficAgent.Type.PEDESTRIAN && Math.abs(a.x) < 4.5f;
        }
        assertTrue(crossing, "con el coche cerca, cruzan");
        assertEquals(0, pedestrian, "parado antes del paso, ninguna falta");
        // Ahora pasa por el paso sin parar mientras cruzan
        place(-1.75f, 33f, 0f, 6f);
        run(2f, true);
        assertTrue(pedestrian >= 1, "pasar con peatones en el paso es no cederles el paso");
        assertFalse(pedestrian > 2, "una falta por peatón y cruce, no una por paso");
    }
}
