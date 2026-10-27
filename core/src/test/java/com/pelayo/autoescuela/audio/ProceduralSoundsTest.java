package com.pelayo.autoescuela.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Comprueba la síntesis sin escucharla: que nada sature, que los bucles
 * enlacen sin clic (el salto del final al principio no es mayor que los
 * saltos normales dentro del bucle), que los bucles de motor contengan un
 * número entero de explosiones y que los efectos empiecen y acaben en silencio.
 */
class ProceduralSoundsTest {

    @Test
    void everySoundIsSaneAndDoesNotClip() {
        for (String name : ProceduralSounds.names()) {
            float[] s = ProceduralSounds.synthesize(name);
            assertTrue(s.length > 100, name + ": vacío");
            for (float v : s) {
                assertFalse(Float.isNaN(v) || Float.isInfinite(v), name + ": NaN");
            }
            float peak = Dsp.peak(s);
            assertTrue(peak <= 0.85f, name + ": pico " + peak);
            assertTrue(peak >= 0.2f, name + ": demasiado bajo " + peak);
            assertTrue(s.length <= 2.1 * Dsp.SR, name + ": demasiado largo " + s.length);
        }
    }

    @Test
    void loopsAreSeamless() {
        for (String name : ProceduralSounds.names()) {
            if (!ProceduralSounds.isLoop(name)) {
                continue;
            }
            float[] s = ProceduralSounds.synthesize(name);
            float[] diffs = new float[s.length - 1];
            for (int i = 1; i < s.length; i++) {
                diffs[i - 1] = Math.abs(s[i] - s[i - 1]);
            }
            float seam = Math.abs(s[0] - s[s.length - 1]);
            Arrays.sort(diffs);
            float p999 = diffs[(int) (diffs.length * 0.999)];
            assertTrue(seam <= p999, name + ": salto en el empalme " + seam + " (p99,9 " + p999 + ")");
            assertEquals(0f, mean(s), 0.01f, name + ": continua");
        }
    }

    @Test
    void engineLoopsHoldWholeFiringPeriods() {
        for (int rpm : ProceduralSounds.ENGINE_REFS) {
            float[] s = ProceduralSounds.synthesize(ProceduralSounds.engineName(rpm, true));
            double cycles = s.length / (double) Dsp.SR * rpm / 120.0;
            // El bucle dura ~2 s y contiene un número entero de ciclos (salvo el redondeo a muestras)
            assertEquals(Math.round(cycles), cycles, 0.01, rpm + " rpm: ciclos " + cycles);
            assertEquals(2.0, s.length / (double) Dsp.SR, 0.1);
        }
    }

    @Test
    void oneShotsStartAndEndQuiet() {
        for (String name : ProceduralSounds.names()) {
            if (ProceduralSounds.isLoop(name)) {
                continue;
            }
            float[] s = ProceduralSounds.synthesize(name);
            assertTrue(Math.abs(s[0]) < 0.01f, name + ": empieza con " + s[0]);
            assertTrue(Math.abs(s[s.length - 1]) < 0.01f, name + ": acaba con " + s[s.length - 1]);
        }
    }

    private static float mean(float[] s) {
        double sum = 0.0;
        for (float v : s) {
            sum += v;
        }
        return (float) (sum / s.length);
    }
}
