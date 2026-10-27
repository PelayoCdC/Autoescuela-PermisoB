package com.pelayo.autoescuela.sim.vehicle;

/** Sucesos discretos del coche, para audio, HUD y evaluación. */
public enum VehicleEvent {
    /** El motor se ha calado. */
    STALL,
    /** Intento de cambiar de marcha sin pisar el embrague (rascada). */
    GRIND,
    /** Intento de meter la marcha atrás en movimiento. */
    REVERSE_REFUSED,
    /** El motor ha arrancado. */
    ENGINE_START,
    /** Se ha cortado el contacto. */
    ENGINE_OFF,
    /** Marcha cambiada. */
    GEAR_CHANGED,
    /** Intento de arrancar con una marcha puesta y el embrague suelto. */
    START_IN_GEAR
}
