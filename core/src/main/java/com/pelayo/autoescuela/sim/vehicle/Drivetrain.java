package com.pelayo.autoescuela.sim.vehicle;

import com.badlogic.gdx.utils.Array;
import com.pelayo.autoescuela.data.VehicleSpec;
import com.pelayo.autoescuela.input.DriverInputState;

/**
 * Cadena cinemática completa: motor → embrague → caja → ruedas motrices. Cada
 * paso toma los mandos y la velocidad de las ruedas y devuelve el par que
 * llega a las ruedas. No sabe nada de bullet.
 */
public class Drivetrain {

    public final Engine engine;
    public final Clutch clutch;
    public final Gearbox gearbox;
    private final VehicleSpec spec;
    private float wheelTorque;

    public Drivetrain(VehicleSpec spec) {
        this.spec = spec;
        engine = new Engine(spec.engine);
        clutch = new Clutch(spec.clutch);
        gearbox = new Gearbox(spec.gearbox);
    }

    /**
     * Un paso fijo.
     *
     * @param in           mandos del paso
     * @param forwardSpeed velocidad longitudinal del coche (m/s)
     * @param events       donde anotar calados, rascadas, etc.
     * @return par total en las ruedas motrices (Nm, positivo = adelante)
     */
    public float step(DriverInputState in, float forwardSpeed, float dt, Array<VehicleEvent> events) {
        gearbox.update(dt);
        if (in.gearSelect != DriverInputState.GEAR_NONE) {
            gearbox.request(in.gearSelect, in.clutch, forwardSpeed, events);
        } else if (in.gearUp) {
            gearbox.request(Math.min(Gearbox.MAX_GEAR, gearbox.getGear() + 1), in.clutch, forwardSpeed, events);
        } else if (in.gearDown) {
            gearbox.request(Math.max(Gearbox.REVERSE, gearbox.getGear() - 1), in.clutch, forwardSpeed, events);
        }
        if (in.ignition) {
            engine.ignition(in.clutch > 0.9f || gearbox.getGear() == Gearbox.NEUTRAL, events);
        }

        float ratio = gearbox.ratio();
        float omegaWheel = forwardSpeed / spec.wheelRadius;
        float omegaIn = omegaWheel * ratio;
        float engineTorque = engine.produceTorque(in.throttle);
        float clutchTorque = clutch.solve(engine, engineTorque, in.clutch, omegaIn, ratio != 0f, dt);
        engine.integrate(engineTorque - clutchTorque, dt, events);
        wheelTorque = clutchTorque * ratio * spec.gearbox.efficiency;
        return wheelTorque;
    }

    public float getWheelTorque() {
        return wheelTorque;
    }

    /** Estado inicial típico de una práctica: motor al ralentí, punto muerto. */
    public void resetRunning() {
        engine.forceRunning();
        gearbox.forceGear(Gearbox.NEUTRAL);
    }

    public void resetOff() {
        engine.forceOff();
        gearbox.forceGear(Gearbox.NEUTRAL);
    }
}
