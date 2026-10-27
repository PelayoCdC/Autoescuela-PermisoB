package com.pelayo.autoescuela.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.pelayo.autoescuela.data.VehicleSpec;
import com.pelayo.autoescuela.sim.vehicle.BulletVehicle;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.loaders.gltf.GLTFLoader;
import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

/**
 * Enlaza el modelo del coche (glTF de {@code models/car/car.glb} si existe, o
 * el placeholder) con el estado físico: mueve el chasis, las cuatro ruedas,
 * el volante y la palanca. Busca los nodos por nombre ({@code docs/ASSETS.md})
 * y, si alguno falta, lo ignora avisando por log.
 *
 * Con el glTF, las ruedas son nodos del propio modelo; con el placeholder son
 * instancias separadas.
 */
public class CarView implements Disposable {

    public static final String CAR_GLB = "models/car/car.glb";
    public static final String CAR_GLTF = "models/car/car.gltf";

    private final VehicleSpec spec;
    private final SceneAsset asset;
    private final Model carModel;
    private final Model wheelModel;
    public final ModelInstance chassis;
    public final ModelInstance[] wheels = new ModelInstance[4];
    public final Scene chassisScene;
    public final Scene[] wheelScenes = new Scene[4];
    private final Node[] wheelNodes = new Node[4];
    private final boolean wheelsInModel;

    private final Node steeringWheel;
    private final Node gearLever;
    private final Node driverEye;
    private final Quaternion steeringRest = new Quaternion();
    private float wheelTurnsShown = -1f;
    /** Pintura con la que viene el modelo, para poder volver a ella. */
    private com.badlogic.gdx.graphics.g3d.Material originalBody;
    private final Quaternion leverRest = new Quaternion();
    private final Quaternion tmpQ = new Quaternion();
    private final Vector3 eyeOffset = new Vector3();
    private final Matrix4 tmp = new Matrix4();
    private final Matrix4 inverseChassis = new Matrix4();

    /** Carga el glTF del coche si existe; si no, usa el placeholder. */
    public static CarView create(VehicleSpec spec) {
        FileHandle glb = Gdx.files.internal(CAR_GLB);
        FileHandle gltf = Gdx.files.internal(CAR_GLTF);
        try {
            if (glb.exists()) {
                return new CarView(spec, new GLBLoader().load(glb));
            }
            if (gltf.exists()) {
                return new CarView(spec, new GLTFLoader().load(gltf));
            }
        } catch (Exception e) {
            Gdx.app.error("CarView", "No se pudo cargar el modelo del coche; se usa el placeholder", e);
        }
        return new CarView(spec);
    }

    /** Con los modelos placeholder generados en código. */
    public CarView(VehicleSpec spec) {
        this.spec = spec;
        this.asset = null;
        this.carModel = PlaceholderModels.car(spec);
        this.wheelModel = PlaceholderModels.wheel(spec);
        chassis = new ModelInstance(carModel);
        chassisScene = new Scene(chassis);
        for (int i = 0; i < 4; i++) {
            wheels[i] = new ModelInstance(wheelModel);
            wheelScenes[i] = new Scene(wheels[i]);
        }
        wheelsInModel = false;
        steeringWheel = node("steering_wheel");
        gearLever = node("gear_lever");
        driverEye = node("driver_eye");
        initRest();
    }

    /** Con un modelo glTF cargado. */
    public CarView(VehicleSpec spec, SceneAsset asset) {
        this.spec = spec;
        this.asset = asset;
        this.carModel = null;
        this.wheelModel = null;
        chassisScene = new Scene(asset.scene);
        chassis = chassisScene.modelInstance;
        String[] names = {"wheel_fl", "wheel_fr", "wheel_rl", "wheel_rr"};
        boolean all = true;
        for (int i = 0; i < 4; i++) {
            wheelNodes[i] = chassis.getNode(names[i], true);
            all &= wheelNodes[i] != null;
        }
        wheelsInModel = all;
        if (!all) {
            Gdx.app.log("CarView", "El glTF no tiene los cuatro nodos de rueda; las ruedas no se animarán");
        }
        steeringWheel = node("steering_wheel");
        gearLever = node("gear_lever");
        driverEye = node("driver_eye");
        initRest();
    }

    private void initRest() {
        darkenInterior(chassis);
        com.badlogic.gdx.graphics.g3d.Material body = chassis.getMaterial("Body");
        originalBody = body != null ? body.copy() : null;
        if (steeringWheel != null) {
            steeringRest.set(steeringWheel.rotation);
        }
        if (gearLever != null) {
            leverRest.set(gearLever.rotation);
        }
        if (driverEye != null && !spec.eyeOverride) {
            driverEye.globalTransform.getTranslation(eyeOffset);
            if (eyeOffset.isZero()) {
                eyeOffset.set(driverEye.translation);
            }
        } else {
            eyeOffset.set(spec.eyeX, spec.eyeY, spec.eyeZ);
        }
    }

    private Node node(String id) {
        Node node = chassis.getNode(id, true);
        if (node == null) {
            Gdx.app.log("CarView", "El modelo del coche no tiene el nodo '" + id + "'");
        }
        return node;
    }

    /** Centro del volante en el mundo para esa pose del chasis; false si el modelo no tiene volante. */
    public boolean getSteeringWheelCenter(com.badlogic.gdx.math.Matrix4 chassisTransform, Vector3 out) {
        if (steeringWheel == null) {
            return false;
        }
        tmp.set(chassisTransform).mul(steeringWheel.globalTransform);
        tmp.getTranslation(out);
        return true;
    }

    /**
     * Vueltas de volante de tope a tope que se dibujan (por defecto las del coche). Con el volante táctil
     * se pone 1: media vuelta del dedo es todo el giro, y el volante dibujado sigue al dedo.
     */
    public void setWheelTurnsShown(float turns) {
        wheelTurnsShown = turns;
    }

    /** Posición de los ojos del conductor respecto al chasis. */
    public Vector3 getEyeOffset() {
        return eyeOffset;
    }

    public boolean isPlaceholder() {
        return asset == null;
    }

    public void addTo(SceneRenderer renderer) {
        renderer.add(chassisScene);
        if (!wheelsInModel) {
            for (Scene s : wheelScenes) {
                if (s != null) {
                    renderer.add(s);
                }
            }
        }
    }

    /**
     * Habitáculo como el del Clio III de calle: salpicadero, puertas y volante en plástico antracita y
     * asientos de tela gris muy oscuro (el modelo los trae en gris claro). Sirve también para los coches
     * aparcados, que son instancias del mismo modelo. Sin esos materiales (modelo provisional) no hace nada.
     */
    public static void darkenInterior(ModelInstance instance) {
        com.badlogic.gdx.graphics.g3d.Material trim = instance.getMaterial("Interieur");
        if (trim != null) {
            trim.set(net.mgsx.gltf.scene3d.attributes.PBRColorAttribute.createBaseColorFactor(
                new com.badlogic.gdx.graphics.Color(0.032f, 0.033f, 0.035f, 1f)));
            trim.set(net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute.createRoughness(0.72f));
        }
        com.badlogic.gdx.graphics.g3d.Material seats = instance.getMaterial("Siege");
        if (seats != null) {
            seats.set(net.mgsx.gltf.scene3d.attributes.PBRColorAttribute.createBaseColorFactor(
                new com.badlogic.gdx.graphics.Color(0.022f, 0.023f, 0.026f, 1f)));
            seats.set(net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute.createRoughness(0.95f));
        }
    }

    /** Pinta la carrocería (material "Body") con ese color; ORIGINAL la deja como venga en el modelo. */
    public void setPaint(com.pelayo.autoescuela.app.CarPaint paint) {
        com.badlogic.gdx.graphics.g3d.Material body = chassis.getMaterial("Body");
        if (body == null) {
            return;
        }
        if (paint == com.pelayo.autoescuela.app.CarPaint.ORIGINAL) {
            if (originalBody != null) {
                body.clear();
                body.set(originalBody);
            }
            return;
        }
        body.set(net.mgsx.gltf.scene3d.attributes.PBRColorAttribute.createBaseColorFactor(paint.linear()));
        body.set(net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute.createMetallic(paint.finish.metallic));
        body.set(net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute.createRoughness(paint.finish.roughness));
    }

    public void removeFrom(SceneRenderer renderer) {
        renderer.remove(chassisScene);
        if (!wheelsInModel) {
            for (Scene s : wheelScenes) {
                if (s != null) {
                    renderer.remove(s);
                }
            }
        }
    }

    /** Copia el estado físico al modelo. Llamar una vez por frame tras la simulación. */
    public void update(VehicleState state, BulletVehicle body) {
        chassis.transform.set(state.transform);
        if (wheelsInModel) {
            inverseChassis.set(state.transform).inv();
            for (int i = 0; i < 4; i++) {
                body.getWheelTransform(i, tmp);
                wheelNodes[i].localTransform.set(inverseChassis).mul(tmp);
                wheelNodes[i].isAnimated = true;
            }
        } else {
            for (int i = 0; i < 4; i++) {
                body.getWheelTransform(i, wheels[i].transform);
            }
        }
        if (steeringWheel != null) {
            float turns = wheelTurnsShown > 0f ? wheelTurnsShown : spec.steering.wheelTurns;
            // Dirección a la izquierda (steerAngle > 0) = volante girado en sentido antihorario visto por el conductor
            float turnDeg = state.steerAngle * MathUtils.radiansToDegrees
                / spec.steering.maxAngleDeg * turns * 180f;
            steeringWheel.rotation.set(steeringRest).mul(tmpQ.setFromAxis(Vector3.Y, turnDeg));
        }
        if (gearLever != null) {
            // Patrón en H simplificado: inclinación lateral por columna y adelante/atrás por fila
            int g = state.gear;
            float lateral = g == 1 || g == 2 ? 14f : g == 5 || g == -1 ? -14f : 0f;
            float longitudinal = g == 1 || g == 3 || g == 5 ? -12f : g == 2 || g == 4 || g == -1 ? 12f : 0f;
            gearLever.rotation.set(leverRest).mul(tmpQ.setFromAxis(Vector3.Z, lateral))
                .mul(tmpQ.setFromAxis(Vector3.X, longitudinal));
        }
        chassis.calculateTransforms();
    }

    @Override
    public void dispose() {
        if (asset != null) {
            asset.dispose();
        }
        if (carModel != null) {
            carModel.dispose();
        }
        if (wheelModel != null) {
            wheelModel.dispose();
        }
    }
}
