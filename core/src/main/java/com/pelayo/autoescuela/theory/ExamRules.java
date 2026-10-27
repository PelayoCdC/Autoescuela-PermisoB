package com.pelayo.autoescuela.theory;

/** Reglas del examen teórico oficial del permiso B. */
public final class ExamRules {

    public static final int QUESTIONS = 30;
    public static final int TIME_LIMIT_SECONDS = 30 * 60;
    public static final int MAX_FAILS = 3;

    private ExamRules() {
    }

    public static boolean passes(int fails) {
        return fails <= MAX_FAILS;
    }
}
