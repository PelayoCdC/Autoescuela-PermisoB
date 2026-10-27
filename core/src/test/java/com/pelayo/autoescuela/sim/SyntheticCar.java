package com.pelayo.autoescuela.sim;

import com.badlogic.gdx.math.MathUtils;
import com.pelayo.autoescuela.app.I18n;
import com.pelayo.autoescuela.data.ScenarioDef;
import com.pelayo.autoescuela.data.VehicleSpec;
import com.pelayo.autoescuela.sim.eval.Maneuver;
import com.pelayo.autoescuela.sim.eval.ManeuverContext;
import com.pelayo.autoescuela.sim.eval.ManeuverRegistry;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import com.pelayo.autoescuela.sim.world.ScenarioGeometry;

/**
 * Coche ideal para probar evaluadores sin física: se mueve por teletransporte
 * y rellena el {@link VehicleState} como lo haría la simulación.
 */
public class SyntheticCar {

    public static final float DT = PhysicsWorld.DT;

    public final ScenarioDef def;
    public final VehicleSpec spec;
    public final Maneuver maneuver;
    public final VehicleState s = new VehicleState();
    public float x, z, yaw;

    public SyntheticCar(String scenarioId) {
        def = ScenarioDef.load(scenarioId);
        spec = VehicleSpec.loadDefault();
        maneuver = ManeuverRegistry.create(def.maneuver);
        maneuver.start(new ManeuverContext(new ScenarioGeometry(def), null, spec, new I18n("es")));
        x = def.startPose.x;
        z = def.startPose.z;
        yaw = def.startPose.yawDeg;
        s.handbrake = false;
        s.engineRunning = true;
        for (int i = 0; i < 4; i++) {
            s.wheelInContact[i] = true;
            s.wheelGround[i] = BodyTag.ROAD;
        }
        place(0f);
    }

    /** Coloca el coche y rellena el estado. */
    public void place(float speed) {
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

    /** Un paso: avanza a esa velocidad (negativa = atrás) con ese giro de rumbo por segundo. */
    public void step(float speed, float yawRateDegPerSec) {
        yaw += yawRateDegPerSec * DT;
        z += speed * MathUtils.cosDeg(yaw) * DT;
        x += speed * MathUtils.sinDeg(yaw) * DT;
        place(speed);
        maneuver.update(s, DT);
    }

    /** Recto durante un tiempo. */
    public void drive(float speed, float seconds) {
        turn(speed, 0f, seconds);
    }

    /** Con giro de rumbo constante durante un tiempo. */
    public void turn(float speed, float yawRateDegPerSec, float seconds) {
        int steps = Math.round(seconds / DT);
        for (int i = 0; i < steps; i++) {
            step(speed, yawRateDegPerSec);
        }
    }

    public void stop(float seconds) {
        drive(0f, seconds);
    }

    /** Teletransporta sin evaluar el trayecto (último paso sí se evalúa). */
    public void teleport(float px, float pz, float pyaw) {
        x = px;
        z = pz;
        yaw = pyaw;
        place(0f);
        maneuver.update(s, DT);
    }
}
