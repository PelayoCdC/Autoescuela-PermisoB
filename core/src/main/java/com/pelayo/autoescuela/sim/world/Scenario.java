package com.pelayo.autoescuela.sim.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.scene3d.scene.SceneAsset;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.pelayo.autoescuela.data.ScenarioDef;
import com.pelayo.autoescuela.render.SceneRenderer;
import com.pelayo.autoescuela.sim.PhysicsWorld;
import net.mgsx.gltf.scene3d.scene.Scene;

/**
 * Escenario cargado: geometría para los evaluadores, cuerpos en el mundo
 * físico, instancias de render y la pose inicial. Lo construye
 * {@link ScenarioLoader}.
 */
public class Scenario implements Disposable {

    /** Cono u otro objeto móvil cuyo desplazamiento cuenta como toque. */
    public static class Movable {
        public final String name;
        public final btRigidBody body;
        public final ModelInstance instance;
        public final Vector3 initialPosition = new Vector3();
        /** Desplazamiento vertical del modelo respecto al cuerpo (glTF con origen en el suelo). */
        private final float renderOffsetY;
        private final Matrix4 tmp = new Matrix4();
        private final Vector3 tmpV = new Vector3();

        Movable(String name, btRigidBody body, ModelInstance instance, float renderOffsetY) {
            this.name = name;
            this.body = body;
            this.instance = instance;
            this.renderOffsetY = renderOffsetY;
            body.getWorldTransform(tmp);
            tmp.getTranslation(initialPosition);
        }

        /** Se ha movido más de {@code tolerance} metros desde su sitio. */
        public boolean isDisplaced(float tolerance) {
            body.getWorldTransform(tmp);
            tmp.getTranslation(tmpV);
            return tmpV.dst2(initialPosition) > tolerance * tolerance;
        }

        void syncRender() {
            if (instance != null) {
                body.getWorldTransform(instance.transform);
                if (renderOffsetY != 0f) {
                    instance.transform.translate(0f, renderOffsetY, 0f);
                }
            }
        }
    }

    /** Modelos glTF del catálogo assets/models/env/<tipo>.glb, cargados una vez por escenario. */
    private final ObjectMap<String, SceneAsset> catalog = new ObjectMap<>();
    private final ObjectSet<String> catalogMissing = new ObjectSet<>();

    boolean catalogHas(String type) {
        if (catalog.containsKey(type)) {
            return true;
        }
        if (catalogMissing.contains(type)) {
            return false;
        }
        FileHandle file = Gdx.files.internal("models/env/" + type + ".glb");
        if (!file.exists()) {
            catalogMissing.add(type);
            return false;
        }
        try {
            catalog.put(type, new GLBLoader().load(file));
            return true;
        } catch (Exception e) {
            Gdx.app.error("Scenario", "No se pudo cargar " + file, e);
            catalogMissing.add(type);
            return false;
        }
    }

    /** Instancia del modelo del catálogo para ese tipo, o null si no hay fichero. */
    ModelInstance instanceFromCatalog(String type) {
        if (!catalogHas(type)) {
            return null;
        }
        return new ModelInstance(catalog.get(type).scene.model);
    }

    public final ScenarioDef def;
    public final ScenarioGeometry geometry;
    public final Matrix4 startPose = new Matrix4();
    public final Array<Movable> movables = new Array<>();
    /** Coches y peatones en movimiento (vacío si el escenario no tiene). */
    public final TrafficSystem traffic = new TrafficSystem();

    private final PhysicsWorld physics;
    private final SceneRenderer renderer;
    private final Array<Model> models = new Array<>();
    private final Array<Scene> scenes = new Array<>();

    Scenario(ScenarioDef def, PhysicsWorld physics, SceneRenderer renderer) {
        this.def = def;
        this.physics = physics;
        this.renderer = renderer;
        this.geometry = new ScenarioGeometry(def);
        startPose.setToTranslation(def.startPose.x, def.startPose.y, def.startPose.z)
            .rotate(Vector3.Y, def.startPose.yawDeg)
            .rotate(Vector3.X, def.startPose.pitchDeg);
    }

    boolean hasRenderer() {
        return renderer != null;
    }

    SceneRenderer renderer() {
        return renderer;
    }

    /** Modelo del coche del jugador (car.glb), cargado una vez, para los coches aparcados; o null. */
    SceneAsset playerCarAsset() {
        String key = "__player_car";
        if (catalog.containsKey(key)) {
            return catalog.get(key);
        }
        if (catalogMissing.contains(key)) {
            return null;
        }
        FileHandle file = Gdx.files.internal(com.pelayo.autoescuela.render.CarView.CAR_GLB);
        try {
            if (file.exists()) {
                SceneAsset asset = new GLBLoader().load(file);
                catalog.put(key, asset);
                return asset;
            }
        } catch (Exception e) {
            Gdx.app.error("Scenario", "No se pudo cargar " + file, e);
        }
        catalogMissing.add(key);
        return null;
    }

    void addModel(Model model) {
        models.add(model);
    }

    void addScene(Scene scene) {
        scenes.add(scene);
        if (renderer != null) {
            renderer.add(scene);
        }
    }

    void addMovable(Movable movable) {
        movables.add(movable);
    }

    /** Copia las poses de los objetos móviles al render. Llamar una vez por frame. */
    public void syncRender() {
        for (Movable m : movables) {
            m.syncRender();
        }
        traffic.syncRender();
    }

    /** Algún cono desplazado más de 3 cm. */
    public boolean anyMovableDisplaced() {
        for (Movable m : movables) {
            if (m.isDisplaced(0.03f)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void dispose() {
        if (renderer != null) {
            for (Scene s : scenes) {
                renderer.remove(s);
            }
        }
        scenes.clear();
        for (Model m : models) {
            m.dispose();
        }
        models.clear();
        movables.clear();
        traffic.agents.clear();
        for (SceneAsset asset : catalog.values()) {
            asset.dispose();
        }
        catalog.clear();
        physics.clearBodies();
    }
}
