package com.pelayo.autoescuela.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.pelayo.autoescuela.app.Assets;

/**
 * Construye la Skin de scene2d en código, sin ficheros de skin externos: las
 * esquinas redondeadas, los círculos del HUD y los iconos se rasterizan con
 * antialiasing ({@link Shapes}, {@link Icons}). Paleta: fondo azul noche,
 * acento azul "DGT", verde/rojo para apto/no apto y ámbar para avisos.
 */
public final class SkinFactory {

    public static final Color BG = new Color(0x0e131bff);
    public static final Color BG_TOP = new Color(0x18263aff);
    public static final Color PANEL = new Color(0x1a2330f5);
    public static final Color PANEL_BORDER = new Color(0x2c3a4cff);
    public static final Color ACCENT = new Color(0x1f7ae0ff);
    public static final Color ACCENT_LIGHT = new Color(0x4a9cf5ff);
    public static final Color ACCENT_DARK = new Color(0x155aa8ff);
    public static final Color OK = new Color(0x35b85cff);
    public static final Color FAIL = new Color(0xe4493fff);
    public static final Color WARN = new Color(0xf2b13bff);
    public static final Color TEXT_DIM = new Color(0xa9b5c4ff);
    public static final Color TEXT_FAINT = new Color(0x6f7d8fff);

    /** Radio de las esquinas de botones y paneles (px). */
    private static final int RADIUS = 10;

    private SkinFactory() {
    }

    public static Skin create(Assets assets) {
        Skin skin = new Skin();

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture white = new Texture(pixmap);
        pixmap.dispose();
        skin.add("white", white);
        skin.add("white", new TextureRegion(white));
        skin.add("white", flat(white, Color.WHITE), Drawable.class);

        // Formas blancas con antialiasing: se tiñen al dibujar
        Texture round = texture(skin, "round", Shapes.white(RADIUS * 2 + 2, RADIUS * 2 + 2,
            Shapes.roundRect(0f, 0f, RADIUS * 2 + 2, RADIUS * 2 + 2, RADIUS)));
        Texture pill = texture(skin, "pill", Shapes.white(34, 34, Shapes.roundRect(0f, 0f, 34f, 34f, 17f)));
        Texture bar = texture(skin, "bar", Shapes.white(12, 12, Shapes.roundRect(0f, 0f, 12f, 12f, 6f)));
        region(skin, "circle", Shapes.white(256, 256, Shapes.circle(128f, 128f, 126f)));
        region(skin, "ring", Shapes.white(256, 256, Shapes.ring(128f, 128f, 120f, 6f)));
        region(skin, "ring-bold", Shapes.white(128, 128, Shapes.ring(64f, 64f, 56f, 12f)));
        region(skin, "glow", glow(128));
        region(skin, "arrow", Shapes.white(64, 64, Shapes.polygon(4f, 32f, 34f, 4f, 34f, 20f, 60f, 20f, 60f, 44f,
            34f, 44f, 34f, 60f)));
        region(skin, "needle", Shapes.white(128, 16, Shapes.polygon(0f, 6f, 120f, 7f, 127f, 8f, 120f, 9f, 0f, 10f)));
        for (String name : Icons.NAMES) {
            Pixmap icon = Icons.create(name);
            Texture t = new Texture(icon, true);
            t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
            icon.dispose();
            skin.add("icon-" + name + "-tex", t);
            skin.add("icon-" + name, new TextureRegionDrawable(new TextureRegion(t)), Drawable.class);
        }
        skin.add("bg", gradient(skin), Drawable.class);

        skin.add("font-huge", assets.fontHuge);
        skin.add("font-title", assets.fontTitle);
        skin.add("font-bold", assets.fontBold);
        skin.add("font-body", assets.fontBody);
        skin.add("font-small", assets.fontSmall);

        skin.add("default", new Label.LabelStyle(assets.fontBody, Color.WHITE));
        skin.add("huge", new Label.LabelStyle(assets.fontHuge, Color.WHITE));
        skin.add("title", new Label.LabelStyle(assets.fontTitle, Color.WHITE));
        skin.add("bold", new Label.LabelStyle(assets.fontBold, Color.WHITE));
        skin.add("small", new Label.LabelStyle(assets.fontSmall, TEXT_DIM));
        skin.add("small-white", new Label.LabelStyle(assets.fontSmall, Color.WHITE));
        skin.add("ok", new Label.LabelStyle(assets.fontBody, OK));
        skin.add("fail", new Label.LabelStyle(assets.fontBody, FAIL));
        skin.add("warn", new Label.LabelStyle(assets.fontSmall, WARN));
        skin.add("accent", new Label.LabelStyle(assets.fontSmall, ACCENT_LIGHT));

        TextButton.TextButtonStyle button = new TextButton.TextButtonStyle();
        button.font = assets.fontBold;
        button.fontColor = Color.WHITE;
        button.downFontColor = new Color(0.85f, 0.9f, 1f, 1f);
        button.disabledFontColor = new Color(1f, 1f, 1f, 0.35f);
        button.up = rounded(round, ACCENT);
        button.over = rounded(round, ACCENT_LIGHT);
        button.down = rounded(round, ACCENT_DARK);
        button.disabled = rounded(round, new Color(0.25f, 0.29f, 0.35f, 0.8f));
        skin.add("default", button);

        TextButton.TextButtonStyle secondary = new TextButton.TextButtonStyle(button);
        secondary.font = assets.fontBody;
        secondary.up = rounded(round, new Color(0x263243ff));
        secondary.over = rounded(round, new Color(0x314057ff));
        secondary.down = rounded(round, new Color(0x1c2532ff));
        skin.add("secondary", secondary);

        TextButton.TextButtonStyle ghost = new TextButton.TextButtonStyle(secondary);
        ghost.up = null;
        ghost.over = rounded(round, new Color(1f, 1f, 1f, 0.08f));
        ghost.down = rounded(round, new Color(1f, 1f, 1f, 0.04f));
        ghost.fontColor = TEXT_DIM;
        ghost.overFontColor = Color.WHITE;
        skin.add("ghost", ghost);

        TextButton.TextButtonStyle small = new TextButton.TextButtonStyle(button);
        small.font = assets.fontSmall;
        small.checked = rounded(round, WARN); // marcha engranada, freno de mano
        small.checkedFontColor = new Color(0x1a1a1aff);
        skin.add("small", small);

        TextButton.TextButtonStyle smallSecondary = new TextButton.TextButtonStyle(secondary);
        smallSecondary.font = assets.fontSmall;
        skin.add("small-secondary", smallSecondary);

        // Pestañas / filtros: neutras, con el acento cuando están marcadas
        TextButton.TextButtonStyle tab = new TextButton.TextButtonStyle(secondary);
        tab.font = assets.fontSmall;
        tab.checked = rounded(round, ACCENT);
        skin.add("tab", tab);

        // Opciones de respuesta de los tests: neutra, correcta y errónea
        TextButton.TextButtonStyle answer = new TextButton.TextButtonStyle(secondary);
        answer.font = assets.fontBody;
        answer.over = rounded(round, new Color(0x34465fff));
        answer.checked = rounded(round, ACCENT_DARK);
        skin.add("answer", answer);
        TextButton.TextButtonStyle answerOk = new TextButton.TextButtonStyle(secondary);
        answerOk.up = answerOk.over = answerOk.down = answerOk.disabled = rounded(round, new Color(0x2a8a48ff));
        answerOk.disabledFontColor = Color.WHITE;
        skin.add("answer-ok", answerOk);
        TextButton.TextButtonStyle answerFail = new TextButton.TextButtonStyle(secondary);
        answerFail.up = answerFail.over = answerFail.down = answerFail.disabled = rounded(round, new Color(0xb3392fff));
        answerFail.disabledFontColor = Color.WHITE;
        skin.add("answer-fail", answerFail);

        skin.add("panel", framed(PANEL, PANEL_BORDER, skin, "panel"), Drawable.class);
        skin.add("panel-dark", framed(new Color(0x0b0f15e0), new Color(0x2a3646ff), skin, "panel-dark"),
            Drawable.class);
        skin.add("panel-accent", framed(new Color(0x16263bf5), ACCENT, skin, "panel-accent"), Drawable.class);
        skin.add("panel-ok", framed(new Color(0x15301ff5), OK, skin, "panel-ok"), Drawable.class);
        skin.add("hud-box", rounded(round, new Color(0.02f, 0.03f, 0.05f, 0.62f)), Drawable.class);
        skin.add("round-white", rounded(round, Color.WHITE, 0f, 0f), Drawable.class);
        skin.add("chip", rounded(pill, new Color(0x263243ff)), Drawable.class);
        skin.add("chip-accent", rounded(pill, ACCENT), Drawable.class);
        skin.add("chip-ok", rounded(pill, new Color(0x2a8a48ff)), Drawable.class);
        skin.add("chip-warn", rounded(pill, new Color(0x8a6417ff)), Drawable.class);
        skin.add("chip-fail", rounded(pill, new Color(0xa3302aff)), Drawable.class);
        skin.add("chip-orange", rounded(pill, new Color(0xa85a14ff)), Drawable.class);
        skin.add("separator", flat(white, PANEL_BORDER, 0f, 2f), Drawable.class);

        // Tarjetas pulsables del menú principal
        Button.ButtonStyle card = new Button.ButtonStyle();
        card.up = framed(PANEL, PANEL_BORDER, skin, "card-up");
        card.over = framed(new Color(0x1f2b3cf5), ACCENT, skin, "card-over");
        card.down = framed(new Color(0x16202cf5), ACCENT_DARK, skin, "card-down");
        card.disabled = framed(new Color(0x151b24f0), PANEL_BORDER, skin, "card-disabled");
        skin.add("card", card);

        Window.WindowStyle window = new Window.WindowStyle();
        window.titleFont = assets.fontBold;
        window.titleFontColor = Color.WHITE;
        window.background = skin.getDrawable("panel");
        skin.add("default", window);

        Slider.SliderStyle slider = new Slider.SliderStyle();
        slider.background = rounded(bar, new Color(0x263243ff), 0f, 12f);
        slider.knobBefore = rounded(bar, ACCENT, 0f, 12f);
        TextureRegionDrawable knob = new TextureRegionDrawable(skin.getRegion("circle"));
        slider.knob = knob.tint(Color.WHITE);
        slider.knob.setMinWidth(26f);
        slider.knob.setMinHeight(26f);
        slider.knobOver = knob.tint(new Color(0.85f, 0.92f, 1f, 1f));
        slider.knobOver.setMinWidth(26f);
        slider.knobOver.setMinHeight(26f);
        skin.add("default-horizontal", slider);
        skin.add("default", slider);

        ProgressBar.ProgressBarStyle progress = new ProgressBar.ProgressBarStyle();
        progress.background = rounded(bar, new Color(0x263243ff), 0f, 12f);
        progress.knobBefore = rounded(bar, ACCENT, 0f, 12f);
        skin.add("default-horizontal", progress);
        ProgressBar.ProgressBarStyle progressOk = new ProgressBar.ProgressBarStyle(progress);
        progressOk.knobBefore = rounded(bar, OK, 0f, 12f);
        skin.add("ok-horizontal", progressOk);

        // Casilla de verificación: cuadrado redondeado hueco / con marca
        CheckBox.CheckBoxStyle check = new CheckBox.CheckBoxStyle();
        check.font = assets.fontBody;
        check.fontColor = Color.WHITE;
        check.checkboxOff = rounded(round, new Color(0x263243ff), 28f, 28f);
        check.checkboxOn = rounded(round, ACCENT, 28f, 28f);
        skin.add("default", check);

        ScrollPane.ScrollPaneStyle scroll = new ScrollPane.ScrollPaneStyle();
        scroll.vScroll = rounded(bar, new Color(1f, 1f, 1f, 0.05f), 10f, 12f);
        scroll.vScrollKnob = rounded(bar, new Color(0x3d4f69ff), 10f, 30f);
        skin.add("default", scroll);

        return skin;
    }

    // --- Ayudantes -------------------------------------------------------------

    private static Texture texture(Skin skin, String name, Pixmap pixmap) {
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        skin.add(name + "-tex", texture); // la skin lo libera al cerrarse
        return texture;
    }

    /** Región con mipmaps: se dibuja a muchos tamaños (relojes, testigos) sin dientes de sierra. */
    private static void region(Skin skin, String name, Pixmap pixmap) {
        Texture texture = new Texture(pixmap, true);
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        skin.add(name + "-tex", texture);
        skin.add(name, new TextureRegion(texture));
    }

    /** Mancha radial blanca que se desvanece hacia el borde (halos del HUD). */
    private static Pixmap glow(int size) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        float c = size / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float d = (float) Math.hypot(x + 0.5f - c, y + 0.5f - c) / c;
                float a = d >= 1f ? 0f : (1f - d) * (1f - d);
                p.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, a));
            }
        }
        return p;
    }

    /** Degradado vertical de fondo para las pantallas de menú. */
    private static Drawable gradient(Skin skin) {
        int h = 256;
        Pixmap p = new Pixmap(1, h, Pixmap.Format.RGBA8888);
        Color c = new Color();
        for (int y = 0; y < h; y++) {
            float t = y / (h - 1f);
            c.set(BG_TOP).lerp(BG, (float) Math.pow(t, 0.7f));
            p.drawPixel(0, y, Color.rgba8888(c));
        }
        return new TextureRegionDrawable(new TextureRegion(texture(skin, "bg", p)));
    }

    /**
     * Panel redondeado con borde de 2 px: textura propia (borde y relleno
     * mezclados con antialiasing) usada como NinePatch.
     */
    private static Drawable framed(Color fill, Color border, Skin skin, String name) {
        int side = RADIUS * 2 + 4;
        Pixmap pixmap = new Pixmap(side, side, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        Shapes.fill(pixmap, Shapes.roundRect(0f, 0f, side, side, RADIUS), border);
        // El relleno sustituye al borde por dentro (no se mezcla con él)
        Pixmap inner = Shapes.white(side, side, Shapes.roundRect(2f, 2f, side - 4f, side - 4f, RADIUS - 2f));
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                float cov = (inner.getPixel(x, y) & 0xff) / 255f;
                if (cov > 0f) {
                    Color b = new Color(pixmap.getPixel(x, y));
                    Color f = new Color(fill);
                    b.lerp(f, cov);
                    pixmap.drawPixel(x, y, Color.rgba8888(b));
                }
            }
        }
        inner.dispose();
        Texture texture = texture(skin, name, pixmap);
        int split = RADIUS + 1;
        NinePatch patch = new NinePatch(new TextureRegion(texture), split, split, split, split);
        NinePatchDrawable drawable = new NinePatchDrawable(patch);
        drawable.setPadding(8f, 10f, 8f, 10f);
        return drawable;
    }

    /** Rectángulo redondeado de color a partir de la textura blanca "round" o "pill". */
    private static NinePatchDrawable rounded(Texture shape, Color color) {
        NinePatchDrawable drawable = rounded(shape, color, 0f, 0f);
        drawable.setPadding(4f, 12f, 4f, 12f);
        return drawable;
    }

    /** Sin relleno interior: barras, casillas y fondos de tamaño fijo. */
    private static NinePatchDrawable rounded(Texture shape, Color color, float minWidth, float minHeight) {
        int split = shape.getWidth() / 2 - 1;
        NinePatch patch = new NinePatch(new TextureRegion(shape), split, split, split, split);
        patch.setColor(color);
        NinePatchDrawable drawable = new NinePatchDrawable(patch) {
            // Una barra de progreso a 0 pide anchura 0: sin esto el NinePatch pintaría sus esquinas igualmente
            @Override
            public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float x, float y, float width, float height) {
                if (width >= 1f && height >= 1f) {
                    super.draw(batch, x, y, width, height);
                }
            }
        };
        drawable.setPadding(0f, 0f, 0f, 0f);
        drawable.setMinWidth(minWidth);
        drawable.setMinHeight(minHeight);
        return drawable;
    }

    private static NinePatchDrawable flat(Texture white, Color color) {
        return flat(white, color, 0f, 0f);
    }

    private static NinePatchDrawable flat(Texture white, Color color, float minWidth, float minHeight) {
        NinePatch patch = new NinePatch(new TextureRegion(white), 0, 0, 0, 0);
        patch.setColor(color);
        NinePatchDrawable drawable = new NinePatchDrawable(patch);
        drawable.setMinWidth(minWidth);
        drawable.setMinHeight(minHeight);
        return drawable;
    }
}
