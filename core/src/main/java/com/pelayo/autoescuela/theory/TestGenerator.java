package com.pelayo.autoescuela.theory;

import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.Array;
import com.pelayo.autoescuela.data.PreguntaDef;
import com.pelayo.autoescuela.data.StatsData;
import com.pelayo.autoescuela.data.TemaDef;

/** Elige preguntas para un test, un repaso o un simulacro de examen. */
public class TestGenerator {

    private final TheoryRepository repo;
    private final RandomXS128 random;

    public TestGenerator(TheoryRepository repo) {
        this(repo, new RandomXS128());
    }

    public TestGenerator(TheoryRepository repo, RandomXS128 random) {
        this.repo = repo;
        this.random = random;
    }

    /** Copias de las preguntas con las opciones barajadas (ver {@link PreguntaDef#withShuffledOptions}). */
    public static Array<PreguntaDef> shuffleOptions(Array<PreguntaDef> questions) {
        RandomXS128 random = new RandomXS128();
        Array<PreguntaDef> out = new Array<>(questions.size);
        for (PreguntaDef q : questions) {
            out.add(q.withShuffledOptions(random));
        }
        return out;
    }

    /** Fisher-Yates con el generador propio (Array.shuffle usa el global, no reproducible). */
    private <T> void shuffle(Array<T> list) {
        for (int i = list.size - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            list.swap(i, j);
        }
    }

    /** Hasta n preguntas de un tema, barajadas. */
    public Array<PreguntaDef> byTopic(String temaId, int n) {
        Array<PreguntaDef> pool = new Array<>(repo.preguntasDe(temaId));
        shuffle(pool);
        pool.truncate(Math.min(n, pool.size));
        return pool;
    }

    /** n preguntas al azar de todo el banco. */
    public Array<PreguntaDef> random(int n) {
        Array<PreguntaDef> pool = new Array<>(repo.preguntas);
        shuffle(pool);
        pool.truncate(Math.min(n, pool.size));
        return pool;
    }

    /** Hasta n preguntas de las falladas pendientes de repaso. */
    public Array<PreguntaDef> failedReview(StatsData stats, int n) {
        Array<PreguntaDef> pool = new Array<>();
        for (String id : stats.failedIds) {
            PreguntaDef p = repo.pregunta(id);
            if (p != null) {
                pool.add(p);
            }
        }
        shuffle(pool);
        pool.truncate(Math.min(n, pool.size));
        return pool;
    }

    /**
     * Repaso inteligente de n preguntas: primero las que toca repasar (las más atrasadas y de caja más baja),
     * hasta un 60 % del test, y el resto preguntas nuevas, elegidas sobre todo de los temas con peores
     * resultados y más peso en el examen. Si no hay nuevas, se adelantan las que vencen antes.
     */
    public Array<PreguntaDef> smartReview(StatsData stats, int n, long now) {
        Array<PreguntaDef> result = new Array<>();
        Array<String> due = new Array<>();
        for (com.badlogic.gdx.utils.ObjectMap.Entry<String, StatsData.Card> e : stats.cards) {
            if (e.value.due <= now && repo.pregunta(e.key) != null) {
                due.add(e.key);
            }
        }
        due.sort((a, b) -> {
            StatsData.Card ca = stats.cards.get(a), cb = stats.cards.get(b);
            return ca.box != cb.box ? Integer.compare(ca.box, cb.box) : Long.compare(ca.due, cb.due);
        });
        int dueQuota = Math.max(1, Math.round(n * 0.6f));
        for (int i = 0; i < due.size && result.size < dueQuota; i++) {
            result.add(repo.pregunta(due.get(i)));
        }
        // Nuevas: por tema, con peso = parte del examen x lo que falta para dominarlo
        Array<TemaDef> temas = new Array<>();
        Array<Array<PreguntaDef>> unseen = new Array<>();
        float[] weight = new float[repo.temas.size];
        float total = 0f;
        for (TemaDef t : repo.temas) {
            Array<PreguntaDef> pool = new Array<>();
            for (PreguntaDef p : repo.preguntasDe(t.id)) {
                if (!stats.cards.containsKey(p.id)) {
                    pool.add(p);
                }
            }
            shuffle(pool);
            StatsData.TopicStats ts = stats.perTema.get(t.id);
            float acc = ts == null ? 0.75f : (ts.correct + 3f) / (ts.asked + 4f);
            weight[temas.size] = pool.size == 0 ? 0f : repo.preguntasDe(t.id).size * (1.05f - acc);
            total += weight[temas.size];
            temas.add(t);
            unseen.add(pool);
        }
        while (result.size < n && total > 0f) {
            float pick = random.nextFloat() * total;
            int i = 0;
            while (i < temas.size - 1 && pick > weight[i]) {
                pick -= weight[i];
                i++;
            }
            Array<PreguntaDef> pool = unseen.get(i);
            if (pool.size == 0) {
                total -= weight[i];
                weight[i] = 0f;
                continue;
            }
            result.add(pool.pop());
            if (pool.size == 0) {
                total -= weight[i];
                weight[i] = 0f;
            }
        }
        // Sin nuevas: las que vencen antes
        if (result.size < n) {
            Array<String> rest = new Array<>();
            for (String id : stats.cards.keys()) {
                if (!due.contains(id, false) && repo.pregunta(id) != null) {
                    rest.add(id);
                }
            }
            rest.sort((a, b) -> Long.compare(stats.cards.get(a).due, stats.cards.get(b).due));
            for (int i = 0; i < rest.size && result.size < n; i++) {
                result.add(repo.pregunta(rest.get(i)));
            }
        }
        shuffle(result);
        return result;
    }

    /**
     * Simulacro oficial: 30 preguntas sin repetir, repartidas entre los temas
     * en proporción a su tamaño y con al menos una de cada tema que tenga
     * preguntas (mientras haya sitio). Si el banco tiene menos de 30, las da todas.
     */
    public Array<PreguntaDef> officialExam() {
        return exam(ExamRules.QUESTIONS);
    }

    public Array<PreguntaDef> exam(int total) {
        Array<PreguntaDef> result = new Array<>();
        if (repo.preguntas.size <= total) {
            result.addAll(repo.preguntas);
            shuffle(result);
            return result;
        }
        Array<TemaDef> temas = new Array<>();
        for (TemaDef t : repo.temas) {
            if (repo.preguntasDe(t.id).size > 0) {
                temas.add(t);
            }
        }
        int[] quota = new int[temas.size];
        int assigned = 0;
        // Una por tema primero
        for (int i = 0; i < temas.size && assigned < total; i++) {
            quota[i] = 1;
            assigned++;
        }
        // El resto proporcional al tamaño, por restos mayores
        int remaining = total - assigned;
        float[] exact = new float[temas.size];
        int poolTotal = repo.preguntas.size;
        for (int i = 0; i < temas.size; i++) {
            exact[i] = remaining * (float) repo.preguntasDe(temas.get(i).id).size / poolTotal;
            int whole = (int) exact[i];
            whole = Math.min(whole, repo.preguntasDe(temas.get(i).id).size - quota[i]);
            quota[i] += whole;
            assigned += whole;
            exact[i] -= whole;
        }
        while (assigned < total) {
            int best = -1;
            for (int i = 0; i < temas.size; i++) {
                if (quota[i] < repo.preguntasDe(temas.get(i).id).size
                    && (best < 0 || exact[i] > exact[best])) {
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            quota[best]++;
            exact[best] = -1f;
            assigned++;
        }
        for (int i = 0; i < temas.size; i++) {
            result.addAll(byTopic(temas.get(i).id, quota[i]));
        }
        shuffle(result);
        return result;
    }
}
