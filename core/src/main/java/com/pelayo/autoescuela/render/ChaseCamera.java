package com.pelayo.autoescuela.render;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/** Cámara exterior detrás y por encima del coche, para depurar la física. */
public class ChaseCamera {

    public final PerspectiveCamera camera;
    private final Vector3 target = new Vector3();
    private final Vector3 desired = new Vector3();
    private final Vector3 offset = new Vector3(0f, 2.2f, -7f);

    public ChaseCamera(float aspect) {
        camera = new PerspectiveCamera(60f, aspect, 1f);
        camera.near = 0.1f;
        camera.far = 600f;
    }

    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    private boolean topView;

    /** Vista cenital (plano) en lugar de la de persecución. */
    public void setTopView(boolean topView) {
        this.topView = topView;
    }

    public void update(Matrix4 chassis, float delta) {
        chassis.getTranslation(target);
        if (topView) {
            desired.set(target.x, target.y + 90f, target.z - 0.01f);
            camera.position.lerp(desired, Math.min(1f, delta * 5f));
            camera.up.set(0f, 0f, 1f);
            camera.lookAt(target.x, target.y, target.z);
            camera.update();
            return;
        }
        desired.set(offset).rot(chassis).add(target);
        desired.y = Math.max(desired.y, target.y + 1.2f);
        camera.position.lerp(desired, Math.min(1f, delta * 5f));
        camera.up.set(Vector3.Y);
        camera.lookAt(target.x, target.y + 0.6f, target.z);
        camera.update();
    }

    public void snap(Matrix4 chassis) {
        chassis.getTranslation(target);
        camera.position.set(offset).rot(chassis).add(target);
        update(chassis, 1f);
    }
}
