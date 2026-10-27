package com.pelayo.autoescuela.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;

/**
 * Escenario de práctica ({@code assets/data/scenarios/<id>.json}): módulos
 * físicos, marcas viales, zonas con nombre que usan los evaluadores, pose
 * inicial y parámetros de la maniobra. POJO para libGDX Json.
 *
 * Coordenadas del mundo: X hacia la izquierda del sentido +Z, Y arriba.
 * Un yaw de 0° mira hacia +Z y crece hacia la izquierda (+X).
 */
public class ScenarioDef {

    public String id;
    /** Nombre y descripción que ve el usuario (en español). */
    public String name;
    public String description;
    /** Maniobra a evaluar: PARALLEL_PARKING, FREE_DRIVE... (ver ManeuverRegistry). */
    public String maneuver = "FREE_DRIVE";
    /** Orden en el menú de prácticas. */
    public int order = 100;
    /** Sección del menú de prácticas: lesson (aprende a conducir), exercise o exam (maniobras DGT). */
    public String category = "exercise";
    /** Pasos de la lección guiada (solo con maniobra TUTORIAL). */
    public LessonDef lesson;

    public Pose startPose = new Pose();
    public Array<ModuleDef> modules = new Array<>();
    public Array<CurbDef> curbs = new Array<>();
    public Array<MarkingDef> markings = new Array<>();
    public Array<ZoneDef> zones = new Array<>();
    public Params params = new Params();
    /** Indicaciones del examinador para el examen por recorrido (maniobra ROUTE). */
    public Array<RouteStepDef> route = new Array<>();

    /** Un tramo del recorrido: qué dice el examinador y dónde termina. */
    public static class RouteStepDef {
        public String say;
        /** Al entrar en esta zona se pasa al siguiente tramo. */
        public String zone;
        public float speedLimit = 50f;
        /** Zona de un STOP: hay que parar del todo dentro antes de salir por delante. */
        public String stopZone;
        /** "left" o "right": intermitente puesto en algún momento dentro de signalZone. */
        public String signal;
        public String signalZone;
        /** Zonas que son salirse del itinerario. */
        public String[] wrong;
        /** Último tramo: parar dentro de la zona para terminar. */
        public boolean park;
    }

    /** Coches y peatones que se mueven por el escenario. */
    public Array<TrafficDef> traffic = new Array<>();

    /**
     * Coche o peatón con su camino: una polilínea {@code path} o un arco {@code arc} (barrido negativo =
     * sentido horario visto desde arriba). Con {@code count} > 1 salen varios iguales separados {@code gap}
     * metros a lo largo del camino.
     */
    public static class TrafficDef {
        /** "car" o "pedestrian". */
        public String type = "car";
        public float[][] path;
        public ArcDef arc;
        public float speedKmh = 30f;
        /** Al llegar al final vuelve al principio (los coches que circulan sin parar). */
        public boolean loop = true;
        /** Al llegar al final da la vuelta y regresa (peatones que cruzan una y otra vez). */
        public boolean pingPong;
        /** Pausa en cada extremo (s), para pingPong. */
        public float pause = 2f;
        /** Espera antes de arrancar (s). */
        public float delay;
        /** No arranca hasta que el jugador esté a menos de esta distancia (m) de su inicio; 0 = desde el principio. */
        public float triggerDistance;
        public int count = 1;
        public float gap = 30f;
        /** Desplazamiento inicial a lo largo del camino (m). */
        public float offset;
        /** Color (hex RRGGBB). */
        public String color;

        public float[][] resolvedPath() {
            return path != null ? path : arc.points();
        }
    }

    public static class Pose {
        public float x, y = 0.9f, z;
        public float yawDeg;
        /** Inclinación inicial (grados, negativa = morro arriba), para empezar en una cuesta. */
        public float pitchDeg;
    }

    /**
     * Arco en el plano XZ para generar puntos de bordillos, marcas y calzadas
     * curvas. El ángulo se mide como el rumbo: 0° en +Z, creciendo hacia +X.
     * Punto(θ) = (cx + r·sin θ, cz + r·cos θ).
     */
    public static class ArcDef {
        public float cx, cz;
        public float radius;
        public float startDeg;
        public float sweepDeg;
        public int steps = 16;

        public float[][] points() {
            float[][] pts = new float[steps + 1][2];
            for (int i = 0; i <= steps; i++) {
                float a = startDeg + sweepDeg * i / steps;
                pts[i][0] = cx + radius * (float) Math.sin(Math.toRadians(a));
                pts[i][1] = cz + radius * (float) Math.cos(Math.toRadians(a));
            }
            return pts;
        }
    }

    /**
     * Pieza del escenario. {@code type}: ground, road, sidewalk, slope, cone,
     * parked_car, wall, block, road_arc, sidewalk_arc, island, sign.
     * {@code x,z} es el centro (en slope, el centro del borde bajo);
     * {@code length} va a lo largo del eje Z local y {@code width} a lo largo
     * de X. Los tipos *_arc usan {@code arc} (radio al eje de la pieza).
     */
    public static class ModuleDef {
        public String type;
        public float x, y, z;
        public float yawDeg;
        public float length = 10f;
        public float width = 7f;
        public float height = 1f;
        /** Altura del bordillo (sidewalk) en metros. */
        public float curbHeight = 0.14f;
        /** Pendiente (slope) en tanto por ciento. */
        public float gradePct = 10f;
        /** Color hexadecimal opcional (RRGGBB). */
        public String color;
        /** Nombre opcional para referirse al módulo (p.ej. un cono concreto). */
        public String name;
        /** Arco para road_arc / sidewalk_arc. */
        public ArcDef arc;
        /** Radio para island. */
        public float radius = 6f;
        /** Tipo de señal para sign: stop, yield. */
        public String sign;
    }

    /** Borde de bordillo: polilínea de puntos [x, z] a nivel del suelo, o un arco. */
    public static class CurbDef {
        public String name;
        public float[][] points;
        public ArcDef arc;

        public float[][] resolvedPoints() {
            return arc != null ? arc.points() : points;
        }
    }

    /**
     * Marca vial. {@code kind}: LANE (polilínea con anchura), BAY (plaza
     * rectangular: center, size [ancho X, largo Z], yawDeg), STOP (línea de
     * detención: dos puntos), GUIDE (eje de referencia invisible para la
     * evaluación). Las polilíneas pueden darse como {@code arc}.
     */
    public static class MarkingDef {
        public String kind = "LANE";
        public String name;
        public float[][] points;
        public ArcDef arc;
        public float width = 0.12f;
        public float[] center;
        public float[] size;
        public float yawDeg;

        public float[][] resolvedPoints() {
            return arc != null ? arc.points() : points;
        }
    }

    /** Zona rectangular con nombre (center [x, z], size [ancho X, largo Z], yawDeg). */
    public static class ZoneDef {
        public String name;
        public float[] center;
        public float[] size;
        public float yawDeg;
    }

    /**
     * Lección guiada: una secuencia de pasos. Cada paso explica algo y pide
     * uno o varios objetivos que se comprueban con el estado del coche; el
     * paso se supera cuando todos se cumplen a la vez durante {@code hold}
     * segundos (o al pulsar Continuar si es un paso de lectura, {@code ack}).
     * En los textos, {NOMBRE_DE_ACCION} se sustituye por su tecla.
     */
    public static class LessonDef {
        public Array<StepDef> steps = new Array<>();
        /** Lo aprendido, para la pantalla final. */
        public String[] summary;
    }

    public static class StepDef {
        public String title;
        public String text;
        /** Pista que aparece si el paso se alarga más de {@code hintAfter} segundos. */
        public String hint;
        public float hintAfter = 15f;
        /** Paso de lectura: se supera con Continuar. */
        public boolean ack;
        public Array<GoalDef> goals = new Array<>();
        /** Tiempo que deben cumplirse todos los objetivos a la vez (s). */
        public float hold = 0.4f;
        /** Mandos a resaltar en el HUD: clutch, brake, throttle, tacho, speed, gear, handbrake, indicators. */
        public String[] highlight;
    }

    /**
     * Objetivo de un paso. {@code type}: clutch, brake, throttle, steer (valor
     * entre min y max), rpm, speed (km/h), gear (value: -1 R, 0 N, 1..5),
     * handbrake / engine (value 1 o 0), stopped, moving, distance (metros
     * recorridos en el paso; negativos = marcha atrás), zone (nombre),
     * indicator (value -1, 0, 1), look (mirar atrás) y event (nombre de
     * VehicleEvent ocurrido durante el paso).
     */
    public static class GoalDef {
        public String type;
        public float min = -1e9f;
        public float max = 1e9f;
        public int value;
        public float meters;
        public String zone;
        public String event;
        /** Texto propio del objetivo (si no, se genera según el tipo). */
        public String label;
    }

    public static class Params {
        public int maxMovements = 3;
        /** Distancia máxima de las ruedas al bordillo al aparcar (m). */
        public float maxCurbDistance = 0.30f;
        public float timeLimitSec = 240f;
        /** Empezar con el motor apagado (ejercicio de arranque). */
        public boolean engineOff = false;
        /** Tolerancia de alineación respecto a la plaza (grados). */
        public float maxAlignmentDeg = 5f;
        /** Distancia mínima de parada antes de la curva (maniobra G), m. */
        public float stopDistance = 10f;
        /** Desviación lateral máxima respecto al eje guía (maniobra G), m. */
        public float maxCentering = 0.6f;
        /** Desviación típica máxima de la velocidad para considerarla uniforme (G), m/s. */
        public float maxSpeedStdDev = 0.4f;
        /** Retroceso en el arranque en pendiente: leve y deficiente (m). */
        public float rollbackMinor = 0.3f;
        public float rollbackDeficient = 1.0f;
        /** Descentrado máximo dentro de una plaza en batería (m). */
        public float maxBayOffset = 0.25f;
    }

    public static ScenarioDef load(FileHandle file) {
        return new Json().fromJson(ScenarioDef.class, file);
    }

    public static ScenarioDef load(String id) {
        return load(Gdx.files.internal("data/scenarios/" + id + ".json"));
    }

    /** Ids listados en {@code data/scenarios/index.json}, en orden. */
    public static Array<String> listIds() {
        return new Json().fromJson(Array.class, String.class, Gdx.files.internal("data/scenarios/index.json"));
    }
}
