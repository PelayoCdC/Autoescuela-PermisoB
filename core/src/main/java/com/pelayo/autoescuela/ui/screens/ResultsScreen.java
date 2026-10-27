package com.pelayo.autoescuela.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.pelayo.autoescuela.AutoescuelaGame;
import com.pelayo.autoescuela.data.ScenarioDef;
import com.pelayo.autoescuela.sim.eval.EvaluationResult;
import com.pelayo.autoescuela.sim.eval.Fault;
import com.pelayo.autoescuela.ui.SkinFactory;

/**
 * Resultado de una práctica. Maniobras y ejercicios: veredicto APTO / NO
 * APTO, faltas y métricas. Lecciones: lección completada, lo aprendido y
 * paso a la siguiente. Anota el progreso (lección hecha, práctica superada).
 */
public class ResultsScreen extends ScreenAdapter {

    private final AutoescuelaGame game;
    private final ScenarioDef def;
    private final Stage stage;

    public ResultsScreen(AutoescuelaGame game, ScenarioDef def, EvaluationResult result) {
        this.game = game;
        this.def = def;
        stage = new Stage(new ExtendViewport(AutoescuelaGame.UI_WIDTH, AutoescuelaGame.UI_HEIGHT));

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(game.skin.getDrawable("bg"));
        root.pad(28f);
        stage.addActor(root);

        boolean lesson = def.lesson != null;
        if (lesson && result.completed) {
            game.progress.markLessonDone(def.id);
            buildLesson(root, result);
        } else {
            if (result.completed && result.passed) {
                game.progress.markPassed(def.id);
            }
            buildManeuver(root, result);
        }
        root.getColor().a = 0f;
        root.addAction(Actions.fadeIn(0.3f));
    }

    // --- Lección ------------------------------------------------------------------

    private void buildLesson(Table root, EvaluationResult result) {
        Image star = new Image(game.skin.getDrawable("icon-star"), Scaling.fit);
        star.setColor(SkinFactory.WARN);
        star.setOrigin(36f, 36f);
        star.setScale(0.3f);
        star.addAction(Actions.scaleTo(1f, 1f, 0.5f, com.badlogic.gdx.math.Interpolation.swingOut));
        root.add(star).size(72f).padBottom(8f).row();
        Label done = new Label(game.i18n.t("lesson.done"), game.skin, "title");
        done.setColor(SkinFactory.OK);
        root.add(done).padBottom(4f).row();
        root.add(new Label(def.name, game.skin, "default")).padBottom(6f).row();
        int secs = (int) result.duration;
        root.add(new Label(game.i18n.t("lesson.stats", secs / 60, String.format("%02d", secs % 60),
            result.metrics.get(game.i18n.t("metric.stalls"), "0")), game.skin, "small")).padBottom(18f).row();

        Table learned = new Table();
        learned.setBackground(game.skin.getDrawable("panel"));
        learned.pad(18f, 22f, 18f, 22f);
        learned.top().left();
        learned.add(new Label(game.i18n.t("lesson.learned"), game.skin, "bold")).left().colspan(2).padBottom(10f).row();
        if (def.lesson.summary != null) {
            for (String s : def.lesson.summary) {
                Image check = new Image(game.skin.getDrawable("icon-check"), Scaling.fit);
                check.setColor(SkinFactory.OK);
                Label l = new Label(s, game.skin, "small-white");
                l.setWrap(true);
                learned.add(check).size(18f).top().padTop(3f).padRight(12f);
                learned.add(l).width(660f).left().padBottom(8f).row();
            }
        }
        ScrollPane scroll = new ScrollPane(learned, game.skin);
        scroll.setFadeScrollBars(false);
        root.add(scroll).width(760f).expandY().top().row();

        ScenarioDef next = nextLesson();
        Table buttons = new Table();
        if (next != null) {
            TextButton nextButton = new TextButton(game.i18n.t("lesson.next", next.name), game.skin);
            nextButton.addListener(go(() -> new DrivingScreen(game, next)));
            buttons.add(nextButton).height(54f).padRight(14f);
        } else {
            Label all = new Label(game.i18n.t("lesson.allDone"), game.skin, "ok");
            all.setWrap(true);
            all.setAlignment(com.badlogic.gdx.utils.Align.center);
            root.add(all).width(760f).padTop(12f).row();
        }
        TextButton retry = new TextButton(game.i18n.t("results.retry"), game.skin, "secondary");
        retry.addListener(go(() -> new DrivingScreen(game, def)));
        TextButton menu = new TextButton(game.i18n.t("results.menu"), game.skin, "secondary");
        menu.addListener(go(() -> new PracticeMenuScreen(game, def.category)));
        buttons.add(retry).width(180f).height(54f).padRight(14f);
        buttons.add(menu).width(240f).height(54f);
        root.add(buttons).padTop(18f);
    }

    /** Repetición con su botón de reproducir, la barra de tiempo y una ayuda. */
    private Table replayPanel(com.pelayo.autoescuela.ui.widgets.ReplayMap replay) {
        Table panel = new Table();
        panel.setBackground(game.skin.getDrawable("panel"));
        panel.pad(16f, 18f, 14f, 18f);
        panel.add(new Label(game.i18n.t("results.replay"), game.skin, "bold")).left().padBottom(10f).row();
        panel.add(replay).size(replay.getWidth(), replay.getHeight()).padBottom(10f).row();
        Table controls = new Table();
        TextButton play = new TextButton(game.i18n.t("results.replay.play"), game.skin, "small");
        com.badlogic.gdx.scenes.scene2d.ui.Slider slider = new com.badlogic.gdx.scenes.scene2d.ui.Slider(0f,
            Math.max(0.1f, replay.getDuration()), 0.05f, false, game.skin);
        Label clock = new Label("", game.skin, "small");
        boolean[] syncing = {false};
        replay.onTime = () -> {
            syncing[0] = true;
            slider.setValue(replay.getTime());
            syncing[0] = false;
            int t = (int) replay.getTime();
            clock.setText(String.format("%d:%02d", t / 60, t % 60));
            play.setText(game.i18n.t(replay.isPlaying() ? "results.replay.pause" : "results.replay.play"));
        };
        play.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                replay.setPlaying(!replay.isPlaying());
                replay.onTime.run();
            }
        });
        slider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!syncing[0]) {
                    replay.seek(slider.getValue());
                }
            }
        });
        replay.onTime.run();
        replay.setPlaying(true); // empieza sola
        controls.add(play).width(120f).height(38f).padRight(12f);
        controls.add(slider).width(220f).padRight(12f);
        controls.add(clock).width(50f);
        panel.add(controls).left().row();
        Label hint = new Label(game.i18n.t("results.replay.hint"), game.skin, "small");
        hint.setWrap(true);
        hint.setColor(SkinFactory.TEXT_DIM);
        panel.add(hint).width(440f).padTop(8f).left();
        return panel;
    }

    /** Lección siguiente en el orden del menú, o null si era la última. */
    private ScenarioDef nextLesson() {
        ScenarioDef best = null;
        for (String id : ScenarioDef.listIds()) {
            ScenarioDef d = ScenarioDef.load(id);
            if (d.lesson != null && d.order > def.order && (best == null || d.order < best.order)) {
                best = d;
            }
        }
        return best;
    }

    // --- Maniobra -----------------------------------------------------------------

    private void buildManeuver(Table root, EvaluationResult result) {
        root.add(new Label(def.name, game.skin, "default")).padBottom(4f).row();
        String verdict = !result.completed ? game.i18n.t("results.aborted")
            : result.passed ? game.i18n.t("results.passed") : game.i18n.t("results.failed");
        Label verdictLabel = new Label(verdict, game.skin, result.completed ? "huge" : "title");
        verdictLabel.setColor(result.completed && result.passed ? SkinFactory.OK : SkinFactory.FAIL);
        root.add(verdictLabel).padBottom(18f).row();

        Table content = new Table();
        content.top();

        // Repetición vista desde arriba, con las faltas numeradas donde ocurrieron
        com.pelayo.autoescuela.ui.widgets.ReplayMap replay = null;
        if (result.trace.size > 0) {
            replay = new com.pelayo.autoescuela.ui.widgets.ReplayMap(game.skin, def, result, 440f, 300f);
            content.add(replayPanel(replay)).top().padRight(18f);
        }
        final com.pelayo.autoescuela.ui.widgets.ReplayMap replayRef = replay;
        Table right = new Table();
        right.top();

        Table faults = new Table();
        faults.setBackground(game.skin.getDrawable("panel"));
        faults.pad(16f, 18f, 16f, 18f);
        faults.top().left();
        faults.add(new Label(game.i18n.t("results.faults"), game.skin, "bold")).left().colspan(3).padBottom(10f).row();
        if (result.faults.size == 0) {
            faults.add(new Label(game.i18n.t(result.completed ? "results.noFaults" : "results.noFaultsYet"), game.skin,
                result.completed ? "ok" : "small")).left().colspan(3).row();
        }
        int number = 0;
        for (Fault f : result.faults) {
            number++;
            // Número como en la repetición; tocar la falta lleva la repetición a ese momento
            Label num = new Label(String.valueOf(number), game.skin, "small-white");
            num.setAlignment(com.badlogic.gdx.utils.Align.center);
            Table numDot = new Table();
            numDot.setBackground(game.skin.newDrawable("round-white",
                com.pelayo.autoescuela.ui.widgets.ReplayMap.color(f.severity, 1f)));
            numDot.add(num).size(24f, 24f);
            if (replayRef != null) {
                com.badlogic.gdx.scenes.scene2d.utils.ClickListener seek =
                    new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                        @Override
                        public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                            replayRef.seek(f.time - 1.5f);
                        }
                    };
                numDot.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
                numDot.addListener(seek);
            }
            faults.add(numDot).size(26f).top().padRight(8f);
            // Gravedad en una etiqueta de color (el texto de color sobre fondo oscuro se leía mal)
            Label sevText = new Label(game.i18n.t("results.severity." + f.severity.name()), game.skin, "small-white");
            Table sev = new Table();
            sev.setBackground(game.skin.getDrawable(f.severity.name().equals("ELIMINATORIA") ? "chip-fail"
                : f.severity.name().equals("DEFICIENTE") ? "chip-orange" : "chip-warn"));
            sev.add(sevText);
            Label msg = new Label(String.format("%s  (%d:%02d)", f.message, (int) f.time / 60, (int) f.time % 60),
                game.skin, "small-white");
            msg.setWrap(true);
            if (replayRef != null) {
                msg.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                    @Override
                    public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                        replayRef.seek(f.time - 1.5f);
                    }
                });
            }
            faults.add(sev).left().width(118f).top().padRight(10f);
            faults.add(msg).left().width(replayRef != null ? 300f : 410f).padBottom(8f).row();
        }
        right.add(faults).width(replayRef != null ? 500f : 580f).top().row();

        Table metrics = new Table();
        metrics.setBackground(game.skin.getDrawable("panel"));
        metrics.pad(16f, 18f, 16f, 18f);
        metrics.top().left();
        metrics.add(new Label(game.i18n.t("results.metrics"), game.skin, "bold")).left().colspan(2).padBottom(10f).row();
        metrics.add(new Label(game.i18n.t("results.duration"), game.skin, "small")).left().padRight(12f);
        metrics.add(new Label(String.format("%d:%02d", (int) result.duration / 60, (int) result.duration % 60),
            game.skin, "small-white")).left().row();
        for (ObjectMap.Entry<String, String> e : result.metrics) {
            metrics.add(new Label(e.key, game.skin, "small")).left().padRight(12f).padTop(4f);
            metrics.add(new Label(e.value, game.skin, "small-white")).left().padTop(4f).row();
        }
        if (replayRef != null) {
            right.add(metrics).width(500f).top().padTop(14f);
            content.add(right).top();
        } else {
            content.add(right).top().padRight(18f);
            content.add(metrics).width(340f).top();
        }

        ScrollPane scroll = new ScrollPane(content, game.skin);
        scroll.setFadeScrollBars(false);
        root.add(scroll).expand().fill().row();

        Table buttons = new Table();
        TextButton retry = new TextButton(game.i18n.t("results.retry"), game.skin);
        retry.addListener(go(() -> new DrivingScreen(game, def)));
        TextButton menu = new TextButton(game.i18n.t("results.menu"), game.skin, "secondary");
        menu.addListener(go(() -> new PracticeMenuScreen(game, def.category)));
        buttons.add(retry).width(220f).height(54f).padRight(16f);
        buttons.add(menu).width(260f).height(54f);
        root.add(buttons).padTop(14f);
    }

    private interface ScreenFactory {
        com.badlogic.gdx.Screen create();
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

    private static Color severityColor(Fault f) {
        switch (f.severity) {
            case ELIMINATORIA: return SkinFactory.FAIL;
            case DEFICIENTE: return new Color(1f, 0.6f, 0.2f, 1f);
            default: return new Color(1f, 0.9f, 0.4f, 1f);
        }
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    private int frames;

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.setScreen(new PracticeMenuScreen(game, def.category));
            dispose();
            return;
        }
        ScreenUtils.clear(SkinFactory.BG);
        stage.act(delta);
        stage.draw();
        // Modo desarrollo: captura de la pantalla de resultados y salida
        if (game.dev.exitAt >= 0f && ++frames == 20) {
            com.pelayo.autoescuela.app.Screenshots.save("shot_results");
            Gdx.app.exit();
        }
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
