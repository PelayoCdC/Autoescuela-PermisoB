package com.pelayo.autoescuela.sim.vehicle;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.pelayo.autoescuela.sim.BodyTag;

/**
 * Instantánea del coche tras cada paso de simulación. La leen el render, el
 * HUD, el audio y los evaluadores de maniobras; nadie la modifica salvo
 * {@link Vehicle}.
 */
public class VehicleState {

    public final Matrix4 transform = new Matrix4();
    public final Vector3 position = new Vector3();
    public final Vector3 forward = new Vector3();
    /** Rumbo en grados (0 = +Z, crece hacia la izquierda). */
    public float yawDeg;

    /** Velocidad longitudinal (m/s, + adelante) y su módulo en km/h. */
    public float forwardSpeed;
    public float speedKmh;

    /** Mandos efectivos tras el suavizado (0..1; dirección -1..1, + izquierda). */
    public float steer;
    public float throttle;
    public float brake;
    public float clutch;
    public boolean handbrake;
    /** Ángulo real de las ruedas delanteras (rad). */
    public float steerAngle;

    /** Marcha engranada: -1 R, 0 N, 1..5. */
    public int gear;
    public float rpm;
    public boolean engineRunning;
    public boolean engineStarting;
    /** Deslizamiento del embrague (rad/s, motor − caja). */
    public float clutchSlip;
    public boolean clutchLocked;
    /** Par total en las ruedas motrices (Nm). */
    public float wheelTorque;
    public boolean indicatorLeft;
    public boolean indicatorRight;
    /** Hacia dónde mira el conductor (-1..1, + izquierda; 1 = por encima del hombro). */
    public float lookYaw;

    public final BodyTag[] wheelGround = new BodyTag[4];
    public final Vector3[] wheelContact = {new Vector3(), new Vector3(), new Vector3(), new Vector3()};
    public final boolean[] wheelInContact = new boolean[4];

    /** Eventos ocurridos en el último paso (se vacía al empezar cada paso). */
    public final Array<VehicleEvent> events = new Array<>();
    /** Etiquetas de los cuerpos con los que ha chocado el chasis en el último paso. */
    public final Array<BodyTag> contacts = new Array<>();

    /** Tiempo de simulación acumulado (s). */
    public float time;

    public VehicleState() {
        for (int i = 0; i < 4; i++) {
            wheelGround[i] = BodyTag.OTHER;
        }
    }
}
