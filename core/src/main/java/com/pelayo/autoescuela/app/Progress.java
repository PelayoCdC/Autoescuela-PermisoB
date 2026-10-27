package com.pelayo.autoescuela.app;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/**
 * Progreso de las prácticas: lecciones completadas y escenarios superados
 * (APTO al menos una vez). Se guarda en {@link Preferences} aparte de los
 * ajustes, para que restablecer los ajustes no borre el progreso.
 */
public class Progress {

    private static final String PREFS = "autoescuela-progress";
    private final Preferences prefs;

    public Progress() {
        this(Gdx.app.getPreferences(PREFS));
    }

    public Progress(Preferences prefs) {
        this.prefs = prefs;
    }

    public boolean isLessonDone(String id) {
        return prefs.getBoolean("lesson." + id, false);
    }

    public void markLessonDone(String id) {
        prefs.putBoolean("lesson." + id, true);
        prefs.flush();
    }

    public boolean isPassed(String id) {
        return prefs.getBoolean("passed." + id, false);
    }

    public void markPassed(String id) {
        prefs.putBoolean("passed." + id, true);
        prefs.flush();
    }
}
