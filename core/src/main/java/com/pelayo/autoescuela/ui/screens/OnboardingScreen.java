package com.pelayo.autoescuela.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.pelayo.autoescuela.AutoescuelaGame;
import com.pelayo.autoescuela.app.Settings;
import com.pelayo.autoescuela.ui.SkinFactory;

/**
 * Primera vez que se abre la app: cómo se va a conducir (táctil o teclado y
 * mando), de qué lado van los pedales y qué tamaño tienen los mandos, con un
 * boceto de la pantalla que cambia al elegir. Todo se puede cambiar luego en
 * Ajustes (también desde la pausa).
 */
public class OnboardingScreen extends ScreenAdapter {

    private final AutoescuelaGame game;
    private final Stage stage;
    private final Settings s;
    private final Preview preview;
    private final Table pedalRow;

    public OnboardingScreen(AutoescuelaGame game) {
        this.game = game;
        this.s = game.settings;
        stage = new Stage(new ExtendViewport(AutoescuelaGame.UI_WIDTH, AutoescuelaGame.UI_HEIGHT));

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(game.skin.getDrawable("bg"));
        root.pad(24f);
        stage.addActor(root);
        root.add(new Label(game.i18n.t("onboarding.title"), game.skin, "title")).padBottom(4f).row();
        Label intro = new Label(game.i18n.t("onboarding.intro"), game.skin, "small");
        intro.setColor(SkinFactory.TEXT_DIM);
        root.add(intro).padBottom(18f).row();

        Table body = new Table();
        Table options = new Table();
        options.defaults().left();
        preview = new Preview();

        options.add(new Label(game.i18n.t("onboarding.input"), game.skin, "bold")).padBottom(8f).row();
        Table inputRow = new Table();
        TextButton touch = option(inputRow, "onboarding.input.touch", s.isTouchControls(), () -> setTouch(true));
        TextButton keys = option(inputRow, "onboarding.input.keys", !s.isTouchControls(), () -> setTouch(false));
        new ButtonGroup<>(touch, keys);
        options.add(inputRow).padBottom(18f).row();

        options.add(new Label(game.i18n.t("onboarding.pedals"), game.skin, "bold")).padBottom(8f).row();
        pedalRow = new Table();
        boolean left = s.getPedalSide() == Settings.PedalSide.LEFT;
        TextButton pl = option(pedalRow, "settings.pedalSide.LEFT", left, () -> setPedals(Settings.PedalSide.LEFT));
        TextButton pr = option(pedalRow, "settings.pedalSide.RIGHT", !left, () -> setPedals(Settings.PedalSide.RIGHT));
        new ButtonGroup<>(pl, pr);
        options.add(pedalRow).padBottom(6f).row();
        Label pedalHint = new Label(game.i18n.t("onboarding.pedals.hint"), game.skin, "small");
        pedalHint.setWrap(true);
        pedalHint.setColor(SkinFactory.TEXT_DIM);
        options.add(pedalHint).width(470f).padBottom(18f).row();

        options.add(new Label(game.i18n.t("onboarding.size"), game.skin, "bold")).padBottom(8f).row();
        Table sizeRow = new Table();
        TextButton[] sizes = new TextButton[Settings.HudSize.values().length];
        for (Settings.HudSize size : Settings.HudSize.values()) {
            sizes[size.ordinal()] = option(sizeRow, "settings.hudSize." + size.name(), s.getHudSize() == size, () -> {
                s.setHudSize(size);
                preview.hudScale = size.viewportScale;
            });
        }
        new ButtonGroup<>(sizes);
        options.add(sizeRow).row();

        body.add(options).top().padRight(36f);
        Table previewBox = new Table();
        previewBox.add(preview).size(Preview.W, Preview.H).row();
        Label previewNote = new Label(game.i18n.t("onboarding.preview"), game.skin, "small");
        previewNote.setColor(SkinFactory.TEXT_FAINT);
        previewBox.add(previewNote).padTop(18f);
        body.add(previewBox).top();
        root.add(body).expand().top().row();

        Label later = new Label(game.i18n.t("onboarding.later"), game.skin, "small");
        later.setColor(SkinFactory.TEXT_DIM);
        root.add(later).padBottom(10f).row();
        TextButton start = new TextButton(game.i18n.t("onboarding.start"), game.skin);
        start.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                finish();
            }
        });
        root.add(start).width(280f).height(52f);

        preview.touch = s.isTouchControls();
        preview.pedalsLeft = left;
        preview.hudScale = s.getHudSize().viewportScale;
        pedalRow.setVisible(preview.touch);
        pedalHint.setVisible(preview.touch);
        this.pedalHint = pedalHint;
        ready = true;
    }

    private final Label pedalHint;
    /** Los grupos de botones disparan eventos al montarse: hasta terminar, no se guarda nada. */
    private boolean ready;

    private TextButton option(Table row, String key, boolean checked, Runnable action) {
        TextButton b = new TextButton(game.i18n.t(key), game.skin, "tab");
        b.setChecked(checked);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (ready && b.isChecked()) {
                    action.run();
                }
            }
        });
        row.add(b).height(46f).minWidth(150f).padRight(10f);
        return b;
    }

    private void setTouch(boolean touch) {
        s.setTouchControls(touch);
        preview.touch = touch;
        pedalRow.setVisible(touch);
        pedalHint.setVisible(touch);
    }

    private void setPedals(Settings.PedalSide side) {
        s.setPedalSide(side);
        preview.pedalsLeft = side == Settings.PedalSide.LEFT;
    }

    private void finish() {
        s.setOnboarded(true);
        s.flush();
        game.setScreen(new MainMenuScreen(game));
        dispose();
    }

    /**
     * Boceto de un móvil apaisado (20:9) con los mandos a escala del tamaño de HUD elegido: pedales,
     * palanca, volante, minimapa y lección, para ver de un vistazo cómo quedará.
     */
    private class Preview extends Actor {
        static final float W = 560f;
        static final float H = 252f;
        boolean touch = true;
        boolean pedalsLeft = true;
        float hudScale = 1.1f;

        @Override
        public void draw(Batch batch, float parentAlpha) {
            TextureRegion white = game.skin.getRegion("white");
            TextureRegion circle = game.skin.getRegion("circle");
            TextureRegion ring = game.skin.getRegion("ring");
            Drawable box = game.skin.getDrawable("hud-box");
            float x = getX(), y = getY();
            // Marco del móvil y "parabrisas"
            batch.setColor(0.05f, 0.06f, 0.08f, 1f);
            batch.draw(white, x - 8f, y - 8f, W + 16f, H + 16f);
            batch.setColor(0.42f, 0.6f, 0.78f, 1f);
            batch.draw(white, x, y + H * 0.45f, W, H * 0.55f);
            batch.setColor(0.32f, 0.33f, 0.35f, 1f);
            batch.draw(white, x, y, W, H * 0.45f);
            batch.setColor(0.14f, 0.15f, 0.17f, 1f);
            batch.draw(white, x, y, W, H * 0.3f);
            // Unidades del HUD a píxeles del boceto: el mundo mide 720·escala de alto
            float k = H / (720f * hudScale);
            float worldW = W / k;
            batch.setColor(Color.WHITE);
            // Minimapa y lección arriba
            box.draw(batch, x + W - (22f + 210f) * k, y + H - (40f + 210f) * k, 210f * k, 210f * k);
            box.draw(batch, x + 20f * k, y + H - (40f + 230f) * k, 430f * k, 230f * k);
            // Volante en el centro abajo
            batch.setColor(1f, 1f, 1f, 0.75f);
            float r = 165f * k;
            batch.draw(ring, x + W / 2f - r, y + 40f * k - r * 0.6f, r * 2f, r * 2f);
            if (touch) {
                float pedalsW = 256f * k;
                float handW = 282f * k;
                float px = pedalsLeft ? x + 36f * k : x + W - 36f * k - pedalsW;
                float hx = pedalsLeft ? x + W - 36f * k - handW : x + 36f * k;
                Color[] colors = {new Color(0.4f, 0.6f, 1f, 1f), new Color(1f, 0.35f, 0.3f, 1f),
                    new Color(0.4f, 0.9f, 0.4f, 1f)};
                for (int i = 0; i < 3; i++) {
                    batch.setColor(colors[i]);
                    batch.draw(white, px + i * 90f * k, y + 30f * k, 76f * k, 220f * k);
                }
                batch.setColor(Color.WHITE);
                float shifterX = pedalsLeft ? hx + (64f + 28f) * k : hx;
                box.draw(batch, shifterX, y + 34f * k, 190f * k, 176f * k);
                float buttonsX = pedalsLeft ? hx : hx + (190f + 28f) * k;
                batch.setColor(0.85f, 0.6f, 0.15f, 1f);
                batch.draw(circle, buttonsX, y + 146f * k, 64f * k, 64f * k);
                batch.setColor(0.2f, 0.22f, 0.26f, 1f);
                batch.draw(circle, buttonsX, y + 40f * k, 64f * k, 64f * k);
            } else {
                // Teclado: reloj abajo a la derecha y barras de pedales a la izquierda
                batch.setColor(Color.WHITE);
                box.draw(batch, x + 36f * k, y + 50f * k, 214f * k, 150f * k);
                batch.setColor(0.08f, 0.1f, 0.13f, 0.9f);
                float tr = 122f * k;
                batch.draw(circle, x + worldW * k - 150f * k - tr, y + 140f * k - tr, tr * 2f, tr * 2f);
            }
            batch.setColor(Color.WHITE);
        }
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
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
