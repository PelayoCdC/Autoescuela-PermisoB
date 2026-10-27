package com.pelayo.autoescuela.input;

import com.badlogic.gdx.math.MathUtils;

/**
 * Convierte una tecla (todo o nada) en un pedal analógico 0..1 con rampas
 * distintas al pisar y al soltar. Con el embrague la rampa de soltado es lenta
 * para que el punto de fricción sea controlable con teclado.
 */
public class PedalSmoother {

    private final float pressRate;
    private final float releaseRate;
    private float value;

    /**
     * @param pressRate   recorrido por segundo al pisar (p.ej. 4 = de 0 a 1 en 0,25 s)
     * @param releaseRate recorrido por segundo al soltar
     */
    public PedalSmoother(float pressRate, float releaseRate) {
        this.pressRate = pressRate;
        this.releaseRate = releaseRate;
    }

    /** Acerca el valor al objetivo con la rampa que toque y lo devuelve. */
    public float update(float target, float dt) {
        target = MathUtils.clamp(target, 0f, 1f);
        if (target > value) {
            value = Math.min(target, value + pressRate * dt);
        } else if (target < value) {
            value = Math.max(target, value - releaseRate * dt);
        }
        return value;
    }

    public float get() {
        return value;
    }

    public void set(float value) {
        this.value = MathUtils.clamp(value, 0f, 1f);
    }
}
