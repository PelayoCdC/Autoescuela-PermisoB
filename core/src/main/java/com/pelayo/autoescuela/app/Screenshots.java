package com.pelayo.autoescuela.app;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import java.nio.ByteBuffer;

/** Guarda el framebuffer actual como PNG (útil para capturas automáticas). */
public final class Screenshots {

    private Screenshots() {
    }

    /** Captura a {@code cache/<name>.png} (relativo al directorio local). */
    public static FileHandle save(String name) {
        int w = Gdx.graphics.getBackBufferWidth();
        int h = Gdx.graphics.getBackBufferHeight();
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, w, h);
        // El framebuffer viene boca abajo: invertir filas
        ByteBuffer pixels = pixmap.getPixels();
        int bytesPerRow = w * 4;
        byte[] row = new byte[bytesPerRow];
        byte[] other = new byte[bytesPerRow];
        for (int y = 0; y < h / 2; y++) {
            int top = y * bytesPerRow;
            int bottom = (h - 1 - y) * bytesPerRow;
            pixels.position(top);
            pixels.get(row);
            pixels.position(bottom);
            pixels.get(other);
            pixels.position(top);
            pixels.put(other);
            pixels.position(bottom);
            pixels.put(row);
        }
        pixels.position(0);
        FileHandle file = Gdx.files.local("cache/" + name + ".png");
        PixmapIO.writePNG(file, pixmap);
        pixmap.dispose();
        Gdx.app.log("Screenshots", "guardada " + file.path());
        return file;
    }
}
