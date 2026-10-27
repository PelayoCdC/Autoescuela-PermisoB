package com.pelayo.autoescuela.input;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.math.MathUtils;
import com.pelayo.autoescuela.data.VehicleSpec;

/**
 * Mandos con gamepad (gdx-controllers, mapeo genérico tipo Xbox):
 *
 * <pre>
 * Gatillo dcho  gas            Gatillo izq.  freno
 * LB            embrague       Stick izq.    dirección
 * Stick dcho    mirar          X             freno de mano
 * RB / A        subir marcha   B             bajar marcha
 * Start         contacto       D-pad ← →     intermitentes
 * Y             cámara         Back          recolocar
 * </pre>
 *
 * Los gatillos llegan como ejes 4 y 5 en escritorio; si el mando no los
 * expone, se usan los botones L2/R2 con rampa.
 */
public class GamepadDriverInput implements DriverInput {

    private static final int AXIS_TRIGGER_LEFT = 4;
    private static final int AXIS_TRIGGER_RIGHT = 5;
    private static final float DEADZONE = 0.12f;

    private final PedalSmoother throttleRamp = new PedalSmoother(3f, 4f);
    private final PedalSmoother brakeRamp = new PedalSmoother(4f, 5f);
    private final PedalSmoother clutchRamp;
    private final boolean[] wasDown = new boolean[32];

    public GamepadDriverInput(VehicleSpec spec) {
        clutchRamp = new PedalSmoother(spec.clutch.pressRate, spec.clutch.releaseRate);
    }

    /** Hay algún mando conectado. */
    public boolean isAvailable() {
        return Controllers.getCurrent() != null;
    }

    @Override
    public void poll(DriverInputState out, float dt) {
        Controller c = Controllers.getCurrent();
        if (c == null) {
            return;
        }
        ControllerMapping m = c.getMapping();

        if (c.getAxisCount() > AXIS_TRIGGER_RIGHT) {
            out.throttle = trigger(c.getAxis(AXIS_TRIGGER_RIGHT));
            out.brake = trigger(c.getAxis(AXIS_TRIGGER_LEFT));
        } else {
            out.throttle = throttleRamp.update(c.getButton(m.buttonR2) ? 1f : 0f, dt);
            out.brake = brakeRamp.update(c.getButton(m.buttonL2) ? 1f : 0f, dt);
        }
        out.clutch = clutchRamp.update(c.getButton(m.buttonL1) ? 1f : 0f, dt);

        // Stick: + derecha en el mando, + izquierda en nuestro eje de dirección
        out.steer = -deadzone(c.getAxis(m.axisLeftX));
        out.lookYaw = -deadzone(c.getAxis(m.axisRightX));
        out.lookPitch = -deadzone(c.getAxis(m.axisRightY));

        out.handbrakeToggle = justPressed(c, m.buttonX, 0);
        out.gearUp = justPressed(c, m.buttonR1, 1) || justPressed(c, m.buttonA, 2);
        out.gearDown = justPressed(c, m.buttonB, 3);
        out.ignition = justPressed(c, m.buttonStart, 4);
        out.indicatorLeft = justPressed(c, m.buttonDpadLeft, 5);
        out.indicatorRight = justPressed(c, m.buttonDpadRight, 6);
        out.cameraToggle = justPressed(c, m.buttonY, 7);
        out.resetVehicle = justPressed(c, m.buttonBack, 8);
    }

    /** Gatillo: algunos drivers dan -1..1 y otros 0..1. */
    private static float trigger(float raw) {
        float v = raw < -0.001f ? (raw + 1f) / 2f : raw;
        return MathUtils.clamp(v, 0f, 1f);
    }

    private static float deadzone(float v) {
        if (Math.abs(v) < DEADZONE) {
            return 0f;
        }
        return MathUtils.clamp((v - Math.signum(v) * DEADZONE) / (1f - DEADZONE), -1f, 1f);
    }

    private boolean justPressed(Controller c, int button, int slot) {
        boolean down = button >= 0 && c.getButton(button);
        boolean pressed = down && !wasDown[slot];
        wasDown[slot] = down;
        return pressed;
    }
}
