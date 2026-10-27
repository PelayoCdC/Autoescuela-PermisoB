package com.pelayo.autoescuela.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.pelayo.autoescuela.AutoescuelaGame;
import com.pelayo.autoescuela.app.Settings;
import com.pelayo.autoescuela.ui.SkinFactory;

/** Ajustes: volumen, espejos, calidad gráfica, controles táctiles y ayuda de teclas. */
public class SettingsScreen extends ScreenAdapter {

    private final AutoescuelaGame game;
    private final Stage stage;
    /** Pantalla a la que volver (la práctica en pausa), o null para volver al menú principal. */
    private final com.badlogic.gdx.Screen returnTo;

    public SettingsScreen(AutoescuelaGame game) {
        this(game, null);
    }

    /** Ajustes abiertos desde otra pantalla (el menú de pausa): al salir se vuelve a ella sin cerrarla. */
    public SettingsScreen(AutoescuelaGame game, com.badlogic.gdx.Screen returnTo) {
        this.game = game;
        this.returnTo = returnTo;
        stage = new Stage(new ExtendViewport(AutoescuelaGame.UI_WIDTH, AutoescuelaGame.UI_HEIGHT));
        Settings s = game.settings;

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(game.skin.getDrawable("bg"));
        root.pad(24f);
        stage.addActor(root);
        root.add(new Label(game.i18n.t("settings.title"), game.skin, "title")).padBottom(returnTo != null ? 4f : 12f).row();
        if (returnTo != null) {
            Label note = new Label(game.i18n.t("settings.fromPause"), game.skin, "small");
            note.setColor(SkinFactory.TEXT_DIM);
            root.add(note).padBottom(10f).row();
        }

        Table form = new Table();
        form.setBackground(game.skin.getDrawable("panel"));
        form.pad(22f, 28f, 22f, 28f);
        form.defaults().left().padBottom(10f);

        // Volumen
        form.add(new Label(game.i18n.t("settings.volume"), game.skin, "default")).width(260f);
        Slider volume = new Slider(0f, 1f, 0.05f, false, game.skin);
        volume.setValue(s.getMasterVolume());
        volume.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                s.setMasterVolume(volume.getValue());
            }
        });
        form.add(volume).width(300f).row();

        // Espejos
        form.add(new Label(game.i18n.t("settings.mirrors"), game.skin, "default")).width(260f);
        form.add(cycle(Settings.Mirrors.values(), s.getMirrors(), "settings.mirrors.", v -> s.setMirrors((Settings.Mirrors) v)))
            .width(300f).height(44f).row();

        // Calidad
        form.add(new Label(game.i18n.t("settings.quality"), game.skin, "default")).width(260f);
        form.add(cycle(Settings.Quality.values(), s.getQuality(), "settings.quality.", v -> s.setQuality((Settings.Quality) v)))
            .width(300f).height(44f).row();

        form.add(new Label(game.i18n.t("settings.performance"), game.skin, "default")).width(260f).top();
        Table perf = new Table();
        perf.add(check("settings.autoQuality.enabled", s.isAutoQuality(), s::setAutoQuality)).left().row();
        perf.add(check("settings.fps.enabled", s.isShowFps(), s::setShowFps)).left().padTop(6f);
        form.add(perf).left().row();

        // Tamaño del HUD y dónde van los relojes en la cabina
        form.add(new Label(game.i18n.t("settings.hudSize"), game.skin, "default")).width(260f);
        form.add(cycle(Settings.HudSize.values(), s.getHudSize(), "settings.hudSize.", v -> s.setHudSize((Settings.HudSize) v)))
            .width(300f).height(44f).row();
        form.add(new Label(game.i18n.t("settings.cockpitView"), game.skin, "default")).width(260f);
        form.add(cycle(Settings.CockpitView.values(), s.getCockpitView(), "settings.cockpitView.",
            v -> s.setCockpitView((Settings.CockpitView) v))).width(300f).height(44f).row();
        form.add(new Label(game.i18n.t("settings.gauges"), game.skin, "default")).width(260f);
        form.add(cycle(Settings.Gauges.values(), s.getGauges(), "settings.gauges.", v -> s.setGauges((Settings.Gauges) v)))
            .width(300f).height(44f).row();

        // Color del coche: una muestra por pintura; la elegida lleva un aro blanco
        form.add(new Label(game.i18n.t("settings.paint"), game.skin, "default")).width(260f).top().padTop(8f);
        Table paintColumn = new Table();
        Table swatches = new Table();
        Label paintName = new Label(paintLabel(s.getCarPaint()), game.skin, "small");
        com.badlogic.gdx.utils.Array<com.badlogic.gdx.scenes.scene2d.ui.Image> rings = new com.badlogic.gdx.utils.Array<>();
        for (com.pelayo.autoescuela.app.CarPaint paint : com.pelayo.autoescuela.app.CarPaint.values()) {
            com.badlogic.gdx.scenes.scene2d.ui.Image ring = new com.badlogic.gdx.scenes.scene2d.ui.Image(
                game.skin.getRegion("ring-bold"));
            ring.setColor(paint == s.getCarPaint() ? com.badlogic.gdx.graphics.Color.WHITE
                : new com.badlogic.gdx.graphics.Color(1f, 1f, 1f, 0f));
            com.badlogic.gdx.scenes.scene2d.ui.Image dot = new com.badlogic.gdx.scenes.scene2d.ui.Image(
                game.skin.getRegion("circle"));
            dot.setColor(paint.swatch());
            com.badlogic.gdx.scenes.scene2d.ui.Container<com.badlogic.gdx.scenes.scene2d.ui.Image> inner =
                new com.badlogic.gdx.scenes.scene2d.ui.Container<>(dot).size(28f);
            com.badlogic.gdx.scenes.scene2d.ui.Stack stack = new com.badlogic.gdx.scenes.scene2d.ui.Stack(ring, inner);
            stack.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
            rings.add(ring);
            stack.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    s.setCarPaint(paint);
                    paintName.setText(paintLabel(paint));
                    for (int i = 0; i < rings.size; i++) {
                        rings.get(i).setColor(1f, 1f, 1f, i == paint.ordinal() ? 1f : 0f);
                    }
                }
            });
            swatches.add(stack).size(40f).padRight(6f).padBottom(6f);
            if (paint.ordinal() % 9 == 8) {
                swatches.row();
            }
        }
        paintColumn.add(swatches).left().row();
        paintColumn.add(paintName).left().padTop(4f);
        form.add(paintColumn).left().row();

        // Retrovisor derecho en pantalla
        form.add(new Label(game.i18n.t("settings.rightMirror"), game.skin, "default")).width(260f);
        CheckBox rightMirror = new CheckBox("  " + game.i18n.t("settings.rightMirror.enabled"), game.skin);
        rightMirror.setChecked(s.isRightMirrorInset());
        rightMirror.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                s.setRightMirrorInset(rightMirror.isChecked());
            }
        });
        form.add(rightMirror).left().row();

        // Controles táctiles
        form.add(new Label(game.i18n.t("settings.touch"), game.skin, "default")).width(260f);
        CheckBox touch = new CheckBox("  " + game.i18n.t("settings.touch.enabled"), game.skin);
        touch.setChecked(s.isTouchControls());
        touch.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                s.setTouchControls(touch.isChecked());
            }
        });
        form.add(touch).left().row();
        form.add(new Label(game.i18n.t("settings.pedalSide"), game.skin, "default")).width(260f);
        form.add(cycle(Settings.PedalSide.values(), s.getPedalSide(), "settings.pedalSide.",
            v -> s.setPedalSide((Settings.PedalSide) v))).width(300f).height(44f).row();
        form.add(new Label(game.i18n.t("settings.stickyClutch"), game.skin, "default")).width(260f);
        form.add(check("settings.stickyClutch.enabled", s.isStickyClutch(), s::setStickyClutch)).left().row();
        form.add(new Label(game.i18n.t("settings.voice"), game.skin, "default")).width(260f);
        form.add(check("settings.voice.enabled", s.isVoice(), s::setVoice)).left().row();
        form.add(new Label(game.i18n.t("settings.vibration"), game.skin, "default")).width(260f);
        form.add(check("settings.vibration.enabled", s.isVibration(), s::setVibration)).left().row();
        form.add(new Label(game.i18n.t("settings.minimap"), game.skin, "default")).width(260f);
        form.add(check("settings.minimap.enabled", s.isMinimap(), s::setMinimap)).left().row();

        // Contenido desplazable: con todas las opciones no cabe en pantallas bajas
        Table content = new Table();
        content.add(form).width(820f).padBottom(16f).row();

        Label help = new Label(game.i18n.t("settings.keys"), game.skin, "small");
        help.setWrap(true);
        content.add(help).width(900f).padBottom(8f).row();
        com.badlogic.gdx.scenes.scene2d.ui.ScrollPane scroll = new com.badlogic.gdx.scenes.scene2d.ui.ScrollPane(content, game.skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        root.add(scroll).expand().fill().padBottom(12f).row();

        Table buttons = new Table();
        TextButton controls = new TextButton(game.i18n.t("settings.controls"), game.skin);
        controls.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.settings.flush();
                game.setScreen(new ControlsScreen(game, returnTo));
                dispose();
            }
        });
        TextButton back = new TextButton(game.i18n.t(returnTo != null ? "settings.backToPractice" : "practice.back"),
            game.skin, returnTo != null ? "default" : "secondary");
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                leave();
            }
        });
        buttons.add(controls).width(260f).height(46f).padRight(12f);
        buttons.add(back).width(returnTo != null ? 280f : 220f).height(46f);
        root.add(buttons);
    }

    /** "Bleu Iceberg (RNK) · nacarado": nombre del catálogo, código de pintura y acabado. */
    private String paintLabel(com.pelayo.autoescuela.app.CarPaint paint) {
        if (paint == com.pelayo.autoescuela.app.CarPaint.ORIGINAL) {
            return game.i18n.t("settings.paint.original");
        }
        return game.i18n.t("settings.paint.label", paint.officialName, paint.code,
            game.i18n.t("settings.paint.finish." + paint.finish.name()));
    }

    /** Casilla con texto que guarda su valor al cambiar. */
    private CheckBox check(String textKey, boolean value, java.util.function.Consumer<Boolean> onChange) {
        CheckBox box = new CheckBox("  " + game.i18n.t(textKey), game.skin);
        box.setChecked(value);
        box.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                onChange.accept(box.isChecked());
            }
        });
        return box;
    }

    /** Botón que recorre los valores de un enum al pulsarlo. */
    private <E extends Enum<E>> TextButton cycle(E[] values, E current, String keyPrefix,
                                                 java.util.function.Consumer<Enum<?>> onChange) {
        TextButton button = new TextButton(game.i18n.t(keyPrefix + current.name()), game.skin, "secondary");
        final int[] index = {current.ordinal()};
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                index[0] = (index[0] + 1) % values.length;
                button.setText(game.i18n.t(keyPrefix + values[index[0]].name()));
                onChange.accept(values[index[0]]);
            }
        });
        return button;
    }

    private void leave() {
        game.settings.flush();
        game.setScreen(returnTo != null ? returnTo : new MainMenuScreen(game));
        dispose();
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            leave();
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
