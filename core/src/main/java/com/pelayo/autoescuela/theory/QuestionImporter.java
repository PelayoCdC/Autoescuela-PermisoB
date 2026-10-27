package com.pelayo.autoescuela.theory;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.ObjectSet;
import com.pelayo.autoescuela.data.PreguntaDef;

/**
 * Importa bancos de preguntas del usuario desde JSON (mismo esquema que
 * preguntas.json) o CSV con punto y coma:
 * {@code tema;enunciado;opcion1;opcion2;opcion3;correcta;explicacion;imagen}
 * (correcta = 1, 2 o 3, o A/B/C). Deduplica por enunciado y fusiona en
 * {@code local/save/preguntas_usuario.json}. Las imágenes referenciadas se
 * copian a {@code local/theory/}.
 */
public class QuestionImporter {

    public static class Report {
        public int added;
        public int duplicates;
        public int invalid;
        public final Array<String> problems = new Array<>();

        @Override
        public String toString() {
            return "añadidas " + added + ", duplicadas " + duplicates + ", inválidas " + invalid;
        }
    }

    private final TheoryRepository repo;
    private final FileHandle userFile;

    public QuestionImporter(TheoryRepository repo) {
        this(repo, Gdx.files.local(TheoryRepository.USER_FILE));
    }

    public QuestionImporter(TheoryRepository repo, FileHandle userFile) {
        this.repo = repo;
        this.userFile = userFile;
    }

    /** Importa un fichero .json o .csv. */
    public Report importFile(FileHandle file) {
        Array<PreguntaDef> candidates = file.extension().equalsIgnoreCase("csv")
            ? parseCsv(file) : parseJson(file);
        return merge(candidates, file);
    }

    private Array<PreguntaDef> parseJson(FileHandle file) {
        return new Json().fromJson(Array.class, PreguntaDef.class, file);
    }

    private Array<PreguntaDef> parseCsv(FileHandle file) {
        Array<PreguntaDef> out = new Array<>();
        String[] lines = file.readString("UTF-8").split("\\r?\\n");
        int n = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split(";", -1);
            if (f.length < 6) {
                continue;
            }
            if (n == 0 && f[0].equalsIgnoreCase("tema") && f[1].equalsIgnoreCase("enunciado")) {
                continue; // cabecera
            }
            n++;
            PreguntaDef p = new PreguntaDef();
            p.tema = f[0].trim();
            p.enunciado = f[1].trim();
            p.opciones = new String[] {f[2].trim(), f[3].trim(), f[4].trim()};
            String c = f[5].trim().toUpperCase();
            p.correcta = c.equals("A") ? 0 : c.equals("B") ? 1 : c.equals("C") ? 2
                : c.matches("[123]") ? Integer.parseInt(c) - 1 : -1;
            p.explicacion = f.length > 6 ? f[6].trim() : "";
            p.imagen = f.length > 7 && !f[7].trim().isEmpty() ? f[7].trim() : null;
            p.fuente = "importada";
            out.add(p);
        }
        return out;
    }

    private Report merge(Array<PreguntaDef> candidates, FileHandle source) {
        Report report = new Report();
        Array<PreguntaDef> user = loadUser();
        ObjectSet<String> known = repo.normalizedEnunciados();
        for (PreguntaDef p : user) {
            known.add(TheoryRepository.normalize(p.enunciado));
        }
        int seq = user.size;
        for (PreguntaDef p : candidates) {
            if (p.id == null || p.id.isBlank() || repo.hasPregunta(p.id) || containsId(user, p.id)) {
                p.id = "u" + String.format("%05d", ++seq);
            }
            if (p.fuente == null) {
                p.fuente = "importada";
            }
            String problem = p.validate();
            if (problem == null && repo.tema(p.tema) == null) {
                problem = p.id + ": tema desconocido '" + p.tema + "'";
            }
            if (problem != null) {
                report.invalid++;
                report.problems.add(problem);
                continue;
            }
            String key = TheoryRepository.normalize(p.enunciado);
            if (known.contains(key)) {
                report.duplicates++;
                continue;
            }
            known.add(key);
            copyImage(p, source);
            user.add(p);
            report.added++;
        }
        saveUser(user);
        for (PreguntaDef p : user) {
            repo.add(p);
        }
        return report;
    }

    private static boolean containsId(Array<PreguntaDef> list, String id) {
        for (PreguntaDef p : list) {
            if (id.equals(p.id)) {
                return true;
            }
        }
        return false;
    }

    /** Si la imagen está junto al fichero importado, se copia a local/theory/. */
    private void copyImage(PreguntaDef p, FileHandle source) {
        if (p.imagen == null) {
            return;
        }
        FileHandle img = source.sibling(p.imagen);
        if (img.exists()) {
            FileHandle dest = Gdx.files.local("theory/" + img.name());
            dest.parent().mkdirs();
            img.copyTo(dest);
            p.imagen = "theory/" + img.name();
        }
    }

    public Array<PreguntaDef> loadUser() {
        if (!userFile.exists()) {
            return new Array<>();
        }
        return new Json().fromJson(Array.class, PreguntaDef.class, userFile);
    }

    private void saveUser(Array<PreguntaDef> user) {
        Json json = new Json(JsonWriter.OutputType.json);
        json.setUsePrototypes(false);
        userFile.parent().mkdirs();
        userFile.writeString(json.prettyPrint(json.toJson(user, Array.class, PreguntaDef.class)), false, "UTF-8");
    }
}
