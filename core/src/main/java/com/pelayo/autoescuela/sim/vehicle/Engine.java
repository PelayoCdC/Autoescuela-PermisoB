package com.pelayo.autoescuela.sim.vehicle;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.pelayo.autoescuela.data.VehicleSpec;

/**
 * Motor de combustión como cuerpo giratorio con inercia: produce par según la
 * curva a plena carga y el acelerador, resta el freno motor, mantiene el
 * ralentí con un control sencillo y se cala si baja de vueltas con carga.
 */
public class Engine {

    /** Tiempo por debajo de las revoluciones de calado antes de calar (s). */
    private static final float STALL_DELAY = 0.12f;
    /** Revoluciones a las que el motor "prende" durante el arranque. */
    private static final float FIRE_RPM = 520f;
    /** Exponente del mapa de pedal: 0,25 de pedal → 0,43 del par. */
    private static final float PEDAL_MAP_EXPONENT = 0.6f;
    /** Ancho (rpm) de la zona en la que el regulador recorta el gasóleo al acercarse al techo del pedal. */
    private static final float GOVERNOR_BAND = 450f;

    private final VehicleSpec.Engine spec;
    private float omega;
    private boolean running;
    private boolean starting;
    private float starterTimer;
    private float stallTimer;

    public Engine(VehicleSpec.Engine spec) {
        this.spec = spec;
    }

    public float getRpm() {
        return omega * 60f / MathUtils.PI2;
    }

    public float getOmega() {
        return omega;
    }

    public float getInertia() {
        return spec.inertia;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isStarting() {
        return starting;
    }

    /** Pone el motor girando al ralentí (inicio de escenario con el motor en marcha). */
    public void forceRunning() {
        running = true;
        starting = false;
        omega = spec.idleRpm * MathUtils.PI2 / 60f;
        stallTimer = 0f;
    }

    public void forceOff() {
        running = false;
        starting = false;
        omega = 0f;
    }

    /** Par a plena carga a esas revoluciones, interpolando la curva. */
    public float torqueCurve(float rpm) {
        float[][] c = spec.torqueCurve;
        if (rpm <= c[0][0]) {
            return c[0][1];
        }
        for (int i = 1; i < c.length; i++) {
            if (rpm <= c[i][0]) {
                float t = (rpm - c[i - 1][0]) / (c[i][0] - c[i - 1][0]);
                return MathUtils.lerp(c[i - 1][1], c[i][1], t);
            }
        }
        return c[c.length - 1][1];
    }

    /** Par resistente interno (bombeo y fricción) a esas revoluciones. */
    public float dragTorque(float rpm) {
        return spec.dragC0 + spec.dragC1 * rpm / 1000f;
    }

    /**
     * Par que produce el motor este paso con el acelerador dado, sin contar la
     * carga del embrague (positivo acelera el cigüeñal).
     */
    public float produceTorque(float throttle) {
        float rpm = getRpm();
        float drag = dragTorque(rpm);
        if (running) {
            // Mapa de pedal progresivo (un cuarto de pedal da bastante más de un cuarto del gasóleo)
            float pedal = MathUtils.clamp(throttle, 0f, 1f);
            float eff = rpm > spec.revLimitRpm ? 0f : (float) Math.pow(pedal, PEDAL_MAP_EXPONENT);
            // Regulador de todo régimen (como en los diésel): cada posición del pedal tiene un techo de
            // vueltas; al acercarse, el gasóleo se recorta. Sin carga (punto muerto o embrague pisado) el
            // motor se queda en ese techo en lugar de subir hasta el corte; con carga apenas influye.
            float ceiling = spec.idleRpm + pedal * (spec.revLimitRpm - spec.idleRpm);
            eff *= MathUtils.clamp((ceiling - rpm) / GOVERNOR_BAND + 1f, 0f, 1f);
            float gross = eff * (torqueCurve(rpm) + drag);
            // Regulador de ralentí: inyecta lo necesario para no caer de idleRpm,
            // hasta idleTorqueMax. Basta para avanzar a ralentí soltando despacio,
            // pero no para resistir un embrague soltado de golpe.
            float governor = spec.idleKp * (spec.idleRpm - rpm) + dragTorque(spec.idleRpm);
            gross = Math.max(gross, MathUtils.clamp(governor, 0f, spec.idleTorqueMax));
            return gross - drag;
        }
        if (starting) {
            return spec.starterTorque - drag * 0.5f;
        }
        return -drag * 0.5f; // motor apagado: se va parando
    }

    /** Integra las revoluciones con el par neto (producido menos el del embrague). */
    public void integrate(float netTorque, float dt, Array<VehicleEvent> events) {
        omega += netTorque / spec.inertia * dt;
        if (omega < 0f) {
            omega = 0f;
        }
        float rpm = getRpm();
        if (starting) {
            starterTimer += dt;
            if (rpm >= FIRE_RPM) {
                starting = false;
                running = true;
                stallTimer = 0f;
                events.add(VehicleEvent.ENGINE_START);
            } else if (starterTimer > spec.starterTime) {
                starting = false; // no ha prendido (p.ej. arrancando con marcha y sin embrague)
            }
        }
        if (running) {
            if (rpm < spec.stallRpm) {
                stallTimer += dt;
                if (stallTimer > STALL_DELAY) {
                    running = false;
                    stallTimer = 0f;
                    events.add(VehicleEvent.STALL);
                }
            } else {
                stallTimer = 0f;
            }
        }
    }

    /** Llave de contacto: apaga el motor si está en marcha, o acciona el arranque. */
    public void ignition(boolean clutchPressedOrNeutral, Array<VehicleEvent> events) {
        if (running) {
            running = false;
            events.add(VehicleEvent.ENGINE_OFF);
            return;
        }
        if (!starting) {
            starting = true;
            starterTimer = 0f;
            if (!clutchPressedOrNeutral) {
                events.add(VehicleEvent.START_IN_GEAR);
            }
        }
    }
}
