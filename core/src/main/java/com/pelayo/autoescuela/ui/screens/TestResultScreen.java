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
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.pelayo.autoescuela.AutoescuelaGame;
import com.pelayo.autoescuela.data.PreguntaDef;
import com.pelayo.autoescuela.theory.TestGenerator;
import com.pelayo.autoescuela.theory.TestSession;
import com.pelayo.autoescuela.ui.SkinFactory;
import com.pelayo.autoescuela.ui.widgets.QuestionCard;

/** Resultado de un test: puntuación, veredicto (en examen) y repaso de las falladas. */
public class TestResultScreen extends ScreenAdapter {

    private final AutoescuelaGame game;
    private final Stage stage;
    private final Array<QuestionCard> cards = new Array<>();

    public TestResultScreen(AutoescuelaGame game, TestSession session) {
        this.game = game;
        stage = new Stage(new ExtendViewport(AutoescuelaGame.UI_WIDTH, AutoescuelaGame.UI_HEIGHT));

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(game.skin.getDrawable("bg"));
        root.pad(16f);
        stage.addActor(root);

        int correct = session.correctCount();
        int total = session.questions.size;
        root.add(new Label(game.i18n.t("test.result.score", correct, total), game.skin, "title")).padBottom(4f).row();
        if (session.mode == TestSession.Mode.EXAM) {
            Label verdict = new Label(session.passed() ? game.i18n.t("results.passed") : game.i18n.t("results.failed"),
                game.skin, "title");
            verdict.setColor(session.passed() ? SkinFactory.OK : SkinFactory.FAIL);
            root.add(verdict).row();
            root.add(new Label(game.i18n.t("test.result.examNote", session.failCount(),
                (int) session.elapsed / 60, (int) session.elapsed % 60), game.skin, "small")).padBottom(8f).row();
        } else {
            root.add(new Label(game.i18n.t("test.result.time", (int) session.elapsed / 60, (int) session.elapsed % 60),
                game.skin, "small")).padBottom(8f).row();
        }

        Table content = new Table();
        content.top();
        boolean anyFailed = false;
        for (int i = 0; i < total; i++) {
            if (session.isCorrect(i)) {
                continue;
            }
            anyFailed = true;
            PreguntaDef q = session.questions.get(i);
            QuestionCard card = new QuestionCard(game.skin, 940f);
            card.show(q, session.answers[i], true);
            cards.add(card);
            content.add(card).width(940f).padBottom(10f).row();
        }
        if (!anyFailed) {
            content.add(new Label(game.i18n.t("test.result.perfect"), game.skin, "ok")).padTop(20f).row();
        } else {
            content.add(new Label(game.i18n.t("test.result.failedList"), game.skin, "small")).padBottom(6f).row();
            content.swapActor(0, content.getChildren().size - 1);
        }
        ScrollPane scroll = new ScrollPane(content, game.skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        root.add(scroll).width(960f).expand().fill().row();

        Table buttons = new Table();
        TextButton again = new TextButton(game.i18n.t("test.result.again"), game.skin);
        again.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Array<PreguntaDef> qs = new Array<>(session.questions);
                qs.shuffle();
                game.setScreen(new TestScreen(game, new TestSession(session.mode, TestGenerator.shuffleOptions(qs))));
                dispose();
            }
        });
        TextButton back = new TextButton(game.i18n.t("test.result.back"), game.skin, "secondary");
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new TheoryScreen(game));
                dispose();
            }
        });
        buttons.add(again).width(220f).height(46f).padRight(12f);
        buttons.add(back).width(220f).height(46f);
        root.add(buttons).padTop(10f);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.setScreen(new TheoryScreen(game));
            dispose();
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
        for (QuestionCard c : cards) {
            c.dispose();
        }
        stage.dispose();
    }
}
