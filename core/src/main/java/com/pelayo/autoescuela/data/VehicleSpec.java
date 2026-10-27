package com.pelayo.autoescuela.data;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;

/**
 * Parámetros del coche ({@code assets/data/vehicle.json}). POJO para libGDX
 * Json: campos públicos, sin lógica. Ejes locales del chasis: +Z adelante,
 * +Y arriba, +X izquierda (sistema diestro de bullet con forward = Z).
 * Unidades SI salvo indicación.
 */
public class VehicleSpec {

    public String name = "Coche";

    /** Masa total en orden de marcha (kg). */
    public float mass = 1165f;

    /** Caja de colisión del chasis (m): anchura, altura, longitud. */
    public float width = 1.72f;
    public float height = 1.45f;
    public float length = 3.99f;

    /** Altura del centro de masas sobre el suelo (m). */
    public float comHeight = 0.38f;
    /** Altura libre de los bajos (m): por debajo solo hay ruedas. */
    public float groundClearance = 0.16f;

    /** Distancia entre ejes y anchura de vía (m). */
    public float wheelBase = 2.575f;
    public float track = 1.47f;
    /** Desplazamiento longitudinal del centro de masas respecto al punto medio entre ejes (m, + adelante). */
    public float comOffsetZ = 0.15f;

    public float wheelRadius = 0.30f;
    public float wheelWidth = 0.185f;

    /** Posición de los ojos del conductor respecto al centro de masas (m). */
    public float eyeX = 0.37f;
    public float eyeY = 0.85f;
    public float eyeZ = 0.15f;
    /** Si es true, eyeX/Y/Z mandan sobre el nodo driver_eye del modelo (para afinar la cabina sin reexportar). */
    public boolean eyeOverride;

    public Suspension suspension = new Suspension();
    public Steering steering = new Steering();
    public Engine engine = new Engine();
    public Clutch clutch = new Clutch();
    public Gearbox gearbox = new Gearbox();
    public Brakes brakes = new Brakes();

    public static class Suspension {
        /** Rigidez de btRaycastVehicle (fuerza = stiffness · compresión · masa). */
        public float stiffness = 30f;
        public float dampingCompression = 3.3f;
        public float dampingRelaxation = 5.5f;
        /** Longitud en reposo del amortiguador (m). */
        public float restLength = 0.22f;
        public float maxTravelCm = 12f;
        public float maxForce = 12000f;
        /** Agarre del neumático (valor de bullet, adimensional). */
        public float frictionSlip = 2.5f;
        public float rollInfluence = 0.1f;
    }

    public static class Steering {
        public float maxAngleDeg = 35f;
        /** Vueltas del volante de tope a tope. */
        public float wheelTurns = 2.7f;
        /** Velocidad de giro de las ruedas con teclado (grados/s). */
        public float rateDegPerSec = 90f;
        /** Velocidad de auto-centrado al soltar (grados/s). */
        public float returnDegPerSec = 140f;
    }

    public static class Engine {
        public float idleRpm = 800f;
        public float redlineRpm = 4500f;
        public float revLimitRpm = 4800f;
        public float stallRpm = 450f;
        /** Inercia del cigüeñal + volante (kg·m²). */
        public float inertia = 0.20f;
        /** Par bruto máximo del regulador de ralentí (Nm) y su ganancia (Nm por rpm de error). */
        public float idleTorqueMax = 90f;
        public float idleKp = 0.5f;
        /** Freno motor: par resistente = c0 + c1·(rpm/1000) (Nm). */
        public float dragC0 = 12f;
        public float dragC1 = 9f;
        public float starterTorque = 45f;
        public float starterTime = 0.8f;
        /** Curva de par a plena carga: pares [rpm, Nm]. */
        public float[][] torqueCurve = {
            {0, 0}, {500, 60}, {800, 100}, {1250, 150}, {1900, 200}, {2500, 195},
            {3000, 180}, {3750, 160}, {4500, 110}, {4800, 0}
        };
    }

    public static class Clutch {
        /** Par máximo transmisible con el pedal suelto (Nm). */
        public float maxTorque = 230f;
        /** Rigidez viscosa del acoplamiento (Nm·s/rad). */
        public float stiffness = 60f;
        /** Recorrido del pedal (1 - pedal) donde empieza y termina el punto de fricción. */
        public float bitePointLow = 0.35f;
        public float bitePointHigh = 0.70f;
        /** Velocidad de pisado y de soltado con teclado (recorrido/s). */
        public float pressRate = 6f;
        public float releaseRate = 1.6f;
    }

    public static class Gearbox {
        public float[] ratios = {3.73f, 2.05f, 1.32f, 0.97f, 0.76f};
        public float reverse = 3.55f;
        public float finalDrive = 3.59f;
        public float efficiency = 0.9f;
        /** Tiempo con la caja en punto muerto durante un cambio (s). */
        public float shiftTime = 0.25f;
        /** Índices de las ruedas motrices (0 FL, 1 FR, 2 RL, 3 RR). */
        public int[] drivenWheels = {0, 1};
    }

    public static class Brakes {
        /** Valor de freno de bullet por rueda a pedal fondo (reparto delantero). */
        public float maxForce = 90f;
        public float frontBias = 0.65f;
        /** Freno de mano: valor de freno de bullet en cada rueda trasera. */
        public float handbrakeForce = 60f;
        /** Coeficiente de resistencia a la rodadura. */
        public float rollingResistance = 0.012f;
        /** Cd·A (m²) para la resistencia aerodinámica. */
        public float dragArea = 0.68f;
    }

    public static VehicleSpec load(FileHandle file) {
        return new Json().fromJson(VehicleSpec.class, file);
    }

    public static VehicleSpec loadDefault() {
        return load(Gdx.files.internal("data/vehicle.json"));
    }
}
