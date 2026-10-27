package com.pelayo.autoescuela.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pelayo.autoescuela.sim.eval.probes.MovementCounter;
import org.junit.jupiter.api.Test;

class MovementCounterTest {

    private static final float DT = 1f / 120f;

    private static void move(MovementCounter c, float speed, float seconds) {
        int steps = Math.round(seconds / DT);
        for (int i = 0; i < steps; i++) {
            c.update(speed, DT);
        }
    }

    @Test
    void countsEachDirectionChange() {
        MovementCounter c = new MovementCounter();
        assertEquals(0, c.getMovements());
        move(c, -1f, 2f);   // marcha atrás
        assertEquals(1, c.getMovements());
        move(c, 0f, 1f);    // parado
        move(c, 1f, 1f);    // adelante
        assertEquals(2, c.getMovements());
        move(c, -0.5f, 1f); // atrás otra vez
        assertEquals(3, c.getMovements());
    }

    @Test
    void ignoresTinyRocking() {
        MovementCounter c = new MovementCounter();
        move(c, -1f, 1f);
        // Un balanceo de 5 cm hacia delante al frenar no cuenta
        move(c, 0.5f, 0.1f);
        assertEquals(1, c.getMovements());
        move(c, -1f, 0.5f);
        assertEquals(1, c.getMovements());
    }
}
