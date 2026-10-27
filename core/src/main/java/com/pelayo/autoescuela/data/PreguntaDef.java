package com.pelayo.autoescuela.data;

/**
 * Pregunta tipo test ({@code assets/data/preguntas.json} y
 * {@code local/save/preguntas_usuario.json}). POJO para libGDX Json.
 */
public class PreguntaDef {

    public String id;
    /** Id del tema al que pertenece. */
    public String tema;
    public String enunciado;
    /** Tres opciones (algunos bancos importados traen preguntas de dos). */
    public String[] opciones;
    /** Índice de la opción correcta. */
    public int correcta;
    public String explicacion;
    /** Imagen opcional (ruta relativa a assets/ o a local/). */
    public String imagen;
    /** 1 fácil .. 3 difícil. */
    public int dificultad = 1;
    /** Procedencia (p.ej. "propia", "DGT", "importada"). */
    public String fuente = "propia";

    /** Opciones que aluden a las demás: si existen, el orden de las opciones no se puede cambiar. */
    private static final java.util.regex.Pattern REFERS_TO_OTHERS = java.util.regex.Pattern.compile(
        "(?i)anteriores|ambas|las dos|ninguna de|todas las|\\b[abc] y [abc]\\b");

    /**
     * Copia con las opciones en otro orden (y {@code correcta} reasignada), para
     * que la posición de la respuesta no se pueda memorizar. Si alguna opción
     * alude a las demás ("todas las anteriores"...), se devuelve tal cual.
     */
    public PreguntaDef withShuffledOptions(com.badlogic.gdx.math.RandomXS128 random) {
        for (String o : opciones) {
            if (REFERS_TO_OTHERS.matcher(o).find()) {
                return this;
            }
        }
        int n = opciones.length;
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        PreguntaDef copy = new PreguntaDef();
        copy.id = id;
        copy.tema = tema;
        copy.enunciado = enunciado;
        copy.explicacion = explicacion;
        copy.imagen = imagen;
        copy.dificultad = dificultad;
        copy.fuente = fuente;
        copy.opciones = new String[n];
        for (int i = 0; i < n; i++) {
            copy.opciones[i] = opciones[order[i]];
            if (order[i] == correcta) {
                copy.correcta = i;
            }
        }
        return copy;
    }

    /** Comprueba la estructura; devuelve el problema o null si está bien. */
    public String validate() {
        if (id == null || id.isBlank()) {
            return "sin id";
        }
        if (tema == null || tema.isBlank()) {
            return id + ": sin tema";
        }
        if (enunciado == null || enunciado.isBlank()) {
            return id + ": sin enunciado";
        }
        if (opciones == null || opciones.length < 2 || opciones.length > 3) {
            return id + ": debe tener 2 o 3 opciones";
        }
        for (String o : opciones) {
            if (o == null || o.isBlank()) {
                return id + ": opción vacía";
            }
        }
        if (correcta < 0 || correcta >= opciones.length) {
            return id + ": 'correcta' no corresponde a ninguna opción";
        }
        return null;
    }
}
