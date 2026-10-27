package com.pelayo.autoescuela.sim;

/**
 * Acumulador de tiempo para ejecutar la simulación a paso fijo
 * independientemente de la tasa de refresco. Limita los pasos por frame para
 * no entrar en espiral tras un parón.
 */
public class FixedStepper {

    private final float dt;
    private final int maxStepsPerFrame;
    private float accumulator = 0f;

    public FixedStepper(float dt, int maxStepsPerFrame) {
        this.dt = dt;
        this.maxStepsPerFrame = maxStepsPerFrame;
    }

    /** Devuelve cuántos pasos fijos toca ejecutar para este frame. */
    public int advance(float frameDelta) {
        accumulator += Math.min(frameDelta, 0.1f);
        int steps = 0;
        while (accumulator >= dt && steps < maxStepsPerFrame) {
            accumulator -= dt;
            steps++;
        }
        if (steps == maxStepsPerFrame) {
            accumulator = 0f; // descartamos el resto: mejor ralentizar que explotar
        }
        return steps;
    }

    public float getDt() {
        return dt;
    }

    public void reset() {
        accumulator = 0f;
    }
}
