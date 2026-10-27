package com.pelayo.autoescuela.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.pelayo.autoescuela.HeadlessTestApp;
import com.pelayo.autoescuela.data.VehicleSpec;
import com.pelayo.autoescuela.sim.vehicle.BulletVehicle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** El chasis del coche avisa al tocar un bordillo alto. */
class ContactRouterTest {

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
    void chassisHittingCurbIsReported() {
        PhysicsWorld physics = new PhysicsWorld();
        VehicleSpec spec = VehicleSpec.loadDefault();
        physics.addStatic(new btBoxShape(new Vector3(100f, 0.1f, 100f)),
            new Matrix4().setToTranslation(0f, -0.1f, 0f), BodyTag.ROAD, 0.9f);
        // Un "bordillo" alto como un muro justo delante del coche
        physics.addStatic(new btBoxShape(new Vector3(2f, 0.5f, 0.2f)),
            new Matrix4().setToTranslation(0f, 0.5f, 4f), BodyTag.CURB, 0.8f);
        BulletVehicle car = new BulletVehicle(physics, spec, new Matrix4().setToTranslation(0f, 0.9f, 0f));

        boolean reported = false;
        for (int i = 0; i < 600 && !reported; i++) {
            car.setDriveForce(BulletVehicle.WHEEL_FL, 1500f);
            car.setDriveForce(BulletVehicle.WHEEL_FR, 1500f);
            physics.step();
            for (ContactRouter.Hit hit : physics.contacts.getHits()) {
                if (hit.tag == BodyTag.CURB) {
                    reported = true;
                }
            }
        }
        assertTrue(reported, "el contacto con el bordillo debería llegar al ContactRouter");
        car.dispose();
        physics.dispose();
    }
}
