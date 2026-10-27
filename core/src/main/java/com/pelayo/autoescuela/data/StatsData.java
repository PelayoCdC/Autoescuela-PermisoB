package com.pelayo.autoescuela.data;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

/** Progreso del usuario en la teoría ({@code local/save/stats.json}). POJO para libGDX Json. */
public class StatsData {

    public static class TopicStats {
        public int asked;
        public int correct;
    }

    public static class ExamRecord {
        /** Fecha en milisegundos desde 1970. */
        public long date;
        public int correct;
        public int total;
        public boolean passed;
        public int seconds;
    }

    /**
     * Ficha de repaso espaciado de una pregunta (sistema de cajas de Leitner): cada acierto la sube de caja
     * y la aleja en el tiempo; un fallo la devuelve a la primera.
     */
    public static class Card {
        /** 0 = recién fallada o vista una vez .. 5 = dominada. */
        public int box;
        /** Cuándo toca repasarla (ms desde 1970). */
        public long due;
        public int seen;
        public int wrong;
    }

    /** Fichas de repaso por id de pregunta (solo las que han salido alguna vez). */
    public ObjectMap<String, Card> cards = new ObjectMap<>();
    /** Aciertos por tema. */
    public ObjectMap<String, TopicStats> perTema = new ObjectMap<>();
    /** Preguntas falladas pendientes de repaso. */
    public Array<String> failedIds = new Array<>();
    /** Aciertos seguidos en repaso por pregunta fallada (a 2 se retira de la lista). */
    public ObjectMap<String, Integer> reviewStreak = new ObjectMap<>();
    public Array<ExamRecord> examHistory = new Array<>();
    public int totalAnswered;
    public int totalCorrect;
}
