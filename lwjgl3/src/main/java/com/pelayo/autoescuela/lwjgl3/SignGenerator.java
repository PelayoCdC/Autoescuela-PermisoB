package com.pelayo.autoescuela.lwjgl3;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/**
 * Genera las imágenes de señales de tráfico del módulo de teoría con Java2D (sin libGDX):
 * PNG de 256x256 con fondo transparente en {@code assets/theory/signs/<CODIGO>.png} y una
 * hoja de contacto en {@code assets/cache/signs_sheet.png} para revisarlas de un vistazo.
 * Colores y proporciones aproximan el Catálogo Oficial de Señales de Circulación.
 * Uso: {@code gradlew signs [-PsignsOut=carpeta]}.
 */
public final class SignGenerator {

    private static final int SIZE = 256;
    private static final double C = SIZE / 2.0;

    private static final Color RED = new Color(0xC1121F);
    private static final Color BLUE = new Color(0x1F5AA6);
    private static final Color AMBER = new Color(0xF4B400);
    private static final Color BLACK = new Color(0x161616);
    private static final Color WHITE = Color.WHITE;
    private static final Color GREY = new Color(0x8C8C8C);
    private static final Color LIGHT_RED = new Color(0xE53935);
    private static final Color LIGHT_AMBER = new Color(0xFFB300);
    private static final Color LIGHT_GREEN = new Color(0x2E9E4F);

    // Discos: radio exterior (con el ribete blanco), radio de la señal y grosor del anillo rojo
    private static final double DISC_R = 125;
    private static final double RIM = 3;
    private static final double RING = 24;
    /** Radio interior del anillo rojo de las señales de prohibición. */
    private static final double INNER_R = DISC_R - RIM - RING;

    // Triángulos de peligro: radio circunscrito, radio de esquina, ancho del borde rojo
    private static final double TRI_R = 156;
    private static final double TRI_CORNER = 14;
    private static final double TRI_BORDER = 22;

    private SignGenerator() {
    }

    public static void main(String[] args) throws IOException {
        File outDir = new File(args.length > 0 ? args[0] : "assets/theory/signs");
        File sheetFile = new File("assets/cache/signs_sheet.png");
        if (!outDir.isDirectory() && !outDir.mkdirs()) {
            throw new IOException("No se puede crear " + outDir.getAbsolutePath());
        }
        Map<String, Consumer<Graphics2D>> signs = catalog();
        Map<String, BufferedImage> images = new LinkedHashMap<>();
        for (Map.Entry<String, Consumer<Graphics2D>> e : signs.entrySet()) {
            BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            setup(g);
            e.getValue().accept(g);
            g.dispose();
            File f = new File(outDir, e.getKey() + ".png");
            ImageIO.write(img, "png", f);
            images.put(e.getKey(), img);
        }
        System.out.println(images.size() + " PNG escritos en " + outDir.getAbsolutePath());
        writeContactSheet(images, sheetFile);
        System.out.println("Hoja de contacto: " + sheetFile.getAbsolutePath());
    }

    /** Código de la señal (nombre de fichero) → función que la dibuja. */
    private static Map<String, Consumer<Graphics2D>> catalog() {
        Map<String, Consumer<Graphics2D>> m = new LinkedHashMap<>();
        m.put("R-1", SignGenerator::yield);
        m.put("R-2", SignGenerator::stop);
        m.put("R-3", SignGenerator::priorityRoad);
        m.put("R-4", SignGenerator::endOfPriorityRoad);
        m.put("R-100", SignGenerator::noVehicles);
        m.put("R-101", SignGenerator::noEntry);
        m.put("R-301_30", g -> speedLimit(g, 30));
        m.put("R-301_50", g -> speedLimit(g, 50));
        m.put("R-301_90", g -> speedLimit(g, 90));
        m.put("R-302", g -> noTurn(g, true));
        m.put("R-303", g -> noTurn(g, false));
        m.put("R-305", SignGenerator::noOvertaking);
        m.put("R-307", SignGenerator::noStopping);
        m.put("R-308", SignGenerator::noParking);
        m.put("R-400a", SignGenerator::aheadRight);
        m.put("R-401a", SignGenerator::keepRight);
        m.put("R-402", SignGenerator::roundabout);
        m.put("R-500", SignGenerator::endOfProhibitions);
        m.put("R-501_50", g -> endOfSpeedLimit(g, 50));
        m.put("P-1", SignGenerator::dangerIntersection);
        m.put("P-3", SignGenerator::dangerTrafficLights);
        m.put("P-4", SignGenerator::dangerRoundabout);
        m.put("P-13a", SignGenerator::dangerCurveRight);
        m.put("P-50", SignGenerator::dangerOther);
        m.put("S-13", SignGenerator::pedestrianCrossing);
        m.put("S-28", SignGenerator::residentialStreet);
        return m;
    }

    private static void setup(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    // ---------------------------------------------------------------------------------------
    // Señales de prioridad
    // ---------------------------------------------------------------------------------------

    /** R-1 Ceda el paso: triángulo con el vértice abajo, borde rojo, fondo blanco. */
    private static void yield(Graphics2D g) {
        double cy = C - 32;
        g.setColor(WHITE);
        g.fill(roundedPolygon(C, cy, TRI_R, 3, 90, TRI_CORNER));
        g.setColor(RED);
        g.fill(inset(C, cy, TRI_R, 3, 90, TRI_CORNER, RIM));
        g.setColor(WHITE);
        g.fill(inset(C, cy, TRI_R, 3, 90, TRI_CORNER, RIM + TRI_BORDER + 2));
    }

    /** R-2 STOP: octógono rojo con borde blanco y texto STOP. */
    private static void stop(Graphics2D g) {
        double r = 124 / Math.cos(Math.PI / 8);
        g.setColor(WHITE);
        g.fill(roundedPolygon(C, C, r, 8, 22.5, 6));
        g.setColor(RED);
        g.fill(inset(C, C, r, 8, 22.5, 6, 9));
        drawText(g, "STOP", C, C, 66, 0.84, WHITE);
    }

    /** R-3 Calzada con prioridad: rombo blanco con rombo interior amarillo y borde negro fino. */
    private static void priorityRoad(Graphics2D g) {
        double r = 126;
        g.setColor(BLACK);
        g.fill(roundedPolygon(C, C, r, 4, -90, 8));
        g.setColor(WHITE);
        g.fill(inset(C, C, r, 4, -90, 8, 3.5));
        g.setColor(AMBER);
        g.fill(roundedPolygon(C, C, r * 0.56, 4, -90, 5));
    }

    /** R-4 Fin de prioridad: R-3 cruzada por una banda de rayas negras finas. */
    private static void endOfPriorityRoad(Graphics2D g) {
        priorityRoad(g);
        Shape clip = inset(C, C, 126, 4, -90, 8, 3.5);
        // Banda perpendicular a los lados inferior-izquierdo y superior-derecho
        stripeBand(g, clip, 45, 7, 6, 4.5, BLACK);
    }

    // ---------------------------------------------------------------------------------------
    // Señales de prohibición y restricción
    // ---------------------------------------------------------------------------------------

    /** R-100 Circulación prohibida. */
    private static void noVehicles(Graphics2D g) {
        prohibitionDisc(g, WHITE);
    }

    /** R-101 Entrada prohibida: disco rojo con barra horizontal blanca. */
    private static void noEntry(Graphics2D g) {
        g.setColor(WHITE);
        g.fill(circle(C, C, DISC_R));
        g.setColor(RED);
        g.fill(circle(C, C, DISC_R - RIM));
        double w = 176;
        double h = 40;
        g.setColor(WHITE);
        g.fill(new Rectangle2D.Double(C - w / 2, C - h / 2, w, h));
    }

    /** R-301 Velocidad máxima. */
    private static void speedLimit(Graphics2D g, int kmh) {
        prohibitionDisc(g, WHITE);
        drawText(g, Integer.toString(kmh), C, C + 2, 112, 0.80, BLACK);
    }

    /** R-302 (derecha) / R-303 (izquierda) Giro prohibido. */
    private static void noTurn(Graphics2D g, boolean right) {
        prohibitionDisc(g, WHITE);
        AffineTransform old = g.getTransform();
        if (!right) {
            g.translate(SIZE, 0);
            g.scale(-1, 1);
        }
        double w = 20;
        double x0 = C - 26;
        double bend = 32;
        Path2D shaft = new Path2D.Double();
        shaft.moveTo(x0, 202);
        shaft.lineTo(x0, 112 + bend);
        shaft.quadTo(x0, 112, x0 + bend, 112);
        shaft.lineTo(C + 42, 112);
        g.setColor(BLACK);
        g.setStroke(new BasicStroke((float) w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.draw(shaft);
        g.fill(arrowHead(C + 76, 112, 0, 38, 56));
        g.setTransform(old);
        prohibitionBar(g, 135);
    }

    /** R-305 Adelantamiento prohibido: dos turismos vistos por detrás, el izquierdo rojo. */
    private static void noOvertaking(Graphics2D g) {
        prohibitionDisc(g, WHITE);
        carRear(g, C - 41, C + 6, 72, RED);
        carRear(g, C + 41, C + 6, 72, BLACK);
    }

    /** R-307 Parada y estacionamiento prohibido: fondo azul, anillo rojo y aspa. */
    private static void noStopping(Graphics2D g) {
        prohibitionDisc(g, BLUE);
        prohibitionBar(g, 135);
        prohibitionBar(g, 45);
    }

    /** R-308 Estacionamiento prohibido: fondo azul, anillo rojo y una diagonal. */
    private static void noParking(Graphics2D g) {
        prohibitionDisc(g, BLUE);
        prohibitionBar(g, 135);
    }

    /** R-500 Fin de prohibiciones: disco blanco, borde negro fino y banda de rayas. */
    private static void endOfProhibitions(Graphics2D g) {
        endDisc(g);
        stripeBand(g, circle(C, C, DISC_R - RIM - 3), 45, 7, 7, 4, BLACK);
    }

    /** R-501 Fin de velocidad máxima: número en gris tachado por una banda de rayas. */
    private static void endOfSpeedLimit(Graphics2D g, int kmh) {
        endDisc(g);
        drawText(g, Integer.toString(kmh), C, C + 2, 112, 0.80, GREY);
        stripeBand(g, circle(C, C, DISC_R - RIM - 3), 45, 5, 6, 4, BLACK);
    }

    // ---------------------------------------------------------------------------------------
    // Señales de obligación
    // ---------------------------------------------------------------------------------------

    /** R-400a Sentido obligatorio (derecha). */
    private static void aheadRight(Graphics2D g) {
        obligationDisc(g);
        straightArrow(g, C - 74, C, C + 76, C, 26, 60, 78);
    }

    /** R-401a Paso obligatorio (por la derecha): flecha diagonal hacia abajo a la derecha. */
    private static void keepRight(Graphics2D g) {
        obligationDisc(g);
        double d = 56;
        straightArrow(g, C - d, C - d, C + d, C + d, 26, 58, 76);
    }

    /** R-402 Intersección de sentido giratorio obligatorio: tres flechas en sentido antihorario. */
    private static void roundabout(Graphics2D g) {
        obligationDisc(g);
        rotaryArrows(g, C, C, 64, 21, WHITE);
    }

    // ---------------------------------------------------------------------------------------
    // Señales de peligro
    // ---------------------------------------------------------------------------------------

    /** P-1 Intersección con prioridad: cruz con la vía prioritaria (vertical) más ancha. */
    private static void dangerIntersection(Graphics2D g) {
        double cy = dangerTriangle(g);
        g.setColor(BLACK);
        g.fill(new Rectangle2D.Double(C - 11, cy - 72, 22, 122));
        g.fill(new Rectangle2D.Double(C - 48, cy - 18, 96, 13));
    }

    /** P-3 Semáforos. */
    private static void dangerTrafficLights(Graphics2D g) {
        double cy = dangerTriangle(g);
        double w = 42;
        double h = 112;
        double top = cy - 70;
        g.setColor(BLACK);
        g.fill(new RoundRectangle2D.Double(C - w / 2, top, w, h, 12, 12));
        Color[] lights = {LIGHT_RED, LIGHT_AMBER, LIGHT_GREEN};
        double lr = 13;
        for (int i = 0; i < 3; i++) {
            double ly = top + h * (i + 0.5) / 3;
            g.setColor(lights[i]);
            g.fill(circle(C, ly, lr));
        }
    }

    /** P-4 Intersección con circulación giratoria. */
    private static void dangerRoundabout(Graphics2D g) {
        double cy = dangerTriangle(g);
        rotaryArrows(g, C, cy - 8, 40, 13, BLACK);
    }

    /** P-13a Curva peligrosa hacia la derecha. */
    private static void dangerCurveRight(Graphics2D g) {
        double cy = dangerTriangle(g);
        double w = 18;
        double x0 = C - 26;
        double top = cy - 46;
        Path2D shaft = new Path2D.Double();
        shaft.moveTo(x0, cy + 48);
        shaft.lineTo(x0, top + 30);
        shaft.curveTo(x0, top + 6, x0 + 12, top, x0 + 30, top);
        g.setColor(BLACK);
        g.setStroke(new BasicStroke((float) w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.draw(shaft);
        g.fill(arrowHead(x0 + 68, top, 0, 35, 46));
    }

    /** P-50 Otros peligros: signo de exclamación. */
    private static void dangerOther(Graphics2D g) {
        double cy = dangerTriangle(g);
        Path2D bar = new Path2D.Double();
        double top = cy - 74;
        double bottom = cy + 16;
        bar.moveTo(C - 12, top + 6);
        bar.quadTo(C, top - 4, C + 12, top + 6);
        bar.lineTo(C + 6, bottom);
        bar.lineTo(C - 6, bottom);
        bar.closePath();
        g.setColor(BLACK);
        g.fill(bar);
        g.fill(circle(C, cy + 38, 11));
    }

    // ---------------------------------------------------------------------------------------
    // Señales de indicación
    // ---------------------------------------------------------------------------------------

    /** S-13 Situación de un paso para peatones: cuadrado azul, triángulo blanco y peatón. */
    private static void pedestrianCrossing(Graphics2D g) {
        blueSquare(g, 248, 248);
        // Triángulo blanco (vértice arriba) con esquinas redondeadas
        double tr = 118;
        double tcy = C + 26;
        g.setColor(WHITE);
        g.fill(roundedPolygon(C, tcy, tr, 3, -90, 9));
        Shape inner = inset(C, tcy, tr, 3, -90, 9, 6);
        Shape oldClip = g.getClip();
        g.setClip(inner);
        // Franjas del paso de cebra
        g.setColor(BLACK);
        double zy = tcy + 26;
        for (int i = -3; i <= 3; i++) {
            double x = C + i * 27;
            Path2D s = new Path2D.Double();
            s.moveTo(x - 8, zy);
            s.lineTo(x + 8, zy);
            s.lineTo(x + 14, zy + 22);
            s.lineTo(x - 2, zy + 22);
            s.closePath();
            g.fill(s);
        }
        g.setClip(oldClip);
        pedestrian(g, C, zy - 4, 0.95);
    }

    /** S-28 Calle residencial: rectángulo azul con casa, adulto con niño y coche en blanco. */
    private static void residentialStreet(Graphics2D g) {
        blueSquare(g, 248, 208);
        g.setColor(WHITE);
        // Casas al fondo
        house(g, 58, 108, 58);
        house(g, 200, 104, 50);
        // Suelo
        g.fill(new Rectangle2D.Double(16, 184, 224, 4));
        // Adulto jugando con un niño y una pelota
        BasicStroke limb = new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        g.setStroke(limb);
        g.fill(circle(64, 120, 9));
        g.draw(new Line2D.Double(64, 132, 66, 160));
        g.draw(new Line2D.Double(66, 160, 56, 182));
        g.draw(new Line2D.Double(66, 160, 76, 182));
        g.draw(new Line2D.Double(64, 138, 86, 150));
        g.draw(new Line2D.Double(64, 138, 50, 156));
        g.setStroke(new BasicStroke(6.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.fill(circle(100, 145, 7));
        g.draw(new Line2D.Double(100, 155, 101, 168));
        g.draw(new Line2D.Double(101, 168, 95, 182));
        g.draw(new Line2D.Double(101, 168, 107, 182));
        g.draw(new Line2D.Double(100, 158, 90, 152));
        g.fill(circle(116, 176, 6.5));
        // Coche de perfil
        carSide(g, 182, 182, 92);
    }

    // ---------------------------------------------------------------------------------------
    // Helpers de forma
    // ---------------------------------------------------------------------------------------

    private static Ellipse2D circle(double cx, double cy, double r) {
        return new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
    }

    /**
     * Polígono regular con esquinas redondeadas. {@code r} es el radio circunscrito del polígono
     * sin redondear y {@code startDeg} el ángulo del primer vértice (0 = derecha, -90 = arriba).
     */
    private static Shape roundedPolygon(double cx, double cy, double r, int n, double startDeg, double corner) {
        double apothem = r * Math.cos(Math.PI / n);
        double scale = (apothem - corner) / apothem;
        Path2D core = new Path2D.Double();
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(startDeg + 360.0 * i / n);
            double x = cx + r * scale * Math.cos(a);
            double y = cy + r * scale * Math.sin(a);
            if (i == 0) {
                core.moveTo(x, y);
            } else {
                core.lineTo(x, y);
            }
        }
        core.closePath();
        Area area = new Area(core);
        if (corner > 0) {
            area.add(new Area(new BasicStroke((float) (2 * corner), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                .createStrokedShape(core)));
        }
        return area;
    }

    /** El mismo polígono redondeado encogido una distancia {@code d} perpendicular a los lados. */
    private static Shape inset(double cx, double cy, double r, int n, double startDeg, double corner, double d) {
        double newR = r - d / Math.cos(Math.PI / n);
        return roundedPolygon(cx, cy, newR, n, startDeg, Math.max(corner - d, 1));
    }

    /** Triángulo de peligro (vértice arriba); devuelve la y del centro del área blanca útil. */
    private static double dangerTriangle(Graphics2D g) {
        double cy = C + 32;
        g.setColor(WHITE);
        g.fill(roundedPolygon(C, cy, TRI_R, 3, -90, TRI_CORNER));
        g.setColor(RED);
        g.fill(inset(C, cy, TRI_R, 3, -90, TRI_CORNER, RIM));
        g.setColor(WHITE);
        g.fill(inset(C, cy, TRI_R, 3, -90, TRI_CORNER, RIM + TRI_BORDER));
        return cy + 8;
    }

    /** Disco de prohibición: ribete blanco, anillo rojo y fondo {@code inside}. */
    private static void prohibitionDisc(Graphics2D g, Color inside) {
        g.setColor(WHITE);
        g.fill(circle(C, C, DISC_R));
        g.setColor(RED);
        g.fill(circle(C, C, DISC_R - RIM));
        g.setColor(inside);
        g.fill(circle(C, C, INNER_R));
    }

    /** Barra roja diagonal de prohibición a {@code deg} grados (135 = de arriba-izquierda a abajo-derecha). */
    private static void prohibitionBar(Graphics2D g, double deg) {
        double a = Math.toRadians(deg);
        double len = INNER_R + 2;
        double dx = Math.cos(a) * len;
        double dy = Math.sin(a) * len;
        Shape bar = new BasicStroke((float) RING, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)
            .createStrokedShape(new Line2D.Double(C + dx, C - dy, C - dx, C + dy));
        Area area = new Area(bar);
        area.intersect(new Area(circle(C, C, INNER_R + 1)));
        g.setColor(RED);
        g.fill(area);
    }

    /** Disco de obligación: ribete blanco y fondo azul. */
    private static void obligationDisc(Graphics2D g) {
        g.setColor(WHITE);
        g.fill(circle(C, C, DISC_R));
        g.setColor(BLUE);
        g.fill(circle(C, C, DISC_R - RIM));
    }

    /** Disco de fin de prohibición: blanco con un borde negro fino. */
    private static void endDisc(Graphics2D g) {
        g.setColor(WHITE);
        g.fill(circle(C, C, DISC_R));
        g.setColor(BLACK);
        g.fill(circle(C, C, DISC_R - RIM));
        g.setColor(WHITE);
        g.fill(circle(C, C, DISC_R - RIM - 3));
    }

    /** Rectángulo azul de indicación con ribete blanco y esquinas redondeadas. */
    private static void blueSquare(Graphics2D g, double w, double h) {
        g.setColor(WHITE);
        g.fill(new RoundRectangle2D.Double(C - w / 2, C - h / 2, w, h, 26, 26));
        g.setColor(BLUE);
        g.fill(new RoundRectangle2D.Double(C - w / 2 + RIM, C - h / 2 + RIM, w - 2 * RIM, h - 2 * RIM, 20, 20));
    }

    /**
     * Banda de {@code count} rayas paralelas que cruza el centro de la señal en dirección {@code deg}
     * (grados matemáticos, y hacia arriba), recortada a {@code clip}.
     */
    private static void stripeBand(Graphics2D g, Shape clip, double deg, int count, double spacing, double width,
                                   Color color) {
        double a = Math.toRadians(deg);
        double ux = Math.cos(a);
        double uy = -Math.sin(a);
        double nx = -uy;
        double ny = ux;
        Shape old = g.getClip();
        g.clip(clip);
        g.setColor(color);
        g.setStroke(new BasicStroke((float) width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        for (int i = 0; i < count; i++) {
            double off = (i - (count - 1) / 2.0) * (spacing + width);
            double px = C + nx * off;
            double py = C + ny * off;
            g.draw(new Line2D.Double(px - ux * SIZE, py - uy * SIZE, px + ux * SIZE, py + uy * SIZE));
        }
        g.setClip(old);
    }

    // ---------------------------------------------------------------------------------------
    // Helpers de flechas y pictogramas
    // ---------------------------------------------------------------------------------------

    /** Punta de flecha triangular con el vértice en (tipX, tipY) apuntando a {@code angle} (radianes, pantalla). */
    private static Shape arrowHead(double tipX, double tipY, double angle, double length, double width) {
        double ux = Math.cos(angle);
        double uy = Math.sin(angle);
        double bx = tipX - ux * length;
        double by = tipY - uy * length;
        Path2D p = new Path2D.Double();
        p.moveTo(tipX, tipY);
        p.lineTo(bx - uy * width / 2, by + ux * width / 2);
        p.lineTo(bx + uy * width / 2, by - ux * width / 2);
        p.closePath();
        return p;
    }

    /** Flecha recta de (x0,y0) a la punta (x1,y1). */
    private static void straightArrow(Graphics2D g, double x0, double y0, double x1, double y1,
                                      double shaftW, double headLen, double headW) {
        double angle = Math.atan2(y1 - y0, x1 - x0);
        double ex = x1 - Math.cos(angle) * (headLen - 2);
        double ey = y1 - Math.sin(angle) * (headLen - 2);
        g.setColor(WHITE);
        g.setStroke(new BasicStroke((float) shaftW, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.draw(new Line2D.Double(x0, y0, ex, ey));
        g.fill(arrowHead(x1, y1, angle, headLen, headW));
    }

    /**
     * Tres flechas curvas sobre un círculo de radio {@code r} en sentido antihorario (visto en la señal),
     * como en R-402 y P-4.
     */
    private static void rotaryArrows(Graphics2D g, double cx, double cy, double r, double w, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke((float) w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        double headLen = w * 1.9;
        double headW = w * 2.6;
        double arc = 98;
        double headDeg = Math.toDegrees(headLen / r);
        for (int i = 0; i < 3; i++) {
            // Ángulos matemáticos (antihorario con y hacia arriba)
            double start = 90 + 30 + i * 120;
            double end = start + arc;
            g.draw(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, start, arc - headDeg + 3, Arc2D.OPEN));
            double ea = Math.toRadians(end);
            double tipX = cx + r * Math.cos(ea);
            double tipY = cy - r * Math.sin(ea);
            // Tangente antihoraria en pantalla: (-sin, -cos); se inclina un poco hacia dentro
            double baseA = Math.toRadians(end - headDeg);
            double bx = cx + r * Math.cos(baseA);
            double by = cy - r * Math.sin(baseA);
            double angle = Math.atan2(tipY - by, tipX - bx);
            g.fill(arrowHead(tipX, tipY, angle, headLen, headW));
        }
    }

    /** Turismo visto por detrás centrado en (cx, cy), de ancho {@code w}. */
    private static void carRear(Graphics2D g, double cx, double cy, double w, Color color) {
        double bodyH = w * 0.42;
        double bodyTop = cy - bodyH * 0.25;
        double roofW = w * 0.72;
        double roofTop = bodyTop - w * 0.44;
        Area car = new Area(new RoundRectangle2D.Double(cx - w / 2, bodyTop, w, bodyH, w * 0.16, w * 0.16));
        Path2D cabin = new Path2D.Double();
        cabin.moveTo(cx - w * 0.46, bodyTop + 2);
        cabin.lineTo(cx - roofW / 2, roofTop + 6);
        cabin.quadTo(cx - roofW / 2 + 2, roofTop, cx - roofW / 2 + 10, roofTop);
        cabin.lineTo(cx + roofW / 2 - 10, roofTop);
        cabin.quadTo(cx + roofW / 2 - 2, roofTop, cx + roofW / 2, roofTop + 6);
        cabin.lineTo(cx + w * 0.46, bodyTop + 2);
        cabin.closePath();
        car.add(new Area(cabin));
        // Ruedas
        double wheelW = w * 0.2;
        double wheelH = w * 0.2;
        double wheelTop = bodyTop + bodyH - 4;
        car.add(new Area(new RoundRectangle2D.Double(cx - w / 2 + 3, wheelTop, wheelW, wheelH, 5, 5)));
        car.add(new Area(new RoundRectangle2D.Double(cx + w / 2 - 3 - wheelW, wheelTop, wheelW, wheelH, 5, 5)));
        // Luna trasera
        double winTop = roofTop + w * 0.08;
        double winBottom = bodyTop - w * 0.03;
        Path2D win = new Path2D.Double();
        win.moveTo(cx - roofW / 2 + w * 0.07, winTop);
        win.lineTo(cx + roofW / 2 - w * 0.07, winTop);
        win.lineTo(cx + w * 0.37, winBottom);
        win.lineTo(cx - w * 0.37, winBottom);
        win.closePath();
        car.subtract(new Area(win));
        // Matrícula
        car.subtract(new Area(new Rectangle2D.Double(cx - w * 0.17, bodyTop + bodyH * 0.42, w * 0.34, bodyH * 0.24)));
        g.setColor(color);
        g.fill(car);
    }

    /** Peatón caminando hacia la derecha con los pies en (cx, footY). */
    private static void pedestrian(Graphics2D g, double cx, double footY, double s) {
        g.setColor(BLACK);
        g.setStroke(new BasicStroke((float) (11 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double hipX = cx - 2 * s;
        double hipY = footY - 42 * s;
        double neckX = cx + 4 * s;
        double neckY = footY - 82 * s;
        g.fill(circle(neckX + 3 * s, neckY - 13 * s, 10.5 * s));
        // Tronco
        g.setStroke(new BasicStroke((float) (14 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(neckX, neckY, hipX, hipY));
        g.setStroke(new BasicStroke((float) (10 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Piernas
        Path2D front = new Path2D.Double();
        front.moveTo(hipX, hipY);
        front.lineTo(hipX + 12 * s, hipY + 20 * s);
        front.lineTo(hipX + 24 * s, footY - 4 * s);
        g.draw(front);
        Path2D back = new Path2D.Double();
        back.moveTo(hipX, hipY);
        back.lineTo(hipX - 8 * s, hipY + 22 * s);
        back.lineTo(hipX - 22 * s, footY - 6 * s);
        g.draw(back);
        // Brazos
        g.setStroke(new BasicStroke((float) (8 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D armF = new Path2D.Double();
        armF.moveTo(neckX, neckY + 4 * s);
        armF.lineTo(neckX + 9 * s, neckY + 22 * s);
        armF.lineTo(neckX + 20 * s, neckY + 36 * s);
        g.draw(armF);
        Path2D armB = new Path2D.Double();
        armB.moveTo(neckX, neckY + 4 * s);
        armB.lineTo(neckX - 10 * s, neckY + 22 * s);
        armB.lineTo(neckX - 22 * s, neckY + 32 * s);
        g.draw(armB);
    }

    /** Casa sencilla (cuerpo + tejado + puerta recortada) con base en y={@code baseY}. */
    private static void house(Graphics2D g, double cx, double baseY, double w) {
        double bodyH = w * 0.62;
        Area a = new Area(new Rectangle2D.Double(cx - w / 2, baseY - bodyH, w, bodyH));
        Path2D roof = new Path2D.Double();
        roof.moveTo(cx - w / 2 - 6, baseY - bodyH + 1);
        roof.lineTo(cx, baseY - bodyH - w * 0.45);
        roof.lineTo(cx + w / 2 + 6, baseY - bodyH + 1);
        roof.closePath();
        a.add(new Area(roof));
        a.subtract(new Area(new Rectangle2D.Double(cx - w * 0.1, baseY - bodyH * 0.6, w * 0.2, bodyH * 0.6)));
        a.subtract(new Area(new Rectangle2D.Double(cx - w * 0.38, baseY - bodyH * 0.75, w * 0.18, w * 0.16)));
        a.subtract(new Area(new Rectangle2D.Double(cx + w * 0.2, baseY - bodyH * 0.75, w * 0.18, w * 0.16)));
        g.setColor(WHITE);
        g.fill(a);
    }

    /** Turismo de perfil (mirando a la izquierda) con las ruedas apoyadas en y={@code groundY}. */
    private static void carSide(Graphics2D g, double cx, double groundY, double len) {
        double wheelR = len * 0.11;
        double bodyBottom = groundY - wheelR * 0.9;
        double bodyH = len * 0.2;
        Area car = new Area(new RoundRectangle2D.Double(cx - len / 2, bodyBottom - bodyH, len, bodyH, 12, 12));
        Path2D cabin = new Path2D.Double();
        double roofY = bodyBottom - bodyH - len * 0.17;
        cabin.moveTo(cx - len * 0.30, bodyBottom - bodyH + 1);
        cabin.lineTo(cx - len * 0.15, roofY);
        cabin.lineTo(cx + len * 0.22, roofY);
        cabin.lineTo(cx + len * 0.34, bodyBottom - bodyH + 1);
        cabin.closePath();
        car.add(new Area(cabin));
        // Ventanillas
        Path2D win = new Path2D.Double();
        win.moveTo(cx - len * 0.24, bodyBottom - bodyH - 1);
        win.lineTo(cx - len * 0.13, roofY + 5);
        win.lineTo(cx + len * 0.02, roofY + 5);
        win.lineTo(cx + len * 0.02, bodyBottom - bodyH - 1);
        win.closePath();
        car.subtract(new Area(win));
        Path2D win2 = new Path2D.Double();
        win2.moveTo(cx + len * 0.07, bodyBottom - bodyH - 1);
        win2.lineTo(cx + len * 0.07, roofY + 5);
        win2.lineTo(cx + len * 0.20, roofY + 5);
        win2.lineTo(cx + len * 0.28, bodyBottom - bodyH - 1);
        win2.closePath();
        car.subtract(new Area(win2));
        // Pasos de rueda y ruedas
        double[] wx = {cx - len * 0.29, cx + len * 0.29};
        for (double x : wx) {
            car.subtract(new Area(circle(x, groundY - wheelR, wheelR + 3)));
            car.add(new Area(circle(x, groundY - wheelR, wheelR)));
        }
        g.setColor(WHITE);
        g.fill(car);
        g.setColor(BLUE);
        for (double x : wx) {
            g.fill(circle(x, groundY - wheelR, wheelR * 0.38));
        }
    }

    /** Texto centrado en (cx, cy) con altura de mayúsculas/dígitos {@code capH} y compresión horizontal. */
    private static void drawText(Graphics2D g, String text, double cx, double cy, double capH, double condense,
                                 Color color) {
        Font base = pickFont();
        FontRenderContext frc = g.getFontRenderContext();
        // Altura real de los glifos a tamaño 100 para escalar al alto pedido
        GlyphVector probe = base.deriveFont(100f).createGlyphVector(frc, text);
        Rectangle2D pb = probe.getVisualBounds();
        float size = (float) (100 * capH / pb.getHeight());
        GlyphVector gv = base.deriveFont(size).createGlyphVector(frc, text);
        Shape outline = gv.getOutline();
        Rectangle2D b = outline.getBounds2D();
        AffineTransform t = new AffineTransform();
        t.translate(cx, cy);
        t.scale(condense, 1);
        t.translate(-b.getCenterX(), -b.getCenterY());
        g.setColor(color);
        g.fill(t.createTransformedShape(outline));
    }

    private static Font pickFont() {
        for (String name : new String[] {"Arial", "Helvetica", "Liberation Sans", "DejaVu Sans"}) {
            Font f = new Font(name, Font.BOLD, 100);
            if (f.getFamily(Locale.ROOT).equalsIgnoreCase(name)) {
                return f;
            }
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, 100);
    }

    // ---------------------------------------------------------------------------------------
    // Hoja de contacto
    // ---------------------------------------------------------------------------------------

    private static void writeContactSheet(Map<String, BufferedImage> images, File file) throws IOException {
        int cols = 6;
        int cell = SIZE + 24;
        int thumb = SIZE;
        int label = 22;
        int rows = (images.size() + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * cell, rows * (cell + label), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        setup(g);
        // Gris medio para que se vea el ribete blanco
        g.setColor(new Color(0x9AA0A6));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        int i = 0;
        for (Map.Entry<String, BufferedImage> e : images.entrySet()) {
            int x = (i % cols) * cell;
            int y = (i / cols) * (cell + label);
            // A tamaño real: reescalar con drawImage desplaza visualmente las rayas finas (aliasing)
            g.drawImage(e.getValue(), x + (cell - thumb) / 2, y + 12, null);
            g.setColor(BLACK);
            int tw = g.getFontMetrics().stringWidth(e.getKey());
            g.drawString(e.getKey(), x + (cell - tw) / 2, y + cell + 8);
            i++;
        }
        g.dispose();
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("No se puede crear " + dir.getAbsolutePath());
        }
        ImageIO.write(sheet, "png", file);
    }
}
