package com.pelayo.autoescuela.sim.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.collision.btConeShape;
import com.badlogic.gdx.physics.bullet.collision.btCylinderShape;
import com.badlogic.gdx.physics.bullet.collision.btStaticPlaneShape;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.pelayo.autoescuela.data.ScenarioDef;
import com.pelayo.autoescuela.render.MarkingRenderer;
import com.pelayo.autoescuela.render.PlaceholderModels;
import com.pelayo.autoescuela.render.env.EnvModels;
import com.pelayo.autoescuela.app.CarPaint;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.math.collision.BoundingBox;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import com.pelayo.autoescuela.render.env.EnvTextures;
import com.pelayo.autoescuela.render.SceneRenderer;
import com.pelayo.autoescuela.sim.BodyTag;
import com.pelayo.autoescuela.sim.PhysicsWorld;
import net.mgsx.gltf.scene3d.scene.Scene;

/**
 * Convierte un {@link ScenarioDef} en cuerpos de bullet e instancias de
 * render (placeholders; los glTF del catálogo llegan en M6). Con
 * {@code renderer == null} solo crea la física (tests).
 */
public final class ScenarioLoader {

    /** Altura de la hierba, un poco por debajo del asfalto (y = 0). */
    private static final float GRASS_Y = -0.01f;
    /** Longitud máxima de una losa de calzada: las cajas largas dan raycasts imprecisos. */
    private static final float MAX_SLAB = 50f;

    private static final Color GRASS = new Color(0.33f, 0.48f, 0.24f, 1f);
    private static final Color CONE = new Color(1f, 0.45f, 0.05f, 1f);
    /** Coches aparcados: colores oficiales de la gama Clio III (los más vistos en la calle). */
    private static final Color[] CAR_COLORS = {
        CarPaint.BLANC_GLACIER.linear(), CarPaint.GRIS_PLATINE.linear(), CarPaint.NOIR_NACRE.linear(),
        CarPaint.BLEU_EXTREME.linear(), CarPaint.ROUGE_DYNA.linear(), CarPaint.GRIS_ECLIPSE.linear(),
        CarPaint.BLEU_ICEBERG.linear(), CarPaint.BEIGE_POIVRE.linear(), CarPaint.ROUGE_VIF.linear()
    };

    private ScenarioLoader() {
    }

    public static Scenario load(ScenarioDef def, PhysicsWorld physics, SceneRenderer renderer) {
        Scenario scenario = new Scenario(def, physics, renderer);
        // Terreno base (hierba): pisarlo es salirse de la calzada. Es un plano
        // infinito porque el raycast de bullet contra cajas enormes es impreciso.
        physics.addStatic(new btStaticPlaneShape(new Vector3(0f, 1f, 0f), GRASS_Y),
            new Matrix4(), BodyTag.OTHER, 0.8f);
        if (renderer != null) {
            Model grass = EnvModels.box(3000f, 0.02f, 3000f,
                EnvModels.textured(renderer.textures.grass, Color.WHITE, 0.95f), EnvTextures.GRASS_TILE, EnvTextures.GRASS_TILE);
            ModelInstance instance = new ModelInstance(grass);
            instance.transform.setToTranslation(0f, GRASS_Y - 0.01f, 0f);
            scenario.addModel(grass);
            scenario.addScene(new Scene(instance));
        }
        int carColor = 0;
        for (ScenarioDef.ModuleDef m : def.modules) {
            Matrix4 pose = new Matrix4().setToTranslation(m.x, m.y, m.z).rotate(Vector3.Y, m.yawDeg);
            Color color = m.color != null ? Color.valueOf(m.color) : null;
            switch (m.type) {
                case "ground":
                case "road":
                    addSlabs(scenario, physics, m.width, 0.2f, m.length, pose.translate(0f, -0.1f, 0f),
                        BodyTag.ROAD, color, 0.9f);
                    break;
                case "sidewalk":
                    addSlabs(scenario, physics, m.width, m.curbHeight, m.length,
                        pose.translate(0f, m.curbHeight / 2f, 0f), BodyTag.CURB, color, 0.8f);
                    break;
                case "slope": {
                    float angle = (float) Math.atan(m.gradePct / 100f) * MathUtils.radiansToDegrees;
                    // El centro del borde bajo está en (x, z); la losa sube hacia +Z local
                    pose.rotate(Vector3.X, -angle).translate(0f, -0.1f, m.length / 2f);
                    addBox(scenario, physics, m.width, 0.2f, m.length, pose, BodyTag.ROAD, color, 0.9f);
                    break;
                }
                case "cone":
                    addCone(scenario, physics, m, pose);
                    break;
                case "parked_car":
                    addParkedCar(scenario, physics, pose, color != null ? color : CAR_COLORS[carColor++ % CAR_COLORS.length]);
                    break;
                case "wall":
                    addBox(scenario, physics, 0.25f, m.height, m.length, pose.translate(0f, m.height / 2f, 0f),
                        BodyTag.BARRIER, color, 0.7f);
                    break;
                case "block":
                    addBox(scenario, physics, m.width, m.height, m.length, pose.translate(0f, m.height / 2f, 0f),
                        BodyTag.BARRIER, color, 0.7f);
                    break;
                case "road_arc":
                    addArc(scenario, physics, m.arc, m.width, 0.2f, -0.1f, BodyTag.ROAD, color, 0.9f);
                    break;
                case "sidewalk_arc":
                    addArc(scenario, physics, m.arc, m.width, m.curbHeight, m.curbHeight / 2f, BodyTag.CURB, color, 0.8f);
                    break;
                case "island": {
                    btCylinderShape shape = new btCylinderShape(new Vector3(m.radius, m.height / 2f, m.radius));
                    physics.addStatic(shape, pose.translate(0f, m.height / 2f, 0f), BodyTag.BARRIER, 0.7f);
                    if (renderer != null) {
                        Model model = PlaceholderModels.cylinder(m.radius, m.height, color != null ? color : GRASS);
                        ModelInstance instance = new ModelInstance(model);
                        instance.transform.set(pose);
                        scenario.addModel(model);
                        scenario.addScene(new Scene(instance));
                    }
                    break;
                }
                case "sign": {
                    // Poste de la señal: chocar con él es como con una valla
                    btCylinderShape pole = new btCylinderShape(new Vector3(0.06f, 1.25f, 0.06f));
                    physics.addStatic(pole, new Matrix4(pose).translate(0f, 1.25f, 0f), BodyTag.BARRIER, 0.6f);
                    if (renderer != null) {
                        String kind = m.sign == null ? "stop" : m.sign;
                        ModelInstance instance = scenario.instanceFromCatalog("sign_" + kind);
                        if (instance == null) {
                            Model model = PlaceholderModels.sign(kind);
                            instance = new ModelInstance(model);
                            scenario.addModel(model);
                        }
                        instance.transform.set(pose);
                        scenario.addScene(new Scene(instance));
                    }
                    break;
                }
                default:
                    throw new IllegalArgumentException("Módulo desconocido en " + def.id + ": " + m.type);
            }
        }
        if (renderer != null) {
            // El decorado (edificios, árboles, farolas) se genera al dibujarlo; también choca: es un obstáculo
            // rígido como una valla. Está fuera del recinto de la práctica, así que sin gráficos (pruebas) no hace falta
            com.badlogic.gdx.utils.Array<com.pelayo.autoescuela.render.env.SceneryBuilder.Collider> colliders =
                new com.badlogic.gdx.utils.Array<>();
            Model scenery = com.pelayo.autoescuela.render.env.SceneryBuilder.build(def, renderer.textures, colliders);
            scenario.addModel(scenery);
            scenario.addScene(new Scene(new ModelInstance(scenery)));
            for (com.pelayo.autoescuela.render.env.SceneryBuilder.Collider c : colliders) {
                btCollisionShape shape = c.box ? new btBoxShape(new Vector3(c.sx / 2f, c.sy / 2f, c.sz / 2f))
                    : new btCylinderShape(new Vector3(c.sx, c.sy / 2f, c.sx));
                physics.addStatic(shape, new Matrix4().setToTranslation(c.x, c.y, c.z).rotate(Vector3.Y, c.yawDeg),
                    BodyTag.BARRIER, 0.6f);
            }
        }
        addTraffic(scenario, physics);
        if (renderer != null && scenario.geometry.markings.size > 0) {
            Model markings = MarkingRenderer.build(scenario.geometry.markings);
            scenario.addModel(markings);
            scenario.addScene(new Scene(new ModelInstance(markings)));
        }
        return scenario;
    }

    /**
     * Calzada o acera curva: tramos rectos de unos 5° a lo largo del arco.
     * {@code yCenter} es la altura del centro de cada losa.
     */
    private static void addArc(Scenario scenario, PhysicsWorld physics, ScenarioDef.ArcDef arc, float width,
                               float height, float yCenter, BodyTag tag, Color color, float friction) {
        if (arc == null) {
            throw new IllegalArgumentException("Módulo *_arc sin 'arc' en " + scenario.def.id);
        }
        int segments = Math.max(1, (int) Math.ceil(Math.abs(arc.sweepDeg) / 5f));
        float step = arc.sweepDeg / segments;
        float chord = Math.abs(arc.radius * step * MathUtils.degreesToRadians);
        for (int i = 0; i < segments; i++) {
            float a = arc.startDeg + step * (i + 0.5f);
            float px = arc.cx + arc.radius * MathUtils.sinDeg(a);
            float pz = arc.cz + arc.radius * MathUtils.cosDeg(a);
            // Tangente (para θ creciente): (cos a, -sin a) → rumbo
            float yaw = MathUtils.atan2(MathUtils.cosDeg(a), -MathUtils.sinDeg(a)) * MathUtils.radiansToDegrees;
            Matrix4 t = new Matrix4().setToTranslation(px, yCenter, pz).rotate(Vector3.Y, yaw);
            addBox(scenario, physics, width, height, chord + 0.15f, t, tag, color, friction);
        }
    }

    /** Caja larga troceada en losas de como mucho MAX_SLAB a lo largo de Z local. */
    private static void addSlabs(Scenario scenario, PhysicsWorld physics, float w, float h, float d,
                                 Matrix4 transform, BodyTag tag, Color color, float friction) {
        int n = Math.max(1, (int) Math.ceil(d / MAX_SLAB));
        float slab = d / n;
        for (int i = 0; i < n; i++) {
            float zc = -d / 2f + slab * (i + 0.5f);
            addBox(scenario, physics, w, h, slab, new Matrix4(transform).translate(0f, 0f, zc), tag, color, friction);
        }
    }

    private static void addBox(Scenario scenario, PhysicsWorld physics, float w, float h, float d,
                               Matrix4 transform, BodyTag tag, Color color, float friction) {
        btBoxShape shape = new btBoxShape(new Vector3(w / 2f, h / 2f, d / 2f));
        physics.addStatic(shape, transform, tag, friction);
        if (scenario.hasRenderer()) {
            Model model = surfaceModel(scenario, w, h, d, tag, color);
            ModelInstance instance = new ModelInstance(model);
            instance.transform.set(transform);
            scenario.addModel(model);
            scenario.addScene(new Scene(instance));
        }
    }

    /**
     * Modelo de una losa: con color propio del módulo, liso; si no, con la
     * textura de su tipo (asfalto, baldosa de acera u hormigón).
     */
    private static Model surfaceModel(Scenario scenario, float w, float h, float d, BodyTag tag, Color color) {
        if (color != null) {
            return PlaceholderModels.box(w, h, d, color);
        }
        EnvTextures tex = scenario.renderer().textures;
        switch (tag) {
            case ROAD:
                return EnvModels.box(w, h, d, EnvModels.textured(tex.asphalt, Color.WHITE, 0.92f),
                    EnvTextures.ASPHALT_TILE, EnvTextures.ASPHALT_TILE);
            case CURB:
                return EnvModels.box(w, h, d, EnvModels.textured(tex.sidewalk, Color.WHITE, 0.85f),
                    EnvTextures.SIDEWALK_TILE, EnvTextures.SIDEWALK_TILE);
            default:
                return EnvModels.box(w, h, d, EnvModels.textured(tex.concrete, Color.WHITE, 0.9f),
                    EnvTextures.CONCRETE_TILE, EnvTextures.CONCRETE_TILE);
        }
    }

    private static void addCone(Scenario scenario, PhysicsWorld physics, ScenarioDef.ModuleDef m, Matrix4 pose) {
        float radius = 0.16f;
        float height = 0.5f;
        btCollisionShape shape = new btConeShape(radius, height);
        pose.translate(0f, height / 2f + 0.01f, 0f);
        btRigidBody body = physics.addDynamic(shape, 1.2f, pose, BodyTag.CONE, 0.7f);
        ModelInstance instance = null;
        if (scenario.hasRenderer()) {
            ModelInstance fromFile = scenario.instanceFromCatalog("cone");
            if (fromFile != null) {
                // El glTF tiene el origen en el suelo; el cuerpo, en el centro del cono
                instance = fromFile;
                instance.transform.set(pose).translate(0f, -height / 2f, 0f);
            } else {
                Model model = PlaceholderModels.cone(radius, height, CONE);
                instance = new ModelInstance(model);
                instance.transform.set(pose);
                scenario.addModel(model);
            }
            scenario.addScene(new Scene(instance));
        }
        scenario.addMovable(new Scenario.Movable(m.name, body, instance, fromFile(scenario, "cone") ? -height / 2f : 0f));
    }

    /** Ropa de los peatones: camisetas y pantalones variados. */
    private static final Color[] SHIRTS = {
        new Color(0.75f, 0.2f, 0.2f, 1f), new Color(0.2f, 0.45f, 0.75f, 1f), new Color(0.9f, 0.75f, 0.2f, 1f),
        new Color(0.3f, 0.6f, 0.35f, 1f), new Color(0.85f, 0.85f, 0.85f, 1f), new Color(0.55f, 0.3f, 0.6f, 1f)
    };
    private static final Color[] TROUSERS = {
        new Color(0.12f, 0.14f, 0.2f, 1f), new Color(0.25f, 0.25f, 0.27f, 1f), new Color(0.35f, 0.28f, 0.2f, 1f)
    };

    /**
     * Coches y peatones del escenario: cuerpo cinemático (para chocar), modelo (el mismo Clio con otro color,
     * o un peatón sencillo) y el sistema que los mueve antes de cada paso de física.
     */
    private static void addTraffic(Scenario scenario, PhysicsWorld physics) {
        int index = 0;
        for (ScenarioDef.TrafficDef t : scenario.def.traffic) {
            float[][] path = t.resolvedPath();
            for (int i = 0; i < Math.max(1, t.count); i++, index++) {
                TrafficAgent a = new TrafficAgent(t, path, t.offset + i * t.gap);
                boolean car = a.type == TrafficAgent.Type.CAR;
                btCollisionShape shape = car ? new btBoxShape(new Vector3(0.86f, 0.7f, 2.0f))
                    : new btCylinderShape(new Vector3(0.28f, 0.88f, 0.28f));
                a.bodyY = car ? 0.72f : 0.88f;
                a.motion = new PhysicsWorld.DefaultMotionState(new Matrix4());
                a.body = physics.addKinematic(shape, new Matrix4(), car ? BodyTag.TRAFFIC_CAR : BodyTag.PEDESTRIAN,
                    a.motion);
                a.updatePose();
                if (scenario.hasRenderer()) {
                    ModelInstance instance;
                    if (car) {
                        Color color = t.color != null ? Color.valueOf(t.color) : CAR_COLORS[(index * 4 + 1) % CAR_COLORS.length];
                        if (scenario.playerCarAsset() != null) {
                            instance = new ModelInstance(scenario.playerCarAsset().scene.model);
                            com.pelayo.autoescuela.render.CarView.darkenInterior(instance);
                            Material body = instance.getMaterial("Body");
                            if (body != null) {
                                body.set(PBRColorAttribute.createBaseColorFactor(color));
                            }
                            a.renderY = -instance.calculateBoundingBox(new BoundingBox()).min.y;
                        } else {
                            Model model = PlaceholderModels.parkedCar(1.72f, 1.45f, 4.0f, color);
                            scenario.addModel(model);
                            instance = new ModelInstance(model);
                        }
                    } else {
                        Model model = PlaceholderModels.pedestrian(SHIRTS[index % SHIRTS.length],
                            TROUSERS[index % TROUSERS.length]);
                        scenario.addModel(model);
                        instance = new ModelInstance(model);
                    }
                    a.instance = instance;
                    scenario.addScene(new Scene(instance));
                }
                scenario.traffic.agents.add(a);
            }
        }
        if (scenario.traffic.agents.size > 0) {
            scenario.traffic.syncRender();
            physics.preStep.add(() -> scenario.traffic.step(PhysicsWorld.DT));
        }
    }

    private static boolean fromFile(Scenario scenario, String type) {
        return scenario.hasRenderer() && scenario.catalogHas(type);
    }

    private static void addParkedCar(Scenario scenario, PhysicsWorld physics, Matrix4 pose, Color color) {
        float w = 1.72f;
        float h = 1.45f;
        float l = 4.0f;
        btBoxShape shape = new btBoxShape(new Vector3(w / 2f, h / 2f, l / 2f));
        Matrix4 t = new Matrix4(pose).translate(0f, h / 2f, 0f);
        physics.addStatic(shape, t, BodyTag.PARKED_CAR, 0.6f);
        if (scenario.hasRenderer()) {
            ModelInstance instance = scenario.instanceFromCatalog("parked_car");
            if (instance != null) {
                instance.transform.set(pose);
            } else if (scenario.playerCarAsset() != null) {
                // El mismo Clio que conduce el alumno, con otra pintura (la instancia copia los materiales)
                instance = new ModelInstance(scenario.playerCarAsset().scene.model);
                com.pelayo.autoescuela.render.CarView.darkenInterior(instance);
                Material body = instance.getMaterial("Body");
                if (body != null) {
                    body.set(PBRColorAttribute.createBaseColorFactor(color));
                }
                BoundingBox bb = instance.calculateBoundingBox(new BoundingBox());
                instance.transform.set(pose).translate(0f, -bb.min.y, 0f); // ruedas apoyadas en el suelo
            } else {
                Model model = PlaceholderModels.parkedCar(w, h, l, color);
                instance = new ModelInstance(model);
                scenario.addModel(model);
                instance.transform.set(pose);
            }
            scenario.addScene(new Scene(instance));
        }
    }
}
