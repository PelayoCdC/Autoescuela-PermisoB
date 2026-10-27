package com.pelayo.autoescuela.ui.screens;

import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.pelayo.autoescuela.AutoescuelaGame;
import com.pelayo.autoescuela.data.ScenarioDef;
import com.pelayo.autoescuela.ui.SkinFactory;

/** Pantalla de carga: avanza el AssetManager y muestra una barra de progreso. */
public class LoadingScreen extends ScreenAdapter {

    private final AutoescuelaGame game;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final FitViewport viewport =
        new FitViewport(AutoescuelaGame.UI_WIDTH, AutoescuelaGame.UI_HEIGHT);

    private float shownProgress = 0f;

    public LoadingScreen(AutoescuelaGame game) {
        this.game = game;
        game.assets.queueInitial();
    }

    @Override
    public void render(float delta) {
        boolean done = game.assets.update();
        shownProgress = Math.min(1f, shownProgress + delta * 3f);
        if (shownProgress < game.assets.getProgress()) {
            shownProgress = game.assets.getProgress();
        }

        ScreenUtils.clear(SkinFactory.BG);
        viewport.apply(true);
        shapes.setProjectionMatrix(viewport.getCamera().combined);

        float barWidth = AutoescuelaGame.UI_WIDTH * 0.4f;
        float barHeight = 10f;
        float x = (AutoescuelaGame.UI_WIDTH - barWidth) / 2f;
        float y = AutoescuelaGame.UI_HEIGHT * 0.4f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(SkinFactory.PANEL);
        shapes.rect(x, y, barWidth, barHeight);
        shapes.setColor(SkinFactory.ACCENT);
        shapes.rect(x, y, barWidth * shownProgress, barHeight);
        shapes.end();

        if (done && shownProgress >= 1f) {
            game.assets.finishInitial();
            game.skin = SkinFactory.create(game.assets);
            if ("driving".equals(game.dev.screen) || game.dev.scenario != null) {
                String id = game.dev.scenario != null ? game.dev.scenario : "test_ground";
                game.setScreen(new DrivingScreen(game, ScenarioDef.load(id)));
            } else if ("practice".equals(game.dev.screen)) {
                game.setScreen(new PracticeMenuScreen(game));
            } else if (game.dev.screen != null && game.dev.screen.startsWith("practice-")) {
                game.setScreen(new PracticeMenuScreen(game, game.dev.screen.substring("practice-".length())));
            } else if ("theory".equals(game.dev.screen)) {
                game.setScreen(new TheoryScreen(game));
            } else if ("topic".equals(game.dev.screen)) {
                game.ensureTheory();
                game.setScreen(new TopicScreen(game, game.theory.temas.first()));
            } else if ("test".equals(game.dev.screen) || "exam".equals(game.dev.screen)) {
                game.ensureTheory();
                boolean exam = "exam".equals(game.dev.screen);
                com.pelayo.autoescuela.theory.TestGenerator gen = new com.pelayo.autoescuela.theory.TestGenerator(game.theory);
                game.setScreen(new TestScreen(game, new com.pelayo.autoescuela.theory.TestSession(
                    exam ? com.pelayo.autoescuela.theory.TestSession.Mode.EXAM
                        : com.pelayo.autoescuela.theory.TestSession.Mode.PRACTICE,
                    exam ? gen.officialExam() : gen.random(10))));
            } else if ("stats".equals(game.dev.screen)) {
                game.ensureTheory();
                game.setScreen(new StatsScreen(game));
            } else if ("settings".equals(game.dev.screen)) {
                game.setScreen(new SettingsScreen(game));
            } else if ("controls".equals(game.dev.screen)) {
                game.setScreen(new ControlsScreen(game));
            } else if ("onboarding".equals(game.dev.screen) || (game.dev.screen == null && !game.settings.isOnboarded())) {
                game.setScreen(new OnboardingScreen(game)); // la primera vez: cómo se va a conducir
            } else {
                game.setScreen(new MainMenuScreen(game));
            }
            dispose();
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
