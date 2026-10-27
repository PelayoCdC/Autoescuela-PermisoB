package com.pelayo.autoescuela.android;

import android.os.Bundle;
import android.view.WindowManager;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.pelayo.autoescuela.AutoescuelaGame;

/** Arranque en Android. */
public class AndroidLauncher extends AndroidApplication {

    private android.speech.tts.TextToSpeech tts;
    private volatile boolean ttsReady;

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        config.useAccelerometer = false;
        config.useCompass = false;
        // GL ES 3.0 para los shaders PBR; libGDX cae a 2.0 si el dispositivo no lo soporta
        config.useGL30 = true;
        config.depth = 24;
        config.numSamples = 0;
        AutoescuelaGame game = new AutoescuelaGame();
        // Voz del examinador con la síntesis del sistema, en español
        tts = new android.speech.tts.TextToSpeech(this, status -> {
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                tts.setLanguage(new java.util.Locale("es", "ES"));
                ttsReady = true;
            }
        });
        game.speech = new com.pelayo.autoescuela.app.Speech() {
            @Override
            public void speak(String text) {
                if (ttsReady) {
                    tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "examinador");
                }
            }

            @Override
            public void stop() {
                if (ttsReady) {
                    tts.stop();
                }
            }
        };
        // Gdx.app.exit() en Android solo hace Activity.finish() (la app queda en
        // Recientes y el proceso se reutiliza); cerrarla de verdad desde Salir.
        game.setPlatformExit(() -> {
            finishAndRemoveTask();
            System.exit(0);
        });
        initialize(game, config);
    }
}
