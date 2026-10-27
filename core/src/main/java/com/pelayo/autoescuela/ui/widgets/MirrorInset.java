package com.pelayo.autoescuela.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;

/**
 * Retrovisor en pantalla: muestra la imagen de un espejo del coche (la misma
 * que se pinta en el espejo 3D) en un recuadro, para poder mirarlo sin girar
 * la cabeza. Se usa para el derecho, que desde el asiento del conductor
 * queda fuera de la vista.
 */
public class MirrorInset extends Actor {

    private final TextureRegion region;
    private final Drawable frame;
    private final BitmapFont font;
    private final String label;
    private final GlyphLayout layout = new GlyphLayout();

    /**
     * @param flipY la imagen del framebuffer llega al revés en vertical según cómo se
     *              haya renderizado el espejo
     */
    public MirrorInset(Skin skin, Texture texture, String label, boolean flipY, float width, float height) {
        region = new TextureRegion(texture);
        region.flip(false, flipY);
        frame = skin.getDrawable("hud-box");
        font = skin.getFont("font-small");
        this.label = label;
        setSize(width, height);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX(), y = getY(), w = getWidth(), h = getHeight();
        batch.setColor(1f, 1f, 1f, parentAlpha);
        frame.draw(batch, x - 6f, y - 6f, w + 12f, h + 12f);
        batch.setColor(1f, 1f, 1f, parentAlpha);
        batch.draw(region, x, y, w, h);
        // Rótulo en una pestaña encima del recuadro, sin tapar la imagen
        layout.setText(font, label, new Color(1f, 1f, 1f, 0.8f), 0f, Align.left, false);
        frame.draw(batch, x - 6f, y + h + 4f, layout.width + 20f, layout.height + 14f);
        font.draw(batch, layout, x + 4f, y + h + 11f + layout.height);
        batch.setColor(Color.WHITE);
    }
}
