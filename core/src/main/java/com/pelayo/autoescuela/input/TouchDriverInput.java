package com.pelayo.autoescuela.input;

import com.badlogic.gdx.math.MathUtils;
import com.pelayo.autoescuela.data.VehicleSpec;
import com.pelayo.autoescuela.ui.widgets.TouchControls;

/**
 * Mandos táctiles: lee el estado de los widgets de {@link TouchControls}
 * (volante arrastrable, pedales analógicos por posición del dedo, botones de
 * marchas, freno de mano, llave e intermitentes).
 */
public class TouchDriverInput implements DriverInput {

    private final TouchControls controls;
    private final PedalSmoother clutch;
    private float steer;

    public TouchDriverInput(VehicleSpec spec, TouchControls controls) {
        this.controls = controls;
        this.clutch = new PedalSmoother(spec.clutch.pressRate * 2f, spec.clutch.releaseRate * 2f);
    }

    @Override
    public void poll(DriverInputState out, float dt) {
        TouchControls.State s = controls.state;
        out.throttle = s.throttle;
        out.brake = s.brake;
        // El embrague táctil se suaviza un poco para que no sea todo o nada
        out.clutch = clutch.update(s.clutch, dt);
        // El volante vuelve solo al centro al soltarlo
        if (s.steering) {
            steer = s.steer;
        } else {
            steer = MathUtils.lerp(steer, 0f, Math.min(1f, dt * 6f));
        }
        out.steer = steer;
        out.lookYaw = s.lookYaw;
        out.lookPitch = s.lookPitch;

        out.handbrakeToggle = s.takeHandbrake();
        out.gearUp = s.takeGearUp();
        out.gearDown = s.takeGearDown();
        out.gearSelect = s.takeGearSelect();
        out.ignition = s.takeIgnition();
        out.indicatorLeft = s.takeIndicatorLeft();
        out.indicatorRight = s.takeIndicatorRight();
        out.cameraToggle = s.takeCamera();
        out.resetVehicle = s.takeReset();
    }
}
