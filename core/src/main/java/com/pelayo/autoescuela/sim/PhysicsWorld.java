package com.pelayo.autoescuela.sim;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBroadphaseInterface;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver;
import com.badlogic.gdx.physics.bullet.linearmath.btMotionState;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.LongMap;

/**
 * Mundo de bullet con su configuración y el registro de qué es cada cuerpo.
 * Todo objeto nativo creado aquí se guarda para liberarlo en {@link #dispose()}
 * (bullet no se libera con el GC de Java; las fugas son nativas).
 */
public class PhysicsWorld implements Disposable {

    /** Paso fijo de la simulación (s). */
    public static final float DT = 1f / 120f;

    /** Grupos de colisión: escenario (suelo, bordillos, conos...) y coche del jugador. */
    public static final int GROUP_WORLD = 1;
    public static final int GROUP_PLAYER = 2;
    public static final int MASK_ALL = -1;

    public final btDiscreteDynamicsWorld world;
    public final ContactRouter contacts;

    private final btDefaultCollisionConfiguration config;
    private final btCollisionDispatcher dispatcher;
    private final btBroadphaseInterface broadphase;
    private final btSequentialImpulseConstraintSolver solver;

    /** Cuerpos y formas creados por el mundo (estáticos y dinámicos de escenario), para liberarlos. */
    private final Array<btRigidBody> bodies = new Array<>();
    private final Array<btCollisionShape> shapes = new Array<>();
    private final Array<btMotionState> motionStates = new Array<>();

    /** Etiqueta de cada objeto de colisión por su puntero nativo y por su userValue. */
    private final LongMap<BodyTag> tags = new LongMap<>();
    private final IntMap<BodyTag> tagsByUserValue = new IntMap<>();
    private int nextUserValue = 1;

    public PhysicsWorld() {
        config = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(config);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, config);
        world.setGravity(new Vector3(0f, -9.81f, 0f));
        contacts = new ContactRouter(this);
    }

    /** Un paso fijo exacto (sin subpasos internos): determinista. */
    public void step() {
        contacts.clear();
        for (Runnable r : preStep) {
            r.run();
        }
        world.stepSimulation(DT, 0, DT);
    }

    /** Se ejecutan al empezar cada paso, antes de integrar (p.ej. mover el tráfico). */
    public final Array<Runnable> preStep = new Array<>();

    /**
     * Cuerpo cinemático (lo mueve el código, no la física): empuja a los dinámicos y genera contactos.
     * Se mueve cambiando {@code motionState.transform} y la transformación del cuerpo.
     */
    public btRigidBody addKinematic(btCollisionShape shape, Matrix4 transform, BodyTag tag, DefaultMotionState motionState) {
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(0f, motionState, shape, Vector3.Zero);
        btRigidBody body = new btRigidBody(info);
        info.dispose();
        body.setCollisionFlags(body.getCollisionFlags() | btCollisionObject.CollisionFlags.CF_KINEMATIC_OBJECT);
        body.setActivationState(com.badlogic.gdx.physics.bullet.collision.Collision.DISABLE_DEACTIVATION);
        body.setWorldTransform(transform);
        register(body, tag);
        world.addRigidBody(body, GROUP_WORLD, MASK_ALL);
        bodies.add(body);
        shapes.add(shape);
        motionStates.add(motionState);
        return body;
    }

    /**
     * Registra una etiqueta para un objeto de colisión (del jugador o de un
     * escenario) y le asigna un userValue único para los callbacks de contacto.
     */
    public int register(btCollisionObject object, BodyTag tag) {
        int userValue = nextUserValue++;
        object.setUserValue(userValue);
        tags.put(object.getCPointer(), tag);
        tagsByUserValue.put(userValue, tag);
        return userValue;
    }

    /** Etiqueta del objeto con ese puntero nativo (p.ej. el alcanzado por un rayo). */
    public BodyTag tagOf(long cPointer) {
        BodyTag tag = tags.get(cPointer);
        return tag == null ? BodyTag.OTHER : tag;
    }

    public BodyTag tagOfUserValue(int userValue) {
        BodyTag tag = tagsByUserValue.get(userValue);
        return tag == null ? BodyTag.OTHER : tag;
    }

    /** Crea y añade un cuerpo estático con la forma y transformación dadas. */
    public btRigidBody addStatic(btCollisionShape shape, Matrix4 transform, BodyTag tag,
                                 float friction) {
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(0f, null, shape, Vector3.Zero);
        info.setFriction(friction);
        btRigidBody body = new btRigidBody(info);
        info.dispose();
        body.setWorldTransform(transform);
        register(body, tag);
        world.addRigidBody(body, GROUP_WORLD, MASK_ALL);
        bodies.add(body);
        shapes.add(shape);
        return body;
    }

    /** Crea y añade un cuerpo dinámico (p.ej. un cono). */
    public btRigidBody addDynamic(btCollisionShape shape, float mass, Matrix4 transform,
                                  BodyTag tag, float friction) {
        Vector3 inertia = new Vector3();
        shape.calculateLocalInertia(mass, inertia);
        btMotionState motionState = new DefaultMotionState(transform);
        btRigidBody.btRigidBodyConstructionInfo info =
            new btRigidBody.btRigidBodyConstructionInfo(mass, motionState, shape, inertia);
        info.setFriction(friction);
        btRigidBody body = new btRigidBody(info);
        info.dispose();
        register(body, tag);
        world.addRigidBody(body, GROUP_WORLD, MASK_ALL);
        bodies.add(body);
        shapes.add(shape);
        motionStates.add(motionState);
        return body;
    }

    /** Quita y libera los cuerpos de escenario (no los del jugador). */
    public void clearBodies() {
        preStep.clear();
        for (btRigidBody body : bodies) {
            world.removeRigidBody(body);
            tags.remove(body.getCPointer());
            tagsByUserValue.remove(body.getUserValue());
            body.dispose();
        }
        bodies.clear();
        for (btCollisionShape shape : shapes) {
            shape.dispose();
        }
        shapes.clear();
        for (btMotionState ms : motionStates) {
            ms.dispose();
        }
        motionStates.clear();
    }

    @Override
    public void dispose() {
        clearBodies();
        contacts.dispose();
        world.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        config.dispose();
    }

    /** Estado de movimiento mínimo: guarda la transformación en una Matrix4 propia. */
    public static class DefaultMotionState extends btMotionState {
        public final Matrix4 transform = new Matrix4();

        public DefaultMotionState(Matrix4 initial) {
            transform.set(initial);
        }

        @Override
        public void getWorldTransform(Matrix4 worldTrans) {
            worldTrans.set(transform);
        }

        @Override
        public void setWorldTransform(Matrix4 worldTrans) {
            transform.set(worldTrans);
        }
    }
}
