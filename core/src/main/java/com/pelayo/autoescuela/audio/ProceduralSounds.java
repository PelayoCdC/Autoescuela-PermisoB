package com.pelayo.autoescuela.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Sonidos del coche sintetizados en código ({@link SfxRecipes}) y guardados
 * como WAV mono de 16 bits en {@code cache/sfx/v<VERSION>/} la primera vez.
 * Si cambia la síntesis, se sube {@link #VERSION} y se regeneran (los de la
 * versión anterior se borran). {@link EngineAudio} prefiere muestras grabadas
 * en {@code assets/sounds/<nombre>.ogg|wav|mp3} si existen.
 */
public final class ProceduralSounds {

    public static final int SAMPLE_RATE = Dsp.SR;
    /** Versión de la síntesis: forma parte de la ruta de la caché. */
    public static final int VERSION = 3;
    private static final String CACHE_ROOT = "cache/sfx";
    private static final String CACHE_DIR = CACHE_ROOT + "/v" + VERSION + "/";

    /** rpm de referencia de los bucles de motor (con fundido cruzado entre ellos). */
    public static final int[] ENGINE_REFS = {800, 1500, 2500, 4000};

    public static final String TURBO = "turbo";
    public static final String ROAD = "road";
    public static final String WIND = "wind";
    public static final String SKID = "skid";
    public static final String GEAR_WHINE = "gear_whine";
    public static final String STARTER = "starter";
    public static final String STALL = "stall";
    public static final String ENGINE_OFF = "engine_off";
    public static final String GRIND = "grind";
    public static final String GEAR_SHIFT = "gear_shift";
    public static final String HANDBRAKE_ON = "handbrake_on";
    public static final String HANDBRAKE_OFF = "handbrake_off";
    public static final String INDICATOR_ON = "indicator_on";
    public static final String INDICATOR_OFF = "indicator_off";
    public static final String CURB = "curb";
    public static final String IMPACT = "impact";
    public static final String CONE = "cone";

    private static final List<String> NAMES;

    static {
        List<String> names = new ArrayList<>();
        for (int rpm : ENGINE_REFS) {
            names.add(engineName(rpm, true));
            names.add(engineName(rpm, false));
        }
        names.addAll(Arrays.asList(TURBO, ROAD, WIND, SKID, GEAR_WHINE, STARTER, STALL, ENGINE_OFF, GRIND,
            GEAR_SHIFT, HANDBRAKE_ON, HANDBRAKE_OFF, INDICATOR_ON, INDICATOR_OFF, CURB, IMPACT, CONE));
        NAMES = Collections.unmodifiableList(names);
    }

    private ProceduralSounds() {
    }

    /** Nombre del bucle de motor a esas rpm, con carga ({@code _on}) o sin ella ({@code _off}). */
    public static String engineName(int rpm, boolean onLoad) {
        return "engine_" + rpm + (onLoad ? "_on" : "_off");
    }

    /** Todos los sonidos que se saben sintetizar. */
    public static List<String> names() {
        return NAMES;
    }

    /** true si el sonido se reproduce en bucle (debe enlazar sin corte). */
    public static boolean isLoop(String name) {
        return name.startsWith("engine_") && !name.equals(ENGINE_OFF)
            || name.equals(TURBO) || name.equals(ROAD) || name.equals(WIND)
            || name.equals(SKID) || name.equals(GEAR_WHINE);
    }

    /** Sintetiza el sonido (sin tocar disco). */
    public static float[] synthesize(String name) {
        switch (name) {
            case TURBO: return SfxRecipes.turbo();
            case ROAD: return SfxRecipes.road();
            case WIND: return SfxRecipes.wind();
            case SKID: return SfxRecipes.skid();
            case GEAR_WHINE: return SfxRecipes.gearWhine();
            case STARTER: return SfxRecipes.starter();
            case STALL: return SfxRecipes.stall();
            case ENGINE_OFF: return SfxRecipes.engineOff();
            case GRIND: return SfxRecipes.grind();
            case GEAR_SHIFT: return SfxRecipes.gearShift();
            case HANDBRAKE_ON: return SfxRecipes.handbrakeOn();
            case HANDBRAKE_OFF: return SfxRecipes.handbrakeOff();
            case INDICATOR_ON: return SfxRecipes.indicator(true);
            case INDICATOR_OFF: return SfxRecipes.indicator(false);
            case CURB: return SfxRecipes.curb();
            case IMPACT: return SfxRecipes.impact();
            case CONE: return SfxRecipes.cone();
            default:
                for (int rpm : ENGINE_REFS) {
                    if (name.equals(engineName(rpm, true))) {
                        return SfxRecipes.engineLoop(rpm, true);
                    }
                    if (name.equals(engineName(rpm, false))) {
                        return SfxRecipes.engineLoop(rpm, false);
                    }
                }
                throw new IllegalArgumentException("Sonido desconocido: " + name);
        }
    }

    /** Fichero de la caché para ese sonido (puede no existir aún). */
    public static FileHandle cacheFile(String name) {
        return Gdx.files.local(CACHE_DIR + name + ".wav");
    }

    /** El WAV del sonido, sintetizándolo si no está en la caché. */
    public static FileHandle get(String name) {
        ensure(Collections.singletonList(name));
        return cacheFile(name);
    }

    /**
     * Sintetiza (en paralelo, con varios hilos) los sonidos de la lista que no
     * estén ya en la caché. Solo calcula y escribe ficheros; los
     * {@code Sound} se crean después en el hilo de la aplicación.
     */
    public static void ensure(Collection<String> names) {
        Set<String> missing = new LinkedHashSet<>();
        for (String name : names) {
            if (!cacheFile(name).exists()) {
                missing.add(name);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        deleteOldVersions();
        int threads = Math.max(1, Math.min(missing.size(), Math.min(4, Runtime.getRuntime().availableProcessors())));
        if (threads == 1) {
            for (String name : missing) {
                write(cacheFile(name), synthesize(name));
            }
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "sfx-synth");
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<?>> jobs = new ArrayList<>();
            for (String name : missing) {
                jobs.add(pool.submit(() -> write(cacheFile(name), synthesize(name))));
            }
            for (Future<?> job : jobs) {
                job.get();
            }
        } catch (Exception e) {
            throw new RuntimeException("No se pudieron sintetizar los sonidos", e);
        } finally {
            pool.shutdown();
        }
    }

    /** Borra los WAV de versiones anteriores de la síntesis (cache/sfx/*.wav y cache/sfx/v<n>/). */
    private static void deleteOldVersions() {
        try {
            FileHandle root = Gdx.files.local(CACHE_ROOT);
            if (!root.isDirectory()) {
                return;
            }
            for (FileHandle f : root.list()) {
                if (f.isDirectory()) {
                    if (f.name().startsWith("v") && !f.name().equals("v" + VERSION)) {
                        f.deleteDirectory();
                    }
                } else if (f.extension().equalsIgnoreCase("wav")) {
                    f.delete();
                }
            }
        } catch (Exception e) {
            if (Gdx.app != null) {
                Gdx.app.error("ProceduralSounds", "No se pudo limpiar la caché antigua", e);
            }
        }
    }

    /** Escribe un WAV PCM mono de 16 bits (primero a .tmp y luego renombra, por si se corta). */
    static void write(FileHandle file, float[] buf) {
        int dataLen = buf.length * 2;
        byte[] b = new byte[44 + dataLen];
        putAscii(b, 0, "RIFF");
        putInt(b, 4, 36 + dataLen);
        putAscii(b, 8, "WAVE");
        putAscii(b, 12, "fmt ");
        putInt(b, 16, 16);
        putShort(b, 20, 1);  // PCM
        putShort(b, 22, 1);  // mono
        putInt(b, 24, SAMPLE_RATE);
        putInt(b, 28, SAMPLE_RATE * 2);
        putShort(b, 32, 2);
        putShort(b, 34, 16);
        putAscii(b, 36, "data");
        putInt(b, 40, dataLen);
        int o = 44;
        for (float v : buf) {
            float c = Math.max(-1f, Math.min(1f, v));
            putShort(b, o, Math.round(c * 32767f));
            o += 2;
        }
        FileHandle tmp = file.sibling(file.name() + ".tmp");
        tmp.parent().mkdirs();
        tmp.writeBytes(b, false);
        tmp.moveTo(file);
    }

    private static void putAscii(byte[] b, int o, String s) {
        for (int i = 0; i < s.length(); i++) {
            b[o + i] = (byte) s.charAt(i);
        }
    }

    private static void putInt(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
        b[o + 2] = (byte) (v >> 16);
        b[o + 3] = (byte) (v >> 24);
    }

    private static void putShort(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
    }
}
