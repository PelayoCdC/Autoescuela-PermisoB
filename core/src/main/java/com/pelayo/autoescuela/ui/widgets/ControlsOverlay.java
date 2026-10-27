package com.pelayo.autoescuela.ui.widgets;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.pelayo.autoescuela.app.I18n;
import com.pelayo.autoescuela.input.InputMapping;
import com.pelayo.autoescuela.input.InputMapping.Action;

/**
 * Panel con todas las teclas del conductor y cómo se usa el embrague. Se
 * muestra con la tecla de ayuda (F1) y al empezar una práctica.
 */
public class ControlsOverlay extends Table {

    private static final Action[][] COLUMNS = {
        {Action.THROTTLE, Action.BRAKE, Action.STEER_LEFT, Action.STEER_RIGHT, Action.CLUTCH, Action.CLUTCH_HALF,
            Action.HANDBRAKE, Action.IGNITION},
        {Action.GEAR_1, Action.GEAR_2, Action.GEAR_3, Action.GEAR_4, Action.GEAR_5, Action.GEAR_R, Action.GEAR_N,
            Action.GEAR_UP, Action.GEAR_DOWN},
        {Action.LOOK_LEFT, Action.LOOK_RIGHT, Action.LOOK_BACK, Action.INDICATOR_LEFT, Action.INDICATOR_RIGHT,
            Action.CAMERA, Action.RESET, Action.PAUSE, Action.CONTINUE, Action.LESSON_BACK, Action.HELP},
    };

    public ControlsOverlay(Skin skin, I18n i18n, InputMapping keys) {
        setBackground(skin.getDrawable("panel-dark"));
        pad(18f);
        top();
        add(new Label(i18n.t("controls.title"), skin, "default")).colspan(3).padBottom(10f).row();

        Table columns = new Table();
        for (Action[] column : COLUMNS) {
            Table t = new Table();
            t.top().left();
            t.defaults().left().padBottom(3f);
            for (Action a : column) {
                Label key = new Label(keys.keyName(a), skin, "small");
                key.setColor(1f, 0.85f, 0.4f, 1f);
                t.add(key).width(104f).padRight(8f);
                t.add(new Label(i18n.t(a.helpKey), skin, "small")).row();
            }
            columns.add(t).top().padRight(24f);
        }
        add(columns).row();

        Label clutch = new Label(i18n.t("controls.clutchHelp", keys.keyName(Action.CLUTCH),
            keys.keyName(Action.CLUTCH_HALF), keys.keyName(Action.THROTTLE)), skin, "small");
        clutch.setWrap(true);
        add(clutch).width(820f).padTop(12f).row();
        Label foot = new Label(i18n.t("controls.foot", keys.keyName(Action.HELP)), skin, "small");
        foot.setColor(0.75f, 0.78f, 0.82f, 1f);
        add(foot).padTop(8f);
    }
}
