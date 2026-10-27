package com.pelayo.autoescuela;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.pelayo.autoescuela.app.Assets;
import com.pelayo.autoescuela.app.DevOptions;
import com.pelayo.autoescuela.app.I18n;
import com.pelayo.autoescuela.app.Progress;
import com.pelayo.autoescuela.app.Screenshots;
import com.pelayo.autoescuela.app.Settings;
import com.pelayo.autoescuela.input.InputMapping;
import com.pelayo.autoescuela.theory.StatsStore;
import com.pelayo.autoescuela.theory.TheoryRepository;
import com.pelayo.autoescuela.ui.screens.LoadingScreen;

/**
 * Punto de entrada común a escritorio y Android. Pone en pie los servicios
 * compartidos (recursos, ajustes, textos, skin) y arranca en la pantalla de
 * carga; la navegación entre pantallas se hace con {@link #setScreen}.
 */
public class AutoescuelaGame extends Game {

    /** Resolución virtual de la interfaz (scene2d), en unidades de mundo. */
    public static final float UI_WIDTH = 1280f;
    public static final float UI_HEIGHT = 720f;

    public Assets assets;
    public Settings settings;
    /** Teclas del conductor (reasignables en Ajustes → Controles). */
    public InputMapping keys;
    /** Lecciones completadas y prácticas superadas. */
    public Progress progress;
    public I18n i18n;
    /** Temario y banco de preguntas; se cargan la primera vez que se entra en teoría. */
    public TheoryRepository theory;
    public StatsStore stats;
    /** Se construye al terminar la carga (necesita las fuentes generadas). */
    public Skin skin;
    /** Opciones de desarrollo (pantalla inicial, guion de mandos, capturas). */
    public final DevOptions dev;

    /** Cómo cerrar la app de verdad en cada plataforma (en Android, finishAndRemoveTask). */
    private Runnable platformExit = () -> Gdx.app.exit();

    public AutoescuelaGame() {
        this(new DevOptions());
    }

    public AutoescuelaGame(DevOptions dev) {
        this.dev = dev;
    }

    @Override
    public void create() {
        // Imprescindible antes de crear cualquier objeto de bullet (carga las natives)
        Bullet.init();
        Gdx.app.log("Autoescuela", "Bullet " + Bullet.VERSION + " inicializado");

        settings = new Settings();
        keys = new InputMapping();
        progress = new Progress();
        i18n = new I18n(settings.getLanguage());
        assets = new Assets();

        setScreen(new LoadingScreen(this));
    }

    /** Carga el temario, las preguntas (propias y del usuario) y las estadísticas si aún no están. */
    public void ensureTheory() {
        if (theory == null) {
            theory = TheoryRepository.loadDefault();
        }
        if (stats == null) {
            stats = new StatsStore();
        }
    }

    /** Voz del examinador (la pone cada plataforma). */
    public com.pelayo.autoescuela.app.Speech speech = com.pelayo.autoescuela.app.Speech.NONE;

    public void setPlatformExit(Runnable platformExit) {
        this.platformExit = platformExit;
    }

    /** Guarda los ajustes y cierra la aplicación. */
    public void exit() {
        settings.flush();
        platformExit.run();
    }

    private float wallTime;
    private int nextWallShot;

    @Override
    public void render() {
        super.render();
        // Modo desarrollo: capturas y cierre por tiempo real en cualquier pantalla
        if (dev.wallShots.size > 0 || dev.wallExitAt >= 0f) {
            wallTime += Gdx.graphics.getDeltaTime();
            while (nextWallShot < dev.wallShots.size && wallTime >= dev.wallShots.get(nextWallShot)) {
                Screenshots.save("wshot_" + nextWallShot);
                nextWallShot++;
            }
            if (dev.wallExitAt >= 0f && wallTime >= dev.wallExitAt) {
                Gdx.app.exit();
            }
        }
    }

    @Override
    public void dispose() {
        if (screen != null) {
            screen.dispose();
        }
        if (skin != null) {
            skin.dispose();
        }
        if (assets != null) {
            assets.dispose();
        }
    }
}
