package com.pelayo.autoescuela.app;

/**
 * Voz del examinador: lee en alto las indicaciones. Cada plataforma pone la
 * suya (Android: TextToSpeech; Windows: la síntesis de voz del sistema); por
 * defecto no dice nada.
 */
public interface Speech {

    /** Dice el texto, cortando lo que estuviera diciendo. */
    void speak(String text);

    /** Calla. */
    default void stop() {
    }

    Speech NONE = text -> { };
}
