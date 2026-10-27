package com.pelayo.autoescuela.render;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.physics.bullet.DebugDrawer;
import com.badlogic.gdx.physics.bullet.linearmath.btIDebugDraw;
import com.badlogic.gdx.utils.Disposable;
import com.pelayo.autoescuela.sim.PhysicsWorld;

/** Dibuja las formas de colisión de bullet en alambre (tecla F3). */
public class PhysicsDebugView implements Disposable {

    private final PhysicsWorld physics;
    private final DebugDrawer drawer;
    private boolean enabled;

    public PhysicsDebugView(PhysicsWorld physics) {
        this.physics = physics;
        drawer = new DebugDrawer();
        drawer.setDebugMode(btIDebugDraw.DebugDrawModes.DBG_DrawWireframe);
        physics.world.setDebugDrawer(drawer);
    }

    public void toggle() {
        enabled = !enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void render(Camera camera) {
        if (!enabled) {
            return;
        }
        drawer.begin(camera);
        physics.world.debugDrawWorld();
        drawer.end();
    }

    @Override
    public void dispose() {
        physics.world.setDebugDrawer(null);
        drawer.dispose();
    }
}
