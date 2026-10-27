package com.pelayo.autoescuela.audio;

import static com.pelayo.autoescuela.audio.Dsp.SR;
import static com.pelayo.autoescuela.audio.Dsp.TAU;
import static com.pelayo.autoescuela.audio.Dsp.addBurst;
import static com.pelayo.autoescuela.audio.Dsp.addPing;
import static com.pelayo.autoescuela.audio.Dsp.addPulse;
import static com.pelayo.autoescuela.audio.Dsp.addThump;
import static com.pelayo.autoescuela.audio.Dsp.filter;
import static com.pelayo.autoescuela.audio.Dsp.filterLoop;
import static com.pelayo.autoescuela.audio.Dsp.idx;
import static com.pelayo.autoescuela.audio.Dsp.mixPeak;
import static com.pelayo.autoescuela.audio.Dsp.mixRms;
import static com.pelayo.autoescuela.audio.Dsp.noise;
import static com.pelayo.autoescuela.audio.Dsp.rnd;

import com.badlogic.gdx.math.RandomXS128;
import com.pelayo.autoescuela.audio.Dsp.Biquad;

/**
 * Recetas de síntesis de los sonidos del coche, sin E/S: devuelven muestras
 * mono a {@link Dsp#SR} Hz en -1..1 (nunca saturan).
 *
 * <p>Los bucles se construyen exactamente periódicos: toda frecuencia o
 * modulación cabe un número entero de veces en el bucle, los impulsos se
 * escriben de forma circular y los filtros se aplican "en anillo"
 * ({@link Dsp#filterLoop}); así el último valor enlaza con el primero sin
 * clic. Los efectos sueltos se normalizan por pico y los bucles por valor
 * eficaz, para que la mezcla de {@link EngineAudio} sea predecible.
 */
final class SfxRecipes {

    /** Duración aproximada de cada bucle de motor (s). */
    private static final double ENGINE_LOOP_SECONDS = 2.0;
    /** Valor eficaz de los bucles de motor y techo del limitador. */
    private static final float ENGINE_RMS = 0.17f;
    private static final float ENGINE_CEILING = 0.8f;

    private SfxRecipes() {
    }

    // ------------------------------------------------------------------ motor

    /**
     * Bucle de motor diésel de 4 cilindros (4 tiempos) a {@code refRpm}.
     * {@code onLoad}: con gas (combustión dura, más traqueteo y bramido) o con
     * poca carga (ralentí suave, retención).
     *
     * <p>Capas: impulsos de combustión a rpm/60·2 por segundo con pequeñas
     * diferencias fijas entre cilindros (el "carácter" de un 4 cilindros), su
     * paso por el escape (grave y resonancia de ~90 Hz) y por el bloque
     * (medios), el rumor de la frecuencia de explosión, el traqueteo de la
     * inyección (ruido metálico corto en cada explosión, con inyección piloto),
     * ruido mecánico de distribución y el bramido de admisión con carga.
     */
    static float[] engineLoop(float refRpm, boolean onLoad) {
        RandomXS128 rng = new RandomXS128((long) refRpm * 31L + (onLoad ? 7L : 3L));
        double cycleNominal = refRpm / 120.0; // un ciclo = 2 vueltas
        int cycles = Math.max(2, (int) Math.round(ENGINE_LOOP_SECONDS * cycleNominal));
        int n = (int) Math.round(cycles * SR / cycleNominal);
        double fc = cycles * (double) SR / n; // ciclo exacto: el bucle contiene "cycles" ciclos justos
        double ff = fc * 4.0;                 // explosiones por segundo
        double rev = fc * 2.0;                // vueltas por segundo
        int firings = cycles * 4;
        double period = (double) n / firings;
        float rpmNorm = Math.max(0f, Math.min(1f, (refRpm - 800f) / 3200f));

        // Diferencias fijas entre cilindros: amplitud, adelanto y traqueteo
        float[] cylAmp = {1.0f, 0.88f, 1.08f, 0.93f};
        float[] cylShift = {0f, 0.028f, -0.02f, 0.012f};
        float[] cylClat = {1.0f, 1.18f, 0.84f, 1.06f};

        float[] exc = new float[n];
        float[] clat = new float[n];
        float[] pings = new float[n];
        double rise = onLoad ? 0.00035 : 0.0008;
        double decay = onLoad ? 0.0022 : 0.0034;
        for (int e = 0; e < firings; e++) {
            int c = e % 4;
            // Cada explosión algo distinta (un diésel al ralentí no es una máquina perfecta)
            int pos = (int) Math.round((e + cylShift[c] + rnd(rng) * 0.012) * period);
            double a = cylAmp[c] * (1.0 + rnd(rng) * 0.1);
            addPulse(exc, pos, a, rise, decay, true);
            double k = cylClat[c] * (1.0 + rnd(rng) * 0.15);
            addBurst(clat, pos - idx(0.0011), 0.35 * k, 0.0001, 0.0006, rng, true); // inyección piloto
            addBurst(clat, pos, k, 0.00015, onLoad ? 0.0016 : 0.0012, rng, true);
            addPing(pings, pos, 1320.0 * (1.0 + 0.025 * c), 0.5 * k, 0.003, true);
            addPing(pings, pos, 2870.0 * (1.0 - 0.02 * c), 0.28 * k, 0.0018, true);
        }

        float[] body = filterLoop(exc, Biquad.lowpass(onLoad ? 240 : 175, 0.9), Biquad.highpass(28, 0.7));
        float[] boom = filterLoop(exc, Biquad.bandpass(92, 2.5));
        float[] mid = filterLoop(exc, Biquad.bandpass(onLoad ? 540 : 430, 1.4));
        float[] clatter = filterLoop(clat, Biquad.bandpass(2300, 0.9));
        mixRms(clatter, pings, 0.6f * Dsp.rms(clatter));

        // Rumor grave: frecuencia de explosión y desequilibrio por vuelta
        float[] rumble = new float[n];
        double p1 = rng.nextFloat() * TAU;
        double p2 = rng.nextFloat() * TAU;
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            rumble[i] = (float) (Math.sin(TAU * ff * t + p1) + 0.3 * Math.sin(TAU * rev * t + p2));
        }

        // Ruido mecánico (distribución, bomba) que late con el giro
        float[] mech = filterLoop(noise(n, rng), Biquad.bandpass(1100, 0.6));
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            double m = Math.cos(Math.PI * 2.0 * rev * t);
            mech[i] *= (float) (0.7 + 0.3 * m * m);
        }

        // Bramido de admisión/escape: ruido que pulsa con cada explosión
        float[] env = filterLoop(exc, Biquad.lowpass(60, 0.7));
        Dsp.normalizePeak(env, 1f);
        float[] roarSrc = noise(n, rng);
        for (int i = 0; i < n; i++) {
            roarSrc[i] *= 0.3f + 0.7f * Math.max(0f, env[i]);
        }
        float[] roar = filterLoop(roarSrc, Biquad.lowpass(onLoad ? 900 : 650, 0.7), Biquad.highpass(60, 0.7));

        float[] out = new float[n];
        mixRms(out, body, 1.0f);
        mixRms(out, boom, 0.45f);
        mixRms(out, mid, onLoad ? 0.42f : 0.3f);
        mixRms(out, rumble, 0.35f);
        mixRms(out, clatter, (onLoad ? 0.3f : 0.22f) * (1f - 0.35f * rpmNorm));
        mixRms(out, mech, 0.07f + 0.06f * rpmNorm);
        mixRms(out, roar, onLoad ? 0.28f + 0.12f * rpmNorm : 0.07f);

        // Oído desde el asiento: el salpicadero y el aislamiento se comen los agudos (el traqueteo llega
        // apagado), el habitáculo retumba con la explosión y su segundo armónico, y se oye algo de aire
        float[] dull = filterLoop(out, Biquad.lowpass(onLoad ? 1500 : 1100, 0.6));
        float[] cabinBoom = filterLoop(exc, Biquad.bandpass(Math.max(40.0, Math.min(150.0, ff * 2.0)), 3.0));
        float[] air = filterLoop(noise(n, rng), Biquad.lowpass(2500, 0.6), Biquad.highpass(200, 0.7));
        float level = Dsp.rms(out);
        for (int i = 0; i < n; i++) {
            out[i] = 0.78f * dull[i] + 0.22f * out[i];
        }
        mixRms(out, cabinBoom, 0.32f * level);
        mixRms(out, air, 0.05f * level);
        Dsp.removeDc(out);
        Dsp.normalizeRms(out, ENGINE_RMS);
        Dsp.softLimit(out, ENGINE_CEILING);
        return out;
    }

    /** Silbido del turbo (bucle de 1 s): tono de álabes y soplido; el tono lo sube la mezcla. */
    static float[] turbo() {
        RandomXS128 rng = new RandomXS128(21);
        int n = SR;
        float[] whistle = new float[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            double flutter = 1.0 + 0.1 * Math.sin(TAU * 7 * t);
            whistle[i] = (float) (flutter * (Math.sin(TAU * 2600 * t) + 0.3 * Math.sin(TAU * 3900 * t + 1.0)));
        }
        float[] hiss = filterLoop(noise(n, rng), Biquad.bandpass(3400, 1.5));
        float[] whoosh = filterLoop(noise(n, rng), Biquad.bandpass(900, 0.7));
        float[] out = new float[n];
        mixRms(out, whistle, 0.5f);
        mixRms(out, hiss, 0.6f);
        mixRms(out, whoosh, 0.5f);
        Dsp.normalizeRms(out, 0.14f);
        Dsp.softLimit(out, 0.7f);
        return out;
    }

    /** Rodadura de neumáticos (bucle de 2 s): retumbo del firme, banda del neumático y algo de siseo. */
    static float[] road() {
        RandomXS128 rng = new RandomXS128(31);
        int n = 2 * SR;
        float[] rumble = filterLoop(noise(n, rng), Biquad.lowpass(140, 0.7), Biquad.highpass(30, 0.7));
        float[] tyre = filterLoop(noise(n, rng), Biquad.bandpass(850, 0.8));
        float[] hiss = filterLoop(noise(n, rng), Biquad.bandpass(2500, 0.8));
        float[] out = new float[n];
        mixRms(out, rumble, 1.0f);
        mixRms(out, tyre, 0.55f);
        mixRms(out, hiss, 0.15f);
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            out[i] *= (float) (1.0 + 0.12 * Math.sin(TAU * 3 * t) + 0.08 * Math.sin(TAU * 7 * t + 2.0));
        }
        Dsp.normalizeRms(out, 0.17f);
        Dsp.softLimit(out, 0.75f);
        return out;
    }

    /** Viento (bucle de 2 s): soplido de banda media con rachas lentas. */
    static float[] wind() {
        RandomXS128 rng = new RandomXS128(41);
        int n = 2 * SR;
        float[] body = filterLoop(noise(n, rng), Biquad.bandpass(450, 0.6));
        float[] high = filterLoop(noise(n, rng), Biquad.bandpass(1400, 0.8));
        float[] buffet = filterLoop(noise(n, rng), Biquad.lowpass(120, 0.7), Biquad.highpass(25, 0.7));
        float[] out = new float[n];
        mixRms(out, body, 1.0f);
        mixRms(out, high, 0.35f);
        mixRms(out, buffet, 0.4f);
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            out[i] *= (float) (1.0 + 0.18 * Math.sin(TAU * 0.5 * t) + 0.1 * Math.sin(TAU * 1.5 * t + 1.0));
        }
        Dsp.normalizeRms(out, 0.17f);
        Dsp.softLimit(out, 0.75f);
        return out;
    }

    /** Chirrido de neumáticos (bucle de 1 s): tono inestable ~1,1 kHz con armónicos y algo de ruido. */
    static float[] skid() {
        RandomXS128 rng = new RandomXS128(51);
        int n = SR;
        float[] wobble = filterLoop(noise(n, rng), Biquad.lowpass(15, 0.7));
        Dsp.normalizePeak(wobble, 1f);
        float[] rough = filterLoop(noise(n, rng), Biquad.lowpass(80, 0.7));
        Dsp.normalizePeak(rough, 1f);
        double f0 = 1150.0;
        double[] phase = new double[n + 1];
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            double f = f0 * (1.0 + 0.02 * Math.sin(TAU * 5 * t) + 0.012 * Math.sin(TAU * 13 * t + 1.0)
                + 0.02 * wobble[i]);
            phase[i + 1] = phase[i] + f / SR;
        }
        // Corrige la fase para que el bucle contenga un número entero de ciclos
        double err = phase[n] - Math.round(phase[n]);
        float[] tone = new float[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            double ph = phase[i] - err * i / n;
            double am = 0.75 + 0.25 * Math.sin(TAU * 31 * t) + 0.2 * rough[i];
            tone[i] = (float) (am * (Math.sin(TAU * ph) + 0.3 * Math.sin(TAU * 2 * ph) + 0.12 * Math.sin(TAU * 3 * ph)));
        }
        float[] band = filterLoop(noise(n, rng), Biquad.bandpass(1800, 2.0));
        float[] out = new float[n];
        mixRms(out, tone, 1.0f);
        mixRms(out, band, 0.35f);
        Dsp.normalizeRms(out, 0.15f);
        Dsp.softLimit(out, 0.7f);
        return out;
    }

    /** Silbido de engranajes (marcha atrás de dientes rectos), bucle de 1 s a ~730 Hz. */
    static float[] gearWhine() {
        int n = SR;
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            double am = 0.85 + 0.15 * Math.sin(TAU * 3 * t);
            out[i] = (float) (am * (Math.sin(TAU * 730 * t) + 0.25 * Math.sin(TAU * 1460 * t + 0.5)
                + 0.08 * Math.sin(TAU * 2190 * t + 1.3)));
        }
        Dsp.normalizeRms(out, 0.15f);
        Dsp.softLimit(out, 0.7f);
        return out;
    }

    // -------------------------------------------------------- arranque/parada

    /**
     * Motor de arranque (1,5 s; la mezcla lo corta con fundido cuando el motor
     * prende): golpe del solenoide, piñón que engrana y el "rrr-rrr" del motor
     * eléctrico, que se frena y gruñe en cada compresión.
     */
    static float[] starter() {
        RandomXS128 rng = new RandomXS128(11);
        int n = idx(1.5);
        float[] motor = new float[n];
        float[] buzz = noise(n, rng);
        float[] exc = new float[n];
        double phC = 0.3;
        double phM = 0.0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            double crank = 230.0 * (1.0 - Math.exp(-t / 0.10)) + 30.0 * t; // rpm del cigüeñal arrastrado
            double s = Math.sin(TAU * phC);
            double c = s > 0 ? s * s * s : 0.0;                          // carga de compresión
            double eff = crank * (1.0 - 0.35 * c); // cada compresión frena el arrastre: "rr-rr-rr"
            double before = phC;
            phC += eff / 60.0 * 2.0 / SR;                                 // 2 compresiones por vuelta
            if (Math.floor(before + 0.5) != Math.floor(phC + 0.5)) {
                addPulse(exc, i, 1.0, 0.002, 0.012, false);               // paso por el PMS
            }
            phM += eff / 60.0 * 117.0 / SR;                               // piñón 9 dientes, corona 13:1
            double saw = 0.0;
            for (int k = 1; k <= 6; k++) {
                saw += Math.sin(TAU * k * phM) / k * (k % 2 == 0 ? 0.6 : 1.0);
            }
            double spin = Math.min(1.0, t / 0.06);
            motor[i] = (float) (saw * spin * (0.55 + 0.45 * c));
            buzz[i] *= (float) (spin * (0.4 + 0.6 * c));
        }
        float[] chug = filter(exc, Biquad.lowpass(250, 0.8));
        float[] chugBoom = filter(exc, Biquad.bandpass(90, 2.0));
        float[] buzzF = filter(buzz, Biquad.bandpass(2200, 1.5));
        float[] out = new float[n];
        mixRms(out, filter(motor, Biquad.lowpass(2500, 0.7)), 1.0f);
        mixRms(out, buzzF, 0.18f);
        mixRms(out, chug, 0.7f);
        mixRms(out, chugBoom, 0.4f);
        // Solenoide y piñón al principio
        float[] clunk = new float[n];
        addThump(clunk, 0, 160, 120, 0.02, 1.0, 0.03);
        addPing(clunk, 0, 2600, 0.5, 0.006, false);
        addPing(clunk, idx(0.02), 1900, 0.4, 0.005, false);
        addPing(clunk, idx(0.02), 3400, 0.25, 0.003, false);
        mixPeak(out, clunk, 1.1f * Dsp.peak(out));
        // Desde el asiento: más apagado, con el retumbo de cada compresión en el habitáculo
        float[] dullStart = filter(out, Biquad.lowpass(1800, 0.6));
        for (int i = 0; i < n; i++) {
            out[i] = 0.7f * dullStart[i] + 0.3f * out[i];
        }
        mixRms(out, filter(exc, Biquad.bandpass(55, 2.0)), 0.25f * Dsp.rms(out));
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            out[i] *= (float) (1.0 - Dsp.smooth(t, 1.2, 1.5));
        }
        Dsp.fadeEdges(out, 0.0005, 0.01);
        Dsp.normalizePeak(out, 0.7f);
        return out;
    }

    /**
     * Motor que se para: explosiones cada vez más espaciadas desde
     * {@code startRpm} hasta pararse en {@code stopTime}; con combustión
     * (calado: tirones irregulares con fallos) o sin ella (llave: solo
     * compresiones), y la sacudida final del motor sobre sus soportes.
     */
    private static float[] spinDown(long seed, double startRpm, double stopTime, boolean combustion,
                                    double shudderAt, double shudderAmp, double length) {
        RandomXS128 rng = new RandomXS128(seed);
        int n = idx(length);
        float[] exc = new float[n];
        float[] clat = new float[n];
        boolean[] misfire = {false, false, true, false, true, false, true, true};
        double ph = 0.0;
        int count = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            double rpm = startRpm * Math.pow(Math.max(0.0, 1.0 - t / stopTime), 0.7);
            ph += rpm / 30.0 / SR; // explosiones por segundo = rpm / 30
            if (ph >= 1.0) {
                ph -= 1.0;
                count++;
                double fade = t / stopTime;
                boolean fire = combustion && fade < 0.75 && !misfire[count % misfire.length];
                if (fire) {
                    double a = (1.0 - 0.4 * fade) * (1.0 + rnd(rng) * 0.15);
                    addPulse(exc, i, a, 0.0004, 0.003, false);
                    addBurst(clat, i, a, 0.00015, 0.0015, rng, false);
                    addPing(clat, i, 1350, 0.4 * a, 0.003, false);
                } else {
                    addPulse(exc, i, 0.35 * (1.0 - 0.5 * fade), 0.002, 0.01, false);
                }
            }
        }
        float[] out = new float[n];
        mixPeak(out, filter(exc, Biquad.lowpass(180, 0.9), Biquad.highpass(25, 0.7)), 1.0f);
        mixPeak(out, filter(exc, Biquad.bandpass(85, 2.5)), 0.5f);
        if (combustion) {
            mixPeak(out, filter(clat, Biquad.bandpass(2300, 0.9)), 0.3f);
        }
        float[] shudder = new float[n];
        int s0 = idx(shudderAt);
        addThump(shudder, s0, 52, 34, 0.12, 1.0, 0.11);
        addPing(shudder, s0 + idx(0.01), 120, 0.4, 0.05, false);
        float[] rattle = new float[n];
        for (int k = 0; k < 8; k++) {
            addBurst(rattle, s0 + idx(0.01 + 0.15 * rng.nextFloat()), 0.5 + 0.5 * rng.nextFloat(),
                0.0002, 0.003, rng, false);
        }
        mixPeak(shudder, filter(rattle, Biquad.bandpass(2400, 1.2)), 0.12f);
        mixPeak(out, shudder, (float) shudderAmp);
        Dsp.fadeEdges(out, 0.0005, 0.03);
        Dsp.normalizePeak(out, 0.75f);
        return out;
    }

    /** Calado: el motor se ahoga a tirones y se para con una sacudida. */
    static float[] stall() {
        return spinDown(61, 480, 0.62, true, 0.55, 0.9, 1.1);
    }

    /** Parada con la llave: sin combustión, las compresiones se espacian y una sacudida suave. */
    static float[] engineOff() {
        return spinDown(71, 800, 0.7, false, 0.62, 0.55, 1.0);
    }

    // ---------------------------------------------------------------- caja

    /**
     * Rascada: los dientes del sincronizador chocan cientos de veces por
     * segundo, cada vez más despacio, con resonancias metálicas y un gruñido
     * grave; con vibración irregular.
     */
    static float[] grind() {
        RandomXS128 rng = new RandomXS128(81);
        double len = 0.55;
        int n = idx(len);
        float[] metal = new float[n];
        float[] hits = new float[n];
        float[] growl = new float[n];
        double ph = 0.0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            double rate = 250.0 - 130.0 * t / len;
            ph += rate / SR;
            growl[i] = (float) (2.0 * (ph - Math.floor(ph)) - 1.0);
            if (ph >= 1.0) {
                ph -= 1.0 + rnd(rng) * 0.2;
                double a = (0.5 + 0.5 * rng.nextFloat()) * (0.6 + 0.4 * Math.abs(Math.sin(TAU * 17 * t)));
                double d = 1.0 + rnd(rng) * 0.04;
                addPing(metal, i, 2100 * d, a, 0.004, false);
                addPing(metal, i, 3150 * d, 0.6 * a, 0.003, false);
                addPing(metal, i, 4400 * d, 0.35 * a, 0.002, false);
                addPing(metal, i, 5900 * d, 0.12 * a, 0.0015, false);
                addBurst(hits, i, 0.6 * a, 0.0001, 0.001, rng, false);
            }
        }
        float[] out = new float[n];
        mixPeak(out, metal, 1.0f);
        mixPeak(out, filter(hits, Biquad.bandpass(3000, 1.0)), 0.5f);
        mixPeak(out, filter(growl, Biquad.lowpass(600, 0.8)), 0.45f);
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            out[i] *= (float) (Math.min(1.0, t / 0.008) * (1.0 - Dsp.smooth(t, 0.33, len)));
        }
        out = filter(out, Biquad.highpass(120, 0.7));
        Dsp.fadeEdges(out, 0.0005, 0.01);
        Dsp.normalizePeak(out, 0.75f);
        return out;
    }

    /** Palanca de cambios: "clic" al salir de la marcha y "clonc" amortiguado al entrar. */
    static float[] gearShift() {
        RandomXS128 rng = new RandomXS128(91);
        int n = idx(0.3);
        float[] out = new float[n];
        float[] click = new float[n];
        addBurst(click, 0, 0.4, 0.0001, 0.0025, rng, false);
        int in = idx(0.075);
        addBurst(click, in, 0.35, 0.0001, 0.003, rng, false);
        addPing(click, idx(0.038), 3200, 0.25, 0.0015, false); // la palanca pasa por punto muerto (rejilla)
        mixPeak(out, filter(click, Biquad.bandpass(2200, 1.0)), 0.35f);
        float[] thud = new float[n];
        addThump(thud, 0, 170, 140, 0.02, 0.35, 0.018);
        addThump(thud, in, 125, 100, 0.02, 1.0, 0.035);
        addPing(thud, in, 260, 0.5, 0.025, false);
        addPing(thud, in, 620, 0.25, 0.012, false);
        mixPeak(out, thud, 1.0f);
        float[] boot = filter(noise(n, rng), Biquad.lowpass(400, 0.7));
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            boot[i] *= (float) (Dsp.smooth(t, 0.0, 0.03) * (1.0 - Dsp.smooth(t, 0.05, 0.1)));
        }
        mixPeak(out, boot, 0.16f); // fuelle de goma de la palanca
        Dsp.fadeEdges(out, 0.0005, 0.02);
        Dsp.normalizePeak(out, 0.7f);
        return out;
    }

    // ---------------------------------------------------------------- mandos

    /** Freno de mano al subirlo: carraca del trinquete, tensión del cable y tope. */
    static float[] handbrakeOn() {
        RandomXS128 rng = new RandomXS128(101);
        int n = idx(0.5);
        float[] clicks = new float[n];
        float[] grit = new float[n];
        double[] gaps = {0.05, 0.045, 0.042, 0.04, 0.042, 0.048, 0.06};
        double t = 0.03;
        for (double g : gaps) {
            int i = idx(t);
            double a = 0.7 + 0.3 * rng.nextFloat();
            addPing(clicks, i, 3300, a, 0.0022, false);
            addPing(clicks, i, 5100, 0.45 * a, 0.0015, false);
            addPing(clicks, i, 6900, 0.15 * a, 0.001, false);
            addPing(clicks, i, 420, 0.25 * a, 0.006, false);
            addBurst(grit, i, 0.5 * a, 0.0001, 0.0008, rng, false);
            t += g;
        }
        float[] out = new float[n];
        mixPeak(out, clicks, 1.0f);
        mixPeak(out, filter(grit, Biquad.bandpass(4000, 1.0)), 0.4f);
        float[] creak = filter(noise(n, rng), Biquad.lowpass(300, 0.8));
        for (int i = 0; i < n; i++) {
            double s = (double) i / SR;
            creak[i] *= (float) (Dsp.smooth(s, 0.0, 0.3) * (1.0 - Dsp.smooth(s, 0.33, 0.4)));
        }
        mixPeak(out, creak, 0.12f);
        float[] stop = new float[n];
        addThump(stop, idx(t), 200, 170, 0.02, 1.0, 0.025);
        mixPeak(out, stop, 0.45f);
        Dsp.fadeEdges(out, 0.0005, 0.02);
        Dsp.normalizePeak(out, 0.65f);
        return out;
    }

    /** Freno de mano al quitarlo: botón, el trinquete se suelta y la palanca baja con un golpe. */
    static float[] handbrakeOff() {
        RandomXS128 rng = new RandomXS128(111);
        int n = idx(0.35);
        float[] out = new float[n];
        float[] button = new float[n];
        addBurst(button, 0, 0.6, 0.0001, 0.002, rng, false);
        mixPeak(out, filter(button, Biquad.bandpass(2000, 1.2)), 0.4f);
        float[] metal = new float[n];
        addPing(metal, 0, 1800, 0.5, 0.003, false);
        addPing(metal, idx(0.05), 3000, 0.4, 0.002, false);
        mixPeak(out, metal, 0.4f);
        float[] clunk = new float[n];
        int d = idx(0.13);
        addThump(clunk, d, 160, 135, 0.02, 1.0, 0.04);
        addPing(clunk, d, 330, 0.4, 0.02, false);
        float[] rattle = new float[n];
        for (int k = 0; k < 4; k++) {
            addBurst(rattle, d + idx(0.005 + 0.05 * rng.nextFloat()), 0.4, 0.0001, 0.002, rng, false);
        }
        mixPeak(clunk, filter(rattle, Biquad.bandpass(2600, 1.2)), 0.15f);
        mixPeak(out, clunk, 1.0f);
        Dsp.fadeEdges(out, 0.0005, 0.02);
        Dsp.normalizePeak(out, 0.65f);
        return out;
    }

    /** Relé del intermitente: "tic" (cierra) o "tac" (abre, algo más grave y suave). */
    static float[] indicator(boolean on) {
        RandomXS128 rng = new RandomXS128(on ? 121 : 131);
        int n = idx(0.06);
        float[] out = new float[n];
        float[] snap = new float[n];
        addBurst(snap, 0, 1.0, 0.00005, 0.0005, rng, false);
        mixPeak(out, filter(snap, Biquad.highpass(1500, 0.7)), 0.6f);
        float[] body = new float[n];
        if (on) {
            addPing(body, 0, 2400, 0.6, 0.004, false);
            addPing(body, 0, 4700, 0.3, 0.002, false);
            addPing(body, 0, 900, 0.25, 0.006, false);
        } else {
            addPing(body, 0, 1900, 0.6, 0.005, false);
            addPing(body, 0, 3600, 0.25, 0.0025, false);
            addPing(body, 0, 700, 0.3, 0.007, false);
        }
        mixPeak(out, body, 1.0f);
        Dsp.fadeEdges(out, 0.0001, 0.01);
        Dsp.normalizePeak(out, on ? 0.6f : 0.5f);
        return out;
    }

    // ------------------------------------------------------------- golpes

    /** Rueda que sube o baja un bordillo: golpe de neumático, suspensión y algo de traqueteo. */
    static float[] curb() {
        RandomXS128 rng = new RandomXS128(141);
        int n = idx(0.45);
        float[] out = new float[n];
        float[] thump = new float[n];
        addThump(thump, 0, 100, 55, 0.03, 1.0, 0.07);
        addPing(thump, idx(0.01), 190, 0.45, 0.035, false);
        addPing(thump, idx(0.01), 420, 0.2, 0.015, false);
        mixPeak(out, thump, 1.0f);
        float[] scrape = filter(noise(n, rng), Biquad.lowpass(900, 0.7));
        for (int i = 0; i < n; i++) {
            scrape[i] *= (float) Math.exp(-(double) i / SR / 0.05);
        }
        mixPeak(out, scrape, 0.3f);
        float[] rattle = new float[n];
        for (int k = 0; k < 6; k++) {
            addBurst(rattle, idx(0.02 + 0.16 * rng.nextFloat()), 0.5 + 0.5 * rng.nextFloat(), 0.0001, 0.002, rng, false);
        }
        mixPeak(out, filter(rattle, Biquad.bandpass(2500, 1.2)), 0.12f);
        Dsp.fadeEdges(out, 0.0005, 0.03);
        Dsp.normalizePeak(out, 0.75f);
        return out;
    }

    /**
     * Choque del coche contra una valla u otro coche, oído desde dentro: golpe grave de la masa del coche,
     * la chapa que vibra (parciales inarmónicos de un panel), el plástico del paragolpes que se agrieta
     * (crepitar denso al principio), un crujido de deformación y trocitos que caen después.
     */
    static float[] impact() {
        RandomXS128 rng = new RandomXS128(152);
        int n = idx(0.9);
        float[] out = new float[n];
        float[] mass = new float[n];
        addThump(mass, 0, 95, 42, 0.035, 1.0, 0.06);
        addThump(mass, idx(0.012), 150, 70, 0.02, 0.5, 0.05);
        mixPeak(out, mass, 1.0f);

        float[] panel = new float[n];
        double[] modes = {176, 287, 431, 619, 862, 1190, 1605};
        for (int k = 0; k < modes.length; k++) {
            double f = modes[k] * (1.0 + rnd(rng) * 0.03);
            addPing(panel, idx(0.002 + 0.004 * rng.nextFloat()), f, 1.0 / (1.0 + 0.6 * k), 0.07 - 0.007 * k, false);
        }
        mixPeak(out, panel, 0.55f);

        // Plástico que se agrieta: muchos chasquidos cortos, densos al principio
        float[] crackle = new float[n];
        for (int k = 0; k < 160; k++) {
            double u = rng.nextFloat();
            double t = 0.15 * u * u;
            double a = (1.0 - t / 0.17) * (0.4 + 0.6 * rng.nextFloat());
            addBurst(crackle, idx(t), a, 0.00004, 0.0003 + 0.0009 * rng.nextFloat(), rng, false);
        }
        mixPeak(out, filter(crackle, Biquad.highpass(700, 0.7), Biquad.lowpass(5500, 0.7)), 0.8f);

        float[] crunch = noise(n, rng);
        for (int i = 0; i < n; i++) {
            crunch[i] *= (float) Math.exp(-(double) i / SR / 0.06);
        }
        mixPeak(out, filter(crunch, Biquad.bandpass(520, 0.7)), 0.5f);

        // Trocitos (plástico, cristal de un piloto) que caen y rebotan
        float[] debris = new float[n];
        for (int k = 0; k < 16; k++) {
            double t = 0.07 + 0.5 * rng.nextFloat();
            double a = (1.0 - t) * (0.3 + 0.7 * rng.nextFloat());
            addPing(debris, idx(t), 2800 + 4200 * rng.nextFloat(), a, 0.002 + 0.002 * rng.nextFloat(), false);
            addBurst(debris, idx(t), 0.4 * a, 0.00005, 0.0006, rng, false);
        }
        mixPeak(out, debris, 0.16f);

        float[] rattle = new float[n];
        for (int k = 0; k < 10; k++) {
            double t = 0.04 + 0.35 * rng.nextFloat();
            addBurst(rattle, idx(t), (1.0 - t) * (0.5 + 0.5 * rng.nextFloat()), 0.0001, 0.002, rng, false);
        }
        mixPeak(out, filter(rattle, Biquad.bandpass(1800, 1.2)), 0.1f);

        // Desde dentro: lo más agudo llega algo apagado
        float[] dull = filter(out, Biquad.lowpass(4200, 0.7));
        for (int i = 0; i < n; i++) {
            out[i] = 0.75f * dull[i] + 0.25f * out[i];
        }
        Dsp.fadeEdges(out, 0.0003, 0.06);
        Dsp.normalizePeak(out, 0.85f);
        return out;
    }

    /** Cono golpeado: "bonc" hueco de plástico y el cono que roza al caer. */
    static float[] cone() {
        RandomXS128 rng = new RandomXS128(161);
        int n = idx(0.4);
        float[] out = new float[n];
        float[] body = new float[n];
        addPing(body, 0, 310, 1.0, 0.07, false);
        addPing(body, 0, 740, 0.6, 0.045, false);
        addPing(body, 0, 1260, 0.35, 0.025, false);
        addPing(body, 0, 2100, 0.2, 0.012, false);
        mixPeak(out, body, 1.0f);
        float[] tap = new float[n];
        addBurst(tap, 0, 1.0, 0.0001, 0.004, rng, false);
        mixPeak(out, filter(tap, Biquad.bandpass(1500, 1.0)), 0.4f);
        float[] scrape = filter(noise(n, rng), Biquad.lowpass(1200, 0.7));
        for (int i = 0; i < n; i++) {
            double t = (double) i / SR;
            scrape[i] *= (float) (Math.exp(-Math.pow((t - 0.12) / 0.02, 2)) + 0.7 * Math.exp(-Math.pow((t - 0.22) / 0.025, 2)));
        }
        mixPeak(out, scrape, 0.2f);
        Dsp.fadeEdges(out, 0.0005, 0.03);
        Dsp.normalizePeak(out, 0.7f);
        return out;
    }
}
