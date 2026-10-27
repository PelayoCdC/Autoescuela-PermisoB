package com.pelayo.autoescuela.theory;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.pelayo.autoescuela.data.PreguntaDef;
import com.pelayo.autoescuela.data.StatsData;

/** Guarda y actualiza el progreso de teoría en {@code local/save/stats.json}. */
public class StatsStore {

    public static final String FILE = "save/stats.json";
    /** Aciertos seguidos en repaso para retirar una pregunta de las falladas. */
    private static final int REVIEW_STREAK_TO_CLEAR = 2;

    private final FileHandle file;
    public StatsData data;
    /** Reloj (ms); se cambia en las pruebas. */
    public java.util.function.LongSupplier clock = System::currentTimeMillis;

    /** Espera hasta el siguiente repaso según la caja: 10 min tras un fallo, luego 1, 3, 7, 14 y 30 días. */
    private static final long MIN = 60_000L;
    private static final long DAY = 24 * 60 * MIN;
    public static final long[] INTERVALS = {10 * MIN, DAY, 3 * DAY, 7 * DAY, 14 * DAY, 30 * DAY};

    public StatsStore() {
        this(Gdx.files.local(FILE));
    }

    public StatsStore(FileHandle file) {
        this.file = file;
        load();
    }

    public void load() {
        if (file.exists()) {
            try {
                data = new Json().fromJson(StatsData.class, file);
            } catch (Exception e) {
                Gdx.app.error("StatsStore", "stats.json ilegible, se empieza de cero", e);
                data = new StatsData();
            }
        } else {
            data = new StatsData();
        }
        if (data == null) {
            data = new StatsData();
        }
    }

    public void save() {
        Json json = new Json(JsonWriter.OutputType.json);
        json.setUsePrototypes(false);
        file.parent().mkdirs();
        file.writeString(json.prettyPrint(data), false, "UTF-8");
    }

    /** Registra una respuesta (en cualquier modo). */
    public void recordAnswer(PreguntaDef q, boolean correct, boolean reviewMode) {
        StatsData.Card card = data.cards.get(q.id);
        if (card == null) {
            card = new StatsData.Card();
            data.cards.put(q.id, card);
        }
        card.seen++;
        if (correct) {
            card.box = Math.min(INTERVALS.length - 1, card.seen == 1 ? 1 : card.box + 1);
        } else {
            card.box = 0;
            card.wrong++;
        }
        card.due = clock.getAsLong() + INTERVALS[card.box];
        StatsData.TopicStats t = data.perTema.get(q.tema);
        if (t == null) {
            t = new StatsData.TopicStats();
            data.perTema.put(q.tema, t);
        }
        t.asked++;
        data.totalAnswered++;
        if (correct) {
            t.correct++;
            data.totalCorrect++;
            if (reviewMode && data.failedIds.contains(q.id, false)) {
                int streak = data.reviewStreak.get(q.id, 0) + 1;
                if (streak >= REVIEW_STREAK_TO_CLEAR) {
                    data.failedIds.removeValue(q.id, false);
                    data.reviewStreak.remove(q.id);
                } else {
                    data.reviewStreak.put(q.id, streak);
                }
            }
        } else {
            if (!data.failedIds.contains(q.id, false)) {
                data.failedIds.add(q.id);
            }
            data.reviewStreak.put(q.id, 0);
        }
    }

    /** Registra todas las respuestas de una sesión terminada y, si es examen, el resultado. */
    public void recordSession(TestSession session) {
        boolean review = session.mode == TestSession.Mode.REVIEW;
        for (int i = 0; i < session.questions.size; i++) {
            if (session.isAnswered(i) || session.mode == TestSession.Mode.EXAM) {
                recordAnswer(session.questions.get(i), session.isCorrect(i), review);
            }
        }
        if (session.mode == TestSession.Mode.EXAM) {
            StatsData.ExamRecord r = new StatsData.ExamRecord();
            r.date = System.currentTimeMillis();
            r.correct = session.correctCount();
            r.total = session.questions.size;
            r.passed = session.passed();
            r.seconds = (int) session.elapsed;
            data.examHistory.add(r);
        }
        save();
    }

    /** Preguntas a las que les toca repaso ahora. */
    public int dueCount() {
        long now = clock.getAsLong();
        int n = 0;
        for (StatsData.Card c : data.cards.values()) {
            n += c.due <= now ? 1 : 0;
        }
        return n;
    }

    public float topicAccuracy(String temaId) {
        StatsData.TopicStats t = data.perTema.get(temaId);
        return t == null || t.asked == 0 ? -1f : (float) t.correct / t.asked;
    }
}
