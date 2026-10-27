package com.pelayo.autoescuela.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.pelayo.autoescuela.AutoescuelaGame;
import com.pelayo.autoescuela.input.InputMapping;
import com.pelayo.autoescuela.input.InputMapping.Action;
import com.pelayo.autoescuela.ui.SkinFactory;

/** Reasignación de teclas: pulsa "Cambiar" en una acción y después la tecla nueva. */
public class ControlsScreen extends ScreenAdapter {

    private final AutoescuelaGame game;
    private final Stage stage;
    private final ObjectMap<Action, TextButton> buttons = new ObjectMap<>();
    private final Label status;
    private Action waiting;

    /** Pantalla a la que vuelven los ajustes al salir (la práctica en pausa), o null. */
    private final com.badlogic.gdx.Screen returnTo;

    public ControlsScreen(AutoescuelaGame game) {
        this(game, null);
    }

    public ControlsScreen(AutoescuelaGame game, com.badlogic.gdx.Screen returnTo) {
        this.game = game;
        this.returnTo = returnTo;
        stage = new Stage(new ExtendViewport(AutoescuelaGame.UI_WIDTH, AutoescuelaGame.UI_HEIGHT));
        InputMapping keys = game.keys;

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(game.skin.getDrawable("bg"));
        root.pad(20f);
        stage.addActor(root);
        root.add(new Label(game.i18n.t("controls.screenTitle"), game.skin, "title")).padBottom(6f).row();
        status = new Label(game.i18n.t("controls.hint"), game.skin, "small");
        root.add(status).padBottom(10f).row();

        Table list = new Table();
        list.top();
        list.defaults().padBottom(4f);
        for (Action a : Action.values()) {
            Label name = new Label(game.i18n.t(a.helpKey), game.skin, "default");
            TextButton key = new TextButton(keys.keyName(a), game.skin, "secondary");
            key.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    waiting = a;
                    status.setText(game.i18n.t("controls.pressKey", game.i18n.t(a.helpKey)));
                    status.setColor(1f, 0.85f, 0.4f, 1f);
                }
            });
            buttons.put(a, key);
            list.add(name).left().width(520f).padRight(16f);
            list.add(key).width(200f).height(38f).row();
        }
        ScrollPane scroll = new ScrollPane(list, game.skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        root.add(scroll).expand().fill().row();

        Table bottom = new Table();
        TextButton reset = new TextButton(game.i18n.t("controls.resetDefaults"), game.skin, "secondary");
        reset.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                keys.resetDefaults();
                refresh();
            }
        });
        TextButton back = new TextButton(game.i18n.t("practice.back"), game.skin);
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                leave();
            }
        });
        bottom.add(reset).width(360f).height(46f).padRight(12f);
        bottom.add(back).width(200f).height(46f);
        root.add(bottom).padTop(10f);
    }

    private void refresh() {
        for (ObjectMap.Entry<Action, TextButton> e : buttons) {
            e.value.setText(game.keys.keyName(e.key));
        }
    }

    private final InputAdapter keyCatcher = new InputAdapter() {
        @Override
        public boolean keyDown(int keycode) {
            if (waiting == null) {
                return false;
            }
            if (keycode == Input.Keys.ESCAPE) {
                waiting = null;
                status.setText(game.i18n.t("controls.hint"));
                status.setColor(SkinFactory.TEXT_DIM);
                return true;
            }
            game.keys.set(waiting, keycode);
            status.setText(game.i18n.t("controls.assigned", game.i18n.t(waiting.helpKey), InputMapping.keyName(keycode)));
            status.setColor(SkinFactory.OK);
            waiting = null;
            refresh();
            return true;
        }
    };

    private void leave() {
        game.setScreen(new SettingsScreen(game, returnTo));
        dispose();
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputMultiplexer(keyCatcher, stage));
    }

    @Override
    public void render(float delta) {
        if (waiting == null && Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
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
