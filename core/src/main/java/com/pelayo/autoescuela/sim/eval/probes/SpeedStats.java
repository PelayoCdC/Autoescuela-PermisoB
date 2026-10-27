package com.pelayo.autoescuela.sim.eval.probes;

/** Media y desviación típica de la velocidad mientras el coche se mueve (uniformidad). */
public class SpeedStats {

    private static final float MIN_SPEED = 0.1f;

    private int n;
    private double sum;
    private double sumSq;
    private float max;

    public void reset() {
        n = 0;
        sum = 0;
        sumSq = 0;
        max = 0f;
    }

    public void update(float forwardSpeed) {
        float v = Math.abs(forwardSpeed);
        if (v < MIN_SPEED) {
            return;
        }
        n++;
        sum += v;
        sumSq += v * v;
        max = Math.max(max, v);
    }

    public float mean() {
        return n == 0 ? 0f : (float) (sum / n);
    }

    public float stdDev() {
        if (n < 2) {
            return 0f;
        }
        double mean = sum / n;
        return (float) Math.sqrt(Math.max(0, sumSq / n - mean * mean));
    }

    public float max() {
        return max;
    }

    public int samples() {
        return n;
    }
}
