package com.pelayo.autoescuela.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.pelayo.autoescuela.app.I18n;
import com.pelayo.autoescuela.input.DriverInputState;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;

/**
 * Controles táctiles para móvil (en horizontal), repartidos por manos: el
 * volante se gira tocando el propio volante de la cabina (o uno dibujado con
 * las cámaras de fuera), con los intermitentes a sus lados; abajo, a un lado
 * los tres pedales (la altura del dedo es el recorrido; el embrague marca su
 * punto de fricción) y al otro la palanca en H con el freno de mano y la llave.
 * Por defecto los pedales van a la izquierda: así una mano pisa el embrague
 * mientras la otra cambia de marcha o gira el volante. Arrastrando el dedo por
 * cualquier otra parte de la pantalla se mira alrededor. Cámara y recolocar
 * van arriba a la derecha. Escribe en {@link State}, que lee TouchDriverInput.
 */
public class TouchControls extends Group {

    /** Estado leído por el DriverInput; los pulsos se consumen con take*(). */
    public static class State {
        public float steer;
        public boolean steering;
        public float throttle, brake, clutch;
        /** Mirar alrededor arrastrando el dedo (-1..1, + izquierda / + arriba). */
        public float lookYaw, lookPitch;
        private boolean handbrake, gearUp, gearDown, ignition, indLeft, indRight, camera, reset;
        private int gearSelect = DriverInputState.GEAR_NONE;

        public boolean takeHandbrake() { boolean v = handbrake; handbrake = false; return v; }
        public boolean takeGearUp() { boolean v = gearUp; gearUp = false; return v; }
        public boolean takeGearDown() { boolean v = gearDown; gearDown = false; return v; }
        public boolean takeIgnition() { boolean v = ignition; ignition = false; return v; }
        public boolean takeIndicatorLeft() { boolean v = indLeft; indLeft = false; return v; }
        public boolean takeIndicatorRight() { boolean v = indRight; indRight = false; return v; }
        public boolean takeCamera() { boolean v = camera; camera = false; return v; }
        public boolean takeReset() { boolean v = reset; reset = false; return v; }
        public int takeGearSelect() {
            int v = gearSelect;
            gearSelect = DriverInputState.GEAR_NONE;
            return v;
        }
    }

    private static final Color AMBER = new Color(0.95f, 0.69f, 0.23f, 1f);
    private static final Color GREEN = new Color(0.25f, 0.85f, 0.4f, 1f);
    /** Tamaño de los botones redondos (dedo cómodo). */
    private static final float BUTTON = 64f;

    public final State state = new State();
    private final Skin skin;
    private final LookPad lookPad;
    private final SteeringWheel wheel;
    private final Pedal clutch;
    private final Pedal brake;
    private final Pedal throttle;
    private final GearShifter shifter;
    private final RoundButton handbrake;
    private final RoundButton ignition;
    private final RoundButton indLeft;
    private final RoundButton indRight;
    private final RoundButton camera;
    private final RoundButton reset;
    private final GlyphLayout layout = new GlyphLayout();
    private VehicleState vehicleState;
    /** Pedales a la izquierda (mano izquierda) y palanca a la derecha, o al revés. */
    private final boolean pedalsLeft;
    private final java.util.function.IntConsumer haptic;
    /** Zonas que ocupan abajo los pedales y la palanca (con sus rótulos), para no ponerles nada encima. */
    private final com.badlogic.gdx.math.Rectangle pedalArea = new com.badlogic.gdx.math.Rectangle();
    private final com.badlogic.gdx.math.Rectangle handArea = new com.badlogic.gdx.math.Rectangle();
    private float stageW;

    /**
     * @param biteLow  inicio del punto de fricción como recorrido del pedal liberado (de vehicle.json)
     * @param biteHigh final del punto de fricción
     * @param pedalsLeft pedales a la izquierda y palanca a la derecha (si no, al revés)
     * @param stickyClutch el embrague se queda donde se suelta el dedo
     * @param haptic      vibración (milisegundos) al entrar en la franja de fricción
     */
    public TouchControls(Skin skin, I18n i18n, float stageWidth, float stageHeight, float biteLow, float biteHigh,
                         boolean pedalsLeft, boolean stickyClutch, java.util.function.IntConsumer haptic) {
        this.skin = skin;
        this.pedalsLeft = pedalsLeft;
        this.haptic = haptic;
        setTouchable(Touchable.childrenOnly);

        // Primero (debajo de todo): mirar alrededor arrastrando por la pantalla
        lookPad = new LookPad();
        addActor(lookPad);

        wheel = new SteeringWheel(140f);
        addActor(wheel);

        float pedalW = 76f;
        float pedalH = 220f;
        clutch = new Pedal(i18n.t("hud.clutch"), new Color(0.4f, 0.6f, 1f, 1f), pedalW, pedalH, v -> state.clutch = v);
        // El punto de fricción, en altura del dedo (1 = pisado a fondo): lo contrario del recorrido liberado
        clutch.bite(1f - biteHigh, 1f - biteLow);
        clutch.sticky = stickyClutch;
        brake = new Pedal(i18n.t("hud.brake"), new Color(1f, 0.35f, 0.3f, 1f), pedalW, pedalH, v -> state.brake = v);
        throttle = new Pedal(i18n.t("hud.throttle"), new Color(0.4f, 0.9f, 0.4f, 1f), pedalW, pedalH, v -> state.throttle = v);
        addActor(clutch);
        addActor(brake);
        addActor(throttle);

        shifter = new GearShifter(190f, 176f);
        addActor(shifter);

        handbrake = new RoundButton(null, "P", i18n.t("touch.handbrake"), 0f, () -> state.handbrake = true);
        ignition = new RoundButton("icon-key", null, i18n.t("touch.ignition"), 0f, () -> state.ignition = true);
        indLeft = new RoundButton("arrow", null, null, 0f, () -> state.indLeft = true);
        indRight = new RoundButton("arrow", null, null, 180f, () -> state.indRight = true);
        camera = new RoundButton("icon-camera", null, null, 0f, () -> state.camera = true);
        reset = new RoundButton("icon-reset", null, null, 0f, () -> state.reset = true);
        addActor(handbrake);
        addActor(ignition);
        addActor(indLeft);
        addActor(indRight);
        addActor(camera);
        addActor(reset);
        relayout(stageWidth, stageHeight);
        setWheelAnchor(stageWidth / 2f, 170f, 130f, false);
    }

    /**
     * Coloca los mandos fijos para ese tamaño de pantalla (unidades de interfaz). Llamar al redimensionar.
     * Abajo, un grupo por mano: los pedales (embrague, freno y gas, en el orden del coche) y, en el otro
     * lado, la palanca con el freno de mano y la llave a su lado, por la parte de dentro.
     */
    public void relayout(float w, float h) {
        setSize(w, h);
        stageW = w;
        lookPad.setBounds(0f, 0f, w, h);
        float margin = 36f;
        float gap = 14f;
        float pedalW = clutch.getWidth();
        float pedalsW = 3 * pedalW + 2 * gap;
        float pedalsX = pedalsLeft ? margin : w - margin - pedalsW;
        clutch.setPosition(pedalsX, 30f);
        brake.setPosition(pedalsX + pedalW + gap, 30f);
        throttle.setPosition(pedalsX + 2 * (pedalW + gap), 30f);
        pedalArea.set(pedalsX, 0f, pedalsW, 30f + clutch.getHeight() + 30f);

        float handW = shifter.getWidth() + 28f + BUTTON;
        float handX = pedalsLeft ? w - margin - handW : margin;
        float buttonsX = pedalsLeft ? handX : handX + shifter.getWidth() + 28f;
        shifter.setPosition(pedalsLeft ? handX + BUTTON + 28f : handX, 34f);
        handbrake.setPosition(buttonsX, shifter.getY() + shifter.getHeight() - BUTTON);
        ignition.setPosition(buttonsX, 40f);
        handArea.set(handX, 0f, handW, shifter.getY() + shifter.getHeight() + 8f);

        // Cámara y recolocar: arriba a la derecha, bajo el botón de pausa (junto al minimapa)
        float colX = w - 22f - 210f - 72f + (56f - BUTTON) / 2f;
        camera.setPosition(colX, h - 40f - 44f - 16f - BUTTON);
        reset.setPosition(colX, camera.getY() - 16f - BUTTON);
    }

    public boolean isPedalsLeft() {
        return pedalsLeft;
    }

    /** Zona de los pedales con sus rótulos. */
    public com.badlogic.gdx.math.Rectangle getPedalArea() {
        return pedalArea;
    }

    /** Altura hasta la que llegan los mandos de abajo en el lado derecho (para colocar el retrovisor encima). */
    public float getRightSideTop() {
        com.badlogic.gdx.math.Rectangle right = pedalsLeft ? handArea : pedalArea;
        return right.y + right.height;
    }

    /**
     * Coloca la zona del volante: centro y radio en unidades de interfaz. Con
     * {@code overlay} está encima del volante 3D (vista de cabina) y no se
     * dibuja el volante en pantalla. Los intermitentes van a sus lados.
     */
    public void setWheelAnchor(float cx, float cy, float radius, boolean overlay) {
        if (!(state.steering && wheel.overlay == overlay)) {
            wheel.radius = radius;
            wheel.overlay = overlay;
            wheel.setBounds(cx - radius, cy - radius, radius * 2f, radius * 2f);
        }
        float r = wheel.radius;
        float wcx = wheel.getX() + r, wcy = wheel.getY() + r;
        placeClear(indLeft, wcx - r - 18f - BUTTON, wcy + 10f);
        placeClear(indRight, wcx + r + 18f, wcy + 10f);
    }

    /** Pone el botón ahí o, si pisaría los pedales o la palanca, justo por encima de ellos. */
    private void placeClear(RoundButton button, float x, float y) {
        x = MathUtils.clamp(x, 8f, Math.max(8f, stageW - 8f - BUTTON));
        for (com.badlogic.gdx.math.Rectangle area : new com.badlogic.gdx.math.Rectangle[] {pedalArea, handArea}) {
            boolean overlapX = x + BUTTON + 10f > area.x && x - 10f < area.x + area.width;
            if (overlapX && y < area.y + area.height + 10f) {
                y = area.y + area.height + 14f;
            }
        }
        button.setPosition(x, y);
    }

    /** Refleja el estado del coche: marcha, freno de mano, pedales, volante, motor e intermitentes. */
    public void setVehicleState(VehicleState s) {
        this.vehicleState = s;
        handbrake.active = s.handbrake;
        ignition.active = s.engineRunning || s.engineStarting;
        indLeft.active = s.indicatorLeft;
        indRight.active = s.indicatorRight;
        // Los mandos en pantalla muestran lo que hace el coche, venga del dedo, del teclado o del mando
        clutch.shown = s.clutch;
        brake.shown = s.brake;
        throttle.shown = s.throttle;
        wheel.shownSteer = s.steer;
        shifter.gear = s.gear;
    }

    // --- Mirar alrededor ----------------------------------------------------------------

    /** Zona invisible de toda la pantalla: arrastrar mira a los lados (y algo arriba y abajo). */
    private class LookPad extends Actor {
        private float startX, startY, startYaw, startPitch;

        LookPad() {
            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    startX = x;
                    startY = y;
                    startYaw = state.lookYaw;
                    startPitch = state.lookPitch;
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    // 450 unidades de arrastre = cabeza girada del todo
                    state.lookYaw = MathUtils.clamp(startYaw + (x - startX) / 450f, -1f, 1f);
                    state.lookPitch = MathUtils.clamp(startPitch - (y - startY) / 500f, -1f, 1f);
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    // Al soltar, la cabeza vuelve sola a mirar al frente
                    state.lookYaw = 0f;
                    state.lookPitch = 0f;
                }
            });
        }
    }

    // --- Volante -------------------------------------------------------------------------

    /** Volante circular: el ángulo del dedo respecto al centro da la dirección. */
    private class SteeringWheel extends Actor {
        private float radius;
        /** Encima del volante 3D de la cabina: solo se dibuja un aro de guía. */
        private boolean overlay;
        private float angle;
        /** Dirección real del coche (teclado o mando) para dibujar el volante cuando no se toca. */
        float shownSteer;
        private float grabAngle;
        private float angleAtGrab;

        SteeringWheel(float radius) {
            this.radius = radius;
            setSize(radius * 2f, radius * 2f);
            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    grabAngle = MathUtils.atan2(y - radius, x - radius);
                    angleAtGrab = angle;
                    state.steering = true;
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    float a = MathUtils.atan2(y - radius, x - radius);
                    float delta = a - grabAngle;
                    while (delta > MathUtils.PI) delta -= MathUtils.PI2;
                    while (delta < -MathUtils.PI) delta += MathUtils.PI2;
                    // Media vuelta de volante en pantalla = giro máximo
                    angle = MathUtils.clamp(angleAtGrab + delta, -MathUtils.PI, MathUtils.PI);
                    state.steer = angle / MathUtils.PI;
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    state.steering = false;
                }
            });
        }

        /** Solo responde dentro del círculo (las esquinas del rectángulo son para mirar alrededor). */
        @Override
        public Actor hit(float x, float y, boolean touchable) {
            float dx = x - radius, dy = y - radius;
            return dx * dx + dy * dy <= radius * radius ? super.hit(x, y, touchable) : null;
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            if (!state.steering) {
                angle = MathUtils.lerp(angle, 0f, Math.min(1f, delta * 6f));
                state.steer = angle / MathUtils.PI;
            }
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float cx = getX() + radius;
            float cy = getY() + radius;
            TextureRegion circle = skin.getRegion("circle");
            if (overlay) {
                // El volante del coche ya se ve y gira: un aro tenue marca dónde tocar
                batch.setColor(1f, 1f, 1f, state.steering ? 0.4f : 0.2f);
                batch.draw(skin.getRegion("ring"), cx - radius, cy - radius, radius * 2f, radius * 2f);
                batch.setColor(Color.WHITE);
                return;
            }
            TextureRegion icon = ((TextureRegionDrawable) skin.getDrawable("icon-wheel")).getRegion();
            batch.setColor(0.03f, 0.05f, 0.08f, 0.35f);
            batch.draw(circle, cx - radius, cy - radius, radius * 2f, radius * 2f);
            float deg = (state.steering || Math.abs(angle) > Math.abs(shownSteer * MathUtils.PI) ? angle
                : shownSteer * MathUtils.PI) * MathUtils.radiansToDegrees;
            float r = radius - 8f;
            batch.setColor(0.88f, 0.91f, 0.96f, state.steering ? 0.9f : 0.6f);
            batch.draw(icon, cx - r, cy - r, r, r, r * 2f, r * 2f, 1f, 1f, deg);
            batch.setColor(Color.WHITE);
        }
    }

    // --- Pedales ----------------------------------------------------------------------------

    /** Pedal vertical: la altura del dedo dentro del pedal es el recorrido (0 abajo... 1 arriba). */
    private class Pedal extends Actor {
        private final String label;
        private final Color color;
        private float value;
        private final java.util.function.Consumer<Float> sink;
        /** Recorrido real del pedal en el coche (puede venir del teclado o del mando). */
        float shown;
        private float biteFrom = -1f, biteTo = -1f;
        /** Se queda donde se suelta el dedo (hay que bajarlo al fondo para soltarlo del todo). */
        boolean sticky;
        private boolean inBite;

        Pedal(String label, Color color, float w, float h, java.util.function.Consumer<Float> sink) {
            this.label = label;
            this.color = color;
            this.sink = sink;
            setSize(w, h);
            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    set(y);
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    set(y);
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    if (sticky) {
                        return;
                    }
                    value = 0f;
                    inBite = false;
                    sink.accept(0f);
                }
            });
        }

        /** Franja del punto de fricción (fracciones de altura). */
        void bite(float from, float to) {
            biteFrom = from;
            biteTo = to;
        }

        private void set(float y) {
            value = MathUtils.clamp(y / getHeight(), 0f, 1f);
            sink.accept(value);
            // Un toque de vibración al entrar en la franja de fricción, como notarla con el pie
            boolean now = biteFrom >= 0f && value >= biteFrom && value <= biteTo;
            if (now && !inBite) {
                haptic.accept(com.pelayo.autoescuela.app.Haptics.BITE);
            }
            inBite = now;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Drawable box = skin.getDrawable("hud-box");
            batch.setColor(Color.WHITE);
            box.draw(batch, getX(), getY(), getWidth(), getHeight());
            if (biteFrom >= 0f) {
                batch.setColor(AMBER.r, AMBER.g, AMBER.b, 0.3f);
                batch.draw(skin.getRegion("white"), getX() + 6f, getY() + getHeight() * biteFrom,
                    getWidth() - 12f, getHeight() * (biteTo - biteFrom));
            }
            float v = Math.max(value, shown);
            if (v > 0.01f) {
                batch.setColor(color.r, color.g, color.b, 0.9f);
                skin.getDrawable("round-white").draw(batch, getX(), getY(), getWidth(), Math.max(getHeight() * v, 16f));
            }
            if (sticky && value > 0.01f) {
                // Embrague fijado: una raya blanca marca dónde se ha quedado
                batch.setColor(1f, 1f, 1f, 0.95f);
                batch.draw(skin.getRegion("white"), getX() + 4f, getY() + getHeight() * value - 2f, getWidth() - 8f, 4f);
            }
            batch.setColor(Color.WHITE);
            BitmapFont font = skin.getFont("font-small");
            layout.setText(font, label, Color.WHITE, 0f, Align.left, false);
            font.draw(batch, layout, getX() + getWidth() / 2f - layout.width / 2f, getY() + getHeight() + 22f);
        }
    }

    // --- Botones redondos ------------------------------------------------------------------

    /**
     * Botón redondo con un icono o una letra y, opcionalmente, un rótulo
     * debajo. {@code active} lo enciende (freno de mano puesto, motor en
     * marcha, intermitente parpadeando).
     */
    private class RoundButton extends Actor {
        private final String icon;
        private final String text;
        private final String caption;
        private final float rotation;
        boolean active;
        private boolean pressed;
        private float blink;

        RoundButton(String icon, String text, String caption, float rotation, Runnable action) {
            this.icon = icon;
            this.text = text;
            this.caption = caption;
            this.rotation = rotation;
            setSize(BUTTON, BUTTON);
            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    pressed = true;
                    return true;
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    if (pressed && x >= -10f && y >= -10f && x <= getWidth() + 10f && y <= getHeight() + 10f) {
                        action.run();
                    }
                    pressed = false;
                }
            });
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            blink += delta;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float x = getX(), y = getY(), s = getWidth();
            TextureRegion circle = skin.getRegion("circle");
            boolean indicator = "arrow".equals(icon);
            boolean lit = active && (!indicator || ((int) (blink * 2.5f)) % 2 == 0);
            batch.setColor(0f, 0f, 0f, 0.35f);
            batch.draw(circle, x - 1f, y - 3f, s + 2f, s + 2f);
            Color bg = pressed ? new Color(0.20f, 0.45f, 0.85f, 0.95f)
                : lit ? (indicator ? new Color(0.12f, 0.45f, 0.2f, 0.95f) : new Color(0.55f, 0.38f, 0.08f, 0.95f))
                : new Color(0.06f, 0.08f, 0.11f, 0.78f);
            batch.setColor(bg);
            batch.draw(circle, x, y, s, s);
            Color fg = lit ? (indicator ? GREEN : AMBER) : new Color(0.9f, 0.93f, 0.97f, 1f);
            if (icon != null) {
                TextureRegion region = indicator ? skin.getRegion("arrow")
                    : ((TextureRegionDrawable) skin.getDrawable(icon)).getRegion();
                float is = s * 0.52f;
                batch.setColor(fg);
                batch.draw(region, x + (s - is) / 2f, y + (s - is) / 2f, is / 2f, is / 2f, is, is, 1f, 1f, rotation);
            } else if (text != null) {
                BitmapFont bold = skin.getFont("font-title");
                layout.setText(bold, text, fg, 0f, Align.left, false);
                bold.draw(batch, layout, x + s / 2f - layout.width / 2f, y + s / 2f + layout.height / 2f);
            }
            if (caption != null) {
                BitmapFont font = skin.getFont("font-small");
                layout.setText(font, caption, new Color(1f, 1f, 1f, 0.85f), 0f, Align.left, false);
                font.draw(batch, layout, x + s / 2f - layout.width / 2f, y - 6f);
            }
            batch.setColor(Color.WHITE);
        }
    }

    // --- Palanca de cambios ----------------------------------------------------------------

    /**
     * Palanca de cambios con la rejilla en H del Clio: R arriba a la izquierda,
     * 1-2, 3-4, 5 arriba a la derecha y punto muerto en el centro. Se toca la
     * posición a la que se quiere llevar la palanca; el pomo muestra la marcha
     * metida y se desliza hasta ella.
     */
    private class GearShifter extends Actor {
        /** Posiciones relativas (0..1) de R, N, 1..5 en el cuadro. */
        private final float[][] pos = {
            {0.13f, 0.84f}, {0.5f, 0.5f}, {0.38f, 0.84f}, {0.38f, 0.16f}, {0.62f, 0.84f}, {0.62f, 0.16f},
            {0.87f, 0.84f}
        };
        private final String[] labels = {"R", "N", "1", "2", "3", "4", "5"};
        int gear;
        private float knobX = 0.5f, knobY = 0.5f;

        GearShifter(float w, float h) {
            setSize(w, h);
            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    int best = -1;
                    float bestD = Float.MAX_VALUE;
                    for (int i = 0; i < pos.length; i++) {
                        float dx = x - pos[i][0] * getWidth();
                        float dy = y - pos[i][1] * getHeight();
                        float d = dx * dx + dy * dy;
                        if (d < bestD) {
                            bestD = d;
                            best = i;
                        }
                    }
                    if (best >= 0 && bestD < 55f * 55f) {
                        state.gearSelect = best - 1; // R = -1, N = 0, 1..5
                    }
                    return true;
                }
            });
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            float[] target = pos[MathUtils.clamp(gear + 1, 0, pos.length - 1)];
            float k = Math.min(1f, delta * 14f);
            knobX = MathUtils.lerp(knobX, target[0], k);
            knobY = MathUtils.lerp(knobY, target[1], k);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float x = getX(), y = getY(), w = getWidth(), h = getHeight();
            TextureRegion white = skin.getRegion("white");
            TextureRegion circle = skin.getRegion("circle");
            Drawable box = skin.getDrawable("hud-box");
            batch.setColor(Color.WHITE);
            box.draw(batch, x, y, w, h);
            // Raíles de la rejilla
            batch.setColor(1f, 1f, 1f, 0.28f);
            float t = 6f;
            batch.draw(white, x + pos[0][0] * w, y + h * 0.5f - t / 2f, (pos[6][0] - pos[0][0]) * w, t);
            int[] columns = {0, 2, 4, 6};
            for (int i : columns) {
                float bottom = (i == 2 || i == 4) ? pos[3][1] : 0.5f;
                batch.draw(white, x + pos[i][0] * w - t / 2f, y + bottom * h, t, (pos[i][1] - bottom) * h);
            }
            // Posiciones con su número
            BitmapFont font = skin.getFont("font-small");
            for (int i = 0; i < pos.length; i++) {
                float px = x + pos[i][0] * w, py = y + pos[i][1] * h;
                batch.setColor(0.15f, 0.18f, 0.22f, 1f);
                batch.draw(circle, px - 15f, py - 15f, 30f, 30f);
                layout.setText(font, labels[i], new Color(1f, 1f, 1f, 0.7f), 0f, Align.left, false);
                font.draw(batch, layout, px - layout.width / 2f, py + layout.height / 2f);
            }
            // Pomo de la palanca en la marcha metida
            float kx = x + knobX * w, ky = y + knobY * h;
            batch.setColor(0f, 0f, 0f, 0.4f);
            batch.draw(circle, kx - 21f, ky - 23f, 42f, 42f);
            batch.setColor(gear == -1 ? new Color(1f, 0.55f, 0.35f, 1f) : AMBER);
            batch.draw(circle, kx - 20f, ky - 20f, 40f, 40f);
            BitmapFont bold = skin.getFont("font-bold");
            String g = gear == -1 ? "R" : gear == 0 ? "N" : String.valueOf(gear);
            layout.setText(bold, g, new Color(0.1f, 0.1f, 0.1f, 1f), 0f, Align.left, false);
            bold.draw(batch, layout, kx - layout.width / 2f, ky + layout.height / 2f);
            batch.setColor(Color.WHITE);
        }
    }
}
