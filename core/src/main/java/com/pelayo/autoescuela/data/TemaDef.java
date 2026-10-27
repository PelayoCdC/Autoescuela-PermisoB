package com.pelayo.autoescuela.data;

import com.badlogic.gdx.utils.Array;

/** Tema del temario ({@code assets/data/temas.json}). POJO para libGDX Json. */
public class TemaDef {

    public String id;
    public String titulo;
    public int orden = 100;
    /** Resumen de una línea para la lista de temas. */
    public String resumen;
    public Array<SeccionDef> secciones = new Array<>();

    /**
     * Sección con texto en marcado mínimo: {@code # Título}, {@code ## Subtítulo},
     * {@code - viñeta}, {@code **negrita**}, {@code ![alt](ruta)} y párrafos
     * separados por líneas en blanco.
     */
    public static class SeccionDef {
        public String titulo;
        public String texto;
        /** Imagen opcional (ruta relativa a assets/ o a local/). */
        public String imagen;
    }
}
