package com.pelayo.autoescuela.ui;

import static com.pelayo.autoescuela.ui.Shapes.capsule;
import static com.pelayo.autoescuela.ui.Shapes.circle;
import static com.pelayo.autoescuela.ui.Shapes.polygon;
import static com.pelayo.autoescuela.ui.Shapes.ring;
import static com.pelayo.autoescuela.ui.Shapes.roundRect;
import static com.pelayo.autoescuela.ui.Shapes.subtract;
import static com.pelayo.autoescuela.ui.Shapes.union;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.MathUtils;
import com.pelayo.autoescuela.ui.Shapes.Sdf;

/**
 * Iconos de la interfaz dibujados en código (blancos, para teñir), en una
 * rejilla de {@link #SIZE} píxeles. Se registran en la skin como "icon-*".
 */
public final class Icons {

    public static final int SIZE = 128;

    private Icons() {
    }

    /** Nombres de los iconos disponibles (drawables "icon-" + nombre). */
    public static final String[] NAMES = {"wheel", "car", "book", "gear", "check", "lock", "exit", "cone",
        "play", "flag", "chart", "import", "pedals", "star", "key", "camera", "reset", "map"};

    /** Se rasteriza al doble de la rejilla: el volante táctil se dibuja grande y con mipmaps se reduce bien. */
    public static Pixmap create(String name) {
        Sdf shape = shape(name);
        return Shapes.white(SIZE * 2, SIZE * 2, (x, y) -> shape.distance(x / 2f, y / 2f) * 2f);
    }

    private static Sdf shape(String name) {
        float c = SIZE / 2f;
        switch (name) {
            case "wheel": // volante: aro, buje y tres radios
                return union(ring(c, c, 48f, 13f), circle(c, c + 6f, 15f),
                    capsule(c - 46f, c - 2f, c + 46f, c - 2f, 11f),
                    capsule(c, c + 6f, c, c + 48f, 11f));
            case "car": { // turismo de perfil
                Sdf body = roundRect(10f, 58f, 108f, 34f, 12f);
                Sdf cabin = polygon(32f, 60f, 44f, 30f, 86f, 30f, 102f, 60f);
                Sdf window = union(polygon(46f, 56f, 53f, 38f, 63f, 38f, 63f, 56f),
                    polygon(70f, 56f, 70f, 38f, 82f, 38f, 92f, 56f));
                Sdf wheels = union(circle(36f, 94f, 15f), circle(92f, 94f, 15f));
                Sdf hubs = union(circle(36f, 94f, 6f), circle(92f, 94f, 6f));
                return union(subtract(subtract(union(body, cabin), window), union(circle(36f, 94f, 19f),
                    circle(92f, 94f, 19f))), subtract(wheels, hubs));
            }
            case "book": { // libro abierto
                Sdf left = polygon(10f, 30f, 60f, 38f, 60f, 104f, 10f, 96f);
                Sdf right = polygon(68f, 38f, 118f, 30f, 118f, 96f, 68f, 104f);
                Sdf lines = union(capsule(20f, 52f, 50f, 56f, 4f), capsule(20f, 66f, 50f, 70f, 4f),
                    capsule(20f, 80f, 50f, 84f, 4f), capsule(78f, 56f, 108f, 52f, 4f),
                    capsule(78f, 70f, 108f, 66f, 4f), capsule(78f, 84f, 108f, 80f, 4f));
                return subtract(union(left, right), lines);
            }
            case "gear": { // rueda dentada
                Sdf[] teeth = new Sdf[9];
                for (int i = 0; i < 8; i++) {
                    float a = i * 45f;
                    teeth[i] = capsule(c, c, c + MathUtils.cosDeg(a) * 50f, c + MathUtils.sinDeg(a) * 50f, 18f);
                }
                teeth[8] = circle(c, c, 38f);
                return subtract(union(teeth), circle(c, c, 16f));
            }
            case "check":
                return union(capsule(26f, 66f, 52f, 92f, 16f), capsule(52f, 92f, 102f, 36f, 16f));
            case "lock":
                return union(subtract(roundRect(26f, 56f, 76f, 58f, 10f), circle(c, 80f, 8f)),
                    subtract(ring(c, 52f, 24f, 11f), roundRect(20f, 52f, 88f, 60f, 0f)),
                    capsule(c - 24f, 52f, c - 24f, 60f, 11f), capsule(c + 24f, 52f, c + 24f, 60f, 11f));
            case "exit": // puerta con flecha
                return union(subtract(roundRect(18f, 16f, 60f, 96f, 8f), roundRect(30f, 28f, 36f, 72f, 4f)),
                    capsule(52f, 64f, 108f, 64f, 12f), capsule(108f, 64f, 88f, 44f, 12f),
                    capsule(108f, 64f, 88f, 84f, 12f));
            case "cone": { // cono de tráfico con banda
                Sdf cone = union(polygon(52f, 16f, 76f, 16f, 98f, 100f, 30f, 100f), roundRect(16f, 98f, 96f, 14f, 5f));
                Sdf band = polygon(40f, 52f, 88f, 52f, 92f, 68f, 36f, 68f);
                return subtract(cone, band);
            }
            case "play":
                return polygon(38f, 22f, 106f, 64f, 38f, 106f);
            case "flag": // banderín de meta
                return union(capsule(28f, 14f, 28f, 116f, 10f),
                    subtract(polygon(28f, 18f, 104f, 18f, 104f, 70f, 28f, 70f),
                        union(roundRect(40f, 18f, 16f, 17f, 0f), roundRect(72f, 18f, 16f, 17f, 0f),
                            roundRect(56f, 35f, 16f, 18f, 0f), roundRect(88f, 35f, 16f, 18f, 0f),
                            roundRect(40f, 53f, 16f, 17f, 0f), roundRect(72f, 53f, 16f, 17f, 0f))));
            case "chart":
                return union(roundRect(16f, 70f, 22f, 42f, 4f), roundRect(46f, 44f, 22f, 68f, 4f),
                    roundRect(76f, 20f, 22f, 92f, 4f), capsule(12f, 116f, 116f, 116f, 6f));
            case "import": // bandeja con flecha hacia abajo
                return union(capsule(c, 14f, c, 76f, 12f), capsule(c, 78f, c - 24f, 54f, 12f),
                    capsule(c, 78f, c + 24f, 54f, 12f), capsule(18f, 84f, 18f, 110f, 10f),
                    capsule(18f, 110f, 110f, 110f, 10f), capsule(110f, 110f, 110f, 84f, 10f));
            case "pedals": // tres pedales
                return union(roundRect(12f, 50f, 28f, 60f, 8f), roundRect(50f, 50f, 28f, 60f, 8f),
                    roundRect(90f, 34f, 26f, 78f, 8f), capsule(26f, 14f, 26f, 50f, 6f),
                    capsule(64f, 14f, 64f, 50f, 6f), capsule(103f, 14f, 103f, 34f, 6f));
            case "star": {
                float[] v = new float[20];
                for (int i = 0; i < 10; i++) {
                    float r = i % 2 == 0 ? 56f : 24f;
                    float a = -90f + i * 36f;
                    v[i * 2] = c + MathUtils.cosDeg(a) * r;
                    v[i * 2 + 1] = c + 4f + MathUtils.sinDeg(a) * r;
                }
                return polygon(v);
            }
            case "key": // llave de contacto
                return union(subtract(circle(38f, 64f, 26f), circle(38f, 64f, 10f)),
                    capsule(60f, 64f, 114f, 64f, 13f), capsule(96f, 64f, 96f, 84f, 11f),
                    capsule(110f, 64f, 110f, 80f, 11f));
            case "camera": // cámara de fotos
                return union(subtract(roundRect(12f, 38f, 104f, 68f, 12f), circle(64f, 72f, 22f)),
                    circle(64f, 72f, 12f), roundRect(42f, 26f, 34f, 18f, 5f));
            case "reset": { // flecha circular
                Sdf arc = subtract(ring(64f, 66f, 38f, 13f), polygon(64f, 66f, 128f, 0f, 128f, 40f));
                Sdf head = polygon(76f, 12f, 112f, 26f, 84f, 48f);
                return union(arc, head);
            }
            case "map": // plano plegado en tres paneles, con un punto de destino
                return subtract(union(polygon(12f, 30f, 44f, 18f, 44f, 98f, 12f, 110f),
                    polygon(50f, 18f, 78f, 30f, 78f, 110f, 50f, 98f),
                    polygon(84f, 30f, 116f, 18f, 116f, 98f, 84f, 110f)), circle(64f, 58f, 9f));
            default:
                throw new IllegalArgumentException("Icono desconocido: " + name);
        }
    }
}
