package com.pelayo.autoescuela.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ObjectSet;
import com.pelayo.autoescuela.app.I18n;
import com.pelayo.autoescuela.data.VehicleSpec;
import com.pelayo.autoescuela.sim.vehicle.VehicleEvent;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import com.pelayo.autoescuela.ui.SkinFactory;

/**
 * HUD de conducción: cuentarrevoluciones de aguja con la velocidad en el
 * centro, marcha con su posición en el patrón H, pedales (con la zona del
 * punto de fricción en el embrague), testigos (freno de mano, intermitentes,
 * motor) y avisos de eventos. Todo con las formas antialiasing de la skin.
 *
 * El tutorial puede resaltar mandos con {@link #setHighlights}: "clutch",
 * "brake", "throttle", "tacho", "speed", "gear", "handbrake", "indicators".
 */
public class HudOverlay extends Actor {

    private static final Color CLUTCH_COLOR = new Color(0x4f8ff7ff);
    private static final Color BRAKE_COLOR = new Color(0xef5146ff);
    private static final Color THROTTLE_COLOR = new Color(0x3fcf6bff);
    private static final Color HIGHLIGHT = new Color(0xf2b13bff);

    private final TextureRegion white;
    private final TextureRegion circle;
    private final TextureRegion ring;
    private final TextureRegion ringBold;
    private final TextureRegion glow;
    private final TextureRegion arrow;
    private final TextureRegion needle;
    private final TextureRegion wheelIcon;
    private final Drawable box;
    private final Drawable fill;
    private final BitmapFont fontHuge;
    private final BitmapFont fontTitle;
    private final BitmapFont fontBold;
    private final BitmapFont fontBody;
    private final BitmapFont fontSmall;
    private final I18n i18n;
    private final GlyphLayout layout = new GlyphLayout();
    private final float maxRpm;
    private final float redlineRpm;
    private final float biteLow;
    private final float biteHigh;
    private final Color tmp = new Color();

    private VehicleState state;
    private String message = "";
    private float messageTimer;
    private float blink;
    private String instruction = "";
    private String status = "";
    private boolean touchLayout;
    /** Relojes, marcha y testigos en pantalla (se ocultan si van en el salpicadero). */
    private boolean gaugesVisible = true;
    /** Volante indicador: solo hace falta cuando no se ve el volante del coche (cámaras exteriores). */
    private boolean steeringVisible = true;
    private final ObjectSet<String> highlights = new ObjectSet<>();
    private String[] pedalKeys = {"", "", ""};
    private String clutchKey = "";
    private String ignitionKey = "";

    public HudOverlay(Skin skin, I18n i18n, VehicleSpec spec) {
        this.white = skin.getRegion("white");
        this.circle = skin.getRegion("circle");
        this.ring = skin.getRegion("ring");
        this.ringBold = skin.getRegion("ring-bold");
        this.glow = skin.getRegion("glow");
        this.arrow = skin.getRegion("arrow");
        this.needle = skin.getRegion("needle");
        this.wheelIcon = ((com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable) skin.getDrawable("icon-wheel"))
            .getRegion();
        this.box = skin.getDrawable("hud-box");
        this.fill = skin.getDrawable("round-white");
        this.fontHuge = skin.getFont("font-huge");
        this.fontTitle = skin.getFont("font-title");
        this.fontBold = skin.getFont("font-bold");
        this.fontBody = skin.getFont("font-body");
        this.fontSmall = skin.getFont("font-small");
        this.i18n = i18n;
        this.redlineRpm = spec.engine.redlineRpm;
        this.maxRpm = 5000f;
        this.biteLow = spec.clutch.bitePointLow;
        this.biteHigh = spec.clutch.bitePointHigh;
    }

    /** Disposición para pantalla táctil (sin barras de pedales, cuadro centrado). */
    public void setSteeringVisible(boolean visible) {
        this.steeringVisible = visible;
    }

    public void setGaugesVisible(boolean visible) {
        this.gaugesVisible = visible;
    }

    public void setTouchLayout(boolean touchLayout) {
        this.touchLayout = touchLayout;
    }

    private float gaugeX, gaugeY, gaugeW;
    private float topRightLimit = Float.MAX_VALUE;

    /** En táctil, dónde va la tira de velocidad y revoluciones: encima de los pedales, con su ancho. */
    public void setTouchGaugeAnchor(float x, float y, float width) {
        gaugeX = x;
        gaugeY = y;
        gaugeW = width;
    }

    /** Borde izquierdo de lo que hay arriba a la derecha (retrovisor, botones): la instrucción no lo pisa. */
    public void setTopRightLimit(float x) {
        topRightLimit = x;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction == null ? "" : instruction;
    }

    public void setStatus(String status) {
        this.status = status == null ? "" : status;
    }

    public void setState(VehicleState state) {
        this.state = state;
    }

    /** Mandos que el tutorial quiere señalar (se dibujan con un halo ámbar). */
    public void setHighlights(String[] names) {
        highlights.clear();
        if (names != null) {
            highlights.addAll(names);
        }
    }

    /** Nombres de las teclas de embrague, freno y gas para rotular los pedales. */
    public void setPedalKeys(String clutch, String brake, String throttle) {
        pedalKeys = new String[] {clutch, brake, throttle};
    }

    /** Teclas que se citan en los avisos (embrague y contacto). */
    public void setHintKeys(String clutch, String ignition) {
        clutchKey = clutch;
        ignitionKey = ignition;
    }

    /** Muestra los avisos de los eventos del último paso. */
    public void consumeEvents(VehicleState state) {
        for (VehicleEvent e : state.events) {
            switch (e) {
                case STALL: show(i18n.t("hud.stall", clutchKey, ignitionKey)); break;
                case GRIND: show(i18n.t("hud.grind", clutchKey)); break;
                case REVERSE_REFUSED: show(i18n.t("hud.reverseRefused")); break;
                case START_IN_GEAR: show(i18n.t("hud.startInGear")); break;
                case ENGINE_START: show(i18n.t("hud.engineStart")); break;
                case ENGINE_OFF: show(i18n.t("hud.engineOff")); break;
                default: break;
            }
        }
    }

    public void show(String text) {
        message = text;
        messageTimer = text.isEmpty() ? 0f : 3f;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (messageTimer > 0f) {
            messageTimer -= delta;
        }
        blink += delta;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (state == null) {
            return;
        }
        float w = getStage().getViewport().getWorldWidth();
        float h = getStage().getViewport().getWorldHeight();

        float cx = w - 150f;
        float cy = 140f;
        if (gaugesVisible && touchLayout) {
            // Abajo están los mandos de las dos manos: velocidad y revoluciones en una tira sobre los pedales;
            // la marcha y las luces ya se ven en la palanca y en los botones
            drawCompactGauges(batch, gaugeX, gaugeY, gaugeW);
        } else if (gaugesVisible) {
            drawTachometer(batch, cx, cy, 112f);
            drawGear(batch, cx - 190f, cy - 30f);
            drawLights(batch, cx, cy + 140f);
        }
        if (!touchLayout) {
            drawPedals(batch, 36f, 50f);
            if (steeringVisible) {
                drawSteering(batch, 36f + 214f + 58f, 104f);
            }
        }
        if ((!gaugesVisible || touchLayout) && !state.engineRunning && !state.engineStarting) {
            // Relojes solo en el salpicadero: el aviso de motor apagado va en una franja abajo
            // En táctil la parte de abajo es de los mandos y arriba están la lección y el minimapa:
            // el aviso va en el parabrisas, más estrecho y en dos líneas
            float maxW = touchLayout ? 660f : 0f;
            layout.setText(fontBold, i18n.t("hud.engineOffTitle") + (touchLayout ? "\n" : "  ·  ")
                    + i18n.t("hud.engineStopped", ignitionKey), new Color(1f, 0.6f, 0.45f, 1f), maxW, Align.center, touchLayout);
            float bw = layout.width + 48f;
            float bh = layout.height + 44f;
            float by = touchLayout ? h * 0.42f : 18f;
            batch.setColor(Color.WHITE);
            box.draw(batch, w / 2f - bw / 2f, by, bw, bh);
            fontBold.draw(batch, layout, w / 2f - (touchLayout ? maxW : layout.width) / 2f, by + bh - 22f);
        }
        drawInstruction(batch, w, h);
        drawMessage(batch, w / 2f, h * 0.70f);
        batch.setColor(Color.WHITE);
    }

    // --- Cuentarrevoluciones y velocidad ---------------------------------------

    private void drawTachometer(Batch batch, float cx, float cy, float r) {
        if (highlights.contains("tacho") || highlights.contains("speed")) {
            halo(batch, cx, cy, r + 40f);
        }
        batch.setColor(0.04f, 0.06f, 0.09f, 0.72f);
        draw(batch, circle, cx, cy, r + 10f);
        batch.setColor(1f, 1f, 1f, 0.12f);
        draw(batch, ring, cx, cy, r + 10f);

        float startDeg = 225f;
        float sweep = 270f;
        // Zona roja como arco de marcas gruesas
        for (float rpm = redlineRpm; rpm <= maxRpm; rpm += 50f) {
            float a = startDeg - sweep * rpm / maxRpm;
            batch.setColor(0.9f, 0.2f, 0.18f, 0.9f);
            radial(batch, cx, cy, r - 10f, r - 2f, a, 4f);
        }
        for (float rpm = 0f; rpm <= maxRpm + 1f; rpm += 250f) {
            float a = startDeg - sweep * rpm / maxRpm;
            boolean major = ((int) rpm) % 1000 == 0;
            boolean half = ((int) rpm) % 500 == 0;
            if (rpm >= redlineRpm) {
                batch.setColor(1f, 0.45f, 0.4f, 1f);
            } else {
                batch.setColor(0.92f, 0.94f, 0.97f, major ? 1f : 0.6f);
            }
            float len = major ? 16f : half ? 9f : 5f;
            radial(batch, cx, cy, r - len, r, a, major ? 3f : 1.6f);
            if (major) {
                float tx = cx + MathUtils.cosDeg(a) * (r - 30f);
                float ty = cy + MathUtils.sinDeg(a) * (r - 30f);
                layout.setText(fontSmall, String.valueOf((int) (rpm / 1000f)),
                    rpm >= redlineRpm ? new Color(1f, 0.5f, 0.45f, 1f) : Color.WHITE, 0f, Align.left, false);
                fontSmall.draw(batch, layout, tx - layout.width / 2f, ty + layout.height / 2f);
            }
        }
        layout.setText(fontSmall, "x1000 rpm", new Color(1f, 1f, 1f, 0.45f), 0f, Align.left, false);
        fontSmall.draw(batch, layout, cx - layout.width / 2f, cy + r * 0.42f);

        // Velocidad digital
        String speed = String.valueOf((int) (state.speedKmh + 0.5f));
        layout.setText(fontHuge, speed, Color.WHITE, 0f, Align.left, false);
        fontHuge.draw(batch, layout, cx - layout.width / 2f, cy - 16f);
        layout.setText(fontSmall, "km/h", SkinFactory.TEXT_DIM, 0f, Align.left, false);
        fontSmall.draw(batch, layout, cx - layout.width / 2f, cy - 78f);

        // Aguja con sombra y buje
        float rpm = MathUtils.clamp(state.rpm, 0f, maxRpm);
        float a = startDeg - sweep * rpm / maxRpm;
        float len = r - 8f;
        batch.setColor(0f, 0f, 0f, 0.4f);
        batch.draw(needle, cx + 2f, cy - 10f, 0f, 8f, len, 16f, 1f, 1f, a);
        batch.setColor(state.rpm >= redlineRpm ? new Color(1f, 0.25f, 0.2f, 1f) : new Color(1f, 0.42f, 0.2f, 1f));
        batch.draw(needle, cx, cy - 8f, 0f, 8f, len, 16f, 1f, 1f, a);
        batch.setColor(0.2f, 0.22f, 0.26f, 1f);
        draw(batch, circle, cx, cy, 11f);
        batch.setColor(1f, 0.42f, 0.2f, 1f);
        draw(batch, circle, cx, cy, 5f);

        // Estado del motor
        if (state.engineStarting) {
            layout.setText(fontSmall, i18n.t("hud.starting"), new Color(1f, 0.75f, 0.4f, 1f), 0f, Align.left, false);
            fontSmall.draw(batch, layout, cx - layout.width / 2f, cy - r - 18f);
        } else if (!state.engineRunning) {
            // Motor apagado: el reloj se oscurece y lo dice en grande, sin parpadeos
            batch.setColor(0.02f, 0.02f, 0.03f, 0.78f);
            draw(batch, circle, cx, cy, r + 10f);
            batch.setColor(Color.WHITE);
            layout.setText(fontBold, i18n.t("hud.engineOffTitle"), new Color(1f, 0.45f, 0.35f, 1f), 0f, Align.left, false);
            fontBold.draw(batch, layout, cx - layout.width / 2f, cy + 30f);
            layout.setText(fontSmall, i18n.t("hud.engineStopped", ignitionKey), new Color(1f, 0.8f, 0.6f, 1f),
                r * 1.7f, Align.center, true);
            fontSmall.draw(batch, layout, cx - r * 0.85f, cy - 8f);
        }
        fontSmall.setColor(Color.WHITE);
    }

    /** Velocidad y una barra de revoluciones con la zona roja, en poco sitio (disposición táctil). */
    private void drawCompactGauges(Batch batch, float x, float y, float w) {
        float h = 56f;
        if (highlights.contains("tacho") || highlights.contains("speed")) {
            halo(batch, x + w / 2f, y + h / 2f, w * 0.6f);
        }
        batch.setColor(Color.WHITE);
        box.draw(batch, x, y, w, h);
        String speed = String.valueOf((int) (state.speedKmh + 0.5f));
        layout.setText(fontBold, speed, Color.WHITE, 0f, Align.right, false);
        float numW = Math.max(layout.width, 40f);
        fontBold.draw(batch, layout, x + 12f + numW - layout.width, y + h / 2f + layout.height / 2f);
        layout.setText(fontSmall, "km/h", SkinFactory.TEXT_DIM, 0f, Align.left, false);
        float unitX = x + 12f + numW + 5f;
        fontSmall.draw(batch, layout, unitX, y + h / 2f + layout.height / 2f);

        float bx = unitX + layout.width + 14f;
        float bw = x + w - 12f - bx;
        float by = y + 14f;
        float bh = 10f;
        float red = bw * redlineRpm / maxRpm;
        batch.setColor(1f, 1f, 1f, 0.12f);
        batch.draw(white, bx, by, bw, bh);
        batch.setColor(0.9f, 0.2f, 0.18f, 0.5f);
        batch.draw(white, bx + red, by, bw - red, bh);
        float rpm = MathUtils.clamp(state.rpm, 0f, maxRpm);
        batch.setColor(state.rpm >= redlineRpm ? new Color(1f, 0.25f, 0.2f, 1f) : new Color(1f, 0.42f, 0.2f, 1f));
        batch.draw(white, bx, by, bw * rpm / maxRpm, bh);
        // Con el motor parado marca 0 en rojo (el aviso grande ya está en el parabrisas)
        String rpmText = String.format(java.util.Locale.ROOT, "%d rpm", Math.round(rpm / 50f) * 50);
        layout.setText(fontSmall, rpmText, state.engineRunning ? SkinFactory.TEXT_DIM : new Color(1f, 0.55f, 0.45f, 1f),
            0f, Align.left, false);
        fontSmall.draw(batch, layout, bx, y + h - 9f);
        batch.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    // --- Marcha y patrón H ---------------------------------------------------------

    /** Posiciones del patrón H del Clio: R arriba a la izquierda, 1-2, 3-4, 5. */
    private static final float[][] H_POS = {
        {-1.5f, 1f}, // R
        {0f, 0f},    // N (centro)
        {-0.5f, 1f}, {-0.5f, -1f}, {0.5f, 1f}, {0.5f, -1f}, {1.5f, 1f}
    };

    private void drawGear(Batch batch, float cx, float cy) {
        if (highlights.contains("gear")) {
            halo(batch, cx, cy + 20f, 110f);
        }
        float bw = 112f;
        float bh = 150f;
        batch.setColor(1f, 1f, 1f, 1f);
        box.draw(batch, cx - bw / 2f, cy - bh / 2f + 20f, bw, bh);

        String gear = state.gear == -1 ? "R" : state.gear == 0 ? "N" : String.valueOf(state.gear);
        layout.setText(fontHuge, gear, state.gear == -1 ? new Color(1f, 0.6f, 0.4f, 1f)
            : state.gear == 0 ? SkinFactory.TEXT_DIM : Color.WHITE, 0f, Align.left, false);
        fontHuge.draw(batch, layout, cx - layout.width / 2f, cy + 80f);

        // Sugerencia de cambio, como el indicador de los coches actuales
        String hint = shiftHint();
        if (!hint.isEmpty()) {
            boolean up = hint.charAt(0) == '+';
            Color c = up ? THROTTLE_COLOR : HIGHLIGHT;
            float ax = cx + bw / 2f - 22f, ay = cy + bh / 2f + 2f;
            batch.setColor(c);
            batch.draw(arrow, ax - 8f, ay - 8f, 8f, 8f, 16f, 16f, 1f, 1f, up ? -90f : 90f);
            layout.setText(fontBold, hint.substring(1), c, 0f, Align.left, false);
            fontBold.draw(batch, layout, ax - 6f - layout.width / 2f + 6f, ay - 10f);
            batch.setColor(Color.WHITE);
        }

        // Rejilla en H: raíles finos y un punto por posición
        float sx = 20f;
        float sy = 18f;
        float gy = cy - 12f;
        batch.setColor(1f, 1f, 1f, 0.25f);
        batch.draw(white, cx - 1.5f * sx, gy - 1f, 3f * sx, 2f);          // punto muerto
        batch.draw(white, cx - 0.5f * sx - 1f, gy - sy, 2f, sy * 2f);     // 1-2
        batch.draw(white, cx + 0.5f * sx - 1f, gy - sy, 2f, sy * 2f);     // 3-4
        batch.draw(white, cx - 1.5f * sx - 1f, gy, 2f, sy);               // R
        batch.draw(white, cx + 1.5f * sx - 1f, gy, 2f, sy);               // 5
        for (int g = -1; g <= 5; g++) {
            float[] p = H_POS[g + 1];
            boolean active = g == state.gear;
            if (g == 0 && !active) {
                continue;
            }
            float px = cx + p[0] * sx;
            float py = gy + p[1] * sy;
            batch.setColor(active ? (g == -1 ? new Color(1f, 0.6f, 0.4f, 1f) : HIGHLIGHT) : new Color(1f, 1f, 1f, 0.55f));
            draw(batch, circle, px, py, active ? 6f : 3.5f);
        }
    }

    // --- Volante -------------------------------------------------------------------

    /** Volante pequeño que gira con la dirección (con teclado no se ve en ningún otro sitio). */
    private void drawSteering(Batch batch, float cx, float cy) {
        float r = 40f;
        batch.setColor(Color.WHITE);
        box.draw(batch, cx - r - 10f, cy - r - 10f, (r + 10f) * 2f, (r + 10f) * 2f);
        // Marca de centro arriba y el volante girado (2,7 vueltas de tope a tope: ±486°; se muestra ±450°)
        batch.setColor(1f, 1f, 1f, 0.25f);
        batch.draw(white, cx - 1f, cy + r + 2f, 2f, 6f);
        float deg = state.steer * 450f;
        batch.setColor(Math.abs(state.steer) > 0.98f ? HIGHLIGHT : new Color(0.88f, 0.91f, 0.96f, 1f));
        batch.draw(wheelIcon, cx - r, cy - r, r, r, r * 2f, r * 2f, 1f, 1f, deg);
        batch.setColor(Color.WHITE);
    }

    /** "+N" si conviene subir a la marcha N, "-N" si conviene bajar, o "". */
    private String shiftHint() {
        if (!state.engineRunning || state.gear < 1 || state.clutch > 0.5f) {
            return "";
        }
        if (state.gear < 5 && state.rpm > 2200f && state.throttle > 0.05f) {
            return "+" + (state.gear + 1);
        }
        if (state.gear > 1 && state.rpm < 1050f && state.speedKmh > 3f) {
            return "-" + (state.gear - 1);
        }
        return "";
    }

    // --- Testigos ------------------------------------------------------------------

    private void drawLights(Batch batch, float cx, float cy) {
        boolean on = ((int) (blink * 2.5f)) % 2 == 0;
        if (highlights.contains("indicators")) {
            halo(batch, cx, cy, 70f);
        }
        batch.setColor(state.indicatorLeft && on ? THROTTLE_COLOR : new Color(1f, 1f, 1f, 0.12f));
        batch.draw(arrow, cx - 64f, cy - 14f, 28f, 28f);
        batch.setColor(state.indicatorRight && on ? THROTTLE_COLOR : new Color(1f, 1f, 1f, 0.12f));
        batch.draw(arrow, cx + 36f, cy - 14f, 14f, 14f, 28f, 28f, 1f, 1f, 180f);

        // Freno de mano: (P) rojo
        if (highlights.contains("handbrake")) {
            halo(batch, cx, cy, 44f);
        }
        if (state.handbrake) {
            batch.setColor(BRAKE_COLOR);
            draw(batch, ringBold, cx, cy, 17f);
            layout.setText(fontBold, "P", BRAKE_COLOR, 0f, Align.left, false);
            fontBold.draw(batch, layout, cx - layout.width / 2f, cy + layout.height / 2f);
            fontBold.setColor(Color.WHITE);
        } else {
            batch.setColor(1f, 1f, 1f, 0.12f);
            draw(batch, ringBold, cx, cy, 17f);
        }
    }

    // --- Pedales -------------------------------------------------------------------

    private void drawPedals(Batch batch, float x, float y) {
        float barW = 34f;
        float barH = 130f;
        float gap = 42f;
        String[] names = {i18n.t("hud.clutch"), i18n.t("hud.brake"), i18n.t("hud.throttle")};
        String[] ids = {"clutch", "brake", "throttle"};
        float[] values = {state.clutch, state.brake, state.throttle};
        Color[] colors = {CLUTCH_COLOR, BRAKE_COLOR, THROTTLE_COLOR};

        batch.setColor(Color.WHITE);
        box.draw(batch, x - 14f, y - 40f, 3 * barW + 2 * gap + 28f, barH + 62f);
        for (int i = 0; i < 3; i++) {
            float bx = x + i * (barW + gap);
            if (highlights.contains(ids[i])) {
                halo(batch, bx + barW / 2f, y + barH / 2f, 90f);
            }
            batch.setColor(1f, 1f, 1f, 0.10f);
            box.draw(batch, bx, y, barW, barH);
            if (i == 0) {
                // Zona del punto de fricción
                batch.setColor(HIGHLIGHT.r, HIGHLIGHT.g, HIGHLIGHT.b, 0.28f);
                batch.draw(white, bx, y + barH * biteLow, barW, barH * (biteHigh - biteLow));
            }
            float v = MathUtils.clamp(values[i], 0f, 1f);
            if (v > 0.01f) {
                tmp.set(colors[i]);
                batch.setColor(tmp);
                fill.draw(batch, bx, y, barW, Math.max(barH * v, 12f));
            }
            // Tecla y nombre
            layout.setText(fontBold, pedalKeys[i], Color.WHITE, barW + gap, Align.center, false);
            fontBold.draw(batch, layout, bx + barW / 2f - (barW + gap) / 2f, y - 6f);
            layout.setText(fontSmall, names[i], SkinFactory.TEXT_DIM, barW + gap, Align.center, false);
            fontSmall.draw(batch, layout, bx + barW / 2f - (barW + gap) / 2f, y + barH + 18f);
        }
        fontSmall.setColor(Color.WHITE);
    }

    // --- Textos ----------------------------------------------------------------------

    private void drawInstruction(Batch batch, float w, float h) {
        if (instruction.isEmpty()) {
            return;
        }
        float maxWidth = Math.min(700f, w * 0.5f); // deja sitio al minimapa y al botón de pausa
        float centerX = w / 2f;
        if (topRightLimit < w) {
            // El retrovisor ocupa arriba a la derecha: la caja acaba antes y se corre a la izquierda si hace falta
            maxWidth = Math.min(maxWidth, topRightLimit - 16f - 20f - 44f);
            centerX = Math.min(centerX, topRightLimit - 16f - (maxWidth + 44f) / 2f);
        }
        // Con ajuste de línea y centrado, el texto ocupa maxWidth: se mide aparte el ancho real
        layout.setText(fontBody, instruction, Color.WHITE, maxWidth, Align.center, true);
        float textW = layout.width;
        float textH = layout.height;
        float statusH = 0f;
        if (!status.isEmpty()) {
            statusH = 30f;
        }
        float top = h - 52f;
        float bw = textW + 44f;
        float bh = textH + 30f + statusH;
        batch.setColor(Color.WHITE);
        box.draw(batch, centerX - bw / 2f, top - bh, bw, bh);
        fontBody.draw(batch, layout, centerX - maxWidth / 2f, top - 15f);
        if (!status.isEmpty()) {
            layout.setText(fontSmall, status, HIGHLIGHT, 0f, Align.left, false);
            fontSmall.draw(batch, layout, centerX - layout.width / 2f, top - textH - 26f);
            fontSmall.setColor(Color.WHITE);
        }
    }

    private void drawMessage(Batch batch, float cx, float y) {
        if (messageTimer <= 0f || message.isEmpty()) {
            return;
        }
        float alpha = Math.min(1f, messageTimer / 0.4f);
        float maxWidth = 640f;
        layout.setText(fontBold, message, new Color(1f, 0.88f, 0.55f, alpha), maxWidth, Align.center, true);
        float bw = layout.width + 48f;
        float bh = layout.height + 30f;
        batch.setColor(1f, 1f, 1f, alpha);
        box.draw(batch, cx - bw / 2f, y - bh / 2f, bw, bh);
        batch.setColor(HIGHLIGHT.r, HIGHLIGHT.g, HIGHLIGHT.b, alpha);
        batch.draw(white, cx - bw / 2f + 6f, y - bh / 2f + 8f, 4f, bh - 16f);
        fontBold.setColor(1f, 0.88f, 0.55f, alpha);
        fontBold.draw(batch, layout, cx - maxWidth / 2f, y + layout.height / 2f);
        fontBold.setColor(Color.WHITE);
    }

    // --- Primitivas --------------------------------------------------------------------

    /** Halo ámbar pulsante para señalar un mando. */
    private void halo(Batch batch, float cx, float cy, float r) {
        float pulse = 0.45f + 0.35f * MathUtils.sin(blink * 5f);
        batch.setColor(HIGHLIGHT.r, HIGHLIGHT.g, HIGHLIGHT.b, pulse);
        draw(batch, glow, cx, cy, r);
    }

    private static void draw(Batch batch, TextureRegion region, float cx, float cy, float r) {
        batch.draw(region, cx - r, cy - r, r * 2f, r * 2f);
    }

    /** Segmento radial de r0 a r1 con ese ángulo (grados), grosor t. */
    private void radial(Batch batch, float cx, float cy, float r0, float r1, float deg, float t) {
        float x = cx + MathUtils.cosDeg(deg) * r0;
        float y = cy + MathUtils.sinDeg(deg) * r0;
        batch.draw(white, x, y - t / 2f, 0f, t / 2f, r1 - r0, t, 1f, 1f, deg);
    }
}
