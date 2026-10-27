package com.pelayo.autoescuela.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.MathUtils;
import com.pelayo.autoescuela.data.VehicleSpec;
import com.pelayo.autoescuela.input.InputMapping.Action;

/**
 * Mandos con teclado y ratón, con las teclas de {@link InputMapping} (por
 * defecto: W/S gas y freno, A/D dirección, Q embrague, Shift medio embrague,
 * Espacio freno de mano, 1-5/R/N marchas, E/Z sube/baja, Intro contacto,
 * flechas intermitentes, coma/punto mirar a los lados, M mirar atrás,
 * botón derecho del ratón mirar libremente, V cámara, Retroceso recolocar).
 *
 * El embrague con tecla es analógico: al pulsar baja a fondo en ~0,2 s y al
 * soltar sube despacio (~0,6 s) para poder encontrar el punto de fricción;
 * con la tecla de medio embrague se mantiene justo en ese punto.
 */
public class KeyboardMouseDriverInput implements DriverInput {

    /** Embrague "a medias": justo por encima del punto de fricción. */
    private static final float HALF_CLUTCH = 0.55f;

    private final VehicleSpec spec;
    private final InputMapping keys;
    /**
     * Acelerador con teclado: la tecla es todo o nada, así que el pedal baja despacio (1 s hasta el fondo):
     * un toque corto es poco gas y mantenerla es ir pisando cada vez más, como con el pie.
     */
    private final PedalSmoother throttle = new PedalSmoother(1f, 4f);
    private final PedalSmoother brake = new PedalSmoother(4f, 5f);
    private final PedalSmoother clutch;
    private float steer;
    private float lookYaw;
    private float lookPitch;

    // Pulsos acumulados desde la última consulta
    private boolean handbrake, gearUp, gearDown, ignition, indLeft, indRight, lookReset, camera, reset;
    private int gearSelect = DriverInputState.GEAR_NONE;
    private int lastMouseX, lastMouseY;
    private boolean looking;

    private final InputProcessor processor = new InputAdapter() {
        @Override
        public boolean keyDown(int keycode) {
            Action action = keys.actionFor(keycode);
            if (action == null) {
                return false;
            }
            switch (action) {
                case HANDBRAKE: handbrake = true; return true;
                case GEAR_UP: gearUp = true; return true;
                case GEAR_DOWN: gearDown = true; return true;
                case GEAR_1: gearSelect = 1; return true;
                case GEAR_2: gearSelect = 2; return true;
                case GEAR_3: gearSelect = 3; return true;
                case GEAR_4: gearSelect = 4; return true;
                case GEAR_5: gearSelect = 5; return true;
                case GEAR_R: gearSelect = -1; return true;
                case GEAR_N: gearSelect = 0; return true;
                case IGNITION: ignition = true; return true;
                case INDICATOR_LEFT: indLeft = true; return true;
                case INDICATOR_RIGHT: indRight = true; return true;
                case CAMERA: camera = true; return true;
                case RESET: reset = true; return true;
                default: return false;
            }
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (button == Input.Buttons.RIGHT) {
                looking = true;
                lastMouseX = screenX;
                lastMouseY = screenY;
                return true;
            }
            return false;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (button == Input.Buttons.RIGHT) {
                looking = false;
                lookReset = true;
                return true;
            }
            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (looking) {
                lookYaw = MathUtils.clamp(lookYaw - (screenX - lastMouseX) * 0.004f, -1f, 1f);
                lookPitch = MathUtils.clamp(lookPitch - (screenY - lastMouseY) * 0.004f, -1f, 1f);
                lastMouseX = screenX;
                lastMouseY = screenY;
                return true;
            }
            return false;
        }
    };

    public KeyboardMouseDriverInput(VehicleSpec spec, InputMapping keys) {
        this.spec = spec;
        this.keys = keys;
        clutch = new PedalSmoother(spec.clutch.pressRate, spec.clutch.releaseRate);
    }

    /** Procesador a encadenar en el InputMultiplexer de la pantalla. */
    public InputProcessor getProcessor() {
        return processor;
    }

    @Override
    public void poll(DriverInputState out, float dt) {
        out.throttle = throttle.update(keys.isPressed(Action.THROTTLE) ? 1f : 0f, dt);
        out.brake = brake.update(keys.isPressed(Action.BRAKE) ? 1f : 0f, dt);

        float clutchTarget = 0f;
        if (keys.isPressed(Action.CLUTCH)) {
            clutchTarget = 1f;
        } else if (keys.isPressed(Action.CLUTCH_HALF)) {
            clutchTarget = HALF_CLUTCH;
        }
        out.clutch = clutch.update(clutchTarget, dt);

        // Dirección: gira a ritmo constante y vuelve sola al centro
        float maxDeg = spec.steering.maxAngleDeg;
        float steerRate = spec.steering.rateDegPerSec / maxDeg;
        float returnRate = spec.steering.returnDegPerSec / maxDeg;
        boolean left = keys.isPressed(Action.STEER_LEFT);
        boolean right = keys.isPressed(Action.STEER_RIGHT);
        if (left && !right) {
            steer = Math.min(1f, steer + steerRate * dt);
        } else if (right && !left) {
            steer = Math.max(-1f, steer - steerRate * dt);
        } else if (steer > 0f) {
            steer = Math.max(0f, steer - returnRate * dt);
        } else if (steer < 0f) {
            steer = Math.min(0f, steer + returnRate * dt);
        }
        out.steer = steer;

        // Mirar con teclas: a los lados (ventanillas) o atrás por encima del hombro
        float lookTarget = 0f;
        if (keys.isPressed(Action.LOOK_BACK)) {
            lookTarget = 1f;
        } else if (keys.isPressed(Action.LOOK_LEFT)) {
            lookTarget = 0.75f;
        } else if (keys.isPressed(Action.LOOK_RIGHT)) {
            lookTarget = -0.75f;
        }
        if (!looking) {
            lookYaw = MathUtils.lerp(lookYaw, lookTarget, Math.min(1f, dt * 10f));
            lookPitch = MathUtils.lerp(lookPitch, 0f, Math.min(1f, dt * 10f));
        }
        out.lookYaw = lookYaw;
        out.lookPitch = lookPitch;

        out.handbrakeToggle = handbrake;
        out.gearUp = gearUp;
        out.gearDown = gearDown;
        out.gearSelect = gearSelect;
        out.ignition = ignition;
        out.indicatorLeft = indLeft;
        out.indicatorRight = indRight;
        out.lookReset = lookReset;
        out.cameraToggle = camera;
        out.resetVehicle = reset;

        handbrake = gearUp = gearDown = ignition = indLeft = indRight = lookReset = camera = reset = false;
        gearSelect = DriverInputState.GEAR_NONE;
    }
}
