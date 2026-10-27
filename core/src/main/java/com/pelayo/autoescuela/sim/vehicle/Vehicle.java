package com.pelayo.autoescuela.sim.vehicle;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.pelayo.autoescuela.data.VehicleSpec;
import com.pelayo.autoescuela.input.DriverInput;
import com.pelayo.autoescuela.input.DriverInputState;
import com.pelayo.autoescuela.sim.PhysicsWorld;

/**
 * El coche completo: lee los mandos, hace avanzar la transmisión y la capa
 * bullet un paso fijo y publica un {@link VehicleState}.
 */
public class Vehicle implements Disposable {

    private final PhysicsWorld physics;
    private final VehicleSpec spec;
    private final BulletVehicle body;
    private final Drivetrain drivetrain;
    private final DriverInputState input = new DriverInputState();
    private final VehicleState state = new VehicleState();

    private boolean handbrake = true;
    private boolean indicatorLeft;
    private boolean indicatorRight;
    private final Vector3 force = new Vector3();
    /** Velocidad residual (m/s) que se considera coche parado mientras se frena. */
    private static final float BRAKED_CREEP = 0.25f;

    public Vehicle(PhysicsWorld physics, VehicleSpec spec, Matrix4 startTransform) {
        this.physics = physics;
        this.spec = spec;
        this.body = new BulletVehicle(physics, spec, startTransform);
        this.drivetrain = new Drivetrain(spec);
        drivetrain.resetRunning();
    }

    /** Un paso fijo: mandos → transmisión → fuerzas → bullet → estado. */
    public void step(DriverInput driver, float dt) {
        input.clearPulses();
        driver.poll(input, dt);
        state.events.clear();

        if (input.handbrakeToggle) {
            handbrake = !handbrake;
        }
        if (input.indicatorLeft) {
            indicatorLeft = !indicatorLeft;
            indicatorRight = false;
        }
        if (input.indicatorRight) {
            indicatorRight = !indicatorRight;
            indicatorLeft = false;
        }

        // Dirección: el mando pide el ángulo; las ruedas no giran al instante
        float targetAngle = input.steer * spec.steering.maxAngleDeg * MathUtils.degreesToRadians;
        float maxRate = spec.steering.returnDegPerSec * 1.5f * MathUtils.degreesToRadians * dt;
        state.steerAngle += MathUtils.clamp(targetAngle - state.steerAngle, -maxRate, maxRate);
        body.setSteering(state.steerAngle);

        // Transmisión → par en ruedas → fuerza de tracción en las motrices
        float forwardSpeed = body.getForwardSpeed();
        float wheelTorque = drivetrain.step(input, forwardSpeed, dt, state.events);
        float driveForce = wheelTorque / spec.wheelRadius;
        int driven = spec.gearbox.drivenWheels.length;
        for (int i = 0; i < 4; i++) {
            body.setDriveForce(i, 0f);
        }
        for (int w : spec.gearbox.drivenWheels) {
            body.setDriveForce(w, driveForce / driven);
        }

        // Frenos de pedal (reparto delante/detrás) y freno de mano (traseras)
        float pedal = input.brake * spec.brakes.maxForce;
        float front = pedal * spec.brakes.frontBias;
        float rear = pedal * (1f - spec.brakes.frontBias) + (handbrake ? spec.brakes.handbrakeForce : 0f);
        body.setBrake(BulletVehicle.WHEEL_FL, front);
        body.setBrake(BulletVehicle.WHEEL_FR, front);
        body.setBrake(BulletVehicle.WHEEL_RL, rear);
        body.setBrake(BulletVehicle.WHEEL_RR, rear);

        // Resistencias: rodadura (constante) y aerodinámica (v²), contra la velocidad
        Vector3 velocity = body.getChassis().getLinearVelocity();
        float speed = velocity.len();
        if (speed > 0.05f) {
            float rolling = spec.brakes.rollingResistance * spec.mass * 9.81f;
            float aero = 0.5f * 1.225f * spec.brakes.dragArea * speed * speed;
            force.set(velocity).scl(-(rolling + aero) / speed);
            body.applyCentralForce(force);
        }

        physics.step();
        readState(dt);
    }

    private void readState(float dt) {
        state.time += dt;
        body.getTransform(state.transform);
        state.transform.getTranslation(state.position);
        body.getForward(state.forward);
        state.yawDeg = MathUtils.atan2(state.forward.x, state.forward.z) * MathUtils.radiansToDegrees;
        // btRaycastVehicle aplica los impulsos de suspensión y freno después de integrar, así que la
        // velocidad leída lleva un residuo que el paso siguiente cancela: con el coche frenado y quieto
        // marca entre 0,08 y 0,15 m/s y nunca llegaría a "parado". Con los frenos actuando, se toma por cero.
        state.forwardSpeed = body.getForwardSpeed();
        boolean braking = input.brake > 0.05f || handbrake;
        if (braking && Math.abs(state.forwardSpeed) < BRAKED_CREEP) {
            state.forwardSpeed = 0f;
        }
        state.speedKmh = Math.abs(state.forwardSpeed) * 3.6f;

        state.steer = input.steer;
        state.throttle = input.throttle;
        state.brake = input.brake;
        state.clutch = input.clutch;
        state.handbrake = handbrake;
        state.gear = drivetrain.gearbox.getGear();
        state.engineRunning = drivetrain.engine.isRunning();
        state.engineStarting = drivetrain.engine.isStarting();
        state.rpm = drivetrain.engine.getRpm();
        state.clutchSlip = drivetrain.clutch.getSlip();
        state.clutchLocked = drivetrain.clutch.isLocked();
        state.wheelTorque = drivetrain.getWheelTorque();
        state.indicatorLeft = indicatorLeft;
        state.indicatorRight = indicatorRight;
        state.lookYaw = input.lookYaw;

        for (int i = 0; i < 4; i++) {
            state.wheelInContact[i] = body.isWheelInContact(i);
            state.wheelGround[i] = body.getWheelGround(i);
            body.getWheelContactPoint(i, state.wheelContact[i]);
        }
        state.contacts.clear();
        for (com.pelayo.autoescuela.sim.ContactRouter.Hit hit : physics.contacts.getHits()) {
            state.contacts.add(hit.tag);
        }
    }

    /**
     * Deja que la suspensión se asiente antes de empezar: el coche aparece unos centímetros
     * por encima del suelo y, sin esto, cae y rebota durante el primer segundo (parece que se mueve).
     */
    public void settle(float seconds) {
        DriverInput idle = (out, dt) -> { };
        int steps = Math.round(seconds / PhysicsWorld.DT);
        for (int i = 0; i < steps; i++) {
            step(idle, PhysicsWorld.DT);
        }
        state.events.clear();
        state.time = 0f;
    }

    /** Motor apagado al inicio (ejercicios de arranque). */
    public void setEngineOff() {
        drivetrain.resetOff();
    }

    public VehicleState getState() {
        return state;
    }

    public DriverInputState getLastInput() {
        return input;
    }

    public BulletVehicle getBody() {
        return body;
    }

    public Drivetrain getDrivetrain() {
        return drivetrain;
    }

    public VehicleSpec getSpec() {
        return spec;
    }

    /** Recoloca el coche parado, con freno de mano, en punto muerto y el motor al ralentí. */
    public void reset(Matrix4 transform) {
        body.reset(transform);
        handbrake = true;
        indicatorLeft = indicatorRight = false;
        drivetrain.resetRunning();
        state.steerAngle = 0f;
        state.events.clear();
    }

    @Override
    public void dispose() {
        body.dispose();
    }
}
