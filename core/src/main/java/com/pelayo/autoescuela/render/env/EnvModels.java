package com.pelayo.autoescuela.render.env;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;

/** Piezas del entorno con textura repetida a escala real (calzada, aceras, hierba, muros). */
public final class EnvModels {

    public static final long ATTRS_UV = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
        | VertexAttributes.Usage.TextureCoordinates;

    private EnvModels() {
    }

    /** Material PBR con textura de color teñida. */
    public static Material textured(Texture texture, Color tint, float roughness) {
        return new Material(
            PBRTextureAttribute.createBaseColorTexture(texture),
            PBRColorAttribute.createBaseColorFactor(tint),
            PBRFloatAttribute.createMetallic(0f),
            PBRFloatAttribute.createRoughness(roughness));
    }

    /**
     * Caja centrada en el origen con la textura repetida cada {@code tile}
     * metros en la cara superior (las caras laterales comparten el rango de
     * UV; en losas finas apenas se ven).
     */
    public static Model box(float w, float h, float d, Material material, float tileU, float tileV) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        Node node = mb.node();
        node.id = "box";
        MeshPartBuilder part = mb.part("box", GL20.GL_TRIANGLES, ATTRS_UV, material);
        part.setUVRange(0f, 0f, w / tileU, d / tileV);
        part.box(w, h, d);
        return mb.end();
    }
}
