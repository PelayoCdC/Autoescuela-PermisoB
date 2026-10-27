package com.pelayo.autoescuela.ui.widgets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.Array;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import com.pelayo.autoescuela.sim.world.Scenario;
import com.pelayo.autoescuela.sim.world.Zone;

/**
 * Minimapa del escenario visto desde arriba con el coche en el centro y
 * mirando hacia arriba (gira con el coche): calzadas, aceras, marcas, plazas,
 * coches aparcados, conos y las zonas objetivo resaltadas en ámbar.
 */
public class MiniMap extends Actor {

    private static final Color TARGET = new Color(0.95f, 0.69f, 0.23f, 1f);
    private static final Color CAR = new Color(0.30f, 0.65f, 1f, 1f);

    private final Scenario scenario;
    private final MapPainter painter;
    private final TextureRegion white;
    private final TextureRegion circle;
    private final TextureRegion arrow;
    private final Drawable frame;
    /** Píxeles por metro. */
    private float scale = 3.4f;
    private VehicleState state;
    private final Array<Zone> targets = new Array<>();
    private float blink;

    private final Rectangle scissor = new Rectangle();
    private final Rectangle area = new Rectangle();
    private final Matrix4 tmpM = new Matrix4();
    private final Vector3 tmpV = new Vector3();

    public MiniMap(Skin skin, Scenario scenario, float size) {
        this.scenario = scenario;
        this.painter = new MapPainter(skin, scenario.def, scenario.geometry);
        this.white = skin.getRegion("white");
        this.circle = skin.getRegion("circle");
        this.arrow = skin.getRegion("arrow");
        this.frame = skin.getDrawable("hud-box");
        this.mapIcon = ((com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable) skin.getDrawable("icon-map"))
            .getRegion();
        setSize(size, size);
        addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                toggle();
            }
        });
    }

    /** Tamaño del botón que queda con el minimapa plegado (arriba a la derecha de su sitio). */
    private static final float BUTTON = 56f;
    private final TextureRegion mapIcon;
    private boolean collapsed;
    private Runnable onToggle;

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    /** Se llama al plegarlo o desplegarlo con un toque (para guardarlo en los ajustes). */
    public void setOnToggle(Runnable onToggle) {
        this.onToggle = onToggle;
    }

    public void toggle() {
        collapsed = !collapsed;
        if (onToggle != null) {
            onToggle.run();
        }
    }

    @Override
    public com.badlogic.gdx.scenes.scene2d.Actor hit(float x, float y, boolean touchable) {
        if (collapsed && (x < getWidth() - BUTTON || y < getHeight() - BUTTON)) {
            return null; // plegado: solo responde el botón
        }
        return super.hit(x, y, touchable);
    }

    public void setState(VehicleState state) {
        this.state = state;
    }

    /** Zonas a resaltar (objetivo del paso de la lección o de la maniobra). */
    public void setTargets(Array<Zone> zones) {
        targets.clear();
        if (zones != null) {
            targets.addAll(zones);
        }
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        blink += delta;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (state == null) {
            return;
        }
        batch.setColor(1f, 1f, 1f, parentAlpha);
        if (collapsed) {
            float bx = getX() + getWidth() - BUTTON, by = getY() + getHeight() - BUTTON;
            frame.draw(batch, bx, by, BUTTON, BUTTON);
            float is = BUTTON * 0.6f;
            batch.setColor(0.88f, 0.91f, 0.96f, parentAlpha);
            batch.draw(mapIcon, bx + (BUTTON - is) / 2f, by + (BUTTON - is) / 2f, is, is);
            batch.setColor(Color.WHITE);
            return;
        }
        frame.draw(batch, getX() - 6f, getY() - 6f, getWidth() + 12f, getHeight() + 12f);

        batch.flush();
        area.set(getX(), getY(), getWidth(), getHeight());
        getStage().calculateScissors(area, scissor);
        if (!ScissorStack.pushScissors(scissor)) {
            return;
        }
        // Centrado en el coche y girando con él (su rumbo queda hacia arriba)
        painter.setView(getX(), getY(), getWidth(), getHeight(), state.position.x, state.position.z, state.yawDeg, scale);
        painter.drawScenery(batch);
        batch.setColor(MapPainter.CONE);
        for (Scenario.Movable mv : scenario.movables) {
            mv.body.getWorldTransform(tmpM);
            tmpM.getTranslation(tmpV);
            painter.disc(batch, tmpV.x, tmpV.z, 0.45f);
        }
        // Tráfico: coches en gris claro y peatones en amarillo
        for (com.pelayo.autoescuela.sim.world.TrafficAgent t : scenario.traffic.agents) {
            if (t.type == com.pelayo.autoescuela.sim.world.TrafficAgent.Type.CAR) {
                batch.setColor(0.85f, 0.87f, 0.9f, 1f);
                painter.rect(batch, t.x, t.z, 1.7f, 4.0f, t.yawDeg);
            } else {
                batch.setColor(1f, 0.85f, 0.2f, 1f);
                painter.disc(batch, t.x, t.z, 0.5f);
            }
        }
        float pulse = 0.55f + 0.35f * MathUtils.sin(blink * 4f);
        for (Zone z : targets) {
            batch.setColor(TARGET.r, TARGET.g, TARGET.b, pulse);
            painter.rect(batch, z.cx, z.cz, z.halfX * 2f, z.halfZ * 2f, z.yawDeg);
        }

        // El coche: siempre en el centro, mirando arriba
        float px = getX() + getWidth() / 2f;
        float py = getY() + getHeight() / 2f;
        batch.setColor(0f, 0f, 0f, 0.5f);
        batch.draw(white, px - 0.9f * scale - 1f, py - 2f * scale - 1f, 1.8f * scale + 2f, 4f * scale + 2f);
        batch.setColor(CAR);
        batch.draw(white, px - 0.85f * scale, py - 2f * scale, 1.7f * scale, 4f * scale);
        batch.setColor(Color.WHITE);
        batch.draw(arrow, px - 6f, py - 6f, 6f, 6f, 12f, 12f, 1f, 1f, -90f);
        batch.flush();
        ScissorStack.popScissors();
        batch.setColor(Color.WHITE);
    }
}
