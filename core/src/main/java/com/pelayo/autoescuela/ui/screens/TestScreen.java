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
import com.pelayo.autoescuela.theory.ExamRules;
import com.pelayo.autoescuela.theory.TestSession;
import com.pelayo.autoescuela.ui.SkinFactory;
import com.pelayo.autoescuela.ui.widgets.QuestionCard;

/**
 * Un test en curso. En práctica y repaso se corrige cada pregunta al
 * responder; en examen no hay corrección hasta el final, hay cronómetro de
 * 30 minutos y se puede navegar entre preguntas.
 */
public class TestScreen extends ScreenAdapter {

    private final AutoescuelaGame game;
    private final TestSession session;
    private final Stage stage;
    private final QuestionCard card;
    private final Label header;
    private com.badlogic.gdx.scenes.scene2d.ui.ProgressBar progress;
    private final Label timer;
    private final TextButton next;
    private final TextButton previous;
    private final TextButton finish;
    private boolean revealed;

    public TestScreen(AutoescuelaGame game, TestSession session) {
        this.game = game;
        this.session = session;
        stage = new Stage(new ExtendViewport(AutoescuelaGame.UI_WIDTH, AutoescuelaGame.UI_HEIGHT));

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(game.skin.getDrawable("bg"));
        root.pad(16f);
        stage.addActor(root);

        Table top = new Table();
        header = new Label("", game.skin, "default");
        timer = new Label("", game.skin, "default");
        top.add(header).left().expandX();
        top.add(timer).right();
        root.add(top).width(960f).padBottom(6f).row();
        progress = new com.badlogic.gdx.scenes.scene2d.ui.ProgressBar(0f, 1f, 0.001f, false, game.skin);
        progress.setAnimateDuration(0.25f);
        root.add(progress).width(960f).padBottom(10f).row();

        card = new QuestionCard(game.skin, 960f);
        card.setListener(this::onAnswer);
        // La tarjeta mide lo que su contenido (sin el hueco vacío que dejaba al estirarse)
        Table holder = new Table();
        holder.top();
        holder.add(card).width(960f).top();
        ScrollPane scroll = new ScrollPane(holder, game.skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        root.add(scroll).width(960f).expand().fill().row();

        Table buttons = new Table();
        previous = new TextButton(game.i18n.t("test.previous"), game.skin, "secondary");
        previous.addListener(change(() -> move(-1)));
        next = new TextButton(game.i18n.t("test.next"), game.skin);
        next.addListener(change(() -> move(1)));
        finish = new TextButton(game.i18n.t("test.finish"), game.skin, "secondary");
        finish.addListener(change(this::finishSession));
        TextButton quit = new TextButton(game.i18n.t("test.quit"), game.skin, "secondary");
        quit.addListener(change(this::quit));
        if (session.mode == TestSession.Mode.EXAM) {
            buttons.add(previous).width(160f).height(46f).padRight(8f);
        }
        buttons.add(next).width(200f).height(46f).padRight(8f);
        buttons.add(finish).width(200f).height(46f).padRight(8f);
        buttons.add(quit).width(160f).height(46f);
        root.add(buttons).padTop(10f);

        showCurrent();
    }

    private void showCurrent() {
        int i = session.index;
        revealed = session.mode != TestSession.Mode.EXAM && session.isAnswered(i);
        card.show(session.current(), session.answers[i], revealed);
        progress.setValue((i + 1) / (float) session.questions.size);
        String modeName = game.i18n.t("test.mode." + session.mode.name());
        header.setText(game.i18n.t("test.header", modeName, i + 1, session.questions.size,
            session.mode == TestSession.Mode.EXAM ? "" : game.i18n.t("test.score", session.correctCount(), session.answeredCount())));
        boolean last = i == session.questions.size - 1;
        next.setDisabled(last || (session.mode != TestSession.Mode.EXAM && !revealed));
        previous.setDisabled(i == 0);
        finish.setVisible(session.mode == TestSession.Mode.EXAM || last);
        updateTimer();
    }

    private void onAnswer(int option) {
        session.answer(session.index, option);
        if (session.mode == TestSession.Mode.EXAM) {
            // En examen: marcar y pasar a la siguiente sin corregir
            if (session.index < session.questions.size - 1) {
                session.index++;
            }
            showCurrent();
        } else {
            revealed = true;
            showCurrent();
        }
    }

    private void move(int delta) {
        int target = session.index + delta;
        if (target < 0 || target >= session.questions.size) {
            return;
        }
        session.index = target;
        showCurrent();
    }

    private void finishSession() {
        session.finish();
        game.stats.recordSession(session);
        game.setScreen(new TestResultScreen(game, session));
        dispose();
    }

    private void quit() {
        if (session.answeredCount() > 0) {
            game.stats.recordSession(session);
        }
        game.setScreen(new TheoryScreen(game));
        dispose();
    }

    private void updateTimer() {
        if (session.timeLimit > 0) {
            int s = session.remainingSeconds();
            timer.setText(String.format("%02d:%02d", s / 60, s % 60));
            timer.setColor(s < 120 ? SkinFactory.FAIL : com.badlogic.gdx.graphics.Color.WHITE);
        } else {
            int s = (int) session.elapsed;
            timer.setText(String.format("%02d:%02d", s / 60, s % 60));
        }
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
            quit();
            return;
        }
        if (!session.isFinished()) {
            int before = session.remainingSeconds();
            if (session.tick(delta)) {
                game.stats.recordSession(session);
                game.setScreen(new TestResultScreen(game, session));
                dispose();
                return;
            }
            if ((int) session.elapsed != (int) (session.elapsed - delta) || before != session.remainingSeconds()) {
                updateTimer();
            }
        }
        // Atajos: A/B/C o 1/2/3 para responder, Intro para siguiente
        if (!revealed || session.mode == TestSession.Mode.EXAM) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.A) || Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
                onAnswer(0);
            } else if (Gdx.input.isKeyJustPressed(Input.Keys.B) || Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
                onAnswer(1);
            } else if ((Gdx.input.isKeyJustPressed(Input.Keys.C) || Gdx.input.isKeyJustPressed(Input.Keys.NUM_3))
                    && session.current().opciones.length > 2) {
                onAnswer(2);
            }
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            if (session.index == session.questions.size - 1) {
                finishSession();
                return;
            }
            move(1);
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
        card.dispose();
        stage.dispose();
    }
}
