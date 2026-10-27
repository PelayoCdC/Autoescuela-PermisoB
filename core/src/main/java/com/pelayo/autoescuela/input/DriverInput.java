package com.pelayo.autoescuela.input;

/**
 * Fuente de mandos del conductor (teclado, mando, táctil...). Se consulta una
 * vez por paso fijo de simulación, nunca por frame, para que la física no
 * dependa de la tasa de refresco.
 */
public interface DriverInput {

    /**
     * Rellena {@code out} con los mandos de este paso. Los ejes se dejan en su
     * valor actual (suavizado si procede); los pulsos se ponen a true solo si
     * se activaron desde la última consulta.
     *
     * @param dt duración del paso (s), para las rampas de los pedales
     */
    void poll(DriverInputState out, float dt);
}
