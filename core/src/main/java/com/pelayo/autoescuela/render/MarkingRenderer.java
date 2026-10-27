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
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.pelayo.autoescuela.sim.world.Marking;

/** Pinta las marcas viales como losetas blancas finísimas sobre el asfalto. */
public final class MarkingRenderer {

    private static final float HEIGHT = 0.012f;
    private static final long ATTRS = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;

    private MarkingRenderer() {
    }

    public static Model build(Array<Marking> markings) {
        ModelBuilder mb = new ModelBuilder();
        Material white = PlaceholderModels.material(new Color(0.95f, 0.95f, 0.92f, 1f), 0f, 0.9f);
        mb.begin();
        int n = 0;
        for (Marking m : markings) {
            if (m.kind == Marking.Kind.GUIDE) {
                continue; // invisible
            }
            float width = m.kind == Marking.Kind.STOP ? Math.max(m.width, 0.4f) : m.width;
            float[][] pts = m.points;
            for (int i = 1; i < pts.length; i++) {
                float x0 = pts[i - 1][0], z0 = pts[i - 1][1];
                float x1 = pts[i][0], z1 = pts[i][1];
                float dx = x1 - x0, dz = z1 - z0;
                float len = (float) Math.sqrt(dx * dx + dz * dz);
                if (len < 1e-4f) {
                    continue;
                }
                Node node = mb.node();
                node.id = "marking" + (n++);
                node.translation.set((x0 + x1) / 2f, HEIGHT / 2f, (z0 + z1) / 2f);
                node.rotation.setFromAxis(Vector3.Y, MathUtils.atan2(dx, dz) * MathUtils.radiansToDegrees);
                MeshPartBuilder part = mb.part(node.id, GL20.GL_TRIANGLES, ATTRS, white);
                part.box(width, HEIGHT, len + width);
            }
        }
        return mb.end();
    }
}
