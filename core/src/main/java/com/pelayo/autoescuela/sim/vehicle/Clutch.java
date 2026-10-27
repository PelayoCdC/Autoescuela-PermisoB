package com.pelayo.autoescuela.sim.vehicle;

import com.badlogic.gdx.math.MathUtils;
import com.pelayo.autoescuela.data.VehicleSpec;

/**
 * Embrague de fricción. El pedal (1 = pisado a fondo) fija el par máximo que
 * puede transmitir; por debajo de ese par motor y caja giran solidarios
 * (bloqueado) y por encima patina transmitiendo justo el máximo. Se resuelve
 * de forma implícita para que sea estable con cualquier paso de tiempo.
 */
public class Clutch {

    private final VehicleSpec.Clutch spec;
    private float slip;
    private float torque;
    private boolean locked;
    private float engagement;

    public Clutch(VehicleSpec.Clutch spec) {
        this.spec = spec;
    }

    /**
     * Fracción de acoplamiento 0..1 según el pedal (1 pisado a fondo). Dentro de la franja de fricción el
     * par entra progresivo, como con el muelle de diafragma de un embrague real: al principio poco (el coche
     * se arrastra al ralentí durante buena parte de la franja) y al final rápido hasta acoplar del todo.
     */
    public float engagement(float pedal) {
        float travel = 1f - MathUtils.clamp(pedal, 0f, 1f);
        float t = MathUtils.clamp((travel - spec.bitePointLow) / (spec.bitePointHigh - spec.bitePointLow), 0f, 1f);
        return t * t;
    }

    /** Par máximo transmisible con ese pedal (Nm). */
    public float capacity(float pedal) {
        return engagement(pedal) * spec.maxTorque;
    }

    /**
     * Resuelve el par que pasa del motor al eje primario de la caja este paso.
     *
     * @param engine       motor (se lee su velocidad e inercia)
     * @param engineTorque par que produce el motor este paso (Nm)
     * @param pedal        pedal de embrague 0..1 (1 = pisado)
     * @param omegaIn      velocidad del eje primario impuesta por las ruedas (rad/s)
     * @param coupled      false si la caja está en punto muerto (nada que acoplar)
     * @return par transmitido (positivo = el motor empuja al coche)
     */
    public float solve(Engine engine, float engineTorque, float pedal, float omegaIn,
                       boolean coupled, float dt) {
        float omegaE = engine.getOmega();
        engagement = engagement(pedal);
        if (!coupled) {
            slip = omegaE;
            torque = 0f;
            locked = false;
            return 0f;
        }
        slip = omegaE - omegaIn;
        float cap = engagement * spec.maxTorque;
        if (cap < 0.01f) {
            torque = 0f;
            locked = false;
            return 0f;
        }
        // Par que haría falta para que el motor acabe el paso a la velocidad del eje
        float lockTorque = engineTorque - engine.getInertia() * (omegaIn - omegaE) / dt;
        if (Math.abs(lockTorque) <= cap) {
            locked = true;
            torque = lockTorque;
        } else {
            locked = false;
            torque = Math.signum(lockTorque) * cap;
        }
        return torque;
    }

    /** Diferencia de velocidad motor − eje (rad/s) en el último paso. */
    public float getSlip() {
        return slip;
    }

    public float getTorque() {
        return torque;
    }

    public boolean isLocked() {
        return locked;
    }

    public float getEngagement() {
        return engagement;
    }
}
