package com.pelayo.autoescuela.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.pelayo.autoescuela.AutoescuelaGame;
import com.pelayo.autoescuela.app.Screenshots;
import com.pelayo.autoescuela.audio.EngineAudio;
import com.pelayo.autoescuela.data.ScenarioDef;
import com.pelayo.autoescuela.data.VehicleSpec;
import com.pelayo.autoescuela.input.CompositeDriverInput;
import com.pelayo.autoescuela.input.DriverInput;
import com.pelayo.autoescuela.input.DriverInputState;
import com.pelayo.autoescuela.input.GamepadDriverInput;
import com.pelayo.autoescuela.input.InputMapping.Action;
import com.pelayo.autoescuela.input.KeyboardMouseDriverInput;
import com.pelayo.autoescuela.input.ScriptedDriverInput;
import com.pelayo.autoescuela.input.TouchDriverInput;
import com.pelayo.autoescuela.render.CarView;
import com.pelayo.autoescuela.render.DashboardCluster;
import com.pelayo.autoescuela.render.ChaseCamera;
import com.pelayo.autoescuela.render.CockpitCamera;
import com.pelayo.autoescuela.render.MirrorRenderer;
import com.pelayo.autoescuela.render.PhysicsDebugView;
import com.pelayo.autoescuela.render.SceneRenderer;
import com.pelayo.autoescuela.sim.FixedStepper;
import com.pelayo.autoescuela.sim.PhysicsWorld;
import com.pelayo.autoescuela.sim.eval.EvaluationResult;
import com.pelayo.autoescuela.sim.eval.Maneuver;
import com.pelayo.autoescuela.sim.eval.ManeuverContext;
import com.pelayo.autoescuela.sim.eval.ManeuverRegistry;
import com.pelayo.autoescuela.sim.vehicle.Vehicle;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import com.pelayo.autoescuela.sim.world.Scenario;
import com.pelayo.autoescuela.sim.world.ScenarioLoader;
import com.pelayo.autoescuela.ui.widgets.ControlsOverlay;
import com.pelayo.autoescuela.ui.widgets.HudOverlay;
import com.pelayo.autoescuela.ui.widgets.MiniMap;
import com.pelayo.autoescuela.ui.widgets.MirrorInset;
import com.pelayo.autoescuela.ui.widgets.TouchControls;
import com.pelayo.autoescuela.ui.widgets.TutorialPanel;
import com.pelayo.autoescuela.sim.eval.maneuvers.Tutorial;

/**
 * Pantalla de conducción: carga un escenario, simula el coche a paso fijo,
 * evalúa la maniobra y al terminar pasa a la pantalla de resultados.
 */
public class DrivingScreen extends ScreenAdapter {

    private final AutoescuelaGame game;
    private final ScenarioDef def;
    private VehicleSpec spec;

    private final PhysicsWorld physics = new PhysicsWorld();
    private final FixedStepper stepper = new FixedStepper(PhysicsWorld.DT, 8);
    private final SceneRenderer renderer;
    private final Scenario scenario;
    private Vehicle vehicle;
    private DriverInput driver;
    private KeyboardMouseDriverInput keyboard;
    private Maneuver maneuver;
    private boolean resultShown;

    private CarView carView;
    private CockpitCamera cockpit;
    private ChaseCamera chase;
    private boolean chaseActive;
    private int cameraMode;
    private final PhysicsDebugView debugView = new PhysicsDebugView(physics);
    private EngineAudio audio;
    private MirrorRenderer mirrors;
    /** Cuadro de instrumentos del salpicadero (null con el coche placeholder). */
    private DashboardCluster cluster;

    private final Stage hud;
    private final HudOverlay hudOverlay;
    private final Label debugLabel;
    private final ControlsOverlay controlsOverlay;
    private boolean showDebug;
    private int nextShot = 0;
    private float finishDelay = -1f;
    private TouchControls touchControls;
    private boolean paused;
    /** Menú de pausa (Esc / P): continuar, reiniciar, controles, salir. */
    private final Table pauseMenu = new Table();
    private MiniMap miniMap;
    /** Retrovisor derecho en pantalla (null si está desactivado o no se renderiza ese espejo). */
    private MirrorInset rightMirror;
    /** Botón de pausa en la disposición táctil (no hay tecla Esc). */
    private com.badlogic.gdx.scenes.scene2d.ui.TextButton pauseButton;
    /** Panel de la lección guiada (solo con maniobra TUTORIAL). */
    private final Table lessonHolder = new Table();
    private TutorialPanel lessonPanel;

    public DrivingScreen(AutoescuelaGame game, ScenarioDef def) {
        this.game = game;
        this.def = def;
        spec = VehicleSpec.loadDefault();
        boolean mobile = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
        com.pelayo.autoescuela.app.Settings.Quality quality = game.settings.getQuality();
        int shadows = quality == com.pelayo.autoescuela.app.Settings.Quality.LOW ? 0
            : quality == com.pelayo.autoescuela.app.Settings.Quality.MEDIUM || mobile ? 1024 : 2048;
        renderer = new SceneRenderer(shadows, quality == com.pelayo.autoescuela.app.Settings.Quality.HIGH && !mobile);

        // Tamaño del HUD elegido en Ajustes: más unidades virtuales en pantalla = elementos más pequeños
        hud = new Stage(hudViewport());
        if (wantsTouch()) {
            createTouchControls();
        }

        scenario = ScenarioLoader.load(def, physics, renderer);
        createVehicle();
        startManeuver();
        try {
            audio = new EngineAudio(game.settings.getMasterVolume());
        } catch (Exception e) {
            Gdx.app.error("DrivingScreen", "Sin audio", e);
        }

        hudOverlay = new HudOverlay(game.skin, game.i18n, spec);
        hud.addActor(hudOverlay);
        updateKeyNames();
        if (touchControls != null) {
            hud.addActor(touchControls);
        }

        Table table = new Table();
        table.setFillParent(true);
        table.top().left().pad(36f, 12f, 12f, 12f);
        debugLabel = new Label("", game.skin, "small");
        debugLabel.setVisible(false);
        table.add(debugLabel).left().row();
        hud.addActor(table);

        // Ayuda de controles: centrada, visible al empezar (salvo táctil o guion) y con F1
        controlsOverlay = new ControlsOverlay(game.skin, game.i18n, game.keys);
        Table center = new Table();
        center.setFillParent(true);
        center.center();
        center.add(controlsOverlay);
        hud.addActor(center);
        controlsOverlay.setVisible(touchControls == null && game.dev.script == null && !game.dev.hideHelp
            && def.lesson == null);

        // Ayuda de teclas arriba (sin controles táctiles)
        helpTop.setFillParent(true);
        helpTop.top().padTop(6f);
        Label helpLabel = new Label(game.i18n.t("hud.help", game.keys.keyName(Action.HELP)), game.skin, "small");
        helpLabel.setFontScale(0.85f);
        helpTop.add(helpLabel);
        helpTop.setVisible(touchControls == null);
        hud.addActor(helpTop);

        // Lección guiada: panel arriba a la izquierda (arriba en el centro con controles táctiles)
        lessonHolder.setFillParent(true);
        // Arriba a la izquierda (en táctil esa esquina queda libre: los botones van junto al volante)
        lessonHolder.top().left().pad(40f, 20f, 20f, 20f);
        hud.addActor(lessonHolder);
        attachLessonPanel();

        // Minimapa arriba a la derecha (a la izquierda de las marchas en la disposición táctil)
        miniMap = new MiniMap(game.skin, scenario, 210f);
        miniMap.setCollapsed(!game.settings.isMinimap());
        miniMap.setOnToggle(() -> {
            game.settings.setMinimap(!miniMap.isCollapsed());
            game.settings.flush();
        });
        hud.addActor(miniMap);
        createMirrorInset();
        // Botón de pausa junto al minimapa (en táctil no hay tecla Esc)
        pauseButton = new com.badlogic.gdx.scenes.scene2d.ui.TextButton("II", game.skin, "small");
        pauseButton.setSize(56f, 44f);
        pauseButton.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                setPaused(!paused);
            }
        });
        pauseButton.setVisible(touchControls != null);
        hud.addActor(pauseButton);
        buildPauseMenu();
        hud.addActor(pauseMenu);
        hud.addActor(center); // el panel de controles por encima del menú de pausa
    }

    private void createVehicle() {
        if (vehicle != null) {
            carView.removeFrom(renderer);
            carView.dispose();
            vehicle.dispose();
        }
        vehicle = new Vehicle(physics, spec, scenario.startPose);
        if (def.params.engineOff) {
            vehicle.setEngineOff();
        }
        scenario.traffic.setPlayer(null); // durante el asentamiento el tráfico no reacciona al coche
        vehicle.settle(1.5f);
        scenario.traffic.reset();
        scenario.traffic.setPlayer(vehicle.getState());
        keyboard = new KeyboardMouseDriverInput(spec, game.keys);
        carView = CarView.create(spec);
        buildDriver();
        carView.setPaint(game.dev.paint != null ? com.pelayo.autoescuela.app.CarPaint.parse(game.dev.paint)
            : game.settings.getCarPaint());
        carView.addTo(renderer);
        float aspect = (float) Gdx.graphics.getWidth() / Gdx.graphics.getHeight();
        createCockpitCamera();
        chase = new ChaseCamera(aspect);
        chase.snap(vehicle.getState().transform);
        if (mirrors != null) {
            mirrors.dispose();
        }
        boolean mobile = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
        mirrors = new MirrorRenderer(renderer, carView, game.settings.getMirrors(), game.settings.getQuality(), mobile);
        if (cluster != null) {
            cluster.dispose();
        }
        cluster = DashboardCluster.attach(carView, game.skin, spec.engine.redlineRpm);
        if (miniMap != null) {
            createMirrorInset(); // F5 recrea los espejos: la imagen del recuadro cambia
        }
    }

    /** Crea el panel de la lección para el evaluador actual (se recrea al recolocar). */
    private void attachLessonPanel() {
        lessonHolder.clearChildren();
        lessonPanel = null;
        if (maneuver instanceof Tutorial) {
            float width = 430f;
            lessonPanel = new TutorialPanel(game.skin, game.i18n, game.keys, (Tutorial) maneuver, def.name, width,
                () -> ((Tutorial) maneuver).acknowledge());
            lessonHolder.add(lessonPanel);
        }
    }

    private void startManeuver() {
        maneuver = ManeuverRegistry.create(def.maneuver);
        maneuver.start(new ManeuverContext(scenario.geometry, scenario, spec, game.i18n));
        resultShown = false;
        finishDelay = -1f;
    }

    @Override
    public void show() {
        if (shownBefore) {
            applySettings(); // de vuelta de los ajustes abiertos desde la pausa
        }
        shownBefore = true;
        Gdx.input.setInputProcessor(new InputMultiplexer(hud, keyboard.getProcessor()));
        stepper.reset();
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACK)) {
            if (controlsOverlay.isVisible()) {
                controlsOverlay.setVisible(false);
            } else {
                setPaused(!paused);
            }
        }
        if (game.keys.isJustPressed(Action.MAP)) {
            miniMap.toggle();
        }
        if (game.keys.isJustPressed(Action.CONTINUE) && maneuver instanceof Tutorial) {
            ((Tutorial) maneuver).acknowledge();
        }
        if (game.keys.isJustPressed(Action.LESSON_BACK) && maneuver instanceof Tutorial) {
            ((Tutorial) maneuver).previous();
        }
        if (game.keys.isJustPressed(Action.HELP)) {
            controlsOverlay.setVisible(!controlsOverlay.isVisible());
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F2)) {
            showDebug = !showDebug;
            debugLabel.setVisible(showDebug);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            debugView.toggle();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
            reloadSpec();
        }
        if (game.keys.isJustPressed(Action.PAUSE)) {
            setPaused(!paused);
        }

        int steps = paused ? 0 : stepper.advance(delta);
        for (int i = 0; i < steps; i++) {
            vehicle.step(driver, stepper.getDt());
            VehicleState state = vehicle.getState();
            DriverInputState in = vehicle.getLastInput();
            // La ayuda inicial se cierra sola en cuanto se empieza a conducir
            if (controlsOverlay.isVisible() && (in.throttle > 0.1f || in.handbrakeToggle || in.gearSelect != DriverInputState.GEAR_NONE)) {
                controlsOverlay.setVisible(false);
            }
            if (in.cameraToggle) {
                // Cabina → exterior → cenital → cabina
                cameraMode = (cameraMode + 1) % 3;
                chaseActive = cameraMode != 0;
                chase.setTopView(cameraMode == 2);
                if (cameraMode == 1) {
                    chase.snap(state.transform);
                }
            }
            if (in.lessonContinue && maneuver instanceof Tutorial) {
                ((Tutorial) maneuver).acknowledge();
            }
            if (in.resetVehicle) {
                restart();
            }
            maneuver.update(state, stepper.getDt());
            hapticFeedback(state);
            if (state.events.size > 0) {
                hudOverlay.consumeEvents(state);
                if (audio != null) {
                    audio.update(state, 0f);
                    if (in.ignition && state.engineStarting) {
                        audio.playStarter();
                    }
                }
            }
        }

        VehicleState state = vehicle.getState();
        scenario.syncRender();
        carView.update(state, vehicle.getBody());
        DriverInputState in = vehicle.getLastInput();
        cockpit.update(state.transform, in.lookYaw, in.lookPitch, delta);
        chase.update(state.transform, delta);
        Camera camera = chaseActive ? chase.camera : cockpit.camera;
        if (!chaseActive) {
            mirrors.render(state.transform, delta); // antes de la vista principal
            if (cluster != null) {
                cluster.update(state, delta);
            }
        }
        renderer.setCamera(camera);
        if (audio != null) {
            if (steps > 0) {
                state.events.clear(); // ya consumidos en los pasos
            }
            audio.update(state, delta);
        }

        Gdx.gl.glClearColor(0.5f, 0.7f, 0.9f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        renderer.render(delta);
        debugView.render(camera);

        hudOverlay.setState(state);
        // Con el cuadro en el salpicadero, en la cabina se ocultan los relojes de pantalla
        hudOverlay.setSteeringVisible(chaseActive);
        hudOverlay.setGaugesVisible(chaseActive || cluster == null
            || game.settings.getGauges() == com.pelayo.autoescuela.app.Settings.Gauges.SCREEN);
        miniMap.setState(state);
        miniMap.setTargets(maneuver.getTargetZones());
        float hudW = hud.getViewport().getWorldWidth();
        float hudH = hud.getViewport().getWorldHeight();
        miniMap.setPosition(hudW - miniMap.getWidth() - 22f, hudH - miniMap.getHeight() - 40f);
        hudOverlay.setTopRightLimit(Float.MAX_VALUE);
        if (rightMirror != null) {
            // Debajo del minimapa, a la derecha (donde estaría el espejo); no al mirar a ese lado o desde fuera
            rightMirror.setVisible(!chaseActive && state.lookYaw > -0.35f);
            float mirrorY = miniMap.getY() - rightMirror.getHeight() - 46f;
            float floor = touchControls != null ? touchControls.getRightSideTop() + 12f : 0f;
            if (mirrorY - 6f >= floor) {
                rightMirror.setPosition(hudW - rightMirror.getWidth() - 22f, mirrorY);
            } else {
                // Con el HUD grande no cabe sin pisar los mandos: arriba, a la izquierda de los botones redondos
                float left = miniMap.getX() - 76f - 16f - rightMirror.getWidth();
                rightMirror.setPosition(left, hudH - 74f - rightMirror.getHeight());
                if (rightMirror.isVisible()) {
                    hudOverlay.setTopRightLimit(left - 6f);
                }
            }
        }
        if (touchControls != null) {
            com.badlogic.gdx.math.Rectangle pedals = touchControls.getPedalArea();
            hudOverlay.setTouchGaugeAnchor(pedals.x, pedals.y + pedals.height + 4f, pedals.width);
        }
        if (pauseButton != null) {
            pauseButton.setPosition(miniMap.getX() - 72f, hudH - 40f - 44f); // a la izquierda del minimapa
        }
        speakInstruction(maneuver.getInstruction());
        hudOverlay.setInstruction(maneuver.getInstruction());
        hudOverlay.setStatus(maneuver.getStatus());
        if (lessonPanel != null) {
            lessonPanel.update();
            Tutorial tutorial = (Tutorial) maneuver;
            hudOverlay.setHighlights(tutorial.getStep() != null ? tutorial.getStep().highlight : null);
        }
        if (touchControls != null) {
            touchControls.setVehicleState(state);
            placeTouchWheel(state);
        }
        if (showDebug) {
            updateDebug(state);
        }
        monitorPerformance(delta);
        hud.act(delta);
        hud.draw();

        devCaptures(state.time);

        // Al terminar la maniobra, un instante para ver el aviso y a resultados
        if (maneuver.isFinished() && !resultShown) {
            if (finishDelay < 0f) {
                finishDelay = 1.5f;
                hudOverlay.show(game.i18n.t(def.lesson != null ? "lesson.done" : "maneuver.done"));
            }
            finishDelay -= delta;
            if (finishDelay <= 0f) {
                showResults(maneuver.getResult());
            }
        }
    }

    private final com.badlogic.gdx.math.Vector3 wheelCenter = new com.badlogic.gdx.math.Vector3();
    private final com.badlogic.gdx.math.Vector3 wheelEdge = new com.badlogic.gdx.math.Vector3();
    private final com.badlogic.gdx.math.Vector2 screenTmp = new com.badlogic.gdx.math.Vector2();

    /**
     * Zona táctil del volante: en la cabina, justo encima del volante del coche
     * (se proyecta su centro y su aro a la pantalla); con las cámaras de fuera,
     * un volante dibujado abajo en el centro.
     */
    private void placeTouchWheel(VehicleState state) {
        float w = hud.getViewport().getWorldWidth();
        if (!chaseActive && carView.getSteeringWheelCenter(state.transform, wheelCenter)) {
            com.badlogic.gdx.graphics.PerspectiveCamera cam = cockpit.camera;
            wheelEdge.set(cam.direction).crs(cam.up).nor().scl(0.19f).add(wheelCenter);
            cam.project(wheelCenter);
            cam.project(wheelEdge);
            // project() da píxeles de ventana con Y hacia arriba; el stage, Y hacia abajo en pantalla
            screenTmp.set(wheelCenter.x, Gdx.graphics.getHeight() - wheelCenter.y);
            hud.getViewport().unproject(screenTmp);
            float cx = screenTmp.x, cy = screenTmp.y;
            screenTmp.set(wheelEdge.x, Gdx.graphics.getHeight() - wheelEdge.y);
            hud.getViewport().unproject(screenTmp);
            float r = Math.max(110f, (float) Math.hypot(screenTmp.x - cx, screenTmp.y - cy) * 1.2f);
            touchControls.setWheelAnchor(cx, cy, r, true);
        } else {
            touchControls.setWheelAnchor(w / 2f, 170f, 140f, false);
        }
    }

    /** Cámara de los ojos del conductor con el encuadre de los ajustes (o el de las opciones de desarrollo). */
    private void createCockpitCamera() {
        float aspect = (float) Gdx.graphics.getWidth() / Gdx.graphics.getHeight();
        com.pelayo.autoescuela.app.Settings.CockpitView view = game.settings.getCockpitView();
        com.badlogic.gdx.math.Vector3 eye = game.dev.eye != null
            ? new com.badlogic.gdx.math.Vector3(game.dev.eye[0], game.dev.eye[1], game.dev.eye[2])
            : new com.badlogic.gdx.math.Vector3(carView.getEyeOffset()).add(0f, view.raise, view.forward);
        cockpit = new CockpitCamera(eye, aspect);
        cockpit.setFraming(game.dev.fov > 0f ? game.dev.fov : view.fov, game.dev.eye != null ? 0f : view.pitchDeg);
    }

    private float perfTime;
    private int perfFrames;
    private int slowWindows;
    private Label fpsLabel;

    /**
     * FPS en pantalla (si se pide) y calidad automática en el móvil: si en dos tramos seguidos de 4 s no
     * llega a 26 FPS, baja un escalón (sin sombras; luego espejos en baja) y lo guarda en los ajustes.
     */
    private void monitorPerformance(float delta) {
        if (fpsLabel == null) {
            fpsLabel = new Label("", game.skin, "small");
            fpsLabel.setColor(0.7f, 1f, 0.7f, 0.9f);
            hud.addActor(fpsLabel);
        }
        fpsLabel.setVisible(game.settings.isShowFps());
        if (fpsLabel.isVisible()) {
            fpsLabel.setText(Gdx.graphics.getFramesPerSecond() + " FPS");
            fpsLabel.setPosition(20f, hud.getViewport().getWorldHeight() - 30f);
        }
        boolean mobile = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
        if (!mobile || !game.settings.isAutoQuality() || paused) {
            return;
        }
        perfTime += delta;
        perfFrames++;
        if (perfTime < 4f) {
            return;
        }
        float fps = perfFrames / perfTime;
        perfTime = 0f;
        perfFrames = 0;
        slowWindows = fps < 26f ? slowWindows + 1 : 0;
        if (slowWindows < 2) {
            return;
        }
        slowWindows = 0;
        com.pelayo.autoescuela.app.Settings.Quality q = game.settings.getQuality();
        com.pelayo.autoescuela.app.Settings.Quality lower = q == com.pelayo.autoescuela.app.Settings.Quality.HIGH
            ? com.pelayo.autoescuela.app.Settings.Quality.MEDIUM : com.pelayo.autoescuela.app.Settings.Quality.LOW;
        if (renderer.hasShadows()) {
            renderer.setShadows(false);
        } else if (q == com.pelayo.autoescuela.app.Settings.Quality.LOW) {
            return; // ya está todo al mínimo
        } else {
            lower = com.pelayo.autoescuela.app.Settings.Quality.LOW;
            mirrors.dispose();
            mirrors = new MirrorRenderer(renderer, carView, game.settings.getMirrors(), lower, true);
            createMirrorInset();
        }
        game.settings.setQuality(lower);
        game.settings.flush();
        hudOverlay.show(game.i18n.t("hud.qualityLowered", game.i18n.t("settings.quality." + lower.name())));
    }

    private String spokenInstruction;

    /** Cada indicación nueva del examinador se lee en alto (no en las lecciones, ni en capturas con guion). */
    private void speakInstruction(String instruction) {
        if (instruction == null || instruction.equals(spokenInstruction)) {
            return;
        }
        spokenInstruction = instruction;
        if (def.lesson == null && game.dev.script == null && game.settings.isVoice() && !instruction.isEmpty()) {
            game.speech.speak(instruction);
        }
    }

    private boolean shownBefore;
    private boolean wasHitting;

    /** Vibración al calar y al chocar contra algo (en el móvil, con el ajuste activado). */
    private void hapticFeedback(VehicleState state) {
        if (state.events.contains(com.pelayo.autoescuela.sim.vehicle.VehicleEvent.STALL, true)) {
            com.pelayo.autoescuela.app.Haptics.pulse(game.settings, com.pelayo.autoescuela.app.Haptics.STALL);
        }
        boolean hitting = false;
        for (com.pelayo.autoescuela.sim.BodyTag tag : state.contacts) {
            hitting |= tag == com.pelayo.autoescuela.sim.BodyTag.BARRIER || tag == com.pelayo.autoescuela.sim.BodyTag.PARKED_CAR
                || tag == com.pelayo.autoescuela.sim.BodyTag.CONE || tag == com.pelayo.autoescuela.sim.BodyTag.TRAFFIC_CAR
                || tag == com.pelayo.autoescuela.sim.BodyTag.PEDESTRIAN;
        }
        if (hitting && !wasHitting) {
            com.pelayo.autoescuela.app.Haptics.pulse(game.settings, com.pelayo.autoescuela.app.Haptics.HIT);
        }
        wasHitting = hitting;
    }
    private final Table helpTop = new Table();

    /** Controles táctiles: por el ajuste, o forzados/quitados con las opciones de desarrollo. */
    private boolean wantsTouch() {
        return (game.settings.isTouchControls() && !game.dev.noTouch) || game.dev.touch;
    }

    private boolean wantsPedalsLeft() {
        return game.dev.pedals != null ? !"right".equalsIgnoreCase(game.dev.pedals)
            : game.settings.getPedalSide() == com.pelayo.autoescuela.app.Settings.PedalSide.LEFT;
    }

    /** Resolución virtual del HUD según su tamaño: más unidades en pantalla = elementos más pequeños. */
    private ExtendViewport hudViewport() {
        com.pelayo.autoescuela.app.Settings.HudSize hudSize = game.dev.hud != null
            ? com.pelayo.autoescuela.app.Settings.HudSize.valueOf(game.dev.hud.toUpperCase(java.util.Locale.ROOT))
            : game.settings.getHudSize();
        float scale = hudSize.viewportScale;
        return new ExtendViewport(AutoescuelaGame.UI_WIDTH * scale, AutoescuelaGame.UI_HEIGHT * scale);
    }

    private void createTouchControls() {
        touchControls = new TouchControls(game.skin, game.i18n, AutoescuelaGame.UI_WIDTH, AutoescuelaGame.UI_HEIGHT,
            spec.clutch.bitePointLow, spec.clutch.bitePointHigh, wantsPedalsLeft(), game.settings.isStickyClutch(),
            ms -> com.pelayo.autoescuela.app.Haptics.pulse(game.settings, ms));
        // En táctil, las lecciones y los avisos nombran los mandos de la pantalla en vez de teclas
        game.keys.setNameOverride(a -> game.i18n.t("touchname." + a.name()));
    }

    /** Textos del HUD que nombran mandos (teclas o, en táctil, los de la pantalla). */
    private void updateKeyNames() {
        hudOverlay.setHintKeys(game.keys.keyName(Action.CLUTCH), game.keys.keyName(Action.IGNITION));
        hudOverlay.setTouchLayout(touchControls != null);
        hudOverlay.setPedalKeys(game.keys.keyName(Action.CLUTCH), game.keys.keyName(Action.BRAKE),
            game.keys.keyName(Action.THROTTLE));
    }

    /** Fuente de mandos: guion, o táctil + teclado + mando, o teclado + mando. */
    private void buildDriver() {
        if (game.dev.script != null) {
            driver = new ScriptedDriverInput(game.dev.script);
        } else if (touchControls != null) {
            driver = new CompositeDriverInput(new TouchDriverInput(spec, touchControls),
                new CompositeDriverInput(keyboard, new GamepadDriverInput(spec)));
        } else {
            driver = new CompositeDriverInput(keyboard, new GamepadDriverInput(spec));
        }
        // Con el volante táctil, el de la cabina sigue al dedo: media vuelta = tope
        carView.setWheelTurnsShown(touchControls != null ? 1f : -1f);
    }

    /**
     * Aplica los ajustes cambiados desde el menú de pausa sin reiniciar la práctica: volumen, tamaño del HUD,
     * controles táctiles y lado de los pedales, color del coche, espejos y retrovisor en pantalla. La calidad
     * gráfica (sombras) necesita rehacer el render y se aplica en la siguiente práctica.
     */
    private void applySettings() {
        if (audio != null) {
            audio.setMasterVolume(game.settings.getMasterVolume());
        }
        hud.setViewport(hudViewport());
        hud.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

        // Los mandos táctiles se rehacen siempre: lado de los pedales, embrague fijo, activarlos o quitarlos
        boolean touch = wantsTouch();
        {
            if (touchControls != null) {
                touchControls.remove();
                touchControls = null;
                game.keys.setNameOverride(null);
            }
            if (touch) {
                createTouchControls();
                hud.getRoot().addActorAfter(hudOverlay, touchControls);
            }
            buildDriver();
            updateKeyNames();
            attachLessonPanel(); // sus textos nombran las teclas o los mandos táctiles
            pauseButton.setVisible(touchControls != null);
            helpTop.setVisible(touchControls == null);
            controlsOverlay.setVisible(false);
        }
        if (touchControls != null) {
            touchControls.relayout(hud.getViewport().getWorldWidth(), hud.getViewport().getWorldHeight());
        }

        miniMap.setCollapsed(!game.settings.isMinimap());
        createCockpitCamera();
        cockpit.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        if (game.dev.paint == null) {
            carView.setPaint(game.settings.getCarPaint());
        }
        // Espejos (calidad y cuáles se ven) y su recuadro en pantalla
        if (mirrors != null) {
            mirrors.dispose();
        }
        boolean mobile = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
        mirrors = new MirrorRenderer(renderer, carView, game.settings.getMirrors(), game.settings.getQuality(), mobile);
        createMirrorInset();
    }

    private void createMirrorInset() {
        if (rightMirror != null) {
            rightMirror.remove();
            rightMirror = null;
        }
        com.badlogic.gdx.graphics.Texture tex = mirrors != null ? mirrors.getTexture("mirror_right") : null;
        if (tex != null && game.settings.isRightMirrorInset()) {
            rightMirror = new MirrorInset(game.skin, tex, game.i18n.t("hud.rightMirror"), false, 250f, 125f);
            hud.addActor(rightMirror);
            rightMirror.toBack(); // por detrás del panel de controles y del menú de pausa
        }
    }

    /** Vuelve a empezar la práctica en el sitio de salida. */
    private void restart() {
        vehicle.reset(scenario.startPose);
        if (def.params.engineOff) {
            vehicle.setEngineOff();
        }
        vehicle.settle(1.5f);
        scenario.traffic.reset();
        startManeuver();
        attachLessonPanel();
    }

    private void setPaused(boolean value) {
        paused = value;
        pauseMenu.setVisible(paused);
        stepper.reset();
        if (paused) {
            pauseMenu.getColor().a = 0f;
            pauseMenu.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(0.15f));
        }
    }

    private void buildPauseMenu() {
        Table box = new Table();
        box.setBackground(game.skin.getDrawable("panel-dark"));
        box.pad(26f, 34f, 26f, 34f);
        box.add(new Label(game.i18n.t("pause.title"), game.skin, "title")).padBottom(18f).row();
        String[] keys = {"pause.resume", "pause.restart", "pause.controls", "pause.settings", "pause.exit"};
        Runnable[] actions = {
            () -> setPaused(false),
            () -> {
                restart();
                setPaused(false);
            },
            () -> controlsOverlay.setVisible(!controlsOverlay.isVisible()),
            // La práctica sigue en pausa detrás; al volver se aplican los cambios (show → applySettings)
            () -> game.setScreen(new SettingsScreen(game, this)),
            () -> {
                setPaused(false);
                leave();
            }
        };
        for (int i = 0; i < keys.length; i++) {
            final Runnable action = actions[i];
            com.badlogic.gdx.scenes.scene2d.ui.TextButton b = new com.badlogic.gdx.scenes.scene2d.ui.TextButton(
                game.i18n.t(keys[i]), game.skin, i == 0 ? "default" : "secondary");
            b.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    action.run();
                }
            });
            box.add(b).width(300f).height(52f).padBottom(10f).row();
        }
        Label hint = new Label(game.i18n.t("pause.hint", game.keys.keyName(Action.PAUSE)), game.skin, "small");
        box.add(hint).padTop(6f);
        pauseMenu.setFillParent(true);
        pauseMenu.setBackground(game.skin.newDrawable("white", new com.badlogic.gdx.graphics.Color(0f, 0f, 0f, 0.45f)));
        pauseMenu.add(box);
        pauseMenu.setVisible(false);
    }

    private void leave() {
        if (def.maneuver.equals("FREE_DRIVE") || maneuver.isFinished()) {
            game.setScreen(new PracticeMenuScreen(game, def.category));
            dispose();
        } else {
            showResults(maneuver.abort());
        }
    }

    private void showResults(EvaluationResult result) {
        resultShown = true;
        game.setScreen(new ResultsScreen(game, def, result));
        dispose();
    }

    private void updateDebug(VehicleState s) {
        debugLabel.setText(String.format("v %.2f m/s  rpm %.0f  par rueda %.0f Nm  slip %.0f rad/s %s\n"
                + "dir %+.1f°  pos (%.1f, %.2f, %.1f)  yaw %.0f°\nruedas: %s %s %s %s  contactos: %s\nfps %d",
            s.forwardSpeed, s.rpm, s.wheelTorque, s.clutchSlip, s.clutchLocked ? "bloqueado" : "patina",
            s.steerAngle * MathUtils.radiansToDegrees, s.position.x, s.position.y, s.position.z, s.yawDeg,
            s.wheelGround[0], s.wheelGround[1], s.wheelGround[2], s.wheelGround[3], s.contacts,
            Gdx.graphics.getFramesPerSecond()));
    }

    /** Capturas y cierre automáticos pedidos por línea de comandos. */
    private void devCaptures(float simTime) {
        if (game.dev.pauseAt >= 0f && simTime >= game.dev.pauseAt && !paused) {
            game.dev.pauseAt = -1f;
            setPaused(true);
        }
        while (nextShot < game.dev.shots.size && simTime >= game.dev.shots.get(nextShot)) {
            Screenshots.save("shot_" + nextShot);
            if (nextShot == 0 && mirrors != null) {
                mirrors.dump();
                if (cluster != null) {
                    cluster.dump();
                }
            }
            nextShot++;
        }
        if (game.dev.abortAt >= 0f && simTime >= game.dev.abortAt && !resultShown) {
            showResults(maneuver.abort());
            return;
        }
        if (game.dev.exitAt >= 0f && simTime >= game.dev.exitAt) {
            Gdx.app.exit();
        }
    }

    /** F5: relee vehicle.json y recrea el coche en el sitio donde estaba. */
    private void reloadSpec() {
        try {
            VehicleSpec fresh = VehicleSpec.loadDefault();
            Matrix4 pose = new Matrix4(vehicle.getState().transform);
            spec = fresh;
            createVehicle();
            vehicle.reset(pose);
            Gdx.input.setInputProcessor(new InputMultiplexer(hud, keyboard.getProcessor()));
            hudOverlay.show("vehicle.json recargado");
        } catch (Exception e) {
            Gdx.app.error("DrivingScreen", "No se pudo recargar vehicle.json", e);
            hudOverlay.show("Error al recargar vehicle.json: " + e.getMessage());
        }
    }

    @Override
    public void resize(int width, int height) {
        renderer.resize(width, height);
        cockpit.resize(width, height);
        chase.resize(width, height);
        hud.getViewport().update(width, height, true);
        if (touchControls != null) {
            touchControls.relayout(hud.getViewport().getWorldWidth(), hud.getViewport().getWorldHeight());
        }
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        if (audio != null) {
            audio.silence(); // en otra pantalla no se actualiza el sonido: que no se quede el motor sonando
        }
    }

    @Override
    public void dispose() {
        game.speech.stop();
        game.keys.setNameOverride(null);
        if (audio != null) {
            audio.dispose();
        }
        hud.dispose();
        if (mirrors != null) {
            mirrors.dispose();
        }
        if (cluster != null) {
            cluster.dispose();
        }
        carView.dispose();
        vehicle.dispose();
        scenario.dispose();
        debugView.dispose();
        renderer.dispose();
        physics.dispose();
    }
}
