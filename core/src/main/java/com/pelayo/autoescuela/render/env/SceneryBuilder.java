package com.pelayo.autoescuela.render.env;

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
import com.badlogic.gdx.utils.ObjectMap;
import com.pelayo.autoescuela.data.ScenarioDef;
import com.pelayo.autoescuela.render.PlaceholderModels;
import java.util.Random;

/**
 * Decorado que rodea la zona de práctica, solo visual (sin física): calles
 * con edificios de varias plantas, árboles y farolas si el escenario es
 * urbano (tiene aceras), casas sueltas y arboledas si es rural, y colinas
 * lejanas que la niebla funde con el cielo. Se coloca fuera del rectángulo
 * que ocupan los módulos del escenario, así nunca estorba a las maniobras.
 * Todo va en un único modelo con una parte por material (pocas llamadas de
 * dibujo); la disposición es aleatoria pero fija para cada escenario.
 */
public final class SceneryBuilder {

    private static final long ATTRS = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
    /** Objetos por parte antes de abrir otra (límite de 65.536 vértices por malla). */
    private static final int MAX_PER_PART = 220;

    private final ModelBuilder mb = new ModelBuilder();
    private final Random random;
    private final ObjectMap<String, Material> materials = new ObjectMap<>();
    private final ObjectMap<String, MeshPartBuilder> parts = new ObjectMap<>();
    private final ObjectMap<String, Integer> counts = new ObjectMap<>();
    private final EnvTextures tex;
    private int partIndex;

    private float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

    /**
     * Forma de choque de un objeto del decorado, para añadirla a la física: caja (edificios) de medidas
     * completas sx, sy, sz centrada en (x, y, z) y girada yawDeg, o cilindro vertical (troncos, farolas)
     * de radio sx y alto sy.
     */
    public static final class Collider {
        public final boolean box;
        public final float x, y, z, sx, sy, sz, yawDeg;

        Collider(boolean box, float x, float y, float z, float sx, float sy, float sz, float yawDeg) {
            this.box = box;
            this.x = x;
            this.y = y;
            this.z = z;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.yawDeg = yawDeg;
        }
    }

    private final com.badlogic.gdx.utils.Array<Collider> colliders;

    private SceneryBuilder(EnvTextures tex, long seed, com.badlogic.gdx.utils.Array<Collider> colliders) {
        this.tex = tex;
        this.random = new Random(seed);
        this.colliders = colliders;
    }

    /** Construye el decorado y deja en {@code colliders} las formas de choque de edificios, árboles y farolas. */
    public static Model build(ScenarioDef def, EnvTextures tex, com.badlogic.gdx.utils.Array<Collider> colliders) {
        SceneryBuilder b = new SceneryBuilder(tex, def.id.hashCode(), colliders);
        b.mb.begin();
        b.measure(def);
        b.scenarioDef = def;
        boolean urban = false;
        for (ScenarioDef.ModuleDef m : def.modules) {
            urban |= m.type.startsWith("sidewalk");
        }
        if (urban) {
            b.paving(0f, 52f);
            b.streetLamps(def);
            b.streetFronts(def);
            b.ring(6f, 1, true);
            b.ring(30f, 2, true);
        } else {
            b.ring(10f, 1, false);
            b.ring(32f, 2, false);
        }
        b.hills();
        return b.mb.end();
    }

    // --- Rectángulo ocupado por el escenario ---------------------------------------

    private void measure(ScenarioDef def) {
        include(def.startPose.x, def.startPose.z);
        for (ScenarioDef.ModuleDef m : def.modules) {
            switch (m.type) {
                case "road":
                case "ground":
                case "sidewalk":
                case "block":
                    rect(m.x, m.z, m.width, m.length, m.yawDeg, 0f);
                    break;
                case "wall":
                    rect(m.x, m.z, 0.3f, m.length, m.yawDeg, 0f);
                    break;
                case "slope":
                    rect(m.x, m.z, m.width, m.length, m.yawDeg, m.length / 2f);
                    break;
                case "parked_car":
                    rect(m.x, m.z, 1.8f, 4.2f, m.yawDeg, 0f);
                    break;
                case "road_arc":
                case "sidewalk_arc":
                    if (m.arc != null) {
                        for (float[] p : m.arc.points()) {
                            float dx = p[0] - m.arc.cx, dz = p[1] - m.arc.cz;
                            float len = (float) Math.sqrt(dx * dx + dz * dz);
                            float k = (len + m.width / 2f) / Math.max(len, 0.001f);
                            include(m.arc.cx + dx * k, m.arc.cz + dz * k);
                        }
                    }
                    break;
                case "island":
                    rect(m.x, m.z, m.radius * 2f, m.radius * 2f, 0f, 0f);
                    break;
                default:
                    include(m.x, m.z);
                    break;
            }
        }
        for (ScenarioDef.ZoneDef z : def.zones) {
            if (z.center != null && z.size != null) {
                rect(z.center[0], z.center[1], z.size[0], z.size[1], z.yawDeg, 0f);
            }
        }
    }

    /** Rectángulo de ancho w (X local) y largo l (Z local), desplazado dz a lo largo de su eje. */
    private void rect(float x, float z, float w, float l, float yawDeg, float dz) {
        float s = MathUtils.sinDeg(yawDeg), c = MathUtils.cosDeg(yawDeg);
        float cx = x + s * dz, cz = z + c * dz;
        for (int i = 0; i < 4; i++) {
            float lx = (i % 2 == 0 ? -0.5f : 0.5f) * w;
            float lz = (i < 2 ? -0.5f : 0.5f) * l;
            include(cx + lx * c + lz * s, cz - lx * s + lz * c);
        }
    }

    private void include(float x, float z) {
        minX = Math.min(minX, x);
        maxX = Math.max(maxX, x);
        minZ = Math.min(minZ, z);
        maxZ = Math.max(maxZ, z);
    }

    // --- Anillos de parcelas ---------------------------------------------------------

    /**
     * Recorre los cuatro lados del rectángulo ampliado {@code offset} metros y
     * llena parcelas con edificios o árboles mirando hacia dentro.
     */
    private void ring(float offset, int row, boolean urban) {
        float x0 = minX - offset, x1 = maxX + offset, z0 = minZ - offset, z1 = maxZ + offset;
        // Lado, origen, dirección a lo largo, normal hacia fuera
        side(x0, z0, 0f, 1f, z1 - z0, -1f, 0f, row, urban);   // lado -X
        side(x1, z1, 0f, -1f, z1 - z0, 1f, 0f, row, urban);   // lado +X
        side(x1, z0, -1f, 0f, x1 - x0, 0f, -1f, row, urban);  // lado -Z
        side(x0, z1, 1f, 0f, x1 - x0, 0f, 1f, row, urban);    // lado +Z
    }

    private void side(float ox, float oz, float ax, float az, float length, float nx, float nz, int row, boolean urban) {
        float t = row == 1 ? 0f : rnd(0f, 8f);
        while (t < length) {
            float lot = rnd(9f, 16f);
            float mid = t + lot / 2f;
            float px = ox + ax * mid, pz = oz + az * mid;
            float yaw = MathUtils.atan2(-nx, -nz) * MathUtils.radiansToDegrees; // fachada hacia dentro
            float buildingChance = urban ? (row == 1 ? 0.78f : 0.65f) : (row == 1 ? 0.18f : 0.08f);
            if (random.nextFloat() < buildingChance) {
                float depth = rnd(9f, 15f);
                float set = rnd(0f, urban ? 2f : 8f);
                int floors = urban ? (row == 1 ? 2 + random.nextInt(4) : 4 + random.nextInt(6)) : 1 + random.nextInt(2);
                float bx = px + nx * (set + depth / 2f), bz = pz + nz * (set + depth / 2f);
                float bw = lot - rnd(0.5f, 2f);
                if (fits(bx, bz, bw, depth, yaw)) {
                    building(bx, bz, bw, depth, floors * 3f + (urban ? 0.5f : 0f), yaw);
                }
            } else {
                int trees = urban ? 1 + random.nextInt(2) : 2 + random.nextInt(4);
                for (int i = 0; i < trees; i++) {
                    float along = rnd(-lot / 2f, lot / 2f);
                    float out = rnd(1f, urban ? 6f : 18f);
                    tree(px + ax * along + nx * out, pz + az * along + nz * out);
                }
            }
            t += lot + rnd(0f, urban ? 1.5f : 6f);
        }
    }

    // --- Objetos ------------------------------------------------------------------------

    // --- Fachadas a lo largo de las aceras ------------------------------------------------

    /** Huellas de los edificios ya puestos (centro y radio), para no solaparlos. */
    private final com.badlogic.gdx.utils.FloatArray footprints = new com.badlogic.gdx.utils.FloatArray();
    private ScenarioDef scenarioDef;

    /**
     * Calles con edificios: detrás de cada acera recta (por el lado contrario a la calzada) una fila de
     * fachadas, sin pisar calzadas, aceras, zonas ni otros edificios. Así las manzanas entre calles no
     * quedan vacías.
     */
    private void streetFronts(ScenarioDef def) {
        scenarioDef = def;
        for (ScenarioDef.ModuleDef m : def.modules) {
            if (!"sidewalk".equals(m.type) || m.length < 12f) {
                continue;
            }
            float s = MathUtils.sinDeg(m.yawDeg), c = MathUtils.cosDeg(m.yawDeg);
            // Eje lateral de la acera (X local) y el lado donde no hay calzada
            float probe = m.width / 2f + 1.5f;
            boolean roadOnPlus = occupied(m.x + c * probe, m.z - s * probe, 0f);
            boolean roadOnMinus = occupied(m.x - c * probe, m.z + s * probe, 0f);
            if (roadOnPlus == roadOnMinus) {
                continue; // acera entre dos calzadas o suelta: sin fachada
            }
            float side = roadOnPlus ? -1f : 1f;
            float nx = c * side, nz = -s * side; // hacia fuera de la calle
            float yaw = MathUtils.atan2(-nx, -nz) * MathUtils.radiansToDegrees; // fachada mirando a la calle
            float t = -m.length / 2f + rnd(0f, 2f);
            while (t < m.length / 2f - 4f) {
                float lot = rnd(9f, 15f);
                float mid = t + lot / 2f;
                float depth = rnd(9f, 13f);
                float set = m.width / 2f + rnd(0.3f, 1.2f);
                float bx = m.x + s * mid + nx * (set + depth / 2f);
                float bz = m.z + c * mid + nz * (set + depth / 2f);
                float w = lot - rnd(0.4f, 1.5f);
                if (fits(bx, bz, w, depth, yaw)) {
                    int floors = 2 + random.nextInt(5);
                    building(bx, bz, w, depth, floors * 3f + 0.5f, yaw);
                }
                t += lot + rnd(0f, 1.2f);
            }
        }
    }

    /** ¿Cabe un edificio ahí sin pisar la calle ni otro edificio? */
    private boolean fits(float x, float z, float w, float d, float yawDeg) {
        float s = MathUtils.sinDeg(yawDeg), c = MathUtils.cosDeg(yawDeg);
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                float lx = i * w / 2f, lz = j * d / 2f;
                if (occupied(x + lx * c + lz * s, z - lx * s + lz * c, 0.25f)) {
                    return false;
                }
            }
        }
        float r = 0.5f * (float) Math.sqrt(w * w + d * d) * 0.8f;
        for (int i = 0; i < footprints.size; i += 3) {
            if (dist2(x, z, footprints.get(i), footprints.get(i + 1)) < (r + footprints.get(i + 2)) * (r + footprints.get(i + 2))) {
                return false;
            }
        }
        return true;
    }

    /** El punto cae sobre una calzada, acera, glorieta o zona del escenario (con ese margen en metros). */
    private boolean occupied(float x, float z, float margin) {
        if (scenarioDef == null) {
            return false;
        }
        // Por índice: se llama desde dentro de otros recorridos de los módulos (iteradores de Array no anidables)
        for (int mi = 0; mi < scenarioDef.modules.size; mi++) {
            ScenarioDef.ModuleDef m = scenarioDef.modules.get(mi);
            switch (m.type) {
                case "road":
                case "ground":
                case "sidewalk":
                case "slope": {
                    float s = MathUtils.sinDeg(m.yawDeg), c = MathUtils.cosDeg(m.yawDeg);
                    float cz0 = "slope".equals(m.type) ? m.length / 2f : 0f;
                    float dx = x - (m.x + s * cz0), dz = z - (m.z + c * cz0);
                    float lx = dx * c - dz * s, lz = dx * s + dz * c;
                    if (Math.abs(lx) <= m.width / 2f + margin && Math.abs(lz) <= m.length / 2f + margin) {
                        return true;
                    }
                    break;
                }
                case "road_arc":
                case "sidewalk_arc":
                    if (m.arc != null) {
                        float r = (float) Math.sqrt(dist2(x, z, m.arc.cx, m.arc.cz));
                        if (Math.abs(r - m.arc.radius) <= m.width / 2f + margin) {
                            return true;
                        }
                    }
                    break;
                case "island":
                    if (dist2(x, z, m.x, m.z) <= (m.radius + margin) * (m.radius + margin)) {
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        for (int zi = 0; zi < scenarioDef.zones.size; zi++) {
            ScenarioDef.ZoneDef zd = scenarioDef.zones.get(zi);
            if (zd.center == null || zd.size == null) {
                continue;
            }
            float s = MathUtils.sinDeg(zd.yawDeg), c = MathUtils.cosDeg(zd.yawDeg);
            float dx = x - zd.center[0], dz = z - zd.center[1];
            float lx = dx * c - dz * s, lz = dx * s + dz * c;
            if (Math.abs(lx) <= zd.size[0] / 2f + margin && Math.abs(lz) <= zd.size[1] / 2f + margin) {
                return true;
            }
        }
        return false;
    }

    private void building(float x, float z, float w, float d, float h, float yawDeg) {
        footprints.addAll(x, z, 0.5f * (float) Math.sqrt(w * w + d * d) * 0.8f);
        boolean brick = random.nextFloat() < 0.3f;
        MeshPartBuilder walls = part(brick ? "brick" : "facade" + random.nextInt(4), 1);
        Matrix4 t = new Matrix4().setToTranslation(x, 0f, z).rotate(Vector3.Y, yawDeg);
        walls.setVertexTransform(t);
        float hw = w / 2f, hd = d / 2f;
        // Cuatro fachadas como rectángulos, con las ventanas a escala (7 m x 6 m por repetición)
        wall(walls, -hw, hd, hw, hd, h, 0f, 0f, 1f, w);      // delantera (+Z local)
        wall(walls, hw, -hd, -hw, -hd, h, 0f, 0f, -1f, w);   // trasera
        wall(walls, hw, hd, hw, -hd, h, 1f, 0f, 0f, d);      // lateral +X
        wall(walls, -hw, -hd, -hw, hd, h, -1f, 0f, 0f, d);   // lateral -X
        walls.setVertexTransform(new Matrix4());
        // Cubierta plana con un pequeño peto
        MeshPartBuilder roof = part("roof", 1);
        roof.setVertexTransform(new Matrix4(t).translate(0f, h + 0.25f, 0f));
        roof.box(w + 0.3f, 0.5f, d + 0.3f);
        roof.setVertexTransform(new Matrix4());
        colliders.add(new Collider(true, x, h / 2f, z, w, h, d, yawDeg));
    }

    /** Rectángulo vertical de (ax, az) a (bx, bz), alto h, con normal (nx, 0, nz) y UV a escala. */
    private void wall(MeshPartBuilder part, float ax, float az, float bx, float bz, float h, float nx, float ny,
                      float nz, float len) {
        part.setUVRange(0f, 0f, len / EnvTextures.FACADE_TILE_W, h / EnvTextures.FACADE_TILE_H);
        part.rect(ax, 0f, az, bx, 0f, bz, bx, h, bz, ax, h, az, nx, ny, nz);
    }

    private void tree(float x, float z) {
        float h = rnd(2.2f, 3.4f);
        MeshPartBuilder trunk = part("trunk", 1);
        trunk.setVertexTransform(new Matrix4().setToTranslation(x, h / 2f, z));
        trunk.cylinder(0.32f, h, 0.32f, 6);
        colliders.add(new Collider(false, x, h / 2f, z, 0.17f, h, 0f, 0f));
        boolean conifer = random.nextFloat() < 0.3f;
        MeshPartBuilder leaves = part(random.nextBoolean() ? "leaves" : "leaves2", 1);
        if (conifer) {
            float ch = rnd(5f, 8f);
            leaves.setVertexTransform(new Matrix4().setToTranslation(x, h * 0.6f + ch / 2f, z));
            leaves.cone(rnd(2.6f, 3.4f), ch, rnd(2.6f, 3.4f), 8);
        } else {
            float r = rnd(2.4f, 3.6f);
            leaves.setVertexTransform(new Matrix4().setToTranslation(x, h + r * 0.7f, z)
                .scale(1f, rnd(0.85f, 1.15f), 1f));
            leaves.sphere(r * 2f, r * 1.8f, r * 2f, 8, 6);
            // Segunda masa de hojas para romper la esfera
            leaves.setVertexTransform(new Matrix4().setToTranslation(x + rnd(-0.8f, 0.8f), h + r * 1.2f,
                z + rnd(-0.8f, 0.8f)));
            leaves.sphere(r * 1.3f, r * 1.2f, r * 1.3f, 7, 5);
        }
        trunk.setVertexTransform(new Matrix4());
        leaves.setVertexTransform(new Matrix4());
    }

    /**
    * Suelo de baldosa alrededor del escenario urbano (marco entre {@code inner} y
    * {@code outer} metros del rectángulo ocupado), para que los edificios no
    * queden sobre césped. Solo visual, un pelo por encima de la hierba.
    */
    private void paving(float inner, float outer) {
        MeshPartBuilder p = part("paving", 1);
        float y = -0.004f;
        float ax0 = minX - outer, ax1 = maxX + outer, az0 = minZ - outer, az1 = maxZ + outer;
        float bx0 = minX - inner, bx1 = maxX + inner, bz0 = minZ - inner, bz1 = maxZ + inner;
        quad(p, ax0, az0, ax1, bz0, y);   // franja -Z
        quad(p, ax0, bz1, ax1, az1, y);   // franja +Z
        quad(p, ax0, bz0, bx0, bz1, y);   // franja -X
        quad(p, bx1, bz0, ax1, bz1, y);   // franja +X
    }

    /** Rectángulo horizontal mirando arriba, con la baldosa a escala real. */
    private void quad(MeshPartBuilder p, float x0, float z0, float x1, float z1, float y) {
        p.setUVRange(x0 / EnvTextures.SIDEWALK_TILE, z0 / EnvTextures.SIDEWALK_TILE,
            x1 / EnvTextures.SIDEWALK_TILE, z1 / EnvTextures.SIDEWALK_TILE);
        p.rect(x0, y, z1, x1, y, z1, x1, y, z0, x0, y, z0, 0f, 1f, 0f);
    }

    /** Farolas en el borde exterior de las aceras rectas, cada 25 m. */
    private void streetLamps(ScenarioDef def) {
        float cx = (minX + maxX) / 2f, cz = (minZ + maxZ) / 2f;
        for (ScenarioDef.ModuleDef m : def.modules) {
            if (!"sidewalk".equals(m.type) || m.length < 15f) {
                continue;
            }
            float s = MathUtils.sinDeg(m.yawDeg), c = MathUtils.cosDeg(m.yawDeg);
            // Borde exterior: el más alejado del centro del escenario
            float ex = m.x + c * (m.width / 2f), ez = m.z - s * (m.width / 2f);
            float side = dist2(ex, ez, cx, cz) > dist2(m.x - c * (m.width / 2f), m.z + s * (m.width / 2f), cx, cz)
                ? 1f : -1f;
            float lateral = side * (m.width / 2f - 0.35f);
            for (float along = -m.length / 2f + 8f; along < m.length / 2f - 4f; along += 25f) {
                float x = m.x + c * lateral + s * along;
                float z = m.z - s * lateral + c * along;
                // Brazo hacia la calzada (hacia dentro)
                float ix = -c * side, iz = s * side;
                lamp(x, m.curbHeight, z, ix, iz);
            }
        }
    }

    private void lamp(float x, float y, float z, float ix, float iz) {
        MeshPartBuilder pole = part("metal", 1);
        pole.setVertexTransform(new Matrix4().setToTranslation(x, y + 3.2f, z));
        pole.cylinder(0.12f, 6.4f, 0.12f, 6);
        colliders.add(new Collider(false, x, y + 3.2f, z, 0.07f, 6.4f, 0f, 0f));
        float yaw = MathUtils.atan2(ix, iz) * MathUtils.radiansToDegrees;
        pole.setVertexTransform(new Matrix4().setToTranslation(x + ix * 0.6f, y + 6.3f, z + iz * 0.6f)
            .rotate(Vector3.Y, yaw));
        pole.box(0.08f, 0.08f, 1.3f);
        pole.setVertexTransform(new Matrix4());
        MeshPartBuilder head = part("lamp", 1);
        head.setVertexTransform(new Matrix4().setToTranslation(x + ix * 1.25f, y + 6.2f, z + iz * 1.25f)
            .rotate(Vector3.Y, yaw));
        head.box(0.35f, 0.14f, 0.6f);
        head.setVertexTransform(new Matrix4());
    }

    /** Colinas lejanas en anillo; la niebla las aclara hacia el color del horizonte. */
    private void hills() {
        float cx = (minX + maxX) / 2f, cz = (minZ + maxZ) / 2f;
        int n = 22;
        for (int i = 0; i < n; i++) {
            float a = 360f * i / n + rnd(-6f, 6f);
            float r = rnd(650f, 950f);
            float w = rnd(260f, 420f);
            float h = rnd(35f, 110f);
            MeshPartBuilder hill = part(random.nextBoolean() ? "hill" : "hill2", 1);
            hill.setVertexTransform(new Matrix4().setToTranslation(cx + MathUtils.sinDeg(a) * r, h * 0.42f - 4f,
                cz + MathUtils.cosDeg(a) * r).scale(1f, h / w, 1f));
            hill.sphere(w, w, w * rnd(0.6f, 1f), 14, 8);
            hill.setVertexTransform(new Matrix4());
        }
    }

    // --- Partes y materiales --------------------------------------------------------------

    /** Parte del material, abriendo una nueva cada MAX_PER_PART objetos. */
    private MeshPartBuilder part(String material, int objects) {
        int count = counts.get(material, 0);
        MeshPartBuilder part = parts.get(material);
        if (part == null || count >= MAX_PER_PART) {
            Node node = mb.node();
            node.id = "scenery_" + material + "_" + (partIndex++);
            boolean textured = material.startsWith("facade") || material.equals("brick") || material.equals("roof")
                || material.equals("paving");
            part = mb.part(node.id, GL20.GL_TRIANGLES, textured ? EnvModels.ATTRS_UV : ATTRS, material(material));
            parts.put(material, part);
            count = 0;
        }
        counts.put(material, count + objects);
        return part;
    }

    private Material material(String name) {
        Material m = materials.get(name);
        if (m != null) {
            return m;
        }
        switch (name) {
            case "facade0": m = EnvModels.textured(tex.facade, Color.WHITE, 0.85f); break;
            case "facade1": m = EnvModels.textured(tex.facade, new Color(1f, 0.93f, 0.80f, 1f), 0.85f); break;
            case "facade2": m = EnvModels.textured(tex.facade, new Color(1f, 0.86f, 0.82f, 1f), 0.85f); break;
            case "facade3": m = EnvModels.textured(tex.facade, new Color(0.88f, 0.92f, 1f, 1f), 0.85f); break;
            case "paving": m = EnvModels.textured(tex.sidewalk, new Color(0.95f, 0.95f, 0.95f, 1f), 0.9f); break;
            case "brick": m = EnvModels.textured(tex.facadeBrick, Color.WHITE, 0.9f); break;
            case "roof": m = EnvModels.textured(tex.roof, Color.WHITE, 0.95f); break;
            case "trunk": m = PlaceholderModels.material(new Color(0.30f, 0.22f, 0.15f, 1f), 0f, 0.95f); break;
            case "leaves": m = PlaceholderModels.material(new Color(0.20f, 0.36f, 0.15f, 1f), 0f, 0.9f); break;
            case "leaves2": m = PlaceholderModels.material(new Color(0.27f, 0.42f, 0.17f, 1f), 0f, 0.9f); break;
            case "metal": m = PlaceholderModels.material(new Color(0.32f, 0.34f, 0.36f, 1f), 0.7f, 0.45f); break;
            case "lamp": m = PlaceholderModels.material(new Color(0.85f, 0.85f, 0.80f, 1f), 0.2f, 0.4f); break;
            case "hill": m = PlaceholderModels.material(new Color(0.36f, 0.46f, 0.30f, 1f), 0f, 1f); break;
            case "hill2": m = PlaceholderModels.material(new Color(0.42f, 0.48f, 0.34f, 1f), 0f, 1f); break;
            default: throw new IllegalArgumentException(name);
        }
        materials.put(name, m);
        return m;
    }

    private float rnd(float a, float b) {
        return a + random.nextFloat() * (b - a);
    }

    private static float dist2(float ax, float az, float bx, float bz) {
        return (ax - bx) * (ax - bx) + (az - bz) * (az - bz);
    }
}
