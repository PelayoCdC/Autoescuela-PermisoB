package com.pelayo.autoescuela.sim.eval;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.OrderedMap;

/** Resultado de una maniobra: faltas, veredicto y métricas para la pantalla final. */
public class EvaluationResult {

    public final Array<Fault> faults = new Array<>();
    /**
     * Recorrido para la repetición: cada ~0,1 s el instante (s), la posición x y z (m), el rumbo (grados) y
     * la velocidad (km/h), en grupos de {@link #TRACE_STRIDE} valores.
     */
    public final com.badlogic.gdx.utils.FloatArray trace = new com.badlogic.gdx.utils.FloatArray();
    public static final int TRACE_STRIDE = 5;
    /** Métricas legibles (nombre → valor), en orden de inserción. */
    public final OrderedMap<String, String> metrics = new OrderedMap<>();
    public boolean passed;
    public float duration;
    public boolean completed;

    public int count(FaultSeverity severity) {
        int n = 0;
        for (Fault f : faults) {
            if (f.severity == severity) {
                n++;
            }
        }
        return n;
    }

    public boolean has(String code) {
        for (Fault f : faults) {
            if (f.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
