package com.pelayo.autoescuela;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.pelayo.autoescuela.app.I18n;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Comprueba que el entorno de tests funciona: backend headless con el
 * directorio de trabajo en assets/, natives de bullet y textos cargables.
 */
class SmokeTest {

    private static HeadlessApplication app;

    @BeforeAll
    static void boot() {
        app = HeadlessTestApp.start();
        Bullet.init();
    }

    @AfterAll
    static void shutdown() {
        app.exit();
    }

    @Test
    void bulletNativesLoad() {
        btBoxShape box = new btBoxShape(new Vector3(0.5f, 0.5f, 0.5f));
        Vector3 withMargin = new Vector3(box.getHalfExtentsWithMargin());
        Vector3 withoutMargin = new Vector3(box.getHalfExtentsWithoutMargin());
        box.dispose();
        // Bullet guarda las medias extensiones sin el margen de colisión (0,04 m por defecto)
        assertEquals(0.5f, withMargin.x, 1e-5f, "con margen: " + withMargin);
        assertTrue(withoutMargin.x < 0.5f && withoutMargin.x > 0.4f, "sin margen: " + withoutMargin);
    }

    @Test
    void assetsAreTheWorkingDir() {
        assertTrue(Gdx.files.internal("i18n/strings.properties").exists(),
            "el test debe ejecutarse con workingDir = assets/");
        assertTrue(Gdx.files.internal("fonts/Roboto-Regular.ttf").exists());
    }

    @Test
    void i18nLoadsSpanish() {
        I18n i18n = new I18n("es");
        assertEquals("Autoescuela", i18n.t("app.title"));
        assertEquals("Ajustes: próximamente", i18n.t("menu.comingSoon", "Ajustes"));
    }
}
