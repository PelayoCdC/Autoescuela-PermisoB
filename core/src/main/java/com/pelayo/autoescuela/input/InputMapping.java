package com.pelayo.autoescuela.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.ObjectIntMap;

/**
 * Asignación de teclas del conductor, reasignable desde Ajustes y guardada
 * en {@link Preferences}. Cada {@link Action} tiene una tecla por defecto.
 */
public class InputMapping {

    /** Acciones del teclado. {@code help} es la clave i18n de la descripción. */
    public enum Action {
        THROTTLE(Input.Keys.W, "controls.throttle"),
        BRAKE(Input.Keys.S, "controls.brake"),
        STEER_LEFT(Input.Keys.A, "controls.steerLeft"),
        STEER_RIGHT(Input.Keys.D, "controls.steerRight"),
        CLUTCH(Input.Keys.Q, "controls.clutch"),
        CLUTCH_HALF(Input.Keys.SHIFT_LEFT, "controls.clutchHalf"),
        HANDBRAKE(Input.Keys.SPACE, "controls.handbrake"),
        GEAR_UP(Input.Keys.E, "controls.gearUp"),
        GEAR_DOWN(Input.Keys.Z, "controls.gearDown"),
        GEAR_1(Input.Keys.NUM_1, "controls.gear1"),
        GEAR_2(Input.Keys.NUM_2, "controls.gear2"),
        GEAR_3(Input.Keys.NUM_3, "controls.gear3"),
        GEAR_4(Input.Keys.NUM_4, "controls.gear4"),
        GEAR_5(Input.Keys.NUM_5, "controls.gear5"),
        GEAR_R(Input.Keys.R, "controls.gearR"),
        GEAR_N(Input.Keys.N, "controls.gearN"),
        IGNITION(Input.Keys.ENTER, "controls.ignition"),
        INDICATOR_LEFT(Input.Keys.LEFT, "controls.indicatorLeft"),
        INDICATOR_RIGHT(Input.Keys.RIGHT, "controls.indicatorRight"),
        LOOK_LEFT(Input.Keys.COMMA, "controls.lookLeft"),
        LOOK_RIGHT(Input.Keys.PERIOD, "controls.lookRight"),
        LOOK_BACK(Input.Keys.M, "controls.lookBack"),
        CAMERA(Input.Keys.V, "controls.camera"),
        MAP(Input.Keys.TAB, "controls.map"),
        RESET(Input.Keys.BACKSPACE, "controls.reset"),
        PAUSE(Input.Keys.P, "controls.pause"),
        CONTINUE(Input.Keys.C, "controls.continue"),
        LESSON_BACK(Input.Keys.B, "controls.lessonBack"),
        HELP(Input.Keys.F1, "controls.help");

        public final int defaultKey;
        public final String helpKey;

        Action(int defaultKey, String helpKey) {
            this.defaultKey = defaultKey;
            this.helpKey = helpKey;
        }
    }

    private static final String PREFS = "autoescuela-keys";
    private final Preferences prefs;
    private final ObjectIntMap<Action> keys = new ObjectIntMap<>();

    public InputMapping() {
        this(Gdx.app.getPreferences(PREFS));
    }

    public InputMapping(Preferences prefs) {
        this.prefs = prefs;
        for (Action a : Action.values()) {
            keys.put(a, prefs.getInteger(a.name(), a.defaultKey));
        }
    }

    public int key(Action action) {
        return keys.get(action, action.defaultKey);
    }

    /** Asigna la tecla; si otra acción la tenía, esa otra queda sin tecla (-1). */
    public void set(Action action, int key) {
        for (Action other : Action.values()) {
            if (other != action && keys.get(other, -1) == key) {
                keys.put(other, -1);
                prefs.putInteger(other.name(), -1);
            }
        }
        keys.put(action, key);
        prefs.putInteger(action.name(), key);
        prefs.flush();
    }

    public void resetDefaults() {
        for (Action a : Action.values()) {
            keys.put(a, a.defaultKey);
            prefs.putInteger(a.name(), a.defaultKey);
        }
        prefs.flush();
    }

    public boolean isPressed(Action action) {
        int k = key(action);
        return k >= 0 && Gdx.input.isKeyPressed(k);
    }

    public boolean isJustPressed(Action action) {
        int k = key(action);
        return k >= 0 && Gdx.input.isKeyJustPressed(k);
    }

    /** Acción asignada a esa tecla, o null. */
    public Action actionFor(int keycode) {
        for (Action a : Action.values()) {
            if (keys.get(a, -1) == keycode) {
                return a;
            }
        }
        return null;
    }

    /** Si no es null, da el nombre de cada acción en lugar de su tecla (controles táctiles). */
    private java.util.function.Function<Action, String> nameOverride;

    /** Hay nombres de mandos táctiles en lugar de teclas. */
    public boolean hasNameOverride() {
        return nameOverride != null;
    }

    public void setNameOverride(java.util.function.Function<Action, String> override) {
        this.nameOverride = override;
    }

    /** Nombre legible de la tecla de una acción (o del mando táctil equivalente). */
    public String keyName(Action action) {
        if (nameOverride != null) {
            return nameOverride.apply(action);
        }
        return keyName(key(action));
    }

    public static String keyName(int keycode) {
        if (keycode < 0) {
            return "—";
        }
        switch (keycode) {
            case Input.Keys.SPACE: return "Espacio";
            case Input.Keys.ENTER: return "Intro";
            case Input.Keys.BACKSPACE: return "Retroceso";
            case Input.Keys.SHIFT_LEFT: return "Shift izq.";
            case Input.Keys.SHIFT_RIGHT: return "Shift der.";
            case Input.Keys.CONTROL_LEFT: return "Ctrl izq.";
            case Input.Keys.CONTROL_RIGHT: return "Ctrl der.";
            case Input.Keys.ALT_LEFT: return "Alt";
            case Input.Keys.ALT_RIGHT: return "Alt Gr";
            case Input.Keys.LEFT: return "Flecha izq.";
            case Input.Keys.RIGHT: return "Flecha der.";
            case Input.Keys.UP: return "Flecha arriba";
            case Input.Keys.DOWN: return "Flecha abajo";
            case Input.Keys.COMMA: return "Coma";
            case Input.Keys.PERIOD: return "Punto";
            case Input.Keys.TAB: return "Tab";
            case Input.Keys.ESCAPE: return "Esc";
            default:
                String s = Input.Keys.toString(keycode);
                return s == null ? "?" + keycode : s;
        }
    }
}
