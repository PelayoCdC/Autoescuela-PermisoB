package com.pelayo.autoescuela.app;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Disposable;
import net.mgsx.gltf.loaders.glb.GLBAssetLoader;
import net.mgsx.gltf.loaders.gltf.GLTFAssetLoader;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

/**
 * Gestor central de recursos. Los recursos pesados (modelos glTF, texturas,
 * sonidos) se cargan de forma asíncrona con el {@link AssetManager}; las fuentes
 * se generan con FreeType al terminar la carga inicial.
 */
public class Assets implements Disposable {

    /** Caracteres extra imprescindibles para el español. */
    private static final String SPANISH_CHARS = "áéíóúüñÁÉÍÓÚÜÑ¿¡ºª€—·«»°•–←→";

    private static final String FONT_FILE = "fonts/Roboto-Regular.ttf";

    public final AssetManager manager = new AssetManager();

    public BitmapFont fontHuge;
    public BitmapFont fontTitle;
    public BitmapFont fontBold;
    public BitmapFont fontBody;
    public BitmapFont fontSmall;

    public Assets() {
        // Modelos glTF (.gltf con ficheros sueltos) y binarios (.glb)
        manager.setLoader(SceneAsset.class, ".gltf", new GLTFAssetLoader());
        manager.setLoader(SceneAsset.class, ".glb", new GLBAssetLoader());
    }

    /** Encola los recursos necesarios para el menú y las pantallas de teoría. */
    public void queueInitial() {
        // Por ahora no hay recursos pesados iniciales: los escenarios 3D se
        // cargan al entrar en cada pantalla de conducción.
    }

    /** Avanza la carga; devuelve true cuando ha terminado. */
    public boolean update() {
        return manager.update();
    }

    public float getProgress() {
        return manager.getProgress();
    }

    /** Termina lo pendiente y genera las fuentes. Llamar desde el hilo de render. */
    public void finishInitial() {
        manager.finishLoading();
        generateFonts();
    }

    private void generateFonts() {
        FreeTypeFontGenerator generator =
            new FreeTypeFontGenerator(Gdx.files.internal(FONT_FILE));
        // Solo hay Roboto Regular: las variantes "negrita" se consiguen con un
        // contorno del mismo color que engrosa el trazo
        // La interfaz se diseña a 720 líneas y se escala a la ventana: si las fuentes se generaran a ese
        // tamaño se verían borrosas al ampliarlas. Se generan a la resolución del monitor y se reducen
        // con mipmaps cuando la ventana es más pequeña.
        float density = density();
        fontHuge = generate(generator, 60, 1.6f, density);
        fontTitle = generate(generator, 40, 1.0f, density);
        fontBold = generate(generator, 24, 0.7f, density);
        fontBody = generate(generator, 24, 0f, density);
        fontSmall = generate(generator, 18, 0f, density);
        generator.dispose();
    }

    /** Píxeles reales por unidad de interfaz con la pantalla completa (1 a 720p, 1,5 a 1080p, 2 a 1440p). */
    private static float density() {
        float h;
        try {
            h = Math.max(Gdx.graphics.getDisplayMode().height, Gdx.graphics.getHeight());
        } catch (RuntimeException e) {
            h = Gdx.graphics.getHeight();
        }
        return Math.max(1f, Math.min(2.5f, h / 720f));
    }

    private static BitmapFont generate(FreeTypeFontGenerator generator, int size, float bold, float density) {
        FreeTypeFontGenerator.FreeTypeFontParameter p =
            new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = Math.round(size * density);
        bold *= density;
        p.characters = FreeTypeFontGenerator.DEFAULT_CHARS + SPANISH_CHARS;
        p.color = Color.WHITE;
        if (bold > 0f) {
            p.borderWidth = bold;
            p.borderColor = Color.WHITE;
            p.spaceX = -(int) Math.ceil(bold);
        }
        p.genMipMaps = density > 1.01f;
        p.minFilter = p.genMipMaps ? Texture.TextureFilter.MipMapLinearLinear : Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        p.incremental = false;
        BitmapFont font = generator.generateFont(p);
        font.getData().markupEnabled = true;
        font.getData().setScale(size / (float) p.size);
        font.setUseIntegerPositions(false);
        return font;
    }

    @Override
    public void dispose() {
        if (fontHuge != null) fontHuge.dispose();
        if (fontTitle != null) fontTitle.dispose();
        if (fontBold != null) fontBold.dispose();
        if (fontBody != null) fontBody.dispose();
        if (fontSmall != null) fontSmall.dispose();
        manager.dispose();
    }
}
