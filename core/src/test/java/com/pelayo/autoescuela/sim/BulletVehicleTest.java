package com.pelayo.autoescuela.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.pelayo.autoescuela.HeadlessTestApp;
import com.pelayo.autoescuela.data.VehicleSpec;
import com.pelayo.autoescuela.sim.vehicle.BulletVehicle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** La capa bullet del coche sobre suelo plano y en pendiente, sin ventana. */
class BulletVehicleTest {

    private static HeadlessApplication app;
    private PhysicsWorld physics;
    private VehicleSpec spec;
    private BulletVehicle car;
    private final Vector3 pos = new Vector3();

    @BeforeAll
    static void boot() {
        app = HeadlessTestApp.start();
        Bullet.init();
    }

    @AfterAll
    static void shutdown() {
        app.exit();
    }

    @BeforeEach
    void setUp() {
        physics = new PhysicsWorld();
        spec = VehicleSpec.loadDefault();
    }

    @AfterEach
    void tearDown() {
        if (car != null) {
            car.dispose();
            car = null;
        }
        physics.dispose();
    }

    private void flatGround() {
        physics.addStatic(new btBoxShape(new Vector3(100f, 0.1f, 100f)),
            new Matrix4().setToTranslation(0f, -0.1f, 0f), BodyTag.ROAD, 0.9f);
    }

    /** Rampa del 10 % subiendo hacia +Z, centrada en el origen. */
    private Matrix4 slopeGround() {
        float grade = (float) Math.atan(0.10);
        Matrix4 ramp = new Matrix4().setToTranslation(0f, -0.1f, 0f)
            .rotate(Vector3.X, -grade * MathUtils.radiansToDegrees);
        physics.addStatic(new btBoxShape(new Vector3(20f, 0.1f, 50f)), ramp, BodyTag.ROAD, 0.9f);
        // Coche alineado con la pendiente, un poco por encima
        return new Matrix4().setToTranslation(0f, 0.9f, 0f)
            .rotate(Vector3.X, -grade * MathUtils.radiansToDegrees);
    }

    private void settle(int steps) {
        for (int i = 0; i < steps; i++) {
            physics.step();
        }
    }

    @Test
    void restsOnFlatGroundWithoutDrifting() {
        flatGround();
        car = new BulletVehicle(physics, spec, new Matrix4().setToTranslation(0f, 0.9f, 0f));
        settle(120); // 1 s para asentar la suspensión
        Vector3 start = car.getPosition(new Vector3());
        settle(600); // 5 s sin frenos: puede rodar unos centímetros por la caída inicial
        car.getPosition(pos);
        float drift = (float) Math.hypot(pos.x - start.x, pos.z - start.z);
        assertTrue(drift < 0.10f, "deriva sin frenos: " + drift + " m");
        assertTrue(pos.y > 0.2f && pos.y < 0.6f, "altura del centro de masas: " + pos.y);

        // Con el freno de mano puesto no se mueve ni un centímetro
        car.setBrake(BulletVehicle.WHEEL_RL, spec.brakes.handbrakeForce);
        car.setBrake(BulletVehicle.WHEEL_RR, spec.brakes.handbrakeForce);
        settle(120);
        car.getPosition(start);
        settle(600);
        car.getPosition(pos);
        drift = (float) Math.hypot(pos.x - start.x, pos.z - start.z);
        assertTrue(drift < 0.01f, "deriva con freno de mano: " + drift + " m");
        for (int i = 0; i < 4; i++) {
            assertTrue(car.isWheelInContact(i), "rueda " + i + " sin contacto");
            assertTrue(car.getWheelGround(i) == BodyTag.ROAD, "rueda " + i + " pisa " + car.getWheelGround(i));
        }
    }

    @Test
    void driveForceMovesForwardAlongZ() {
        flatGround();
        car = new BulletVehicle(physics, spec, new Matrix4().setToTranslation(0f, 0.9f, 0f));
        settle(120);
        Vector3 start = car.getPosition(new Vector3());
        for (int i = 0; i < 240; i++) { // 2 s con 1500 N por rueda delantera
            car.setDriveForce(BulletVehicle.WHEEL_FL, 1500f);
            car.setDriveForce(BulletVehicle.WHEEL_FR, 1500f);
            physics.step();
        }
        car.getPosition(pos);
        assertTrue(pos.z - start.z > 1.5f, "avance en Z: " + (pos.z - start.z));
        assertTrue(Math.abs(pos.x - start.x) < 0.3f, "desvío lateral: " + (pos.x - start.x));
        assertTrue(car.getForwardSpeed() > 1f, "velocidad: " + car.getForwardSpeed());
    }

    @Test
    void steeringLeftTurnsLeft() {
        flatGround();
        car = new BulletVehicle(physics, spec, new Matrix4().setToTranslation(0f, 0.9f, 0f));
        settle(120);
        car.setSteering(20f * MathUtils.degreesToRadians); // positivo = izquierda = +X
        for (int i = 0; i < 480; i++) {
            car.setDriveForce(BulletVehicle.WHEEL_FL, 1200f);
            car.setDriveForce(BulletVehicle.WHEEL_FR, 1200f);
            physics.step();
        }
        car.getPosition(pos);
        assertTrue(pos.x > 0.5f, "con dirección positiva el coche debe irse a +X (izquierda): x=" + pos.x);
    }

    @Test
    void rollsBackOnSlopeWithoutBrakes() {
        Matrix4 pose = slopeGround();
        car = new BulletVehicle(physics, spec, pose);
        settle(60);
        Vector3 start = car.getPosition(new Vector3());
        settle(360); // 3 s
        car.getPosition(pos);
        assertTrue(pos.z < start.z - 0.5f, "debería retroceder cuesta abajo: dz=" + (pos.z - start.z));
    }

    @Test
    void handbrakeHoldsOnSlope() {
        Matrix4 pose = slopeGround();
        car = new BulletVehicle(physics, spec, pose);
        car.setBrake(BulletVehicle.WHEEL_RL, spec.brakes.handbrakeForce);
        car.setBrake(BulletVehicle.WHEEL_RR, spec.brakes.handbrakeForce);
        settle(60);
        Vector3 start = car.getPosition(new Vector3());
        settle(600); // 5 s
        car.getPosition(pos);
        assertTrue(Math.abs(pos.z - start.z) < 0.05f,
            "el freno de mano debe sujetar en el 10 %: dz=" + (pos.z - start.z));
    }
}
