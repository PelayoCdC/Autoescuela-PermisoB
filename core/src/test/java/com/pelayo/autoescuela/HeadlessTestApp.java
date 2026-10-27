package com.pelayo.autoescuela;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;

/**
 * Arranca una aplicación libGDX sin ventana para que {@code Gdx.app} y
 * {@code Gdx.files} existan en los tests. No hay bucle de render que corra
 * (updatesPerSecond = -1): cada test llama a lo que quiere probar.
 */
public final class HeadlessTestApp {

    private HeadlessTestApp() {
    }

    public static HeadlessApplication start() {
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        config.updatesPerSecond = -1;
        return new HeadlessApplication(new ApplicationAdapter() {
        }, config);
    }
}
