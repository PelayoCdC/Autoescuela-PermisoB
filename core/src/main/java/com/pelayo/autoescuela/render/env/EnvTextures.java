package com.pelayo.autoescuela.render.env;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import java.util.Random;

/**
 * Texturas del entorno generadas en código (sin ficheros): asfalto, hierba,
 * baldosas de acera, fachada con ventanas, hormigón y tejado. Son
 * periódicas (el ruido se repite en el borde) para poder repetirse sin
 * costuras, y llevan mipmaps para verse bien a lo lejos.
 */
public class EnvTextures implements Disposable {

    /** Metros que cubre una repetición de cada textura. */
    public static final float ASPHALT_TILE = 6f;
    public static final float GRASS_TILE = 10f;
    public static final float SIDEWALK_TILE = 2f;
    public static final float CONCRETE_TILE = 4f;
    /** La fachada tiene 2 x 2 ventanas: 7 m de ancho y 6 m (dos plantas) de alto por repetición. */
    public static final float FACADE_TILE_W = 7f;
    public static final float FACADE_TILE_H = 6f;

    public final Texture asphalt;
    public final Texture grass;
    public final Texture sidewalk;
    public final Texture facade;
    public final Texture facadeBrick;
    public final Texture concrete;
    public final Texture roof;

    public EnvTextures(boolean highQuality) {
        int size = highQuality ? 512 : 256;
        asphalt = upload(asphalt(size));
        grass = upload(grass(size));
        sidewalk = upload(sidewalk(size));
        facade = upload(facade(size, new Color(0.86f, 0.82f, 0.74f, 1f), 11));
        facadeBrick = upload(facade(size, new Color(0.66f, 0.40f, 0.30f, 1f), 23));
        concrete = upload(concrete(size));
        roof = upload(roof(size / 2));
    }

    private static Texture upload(Pixmap pixmap) {
        Texture t = new Texture(pixmap, true);
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        t.setAnisotropicFilter(8f);
        pixmap.dispose();
        return t;
    }

    // --- Ruido periódico -------------------------------------------------------

    /** Ruido de valor periódico de período {@code cells} celdas, en [0, 1]. */
    private static final class Noise {
        private final float[] lattice;
        private final int cells;

        Noise(int cells, long seed) {
            this.cells = cells;
            Random r = new Random(seed);
            lattice = new float[cells * cells];
            for (int i = 0; i < lattice.length; i++) {
                lattice[i] = r.nextFloat();
            }
        }

        /** u, v en [0, 1): repite en el borde. */
        float at(float u, float v) {
            float x = u * cells;
            float y = v * cells;
            int x0 = (int) Math.floor(x);
            int y0 = (int) Math.floor(y);
            float fx = x - x0;
            float fy = y - y0;
            fx = fx * fx * (3f - 2f * fx);
            fy = fy * fy * (3f - 2f * fy);
            float a = get(x0, y0), b = get(x0 + 1, y0), c = get(x0, y0 + 1), d = get(x0 + 1, y0 + 1);
            return (a + (b - a) * fx) * (1f - fy) + (c + (d - c) * fx) * fy;
        }

        private float get(int x, int y) {
            x = Math.floorMod(x, cells);
            y = Math.floorMod(y, cells);
            return lattice[y * cells + x];
        }
    }

    /** Suma de octavas de ruido periódico. */
    private static float fbm(Noise[] octaves, float u, float v) {
        float sum = 0f;
        float amp = 0.5f;
        float total = 0f;
        for (Noise n : octaves) {
            sum += n.at(u, v) * amp;
            total += amp;
            amp *= 0.5f;
        }
        return sum / total;
    }

    private static Noise[] octaves(long seed, int... cells) {
        Noise[] n = new Noise[cells.length];
        for (int i = 0; i < cells.length; i++) {
            n[i] = new Noise(cells[i], seed + i * 7919L);
        }
        return n;
    }

    private static void put(Pixmap p, int x, int y, float r, float g, float b) {
        p.drawPixel(x, y, Color.rgba8888(clamp(r), clamp(g), clamp(b), 1f));
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    // --- Texturas ------------------------------------------------------------------

    private static Pixmap asphalt(int size) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        Noise[] macro = octaves(1, 3, 6);
        Noise[] grain = octaves(2, 64, 128);
        Random r = new Random(3);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float u = x / (float) size, v = y / (float) size;
                float base = 0.25f + (fbm(macro, u, v) - 0.5f) * 0.06f;
                float g = (fbm(grain, u, v) - 0.5f) * 0.10f;
                // Árido: algunos granos claros y oscuros
                float speck = r.nextFloat();
                float s = speck > 0.985f ? 0.10f : speck < 0.02f ? -0.06f : 0f;
                float c = base + g + s;
                put(p, x, y, c, c, c * 1.02f);
            }
        }
        return p;
    }

    private static Pixmap grass(int size) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        Noise[] macro = octaves(10, 2, 4, 8);
        Noise[] blades = octaves(11, 96, 192);
        Random r = new Random(12);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float u = x / (float) size, v = y / (float) size;
                float m = fbm(macro, u, v);
                float b = fbm(blades, u, v) - 0.5f;
                float dry = Math.max(0f, m - 0.62f) * 1.6f; // calvas secas
                float jitter = (r.nextFloat() - 0.5f) * 0.04f;
                float red = 0.24f + dry * 0.28f + b * 0.10f + jitter;
                float green = 0.42f + (m - 0.5f) * 0.12f + b * 0.16f + jitter - dry * 0.05f;
                float blue = 0.16f + b * 0.05f + dry * 0.08f;
                put(p, x, y, red, green, blue);
            }
        }
        return p;
    }

    /** Baldosas de 50 cm (4 x 4 por repetición de 2 m) con juntas. */
    private static Pixmap sidewalk(int size) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        Noise[] grain = octaves(20, 48, 96);
        Noise[] stain = octaves(21, 4, 8);
        int tiles = 4;
        float tile = size / (float) tiles;
        float joint = Math.max(1.5f, size / 170f);
        Random r = new Random(22);
        float[] tone = new float[tiles * tiles];
        for (int i = 0; i < tone.length; i++) {
            tone[i] = (r.nextFloat() - 0.5f) * 0.05f;
        }
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float u = x / (float) size, v = y / (float) size;
                float fx = x % tile, fy = y % tile;
                boolean isJoint = fx < joint || fy < joint;
                int tx = (int) (x / tile), ty = (int) (y / tile);
                float c = 0.66f + tone[ty * tiles + tx] + (fbm(grain, u, v) - 0.5f) * 0.08f
                    - (fbm(stain, u, v) - 0.5f) * 0.06f;
                if (isJoint) {
                    c -= 0.18f;
                }
                put(p, x, y, c, c * 0.98f, c * 0.94f);
            }
        }
        return p;
    }

    /**
     * Fachada: 2 x 2 ventanas por repetición (dos plantas), con marco, cristal
     * oscuro con reflejo, algunas persianas bajadas y un zócalo de forjado.
     */
    private static Pixmap facade(int size, Color wall, long seed) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        Noise[] grain = octaves(seed, 32, 64);
        Random r = new Random(seed);
        float cellW = size / 2f;
        float cellH = size / 2f;
        float[] blind = {r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()};
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float u = x / (float) size, v = y / (float) size;
                float g = (fbm(grain, u, v) - 0.5f) * 0.08f;
                float cr = wall.r + g, cg = wall.g + g, cb = wall.b + g;
                int cx = (int) (x / cellW), cy = (int) (y / cellH);
                float lx = (x % cellW) / cellW; // 0..1 dentro de la celda
                float ly = (y % cellH) / cellH;
                // Línea de forjado
                if (ly > 0.94f) {
                    cr -= 0.10f;
                    cg -= 0.10f;
                    cb -= 0.10f;
                }
                // Ventana: centrada, 46 % de ancho y 52 % de alto
                float wx0 = 0.27f, wx1 = 0.73f, wy0 = 0.22f, wy1 = 0.74f;
                if (lx > wx0 - 0.03f && lx < wx1 + 0.03f && ly > wy0 - 0.03f && ly < wy1 + 0.05f) {
                    // Marco claro y alféizar
                    cr = 0.88f;
                    cg = 0.88f;
                    cb = 0.86f;
                    if (lx > wx0 && lx < wx1 && ly > wy0 && ly < wy1) {
                        float closed = blind[cy * 2 + cx];
                        float blindLevel = closed > 0.6f ? wy0 + (wy1 - wy0) * (closed - 0.4f) : wy0;
                        if (ly < blindLevel) {
                            // Persiana enrollable: lamas horizontales
                            float slat = ((ly * size) % 4f) < 1f ? 0.05f : 0f;
                            cr = 0.80f - slat;
                            cg = 0.78f - slat;
                            cb = 0.72f - slat;
                        } else {
                            // Cristal: oscuro azulado con un reflejo diagonal
                            float refl = Math.max(0f, 1f - Math.abs((lx - wx0) - (ly - wy0) * 0.8f - 0.12f) * 9f) * 0.25f;
                            cr = 0.16f + refl;
                            cg = 0.22f + refl;
                            cb = 0.30f + refl;
                            // Montante central
                            if (Math.abs(lx - 0.5f) < 0.012f) {
                                cr = cg = 0.85f;
                                cb = 0.83f;
                            }
                        }
                    }
                }
                put(p, x, y, cr, cg, cb);
            }
        }
        return p;
    }

    private static Pixmap concrete(int size) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        Noise[] n = octaves(30, 4, 16, 64);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float c = 0.60f + (fbm(n, x / (float) size, y / (float) size) - 0.5f) * 0.16f;
                put(p, x, y, c, c * 0.99f, c * 0.96f);
            }
        }
        return p;
    }

    private static Pixmap roof(int size) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        Noise[] n = octaves(40, 8, 32);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float c = 0.30f + (fbm(n, x / (float) size, y / (float) size) - 0.5f) * 0.10f;
                put(p, x, y, c * 1.05f, c, c * 0.95f);
            }
        }
        return p;
    }

    @Override
    public void dispose() {
        asphalt.dispose();
        grass.dispose();
        sidewalk.dispose();
        facade.dispose();
        facadeBrick.dispose();
        concrete.dispose();
        roof.dispose();
    }
}
