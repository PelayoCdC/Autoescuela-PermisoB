package com.pelayo.autoescuela.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.pelayo.autoescuela.sim.BodyTag;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import java.util.ArrayList;
import java.util.List;

/**
 * Sonido del coche, derivado solo del {@link VehicleState}.
 *
 * <p><b>Motor</b>: cuatro bucles de referencia (800, 1.500, 2.500 y 4.000 rpm),
 * cada uno en versión "con carga" y "sin carga". El tono de cada bucle sigue
 * las rpm (rpm / referencia, dentro del 0,5–2 que admite libGDX) y se mezclan
 * con fundido cruzado de potencia constante en escala logarítmica entre las
 * dos referencias vecinas, así ningún bucle suena estirado más de ~±35 %. La
 * carga estimada (pedal con el mismo mapa que la simulación, más el gasóleo
 * que mete el regulador de ralentí; cero en retención y en el corte de
 * inyección) reparte entre la versión con y sin carga y sube el volumen; con
 * gas y rpm entra además el silbido del turbo, con su retardo.
 *
 * <p><b>Entorno</b>: rodadura y viento que crecen con la velocidad, chirrido
 * leve de neumáticos al frenar fuerte, con el freno de mano en marcha o en
 * curvas rápidas (aceleración lateral estimada), y el silbido de la marcha
 * atrás.
 *
 * <p><b>Efectos</b>: arranque (se corta con fundido cuando el motor prende),
 * calado, parada con la llave, rascada, palanca de cambios, freno de mano
 * (carraca al subirlo, golpe al bajarlo), relé del intermitente (tic-tac),
 * bordillos (rueda que lo sube o lo baja) y golpes del chasis (cono, valla,
 * coche aparcado).
 *
 * <p>Uso: {@link #update} cada frame con el delta y, si se quiere, también en
 * los pasos con eventos con delta 0 (los eventos y contactos de un mismo paso
 * se procesan una sola vez, se llame cuantas veces se llame).
 *
 * <p>Si existe {@code assets/sounds/<nombre>.ogg|wav|mp3} se usa en lugar del
 * sintetizado (nombres en {@link ProceduralSounds} y docs/ASSETS.md).
 */
public class EngineAudio implements Disposable {

    private static final float IDLE_RPM = 800f;
    private static final float REV_CUT_RPM = 4700f;
    /** Batalla del Clio III (m), para estimar la aceleración lateral. */
    private static final float WHEELBASE = 2.575f;
    /** Exponente del mapa de pedal (el mismo que {@code Engine}). */
    private static final float PEDAL_MAP_EXPONENT = 0.6f;

    // Ganancias de mezcla sobre el volumen maestro. Los bucles vienen con
    // valor eficaz ~0,15–0,17 y los efectos con pico 0,5–0,8, de modo que
    // ni con todo sonando a la vez se acerca a saturar.
    private static final float G_ENGINE = 0.7f;
    private static final float G_TURBO = 0.12f;
    private static final float G_ROAD = 0.3f;
    private static final float G_WIND = 0.25f;
    private static final float G_SKID = 0.22f;
    private static final float G_WHINE = 0.12f;
    private static final float G_STARTER = 0.65f;
    private static final float G_STALL = 0.6f;
    private static final float G_ENGINE_OFF = 0.45f;
    private static final float G_GRIND = 0.5f;
    private static final float G_GEAR = 0.32f;
    private static final float G_HANDBRAKE = 0.4f;
    private static final float G_INDICATOR = 0.3f;
    private static final float G_CURB = 0.55f;
    private static final float G_IMPACT = 0.7f;
    private static final float G_CONE = 0.5f;

    /** Relé del intermitente: ~85 destellos por minuto. */
    private static final float INDICATOR_ON_TIME = 0.36f;
    private static final float INDICATOR_OFF_TIME = 0.34f;
    private static final float STARTER_FADE = 0.08f;
    private static final float STARTER_LENGTH = 1.5f;
    private static final float HIT_COOLDOWN = 0.6f;
    private static final float WHEEL_COOLDOWN = 0.2f;

    /** Bucles de motor a unas rpm de referencia; sin {@code off} si solo hay una versión. */
    private static final class EngineLayer {
        final float refRpm;
        final LoopVoice on;
        final LoopVoice off;

        EngineLayer(float refRpm, LoopVoice on, LoopVoice off) {
            this.refRpm = refRpm;
            this.on = on;
            this.off = off;
        }
    }

    private final ObjectMap<String, Sound> loaded = new ObjectMap<>();
    private final EngineLayer[] layers;
    private final float[] weights;
    private final LoopVoice turbo;
    private final LoopVoice road;
    private final LoopVoice wind;
    private final LoopVoice skid;
    private final LoopVoice whine;
    private final Sound starter;
    private final Sound stall;
    private final Sound engineOff;
    private final Sound grind;
    private final Sound gearShift;
    private final Sound handbrakeOn;
    private final Sound handbrakeOff;
    private final Sound tick;
    private final Sound tock;
    private final Sound curb;
    private final Sound impact;
    private final Sound cone;

    private float masterVolume;
    private float smoothedRpm;
    private float load;
    private float boost;

    private boolean initialized;
    private float lastStepTime = Float.NaN;
    private boolean prevHandbrake;
    private boolean prevStarting;
    private boolean indicatorActive;
    private boolean lampOn;
    private float indicatorTimer;
    private final boolean[] prevOnCurb = new boolean[4];
    private final float[] wheelCooldown = new float[4];
    private final float[] hitCooldown = new float[BodyTag.values().length];

    private long starterId = -1;
    private float starterVolume;
    private float starterAge;
    private boolean starterFading;

    public EngineAudio(float masterVolume) {
        this.masterVolume = masterVolume;

        // Qué hay que sintetizar: lo que no tenga muestra grabada
        boolean legacy = useLegacyEngine();
        List<String[]> chains = new ArrayList<>();
        if (!legacy) {
            for (int rpm : ProceduralSounds.ENGINE_REFS) {
                chains.add(engineChain(rpm, true));
                chains.add(engineChain(rpm, false));
            }
        }
        String[][] fixed = {
            {ProceduralSounds.TURBO}, {ProceduralSounds.ROAD}, {ProceduralSounds.WIND}, {ProceduralSounds.SKID},
            {ProceduralSounds.GEAR_WHINE}, {ProceduralSounds.STARTER}, {ProceduralSounds.STALL},
            {ProceduralSounds.ENGINE_OFF}, {ProceduralSounds.GRIND}, {ProceduralSounds.GEAR_SHIFT},
            {ProceduralSounds.HANDBRAKE_ON}, {ProceduralSounds.HANDBRAKE_OFF}, indicatorChain(true),
            indicatorChain(false), {ProceduralSounds.CURB}, {ProceduralSounds.IMPACT}, {ProceduralSounds.CONE},
        };
        for (String[] c : fixed) {
            chains.add(c);
        }
        List<String> toSynth = new ArrayList<>();
        for (String[] c : chains) {
            if (external(c) == null) {
                toSynth.add(c[0]);
            }
        }
        ProceduralSounds.ensure(toSynth);

        if (legacy) {
            // Muestras antiguas: un bucle grave (~1000 rpm) y uno agudo (~3000 rpm)
            layers = new EngineLayer[] {
                new EngineLayer(1000f, engineVoice(sound("engine_low")), null),
                new EngineLayer(3000f, engineVoice(sound("engine_high")), null),
            };
        } else {
            int[] refs = ProceduralSounds.ENGINE_REFS;
            layers = new EngineLayer[refs.length];
            for (int i = 0; i < refs.length; i++) {
                Sound on = sound(engineChain(refs[i], true));
                Sound off = sound(engineChain(refs[i], false));
                layers[i] = new EngineLayer(refs[i], engineVoice(on), on == off ? null : engineVoice(off));
            }
        }
        weights = new float[layers.length];
        turbo = new LoopVoice(sound(ProceduralSounds.TURBO), 0.05f, 0.1f);
        road = new LoopVoice(sound(ProceduralSounds.ROAD), 0.15f, 0.25f);
        wind = new LoopVoice(sound(ProceduralSounds.WIND), 0.2f, 0.3f);
        skid = new LoopVoice(sound(ProceduralSounds.SKID), 0.06f, 0.15f);
        whine = new LoopVoice(sound(ProceduralSounds.GEAR_WHINE), 0.1f, 0.2f);
        starter = sound(ProceduralSounds.STARTER);
        stall = sound(ProceduralSounds.STALL);
        engineOff = sound(ProceduralSounds.ENGINE_OFF);
        grind = sound(ProceduralSounds.GRIND);
        gearShift = sound(ProceduralSounds.GEAR_SHIFT);
        handbrakeOn = sound(ProceduralSounds.HANDBRAKE_ON);
        handbrakeOff = sound(ProceduralSounds.HANDBRAKE_OFF);
        tick = sound(indicatorChain(true));
        tock = sound(indicatorChain(false));
        curb = sound(ProceduralSounds.CURB);
        impact = sound(ProceduralSounds.IMPACT);
        cone = sound(ProceduralSounds.CONE);
    }

    // ------------------------------------------------------------- carga

    /** engine_&lt;rpm&gt;_on|off y, si no, engine_&lt;rpm&gt; (una sola muestra para ambas cargas). */
    private static String[] engineChain(int rpm, boolean onLoad) {
        return new String[] {ProceduralSounds.engineName(rpm, onLoad), "engine_" + rpm};
    }

    /** indicator_on|off y, si no, la muestra antigua indicator (la misma para tic y tac). */
    private static String[] indicatorChain(boolean on) {
        return new String[] {on ? ProceduralSounds.INDICATOR_ON : ProceduralSounds.INDICATOR_OFF, "indicator"};
    }

    /** Con engine_low y engine_high grabados (formato antiguo) y ningún bucle nuevo, se usan esos dos. */
    private static boolean useLegacyEngine() {
        if (external("engine_low") == null || external("engine_high") == null) {
            return false;
        }
        for (int rpm : ProceduralSounds.ENGINE_REFS) {
            if (external(engineChain(rpm, true)) != null || external(engineChain(rpm, false)) != null) {
                return false;
            }
        }
        return true;
    }

    /** Primera muestra grabada que exista de la cadena de nombres, o null. */
    private static FileHandle external(String... names) {
        for (String name : names) {
            for (String ext : new String[] {".ogg", ".wav", ".mp3"}) {
                FileHandle f = Gdx.files.internal("sounds/" + name + ext);
                if (f.exists()) {
                    return f;
                }
            }
        }
        return null;
    }

    /** Carga la muestra grabada de la cadena o, si no hay, la sintetizada del primer nombre. */
    private Sound sound(String... names) {
        FileHandle f = external(names);
        if (f == null) {
            f = ProceduralSounds.get(names[0]);
        }
        String key = f.path();
        Sound s = loaded.get(key);
        if (s == null) {
            s = Gdx.audio.newSound(f);
            loaded.put(key, s);
        }
        return s;
    }

    private static LoopVoice engineVoice(Sound s) {
        return new LoopVoice(s, 0.03f, 0.06f);
    }

    // ------------------------------------------------------------- API

    public void setMasterVolume(float masterVolume) {
        this.masterVolume = masterVolume;
    }

    /** Llamar una vez por frame con el estado tras la simulación (y opcionalmente con delta 0 en los pasos con eventos). */
    public void update(VehicleState state, float delta) {
        if (state.time != lastStepTime) {
            lastStepTime = state.time;
            handleEvents(state);
            handleContacts(state);
        }
        handleEdges(state, delta);
        if (delta > 0f) {
            mix(state, delta);
            updateStarter(delta);
            for (int i = 0; i < wheelCooldown.length; i++) {
                wheelCooldown[i] -= delta;
            }
            for (int i = 0; i < hitCooldown.length; i++) {
                hitCooldown[i] -= delta;
            }
        }
        initialized = true;
    }

    /** Calla todo (al salir a otra pantalla con la práctica en pausa); vuelve a sonar con el siguiente update. */
    public void silence() {
        stopAll();
    }

    /** Arranque pedido con la llave (si ya suena por el estado del motor, no se duplica). */
    public void playStarter() {
        startStarter();
    }

    // ------------------------------------------------------------- eventos

    private void handleEvents(VehicleState state) {
        for (int i = 0; i < state.events.size; i++) {
            switch (state.events.get(i)) {
                case STALL:
                    play(stall, G_STALL, 0.03f);
                    break;
                case ENGINE_OFF:
                    play(engineOff, G_ENGINE_OFF, 0.03f);
                    break;
                case ENGINE_START:
                    fadeStarter();
                    break;
                case GRIND:
                    play(grind, G_GRIND, 0.08f);
                    break;
                case REVERSE_REFUSED:
                    // La marcha atrás no tiene sincronizador: rasca más corto y agudo
                    playAt(grind, G_GRIND * 0.6f, 1.15f);
                    break;
                case GEAR_CHANGED:
                    play(gearShift, G_GEAR, 0.05f);
                    break;
                default:
                    break;
            }
        }
    }

    /** Golpes del chasis (solo cosas contra las que se choca; el suelo y la calzada no). */
    private void handleContacts(VehicleState state) {
        float v = state.speedKmh;
        if (v < 0.7f) {
            return; // contacto en reposo, no un golpe
        }
        float strength = 0.3f + 0.7f * MathUtils.clamp(v / 25f, 0f, 1f);
        for (int i = 0; i < state.contacts.size; i++) {
            BodyTag tag = state.contacts.get(i);
            Sound s;
            float gain;
            switch (tag) {
                case CONE:
                    s = cone;
                    gain = G_CONE;
                    break;
                case BARRIER:
                case PARKED_CAR:
                case TRAFFIC_CAR:
                case PEDESTRIAN:
                    s = impact;
                    gain = G_IMPACT;
                    break;
                case CURB:
                    s = curb;
                    gain = G_CURB * 0.8f;
                    break;
                default:
                    continue;
            }
            if (hitCooldown[tag.ordinal()] <= 0f) {
                hitCooldown[tag.ordinal()] = HIT_COOLDOWN;
                play(s, gain * strength, 0.06f);
            }
        }
    }

    /** Cambios de estado: arranque, freno de mano, intermitente y ruedas en el bordillo. */
    private void handleEdges(VehicleState state, float delta) {
        if (state.engineStarting && !prevStarting) {
            startStarter();
        } else if (!state.engineStarting && prevStarting) {
            fadeStarter();
        }
        prevStarting = state.engineStarting;

        if (initialized && state.handbrake != prevHandbrake) {
            if (state.handbrake) {
                play(handbrakeOn, G_HANDBRAKE, 0.04f);
            } else {
                play(handbrakeOff, G_HANDBRAKE, 0.04f);
            }
        }
        prevHandbrake = state.handbrake;

        // Relé: tic al encender la luz, tac al apagarla
        boolean ind = state.indicatorLeft || state.indicatorRight;
        if (ind && !indicatorActive) {
            indicatorActive = true;
            lampOn = true;
            indicatorTimer = 0f;
            play(tick, G_INDICATOR, 0f);
        } else if (!ind && indicatorActive) {
            indicatorActive = false;
            if (lampOn) {
                play(tock, G_INDICATOR * 0.9f, 0f);
            }
            lampOn = false;
        } else if (ind) {
            indicatorTimer += delta;
            float limit = lampOn ? INDICATOR_ON_TIME : INDICATOR_OFF_TIME;
            if (indicatorTimer >= limit) {
                indicatorTimer = Math.min(indicatorTimer - limit, limit);
                lampOn = !lampOn;
                if (lampOn) {
                    play(tick, G_INDICATOR, 0f);
                } else {
                    play(tock, G_INDICATOR * 0.9f, 0f);
                }
            }
        }

        // Rueda que sube o baja un bordillo
        float v = state.speedKmh;
        for (int i = 0; i < 4; i++) {
            if (!state.wheelInContact[i]) {
                continue;
            }
            boolean onCurb = state.wheelGround[i] == BodyTag.CURB;
            if (initialized && onCurb != prevOnCurb[i] && v > 0.8f && wheelCooldown[i] <= 0f) {
                wheelCooldown[i] = WHEEL_COOLDOWN;
                float strength = 0.35f + 0.65f * MathUtils.clamp(v / 20f, 0f, 1f);
                play(curb, G_CURB * strength * (onCurb ? 1f : 0.7f), 0.08f);
            }
            prevOnCurb[i] = onCurb;
        }
    }

    // ------------------------------------------------------------- mezcla

    private void mix(VehicleState s, float dt) {
        float m = masterVolume;
        boolean running = s.engineRunning;
        smoothedRpm += (s.rpm - smoothedRpm) * Math.min(1f, dt * 25f);
        float rpm = Math.max(1f, smoothedRpm);
        float rpmNorm = MathUtils.clamp((rpm - IDLE_RPM) / 3200f, 0f, 1f);
        float targetLoad = engineLoad(s);
        load = follow(load, targetLoad, targetLoad > load ? 0.05f : 0.12f, dt);

        // Motor: fundido por rpm entre referencias y por carga entre versiones
        float level = running ? m * G_ENGINE * (0.55f + 0.45f * rpmNorm) * (0.72f + 0.28f * load) : 0f;
        layerWeights(rpm);
        float onW = MathUtils.sin(load * MathUtils.HALF_PI);
        float offW = MathUtils.cos(load * MathUtils.HALF_PI);
        for (int i = 0; i < layers.length; i++) {
            EngineLayer layer = layers[i];
            float pitch = rpm / layer.refRpm;
            float w = level * weights[i];
            if (layer.off == null) {
                layer.on.set(w, pitch);
            } else {
                layer.on.set(w * onW, pitch);
                layer.off.set(w * offW, pitch);
                layer.off.update(dt);
            }
            layer.on.update(dt);
        }

        // Turbo: sopla con gas por encima de ~1.300 rpm, con retardo al cargar y al soltar
        float pedal = pedal(s.throttle);
        float boostTarget = running ? MathUtils.clamp((rpm - 1300f) / 1700f, 0f, 1f) * pedal : 0f;
        boost = follow(boost, boostTarget, boostTarget > boost ? 0.7f : 0.35f, dt);
        turbo.set(m * G_TURBO * boost * (float) Math.sqrt(boost), 0.55f + 0.75f * boost + 0.15f * rpmNorm);
        turbo.update(dt);

        // Rodadura y viento
        float v = s.speedKmh;
        boolean grounded = s.wheelInContact[0] || s.wheelInContact[1] || s.wheelInContact[2] || s.wheelInContact[3];
        float sv = Math.min(v / 100f, 1.4f);
        road.set(grounded ? m * G_ROAD * (float) Math.pow(sv, 0.85) : 0f, 0.7f + 0.45f * Math.min(sv, 1.2f));
        road.update(dt);
        float wv = Math.min(v / 130f, 1.3f);
        wind.set(m * G_WIND * wv * wv, 0.75f + 0.5f * Math.min(v / 150f, 1f));
        wind.update(dt);

        // Chirrido: frenazo, freno de mano en marcha o curva rápida
        float vms = Math.abs(s.forwardSpeed);
        float brakeSkid = MathUtils.clamp((s.brake - 0.6f) / 0.4f, 0f, 1f)
            * MathUtils.clamp((v - 20f) / 40f, 0f, 1f) * 0.8f;
        float handbrakeSkid = s.handbrake ? MathUtils.clamp((v - 8f) / 30f, 0f, 1f) : 0f;
        float lateral = vms * vms * (float) Math.tan(Math.min(Math.abs(s.steerAngle), 1.2f)) / WHEELBASE;
        float lateralSkid = MathUtils.clamp((lateral - 5.5f) / 3f, 0f, 1f);
        float skidAmount = grounded ? Math.max(brakeSkid, Math.max(handbrakeSkid, lateralSkid)) : 0f;
        skid.set(m * G_SKID * skidAmount, 0.85f + 0.25f * skidAmount + 0.1f * Math.min(v / 100f, 1f));
        skid.update(dt);

        // Silbido de engranajes: sobre todo la marcha atrás (dientes rectos)
        float whineAmount = 0f;
        if (running && s.gear != 0 && s.clutch < 0.6f && v > 0.5f) {
            whineAmount = s.gear < 0
                ? MathUtils.clamp(v / 10f, 0f, 1f) * (0.6f + 0.4f * load)
                : s.gear == 1 ? 0.25f * MathUtils.clamp(v / 15f, 0f, 1f) : 0f;
        }
        whine.set(m * G_WHINE * whineAmount, rpm / 2000f);
        whine.update(dt);
    }

    /**
     * Carga estimada del motor 0..1: el pedal (con el mapa progresivo de la
     * simulación) o el gasóleo del regulador de ralentí, que mete más cuanto
     * más caen las vueltas; 0 en retención (sin gas por encima del ralentí) y
     * en el corte del limitador.
     */
    private static float engineLoad(VehicleState s) {
        if (!s.engineRunning) {
            return 0f;
        }
        float rpm = s.rpm;
        float idleFuel = (0.25f + 0.6f * MathUtils.clamp((IDLE_RPM + 50f - rpm) / 300f, 0f, 1f))
            * MathUtils.clamp((1150f - rpm) / 250f, 0f, 1f);
        float l = Math.max(pedal(s.throttle), idleFuel);
        if (rpm > REV_CUT_RPM) {
            l *= 0.3f;
        }
        return l;
    }

    private static float pedal(float throttle) {
        return (float) Math.pow(MathUtils.clamp(throttle, 0f, 1f), PEDAL_MAP_EXPONENT);
    }

    /** Pesos de potencia constante entre las dos referencias vecinas, en escala logarítmica de rpm. */
    private void layerWeights(float rpm) {
        for (int i = 0; i < weights.length; i++) {
            weights[i] = 0f;
        }
        int last = layers.length - 1;
        if (rpm <= layers[0].refRpm) {
            weights[0] = 1f;
            return;
        }
        if (rpm >= layers[last].refRpm) {
            weights[last] = 1f;
            return;
        }
        for (int i = 0; i < last; i++) {
            float a = layers[i].refRpm;
            float b = layers[i + 1].refRpm;
            if (rpm < b) {
                float t = (float) (Math.log(rpm / a) / Math.log(b / a));
                weights[i] = MathUtils.cos(t * MathUtils.HALF_PI);
                weights[i + 1] = MathUtils.sin(t * MathUtils.HALF_PI);
                return;
            }
        }
    }

    private static float follow(float value, float target, float tau, float dt) {
        return value + (target - value) * (1f - (float) Math.exp(-dt / tau));
    }

    // ------------------------------------------------------------- efectos

    private void play(Sound s, float gain, float pitchJitter) {
        playAt(s, gain, 1f + MathUtils.random(-pitchJitter, pitchJitter));
    }

    private void playAt(Sound s, float gain, float pitch) {
        float v = Math.min(1f, masterVolume * gain);
        if (v > 0.001f) {
            s.play(v, pitch, 0f);
        }
    }

    private void startStarter() {
        if (starterId >= 0 && !starterFading) {
            return;
        }
        if (starterId >= 0) {
            starter.stop(starterId);
        }
        starterVolume = Math.min(1f, masterVolume * G_STARTER);
        starterId = starter.play(starterVolume);
        starterAge = 0f;
        starterFading = false;
    }

    /** El motor ha prendido o se ha dejado de arrancar: el motor de arranque se va en unas centésimas. */
    private void fadeStarter() {
        if (starterId >= 0) {
            starterFading = true;
        }
    }

    private void updateStarter(float dt) {
        if (starterId < 0) {
            return;
        }
        starterAge += dt;
        if (starterFading) {
            starterVolume -= dt * Math.max(0.05f, masterVolume * G_STARTER) / STARTER_FADE;
            if (starterVolume <= 0f) {
                starter.stop(starterId);
                starterId = -1;
            } else {
                starter.setVolume(starterId, starterVolume);
            }
        } else if (starterAge > STARTER_LENGTH + 0.1f) {
            starterId = -1; // ya ha terminado solo
        }
    }

    private void stopAll() {
        for (EngineLayer layer : layers) {
            layer.on.stop();
            if (layer.off != null) {
                layer.off.stop();
            }
        }
        turbo.stop();
        road.stop();
        wind.stop();
        skid.stop();
        whine.stop();
        if (starterId >= 0) {
            starter.stop(starterId);
            starterId = -1;
        }
    }

    @Override
    public void dispose() {
        stopAll();
        for (Sound s : loaded.values()) {
            s.dispose();
        }
        loaded.clear();
    }
}
