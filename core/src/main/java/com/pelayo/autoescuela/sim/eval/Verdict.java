package com.pelayo.autoescuela.sim.eval;

/**
 * Regla de calificación del examen práctico: no apto con una falta
 * eliminatoria, con dos deficientes o con diez leves.
 */
public final class Verdict {

    public static final int MAX_DEFICIENT = 1;
    public static final int MAX_MINOR = 9;

    private Verdict() {
    }

    public static boolean passes(EvaluationResult result) {
        return result.count(FaultSeverity.ELIMINATORIA) == 0
            && result.count(FaultSeverity.DEFICIENTE) <= MAX_DEFICIENT
            && result.count(FaultSeverity.LEVE) <= MAX_MINOR;
    }
}
