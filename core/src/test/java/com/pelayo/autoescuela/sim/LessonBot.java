package com.pelayo.autoescuela.sim;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.pelayo.autoescuela.data.ScenarioDef.GoalDef;
import com.pelayo.autoescuela.data.ScenarioDef.StepDef;
import com.pelayo.autoescuela.input.DriverInput;
import com.pelayo.autoescuela.input.DriverInputState;
import com.pelayo.autoescuela.sim.eval.maneuvers.Tutorial;
import com.pelayo.autoescuela.sim.vehicle.VehicleState;
import com.pelayo.autoescuela.sim.world.ScenarioGeometry;
import com.pelayo.autoescuela.sim.world.Zone;

/**
 * Alumno automático para los tests de las lecciones: lee los objetivos del
 * paso actual y decide los mandos con reglas sencillas (arrancar, meter la
 * marcha, controlar la velocidad, ir hacia una zona). No es un buen
 * conductor, pero si él puede completar una lección con la física real, los
 * objetivos de la lección son alcanzables.
 */
class LessonBot implements DriverInput {

    private final Tutorial tutorial;
    private final ScenarioGeometry geometry;
    private VehicleState s;
    private float cooldown;
    private float launchClutch = 1f;
    private boolean stalled;
    private int lastStep = -1;

    LessonBot(Tutorial tutorial, ScenarioGeometry geometry) {
        this.tutorial = tutorial;
        this.geometry = geometry;
    }

    void setState(VehicleState state) {
        this.s = state;
    }

    @Override
    public void poll(DriverInputState out, float dt) {
        cooldown -= dt;
        StepDef step = tutorial.getStep();
        if (step == null || s == null) {
            out.brake = 1f;
            return;
        }
        if (tutorial.getStepIndex() != lastStep) {
            lastStep = tutorial.getStepIndex();
            stalled = false;
        }
        GoalDef gearGoal = null, handbrakeGoal = null, engineGoal = null, clutchGoal = null, throttleGoal = null,
            brakeGoal = null, steerGoal = null, indicatorGoal = null, rpmGoal = null;
        boolean look = false, stall = false, stop = false;
        float desired = Float.NaN; // velocidad deseada (m/s, con signo)
        Zone zone = null;
        for (GoalDef g : step.goals) {
            switch (g.type) {
                case "gear": gearGoal = g; break;
                case "handbrake": handbrakeGoal = g; break;
                case "engine": engineGoal = g; break;
                case "clutch": clutchGoal = g; break;
                case "throttle": throttleGoal = g; break;
                case "brake": brakeGoal = g; break;
                case "steer": steerGoal = g; break;
                case "indicator": indicatorGoal = g; break;
                case "rpm": rpmGoal = g; break;
                case "look": look = true; break;
                case "stopped": stop = true; break;
                case "moving": desired = 1.2f; break;
                case "speed":
                    desired = g.min > -1e8f ? (g.min + 4f) / 3.6f : Math.max(0.5f, (g.max - 3f) / 3.6f);
                    break;
                case "distance":
                    float done = g.meters >= 0f ? tutorial.getDistance() : -tutorial.getDistance();
                    desired = done < Math.abs(g.meters) ? Math.signum(g.meters) * 2.5f : 0f;
                    break;
                case "zone": zone = geometry.zone(g.zone); break;
                case "event": stall = true; break;
                default: break;
            }
        }

        // Dirección: hacia el centro de la zona objetivo
        float steer = 0f;
        if (zone != null) {
            Vector2 c = new Vector2(zone.cx, zone.cz);
            boolean inside = zone.contains(s.position);
            float dx = c.x - s.position.x;
            float dz = c.y - s.position.z;
            float ahead = dx * s.forward.x + dz * s.forward.z;
            boolean reverse = ahead < -0.5f && Math.abs(dx) < 2.5f;
            // Signo + izquierda: el eje X del mundo apunta a la izquierda del rumbo 0
            float cross = s.forward.z * dx - s.forward.x * dz;
            float angle = MathUtils.atan2(cross, Math.max(0.1f, Math.abs(ahead)));
            steer = MathUtils.clamp(angle * 2.5f, -1f, 1f) * (reverse ? -1f : 1f);
            if (stop && inside) {
                desired = 0f;
            } else if (!inside || !stop) {
                desired = reverse ? -1.8f : 2.4f;
            } else {
                desired = 0f;
            }
        }
        if (stop && Float.isNaN(desired)) {
            desired = 0f;
        }

        float throttle = 0f, brake = 0f, clutch = 0f;
        boolean busy = false; // arrancando o cambiando de marcha: los objetivos de pedal esperan
        int gearWanted = gearGoal != null ? gearGoal.value : Integer.MIN_VALUE;
        if (gearWanted == Integer.MIN_VALUE && !Float.isNaN(desired) && Math.abs(desired) > 0.01f) {
            gearWanted = desired < 0f ? -1 : (s.gear >= 1 ? s.gear : 1);
        }
        boolean needEngine = (engineGoal != null && engineGoal.value != 0)
            || (!Float.isNaN(desired) && Math.abs(desired) > 0.01f) || rpmGoal != null || stall;

        if (stall) {
            // Provocar el calado: coger velocidad y frenar a fondo sin embrague
            if (!s.engineRunning) {
                stalled = true;
            }
            if (!stalled && s.speedKmh < 9f && s.gear == 1 && s.forwardSpeed >= 0f && !stalledBrake) {
                desired = 3f;
            } else {
                stalledBrake = true;
                out.brake = 1f;
                out.clutch = 0f;
                out.throttle = 0f;
                return;
            }
        } else {
            stalledBrake = false;
        }

        if (needEngine && !s.engineRunning) {
            busy = true;
            clutch = 1f;
            brake = 1f;
            if (!s.engineStarting && cooldown <= 0f && s.clutch > 0.9f) {
                out.ignition = true;
                cooldown = 1.5f;
            }
        } else if (engineGoal != null && engineGoal.value == 0 && s.engineRunning && cooldown <= 0f) {
            out.ignition = true;
            cooldown = 1.5f;
        } else if (gearWanted != Integer.MIN_VALUE && s.gear != gearWanted) {
            busy = true;
            clutch = 1f;
            if (gearWanted == -1 && Math.abs(s.forwardSpeed) > 0.2f) {
                brake = 1f;
            } else if (gearWanted < s.gear && gearWanted > 0 && !Float.isNaN(desired)
                && Math.abs(s.forwardSpeed) > Math.abs(desired) + 0.5f) {
                brake = 0.5f; // reducir a la velocidad adecuada antes de bajar de marcha
            } else if (s.clutch > 0.9f) {
                out.gearSelect = gearWanted;
            }
            launchClutch = 1f;
        } else if (!Float.isNaN(desired)) {
            float v = s.forwardSpeed;
            if (Math.abs(desired) < 0.01f) {
                brake = 1f;
                clutch = Math.abs(v) < 2.5f ? 1f : 0f;
            } else {
                float dir = Math.signum(desired);
                float along = v * dir;
                float err = Math.abs(desired) - along;
                if (s.rpm < 700f && s.clutch > 0.9f && along < 0.8f) {
                    clutch = 1f; // esperar a que el motor recién arrancado llegue al ralentí
                    brake = 1f;
                } else if (along < 0.8f) {
                    // Salida: embrague que sube despacio hasta el punto de fricción
                    launchClutch = Math.max(0.5f, launchClutch - dt * 1.2f);
                    clutch = launchClutch;
                    throttle = along < -0.05f ? 0.85f : 0.5f; // en cuesta, más gas si se va hacia atrás
                } else {
                    launchClutch = Math.max(0f, launchClutch - dt * 1.5f);
                    clutch = launchClutch;
                    throttle = MathUtils.clamp(err * 0.4f, s.rpm < 1100f ? 0.35f : 0f, 0.8f);
                    if (err < -0.4f) {
                        brake = MathUtils.clamp(-err * 0.25f, 0f, 1f);
                    }
                }
            }
        } else {
            brake = 1f;
            clutch = s.gear != 0 ? 1f : 0f;
        }

        // Freno de mano: se quita para moverse; lo que pida el objetivo manda
        boolean wantHandbrake = handbrakeGoal != null ? handbrakeGoal.value != 0
            : !(!Float.isNaN(desired) && Math.abs(desired) > 0.01f) && s.handbrake;
        if (s.handbrake != wantHandbrake && cooldown <= 0f
            && (wantHandbrake ? Math.abs(s.forwardSpeed) < 0.1f : clutch > 0.4f || s.gear == 0 || desired != desired)) {
            out.handbrakeToggle = true;
            cooldown = 0.5f;
        }

        // Objetivos de pedal: mandan sobre el control de velocidad
        if (clutchGoal != null && !busy) {
            boolean min = clutchGoal.min > -1e8f;
            boolean max = clutchGoal.max < 1e8f;
            clutch = min && max ? (clutchGoal.min + clutchGoal.max) / 2f : min ? 1f : 0f;
        }
        if (throttleGoal != null && !busy) {
            throttle = throttleGoal.min > -1e8f ? 1f : 0f;
        }
        if (brakeGoal != null) {
            brake = brakeGoal.min > -1e8f ? 1f : 0f;
        }
        if (rpmGoal != null) {
            throttle = Math.max(throttle, s.rpm < rpmGoal.min + 400f ? 0.6f : 0.3f);
            if (handbrakeGoal == null || handbrakeGoal.value == 0) {
                brake = Math.max(brake, s.gear == 0 ? 0f : 0f);
            }
        }
        if (steerGoal != null) {
            steer = steerGoal.min > -1e8f ? 1f : -1f;
        }
        if (indicatorGoal != null && cooldown <= 0f) {
            int now = s.indicatorLeft ? -1 : s.indicatorRight ? 1 : 0;
            if (now != indicatorGoal.value) {
                if (indicatorGoal.value < 0 || (indicatorGoal.value == 0 && now < 0)) {
                    out.indicatorLeft = true;
                } else {
                    out.indicatorRight = true;
                }
                cooldown = 0.3f;
            }
        }
        out.throttle = throttle;
        out.brake = brake;
        out.clutch = clutch;
        out.steer = steer;
        out.lookYaw = look ? 1f : 0f;
    }

    private boolean stalledBrake;
}
