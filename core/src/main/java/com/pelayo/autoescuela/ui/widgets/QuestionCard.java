package com.pelayo.autoescuela.ui.widgets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Disposable;
import com.pelayo.autoescuela.data.PreguntaDef;

/**
 * Una pregunta con su imagen opcional y tres botones de respuesta. Tras
 * responder puede mostrar la corrección (botón verde/rojo y explicación).
 */
public class QuestionCard extends Table implements Disposable {

    public interface Listener {
        void onAnswer(int option);
    }

    private final Skin skin;
    private final TextButton[] options = new TextButton[3];
    private final Label explanation;
    private Texture texture;
    private PreguntaDef question;
    private Listener listener;

    public QuestionCard(Skin skin, float width) {
        this.skin = skin;
        top().left();
        defaults().left().padBottom(8f);
        setBackground(skin.getDrawable("panel"));
        pad(16f);
        explanation = new Label("", skin, "small");
        explanation.setWrap(true);
        explanation.setVisible(false);
        this.width = width;
    }

    private final float width;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Muestra la pregunta; {@code answered} = opción ya marcada (o -1); {@code reveal} = mostrar corrección. */
    public void show(PreguntaDef q, int answered, boolean reveal) {
        this.question = q;
        clearChildren();
        if (texture != null) {
            texture.dispose();
            texture = null;
        }
        Label statement = new Label(q.enunciado, skin, "default");
        statement.setWrap(true);
        add(statement).width(width - 32f).row();
        // Procedencia, si no es del banco propio (la revista de la DGT exige citarla como fuente)
        if (q.fuente != null && !q.fuente.isEmpty() && !"propia".equals(q.fuente)) {
            Label source = new Label("Fuente: " + q.fuente, skin, "small");
            source.setColor(0.55f, 0.6f, 0.68f, 1f);
            source.setWrap(true);
            add(source).width(width - 32f).padTop(-4f).row();
        }
        if (q.imagen != null) {
            FileHandle file = Gdx.files.internal(q.imagen);
            if (!file.exists()) {
                file = Gdx.files.local(q.imagen);
            }
            if (file.exists()) {
                texture = new Texture(file);
                texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                Image image = new Image(texture);
                // Las miniaturas pequeñas se amplían algo para que se distingan
                float scale = Math.min(1.6f, Math.min((width - 32f) / texture.getWidth(), 200f / texture.getHeight()));
                add(image).size(texture.getWidth() * scale, texture.getHeight() * scale).padBottom(12f).row();
            }
        }
        for (int i = 0; i < q.opciones.length; i++) {
            final int option = i;
            String style = "answer";
            if (reveal) {
                if (i == q.correcta) {
                    style = "answer-ok";
                } else if (i == answered) {
                    style = "answer-fail";
                }
            }
            TextButton button = new TextButton((char) ('A' + i) + ")  " + q.opciones[i], skin, style);
            button.getLabel().setWrap(true);
            button.getLabel().setAlignment(com.badlogic.gdx.utils.Align.left);
            button.getLabelCell().width(width - 72f).pad(6f, 12f, 6f, 12f);
            button.setDisabled(reveal);
            if (!reveal && answered == i) {
                button.setChecked(true);
            }
            button.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (!button.isDisabled() && listener != null) {
                        listener.onAnswer(option);
                    }
                }
            });
            options[i] = button;
            add(button).width(width - 32f).row();
        }
        if (reveal && q.explicacion != null && !q.explicacion.isEmpty()) {
            explanation.setText(q.explicacion);
            explanation.setVisible(true);
            add(explanation).width(width - 32f).padTop(6f).row();
        } else {
            explanation.setVisible(false);
        }
    }

    public PreguntaDef getQuestion() {
        return question;
    }

    @Override
    public void dispose() {
        if (texture != null) {
            texture.dispose();
            texture = null;
        }
    }
}
