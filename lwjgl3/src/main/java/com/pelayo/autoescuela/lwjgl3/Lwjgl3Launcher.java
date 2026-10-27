package com.pelayo.autoescuela.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.pelayo.autoescuela.AutoescuelaGame;
import com.pelayo.autoescuela.app.DevOptions;

/**
 * Arranque en escritorio (Windows/Linux/macOS) con LWJGL3. Admite opciones de
 * desarrollo: {@code --screen=driving --script="0:gear=1|1:throttle=1" --shots=3,6 --exit=7}.
 */
public class Lwjgl3Launcher {

    public static void main(String[] args) {
        DevOptions dev = DevOptions.parse(args);
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Autoescuela");
        config.setWindowIcon("icons/icon128.png", "icons/icon64.png",
            "icons/icon32.png", "icons/icon16.png");
        // --size=1600x720 prueba otras proporciones (p.ej. la de un móvil, 20:9)
        int width = 1280;
        int height = 720;
        for (String arg : args) {
            if (arg.startsWith("--size=")) {
                String[] p = arg.substring(7).split("x");
                width = Integer.parseInt(p[0]);
                height = Integer.parseInt(p[1]);
            }
        }
        config.setWindowedMode(width, height);
        config.useVsync(true);
        config.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        // Los shaders PBR de gdx-gltf rinden mejor y con más funciones en GL 3.x
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        config.setBackBufferConfig(8, 8, 8, 8, 24, 8, 4); // profundidad 24 bits + MSAA x4
        AutoescuelaGame game = new AutoescuelaGame(dev);
        if (WindowsSpeech.available()) {
            game.speech = new WindowsSpeech();
        }
        new Lwjgl3Application(game, config);
    }
}
