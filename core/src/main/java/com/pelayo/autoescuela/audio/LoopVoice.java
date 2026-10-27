package com.pelayo.autoescuela.audio;

import com.badlogic.gdx.audio.Sound;

/**
 * Un bucle que se mezcla con volumen y tono variables: suaviza el volumen
 * (ataque y caída propios, sin saltos que chasqueen), solo ocupa una voz del
 * sistema mientras se oye (arranca al pedir volumen y se para tras un rato en
 * silencio) y limita el tono al rango 0,5–2 que admite libGDX en Android.
 */
final class LoopVoice {

    private static final float START_THRESHOLD = 1e-3f;
    private static final float SILENT = 2e-4f;
    private static final float STOP_AFTER = 0.3f;

    private final Sound sound;
    private final float attack;
    private final float release;
    private long id = -1;
    private float volume;
    private float target;
    private float pitch = 1f;
    private float silentTime;

    /**
     * @param attack  constante de tiempo al subir el volumen (s)
     * @param release constante de tiempo al bajarlo (s)
     */
    LoopVoice(Sound sound, float attack, float release) {
        this.sound = sound;
        this.attack = attack;
        this.release = release;
    }

    void set(float targetVolume, float pitch) {
        this.target = Math.max(0f, targetVolume);
        this.pitch = Math.max(0.5f, Math.min(2f, pitch));
    }

    void update(float dt) {
        float tau = target > volume ? attack : release;
        volume += (target - volume) * (1f - (float) Math.exp(-dt / tau));
        if (id < 0) {
            if (target > START_THRESHOLD) {
                // Si no hay voces libres (o en Android aún no ha cargado) devuelve -1 y se reintenta
                id = sound.loop(volume, pitch, 0f);
                silentTime = 0f;
            } else {
                volume = 0f;
            }
            return;
        }
        if (target < SILENT && volume < SILENT) {
            silentTime += dt;
            if (silentTime > STOP_AFTER) {
                stop();
                return;
            }
        } else {
            silentTime = 0f;
        }
        sound.setVolume(id, volume);
        sound.setPitch(id, pitch);
    }

    float getVolume() {
        return volume;
    }

    boolean isPlaying() {
        return id >= 0;
    }

    void stop() {
        if (id >= 0) {
            sound.stop(id);
            id = -1;
        }
        volume = 0f;
        silentTime = 0f;
    }
}
