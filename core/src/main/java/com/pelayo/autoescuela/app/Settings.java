package com.pelayo.autoescuela.app;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/**
 * Ajustes del usuario, persistidos con {@link Preferences}. Las claves se
 * centralizan aquí para que las pantallas no manejen cadenas sueltas.
 */
public class Settings {

    private static final String PREFS = "autoescuela";

    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_MASTER_VOLUME = "masterVolume";
    private static final String KEY_MIRRORS = "mirrors";
    private static final String KEY_QUALITY = "quality";

    /** Espejos retrovisores renderizados: ninguno, solo el interior, o todos. */
    public enum Mirrors { OFF, INTERIOR, ALL }

    /** Calidad gráfica global (sombras, resolución de espejos, filtros). */
    public enum Quality { LOW, MEDIUM, HIGH }

    private final Preferences prefs;

    public Settings() {
        prefs = Gdx.app.getPreferences(PREFS);
    }

    public String getLanguage() {
        return prefs.getString(KEY_LANGUAGE, "es");
    }

    public void setLanguage(String language) {
        prefs.putString(KEY_LANGUAGE, language);
    }

    public float getMasterVolume() {
        return prefs.getFloat(KEY_MASTER_VOLUME, 0.8f);
    }

    public void setMasterVolume(float volume) {
        prefs.putFloat(KEY_MASTER_VOLUME, volume);
    }

    public Mirrors getMirrors() {
        return Mirrors.valueOf(prefs.getString(KEY_MIRRORS, Mirrors.ALL.name()));
    }

    public void setMirrors(Mirrors mirrors) {
        prefs.putString(KEY_MIRRORS, mirrors.name());
    }

    public Quality getQuality() {
        return Quality.valueOf(prefs.getString(KEY_QUALITY, Quality.HIGH.name()));
    }

    public void setQuality(Quality quality) {
        prefs.putString(KEY_QUALITY, quality.name());
    }

    private static final String KEY_TOUCH = "touchControls";

    /** Controles táctiles en pantalla (por defecto solo en Android). */
    public boolean isTouchControls() {
        boolean android = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
        return prefs.getBoolean(KEY_TOUCH, android);
    }

    public void setTouchControls(boolean enabled) {
        prefs.putBoolean(KEY_TOUCH, enabled);
    }

    /** Tamaño del HUD de conducción: cuántas unidades de interfaz caben en pantalla. */
    public enum HudSize {
        SMALL(1.3f), NORMAL(1.1f), LARGE(0.92f);

        /** Factor sobre la resolución virtual (1280 x 720): mayor = HUD más pequeño. */
        public final float viewportScale;

        HudSize(float viewportScale) {
            this.viewportScale = viewportScale;
        }
    }

    /** Dónde se ven cuentarrevoluciones, velocidad y marcha en la vista de cabina. */
    public enum Gauges { SCREEN, DASHBOARD }

    private static final String KEY_RIGHT_MIRROR = "rightMirrorInset";

    /** Retrovisor derecho en un recuadro de la pantalla (desde el asiento queda fuera de la vista). */
    public boolean isRightMirrorInset() {
        return prefs.getBoolean(KEY_RIGHT_MIRROR, true);
    }

    public void setRightMirrorInset(boolean enabled) {
        prefs.putBoolean(KEY_RIGHT_MIRROR, enabled);
    }

    /** Lado de los pedales táctiles: la palanca y los botones van en el otro. */
    public enum PedalSide { LEFT, RIGHT }

    private static final String KEY_PEDAL_SIDE = "pedalSide";

    public PedalSide getPedalSide() {
        try {
            return PedalSide.valueOf(prefs.getString(KEY_PEDAL_SIDE, PedalSide.LEFT.name()));
        } catch (IllegalArgumentException e) {
            return PedalSide.LEFT;
        }
    }

    public void setPedalSide(PedalSide side) {
        prefs.putString(KEY_PEDAL_SIDE, side.name());
    }

    /**
     * Encuadre de la vista de cabina. WIDE: ojos algo más altos y adelantados, campo algo más cerrado y
     * mirada un poco más alta: el parabrisas ocupa más pantalla y se ve más carretera (pensado para móvil).
     */
    public enum CockpitView {
        NORMAL(0f, 0f, 70f, 0f), WIDE(0.05f, 0.12f, 64f, 4f);

        public final float raise, forward, fov, pitchDeg;

        CockpitView(float raise, float forward, float fov, float pitchDeg) {
            this.raise = raise;
            this.forward = forward;
            this.fov = fov;
            this.pitchDeg = pitchDeg;
        }
    }

    private static final String KEY_COCKPIT_VIEW = "cockpitView";

    public CockpitView getCockpitView() {
        boolean android = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
        CockpitView fallback = android ? CockpitView.WIDE : CockpitView.NORMAL;
        try {
            return CockpitView.valueOf(prefs.getString(KEY_COCKPIT_VIEW, fallback.name()));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public void setCockpitView(CockpitView view) {
        prefs.putString(KEY_COCKPIT_VIEW, view.name());
    }

    private static final String KEY_SHOW_FPS = "showFps";
    private static final String KEY_AUTO_QUALITY = "autoQuality";

    public boolean isShowFps() {
        return prefs.getBoolean(KEY_SHOW_FPS, false);
    }

    public void setShowFps(boolean show) {
        prefs.putBoolean(KEY_SHOW_FPS, show);
    }

    /** Bajar la calidad gráfica sola si el móvil no llega a una fluidez mínima. */
    public boolean isAutoQuality() {
        return prefs.getBoolean(KEY_AUTO_QUALITY, true);
    }

    public void setAutoQuality(boolean enabled) {
        prefs.putBoolean(KEY_AUTO_QUALITY, enabled);
    }

    private static final String KEY_ONBOARDED = "onboarded";

    /** Ya se ha pasado por la configuración inicial (se muestra solo la primera vez). */
    public boolean isOnboarded() {
        return prefs.getBoolean(KEY_ONBOARDED, false);
    }

    public void setOnboarded(boolean done) {
        prefs.putBoolean(KEY_ONBOARDED, done);
    }

    private static final String KEY_VOICE = "examinerVoice";

    /** Leer en voz alta las indicaciones del examinador. */
    public boolean isVoice() {
        return prefs.getBoolean(KEY_VOICE, true);
    }

    public void setVoice(boolean enabled) {
        prefs.putBoolean(KEY_VOICE, enabled);
    }

    private static final String KEY_VIBRATION = "vibration";
    private static final String KEY_STICKY_CLUTCH = "stickyClutch";
    private static final String KEY_MINIMAP = "minimap";

    /** Vibrar al entrar en el punto de fricción, al calar y al chocar (solo móvil). */
    public boolean isVibration() {
        return prefs.getBoolean(KEY_VIBRATION, true);
    }

    public void setVibration(boolean enabled) {
        prefs.putBoolean(KEY_VIBRATION, enabled);
    }

    /** Embrague táctil que se queda donde se suelta el dedo (para salir en cuesta con un pulgar). */
    public boolean isStickyClutch() {
        return prefs.getBoolean(KEY_STICKY_CLUTCH, false);
    }

    public void setStickyClutch(boolean enabled) {
        prefs.putBoolean(KEY_STICKY_CLUTCH, enabled);
    }

    /** Minimapa desplegado (si no, queda un botón para abrirlo). */
    public boolean isMinimap() {
        return prefs.getBoolean(KEY_MINIMAP, true);
    }

    public void setMinimap(boolean visible) {
        prefs.putBoolean(KEY_MINIMAP, visible);
    }

    private static final String KEY_HUD_SIZE = "hudSize";
    private static final String KEY_GAUGES = "gauges";

    public HudSize getHudSize() {
        try {
            return HudSize.valueOf(prefs.getString(KEY_HUD_SIZE, HudSize.NORMAL.name()));
        } catch (IllegalArgumentException e) {
            return HudSize.NORMAL;
        }
    }

    public void setHudSize(HudSize size) {
        prefs.putString(KEY_HUD_SIZE, size.name());
    }

    public Gauges getGauges() {
        try {
            return Gauges.valueOf(prefs.getString(KEY_GAUGES, Gauges.SCREEN.name()));
        } catch (IllegalArgumentException e) {
            return Gauges.SCREEN;
        }
    }

    public void setGauges(Gauges gauges) {
        prefs.putString(KEY_GAUGES, gauges.name());
    }

    private static final String KEY_CAR_PAINT = "carPaint";

    /** Color de la carrocería del coche del alumno. */
    public CarPaint getCarPaint() {
        return CarPaint.parse(prefs.getString(KEY_CAR_PAINT, CarPaint.ORIGINAL.name()));
    }

    public void setCarPaint(CarPaint paint) {
        prefs.putString(KEY_CAR_PAINT, paint.name());
    }

    public void flush() {
        prefs.flush();
    }
}
