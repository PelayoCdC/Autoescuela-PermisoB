package com.pelayo.autoescuela.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.pelayo.autoescuela.AutoescuelaGame;
import com.pelayo.autoescuela.data.TemaDef;
import com.pelayo.autoescuela.theory.ExamRules;
import com.pelayo.autoescuela.theory.TestGenerator;
import com.pelayo.autoescuela.theory.TestSession;
import com.pelayo.autoescuela.ui.SkinFactory;

/** Menú de teoría: temas (leer / test), simulacro, repaso de falladas, estadísticas e importación. */
public class TheoryScreen extends ScreenAdapter {

    private static final int TOPIC_TEST_SIZE = 10;
    private static final int REVIEW_SIZE = 20;

    private final AutoescuelaGame game;
    private final Stage stage;

    public TheoryScreen(AutoescuelaGame game) {
        this.game = game;
        game.ensureTheory();
        stage = new Stage(new ExtendViewport(AutoescuelaGame.UI_WIDTH, AutoescuelaGame.UI_HEIGHT));

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(game.skin.getDrawable("bg"));
        root.pad(20f);
        stage.addActor(root);
        root.add(new Label(game.i18n.t("theory.title"), game.skin, "title")).padBottom(10f).row();

        // Acciones globales
        Table actions = new Table();
        TextButton exam = new TextButton(game.i18n.t("theory.exam"), game.skin);
        exam.addListener(change(() -> startSession(TestSession.Mode.EXAM,
            new TestGenerator(game.theory).officialExam())));
        int due = game.stats.dueCount();
        TextButton smart = new TextButton(game.i18n.t("theory.smart", due), game.skin, "small");
        smart.addListener(change(() -> startSession(TestSession.Mode.REVIEW, new TestGenerator(game.theory)
            .smartReview(game.stats.data, REVIEW_SIZE, game.stats.clock.getAsLong()))));
        int failed = game.stats.data.failedIds.size;
        TextButton review = new TextButton(game.i18n.t("theory.review", failed), game.skin, "small-secondary");
        review.setDisabled(failed == 0);
        review.addListener(change(() -> startSession(TestSession.Mode.REVIEW,
            new TestGenerator(game.theory).failedReview(game.stats.data, REVIEW_SIZE))));
        TextButton randomTest = new TextButton(game.i18n.t("theory.random"), game.skin, "small-secondary");
        randomTest.addListener(change(() -> startSession(TestSession.Mode.PRACTICE,
            new TestGenerator(game.theory).random(TOPIC_TEST_SIZE))));
        TextButton stats = new TextButton(game.i18n.t("theory.stats"), game.skin, "small-secondary");
        stats.addListener(change(() -> go(new StatsScreen(game))));
        TextButton importButton = new TextButton(game.i18n.t("theory.import"), game.skin, "small-secondary");
        importButton.addListener(change(() -> go(new ImportScreen(game))));
        actions.add(smart).width(270f).height(48f).padRight(10f);
        actions.add(exam).width(230f).height(48f).padRight(10f);
        actions.add(randomTest).width(150f).height(48f).padRight(10f);
        actions.add(review).width(170f).height(48f).padRight(10f);
        actions.add(stats).width(140f).height(48f).padRight(10f);
        actions.add(importButton).width(170f).height(48f);
        root.add(actions).padBottom(10f).row();
        root.add(readinessCard()).width(1000f).padBottom(10f).row();

        Label banner = new Label(game.i18n.t("theory.bank", game.theory.preguntas.size, game.theory.temas.size,
            ExamRules.QUESTIONS, ExamRules.MAX_FAILS), game.skin, "small");
        root.add(banner).padBottom(8f).row();

        // Lista de temas
        Table list = new Table();
        list.top();
        for (TemaDef tema : game.theory.temas) {
            Table row = new Table();
            row.setBackground(game.skin.getDrawable("panel"));
            row.pad(12f, 16f, 12f, 16f);
            float acc = game.stats.topicAccuracy(tema.id);
            String accText = acc < 0f ? game.i18n.t("theory.noData") : String.format("%.0f %%", acc * 100f);
            Label title = new Label(tema.orden + ". " + tema.titulo, game.skin, "bold");
            Label summary = new Label(tema.resumen == null ? "" : tema.resumen, game.skin, "small");
            summary.setWrap(true);
            Label accuracy = new Label(accText, game.skin, "small");
            accuracy.setColor(acc < 0f ? SkinFactory.TEXT_DIM : acc >= 0.9f ? SkinFactory.OK
                : acc >= 0.7f ? new com.badlogic.gdx.graphics.Color(1f, 0.85f, 0.4f, 1f) : SkinFactory.FAIL);
            TextButton read = new TextButton(game.i18n.t("theory.read"), game.skin, "small");
            read.addListener(change(() -> go(new TopicScreen(game, tema))));
            int available = game.theory.preguntasDe(tema.id).size;
            TextButton test = new TextButton(game.i18n.t("theory.test", Math.min(TOPIC_TEST_SIZE, available)),
                game.skin, "small");
            test.setDisabled(available == 0);
            test.addListener(change(() -> startSession(TestSession.Mode.PRACTICE,
                new TestGenerator(game.theory).byTopic(tema.id, TOPIC_TEST_SIZE))));
            row.add(title).left().expandX();
            row.add(accuracy).width(70f).right().padRight(10f);
            row.add(read).width(110f).height(38f).padRight(8f);
            row.add(test).width(130f).height(38f).row();
            row.add(summary).left().width(620f).colspan(4).padTop(2f);
            list.add(row).width(1000f).padBottom(8f).row();
        }
        ScrollPane scroll = new ScrollPane(list, game.skin);
        scroll.setFadeScrollBars(false);
        root.add(scroll).expand().fill().row();

        TextButton back = new TextButton(game.i18n.t("practice.back"), game.skin, "secondary");
        back.addListener(change(() -> go(new MainMenuScreen(game))));
        root.add(back).width(220f).height(46f).padTop(10f);
    }

    /** ¿Listo para el examen? Probabilidad estimada de aprobar, fallos esperados, banco visto y simulacros. */
    private Table readinessCard() {
        com.pelayo.autoescuela.theory.Readiness r = com.pelayo.autoescuela.theory.Readiness.of(game.theory,
            game.stats.data);
        Table card = new Table();
        card.setBackground(game.skin.getDrawable("panel"));
        card.pad(12f, 18f, 12f, 18f);
        com.badlogic.gdx.graphics.Color color = r.verdict == com.pelayo.autoescuela.theory.Readiness.Verdict.READY
            ? SkinFactory.OK : r.verdict == com.pelayo.autoescuela.theory.Readiness.Verdict.ALMOST
            ? new com.badlogic.gdx.graphics.Color(1f, 0.85f, 0.4f, 1f) : SkinFactory.FAIL;
        Label verdict = new Label(game.i18n.t("readiness." + r.verdict.name()), game.skin, "bold");
        verdict.setColor(color);
        com.badlogic.gdx.scenes.scene2d.ui.ProgressBar bar = new com.badlogic.gdx.scenes.scene2d.ui.ProgressBar(
            0f, 1f, 0.01f, false, game.skin,
            r.verdict == com.pelayo.autoescuela.theory.Readiness.Verdict.READY ? "ok-horizontal" : "default-horizontal");
        bar.setValue(r.passProbability);
        // Con pocas respuestas la estimación no dice nada: se pide responder algo más
        boolean enough = game.stats.data.totalAnswered >= 30;
        Label detail = new Label(enough ? game.i18n.t("readiness.detail", Math.round(r.passProbability * 100f),
            String.format(java.util.Locale.ROOT, "%.1f", r.expectedFails).replace('.', ','), ExamRules.MAX_FAILS,
            Math.round(r.coverage * 100f), r.recentPassed, r.recentTotal)
            : game.i18n.t("readiness.noData", game.stats.data.totalAnswered), game.skin, "small");
        if (!enough) {
            bar.setValue(0f);
        }
        detail.setWrap(true);
        card.add(verdict).left().padRight(16f);
        card.add(bar).width(260f).padRight(16f);
        card.add(detail).growX().left();
        return card;
    }

    private void startSession(TestSession.Mode mode, com.badlogic.gdx.utils.Array<com.pelayo.autoescuela.data.PreguntaDef> qs) {
        if (qs.size == 0) {
            return;
        }
        go(new TestScreen(game, new TestSession(mode, TestGenerator.shuffleOptions(qs))));
    }

    private void go(com.badlogic.gdx.Screen screen) {
        game.setScreen(screen);
        dispose();
    }

    private static ChangeListener change(Runnable action) {
        return new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        };
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            go(new MainMenuScreen(game));
            return;
        }
        ScreenUtils.clear(SkinFactory.BG);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
