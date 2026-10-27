package com.pelayo.autoescuela.app;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.I18NBundle;
import java.util.Locale;

/**
 * Textos de la interfaz. Envuelve el {@link I18NBundle} de
 * {@code assets/i18n/strings.properties} (UTF-8 sin BOM; variantes
 * {@code strings_en.properties}, etc.).
 */
public class I18n {

    private final I18NBundle bundle;

    public I18n(String language) {
        I18NBundle.setExceptionOnMissingKey(false);
        bundle = I18NBundle.createBundle(
            Gdx.files.internal("i18n/strings"), Locale.forLanguageTag(language), "UTF-8");
    }

    /** Texto de la clave, con los argumentos formateados ({0}, {1}...). */
    public String t(String key, Object... args) {
        return args.length == 0 ? bundle.get(key) : bundle.format(key, args);
    }

    public I18NBundle bundle() {
        return bundle;
    }
}
