package com.pelayo.autoescuela.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Value;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.pelayo.autoescuela.app.I18n;
import com.pelayo.autoescuela.data.ScenarioDef.GoalDef;
import com.pelayo.autoescuela.data.ScenarioDef.StepDef;
import com.pelayo.autoescuela.input.InputMapping;
import com.pelayo.autoescuela.input.InputMapping.Action;
import com.pelayo.autoescuela.sim.eval.maneuvers.Tutorial;
import com.pelayo.autoescuela.sim.vehicle.VehicleEvent;
import com.pelayo.autoescuela.ui.SkinFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Panel de una lección guiada: número de paso, explicación, objetivos con
 * su marca de cumplido, pista si el alumno se atasca y botón Continuar en los
 * pasos de lectura. Se reconstruye al cambiar de paso y se refresca cada
 * frame con {@link #update}.
 */
public class TutorialPanel extends Table {

    private static final Pattern KEY = Pattern.compile("\\{([A-Z_0-9]+)\\}");
    private static final String KEY_COLOR = "[#F2B13B]";

    private final Skin skin;
    private final I18n i18n;
    private final InputMapping keys;
    private final Tutorial tutorial;
    private final String lessonName;
    private final Runnable onContinue;

    private int shownStep = -1;
    private final Label stepLabel;
    private final ProgressBar progress;
    private final Label title;
    private final Label text;
    private final Table goalsTable;
    private final Label hint;
    private final TextButton continueButton;
    private final TextButton prevButton;
    private final TextButton nextButton;
    private final Label reviewLabel;
    private boolean shownReviewing;
    private final Array<Image> checks = new Array<>();
    private final Array<Label> goalLabels = new Array<>();

    public TutorialPanel(Skin skin, I18n i18n, InputMapping keys, Tutorial tutorial, String lessonName,
                         float width, Runnable onContinue) {
        this.skin = skin;
        this.i18n = i18n;
        this.keys = keys;
        this.tutorial = tutorial;
        this.lessonName = lessonName;
        this.onContinue = onContinue;

        setBackground(skin.getDrawable("panel-dark"));
        pad(16f, 18f, 16f, 18f);
        top().left();
        defaults().left();

        Table header = new Table();
        Image icon = new Image(skin.getDrawable("icon-wheel"), Scaling.fit);
        icon.setColor(SkinFactory.ACCENT_LIGHT);
        header.add(icon).size(22f).padRight(8f);
        Label lesson = new Label(lessonName, skin, "accent");
        lesson.setEllipsis(true);
        header.add(lesson).expandX().fillX().minWidth(0f);
        // Navegación entre pasos: anterior (repasar) y siguiente (hasta el paso al que se había llegado)
        prevButton = new TextButton("<", skin, "small-secondary");
        prevButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                tutorial.previous();
            }
        });
        nextButton = new TextButton(">", skin, "small-secondary");
        nextButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                tutorial.next();
            }
        });
        stepLabel = new Label("", skin, "small");
        header.add(prevButton).size(34f, 30f).padLeft(8f);
        header.add(stepLabel).padLeft(6f).padRight(6f);
        header.add(nextButton).size(34f, 30f);
        add(header).width(width).row();

        progress = new ProgressBar(0f, 1f, 0.001f, false, skin, "ok-horizontal");
        progress.setAnimateDuration(0.3f);
        add(progress).width(width).padTop(8f).padBottom(12f).row();

        reviewLabel = new Label(i18n.t("lesson.reviewing", keys.keyName(Action.LESSON_BACK),
            keys.keyName(Action.CONTINUE)), skin, "small-white");
        reviewLabel.setWrap(true);
        Table review = new Table();
        review.setBackground(skin.getDrawable("chip-warn"));
        review.add(reviewLabel).growX();
        add(review).width(width).padBottom(8f).row();

        title = new Label("", skin, "bold");
        title.setWrap(true);
        add(title).width(width).padBottom(6f).row();

        text = new Label("", skin, "small-white");
        text.setWrap(true);
        add(text).width(width).padBottom(10f).row();

        goalsTable = new Table();
        goalsTable.top().left();
        add(goalsTable).width(width).row();

        hint = new Label("", skin, "warn");
        hint.setWrap(true);
        add(hint).width(width).padTop(10f).row();

        continueButton = new TextButton(continueText("lesson.continue"), skin);
        continueButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                TutorialPanel.this.onContinue.run();
            }
        });
        add(continueButton).height(46f).right().padTop(12f);
    }

    /** Refresca el panel con el estado de la lección. */
    public void update() {
        StepDef step = tutorial.getStep();
        if (step == null) {
            return;
        }
        int index = tutorial.getStepIndex();
        if (index != shownStep || tutorial.isReviewing() != shownReviewing) {
            rebuild(step, index);
        }
        prevButton.setDisabled(!tutorial.canGoBack());
        nextButton.setDisabled(!tutorial.canGoForward());
        for (int i = 0; i < checks.size; i++) {
            boolean met = tutorial.isGoalMet(i);
            checks.get(i).setDrawable(skin.getDrawable(met ? "icon-check" : "icon-play"));
            checks.get(i).setColor(met ? SkinFactory.OK : SkinFactory.TEXT_FAINT);
            GoalDef g = step.goals.get(i);
            goalLabels.get(i).setText(goalText(g));
            goalLabels.get(i).setColor(met ? new Color(0.75f, 0.95f, 0.8f, 1f) : Color.WHITE);
        }
        String hintText = "";
        VehicleEvent problem = tutorial.getRecentProblem();
        if (problem != null) {
            hintText = problemText(problem);
        } else if (step.hint != null && tutorial.getStepTime() > step.hintAfter) {
            hintText = keys(step.hint);
        }
        if (!hintText.equals(hint.getText().toString())) {
            hint.setText(hintText);
            showHint(!hintText.isEmpty());
        }
    }

    private void rebuild(StepDef step, int index) {
        shownStep = index;
        stepLabel.setText(i18n.t("lesson.step", index + 1, tutorial.getStepCount()));
        progress.setValue(index / (float) tutorial.getStepCount());
        title.setText(keys(step.title));
        text.setText(step.text == null ? "" : keys(step.text));
        goalsTable.clear();
        checks.clear();
        goalLabels.clear();
        for (GoalDef g : step.goals) {
            Image check = new Image(skin.getDrawable("icon-play"), Scaling.fit);
            Label label = new Label(goalText(g), skin, "small-white");
            label.setWrap(true);
            goalsTable.add(check).size(18f).top().padTop(2f).padRight(10f);
            goalsTable.add(label).growX().padBottom(5f).row();
            checks.add(check);
            goalLabels.add(label);
        }
        shownReviewing = tutorial.isReviewing();
        boolean showContinue = step.ack || shownReviewing;
        continueButton.setVisible(showContinue);
        continueButton.setText(continueText(shownReviewing && !step.ack ? "lesson.resume" : "lesson.continue"));
        getCell(continueButton).height(showContinue ? 46f : 0f).padTop(showContinue ? 12f : 0f);
        Table review = (Table) reviewLabel.getParent();
        review.setVisible(shownReviewing);
        getCell(review).height(shownReviewing ? Value.prefHeight : Value.zero).padBottom(shownReviewing ? 8f : 0f);
        hint.setText("");
        showHint(false);
        clearActions();
        getColor().a = 0.2f;
        addAction(Actions.fadeIn(0.35f));
        invalidateHierarchy();
    }

    /** Muestra u oculta la pista sin dejar hueco en el panel. */
    private void showHint(boolean visible) {
        hint.setVisible(visible);
        getCell(hint).height(visible ? Value.prefHeight : Value.zero).padTop(visible ? 10f : 0f);
        invalidateHierarchy();
    }

    // --- Textos -----------------------------------------------------------------

    /** Texto del botón con su tecla entre paréntesis (en táctil, sin tecla: el botón es el mando). */
    private String continueText(String key) {
        String text = i18n.t(key, keys.keyName(Action.CONTINUE));
        return keys.hasNameOverride() ? text.replaceAll("\\s*\\(.*\\)\\s*$", "") : text;
    }

    /** Sustituye {ACCION} por la tecla asignada, resaltada. */
    private String keys(String s) {
        Matcher m = KEY.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name;
            try {
                name = keys.keyName(Action.valueOf(m.group(1)));
            } catch (IllegalArgumentException e) {
                name = m.group(1);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(KEY_COLOR + name + "[]"));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String key(Action a) {
        return KEY_COLOR + keys.keyName(a) + "[]";
    }

    private String goalText(GoalDef g) {
        if (g.label != null) {
            return keys(g.label);
        }
        boolean hasMin = g.min > -1e8f;
        boolean hasMax = g.max < 1e8f;
        switch (g.type) {
            case "clutch":
                if (hasMin && hasMax) {
                    return i18n.t("lesson.goal.clutchBite", key(Action.CLUTCH_HALF));
                }
                return hasMin ? i18n.t("lesson.goal.clutchDown", key(Action.CLUTCH))
                    : i18n.t("lesson.goal.clutchUp");
            case "brake":
                return hasMin ? i18n.t("lesson.goal.brakeDown", key(Action.BRAKE)) : i18n.t("lesson.goal.brakeUp");
            case "throttle":
                return hasMin ? i18n.t("lesson.goal.throttle", key(Action.THROTTLE)) : i18n.t("lesson.goal.noThrottle");
            case "steer":
                return hasMin ? i18n.t("lesson.goal.steerLeft", key(Action.STEER_LEFT))
                    : i18n.t("lesson.goal.steerRight", key(Action.STEER_RIGHT));
            case "rpm":
                return hasMin ? i18n.t("lesson.goal.rpmAbove", (int) g.min) : i18n.t("lesson.goal.rpmBelow", (int) g.max);
            case "speed":
                return hasMin ? i18n.t("lesson.goal.speedAbove", (int) g.min, (int) tutorial.getSpeedKmh())
                    : i18n.t("lesson.goal.speedBelow", (int) g.max, (int) tutorial.getSpeedKmh());
            case "gear":
                return i18n.t("lesson.goal.gear", i18n.t("lesson.gear." + g.value), key(gearAction(g.value)));
            case "handbrake":
                return i18n.t(g.value != 0 ? "lesson.goal.handbrakeOn" : "lesson.goal.handbrakeOff",
                    key(Action.HANDBRAKE));
            case "engine":
                return i18n.t(g.value != 0 ? "lesson.goal.engineOn" : "lesson.goal.engineOff", key(Action.IGNITION));
            case "stopped":
                return i18n.t("lesson.goal.stopped");
            case "moving":
                return i18n.t("lesson.goal.moving");
            case "distance": {
                float done = Math.max(0f, Math.min(Math.abs(g.meters), g.meters >= 0f ? tutorial.getDistance()
                    : -tutorial.getDistance()));
                return i18n.t(g.meters >= 0f ? "lesson.goal.forward" : "lesson.goal.backward",
                    (int) Math.abs(g.meters), (int) done);
            }
            case "indicator":
                return g.value < 0 ? i18n.t("lesson.goal.indicatorLeft", key(Action.INDICATOR_LEFT))
                    : g.value > 0 ? i18n.t("lesson.goal.indicatorRight", key(Action.INDICATOR_RIGHT))
                    : i18n.t("lesson.goal.indicatorOff");
            case "look":
                return i18n.t("lesson.goal.lookBack", key(Action.LOOK_BACK));
            case "event":
                return i18n.t("lesson.goal.event." + g.event);
            default:
                return g.type;
        }
    }

    private static Action gearAction(int gear) {
        switch (gear) {
            case -1: return Action.GEAR_R;
            case 0: return Action.GEAR_N;
            case 1: return Action.GEAR_1;
            case 2: return Action.GEAR_2;
            case 3: return Action.GEAR_3;
            case 4: return Action.GEAR_4;
            default: return Action.GEAR_5;
        }
    }

    private String problemText(VehicleEvent e) {
        switch (e) {
            case STALL: return i18n.t("lesson.problem.stall", key(Action.CLUTCH), key(Action.GEAR_N),
                key(Action.IGNITION));
            case GRIND: return i18n.t("lesson.problem.grind", key(Action.CLUTCH));
            case START_IN_GEAR: return i18n.t("lesson.problem.startInGear", key(Action.CLUTCH), key(Action.GEAR_N));
            case REVERSE_REFUSED: return i18n.t("lesson.problem.reverse");
            default: return "";
        }
    }
}
