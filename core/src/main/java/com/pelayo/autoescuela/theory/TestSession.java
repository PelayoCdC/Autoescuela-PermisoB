package com.pelayo.autoescuela.theory;

import com.badlogic.gdx.utils.Array;
import com.pelayo.autoescuela.data.PreguntaDef;

/** Un test en curso: preguntas, respuestas dadas, cronómetro y corrección. */
public class TestSession {

    public enum Mode { PRACTICE, EXAM, REVIEW }

    public final Mode mode;
    public final Array<PreguntaDef> questions;
    /** Respuesta por pregunta: 0..2, o -1 si no contestada. */
    public final int[] answers;
    /** Límite de tiempo en segundos (0 = sin límite). */
    public final int timeLimit;
    public float elapsed;
    public int index;
    private boolean finished;

    public TestSession(Mode mode, Array<PreguntaDef> questions) {
        this.mode = mode;
        this.questions = questions;
        this.answers = new int[questions.size];
        java.util.Arrays.fill(answers, -1);
        this.timeLimit = mode == Mode.EXAM ? ExamRules.TIME_LIMIT_SECONDS : 0;
    }

    public PreguntaDef current() {
        return questions.get(index);
    }

    public boolean isAnswered(int i) {
        return answers[i] >= 0;
    }

    public void answer(int i, int option) {
        if (!finished) {
            answers[i] = option;
        }
    }

    public boolean isCorrect(int i) {
        return answers[i] >= 0 && answers[i] == questions.get(i).correcta;
    }

    /** Avanza el cronómetro; devuelve true si se ha agotado el tiempo. */
    public boolean tick(float delta) {
        if (finished) {
            return false;
        }
        elapsed += delta;
        if (timeLimit > 0 && elapsed >= timeLimit) {
            finish();
            return true;
        }
        return false;
    }

    public int remainingSeconds() {
        return timeLimit > 0 ? Math.max(0, (int) (timeLimit - elapsed)) : 0;
    }

    public int correctCount() {
        int n = 0;
        for (int i = 0; i < answers.length; i++) {
            if (isCorrect(i)) {
                n++;
            }
        }
        return n;
    }

    /** Falladas: contestadas mal o, al terminar, sin contestar. */
    public int failCount() {
        return answers.length - correctCount();
    }

    public int answeredCount() {
        int n = 0;
        for (int a : answers) {
            if (a >= 0) {
                n++;
            }
        }
        return n;
    }

    public boolean allAnswered() {
        return answeredCount() == answers.length;
    }

    public void finish() {
        finished = true;
    }

    public boolean isFinished() {
        return finished;
    }

    /** En examen: apto con 3 fallos como máximo. En práctica no hay veredicto. */
    public boolean passed() {
        return ExamRules.passes(failCount());
    }
}
