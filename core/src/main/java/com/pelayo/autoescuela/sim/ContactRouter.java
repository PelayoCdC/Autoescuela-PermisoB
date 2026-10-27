package com.pelayo.autoescuela.sim;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.ContactListener;
import com.badlogic.gdx.physics.bullet.collision.btManifoldPoint;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

/**
 * Recibe los contactos de bullet (solo entre objetos marcados con
 * CF_CUSTOM_MATERIAL_CALLBACK, es decir, el chasis del jugador) y los anota
 * por paso con la etiqueta del otro cuerpo, para que los evaluadores sepan si
 * se ha tocado un bordillo, un cono o un coche aparcado.
 */
public class ContactRouter extends ContactListener {

    /** Un contacto del chasis con algo en el paso actual. */
    public static class Hit implements Pool.Poolable {
        public BodyTag tag = BodyTag.OTHER;
        public final Vector3 point = new Vector3();
        public float impulse;

        @Override
        public void reset() {
            tag = BodyTag.OTHER;
            point.setZero();
            impulse = 0f;
        }
    }

    private final PhysicsWorld physics;
    private final Array<Hit> hits = new Array<>();
    private final Pool<Hit> pool = new Pool<Hit>() {
        @Override
        protected Hit newObject() {
            return new Hit();
        }
    };

    public ContactRouter(PhysicsWorld physics) {
        this.physics = physics;
        enableOnAdded();
    }

    @Override
    public boolean onContactAdded(btManifoldPoint cp, int userValue0, int partId0, int index0,
                                  int userValue1, int partId1, int index1) {
        BodyTag a = physics.tagOfUserValue(userValue0);
        BodyTag b = physics.tagOfUserValue(userValue1);
        if (a == BodyTag.PLAYER_CHASSIS && b != BodyTag.PLAYER_CHASSIS) {
            record(cp, b, false);
        } else if (b == BodyTag.PLAYER_CHASSIS && a != BodyTag.PLAYER_CHASSIS) {
            record(cp, a, true);
        }
        return true;
    }

    private void record(btManifoldPoint cp, BodyTag other, boolean otherIsA) {
        Hit hit = pool.obtain();
        hit.tag = other;
        if (otherIsA) {
            cp.getPositionWorldOnA(hit.point);
        } else {
            cp.getPositionWorldOnB(hit.point);
        }
        hit.impulse = cp.getAppliedImpulse();
        hits.add(hit);
    }

    /** Contactos del chasis en el último paso. */
    public Array<Hit> getHits() {
        return hits;
    }

    /** Vacía la lista; llamar antes de cada paso. */
    public void clear() {
        pool.freeAll(hits);
        hits.clear();
    }
}
