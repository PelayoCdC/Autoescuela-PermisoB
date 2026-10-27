package com.pelayo.autoescuela.theory;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.pelayo.autoescuela.data.PreguntaDef;
import com.pelayo.autoescuela.data.TemaDef;

/**
 * Temas y preguntas cargados y validados: el banco propio de {@code assets}
 * más el banco del usuario de {@code local/save/preguntas_usuario.json}.
 */
public class TheoryRepository {

    public static final String TEMAS_FILE = "data/temas.json";
    public static final String PREGUNTAS_FILE = "data/preguntas.json";
    public static final String USER_FILE = "save/preguntas_usuario.json";
    /** Preguntas de los tests de la revista de la DGT (texto reproducido citando la fuente; explicaciones propias). */
    public static final String DGT_FILE = "data/preguntas_dgt.json";
    /** Bancos externos con permiso de uso: simulador de examen de la DGT (normal y lectura fácil) y TodoTest. */
    public static final String[] EXTERNAL_FILES = {"data/preguntas_sede_dgt.json", "data/preguntas_todotest.json"};

    public final Array<TemaDef> temas = new Array<>();
    public final Array<PreguntaDef> preguntas = new Array<>();
    private final ObjectMap<String, TemaDef> temasById = new ObjectMap<>();
    private final ObjectMap<String, PreguntaDef> preguntasById = new ObjectMap<>();
    private final ObjectMap<String, Array<PreguntaDef>> porTema = new ObjectMap<>();

    public static TheoryRepository loadDefault() {
        TheoryRepository repo = new TheoryRepository();
        repo.loadTemas(Gdx.files.internal(TEMAS_FILE));
        repo.loadPreguntas(Gdx.files.internal(PREGUNTAS_FILE));
        if (Gdx.files.internal(DGT_FILE).exists()) {
            repo.loadPreguntas(Gdx.files.internal(DGT_FILE));
        }
        for (String file : EXTERNAL_FILES) {
            if (Gdx.files.internal(file).exists()) {
                repo.loadPreguntas(Gdx.files.internal(file));
            }
        }
        FileHandle user = Gdx.files.local(USER_FILE);
        if (user.exists()) {
            repo.loadPreguntas(user);
        }
        return repo;
    }

    public void loadTemas(FileHandle file) {
        Array<TemaDef> loaded = new Json().fromJson(Array.class, TemaDef.class, file);
        for (TemaDef t : loaded) {
            if (t.id == null || temasById.containsKey(t.id)) {
                throw new IllegalStateException("Tema sin id o repetido: " + t.id + " en " + file);
            }
            temas.add(t);
            temasById.put(t.id, t);
            porTema.put(t.id, new Array<>());
        }
        temas.sort((a, b) -> Integer.compare(a.orden, b.orden));
    }

    /** Añade preguntas de un fichero; valida estructura, ids únicos y tema existente. */
    public int loadPreguntas(FileHandle file) {
        Array<PreguntaDef> loaded = new Json().fromJson(Array.class, PreguntaDef.class, file);
        int added = 0;
        for (PreguntaDef p : loaded) {
            String problem = p.validate();
            if (problem != null) {
                throw new IllegalStateException(file + ": " + problem);
            }
            if (!temasById.containsKey(p.tema)) {
                throw new IllegalStateException(file + ": la pregunta " + p.id + " usa un tema desconocido: " + p.tema);
            }
            if (preguntasById.containsKey(p.id)) {
                Gdx.app.log("TheoryRepository", "Pregunta repetida ignorada: " + p.id);
                continue;
            }
            preguntas.add(p);
            preguntasById.put(p.id, p);
            porTema.get(p.tema).add(p);
            added++;
        }
        return added;
    }

    /** Añade una pregunta ya validada (p.ej. recién importada); ignora ids repetidos. */
    public boolean add(PreguntaDef p) {
        if (preguntasById.containsKey(p.id) || !temasById.containsKey(p.tema)) {
            return false;
        }
        preguntas.add(p);
        preguntasById.put(p.id, p);
        porTema.get(p.tema).add(p);
        return true;
    }

    public TemaDef tema(String id) {
        return temasById.get(id);
    }

    public PreguntaDef pregunta(String id) {
        return preguntasById.get(id);
    }

    public Array<PreguntaDef> preguntasDe(String temaId) {
        Array<PreguntaDef> list = porTema.get(temaId);
        return list == null ? new Array<>() : list;
    }

    public boolean hasPregunta(String id) {
        return preguntasById.containsKey(id);
    }

    /** Enunciados normalizados ya presentes (para deduplicar al importar). */
    public ObjectSet<String> normalizedEnunciados() {
        ObjectSet<String> set = new ObjectSet<>();
        for (PreguntaDef p : preguntas) {
            set.add(normalize(p.enunciado));
        }
        return set;
    }

    /** Minúsculas, sin acentos ni signos, espacios colapsados. */
    public static String normalize(String s) {
        String n = java.text.Normalizer.normalize(s.toLowerCase(), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        return n.replaceAll("[^a-z0-9ñ ]", " ").replaceAll("\\s+", " ").trim();
    }
}
