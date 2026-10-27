package com.pelayo.autoescuela.render;

import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * Cámara en los ojos del conductor. Sigue al chasis y añade el giro del
 * cuello (mirar a los lados y arriba/abajo) con suavizado.
 */
public class CockpitCamera {

    /** Giro máximo del cuello (grados). */
    private static final float MAX_YAW = 110f;
    private static final float MAX_PITCH = 35f;

    public final PerspectiveCamera camera;
    private final Vector3 eyeOffset = new Vector3();
    private float yaw;
    private float pitch;
    private final Vector3 tmp = new Vector3();
    private final Vector3 right = new Vector3();
    /** Inclinación fija de la mirada (grados, + arriba) según el encuadre elegido. */
    private float basePitch;

    public CockpitCamera(Vector3 eyeOffset, float aspect) {
        this.eyeOffset.set(eyeOffset);
        camera = new PerspectiveCamera(70f, aspect, 1f);
        camera.near = 0.05f;
        camera.far = 600f;
    }

    /** Encuadre: campo de visión vertical e inclinación fija de la mirada. */
    public void setFraming(float fovDeg, float basePitchDeg) {
        camera.fieldOfView = fovDeg;
        basePitch = basePitchDeg;
        camera.update();
    }

    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    /**
     * @param chassis   transformación del chasis
     * @param lookYaw   mirar a los lados (-1..1, + izquierda)
     * @param lookPitch mirar arriba/abajo (-1..1, + arriba)
     */
    public void update(Matrix4 chassis, float lookYaw, float lookPitch, float delta) {
        float k = Math.min(1f, delta * 12f);
        yaw = MathUtils.lerp(yaw, lookYaw * MAX_YAW, k);
        pitch = MathUtils.lerp(pitch, lookPitch * MAX_PITCH, k);

        camera.position.set(eyeOffset).mul(chassis);
        camera.direction.set(0f, 0f, 1f).rot(chassis).nor();
        camera.up.set(0f, 1f, 0f).rot(chassis).nor();
        // Yaw sobre el eje vertical del coche, pitch sobre el eje lateral
        camera.direction.rotate(camera.up, yaw);
        right.set(camera.direction).crs(camera.up).nor();
        camera.direction.rotate(right, -(pitch + basePitch));
        camera.up.set(right).crs(camera.direction).nor();
        camera.update();
    }
}
