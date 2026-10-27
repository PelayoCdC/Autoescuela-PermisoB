package com.pelayo.autoescuela.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;

/**
 * Rasterizador de formas con antialiasing a partir de funciones de distancia
 * con signo (SDF). Pixmap no suaviza los bordes; aquí cada píxel se cubre en
 * proporción a su distancia al contorno, así los círculos del HUD, las
 * esquinas redondeadas de la skin y los iconos se ven limpios a cualquier
 * escala. Todo en píxeles, origen arriba a la izquierda (como Pixmap).
 */
public final class Shapes {

    private Shapes() {
    }

    /** Distancia con signo del punto (x, y) al contorno: negativa dentro. */
    public interface Sdf {
        float distance(float x, float y);
    }

    /** Pinta la forma con un color, mezclando con lo que ya hubiera en el pixmap. */
    public static void fill(Pixmap pixmap, Sdf shape, Color color) {
        int w = pixmap.getWidth();
        int h = pixmap.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float d = shape.distance(x + 0.5f, y + 0.5f);
                float coverage = clamp01(0.5f - d);
                if (coverage <= 0f) {
                    continue;
                }
                blend(pixmap, x, y, color, coverage);
            }
        }
    }

    /** Pixmap nuevo, transparente, con la forma pintada en blanco (para teñir con setColor). */
    public static Pixmap white(int width, int height, Sdf shape) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        fill(pixmap, shape, Color.WHITE);
        return pixmap;
    }

    private static void blend(Pixmap pixmap, int x, int y, Color c, float coverage) {
        int dst = pixmap.getPixel(x, y);
        float da = (dst & 0xff) / 255f;
        float dr = ((dst >>> 24) & 0xff) / 255f;
        float dg = ((dst >>> 16) & 0xff) / 255f;
        float db = ((dst >>> 8) & 0xff) / 255f;
        float sa = c.a * coverage;
        float oa = sa + da * (1f - sa);
        if (oa <= 0f) {
            return;
        }
        float or = (c.r * sa + dr * da * (1f - sa)) / oa;
        float og = (c.g * sa + dg * da * (1f - sa)) / oa;
        float ob = (c.b * sa + db * da * (1f - sa)) / oa;
        pixmap.drawPixel(x, y, Color.rgba8888(or, og, ob, oa));
    }

    // --- Primitivas ------------------------------------------------------------

    public static Sdf circle(float cx, float cy, float r) {
        return (x, y) -> (float) Math.hypot(x - cx, y - cy) - r;
    }

    /** Anillo de radio medio r y grosor t. */
    public static Sdf ring(float cx, float cy, float r, float t) {
        return (x, y) -> Math.abs((float) Math.hypot(x - cx, y - cy) - r) - t / 2f;
    }

    /** Rectángulo de esquinas redondeadas (x0, y0, ancho, alto, radio). */
    public static Sdf roundRect(float x0, float y0, float w, float h, float r) {
        float cx = x0 + w / 2f;
        float cy = y0 + h / 2f;
        float hx = w / 2f - r;
        float hy = h / 2f - r;
        return (x, y) -> {
            float qx = Math.abs(x - cx) - hx;
            float qy = Math.abs(y - cy) - hy;
            float ox = Math.max(qx, 0f);
            float oy = Math.max(qy, 0f);
            return (float) Math.hypot(ox, oy) + Math.min(Math.max(qx, qy), 0f) - r;
        };
    }

    /** Segmento de (ax, ay) a (bx, by) con extremos redondeados y grosor t. */
    public static Sdf capsule(float ax, float ay, float bx, float by, float t) {
        return (x, y) -> {
            float px = x - ax;
            float py = y - ay;
            float vx = bx - ax;
            float vy = by - ay;
            float k = clamp01((px * vx + py * vy) / (vx * vx + vy * vy));
            return (float) Math.hypot(px - vx * k, py - vy * k) - t / 2f;
        };
    }

    /** Polígono convexo o no (vértices x0,y0,x1,y1,...), relleno. */
    public static Sdf polygon(float... v) {
        int n = v.length / 2;
        return (x, y) -> {
            float d = Float.MAX_VALUE;
            boolean inside = false;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                float xi = v[i * 2], yi = v[i * 2 + 1];
                float xj = v[j * 2], yj = v[j * 2 + 1];
                float ex = xj - xi, ey = yj - yi;
                float wx = x - xi, wy = y - yi;
                float k = clamp01((wx * ex + wy * ey) / (ex * ex + ey * ey));
                d = Math.min(d, (float) Math.hypot(wx - ex * k, wy - ey * k));
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                    inside = !inside;
                }
            }
            return inside ? -d : d;
        };
    }

    public static Sdf union(Sdf... shapes) {
        return (x, y) -> {
            float d = Float.MAX_VALUE;
            for (Sdf s : shapes) {
                d = Math.min(d, s.distance(x, y));
            }
            return d;
        };
    }

    /** a menos b. */
    public static Sdf subtract(Sdf a, Sdf b) {
        return (x, y) -> Math.max(a.distance(x, y), -b.distance(x, y));
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }
}
