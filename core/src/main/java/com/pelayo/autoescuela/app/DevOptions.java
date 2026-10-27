package com.pelayo.autoescuela.app;

import com.badlogic.gdx.utils.Array;

/**
 * Opciones de desarrollo pasadas por línea de comandos
 * ({@code gradlew lwjgl3:run --args="--screen=driving --script=... --shots=2,5"}).
 * Permiten arrancar en una pantalla concreta, conducir con una secuencia de
 * mandos fija y guardar capturas en {@code cache/shot_N.png} sin tocar nada.
 */
public final class DevOptions {

    /** Pantalla inicial: null (menú) o "driving". */
    public String screen;
    /** Guion de mandos para {@link com.pelayo.autoescuela.input.ScriptedDriverInput}, o null. */
    public String script;
    /** Instantes de simulación (s) en los que guardar una captura. */
    public final Array<Float> shots = new Array<>();
    /** Cerrar la app a este tiempo de simulación (s), o -1 para no cerrar. */
    public float exitAt = -1f;
    /** Escenario a cargar directamente (id de assets/data/scenarios), o null. */
    public String scenario;
    /** Abandonar la maniobra a este tiempo de simulación (s) para ver los resultados, o -1. */
    public float abortAt = -1f;
    /** Forzar los controles táctiles en escritorio (para probarlos). */
    public boolean touch;
    /** No mostrar la ayuda de controles al empezar (capturas). */
    public boolean hideHelp;
    /** Ignorar el ajuste de controles táctiles (capturas). */
    public boolean noTouch;
    /** Capturas por tiempo real (s) en cualquier pantalla, y cierre por tiempo real. */
    public final Array<Float> wallShots = new Array<>();
    public float wallExitAt = -1f;
    /** Posición de los ojos (x,y,z respecto al chasis) y campo de visión, para afinar la cabina. */
    public float[] eye;
    public float fov = -1f;
    /** Abrir el menú de pausa a este tiempo de simulación (capturas), o -1. */
    public float pauseAt = -1f;
    /** Color del coche para esta ejecución (nombre de CarPaint), sin tocar los ajustes guardados. */
    public String paint;
    /** Tamaño del HUD para esta ejecución (small, normal, large), sin tocar los ajustes guardados. */
    public String hud;
    /** Lado de los pedales táctiles para esta ejecución (left, right). */
    public String pedals;

    public static DevOptions parse(String[] args) {
        DevOptions o = new DevOptions();
        if (args == null) {
            return o;
        }
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                continue;
            }
            int eq = arg.indexOf('=');
            String key = eq < 0 ? arg.substring(2) : arg.substring(2, eq);
            String value = eq < 0 ? "" : arg.substring(eq + 1);
            switch (key) {
                case "screen": o.screen = value; break;
                case "script": o.script = value; break;
                case "scenario": o.scenario = value; break;
                case "exit": o.exitAt = Float.parseFloat(value); break;
                case "abort": o.abortAt = Float.parseFloat(value); break;
                case "wexit": o.wallExitAt = Float.parseFloat(value); break;
                case "touch": o.touch = true; break;
                case "nohelp": o.hideHelp = true; break;
                case "notouch": o.noTouch = true; break;
                case "eye": {
                    String[] p = value.split(",");
                    o.eye = new float[] {Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2])};
                    break;
                }
                case "fov": o.fov = Float.parseFloat(value); break;
                case "pauseat": o.pauseAt = Float.parseFloat(value); break;
                case "paint": o.paint = value; break;
                case "hud": o.hud = value; break;
                case "pedals": o.pedals = value; break;
                case "wshots":
                    for (String s : value.split(",")) {
                        if (!s.isBlank()) {
                            o.wallShots.add(Float.parseFloat(s.trim()));
                        }
                    }
                    break;
                case "shots":
                    for (String s : value.split(",")) {
                        if (!s.isBlank()) {
                            o.shots.add(Float.parseFloat(s.trim()));
                        }
                    }
                    break;
                default: break;
            }
        }
        return o;
    }
}
