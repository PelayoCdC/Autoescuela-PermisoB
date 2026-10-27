package com.pelayo.autoescuela.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.pelayo.autoescuela.HeadlessTestApp;
import com.pelayo.autoescuela.data.ScenarioDef;
import com.pelayo.autoescuela.data.VehicleSpec;
import com.pelayo.autoescuela.sim.eval.ManeuverRegistry;
import com.pelayo.autoescuela.sim.vehicle.BulletVehicle;
import com.pelayo.autoescuela.sim.world.Scenario;
import com.pelayo.autoescuela.sim.world.ScenarioLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Todos los escenarios del índice cargan, tienen maniobra conocida y el coche arranca sobre asfalto. */
class ScenarioLoadTest {

    private static HeadlessApplication app;

    @BeforeAll
    static void boot() {
        app = HeadlessTestApp.start();
        Bullet.init();
    }

    @AfterAll
    static void shutdown() {
        app.exit();
    }

    @Test
    void everyScenarioLoadsAndCarStartsOnRoad() {
        VehicleSpec spec = VehicleSpec.loadDefault();
        for (String id : ScenarioDef.listIds()) {
            ScenarioDef def = ScenarioDef.load(id);
            assertEquals(id, def.id, "el id del JSON debe coincidir con el nombre del fichero");
            assertNotNull(def.name, id + ": sin nombre");
            assertNotNull(ManeuverRegistry.create(def.maneuver), id + ": maniobra desconocida");

            PhysicsWorld physics = new PhysicsWorld();
            Scenario scenario = ScenarioLoader.load(def, physics, null);
            BulletVehicle car = new BulletVehicle(physics, spec, scenario.startPose);
            for (int i = 0; i < 120; i++) {
                physics.step();
            }
            Vector3 pos = car.getPosition(new Vector3());
            float groundY = 0f;
            Vector3 contact = new Vector3();
            for (int w = 0; w < 4; w++) {
                assertTrue(car.isWheelInContact(w), id + ": la rueda " + w + " está en el aire");
                assertEquals(BodyTag.ROAD, car.getWheelGround(w), id + ": la rueda " + w + " no empieza sobre asfalto");
                groundY += car.getWheelContactPoint(w, contact).y / 4f;
            }
            float height = pos.y - groundY;
            assertTrue(height > 0.2f && height < 0.6f, id + ": el coche no se ha asentado (altura=" + height + ")");
            car.dispose();
            scenario.dispose();
            physics.dispose();
        }
    }
}
