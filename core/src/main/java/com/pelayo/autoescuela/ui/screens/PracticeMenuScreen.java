package com.pelayo.autoescuela.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.pelayo.autoescuela.AutoescuelaGame;
import com.pelayo.autoescuela.data.ScenarioDef;
import com.pelayo.autoescuela.ui.SkinFactory;

/**
 * Menú de prácticas en tres pestañas: lecciones guiadas ("Aprende a
 * conducir"), ejercicios de principiante y maniobras del examen de la DGT.
 * Muestra qué lecciones están completadas y qué prácticas superadas.
 */
public class PracticeMenuScreen extends ScreenAdapter {

    public static final String[] CATEGORIES = {"lesson", "exercise", "exam"};

    private final AutoescuelaGame game;
    private final Stage stage;
    private final Array<ScenarioDef> defs = new Array<>();
    private final Table list = new Table();
    private final Label intro;
    private final ScrollPane scroll;

    public PracticeMenuScreen(AutoescuelaGame game) {
        this(game, "lesson");
    }

    public PracticeMenuScreen(AutoescuelaGame game, String category) {
        this.game = game;
        stage = new Stage(new ExtendViewport(AutoescuelaGame.UI_WIDTH, AutoescuelaGame.UI_HEIGHT));
        for (String id : ScenarioDef.listIds()) {
            defs.add(ScenarioDef.load(id));
        }
        defs.sort((a, b) -> Integer.compare(a.order, b.order));

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(game.skin.getDrawable("bg"));
        root.pad(24f, 40f, 20f, 40f);
        stage.addActor(root);

        root.add(new Label(game.i18n.t("practice.title"), game.skin, "title")).padBottom(14f).row();

        Table tabs = new Table();
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        for (String c : CATEGORIES) {
            TextButton tab = new TextButton(game.i18n.t("practice.tab." + c), game.skin, "tab");
            group.add(tab);
            tab.setChecked(c.equals(category));
            tab.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (tab.isChecked()) {
                        showCategory(c);
                    }
                }
            });
            tabs.add(tab).width(250f).height(44f).padRight(10f);
        }
        group.setMinCheckCount(1);
        group.setMaxCheckCount(1);
        root.add(tabs).padBottom(12f).row();

        intro = new Label("", game.skin, "small");
        intro.setWrap(true);
        intro.setAlignment(Align.center);
        root.add(intro).width(900f).padBottom(12f).row();

        list.top();
        scroll = new ScrollPane(list, game.skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        root.add(scroll).expand().fill().row();

        TextButton back = new TextButton(game.i18n.t("practice.back"), game.skin, "secondary");
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new MainMenuScreen(game));
                dispose();
            }
        });
        root.add(back).width(220f).height(48f).padTop(12f);

        showCategory(category);
    }

    private void showCategory(String category) {
        intro.setText(game.i18n.t("practice.intro." + category));
        list.clear();
        int lessonNumber = 0;
        boolean previousDone = true;
        for (ScenarioDef def : defs) {
            if (!def.category.equals(category)) {
                continue;
            }
            boolean lesson = def.lesson != null;
            boolean done = lesson ? game.progress.isLessonDone(def.id) : game.progress.isPassed(def.id);
            if (lesson) {
                lessonNumber++;
            }
            boolean suggested = lesson && !done && previousDone;
            list.add(row(def, lesson ? lessonNumber : 0, done, suggested)).width(960f).padBottom(10f).row();
            previousDone = done;
        }
        list.getColor().a = 0f;
        list.addAction(Actions.fadeIn(0.2f));
        scroll.setScrollY(0f);
    }

    private Table row(ScenarioDef def, int lessonNumber, boolean done, boolean suggested) {
        Table row = new Table();
        row.setBackground(game.skin.getDrawable(suggested ? "panel-accent" : "panel"));
        row.pad(14f, 18f, 14f, 18f);

        // Medallón: número de lección o icono de la categoría; verde si está hecho
        Stack badge = new Stack();
        Image disc = new Image(game.skin.getRegion("circle"));
        disc.setColor(done ? SkinFactory.OK : suggested ? SkinFactory.ACCENT : new com.badlogic.gdx.graphics.Color(0x263243ff));
        badge.add(disc);
        if (done) {
            Image check = new Image(game.skin.getDrawable("icon-check"), Scaling.fit);
            Table c = new Table();
            c.add(check).size(26f);
            badge.add(c);
        } else if (lessonNumber > 0) {
            Label n = new Label(String.valueOf(lessonNumber), game.skin, "bold");
            n.setAlignment(Align.center);
            badge.add(n);
        } else {
            Image icon = new Image(game.skin.getDrawable(def.category.equals("exam") ? "icon-flag" : "icon-cone"),
                Scaling.fit);
            Table c = new Table();
            c.add(icon).size(24f);
            badge.add(c);
        }
        row.add(badge).size(52f).padRight(18f);

        Table text = new Table();
        text.left();
        Table titleRow = new Table();
        Label name = new Label(def.name, game.skin, "bold");
        titleRow.add(name).left();
        if (done) {
            Label chip = new Label(game.i18n.t(def.lesson != null ? "practice.done" : "practice.passed"),
                game.skin, "small-white");
            Table c = new Table();
            c.setBackground(game.skin.getDrawable("chip-ok"));
            c.add(chip);
            titleRow.add(c).padLeft(12f);
        }
        text.add(titleRow).left().padBottom(4f).row();
        Label desc = new Label(def.description, game.skin, "small");
        desc.setWrap(true);
        text.add(desc).width(660f).left();
        row.add(text).expandX().left();

        TextButton start = new TextButton(game.i18n.t("practice.start"), game.skin,
            suggested || !done ? "default" : "secondary");
        start.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new DrivingScreen(game, def));
                dispose();
            }
        });
        row.add(start).width(140f).height(46f).right().padLeft(16f);
        return row;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.setScreen(new MainMenuScreen(game));
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
