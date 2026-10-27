package com.pelayo.autoescuela.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cubemap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.pelayo.autoescuela.render.env.EnvTextures;
import net.mgsx.gltf.scene3d.attributes.FogAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRCubemapAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx;
import net.mgsx.gltf.scene3d.lights.DirectionalShadowLight;
import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneManager;
import net.mgsx.gltf.scene3d.scene.SceneSkybox;
import net.mgsx.gltf.scene3d.shaders.PBRShaderConfig;
import net.mgsx.gltf.scene3d.shaders.PBRShaderProvider;
import net.mgsx.gltf.scene3d.utils.IBLBuilder;

/**
 * Escena 3D con el {@link SceneManager} de gdx-gltf: sol direccional (con
 * sombras que siguen a la cámara si la calidad lo permite), iluminación de
 * entorno (IBL procedural de exterior), cielo y niebla de distancia que
 * funde el horizonte con el cielo. Guarda también las texturas del entorno.
 */
public class SceneRenderer implements Disposable {

    /** Color del horizonte: la niebla lleva el suelo lejano a este tono. */
    public static final Color HORIZON = new Color(0.74f, 0.82f, 0.90f, 1f);

    public final SceneManager sceneManager;
    public final DirectionalLightEx sun;
    /** Texturas de asfalto, hierba, aceras, fachadas... (null en tests sin gráficos). */
    public final EnvTextures textures;

    private final DirectionalShadowLight shadowLight;
    /** Mismo sol sin sombras, para quitarlas en marcha si el móvil va justo. */
    private DirectionalLightEx plainSun;
    private boolean shadowsOn;
    private final Cubemap environmentCubemap;
    private final Cubemap diffuseCubemap;
    private final Cubemap specularCubemap;
    private final Texture brdfLut;
    final SceneSkybox skybox;
    private final Vector3 tmp = new Vector3();

    /**
     * @param shadowMapSize lado del mapa de sombras (0 = sin sombras)
     */
    public SceneRenderer(int shadowMapSize, boolean highQualityTextures) {
        PBRShaderConfig config = PBRShaderProvider.createDefaultConfig();
        config.numDirectionalLights = 1;
        config.numPointLights = 0;
        config.numSpotLights = 0;
        config.numBones = 0;
        sceneManager = new SceneManager(PBRShaderProvider.createDefault(config),
            PBRShaderProvider.createDefaultDepth(PBRShaderProvider.createDefaultDepthConfig()));

        // Sol de media mañana, algo lateral para que las sombras den volumen
        Vector3 sunDir = new Vector3(-0.45f, -1f, 0.55f).nor();
        if (shadowMapSize > 0) {
            shadowLight = new DirectionalShadowLight(shadowMapSize, shadowMapSize, 70f, 70f, 1f, 160f);
            sun = shadowLight;
            sceneManager.environment.set(new PBRFloatAttribute(PBRFloatAttribute.ShadowBias, 1f / 512f));
        } else {
            shadowLight = null;
            sun = new DirectionalLightEx();
        }
        sun.direction.set(sunDir);
        sun.color.set(1f, 0.97f, 0.92f, 1f);
        sun.intensity = 3.2f;
        sceneManager.environment.add(sun);
        shadowsOn = shadowLight != null;
        if (shadowLight != null) {
            plainSun = new DirectionalLightEx();
            plainSun.direction.set(sun.direction);
            plainSun.color.set(sun.color);
            plainSun.intensity = sun.intensity;
        }
        sceneManager.setAmbientLight(0.55f);

        IBLBuilder ibl = IBLBuilder.createOutdoor(sun);
        environmentCubemap = ibl.buildEnvMap(512);
        diffuseCubemap = ibl.buildIrradianceMap(128);
        specularCubemap = ibl.buildRadianceMap(8);
        ibl.dispose();

        brdfLut = new Texture(Gdx.files.classpath("net/mgsx/gltf/shaders/brdfLUT.png"));
        sceneManager.environment.set(new PBRTextureAttribute(PBRTextureAttribute.BRDFLUTTexture, brdfLut));
        sceneManager.environment.set(PBRCubemapAttribute.createSpecularEnv(specularCubemap));
        sceneManager.environment.set(PBRCubemapAttribute.createDiffuseEnv(diffuseCubemap));

        // Niebla suave: el suelo y los objetos lejanos se funden con el cielo, sin línea de horizonte dura
        sceneManager.environment.set(new ColorAttribute(ColorAttribute.Fog, HORIZON));
        sceneManager.environment.set(FogAttribute.createFog(60f, 900f, 1.4f));

        skybox = new SceneSkybox(environmentCubemap);
        sceneManager.setSkyBox(skybox);

        long t0 = System.nanoTime();
        textures = new EnvTextures(highQualityTextures);
        Gdx.app.log("SceneRenderer", "Texturas del entorno en " + (System.nanoTime() - t0) / 1000000 + " ms");
    }

    public void setCamera(Camera camera) {
        sceneManager.setCamera(camera);
        if (shadowLight != null) {
            // La zona con sombras va con la cámara, adelantada hacia donde mira
            tmp.set(camera.direction.x, 0f, camera.direction.z).nor().scl(20f).add(camera.position);
            tmp.y = 0f; // el suelo, no la altura de la cámara (en la vista cenital está a 90 m)
            shadowLight.setCenter(tmp);
        }
    }

    public boolean hasShadows() {
        return shadowsOn;
    }

    /** Quita o vuelve a poner las sombras sin rehacer la escena (solo si se creó con ellas). */
    public void setShadows(boolean on) {
        if (shadowLight == null || on == shadowsOn) {
            return;
        }
        sceneManager.environment.remove(on ? plainSun : shadowLight);
        sceneManager.environment.add(on ? shadowLight : plainSun);
        shadowsOn = on;
    }

    public void add(Scene scene) {
        sceneManager.addScene(scene);
    }

    public void remove(Scene scene) {
        sceneManager.removeScene(scene);
    }

    public void resize(int width, int height) {
        sceneManager.updateViewport(width, height);
    }

    /** Actualiza animaciones/luces y dibuja la escena con la cámara activa. */
    public void render(float delta) {
        sceneManager.update(delta);
        sceneManager.render();
    }

    @Override
    public void dispose() {
        sceneManager.dispose();
        skybox.dispose();
        environmentCubemap.dispose();
        diffuseCubemap.dispose();
        specularCubemap.dispose();
        brdfLut.dispose();
        textures.dispose();
        if (shadowLight != null) {
            shadowLight.dispose();
        }
    }
}
