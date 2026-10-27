package com.pelayo.autoescuela.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.pelayo.autoescuela.data.VehicleSpec;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;

/**
 * Modelos construidos en código mientras no hay assets glTF. Usan los mismos
 * nombres de nodo que se exigirán al modelo real ({@code docs/ASSETS.md}),
 * así {@link CarView} funciona igual con unos u otros.
 */
public final class PlaceholderModels {

    private static final long ATTRS = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
    /** Con coordenadas de textura (espejos). */
    private static final long ATTRS_UV = ATTRS | VertexAttributes.Usage.TextureCoordinates;

    private PlaceholderModels() {
    }

    /**
     * Peatón sencillo de pie, con el origen entre los pies y mirando a +Z: piernas, tronco, brazos y cabeza,
     * con la camiseta y el pantalón del color dado.
     */
    public static Model pedestrian(Color shirt, Color trousers) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        Material skin = material(new Color(0.78f, 0.6f, 0.47f, 1f), 0f, 0.8f);
        Material top = material(shirt, 0f, 0.85f);
        Material legs = material(trousers, 0f, 0.85f);
        Material shoes = material(new Color(0.08f, 0.08f, 0.09f, 1f), 0f, 0.7f);
        com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder p;
        p = mb.part("legs", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ATTRS, legs);
        p.setVertexTransform(new Matrix4().setToTranslation(-0.1f, 0.45f, 0f));
        p.box(0.16f, 0.8f, 0.2f);
        p.setVertexTransform(new Matrix4().setToTranslation(0.1f, 0.45f, 0f));
        p.box(0.16f, 0.8f, 0.2f);
        p = mb.part("shoes", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ATTRS, shoes);
        p.setVertexTransform(new Matrix4().setToTranslation(-0.1f, 0.04f, 0.04f));
        p.box(0.16f, 0.08f, 0.28f);
        p.setVertexTransform(new Matrix4().setToTranslation(0.1f, 0.04f, 0.04f));
        p.box(0.16f, 0.08f, 0.28f);
        p = mb.part("torso", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ATTRS, top);
        p.setVertexTransform(new Matrix4().setToTranslation(0f, 1.15f, 0f));
        p.box(0.44f, 0.62f, 0.24f);
        p.setVertexTransform(new Matrix4().setToTranslation(-0.29f, 1.12f, 0f));
        p.box(0.12f, 0.58f, 0.14f);
        p.setVertexTransform(new Matrix4().setToTranslation(0.29f, 1.12f, 0f));
        p.box(0.12f, 0.58f, 0.14f);
        p = mb.part("head", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ATTRS, skin);
        p.setVertexTransform(new Matrix4().setToTranslation(0f, 1.6f, 0f));
        p.sphere(0.24f, 0.28f, 0.24f, 10, 8);
        return mb.end();
    }

    /** Material PBR liso. */
    public static Material material(Color color, float metallic, float roughness) {
        return new Material(
            PBRColorAttribute.createBaseColorFactor(color),
            PBRFloatAttribute.createMetallic(metallic),
            PBRFloatAttribute.createRoughness(roughness));
    }

    /**
     * Coche con carrocería, cabina, salpicadero, volante y asientos. El origen
     * del modelo es el centro de masas (como el cuerpo de bullet). Las ruedas
     * van en {@link #wheel(VehicleSpec)}, aparte, porque las mueve bullet.
     */
    public static Model car(VehicleSpec spec) {
        ModelBuilder mb = new ModelBuilder();
        Material paint = material(new Color(0.75f, 0.12f, 0.10f, 1f), 0.6f, 0.35f);
        paint.id = "Body"; // mismo nombre que la pintura del glTF, para poder cambiarle el color
        Material dark = material(new Color(0.08f, 0.08f, 0.09f, 1f), 0.1f, 0.7f);
        Material glass = material(new Color(0.55f, 0.70f, 0.80f, 1f), 0.2f, 0.1f);
        Material seat = material(new Color(0.20f, 0.20f, 0.24f, 1f), 0f, 0.9f);

        float w = spec.width;
        float l = spec.length;
        float groundY = -spec.comHeight; // suelo en coordenadas del chasis
        float floorY = groundY + 0.25f;  // bajos
        float sillY = groundY + 0.72f;   // cintura de la carrocería (base de las ventanillas)
        float roofY = groundY + spec.height;

        mb.begin();
        // Carrocería baja (del suelo a la cintura), sin los pasos de rueda
        box(mb, "body_lower", paint, 0f, (floorY + sillY) / 2f, 0f, w, sillY - floorY, l);
        // Habitáculo: de detrás del asiento trasero hasta la base del parabrisas
        float cabinLen = 2.0f;
        float cabinZ = 0.2f;
        // Cabina: cristales (un poco más estrecha)
        box(mb, "cabin", glass, 0f, (sillY + roofY) / 2f, cabinZ, w * 0.92f, roofY - sillY - 0.06f, cabinLen);
        // Techo
        box(mb, "roof", paint, 0f, roofY - 0.03f, cabinZ, w * 0.9f, 0.06f, cabinLen * 0.85f);
        // Pilares A (dos barras finas en las esquinas delanteras de la cabina)
        float pillarZ = cabinZ + cabinLen / 2f - 0.05f;
        box(mb, "pillar_l", paint, w * 0.44f, (sillY + roofY) / 2f, pillarZ, 0.07f, roofY - sillY, 0.08f);
        box(mb, "pillar_r", paint, -w * 0.44f, (sillY + roofY) / 2f, pillarZ, 0.07f, roofY - sillY, 0.08f);
        // Interior: salpicadero, suelo, asientos
        float dashZ = cabinZ + cabinLen / 2f - 0.2f;
        // Salpicadero: de la cintura hasta 0,95 m del suelo, por debajo de la línea de ojos
        box(mb, "dashboard", dark, 0f, sillY + 0.11f, dashZ, w * 0.88f, 0.22f, 0.4f);
        box(mb, "floor", seat, 0f, floorY + 0.02f, cabinZ, w * 0.88f, 0.04f, cabinLen);
        box(mb, "seat_driver", seat, spec.eyeX, floorY + 0.25f, spec.eyeZ - 0.45f, 0.5f, 0.45f, 0.5f);
        box(mb, "seat_passenger", seat, -spec.eyeX, floorY + 0.25f, spec.eyeZ - 0.45f, 0.5f, 0.45f, 0.5f);
        box(mb, "seat_back_driver", seat, spec.eyeX, floorY + 0.75f, spec.eyeZ - 0.68f, 0.5f, 0.6f, 0.08f);
        // Volante: aro inclinado delante del conductor; el nodo gira sobre su eje Y local
        Node steering = mb.node();
        steering.id = "steering_wheel";
        steering.translation.set(spec.eyeX, sillY + 0.20f, spec.eyeZ + 0.55f);
        steering.rotation.setFromAxis(Vector3.X, -65f); // aro casi vertical, inclinado hacia el conductor
        MeshPartBuilder part = mb.part("steering_wheel", GL20.GL_TRIANGLES, ATTRS, dark);
        // Aro: 24 tramos de caja alrededor del eje Y local (el eje de giro del volante)
        float rimRadius = 0.185f;
        for (int i = 0; i < 24; i++) {
            float a = i * 15f;
            part.setVertexTransform(new Matrix4().rotate(Vector3.Y, a)
                .translate(rimRadius, 0f, 0f));
            part.box(0.03f, 0.03f, rimRadius * 2f * MathUtils.sinDeg(7.5f) + 0.012f);
        }
        part.setVertexTransform(new Matrix4());
        part.cylinder(0.09f, 0.04f, 0.09f, 12);
        // Tres radios
        for (int i = 0; i < 3; i++) {
            part.setVertexTransform(new Matrix4().rotate(Vector3.Y, 90f + i * 120f)
                .translate(rimRadius / 2f, 0f, 0f));
            part.box(rimRadius, 0.02f, 0.03f);
        }
        // Palanca de cambios en la consola central
        Node lever = mb.node();
        lever.id = "gear_lever";
        lever.translation.set(0f, sillY - 0.08f, spec.eyeZ + 0.45f);
        part = mb.part("gear_lever", GL20.GL_TRIANGLES, ATTRS, dark);
        part.setVertexTransform(new Matrix4().translate(0f, 0.1f, 0f));
        part.cylinder(0.02f, 0.2f, 0.02f, 8);
        part.setVertexTransform(new Matrix4().translate(0f, 0.21f, 0f));
        part.sphere(0.06f, 0.06f, 0.06f, 10, 8);
        // Espejos (planos finos con UV) con materiales por nombre para MirrorRenderer
        Material mirror = material(new Color(0.6f, 0.65f, 0.7f, 1f), 1f, 0.05f);
        mirror.id = "mirror_center";
        boxUv(mb, "mirror_center", mirror, 0f, roofY - 0.16f, spec.eyeZ + 0.6f, 0.28f, 0.09f, 0.01f);
        Material mirrorL = material(new Color(0.6f, 0.65f, 0.7f, 1f), 1f, 0.05f);
        mirrorL.id = "mirror_left";
        boxUv(mb, "mirror_left", mirrorL, w / 2f + 0.12f, sillY + 0.12f, spec.eyeZ + 0.75f, 0.18f, 0.11f, 0.02f);
        Material mirrorR = material(new Color(0.6f, 0.65f, 0.7f, 1f), 1f, 0.05f);
        mirrorR.id = "mirror_right";
        boxUv(mb, "mirror_right", mirrorR, -w / 2f - 0.12f, sillY + 0.12f, spec.eyeZ + 0.75f, 0.18f, 0.11f, 0.02f);
        // Carcasas de los espejos laterales
        box(mb, "mirror_left_housing", dark, w / 2f + 0.12f, sillY + 0.12f, spec.eyeZ + 0.77f, 0.2f, 0.13f, 0.03f);
        box(mb, "mirror_right_housing", dark, -w / 2f - 0.12f, sillY + 0.12f, spec.eyeZ + 0.77f, 0.2f, 0.13f, 0.03f);
        // Ojo del conductor: nodo vacío de referencia
        Node eye = mb.node();
        eye.id = "driver_eye";
        eye.translation.set(spec.eyeX, spec.eyeY, spec.eyeZ);
        return mb.end();
    }

    /** Una rueda: cilindro con el eje a lo largo de X. */
    public static Model wheel(VehicleSpec spec) {
        ModelBuilder mb = new ModelBuilder();
        Material rubber = material(new Color(0.06f, 0.06f, 0.06f, 1f), 0f, 0.9f);
        Material rim = material(new Color(0.7f, 0.7f, 0.72f, 1f), 0.9f, 0.3f);
        mb.begin();
        mb.node().id = "wheel";
        MeshPartBuilder part = mb.part("tyre", GL20.GL_TRIANGLES, ATTRS, rubber);
        part.setVertexTransform(new Matrix4().rotate(Vector3.Z, 90f));
        part.cylinder(spec.wheelRadius * 2f, spec.wheelWidth, spec.wheelRadius * 2f, 20);
        part = mb.part("rim", GL20.GL_TRIANGLES, ATTRS, rim);
        part.setVertexTransform(new Matrix4().rotate(Vector3.Z, 90f));
        part.cylinder(spec.wheelRadius * 1.2f, spec.wheelWidth + 0.01f, spec.wheelRadius * 1.2f, 12);
        return mb.end();
    }

    /** Suelo de asfalto: caja plana centrada en el origen con la cara superior en y = 0. */
    public static Model ground(float sizeX, float sizeZ) {
        ModelBuilder mb = new ModelBuilder();
        Material asphalt = material(new Color(0.22f, 0.22f, 0.23f, 1f), 0f, 0.95f);
        mb.begin();
        box(mb, "ground", asphalt, 0f, -0.1f, 0f, sizeX, 0.2f, sizeZ);
        return mb.end();
    }

    /** Caja genérica con material (centrada en el origen del nodo). */
    public static Model box(float w, float h, float d, Color color) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        box(mb, "box", material(color, 0f, 0.8f), 0f, 0f, 0f, w, h, d);
        return mb.end();
    }

    /** Cilindro vertical (isleta de rotonda), centrado en su eje Y. */
    public static Model cylinder(float radius, float height, Color color) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        Node node = mb.node();
        node.id = "cylinder";
        MeshPartBuilder part = mb.part("cylinder", GL20.GL_TRIANGLES, ATTRS, material(color, 0f, 0.9f));
        part.cylinder(radius * 2f, height, radius * 2f, 32);
        return mb.end();
    }

    /** Señal vertical: poste y placa. El origen está en el suelo. */
    public static Model sign(String kind) {
        ModelBuilder mb = new ModelBuilder();
        Material pole = material(new Color(0.5f, 0.5f, 0.52f, 1f), 0.8f, 0.4f);
        Color plateColor = "stop".equals(kind) ? new Color(0.8f, 0.1f, 0.1f, 1f)
            : "yield".equals(kind) ? new Color(0.95f, 0.95f, 0.95f, 1f)
            : new Color(0.1f, 0.3f, 0.8f, 1f);
        Material plate = material(plateColor, 0.1f, 0.5f);
        Material border = material(new Color(0.8f, 0.1f, 0.1f, 1f), 0.1f, 0.5f);
        mb.begin();
        Node node = mb.node();
        node.id = "pole";
        node.translation.set(0f, 1.1f, 0f);
        MeshPartBuilder part = mb.part("pole", GL20.GL_TRIANGLES, ATTRS, pole);
        part.cylinder(0.06f, 2.2f, 0.06f, 8);
        box(mb, "plate", plate, 0f, 2.1f, 0f, 0.7f, 0.7f, 0.03f);
        if ("yield".equals(kind)) {
            // Borde rojo del triángulo (aproximado con un marco)
            box(mb, "border_top", border, 0f, 2.42f, -0.005f, 0.76f, 0.06f, 0.035f);
            box(mb, "border_left", border, 0.34f, 2.1f, -0.005f, 0.06f, 0.7f, 0.035f);
            box(mb, "border_right", border, -0.34f, 2.1f, -0.005f, 0.06f, 0.7f, 0.035f);
        } else if ("stop".equals(kind)) {
            box(mb, "band", material(Color.WHITE, 0.1f, 0.5f), 0f, 2.1f, -0.01f, 0.55f, 0.14f, 0.02f);
        }
        return mb.end();
    }

    /** Cono de señalización (centrado como la btConeShape, eje Y). */
    public static Model cone(float radius, float height, Color color) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        Node node = mb.node();
        node.id = "cone";
        MeshPartBuilder part = mb.part("cone", GL20.GL_TRIANGLES, ATTRS, material(color, 0f, 0.7f));
        part.cone(radius * 2f, height, radius * 2f, 16);
        part = mb.part("base", GL20.GL_TRIANGLES, ATTRS, material(new Color(0.1f, 0.1f, 0.1f, 1f), 0f, 0.9f));
        part.setVertexTransform(new Matrix4().translate(0f, -height / 2f + 0.015f, 0f));
        part.box(radius * 2.4f, 0.03f, radius * 2.4f);
        return mb.end();
    }

    /** Coche aparcado: carrocería baja y cabina. El origen está en el suelo, bajo el centro. */
    public static Model parkedCar(float w, float h, float l, Color color) {
        ModelBuilder mb = new ModelBuilder();
        Material paint = material(color, 0.5f, 0.4f);
        Material glass = material(new Color(0.3f, 0.38f, 0.45f, 1f), 0.3f, 0.2f);
        Material rubber = material(new Color(0.06f, 0.06f, 0.06f, 1f), 0f, 0.9f);
        mb.begin();
        box(mb, "body", paint, 0f, 0.25f + 0.25f, 0f, w, 0.5f, l);
        box(mb, "cabin", glass, 0f, 0.75f + (h - 0.75f) / 2f - 0.03f, -0.1f, w * 0.9f, h - 0.75f, l * 0.5f);
        box(mb, "roof", paint, 0f, h - 0.03f, -0.1f, w * 0.88f, 0.06f, l * 0.45f);
        float r = 0.3f;
        for (int i = 0; i < 4; i++) {
            float x = (i % 2 == 0 ? 1f : -1f) * (w / 2f - 0.1f);
            float z = (i < 2 ? 1f : -1f) * (l / 2f - 0.7f);
            Node node = mb.node();
            node.id = "wheel" + i;
            node.translation.set(x, r, z);
            MeshPartBuilder part = mb.part(node.id, GL20.GL_TRIANGLES, ATTRS, rubber);
            part.setVertexTransform(new Matrix4().rotate(Vector3.Z, 90f));
            part.cylinder(r * 2f, 0.19f, r * 2f, 14);
        }
        return mb.end();
    }

    private static void box(ModelBuilder mb, String id, Material material,
                            float x, float y, float z, float w, float h, float d) {
        Node node = mb.node();
        node.id = id;
        node.translation.set(x, y, z);
        MeshPartBuilder part = mb.part(id, GL20.GL_TRIANGLES, ATTRS, material);
        part.box(w, h, d);
    }

    private static void boxUv(ModelBuilder mb, String id, Material material,
                              float x, float y, float z, float w, float h, float d) {
        Node node = mb.node();
        node.id = id;
        node.translation.set(x, y, z);
        MeshPartBuilder part = mb.part(id, GL20.GL_TRIANGLES, ATTRS_UV, material);
        part.box(w, h, d);
    }
}
