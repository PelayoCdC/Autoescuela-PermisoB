package com.pelayo.autoescuela.theory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import com.pelayo.autoescuela.HeadlessTestApp;
import com.pelayo.autoescuela.data.PreguntaDef;
import com.pelayo.autoescuela.data.TemaDef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Repositorio, generador de tests, sesiones, estadísticas e importador. */
class TheoryTest {

    private static HeadlessApplication app;
    private static TheoryRepository repo;

    @BeforeAll
    static void boot() {
        app = HeadlessTestApp.start();
        repo = new TheoryRepository();
        repo.loadTemas(Gdx.files.internal(TheoryRepository.TEMAS_FILE));
        repo.loadPreguntas(Gdx.files.internal(TheoryRepository.PREGUNTAS_FILE));
    }

    @AfterAll
    static void shutdown() {
        app.exit();
    }

    @Test
    void bankIsValidAndEveryTopicHasQuestions() {
        assertEquals(12, repo.temas.size);
        assertTrue(repo.preguntas.size >= 300, "banco: " + repo.preguntas.size);
        for (TemaDef t : repo.temas) {
            assertTrue(repo.preguntasDe(t.id).size >= 25, "el tema " + t.id + " tiene pocas preguntas");
            assertNotNull(t.titulo);
            assertTrue(t.secciones.size > 0, "el tema " + t.id + " no tiene secciones");
        }
        for (PreguntaDef p : repo.preguntas) {
            assertTrue(p.explicacion != null && !p.explicacion.isBlank(), p.id + " sin explicación");
            if (p.imagen != null) {
                assertTrue(Gdx.files.internal(p.imagen).exists(), p.id + ": imagen inexistente " + p.imagen);
            }
        }
    }

    @Test
    void dgtBankLoadsWithSourceAndUniqueIds() {
        TheoryRepository full = TheoryRepository.loadDefault();
        int dgt = 0;
        int external = 0;
        for (PreguntaDef p : full.preguntas) {
            if (p.id.startsWith("dgt")) {
                dgt++;
                assertTrue(p.fuente.startsWith("Revista"), p.id + " sin fuente de la revista");
                assertTrue(p.explicacion != null && !p.explicacion.isBlank(), p.id + " sin explicación");
            } else if (p.id.startsWith("sede") || p.id.startsWith("tt_")) {
                external++;
                assertTrue(p.fuente != null && !"propia".equals(p.fuente), p.id + " sin fuente");
                assertTrue(p.explicacion != null && !p.explicacion.isBlank(), p.id + " sin explicación");
                if (p.imagen != null) {
                    assertTrue(Gdx.files.internal(p.imagen).exists(), p.id + ": imagen inexistente " + p.imagen);
                }
            }
        }
        assertTrue(dgt >= 150, "preguntas de la DGT: " + dgt);
        // los bancos externos son opcionales: no se distribuyen con el repositorio público
        boolean hayExternos = false;
        for (String file : TheoryRepository.EXTERNAL_FILES) {
            hayExternos |= Gdx.files.internal(file).exists();
        }
        if (hayExternos) {
            assertTrue(external >= 1800, "preguntas de bancos externos: " + external);
        }
        assertEquals(repo.preguntas.size + dgt + external, full.preguntas.size, "ids repetidos entre bancos");
    }

    @Test
    void shuffledOptionsKeepTheCorrectAnswer() {
        RandomXS128 random = new RandomXS128(7);
        int moved = 0;
        for (PreguntaDef p : repo.preguntas) {
            PreguntaDef s = p.withShuffledOptions(random);
            assertEquals(p.id, s.id);
            assertEquals(p.opciones[p.correcta], s.opciones[s.correcta], p.id + ": la correcta ha cambiado");
            ObjectSet<String> a = new ObjectSet<>();
            a.addAll(p.opciones);
            for (String o : s.opciones) {
                assertTrue(a.contains(o), p.id + ": opción perdida");
            }
            if (s.correcta != p.correcta) {
                moved++;
            }
        }
        assertTrue(moved > repo.preguntas.size / 3, "el barajado apenas mueve la correcta: " + moved);
    }

    @Test
    void examHasThirtyUniqueQuestionsCoveringTopics() {
        TestGenerator gen = new TestGenerator(repo, new RandomXS128(42));
        Array<PreguntaDef> exam = gen.officialExam();
        assertEquals(ExamRules.QUESTIONS, exam.size);
        ObjectSet<String> ids = new ObjectSet<>();
        ObjectSet<String> topics = new ObjectSet<>();
        for (PreguntaDef p : exam) {
            assertTrue(ids.add(p.id), "repetida: " + p.id);
            topics.add(p.tema);
        }
        assertEquals(repo.temas.size, topics.size, "el simulacro debe tocar todos los temas");
        // Misma semilla, mismo examen
        Array<PreguntaDef> again = new TestGenerator(repo, new RandomXS128(42)).officialExam();
        for (int i = 0; i < exam.size; i++) {
            assertEquals(exam.get(i).id, again.get(i).id);
        }
    }

    @Test
    void sessionScoringAndExamVerdict() {
        Array<PreguntaDef> qs = new TestGenerator(repo, new RandomXS128(1)).officialExam();
        TestSession s = new TestSession(TestSession.Mode.EXAM, qs);
        assertEquals(ExamRules.TIME_LIMIT_SECONDS, s.timeLimit);
        for (int i = 0; i < qs.size; i++) {
            s.answer(i, i < 4 ? (qs.get(i).correcta + 1) % 3 : qs.get(i).correcta); // 4 fallos
        }
        s.finish();
        assertEquals(26, s.correctCount());
        assertEquals(4, s.failCount());
        assertFalse(s.passed());
        TestSession ok = new TestSession(TestSession.Mode.EXAM, qs);
        for (int i = 0; i < qs.size; i++) {
            ok.answer(i, i < 3 ? (qs.get(i).correcta + 1) % 3 : qs.get(i).correcta);
        }
        ok.finish();
        assertTrue(ok.passed());
        // El tiempo agota el examen
        TestSession slow = new TestSession(TestSession.Mode.EXAM, qs);
        assertTrue(slow.tick(ExamRules.TIME_LIMIT_SECONDS + 1f));
        assertTrue(slow.isFinished());
    }

    @Test
    void statsRoundTripAndFailedReview() {
        FileHandle file = Gdx.files.local("cache/test_stats.json");
        file.delete();
        StatsStore store = new StatsStore(file);
        PreguntaDef q = repo.preguntas.first();
        store.recordAnswer(q, false, false);
        store.recordAnswer(q, true, false);
        store.save();
        StatsStore loaded = new StatsStore(file);
        assertEquals(2, loaded.data.totalAnswered);
        assertEquals(1, loaded.data.totalCorrect);
        assertTrue(loaded.data.failedIds.contains(q.id, false));
        assertEquals(0.5f, loaded.topicAccuracy(q.tema), 1e-6f);
        // Dos aciertos seguidos en repaso la retiran de las falladas
        loaded.recordAnswer(q, true, true);
        assertTrue(loaded.data.failedIds.contains(q.id, false));
        loaded.recordAnswer(q, true, true);
        assertFalse(loaded.data.failedIds.contains(q.id, false));
        Array<PreguntaDef> review = new TestGenerator(repo).failedReview(loaded.data, 10);
        assertEquals(0, review.size);
        file.delete();
    }

    @Test
    void importerReadsJsonAndCsvAndDeduplicates() {
        FileHandle user = Gdx.files.local("cache/test_user.json");
        user.delete();
        TheoryRepository local = new TheoryRepository();
        local.loadTemas(Gdx.files.internal(TheoryRepository.TEMAS_FILE));
        local.loadPreguntas(Gdx.files.internal(TheoryRepository.PREGUNTAS_FILE));
        QuestionImporter importer = new QuestionImporter(local, user);

        FileHandle csv = Gdx.files.local("cache/test_import.csv");
        csv.writeString("tema;enunciado;opcion1;opcion2;opcion3;correcta;explicacion;imagen\n"
            + "t02;¿Pregunta nueva de prueba?;Uno;Dos;Tres;2;Porque sí;\n"
            + "t02;¿PREGUNTA NUEVA DE PRUEBA?;Uno;Dos;Tres;B;duplicada por enunciado;\n"
            + "t99;Tema inexistente;a;b;c;1;;\n"
            + "t02;Solo dos opciones;a;b\n", false, "UTF-8");
        QuestionImporter.Report r = importer.importFile(csv);
        assertEquals(1, r.added, r.toString());
        assertEquals(1, r.duplicates, r.toString());
        assertEquals(1, r.invalid, r.toString());
        assertTrue(user.exists());

        FileHandle json = Gdx.files.local("cache/test_import.json");
        json.writeString("[{\"tema\":\"t03\",\"enunciado\":\"Otra pregunta importada\",\"opciones\":[\"a\",\"b\",\"c\"],"
            + "\"correcta\":0,\"explicacion\":\"x\"},"
            + "{\"tema\":\"t03\",\"enunciado\":\"¿Pregunta nueva de prueba?\",\"opciones\":[\"a\",\"b\",\"c\"],\"correcta\":0}]",
            false, "UTF-8");
        QuestionImporter.Report r2 = importer.importFile(json);
        assertEquals(1, r2.added, r2.toString());
        assertEquals(1, r2.duplicates, r2.toString());
        assertEquals(2, importer.loadUser().size);
        // El repositorio ya ve las nuevas
        assertEquals(repo.preguntas.size + 2, local.preguntas.size);
        csv.delete();
        json.delete();
        user.delete();
    }

    // --- Repaso espaciado y preparación ---------------------------------------------------

    private StatsStore freshStats(long[] now) {
        FileHandle file = Gdx.files.local("cache/test_stats_srs.json");
        file.delete();
        StatsStore stats = new StatsStore(file);
        stats.clock = () -> now[0];
        return stats;
    }

    @Test
    void cardsMoveUpTheBoxesAndFallBackOnAMistake() {
        long[] now = {1_000_000L};
        StatsStore stats = freshStats(now);
        PreguntaDef q = repo.preguntas.first();
        stats.recordAnswer(q, true, false);
        assertEquals(1, stats.data.cards.get(q.id).box);
        assertEquals(0, stats.dueCount(), "acertada: no vuelve hasta mañana");
        now[0] += StatsStore.INTERVALS[1];
        assertEquals(1, stats.dueCount());
        stats.recordAnswer(q, true, true);
        assertEquals(2, stats.data.cards.get(q.id).box);
        stats.recordAnswer(q, false, true);
        assertEquals(0, stats.data.cards.get(q.id).box, "un fallo la devuelve a la primera caja");
        now[0] += StatsStore.INTERVALS[0];
        assertEquals(1, stats.dueCount(), "fallada: vuelve a los 10 minutos");
    }

    @Test
    void smartReviewPutsDueCardsFirstAndFillsWithNewOnes() {
        long[] now = {5_000_000L};
        StatsStore stats = freshStats(now);
        for (int i = 0; i < 5; i++) {
            stats.recordAnswer(repo.preguntas.get(i), false, false);
        }
        now[0] += StatsStore.INTERVALS[0];
        Array<PreguntaDef> qs = new TestGenerator(repo, new RandomXS128(3)).smartReview(stats.data, 20, now[0]);
        assertEquals(20, qs.size);
        ObjectSet<String> ids = new ObjectSet<>();
        for (PreguntaDef p : qs) {
            assertTrue(ids.add(p.id), "sin repetidas: " + p.id);
        }
        for (int i = 0; i < 5; i++) {
            assertTrue(ids.contains(repo.preguntas.get(i).id), "las falladas vencidas entran en el repaso");
        }
        int fresh = 0;
        for (PreguntaDef p : qs) {
            fresh += stats.data.cards.containsKey(p.id) ? 0 : 1;
        }
        assertEquals(15, fresh, "el resto, preguntas nuevas");
    }

    @Test
    void readinessGrowsWithGoodResults() {
        long[] now = {0L};
        StatsStore stats = freshStats(now);
        Readiness empty = Readiness.of(repo, stats.data);
        assertEquals(Readiness.Verdict.NOT_YET, empty.verdict);
        assertEquals(0f, empty.coverage, 1e-4f);
        // 400 respuestas con un 97 % de aciertos y tres simulacros aprobados
        for (int i = 0; i < 400; i++) {
            stats.recordAnswer(repo.preguntas.get(i % repo.preguntas.size), i % 33 != 0, false);
        }
        for (int i = 0; i < 3; i++) {
            com.pelayo.autoescuela.data.StatsData.ExamRecord r = new com.pelayo.autoescuela.data.StatsData.ExamRecord();
            r.passed = true;
            r.correct = 29;
            r.total = 30;
            stats.data.examHistory.add(r);
        }
        Readiness good = Readiness.of(repo, stats.data);
        assertTrue(good.expectedFails < 2f, "fallos esperados: " + good.expectedFails);
        assertTrue(good.passProbability > 0.85f, "probabilidad: " + good.passProbability);
        assertEquals(Readiness.Verdict.READY, good.verdict);
        assertTrue(good.passProbability > empty.passProbability);
    }
}
