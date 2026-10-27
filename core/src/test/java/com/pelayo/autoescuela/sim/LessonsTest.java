package com.pelayo.autoescuela.sim;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.pelayo.autoescuela.HeadlessTestApp;
import com.pelayo.autoescuela.app.I18n;
import com.pelayo.autoescuela.data.ScenarioDef;
import com.pelayo.autoescuela.data.ScenarioDef.GoalDef;
import com.pelayo.autoescuela.data.ScenarioDef.StepDef;
import com.pelayo.autoescuela.data.VehicleSpec;
import com.pelayo.autoescuela.sim.eval.ManeuverContext;
import com.pelayo.autoescuela.sim.eval.maneuvers.Tutorial;
import com.pelayo.autoescuela.sim.vehicle.Vehicle;
import com.pelayo.autoescuela.sim.vehicle.VehicleEvent;
import com.pelayo.autoescuela.sim.world.Scenario;
import com.pelayo.autoescuela.sim.world.ScenarioLoader;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Lecciones guiadas: todas tienen pasos y textos completos, todos los tipos
 * de objetivo son conocidos y un alumno automático ({@link LessonBot}) las
 * completa con la física real del coche en un tiempo razonable.
 */
class LessonsTest {

    private static final List<String> GOAL_TYPES = Arrays.asList("clutch", "brake", "throttle", "steer", "rpm",
        "speed", "gear", "handbrake", "engine", "stopped", "moving", "distance", "zone", "indicator", "look", "event");

    private static HeadlessApplication app;

    @BeforeAll
    static void boot() {
        app = HeadlessTestApp.start();
        Bullet.init();
    }

    @AfterAll
    static void shutdown() {
        app.exit();
    }

    @Test
    void lessonsAreWellFormed() {
        int lessons = 0;
        for (String id : ScenarioDef.listIds()) {
            ScenarioDef def = ScenarioDef.load(id);
            if (!"TUTORIAL".equals(def.maneuver)) {
                continue;
            }
            lessons++;
            assertNotNull(def.lesson, id + ": maniobra TUTORIAL sin lección");
            assertTrue("lesson".equals(def.category), id + ": una lección debe ir en la categoría lesson");
            assertTrue(def.lesson.steps.size >= 3, id + ": muy pocos pasos");
            assertTrue(def.lesson.summary != null && def.lesson.summary.length > 0, id + ": sin resumen");
            for (StepDef step : def.lesson.steps) {
                assertNotNull(step.title, id + ": paso sin título");
                assertTrue(step.ack || step.goals.size > 0, id + ": '" + step.title + "' sin objetivos ni Continuar");
                for (GoalDef g : step.goals) {
                    assertTrue(GOAL_TYPES.contains(g.type), id + ": objetivo desconocido " + g.type);
                    if ("zone".equals(g.type)) {
                        assertNotNull(g.label, id + ": los objetivos de zona necesitan texto propio");
                    }
                    if ("event".equals(g.type)) {
                        VehicleEvent.valueOf(g.event);
                    }
                }
            }
        }
        assertTrue(lessons >= 6, "faltan lecciones");
    }

    @Test
    void stepsCanBeReviewedWithoutSkippingAhead() {
        ScenarioDef def = ScenarioDef.load("l1_controls");
        Tutorial t = new Tutorial();
        t.start(new ManeuverContext(new com.pelayo.autoescuela.sim.world.ScenarioGeometry(def), null,
            VehicleSpec.loadDefault(), new I18n("es")));
        com.pelayo.autoescuela.sim.vehicle.VehicleState s = new com.pelayo.autoescuela.sim.vehicle.VehicleState();
        s.engineRunning = true;
        s.rpm = 800f;
        t.acknowledge();
        t.update(s, PhysicsWorld.DT);
        assertTrue(t.getStepIndex() == 1 && !t.isReviewing(), "el paso de lectura se supera con Continuar");
        t.previous();
        assertTrue(t.getStepIndex() == 0 && t.isReviewing(), "volver atrás deja el paso en repaso");
        for (int i = 0; i < 240; i++) {
            t.update(s, PhysicsWorld.DT);
        }
        assertTrue(t.getStepIndex() == 0, "un paso en repaso no avanza solo");
        assertTrue(t.canGoForward());
        t.next();
        assertTrue(t.getStepIndex() == 1 && !t.isReviewing(), "se vuelve al paso al que se había llegado");
        assertTrue(!t.canGoForward(), "no se puede saltar a pasos no alcanzados");
        // Repasar un paso con objetivos ya cumplidos no lo da por superado sin Continuar
        s.rpm = 2500f;
        t.previous();
        t.next();
        for (int i = 0; i < 240; i++) {
            t.update(s, PhysicsWorld.DT);
        }
        assertTrue(t.getStepIndex() == 2, "el paso actual sí se supera con sus objetivos");
    }

    @Test
    void botCompletesEveryLesson() {
        VehicleSpec spec = VehicleSpec.loadDefault();
        I18n i18n = new I18n("es");
        for (String id : ScenarioDef.listIds()) {
            ScenarioDef def = ScenarioDef.load(id);
            if (def.lesson == null) {
                continue;
            }
            PhysicsWorld physics = new PhysicsWorld();
            Scenario scenario = ScenarioLoader.load(def, physics, null);
            Vehicle vehicle = new Vehicle(physics, spec, scenario.startPose);
            if (def.params.engineOff) {
                vehicle.setEngineOff();
            }
            Tutorial tutorial = new Tutorial();
            tutorial.start(new ManeuverContext(scenario.geometry, scenario, spec, i18n));
            LessonBot bot = new LessonBot(tutorial, scenario.geometry);
            float time = 0f;
            int lastStep = -1;
            float stepStart = 0f;
            while (!tutorial.isFinished()) {
                if (tutorial.getStep() != null && tutorial.getStep().ack) {
                    tutorial.acknowledge();
                }
                bot.setState(vehicle.getState());
                vehicle.step(bot, PhysicsWorld.DT);
                tutorial.update(vehicle.getState(), PhysicsWorld.DT);
                time += PhysicsWorld.DT;
                if (tutorial.getStepIndex() != lastStep) {
                    lastStep = tutorial.getStepIndex();
                    stepStart = time;
                }
                if (time - stepStart > 90f) {
                    StepDef step = tutorial.getStep();
                    fail(id + ": el alumno automático no supera el paso " + (lastStep + 1) + " '"
                        + (step != null ? step.title : "?") + "' (v=" + vehicle.getState().speedKmh + " km/h, marcha "
                        + vehicle.getState().gear + ", bullet v " + vehicle.getBody().getForwardSpeed() + ", freno " + vehicle.getState().brake + ", embrague " + vehicle.getState().clutch + ", fm " + vehicle.getState().handbrake + ", motor " + vehicle.getState().engineRunning + ", pos "
                        + vehicle.getState().position + ")");
                }
            }
            System.out.printf("Lección %s completada en %.0f s%n", id, time);
            vehicle.dispose();
            scenario.dispose();
            physics.dispose();
        }
    }
}
