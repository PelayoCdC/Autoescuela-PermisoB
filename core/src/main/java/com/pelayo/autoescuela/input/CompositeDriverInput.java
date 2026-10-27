package com.pelayo.autoescuela.input;

/**
 * Fusiona teclado y mando: por cada eje manda el que esté más activo, y los
 * pulsos se combinan con OR. Así se puede acelerar con el gatillo y cambiar
 * de marcha con el teclado sin configurar nada.
 */
public class CompositeDriverInput implements DriverInput {

    private final DriverInput primary;
    private final DriverInput secondary;
    private final DriverInputState tmp = new DriverInputState();

    public CompositeDriverInput(DriverInput primary, DriverInput secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    @Override
    public void poll(DriverInputState out, float dt) {
        primary.poll(out, dt);
        tmp.clearPulses();
        tmp.steer = tmp.throttle = tmp.brake = tmp.clutch = tmp.lookYaw = tmp.lookPitch = 0f;
        secondary.poll(tmp, dt);

        out.throttle = Math.max(out.throttle, tmp.throttle);
        out.brake = Math.max(out.brake, tmp.brake);
        out.clutch = Math.max(out.clutch, tmp.clutch);
        out.steer = Math.abs(tmp.steer) > Math.abs(out.steer) ? tmp.steer : out.steer;
        out.lookYaw = Math.abs(tmp.lookYaw) > Math.abs(out.lookYaw) ? tmp.lookYaw : out.lookYaw;
        out.lookPitch = Math.abs(tmp.lookPitch) > Math.abs(out.lookPitch) ? tmp.lookPitch : out.lookPitch;

        out.handbrakeToggle |= tmp.handbrakeToggle;
        out.gearUp |= tmp.gearUp;
        out.gearDown |= tmp.gearDown;
        if (out.gearSelect == DriverInputState.GEAR_NONE) {
            out.gearSelect = tmp.gearSelect;
        }
        out.ignition |= tmp.ignition;
        out.indicatorLeft |= tmp.indicatorLeft;
        out.indicatorRight |= tmp.indicatorRight;
        out.lookReset |= tmp.lookReset;
        out.cameraToggle |= tmp.cameraToggle;
        out.resetVehicle |= tmp.resetVehicle;
        out.lessonContinue |= tmp.lessonContinue;
    }
}
