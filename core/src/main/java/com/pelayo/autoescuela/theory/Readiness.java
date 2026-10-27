package com.pelayo.autoescuela.theory;

import com.badlogic.gdx.utils.Array;
import com.pelayo.autoescuela.data.StatsData;
import com.pelayo.autoescuela.data.TemaDef;

/**
 * ¿Estás listo para el examen? Estima los fallos de un examen de 30 preguntas
 * con los aciertos de cada tema (suavizados hacia un 75 % mientras hay pocos
 * datos) y el peso de cada tema en el examen, y de ahí la probabilidad de no
 * pasar de 3 fallos (aproximación de Poisson). Añade cuánto banco has visto y
 * los últimos simulacros.
 */
public final class Readiness {

    public enum Verdict { NOT_YET, ALMOST, READY }

    /** Aciertos supuestos de un tema sin datos y cuántas respuestas "pesa" esa suposición. */
    private static final float PRIOR = 0.75f;
    private static final float PRIOR_WEIGHT = 4f;
    /** Mínimo de respuestas y de simulacros para dar a alguien por preparado. */
    private static final int MIN_ANSWERS = 300;
    private static final int RECENT_EXAMS = 3;

    public final float expectedFails;
    public final float passProbability;
    /** Fracción del banco que ya ha salido alguna vez. */
    public final float coverage;
    /** Simulacros aprobados de los últimos tres (o menos si no hay tantos). */
    public final int recentPassed;
    public final int recentTotal;
    public final Verdict verdict;

    private Readiness(float expectedFails, float passProbability, float coverage, int recentPassed, int recentTotal,
                      Verdict verdict) {
        this.expectedFails = expectedFails;
        this.passProbability = passProbability;
        this.coverage = coverage;
        this.recentPassed = recentPassed;
        this.recentTotal = recentTotal;
        this.verdict = verdict;
    }

    public static Readiness of(TheoryRepository repo, StatsData stats) {
        int bank = Math.max(1, repo.preguntas.size);
        float expected = 0f;
        for (TemaDef t : repo.temas) {
            int size = repo.preguntasDe(t.id).size;
            if (size == 0) {
                continue;
            }
            float quota = ExamRules.QUESTIONS * (float) size / bank;
            StatsData.TopicStats ts = stats.perTema.get(t.id);
            float asked = ts == null ? 0f : ts.asked;
            float correct = ts == null ? 0f : ts.correct;
            float acc = (correct + PRIOR * PRIOR_WEIGHT) / (asked + PRIOR_WEIGHT);
            expected += quota * (1f - acc);
        }
        float pass = poissonAtMost(expected, ExamRules.MAX_FAILS);
        float coverage = Math.min(1f, stats.cards.size / (float) bank);
        Array<StatsData.ExamRecord> history = stats.examHistory;
        int total = Math.min(RECENT_EXAMS, history.size);
        int passed = 0;
        for (int i = history.size - total; i < history.size; i++) {
            passed += history.get(i).passed ? 1 : 0;
        }
        Verdict verdict;
        if (pass >= 0.85f && stats.totalAnswered >= MIN_ANSWERS && total == RECENT_EXAMS && passed == RECENT_EXAMS) {
            verdict = Verdict.READY;
        } else if (pass >= 0.6f && stats.totalAnswered >= MIN_ANSWERS / 3) {
            verdict = Verdict.ALMOST;
        } else {
            verdict = Verdict.NOT_YET;
        }
        return new Readiness(expected, pass, coverage, passed, total, verdict);
    }

    /** P(X ≤ k) con X ~ Poisson(λ). */
    static float poissonAtMost(float lambda, int k) {
        double term = Math.exp(-lambda);
        double sum = term;
        for (int i = 1; i <= k; i++) {
            term *= lambda / i;
            sum += term;
        }
        return (float) Math.min(1.0, sum);
    }
}
