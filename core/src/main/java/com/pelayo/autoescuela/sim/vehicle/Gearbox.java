package com.pelayo.autoescuela.sim.vehicle;

import com.badlogic.gdx.utils.Array;
import com.pelayo.autoescuela.data.VehicleSpec;

/**
 * Caja manual de 5 marchas + R + N. Un cambio exige el embrague pisado (si no,
 * rasca y no entra), la marcha atrás solo entra con el coche parado, y
 * durante el cambio la caja pasa un instante por punto muerto.
 */
public class Gearbox {

    public static final int REVERSE = -1;
    public static final int NEUTRAL = 0;
    public static final int MAX_GEAR = 5;

    /** Embrague mínimo para que entre una marcha. */
    private static final float CLUTCH_THRESHOLD = 0.85f;
    /** Velocidad máxima para engranar la marcha atrás (m/s). */
    private static final float REVERSE_MAX_SPEED = 0.5f;

    private final VehicleSpec.Gearbox spec;
    private int gear = NEUTRAL;
    private float shiftTimer = 0f;

    public Gearbox(VehicleSpec.Gearbox spec) {
        this.spec = spec;
    }

    /** Marcha seleccionada (-1 R, 0 N, 1..5), aunque el cambio esté en curso. */
    public int getGear() {
        return gear;
    }

    public boolean isShifting() {
        return shiftTimer > 0f;
    }

    public void update(float dt) {
        if (shiftTimer > 0f) {
            shiftTimer -= dt;
        }
    }

    /**
     * Pide una marcha. Devuelve true si el cambio ha entrado.
     *
     * @param clutchPedal  0..1 (1 = pisado a fondo)
     * @param forwardSpeed velocidad del coche (m/s)
     */
    public boolean request(int target, float clutchPedal, float forwardSpeed, Array<VehicleEvent> events) {
        if (target < REVERSE || target > MAX_GEAR || target == gear) {
            return false;
        }
        // A punto muerto se puede ir sin embrague; a una marcha, no
        if (target != NEUTRAL && clutchPedal < CLUTCH_THRESHOLD) {
            events.add(VehicleEvent.GRIND);
            return false;
        }
        if (target == REVERSE && Math.abs(forwardSpeed) > REVERSE_MAX_SPEED) {
            events.add(VehicleEvent.REVERSE_REFUSED);
            return false;
        }
        gear = target;
        shiftTimer = target == NEUTRAL ? 0f : spec.shiftTime;
        events.add(VehicleEvent.GEAR_CHANGED);
        return true;
    }

    public void forceGear(int gear) {
        this.gear = gear;
        shiftTimer = 0f;
    }

    /** Relación total rueda→motor (incluye el grupo); 0 en punto muerto o durante el cambio. */
    public float ratio() {
        if (gear == NEUTRAL || shiftTimer > 0f) {
            return 0f;
        }
        if (gear == REVERSE) {
            return -spec.reverse * spec.finalDrive;
        }
        return spec.ratios[gear - 1] * spec.finalDrive;
    }
}
