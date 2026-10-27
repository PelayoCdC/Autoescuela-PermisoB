package com.pelayo.autoescuela.app;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;

/**
 * Vibración del móvil para notar los mandos sin mirarlos: un toque suave al
 * entrar en el punto de fricción del embrague y uno largo al calar o chocar.
 * Solo en Android y con el ajuste activado.
 */
public final class Haptics {

    /** Toque al entrar en la franja de fricción. */
    public static final int BITE = 18;
    /** Golpe contra algo. */
    public static final int HIT = 90;
    /** Motor calado. */
    public static final int STALL = 320;

    private Haptics() {
    }

    public static void pulse(Settings settings, int millis) {
        if (settings.isVibration() && Gdx.app.getType() == Application.ApplicationType.Android) {
            Gdx.input.vibrate(millis);
        }
    }
}
