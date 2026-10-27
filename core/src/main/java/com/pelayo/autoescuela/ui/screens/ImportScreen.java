package com.pelayo.autoescuela.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.files.FileHandle;
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
import com.pelayo.autoescuela.theory.QuestionImporter;
import com.pelayo.autoescuela.ui.SkinFactory;

/** Importa los bancos de preguntas (.json / .csv) que haya en la carpeta local import/. */
public class ImportScreen extends ScreenAdapter {

    private final AutoescuelaGame game;
    private final Stage stage;
    private final Label report;
    private final Table files;

    public ImportScreen(AutoescuelaGame game) {
        this.game = game;
        stage = new Stage(new ExtendViewport(AutoescuelaGame.UI_WIDTH, AutoescuelaGame.UI_HEIGHT));

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(game.skin.getDrawable("bg"));
        root.pad(20f);
        stage.addActor(root);
        root.add(new Label(game.i18n.t("import.title"), game.skin, "title")).padBottom(8f).row();
        FileHandle dir = Gdx.files.local("import");
        dir.mkdirs();
        Label help = new Label(game.i18n.t("import.help", dir.file().getAbsolutePath()), game.skin, "small");
        help.setWrap(true);
        root.add(help).width(960f).padBottom(12f).row();

        files = new Table();
        files.top().left();
        refreshFiles();
        ScrollPane scroll = new ScrollPane(files, game.skin);
        scroll.setFadeScrollBars(false);
        root.add(scroll).width(960f).height(260f).row();

        report = new Label("", game.skin, "small");
        report.setWrap(true);
        root.add(report).width(960f).padTop(10f).expand().top().row();

        Table buttons = new Table();
        TextButton importAll = new TextButton(game.i18n.t("import.all"), game.skin);
        importAll.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                importAll();
            }
        });
        TextButton refresh = new TextButton(game.i18n.t("import.refresh"), game.skin, "secondary");
        refresh.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                refreshFiles();
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
        buttons.add(importAll).width(240f).height(46f).padRight(10f);
        buttons.add(refresh).width(180f).height(46f).padRight(10f);
        buttons.add(back).width(180f).height(46f);
        root.add(buttons).padTop(10f);
    }

    private Array<FileHandle> listFiles() {
        Array<FileHandle> list = new Array<>();
        for (FileHandle f : Gdx.files.local("import").list()) {
            String ext = f.extension().toLowerCase();
            if (ext.equals("json") || ext.equals("csv")) {
                list.add(f);
            }
        }
        list.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return list;
    }

    private void refreshFiles() {
        files.clearChildren();
        Array<FileHandle> list = listFiles();
        if (list.size == 0) {
            files.add(new Label(game.i18n.t("import.none"), game.skin, "small")).left().row();
        }
        for (FileHandle f : list) {
            files.add(new Label(f.name() + "   (" + f.length() / 1024 + " KB)", game.skin, "small")).left().padBottom(4f).row();
        }
    }

    private void importAll() {
        QuestionImporter importer = new QuestionImporter(game.theory);
        StringBuilder sb = new StringBuilder();
        int added = 0;
        for (FileHandle f : listFiles()) {
            try {
                QuestionImporter.Report r = importer.importFile(f);
                added += r.added;
                sb.append(f.name()).append(": ").append(r).append('\n');
                for (int i = 0; i < Math.min(5, r.problems.size); i++) {
                    sb.append("   · ").append(r.problems.get(i)).append('\n');
                }
            } catch (Exception e) {
                sb.append(f.name()).append(": ").append(game.i18n.t("import.error", e.getMessage())).append('\n');
                Gdx.app.error("ImportScreen", "Error importando " + f, e);
            }
        }
        if (sb.length() == 0) {
            sb.append(game.i18n.t("import.none"));
        }
        sb.append('\n').append(game.i18n.t("import.total", added, game.theory.preguntas.size));
        report.setText(sb.toString());
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
        stage.dispose();
    }
}
