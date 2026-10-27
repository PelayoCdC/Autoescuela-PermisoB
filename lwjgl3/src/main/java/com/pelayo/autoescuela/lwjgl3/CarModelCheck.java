package com.pelayo.autoescuela.lwjgl3;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprueba un car.glb sin abrir la app: lee el JSON del glTF y lista los
 * nodos que espera {@code CarView}, los materiales de espejo y la caja
 * envolvente. Uso: {@code gradlew checkCar [-Pglb=ruta]}.
 */
public final class CarModelCheck {

    private static final String[] REQUIRED = {"wheel_fl", "wheel_fr", "wheel_rl", "wheel_rr",
        "steering_wheel", "gear_lever", "driver_eye"};
    private static final String[] MIRRORS = {"mirror_center", "mirror_left", "mirror_right"};

    private CarModelCheck() {
    }

    public static void main(String[] args) throws IOException {
        File file = new File(args.length > 0 ? args[0] : "assets/models/car/car.glb");
        if (!file.exists()) {
            System.out.println("No existe " + file.getAbsolutePath() + " (la app usa el placeholder)");
            return;
        }
        JsonValue gltf = readJsonChunk(file);
        System.out.println("Fichero: " + file.getAbsolutePath() + " (" + file.length() / 1024 + " KB)");
        if (gltf.has("extensionsRequired")) {
            System.out.println("AVISO: extensiones requeridas " + gltf.get("extensionsRequired").toString()
                + " (Draco no está soportado)");
        }

        List<String> nodeNames = new ArrayList<>();
        for (JsonValue n = gltf.get("nodes") == null ? null : gltf.get("nodes").child; n != null; n = n.next) {
            nodeNames.add(n.getString("name", "(sin nombre)"));
        }
        System.out.println("Nodos (" + nodeNames.size() + "): " + nodeNames);
        for (String r : REQUIRED) {
            System.out.println(String.format("  %-16s %s", r, nodeNames.contains(r) ? "OK" : "FALTA"));
        }
        // Nodos requeridos que no están en el nivel superior
        JsonValue scenes = gltf.get("scenes");
        if (scenes != null && scenes.size > 0) {
            List<Integer> roots = new ArrayList<>();
            for (JsonValue idx = scenes.get(0).get("nodes").child; idx != null; idx = idx.next) {
                roots.add(idx.asInt());
            }
            for (String r : new String[] {"wheel_fl", "wheel_fr", "wheel_rl", "wheel_rr", "steering_wheel", "gear_lever"}) {
                int i = nodeNames.indexOf(r);
                if (i >= 0 && !roots.contains(i)) {
                    System.out.println("  AVISO: " + r + " no está en el nivel superior (tiene padre); "
                        + "sácalo con Alt+P → Clear and Keep Transformation");
                }
            }
        }

        List<String> materials = new ArrayList<>();
        for (JsonValue m = gltf.get("materials") == null ? null : gltf.get("materials").child; m != null; m = m.next) {
            materials.add(m.getString("name", "(sin nombre)"));
        }
        System.out.println("Materiales (" + materials.size() + ")");
        for (String m : MIRRORS) {
            System.out.println(String.format("  %-16s %s", m, materials.contains(m) ? "OK" : "falta (sin espejo)"));
        }

        // Caja envolvente: min/max de los accessors POSITION (sin aplicar transformaciones de nodo)
        float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        JsonValue accessors = gltf.get("accessors");
        for (JsonValue mesh = gltf.get("meshes") == null ? null : gltf.get("meshes").child; mesh != null; mesh = mesh.next) {
            for (JsonValue prim = mesh.get("primitives").child; prim != null; prim = prim.next) {
                JsonValue attrs = prim.get("attributes");
                if (attrs == null || !attrs.has("POSITION")) {
                    continue;
                }
                JsonValue acc = accessors.get(attrs.getInt("POSITION"));
                if (acc.has("min") && acc.has("max")) {
                    float[] mn = acc.get("min").asFloatArray();
                    float[] mx = acc.get("max").asFloatArray();
                    for (int i = 0; i < 3; i++) {
                        lo[i] = Math.min(lo[i], mn[i]);
                        hi[i] = Math.max(hi[i], mx[i]);
                    }
                }
            }
        }
        if (lo[0] < hi[0]) {
            System.out.println(String.format("Caja (coordenadas de malla): ancho X %.2f, alto Y %.2f, largo Z %.2f",
                hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]));
            System.out.println(String.format("  X %.2f..%.2f  Y %.2f..%.2f  Z %.2f..%.2f", lo[0], hi[0], lo[1], hi[1], lo[2], hi[2]));
            System.out.println("  Esperado para un Clio III: ancho ~1.72, alto ~1.45, largo ~3.99, Y mínimo ~-0.38 (suelo)");
            if (hi[2] - lo[2] < 2f || hi[2] - lo[2] > 8f) {
                System.out.println("  AVISO: el largo no parece de un coche en metros: revisa la escala");
            }
            if (hi[0] - lo[0] > hi[2] - lo[2]) {
                System.out.println("  AVISO: es más ancho (X) que largo (Z): el coche no mira hacia +Z; revisa la orientación");
            }
        }
    }

    /** Lee el chunk JSON de un .glb (o el fichero entero si es .gltf). */
    private static JsonValue readJsonChunk(File file) throws IOException {
        if (file.getName().toLowerCase().endsWith(".gltf")) {
            return new JsonReader().parse(new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        }
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            byte[] header = new byte[12];
            in.readFully(header);
            ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            if (h.getInt(0) != 0x46546C67) {
                throw new IOException("No es un fichero glb (cabecera incorrecta)");
            }
            byte[] chunkHeader = new byte[8];
            in.readFully(chunkHeader);
            ByteBuffer c = ByteBuffer.wrap(chunkHeader).order(ByteOrder.LITTLE_ENDIAN);
            int length = c.getInt(0);
            byte[] json = new byte[length];
            in.readFully(json);
            return new JsonReader().parse(new String(json, StandardCharsets.UTF_8));
        }
    }
}
