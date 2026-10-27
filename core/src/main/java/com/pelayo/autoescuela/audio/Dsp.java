package com.pelayo.autoescuela.audio;

import com.badlogic.gdx.math.RandomXS128;

/**
 * Utilidades de síntesis para {@link SfxRecipes}: filtros biquad, ruido,
 * impulsos y "pings" que se escriben de forma circular (para bucles), y
 * normalización/limitación de nivel. Todo trabaja con muestras mono en
 * float (-1..1) a {@link #SR} Hz.
 */
final class Dsp {

    static final int SR = 44100;
    static final double TAU = Math.PI * 2.0;

    private Dsp() {
    }

    /** Muestras que ocupan esos segundos. */
    static int idx(double seconds) {
        return (int) Math.round(seconds * SR);
    }

    /** Aleatorio uniforme en -1..1. */
    static float rnd(RandomXS128 rng) {
        return rng.nextFloat() * 2f - 1f;
    }

    static float[] noise(int n, RandomXS128 rng) {
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            out[i] = rnd(rng);
        }
        return out;
    }

    /** Biquad de las fórmulas RBJ (forma directa I). Tiene estado: uno por pasada. */
    static final class Biquad {
        private final float b0;
        private final float b1;
        private final float b2;
        private final float a1;
        private final float a2;
        private float x1;
        private float x2;
        private float y1;
        private float y2;

        private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = (float) (b0 / a0);
            this.b1 = (float) (b1 / a0);
            this.b2 = (float) (b2 / a0);
            this.a1 = (float) (a1 / a0);
            this.a2 = (float) (a2 / a0);
        }

        static Biquad lowpass(double freq, double q) {
            double w = TAU * freq / SR;
            double c = Math.cos(w);
            double al = Math.sin(w) / (2.0 * q);
            return new Biquad((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al);
        }

        static Biquad highpass(double freq, double q) {
            double w = TAU * freq / SR;
            double c = Math.cos(w);
            double al = Math.sin(w) / (2.0 * q);
            return new Biquad((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al);
        }

        /** Paso banda con ganancia 1 en la frecuencia central. */
        static Biquad bandpass(double freq, double q) {
            double w = TAU * freq / SR;
            double c = Math.cos(w);
            double al = Math.sin(w) / (2.0 * q);
            return new Biquad(al, 0, -al, 1 + al, -2 * c, 1 - al);
        }

        float process(float x) {
            float y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            return y;
        }
    }

    /** Filtra un sonido de una pasada (empieza en reposo). */
    static float[] filter(float[] in, Biquad... chain) {
        float[] out = in.clone();
        for (Biquad f : chain) {
            for (int i = 0; i < out.length; i++) {
                out[i] = f.process(out[i]);
            }
        }
        return out;
    }

    /**
     * Filtra un bucle como si se repitiera indefinidamente: una pasada de
     * calentamiento deja el filtro en el estado del final del bucle y la
     * segunda escribe; así la salida enlaza sin salto en el empalme.
     */
    static float[] filterLoop(float[] in, Biquad... chain) {
        float[] out = in.clone();
        for (Biquad f : chain) {
            for (float v : out) {
                f.process(v);
            }
            for (int i = 0; i < out.length; i++) {
                out[i] = f.process(out[i]);
            }
        }
        return out;
    }

    /** Índice en el buffer; -1 si se sale y no es circular. */
    private static int at(int n, int i, boolean wrap) {
        if (wrap) {
            int k = i % n;
            return k < 0 ? k + n : k;
        }
        return i >= 0 && i < n ? i : -1;
    }

    /** Seno amortiguado (resonancia metálica, golpe...) que empieza en fase 0. */
    static void addPing(float[] buf, int start, double freq, double amp, double decay, boolean wrap) {
        int n = buf.length;
        int len = Math.min(wrap ? n : n - Math.max(0, start), (int) (decay * SR * 8));
        double w = TAU * freq / SR;
        double k = Math.exp(-1.0 / (decay * SR));
        double c2 = 2.0 * Math.cos(w);
        double sPrev = 0.0;
        double s = Math.sin(w);
        double env = amp;
        // j = 0 vale 0 (seno en fase 0)
        for (int j = 1; j < len; j++) {
            env *= k;
            int i = at(n, start + j, wrap);
            if (i >= 0) {
                buf[i] += (float) (env * s);
            }
            double next = c2 * s - sPrev;
            sPrev = s;
            s = next;
        }
    }

    /** Ráfaga de ruido con ataque lineal y caída exponencial. */
    static void addBurst(float[] buf, int start, double amp, double attack, double decay,
                         RandomXS128 rng, boolean wrap) {
        int n = buf.length;
        int a = Math.max(1, (int) (attack * SR));
        int len = Math.min(n, a + (int) (decay * SR * 8));
        double k = Math.exp(-1.0 / (decay * SR));
        double env = amp;
        for (int j = 0; j < len; j++) {
            double e;
            if (j < a) {
                e = amp * j / a;
            } else {
                env *= k;
                e = env;
            }
            int i = at(n, start + j, wrap);
            if (i >= 0) {
                buf[i] += (float) (e * rnd(rng));
            }
        }
    }

    /** Impulso de presión: subida suave (sen²) y caída exponencial. Siempre positivo. */
    static void addPulse(float[] buf, int start, double amp, double rise, double decay, boolean wrap) {
        int n = buf.length;
        int r = Math.max(1, (int) (rise * SR));
        int len = Math.min(n, r + (int) (decay * SR * 8));
        double k = Math.exp(-1.0 / (decay * SR));
        double env = amp;
        for (int j = 0; j < len; j++) {
            double v;
            if (j < r) {
                double s = Math.sin(Math.PI * 0.5 * j / r);
                v = amp * s * s;
            } else {
                env *= k;
                v = env;
            }
            int i = at(n, start + j, wrap);
            if (i >= 0) {
                buf[i] += (float) v;
            }
        }
    }

    /** Golpe grave: seno cuya frecuencia cae de f0 a f1 (constante de tiempo sweep), amortiguado. */
    static void addThump(float[] buf, int start, double f0, double f1, double sweep, double amp, double decay) {
        int n = buf.length;
        int len = Math.min(n - Math.max(0, start), (int) (decay * SR * 8));
        double ph = 0.0;
        for (int j = 0; j < len; j++) {
            double t = (double) j / SR;
            double f = f1 + (f0 - f1) * Math.exp(-t / sweep);
            ph += f / SR;
            int i = start + j;
            if (i >= 0 && i < n) {
                double attack = Math.min(1.0, j / (0.0015 * SR));
                buf[i] += (float) (amp * attack * Math.exp(-t / decay) * Math.sin(TAU * ph));
            }
        }
    }

    static float rms(float[] b) {
        double s = 0.0;
        for (float v : b) {
            s += (double) v * v;
        }
        return (float) Math.sqrt(s / Math.max(1, b.length));
    }

    static float peak(float[] b) {
        float m = 0f;
        for (float v : b) {
            m = Math.max(m, Math.abs(v));
        }
        return m;
    }

    static void scale(float[] b, float k) {
        for (int i = 0; i < b.length; i++) {
            b[i] *= k;
        }
    }

    /** Suma src a dst escalado para que su valor eficaz sea {@code targetRms}. */
    static void mixRms(float[] dst, float[] src, float targetRms) {
        float r = rms(src);
        if (r < 1e-9f) {
            return;
        }
        float k = targetRms / r;
        for (int i = 0; i < dst.length; i++) {
            dst[i] += src[i] * k;
        }
    }

    /** Suma src a dst escalado para que su pico sea {@code targetPeak}. */
    static void mixPeak(float[] dst, float[] src, float targetPeak) {
        float p = peak(src);
        if (p < 1e-9f) {
            return;
        }
        float k = targetPeak / p;
        int n = Math.min(dst.length, src.length);
        for (int i = 0; i < n; i++) {
            dst[i] += src[i] * k;
        }
    }

    static void removeDc(float[] b) {
        double s = 0.0;
        for (float v : b) {
            s += v;
        }
        float mean = (float) (s / Math.max(1, b.length));
        for (int i = 0; i < b.length; i++) {
            b[i] -= mean;
        }
    }

    static void normalizeRms(float[] b, float targetRms) {
        float r = rms(b);
        if (r > 1e-9f) {
            scale(b, targetRms / r);
        }
    }

    static void normalizePeak(float[] b, float targetPeak) {
        float p = peak(b);
        if (p > 1e-9f) {
            scale(b, targetPeak / p);
        }
    }

    /**
     * Limitador suave sin memoria: lineal hasta el 60 % del techo y curva tanh
     * por encima, de modo que nunca pasa de {@code ceiling} (no satura en el WAV).
     */
    static void softLimit(float[] b, float ceiling) {
        float knee = 0.6f * ceiling;
        float room = ceiling - knee;
        for (int i = 0; i < b.length; i++) {
            float a = Math.abs(b[i]);
            if (a > knee) {
                float y = knee + room * (float) Math.tanh((a - knee) / room);
                b[i] = Math.copySign(y, b[i]);
            }
        }
    }

    /** Fundido de entrada y de salida (en segundos) para que un efecto no empiece ni acabe con clic. */
    static void fadeEdges(float[] b, double in, double out) {
        int fi = Math.min(b.length, Math.max(1, idx(in)));
        for (int i = 0; i < fi; i++) {
            b[i] *= (float) i / fi;
        }
        int fo = Math.min(b.length, Math.max(1, idx(out)));
        for (int j = 0; j < fo; j++) {
            b[b.length - 1 - j] *= (float) j / fo;
        }
    }

    /** Ventana coseno de 0 a 1 entre a y b. */
    static double smooth(double x, double a, double b) {
        if (x <= a) {
            return 0.0;
        }
        if (x >= b) {
            return 1.0;
        }
        return 0.5 - 0.5 * Math.cos(Math.PI * (x - a) / (b - a));
    }
}
