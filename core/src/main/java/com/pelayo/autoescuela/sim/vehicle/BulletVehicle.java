package com.pelayo.autoescuela.sim.vehicle;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.collision.btCompoundShape;
import com.badlogic.gdx.physics.bullet.dynamics.btDefaultVehicleRaycaster;
import com.badlogic.gdx.physics.bullet.dynamics.btRaycastVehicle;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btWheelInfo;
import com.badlogic.gdx.physics.bullet.linearmath.btTransform;
import com.badlogic.gdx.utils.Disposable;
import com.pelayo.autoescuela.data.VehicleSpec;
import com.pelayo.autoescuela.sim.BodyTag;
import com.pelayo.autoescuela.sim.PhysicsWorld;

/**
 * Capa bullet del coche: chasis rígido + {@link btRaycastVehicle} con cuatro
 * ruedas. No sabe nada de motor ni marchas: recibe fuerza de tracción por
 * rueda, freno y ángulo de dirección, y expone transformaciones y contactos.
 *
 * Ejes locales: +Z adelante, +Y arriba, +X izquierda. Ruedas: 0 FL, 1 FR, 2 RL, 3 RR.
 */
public class BulletVehicle implements Disposable {

    public static final int WHEEL_FL = 0;
    public static final int WHEEL_FR = 1;
    public static final int WHEEL_RL = 2;
    public static final int WHEEL_RR = 3;

    /** Estado de activación de bullet que impide que el cuerpo se duerma. */
    private static final int DISABLE_DEACTIVATION = 4;
    /** btTriangleRaycastCallback::kF_UseGjkConvexCastRaytest: rayos exactos contra convexos. */
    private static final int RAY_USE_GJK = 1 << 3;

    private final PhysicsWorld physics;
    private final VehicleSpec spec;

    private final btBoxShape chassisBox;
    private final btCompoundShape chassisShape;
    private final PhysicsWorld.DefaultMotionState motionState;
    private final btRigidBody chassis;
    private final btDefaultVehicleRaycaster raycaster;
    private final btRaycastVehicle.btVehicleTuning tuning;
    private final btRaycastVehicle vehicle;
    /** Rayo propio bajo cada rueda: btRaycastVehicle no dice qué objeto pisa. */
    private final ClosestRayResultCallback groundRay;
    private final Vector3 rayFrom = new Vector3();
    private final Vector3 rayTo = new Vector3();

    private final float[] glMatrix = new float[16];
    private final Matrix4 tmpM = new Matrix4();
    private final Vector3 tmpV = new Vector3();
    private final Vector3 tmpV2 = new Vector3();

    public BulletVehicle(PhysicsWorld physics, VehicleSpec spec, Matrix4 startTransform) {
        this.physics = physics;
        this.spec = spec;

        // Caja del chasis desplazada hacia arriba para que el origen del cuerpo
        // (centro de masas) quede bajo y el coche no vuelque en las curvas. La
        // caja empieza en los bajos (groundClearance), por encima del hundimiento
        // estático de la suspensión, para que nunca roce el asfalto.
        float sag = 9.81f / (4f * spec.suspension.stiffness); // compresión en reposo (m)
        float bottomY = spec.groundClearance + sag - spec.comHeight; // bajos respecto al CdM
        float boxHeight = spec.height - spec.groundClearance;
        chassisBox = new btBoxShape(new Vector3(spec.width / 2f, boxHeight / 2f, spec.length / 2f));
        chassisShape = new btCompoundShape();
        chassisShape.addChildShape(new Matrix4().setToTranslation(0f, bottomY + boxHeight / 2f, 0f), chassisBox);

        Vector3 inertia = new Vector3();
        chassisShape.calculateLocalInertia(spec.mass, inertia);
        motionState = new PhysicsWorld.DefaultMotionState(startTransform);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(spec.mass, motionState, chassisShape, inertia);
        info.setFriction(0.6f);
        chassis = new btRigidBody(info);
        info.dispose();
        chassis.setActivationState(DISABLE_DEACTIVATION);
        // Para que bullet avise de los contactos del chasis (bordillos, conos, coches)
        chassis.setCollisionFlags(chassis.getCollisionFlags()
            | btCollisionObject.CollisionFlags.CF_CUSTOM_MATERIAL_CALLBACK);
        physics.register(chassis, BodyTag.PLAYER_CHASSIS);
        physics.world.addRigidBody(chassis, PhysicsWorld.GROUP_PLAYER, PhysicsWorld.MASK_ALL);

        groundRay = new ClosestRayResultCallback(Vector3.Zero, Vector3.Zero);
        groundRay.setCollisionFilterGroup(PhysicsWorld.GROUP_WORLD);
        groundRay.setCollisionFilterMask(PhysicsWorld.GROUP_WORLD); // nunca el propio chasis
        groundRay.setFlags(RAY_USE_GJK); // raycast preciso contra cajas grandes

        raycaster = new btDefaultVehicleRaycaster(physics.world);
        tuning = new btRaycastVehicle.btVehicleTuning();
        vehicle = new btRaycastVehicle(tuning, chassis, raycaster);
        vehicle.setCoordinateSystem(0, 1, 2); // X derecha/izquierda, Y arriba, Z adelante
        physics.world.addVehicle(vehicle);

        addWheels();
    }

    private void addWheels() {
        VehicleSpec.Suspension s = spec.suspension;
        // Punto de anclaje: centro de la rueda en reposo más la longitud del
        // amortiguador, relativo al centro de masas.
        float anchorY = spec.wheelRadius + s.restLength - spec.comHeight;
        float halfTrack = spec.track / 2f;
        float frontZ = spec.wheelBase / 2f - spec.comOffsetZ;
        float rearZ = -spec.wheelBase / 2f - spec.comOffsetZ;
        Vector3 direction = new Vector3(0f, -1f, 0f);
        Vector3 axle = new Vector3(-1f, 0f, 0f);

        addWheel(new Vector3(halfTrack, anchorY, frontZ), direction, axle, true);
        addWheel(new Vector3(-halfTrack, anchorY, frontZ), direction, axle, true);
        addWheel(new Vector3(halfTrack, anchorY, rearZ), direction, axle, false);
        addWheel(new Vector3(-halfTrack, anchorY, rearZ), direction, axle, false);
    }

    private void addWheel(Vector3 anchor, Vector3 direction, Vector3 axle, boolean front) {
        VehicleSpec.Suspension s = spec.suspension;
        btWheelInfo wheel = vehicle.addWheel(anchor, direction, axle, s.restLength,
            spec.wheelRadius, tuning, front);
        wheel.setSuspensionStiffness(s.stiffness);
        wheel.setWheelsDampingCompression(s.dampingCompression);
        wheel.setWheelsDampingRelaxation(s.dampingRelaxation);
        wheel.setFrictionSlip(s.frictionSlip);
        wheel.setRollInfluence(s.rollInfluence);
        wheel.setMaxSuspensionTravelCm(s.maxTravelCm);
        wheel.setMaxSuspensionForce(s.maxForce);
    }

    // --- Mandos ---------------------------------------------------------

    /** Ángulo de las ruedas delanteras en radianes (positivo = izquierda). */
    public void setSteering(float radians) {
        vehicle.setSteeringValue(radians, WHEEL_FL);
        vehicle.setSteeringValue(radians, WHEEL_FR);
    }

    /** Fuerza de tracción (N) aplicada en el punto de contacto de una rueda. */
    public void setDriveForce(int wheel, float newtons) {
        vehicle.applyEngineForce(newtons, wheel);
    }

    /** Valor de freno de bullet por rueda (0 = libre). */
    public void setBrake(int wheel, float value) {
        vehicle.setBrake(value, wheel);
    }

    /** Fuerza en el chasis (N, coordenadas del mundo), p.ej. resistencias. */
    public void applyCentralForce(Vector3 force) {
        chassis.applyCentralForce(force);
    }

    // --- Lectura --------------------------------------------------------

    /** Velocidad a lo largo del eje longitudinal del coche (m/s, + adelante). */
    public float getForwardSpeed() {
        Vector3 forward = getForward(tmpV2);
        return chassis.getLinearVelocity().dot(forward);
    }

    /** Vector adelante del chasis en coordenadas del mundo. */
    public Vector3 getForward(Vector3 out) {
        getTransform(tmpM);
        return out.set(0f, 0f, 1f).rot(tmpM).nor();
    }

    public Matrix4 getTransform(Matrix4 out) {
        motionState.getWorldTransform(out);
        return out;
    }

    public Vector3 getPosition(Vector3 out) {
        return getTransform(tmpM).getTranslation(out);
    }

    /** Transformación mundial de una rueda (centro, con dirección y giro). */
    public Matrix4 getWheelTransform(int wheel, Matrix4 out) {
        vehicle.updateWheelTransform(wheel, true);
        btTransform t = vehicle.getWheelInfo(wheel).getWorldTransform();
        t.getOpenGLMatrix(glMatrix);
        return out.set(glMatrix);
    }

    public boolean isWheelInContact(int wheel) {
        return vehicle.getWheelInfo(wheel).getRaycastInfo().getIsInContact();
    }

    /**
     * Qué pisa la rueda (OTHER si está en el aire). btRaycastVehicle guarda en
     * groundObject un cuerpo fijo interno, así que se lanza un rayo corto
     * alrededor del punto de contacto contra el escenario.
     */
    public BodyTag getWheelGround(int wheel) {
        btWheelInfo.RaycastInfo info = vehicle.getWheelInfo(wheel).getRaycastInfo();
        if (!info.getIsInContact()) {
            return BodyTag.OTHER;
        }
        getWheelContactPoint(wheel, rayFrom);
        rayTo.set(rayFrom).add(0f, -0.15f, 0f);
        rayFrom.add(0f, 0.08f, 0f);
        groundRay.setCollisionObject(null);
        groundRay.setClosestHitFraction(1f);
        groundRay.setRayFromWorld(rayFrom);
        groundRay.setRayToWorld(rayTo);
        physics.world.rayTest(rayFrom, rayTo, groundRay);
        if (!groundRay.hasHit()) {
            return BodyTag.OTHER;
        }
        return physics.tagOf(groundRay.getCollisionObject().getCPointer());
    }

    public Vector3 getWheelContactPoint(int wheel, Vector3 out) {
        btWheelInfo.RaycastInfo info = vehicle.getWheelInfo(wheel).getRaycastInfo();
        return out.set(info.getContactPointWS().getX(), info.getContactPointWS().getY(),
            info.getContactPointWS().getZ());
    }

    /** Ángulo acumulado de giro de la rueda sobre su eje (rad). */
    public float getWheelRotation(int wheel) {
        return vehicle.getWheelInfo(wheel).getRotation();
    }

    public float getSuspensionLength(int wheel) {
        return vehicle.getWheelInfo(wheel).getRaycastInfo().getSuspensionLength();
    }

    public btRigidBody getChassis() {
        return chassis;
    }

    public VehicleSpec getSpec() {
        return spec;
    }

    /** Recoloca el coche parado en la pose dada. */
    public void reset(Matrix4 transform) {
        chassis.setWorldTransform(transform);
        motionState.setWorldTransform(transform);
        chassis.setLinearVelocity(Vector3.Zero);
        chassis.setAngularVelocity(Vector3.Zero);
        chassis.clearForces();
        for (int i = 0; i < 4; i++) {
            vehicle.applyEngineForce(0f, i);
            vehicle.setBrake(0f, i);
        }
        vehicle.setSteeringValue(0f, WHEEL_FL);
        vehicle.setSteeringValue(0f, WHEEL_FR);
        vehicle.resetSuspension();
        chassis.setActivationState(DISABLE_DEACTIVATION);
    }

    @Override
    public void dispose() {
        physics.world.removeVehicle(vehicle);
        physics.world.removeRigidBody(chassis);
        groundRay.dispose();
        vehicle.dispose();
        raycaster.dispose();
        tuning.dispose();
        chassis.dispose();
        motionState.dispose();
        chassisShape.dispose();
        chassisBox.dispose();
    }
}
