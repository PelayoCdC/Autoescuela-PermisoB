package com.pelayo.autoescuela.sim;

/** Qué es cada cuerpo del mundo físico, para la evaluación de maniobras. */
public enum BodyTag {
    /** Asfalto o suelo transitable. */
    ROAD,
    /** Bordillo (pisarlo o tocarlo es falta). */
    CURB,
    /** Cono de señalización (móvil). */
    CONE,
    /** Valla, muro u otro obstáculo rígido. */
    BARRIER,
    /** Coche aparcado. */
    PARKED_CAR,
    /** Coche del tráfico (en marcha). */
    TRAFFIC_CAR,
    /** Peatón. */
    PEDESTRIAN,
    /** Chasis del coche del jugador. */
    PLAYER_CHASSIS,
    /** Cualquier otra cosa. */
    OTHER
}
