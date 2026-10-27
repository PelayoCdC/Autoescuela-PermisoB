package com.pelayo.autoescuela.lwjgl3;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Icono de la app (Java2D): fondo azul como la placa «L» de los coches de
 * autoescuela, un volante blanco y una «L» en el buje. Genera el icono de
 * Android (normal y adaptativo, en todas las densidades) y los de la ventana
 * de escritorio.
 *
 * <pre>gradlew appIcon</pre>
 */
public final class AppIconGenerator {

    private static final Color BLUE_TOP = new Color(0x2a7de1);
    private static final Color BLUE_BOTTOM = new Color(0x0d47a1);
    private static final Color WHITE = new Color(0xffffff);

    private AppIconGenerator() {
    }

    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0] : ".");
        File res = new File(root, "android/src/main/res");
        String[] densities = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        float[] scales = {1f, 1.5f, 2f, 3f, 4f};
        for (int i = 0; i < densities.length; i++) {
            File dir = new File(res, "mipmap-" + densities[i]);
            dir.mkdirs();
            ImageIO.write(legacy(Math.round(48 * scales[i])), "png", new File(dir, "ic_launcher.png"));
            ImageIO.write(foreground(Math.round(108 * scales[i])), "png", new File(dir, "ic_launcher_foreground.png"));
        }
        File icons = new File(root, "assets/icons");
        icons.mkdirs();
        for (int size : new int[] {16, 32, 64, 128}) {
            ImageIO.write(legacy(size), "png", new File(icons, "icon" + size + ".png"));
        }
        ImageIO.write(legacy(512), "png", new File(root, "assets/cache/app_icon_512.png"));
        System.out.println("Iconos generados en " + res.getAbsolutePath() + " y " + icons.getAbsolutePath());
    }

    private static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    /** Icono completo: cuadrado redondeado azul con el volante (Android antiguo y escritorio). */
    static BufferedImage legacy(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        float pad = size * 0.04f;
        float arc = size * 0.22f;
        g.setPaint(new GradientPaint(0, 0, BLUE_TOP, 0, size, BLUE_BOTTOM));
        g.fill(new RoundRectangle2D.Float(pad, pad, size - 2 * pad, size - 2 * pad, arc, arc));
        // Ribete blanco fino, como el borde de la placa
        if (size >= 32) {
            g.setColor(new Color(255, 255, 255, 70));
            g.setStroke(new BasicStroke(size * 0.018f));
            float in = pad + size * 0.035f;
            g.draw(new RoundRectangle2D.Float(in, in, size - 2 * in, size - 2 * in, arc * 0.8f, arc * 0.8f));
        }
        wheel(g, size / 2f, size / 2f, size * 0.36f, size >= 32);
        g.dispose();
        return img;
    }

    /** Capa delantera del icono adaptativo (108 dp; lo visible seguro es el círculo central de 66 dp). */
    static BufferedImage foreground(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(img);
        wheel(g, size / 2f, size / 2f, size * 0.27f, true);
        g.dispose();
        return img;
    }

    /** Volante blanco (aro, tres radios y buje) con una «L» azul en el buje. */
    private static void wheel(Graphics2D g, float cx, float cy, float r, boolean letter) {
        float rim = r * 0.17f;
        g.setColor(new Color(0, 0, 0, 50)); // sombra suave
        g.setStroke(new BasicStroke(rim, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float sh = r * 0.04f;
        g.draw(new Ellipse2D.Float(cx - r + sh, cy - r + sh * 1.5f, 2 * r, 2 * r));
        g.setColor(WHITE);
        g.draw(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
        float hub = r * 0.42f;
        float spoke = r * 0.2f;
        g.setStroke(new BasicStroke(spoke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Radios: izquierda, derecha y abajo (como el volante de la cabina)
        float end = r - spoke * 0.5f; // las puntas redondeadas quedan dentro del aro
        g.draw(new Line2D.Float(cx - end, cy + r * 0.05f, cx - hub * 0.8f, cy + r * 0.05f));
        g.draw(new Line2D.Float(cx + hub * 0.8f, cy + r * 0.05f, cx + end, cy + r * 0.05f));
        g.draw(new Line2D.Float(cx, cy + hub * 0.8f, cx, cy + end));
        g.fill(new Ellipse2D.Float(cx - hub, cy - hub, 2 * hub, 2 * hub));
        if (letter) {
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(6, Math.round(hub * 1.55f)));
            FontRenderContext frc = g.getFontRenderContext();
            GlyphVector gv = font.createGlyphVector(frc, "L");
            java.awt.geom.Rectangle2D b = gv.getVisualBounds();
            g.setColor(BLUE_BOTTOM);
            g.drawGlyphVector(gv, (float) (cx - b.getCenterX()), (float) (cy - b.getCenterY()));
        }
    }
}
