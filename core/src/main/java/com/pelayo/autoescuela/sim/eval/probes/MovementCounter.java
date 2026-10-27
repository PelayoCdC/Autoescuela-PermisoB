package com.pelayo.autoescuela.sim.eval.probes;

/**
 * Cuenta "movimientos" al estilo del examen: cada tramo recorrido en un
 * sentido. Un cambio de sentido solo cuenta cuando se recorren más de 15 cm
 * en el nuevo sentido, para ignorar balanceos al parar.
 */
public class MovementCounter {

    private static final float MIN_DISTANCE = 0.15f;
    private static final float MIN_SPEED = 0.02f;

    private int movements;
    private int currentSign;
    private int candidateSign;
    private float candidateDistance;
    private float distanceInCurrent;

    public void reset() {
        movements = 0;
        currentSign = 0;
        candidateSign = 0;
        candidateDistance = 0f;
        distanceInCurrent = 0f;
    }

    public void update(float forwardSpeed, float dt) {
        if (Math.abs(forwardSpeed) < MIN_SPEED) {
            return;
        }
        int sign = forwardSpeed > 0f ? 1 : -1;
        float d = Math.abs(forwardSpeed) * dt;
        if (sign == currentSign) {
            distanceInCurrent += d;
            candidateSign = 0;
            candidateDistance = 0f;
            return;
        }
        if (sign != candidateSign) {
            candidateSign = sign;
            candidateDistance = 0f;
        }
        candidateDistance += d;
        if (candidateDistance >= MIN_DISTANCE) {
            currentSign = sign;
            movements++;
            distanceInCurrent = candidateDistance;
            candidateSign = 0;
            candidateDistance = 0f;
        }
    }

    public int getMovements() {
        return movements;
    }

    /** Sentido del movimiento actual: 1 adelante, -1 atrás, 0 aún sin moverse. */
    public int getCurrentSign() {
        return currentSign;
    }

    public float getDistanceInCurrent() {
        return distanceInCurrent;
    }
}
