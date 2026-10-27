package com.pelayo.autoescuela.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.pelayo.autoescuela.AutoescuelaGame;
import com.pelayo.autoescuela.data.TemaDef;
import com.pelayo.autoescuela.theory.TestGenerator;
import com.pelayo.autoescuela.theory.TestSession;
import com.pelayo.autoescuela.ui.SkinFactory;
import com.pelayo.autoescuela.ui.widgets.RichText;

/** Lectura de un tema: secciones con texto enriquecido y botón para hacer su test. */
public class TopicScreen extends ScreenAdapter {

    private final AutoescuelaGame game;
    private final Stage stage;
    private final Array<RichText> texts = new Array<>();

    public TopicScreen(AutoescuelaGame game, TemaDef tema) {
        this.game = game;
        stage = new Stage(new ExtendViewport(AutoescuelaGame.UI_WIDTH, AutoescuelaGame.UI_HEIGHT));

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(game.skin.getDrawable("bg"));
        root.pad(20f);
        stage.addActor(root);
        root.add(new Label(tema.orden + ". " + tema.titulo, game.skin, "title")).padBottom(10f).row();

        Table content = new Table();
        content.top();
        float width = 1000f;
        for (TemaDef.SeccionDef s : tema.secciones) {
            Table section = new Table();
            section.setBackground(game.skin.getDrawable("panel"));
            section.pad(14f);
            section.top().left();
            if (s.titulo != null) {
                // Título de la sección con una marca de color, para distinguirlo de los subtítulos del texto
                Table head = new Table();
                com.badlogic.gdx.scenes.scene2d.ui.Image mark = new com.badlogic.gdx.scenes.scene2d.ui.Image(game.skin.getDrawable("white"));
                mark.setColor(com.pelayo.autoescuela.ui.SkinFactory.ACCENT_LIGHT);
                head.add(mark).width(5f).height(26f).padRight(10f);
                head.add(new Label(s.titulo, game.skin, "bold")).left();
                section.add(head).left().padBottom(8f).row();
            }
            String text = s.texto == null ? "" : s.texto;
            if (s.imagen != null) {
                text = "![](" + s.imagen + ")\n\n" + text;
            }
            RichText rich = new RichText(game.skin, text, width - 28f);
            texts.add(rich);
            section.add(rich).left().width(width - 28f).row();
            content.add(section).width(width).padBottom(10f).row();
        }
        ScrollPane scroll = new ScrollPane(content, game.skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        root.add(scroll).expand().fill().row();

        Table buttons = new Table();
        TextButton test = new TextButton(game.i18n.t("topic.test"), game.skin);
        test.setDisabled(game.theory.preguntasDe(tema.id).size == 0);
        test.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Array<com.pelayo.autoescuela.data.PreguntaDef> qs = new TestGenerator(game.theory).byTopic(tema.id, 10);
                if (qs.size > 0) {
                    game.setScreen(new TestScreen(game, new TestSession(TestSession.Mode.PRACTICE, TestGenerator.shuffleOptions(qs))));
                    dispose();
                }
            }
        });
        TextButton back = new TextButton(game.i18n.t("practice.back"), game.skin, "secondary");
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new TheoryScreen(game));
                dispose();
            }
        });
        buttons.add(test).width(260f).height(46f).padRight(12f);
        buttons.add(back).width(200f).height(46f);
        root.add(buttons).padTop(10f);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.setScreen(new TheoryScreen(game));
            dispose();
            return;
        }
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
        for (RichText t : texts) {
            t.dispose();
        }
        stage.dispose();
    }
}
