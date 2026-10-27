package com.pelayo.autoescuela.input;

import com.badlogic.gdx.utils.Array;

/**
 * Reproduce una secuencia de mandos fija, para capturas automáticas y pruebas
 * de regresión. Guion: pasos separados por {@code |}, cada uno
 * {@code tiempo:clave=valor,clave=valor}. Los ejes se mantienen hasta que otro
 * paso los cambie; los pulsos se disparan una vez al llegar al tiempo.
 *
 * <pre>
 * 0:ignition,gear=1,clutch=1 | 1.5:throttle=0.3,clutch=0 | 4:steer=0.5 | 6:camera | 8:brake=1
 * </pre>
 *
 * Claves de eje: throttle, brake, clutch, steer, lookYaw, lookPitch.
 * Pulsos: ignition, handbrake, camera, reset, gearUp, gearDown, indicatorLeft,
 * indicatorRight, lookReset; continue (lecciones); {@code gear=N} selecciona marcha (-1 R, 0 N, 1..5).
 */
public class ScriptedDriverInput implements DriverInput {

    private static class Step {
        float time;
        String[] commands;
    }

    private final Array<Step> steps = new Array<>();
    private int next = 0;
    private float time = 0f;

    private float throttle, brake, clutch, steer, lookYaw, lookPitch;

    public ScriptedDriverInput(String script) {
        for (String raw : script.split("\\|")) {
            String s = raw.trim();
            if (s.isEmpty()) {
                continue;
            }
            int colon = s.indexOf(':');
            Step step = new Step();
            step.time = Float.parseFloat(s.substring(0, colon).trim());
            step.commands = s.substring(colon + 1).split(",");
            steps.add(step);
        }
        steps.sort((a, b) -> Float.compare(a.time, b.time));
    }

    @Override
    public void poll(DriverInputState out, float dt) {
        time += dt;
        while (next < steps.size && steps.get(next).time <= time) {
            for (String cmd : steps.get(next).commands) {
                apply(cmd.trim(), out);
            }
            next++;
        }
        out.throttle = throttle;
        out.brake = brake;
        out.clutch = clutch;
        out.steer = steer;
        out.lookYaw = lookYaw;
        out.lookPitch = lookPitch;
    }

    private void apply(String cmd, DriverInputState out) {
        if (cmd.isEmpty()) {
            return;
        }
        int eq = cmd.indexOf('=');
        String key = eq < 0 ? cmd : cmd.substring(0, eq);
        float value = eq < 0 ? 0f : Float.parseFloat(cmd.substring(eq + 1));
        switch (key) {
            case "throttle": throttle = value; break;
            case "brake": brake = value; break;
            case "clutch": clutch = value; break;
            case "steer": steer = value; break;
            case "lookYaw": lookYaw = value; break;
            case "lookPitch": lookPitch = value; break;
            case "gear": out.gearSelect = (int) value; break;
            case "gearUp": out.gearUp = true; break;
            case "gearDown": out.gearDown = true; break;
            case "ignition": out.ignition = true; break;
            case "handbrake": out.handbrakeToggle = true; break;
            case "camera": out.cameraToggle = true; break;
            case "reset": out.resetVehicle = true; break;
            case "continue": out.lessonContinue = true; break;
            case "indicatorLeft": out.indicatorLeft = true; break;
            case "indicatorRight": out.indicatorRight = true; break;
            case "lookReset": out.lookReset = true; break;
            default: throw new IllegalArgumentException("Mando desconocido en el guion: " + key);
        }
    }

    public float getTime() {
        return time;
    }
}
