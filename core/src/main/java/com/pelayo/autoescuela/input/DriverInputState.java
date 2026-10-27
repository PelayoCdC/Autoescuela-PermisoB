package com.pelayo.autoescuela.input;

/**
 * Mandos del conductor en un paso de simulación. Los ejes van en 0..1 (la
 * dirección en -1..1, positivo = izquierda); los botones de pulso valen true
 * solo en el paso en que se activan.
 */
public class DriverInputState {

    /** Ningún cambio de marcha pedido. */
    public static final int GEAR_NONE = -2;

    public float steer;
    public float throttle;
    public float brake;
    public float clutch;
    public boolean handbrakeToggle;

    public boolean gearUp;
    public boolean gearDown;
    /** Marcha concreta pedida: -1 R, 0 N, 1..5, o {@link #GEAR_NONE}. */
    public int gearSelect = GEAR_NONE;

    public boolean ignition;
    public boolean indicatorLeft;
    public boolean indicatorRight;

    /** Mirar: desplazamiento del cuello en -1..1 (yaw + izquierda, pitch + arriba). */
    public float lookYaw;
    public float lookPitch;
    public boolean lookReset;

    public boolean cameraToggle;
    public boolean resetVehicle;
    /** Continuar en los pasos de lectura de una lección. */
    public boolean lessonContinue;

    public void clearPulses() {
        handbrakeToggle = false;
        gearUp = false;
        gearDown = false;
        gearSelect = GEAR_NONE;
        ignition = false;
        indicatorLeft = false;
        indicatorRight = false;
        lookReset = false;
        cameraToggle = false;
        resetVehicle = false;
        lessonContinue = false;
    }
}
