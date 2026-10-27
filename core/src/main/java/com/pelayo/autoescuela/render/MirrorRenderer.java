package com.pelayo.autoescuela.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.pelayo.autoescuela.app.Settings;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;

/**
 * Espejos retrovisores: por cada material {@code mirror_center/left/right}
 * del coche se renderiza la escena desde la posiciÃ³n del espejo mirando hacia
 * atrÃ¡s en un framebuffer, y esa textura se pinta como emisiva en el espejo.
 * La imagen se invierte horizontalmente con la proyecciÃ³n para que sea un
 * reflejo y no una cÃ¡mara trasera.
 */
public class MirrorRenderer implements Disposable {

    private static class Mirror {
        final String id;
        final Node node;
        /** Centro del espejo en coordenadas del nodo. */
        final Vector3 localCenter = new Vector3();
        final Material material;
        final FrameBuffer fbo;
        final PerspectiveCamera camera;
        final float yawOffsetDeg;
        /** Invertir tambiÃ©n en vertical: las UV de los espejos laterales del modelo van al revÃ©s. */
        final boolean flipY;

        Mirror(String id, Node node, Vector3 localCenter, Material material, int w, int h, float fov,
               float yawOffsetDeg, boolean flipY) {
            this.flipY = flipY;
            this.id = id;
            this.node = node;
            this.localCenter.set(localCenter);
            this.material = material;
            this.fbo = new FrameBuffer(Pixmap.Format.RGB888, w, h, true);
            this.camera = new PerspectiveCamera(fov, w, h);
            this.camera.near = 0.3f;
            this.camera.far = 300f;
            this.yawOffsetDeg = yawOffsetDeg;
            Texture tex = fbo.getColorBufferTexture();
            tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            material.set(PBRColorAttribute.createBaseColorFactor(Color.BLACK));
            material.set(PBRTextureAttribute.createEmissiveTexture(tex));
            material.set(PBRFloatAttribute.createEmissiveIntensity(1f));
            material.set(PBRFloatAttribute.createMetallic(0f));
            material.set(PBRFloatAttribute.createRoughness(1f));
        }
    }

    /** Las UV de los espejos laterales del Clio estÃ¡n volteadas en vertical respecto al interior. */
    private static final boolean SIDE_FLIP_Y = true;

    private final SceneRenderer renderer;
    private final CarView car;
    private final Array<Mirror> mirrors = new Array<>();
    private final Matrix4 tmpM = new Matrix4();
    private final Vector3 pos = new Vector3();
    private final Vector3 forward = new Vector3();
    private final Vector3 up = new Vector3();
    private int frame;
    private final boolean alternate;

    /**
     * @param quality   tamaÃ±o del framebuffer segÃºn calidad
     * @param alternate renderizar un espejo por frame (mÃ³vil)
     */
    public MirrorRenderer(SceneRenderer renderer, CarView car, Settings.Mirrors mode, Settings.Quality quality,
                          boolean alternate) {
        this.renderer = renderer;
        this.car = car;
        this.alternate = alternate;
        int w = quality == Settings.Quality.LOW ? 256 : quality == Settings.Quality.MEDIUM ? 384 : 512;
        int h = w / 2;
        if (mode != Settings.Mirrors.OFF) {
            add("mirror_center", w, h, 32f, 0f, false);
        }
        if (mode == Settings.Mirrors.ALL) {
            add("mirror_left", w, h, 28f, -16f, SIDE_FLIP_Y);   // girada hacia fuera (+X, izquierda)
            add("mirror_right", w, h, 28f, 16f, SIDE_FLIP_Y);   // girada hacia fuera (-X, derecha)
        }
    }

    private void add(String id, int w, int h, float fov, float yawOffset, boolean flipY) {
        Material material = car.chassis.getMaterial(id);
        if (material == null) {
            Gdx.app.log("MirrorRenderer", "El modelo del coche no tiene el material '" + id + "'");
            return;
        }
        // El espejo es la parte de malla que usa ese material: su centro da la posiciÃ³n de la cÃ¡mara
        Node node = null;
        Vector3 center = new Vector3();
        for (Node n : car.chassis.nodes) {
            node = findPart(n, material, center);
            if (node != null) {
                break;
            }
        }
        if (node == null) {
            Gdx.app.log("MirrorRenderer", "El material '" + id + "' no estÃ¡ en ninguna malla del coche");
            return;
        }
        mirrors.add(new Mirror(id, node, center, material, w, h, fov, yawOffset, flipY));
    }

    /** Busca (en el nodo y sus hijos) la parte con ese material; deja su centro local en {@code center}. */
    private static Node findPart(Node node, Material material, Vector3 center) {
        for (NodePart part : node.parts) {
            if (part.material == material) {
                part.meshPart.update();
                center.set(part.meshPart.center);
                return node;
            }
        }
        for (Node child : node.getChildren()) {
            Node found = findPart(child, material, center);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return mirrors.size == 0;
    }

    /** Renderiza las vistas de los espejos. Llamar antes de dibujar la escena principal. */
    public void render(Matrix4 chassis, float delta) {
        if (mirrors.size == 0) {
            return;
        }
        frame++;
        for (int i = 0; i < mirrors.size; i++) {
            if (alternate && (frame % mirrors.size) != i) {
                continue;
            }
            Mirror m = mirrors.get(i);
            // PosiciÃ³n del espejo en el mundo y ejes del coche
            tmpM.set(chassis).mul(m.node.globalTransform);
            pos.set(m.localCenter).mul(tmpM);
            forward.set(0f, 0f, 1f).rot(chassis).nor();
            up.set(0f, 1f, 0f).rot(chassis).nor();
            m.camera.position.set(pos);
            m.camera.direction.set(forward).scl(-1f).rotate(up, m.yawOffsetDeg).nor();
            m.camera.up.set(up);
            m.camera.update();
            // Reflejo: invertir X en la proyecciÃ³n y cambiar el sentido de las caras
            m.camera.projection.val[Matrix4.M00] *= -1f;
            if (m.flipY) {
                m.camera.projection.val[Matrix4.M11] *= -1f;
            }
            m.camera.combined.set(m.camera.projection).mul(m.camera.view);

            m.fbo.begin();
            // Sin cielo de fondo: su shader no admite la proyección volteada (pintaría el suelo arriba)
            renderer.sceneManager.setSkyBox(m.flipY ? null : renderer.skybox);
            Gdx.gl.glClearColor(0.62f, 0.76f, 0.92f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
            // Un volteo invierte el sentido de las caras; dos (X e Y) lo dejan como estaba
            Gdx.gl.glFrontFace(m.flipY ? GL20.GL_CCW : GL20.GL_CW);
            renderer.sceneManager.setCamera(m.camera);
            renderer.sceneManager.renderColors();
            Gdx.gl.glFrontFace(GL20.GL_CCW);
            m.fbo.end();
            renderer.sceneManager.setSkyBox(renderer.skybox);
        }
    }

    /** Imagen del espejo con ese id (mirror_center, mirror_left, mirror_right), o null si no se renderiza. */
    public com.badlogic.gdx.graphics.Texture getTexture(String id) {
        for (Mirror m : mirrors) {
            if (m.id.equals(id)) {
                return m.fbo.getColorBufferTexture();
            }
        }
        return null;
    }

    /** Guarda la imagen de cada espejo en cache/mirror_<id>.png (capturas de desarrollo). */
    public void dump() {
        for (Mirror m : mirrors) {
            m.fbo.begin();
            com.badlogic.gdx.graphics.Pixmap p = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(0, 0,
                m.fbo.getWidth(), m.fbo.getHeight());
            m.fbo.end();
            com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.local("cache/" + m.id + ".png"), p);
            p.dispose();
        }
    }

    @Override
    public void dispose() {
        for (Mirror m : mirrors) {
            m.fbo.dispose();
        }
        mirrors.clear();
    }
}
