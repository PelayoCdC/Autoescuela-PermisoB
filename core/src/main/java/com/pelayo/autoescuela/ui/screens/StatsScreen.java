package com.pelayo.autoescuela.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.pelayo.autoescuela.AutoescuelaGame;
import com.pelayo.autoescuela.data.StatsData;
import com.pelayo.autoescuela.data.TemaDef;
import com.pelayo.autoescuela.ui.SkinFactory;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Progreso: aciertos por tema, falladas pendientes e historial de simulacros. */
public class StatsScreen extends ScreenAdapter {

    private final AutoescuelaGame game;
    private final Stage stage;

    public StatsScreen(AutoescuelaGame game) {
        this.game = game;
        stage = new Stage(new ExtendViewport(AutoescuelaGame.UI_WIDTH, AutoescuelaGame.UI_HEIGHT));
        StatsData d = game.stats.data;

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(game.skin.getDrawable("bg"));
        root.pad(20f);
        stage.addActor(root);
        root.add(new Label(game.i18n.t("stats.title"), game.skin, "title")).padBottom(8f).row();
        float overall = d.totalAnswered == 0 ? 0f : (float) d.totalCorrect / d.totalAnswered;
        root.add(new Label(game.i18n.t("stats.overall", d.totalCorrect, d.totalAnswered, overall * 100f,
            d.failedIds.size), game.skin, "small")).padBottom(12f).row();

        Table content = new Table();
        content.top();

        Table topics = new Table();
        topics.setBackground(game.skin.getDrawable("panel"));
        topics.pad(12f);
        topics.top().left();
        topics.add(new Label(game.i18n.t("stats.byTopic"), game.skin, "default")).left().colspan(3).padBottom(8f).row();
        for (TemaDef t : game.theory.temas) {
            StatsData.TopicStats ts = d.perTema.get(t.id);
            float acc = ts == null || ts.asked == 0 ? 0f : (float) ts.correct / ts.asked;
            Label name = new Label(t.orden + ". " + t.titulo, game.skin, "small");
            ProgressBar bar = new ProgressBar(0f, 1f, 0.01f, false, game.skin);
            bar.setValue(acc);
            Label value = new Label(ts == null || ts.asked == 0 ? game.i18n.t("theory.noData")
                : String.format("%d/%d  %.0f %%", ts.correct, ts.asked, acc * 100f), game.skin, "small");
            name.setWrap(true);
            topics.add(name).left().width(470f).padBottom(4f).padRight(10f);
            topics.add(bar).width(160f).padRight(10f).padBottom(4f);
            topics.add(value).left().width(140f).padBottom(4f).row();
        }
        content.add(topics).width(830f).top().padRight(12f);

        Table exams = new Table();
        exams.setBackground(game.skin.getDrawable("panel"));
        exams.pad(12f);
        exams.top().left();
        exams.add(new Label(game.i18n.t("stats.exams"), game.skin, "default")).left().padBottom(8f).row();
        if (d.examHistory.size == 0) {
            exams.add(new Label(game.i18n.t("stats.noExams"), game.skin, "small")).left().row();
        }
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        for (int i = d.examHistory.size - 1; i >= 0 && i >= d.examHistory.size - 15; i--) {
            StatsData.ExamRecord r = d.examHistory.get(i);
            Label line = new Label(String.format("%s   %d/%d   %s", fmt.format(new Date(r.date)), r.correct, r.total,
                r.passed ? game.i18n.t("results.passed") : game.i18n.t("results.failed")), game.skin, "small");
            line.setColor(r.passed ? SkinFactory.OK : SkinFactory.FAIL);
            exams.add(line).left().padBottom(2f).row();
        }
        content.add(exams).width(300f).top();

        ScrollPane scroll = new ScrollPane(content, game.skin);
        scroll.setFadeScrollBars(false);
        root.add(scroll).expand().fill().row();

        TextButton back = new TextButton(game.i18n.t("practice.back"), game.skin, "secondary");
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new TheoryScreen(game));
                dispose();
            }
        });
        root.add(back).width(220f).height(46f).padTop(10f);
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
        stage.dispose();
    }
}
