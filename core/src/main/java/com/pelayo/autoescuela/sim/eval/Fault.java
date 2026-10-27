package com.pelayo.autoescuela.sim.eval;

/** Una falta cometida durante la maniobra. */
public class Fault {

    /** Código estable (clave i18n sin el prefijo "fault."). */
    public final String code;
    public final FaultSeverity severity;
    /** Instante de simulación (s) en que se anotó. */
    public final float time;
    /** Texto ya traducido para mostrar. */
    public final String message;
    /** Dónde estaba el coche (m), para marcarla en la repetición; NaN si no se sabe. */
    public float x = Float.NaN;
    public float z = Float.NaN;

    public Fault(String code, FaultSeverity severity, float time, String message) {
        this.code = code;
        this.severity = severity;
        this.time = time;
        this.message = message;
    }

    @Override
    public String toString() {
        return severity + " " + code + " @" + time;
    }
}
