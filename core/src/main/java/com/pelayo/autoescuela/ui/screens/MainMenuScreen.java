package com.pelayo.autoescuela.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.pelayo.autoescuela.AutoescuelaGame;
import com.pelayo.autoescuela.data.ScenarioDef;
import com.pelayo.autoescuela.ui.SkinFactory;

/**
 * Menú principal: tres tarjetas (aprende a conducir, prácticas, teoría) con
 * el progreso de cada parte, y abajo ajustes y salir.
 */
public class MainMenuScreen extends ScreenAdapter {

    private final AutoescuelaGame game;
    private final Stage stage;

    public MainMenuScreen(AutoescuelaGame game) {
        this.game = game;
        stage = new Stage(new ExtendViewport(AutoescuelaGame.UI_WIDTH, AutoescuelaGame.UI_HEIGHT));

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(game.skin.getDrawable("bg"));
        stage.addActor(root);

        // Cabecera: volante + nombre
        Table header = new Table();
        Image logo = new Image(game.skin.getDrawable("icon-wheel"), Scaling.fit);
        logo.setColor(SkinFactory.ACCENT_LIGHT);
        header.add(logo).size(64f).padRight(18f);
        Table titles = new Table();
        titles.add(new Label(game.i18n.t("app.title"), game.skin, "huge")).left().row();
        titles.add(new Label(game.i18n.t("app.subtitle"), game.skin, "small")).left();
        header.add(titles);
        root.add(header).padBottom(44f).row();

        // Progreso de cada parte
        int lessons = 0;
        int lessonsDone = 0;
        int practices = 0;
        int practicesPassed = 0;
        for (String id : ScenarioDef.listIds()) {
            ScenarioDef d = ScenarioDef.load(id);
            if (d.lesson != null) {
                lessons++;
                lessonsDone += game.progress.isLessonDone(id) ? 1 : 0;
            } else if (!"FREE_DRIVE".equals(d.maneuver)) {
                practices++;
                practicesPassed += game.progress.isPassed(id) ? 1 : 0;
            }
        }
        game.ensureTheory();
        int answered = game.stats.data.totalAnswered;
        String theoryProgress = answered > 0
            ? game.i18n.t("menu.progress.theory", game.theory.preguntas.size,
                Math.round(100f * game.stats.data.totalCorrect / answered))
            : game.i18n.t("menu.progress.theoryEmpty", game.theory.preguntas.size, game.theory.temas.size);

        Table cards = new Table();
        cards.add(card("wheel", "menu.learn", "menu.learn.desc",
            game.i18n.t("menu.progress.lessons", lessonsDone, lessons), lessonsDone / (float) Math.max(1, lessons),
            () -> new PracticeMenuScreen(game, "lesson"))).size(360f, 320f).padRight(22f);
        cards.add(card("car", "menu.practice", "menu.practice.desc",
            game.i18n.t("menu.progress.maneuvers", practicesPassed, practices),
            practicesPassed / (float) Math.max(1, practices),
            () -> new PracticeMenuScreen(game, "exam"))).size(360f, 320f).padRight(22f);
        cards.add(card("book", "menu.theory", "menu.theory.desc", theoryProgress, -1f,
            () -> new TheoryScreen(game))).size(360f, 320f);
        root.add(cards).padBottom(34f).row();

        // Acceso directo a la siguiente lección sin completar
        ScenarioDef nextLesson = null;
        int nextNumber = 0;
        int number = 0;
        for (String id : ScenarioDef.listIds()) {
            ScenarioDef d = ScenarioDef.load(id);
            if (d.lesson != null) {
                number++;
                if (nextLesson == null && !game.progress.isLessonDone(id)) {
                    nextLesson = d;
                    nextNumber = number;
                }
            }
        }
        if (nextLesson != null) {
            final ScenarioDef target = nextLesson;
            TextButton resume = new TextButton(game.i18n.t("menu.resume", nextNumber, nextLesson.name), game.skin);
            resume.addListener(go(() -> new DrivingScreen(game, target)));
            root.add(resume).height(54f).padBottom(18f).row();
        }

        Table bottom = new Table();
        TextButton settings = new TextButton(game.i18n.t("menu.settings"), game.skin, "secondary");
        settings.addListener(go(() -> new SettingsScreen(game)));
        TextButton exit = new TextButton(game.i18n.t("menu.exit"), game.skin, "ghost");
        exit.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.exit();
            }
        });
        bottom.add(settings).width(200f).height(48f).padRight(14f);
        bottom.add(exit).width(160f).height(48f);
        root.add(bottom).padBottom(26f).row();

        Label footer = new Label(game.i18n.t("menu.footer"), game.skin, "small");
        footer.setColor(SkinFactory.TEXT_FAINT);
        root.add(footer);

        // Entrada suave de las tarjetas
        for (int i = 0; i < cards.getChildren().size; i++) {
            Actor a = cards.getChildren().get(i);
            a.getColor().a = 0f;
            a.addAction(Actions.sequence(Actions.delay(0.06f * i), Actions.fadeIn(0.35f, Interpolation.fade)));
        }
    }

    private interface ScreenFactory {
        com.badlogic.gdx.Screen create();
    }

    private Button card(String icon, String titleKey, String descKey, String progressText, float progress,
                        ScreenFactory target) {
        Button card = new Button(game.skin, "card");
        card.pad(24f, 24f, 22f, 24f);
        card.top().left();
        Image image = new Image(game.skin.getDrawable("icon-" + icon), Scaling.fit);
        image.setColor(SkinFactory.ACCENT_LIGHT);
        card.add(image).size(58f).left().padBottom(16f).row();
        card.add(new Label(game.i18n.t(titleKey), game.skin, "bold")).left().padBottom(8f).row();
        Label desc = new Label(game.i18n.t(descKey), game.skin, "small");
        desc.setWrap(true);
        desc.setAlignment(Align.topLeft);
        card.add(desc).width(310f).expandY().top().left().row();
        if (progress > 0f) {
            com.badlogic.gdx.scenes.scene2d.ui.ProgressBar bar = new com.badlogic.gdx.scenes.scene2d.ui.ProgressBar(
                0f, 1f, 0.01f, false, game.skin, progress >= 1f ? "ok-horizontal" : "default-horizontal");
            bar.setValue(progress);
            card.add(bar).width(310f).padBottom(8f).row();
        } else if (progress == 0f) {
            // Barra vacía: solo el carril (el relleno de la barra no baja de su tamaño mínimo)
            Image track = new Image(game.skin.get("default-horizontal",
                com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle.class).background);
            card.add(track).width(310f).height(12f).padBottom(8f).row();
        }
        Label p = new Label(progressText, game.skin, "accent");
        card.add(p).left();
        card.addListener(go(target));
        return card;
    }

    private ChangeListener go(ScreenFactory factory) {
        return new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(factory.create());
                dispose();
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
            game.exit();
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
