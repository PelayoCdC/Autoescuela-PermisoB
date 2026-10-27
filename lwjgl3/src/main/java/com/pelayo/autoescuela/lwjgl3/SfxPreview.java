package com.pelayo.autoescuela.lwjgl3;

import com.pelayo.autoescuela.audio.ProceduralSounds;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Escucha y mira los sonidos sintetizados sin abrir el juego: escribe cada uno
 * como WAV y una hoja de espectrogramas (tiempo en horizontal, frecuencia en
 * escala logarítmica de 30 Hz a 8 kHz) en assets/cache/sfx_preview.
 *
 * <pre>gradlew sfxPreview</pre>
 */
public final class SfxPreview {

    private static final int SR = ProceduralSounds.SAMPLE_RATE;
    private static final int W = 520;
    private static final int H = 150;

    private SfxPreview() {
    }

    public static void main(String[] args) throws IOException {
        File dir = new File(args.length > 0 ? args[0] : "assets/cache/sfx_preview");
        dir.mkdirs();
        List<String> names = ProceduralSounds.names();
        int cols = 3;
        int rows = (names.size() + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * (W + 10), rows * (H + 26), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(18, 20, 24));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        for (int k = 0; k < names.size(); k++) {
            String name = names.get(k);
            float[] buf = ProceduralSounds.synthesize(name);
            writeWav(new File(dir, name + ".wav"), buf);
            int x = (k % cols) * (W + 10) + 5;
            int y = (k / cols) * (H + 26) + 20;
            g.drawImage(spectrogram(buf), x, y, null);
            g.setColor(Color.WHITE);
            g.drawString(String.format("%s  (%.2f s)", name, buf.length / (float) SR), x, y - 5);
        }
        g.dispose();
        ImageIO.write(sheet, "png", new File(dir, "spectrograms.png"));
        System.out.println(names.size() + " sonidos en " + dir.getAbsolutePath());
    }

    /** Espectrograma: ventana de Hann de 2048 muestras, escala logarítmica de frecuencia, -80..0 dB. */
    static BufferedImage spectrogram(float[] buf) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        int size = 2048;
        double fMin = 30.0;
        double fMax = 8000.0;
        double[][] mags = new double[W][];
        double max = 1e-12;
        for (int x = 0; x < W; x++) {
            int start = (int) ((long) x * Math.max(1, buf.length - size) / W);
            double[] re = new double[size];
            double[] im = new double[size];
            for (int i = 0; i < size; i++) {
                double w = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (size - 1));
                int j = start + i;
                re[i] = j < buf.length ? buf[j] * w : 0.0;
            }
            fft(re, im);
            mags[x] = new double[H];
            for (int y = 0; y < H; y++) {
                double f = fMin * Math.pow(fMax / fMin, (double) y / (H - 1));
                int bin = (int) Math.round(f * size / SR);
                double m = Math.hypot(re[bin], im[bin]);
                mags[x][y] = m;
                max = Math.max(max, m);
            }
        }
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                double db = 20 * Math.log10(mags[x][y] / max + 1e-12);
                double v = Math.max(0.0, Math.min(1.0, (db + 80.0) / 80.0));
                img.setRGB(x, H - 1 - y, heat(v));
            }
        }
        return img;
    }

    private static int heat(double v) {
        int r = (int) (255 * Math.min(1.0, Math.max(0.0, 1.6 * v - 0.3)));
        int g = (int) (255 * Math.min(1.0, Math.max(0.0, 1.8 * v - 0.9)));
        int b = (int) (255 * Math.min(1.0, Math.max(0.0, v < 0.5 ? 1.4 * v : 1.4 * (1.0 - v))));
        return (r << 16) | (g << 8) | b;
    }

    /** FFT radix 2 en el sitio. */
    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double t = re[i];
                re[i] = re[j];
                re[j] = t;
                t = im[i];
                im[i] = im[j];
                im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wr = Math.cos(ang);
            double wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1.0;
                double ci = 0.0;
                for (int k = 0; k < len / 2; k++) {
                    double ur = re[i + k], ui = im[i + k];
                    double vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci;
                    double vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr;
                    re[i + k] = ur + vr;
                    im[i + k] = ui + vi;
                    re[i + k + len / 2] = ur - vr;
                    im[i + k + len / 2] = ui - vi;
                    double nr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = nr;
                }
            }
        }
    }

    private static void writeWav(File file, float[] buf) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(44 + buf.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + buf.length * 2).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(SR).putInt(SR * 2)
            .putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes()).putInt(buf.length * 2);
        for (float v : buf) {
            b.putShort((short) Math.round(Math.max(-1f, Math.min(1f, v)) * 32767f));
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(b.array());
        }
    }
}
