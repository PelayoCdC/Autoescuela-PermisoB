package com.pelayo.autoescuela.ui.widgets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte el marcado mínimo del temario en actores de scene2d:
 * {@code # Título}, {@code ## Subtítulo}, {@code 1. / - viñetas}, {@code **negrita**}
 * (en color de énfasis), {@code ![alt](ruta)} y párrafos separados por líneas en blanco.
 */
public class RichText extends Table implements Disposable {

    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern IMAGE = Pattern.compile("^!\\[(.*?)\\]\\((.+?)\\)$");
    private static final String EMPHASIS = "[#FFD27A]";

    private final Skin skin;
    private final float width;
    private final Array<Texture> textures = new Array<>();

    public RichText(Skin skin, String text, float width) {
        this.skin = skin;
        this.width = width;
        top().left();
        defaults().left().padBottom(6f);
        if (text != null) {
            render(text);
        }
    }

    private void render(String text) {
        StringBuilder paragraph = new StringBuilder();
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                flushParagraph(paragraph);
                continue;
            }
            if (line.startsWith("# ")) {
                flushParagraph(paragraph);
                add(label(line.substring(2), "default")).width(width).padTop(8f).row();
            } else if (line.startsWith("## ")) {
                flushParagraph(paragraph);
                add(label(line.substring(3), "default")).width(width).padTop(4f).row();
            } else if (line.startsWith("- ") || line.matches("^\\d+\\. .*")) {
                flushParagraph(paragraph);
                String body = line.startsWith("- ") ? line.substring(2) : line;
                String bullet = line.startsWith("- ") ? "•  " : "";
                Table row = new Table();
                row.left();
                Label b = label(bullet + body, "small");
                row.add(b).width(width - 16f).left();
                add(row).padLeft(16f).row();
            } else {
                Matcher m = IMAGE.matcher(line);
                if (m.matches()) {
                    flushParagraph(paragraph);
                    addImage(m.group(2));
                } else {
                    if (paragraph.length() > 0) {
                        paragraph.append(' ');
                    }
                    paragraph.append(line);
                }
            }
        }
        flushParagraph(paragraph);
    }

    private void flushParagraph(StringBuilder paragraph) {
        if (paragraph.length() == 0) {
            return;
        }
        add(label(paragraph.toString(), "small")).width(width).row();
        paragraph.setLength(0);
    }

    private Label label(String text, String style) {
        Label label = new Label(markup(text), skin, style);
        label.setWrap(true);
        return label;
    }

    /** **negrita** → color de énfasis (las fuentes tienen el marcado de color activado). */
    static String markup(String text) {
        Matcher m = BOLD.matcher(text.replace("[", "[["));
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(text, last, m.start()).append(EMPHASIS).append(m.group(1)).append("[]");
            last = m.end();
        }
        sb.append(text.substring(last));
        return sb.toString();
    }

    private void addImage(String path) {
        FileHandle file = Gdx.files.internal(path);
        if (!file.exists()) {
            file = Gdx.files.local(path);
        }
        if (!file.exists()) {
            add(label("[imagen no encontrada: " + path + "]", "small")).width(width).row();
            return;
        }
        Texture texture = new Texture(file);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        textures.add(texture);
        Image image = new Image(texture);
        float scale = Math.min(1f, width / texture.getWidth());
        add(image).size(texture.getWidth() * scale, texture.getHeight() * scale).row();
    }

    @Override
    public void dispose() {
        for (Texture t : textures) {
            t.dispose();
        }
        textures.clear();
    }
}
