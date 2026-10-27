package com.pelayo.autoescuela.lwjgl3;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Descarga los tests de la revista de la DGT "Tráfico y Seguridad Vial"
 * (https://revista.dgt.es/es/test/) y los guarda como JSON en bruto para
 * revisarlos e incorporarlos al banco. Solo el texto: su aviso legal autoriza
 * reproducir los textos citando la revista como fuente, pero no las imágenes.
 * Las preguntas que se refieren a su imagen ("esta señal", "en la imagen"...)
 * se marcan con {@code "dependeDeImagen": true}.
 *
 * <pre>
 * gradlew dgtRevista                         (números 261 a 279)
 * gradlew dgtRevista -Pdesde=270 -Phasta=279 -Pout=ruta.json
 * </pre>
 */
public final class RevistaDgtTests {

    private static final String BASE = "https://revista.dgt.es/es/test/Test-num-%d.shtml";
    private static final Pattern ARTICLE = Pattern.compile("<article class=\"test\">(.*?)</article>", Pattern.DOTALL);
    private static final Pattern TITLE = Pattern.compile("<h4 class=\"tit_not\">(.*?)</h4>", Pattern.DOTALL);
    private static final Pattern OPTION = Pattern.compile(
        "<li>\\s*<span class=\"opcion\">\\s*([ABCabc])\\s*[.)]?\\s*</span>(.*?)</li>", Pattern.DOTALL);
    /** La letra correcta: a veces en minúscula o sin la etiqueta span. */
    private static final Pattern ANSWER = Pattern.compile(
        "(?i)Respuesta correcta\\s*</strong>\\s*(?:<span class=\"opcion\">)?\\s*([ABC])\\b", Pattern.DOTALL);
    /** Explicación que algunos números añaden tras la respuesta ("CON MÁS DETALLE"). */
    private static final Pattern DETAIL = Pattern.compile(
        "(?i)CON M[ÁA]S DETALLE\\.?\\s*</strong>\\s*</p>(.*?)</div>", Pattern.DOTALL);
    private static final Pattern ALT = Pattern.compile("<img [^>]*alt=\"([^\"]*)\"", Pattern.DOTALL);
    private static final Pattern DATE = Pattern.compile("<title>([^<]*)</title>");
    /** Enunciados que solo se entienden viendo la foto o el dibujo de la revista. */
    private static final Pattern NEEDS_IMAGE = Pattern.compile(
        "(?iu)\\b(esta|estas|este|estos)\\s+(señal|señales|marca|marcas|panel|paneles|situaci[oó]n|vehículo|"
            + "vehículos|turismo|camión|agente|semáforo|luz|luces|línea|líneas|flecha|cruce|intersecci[oó]n|"
            + "glorieta|carril|imagen|dibujo|fotograf[ií]a|conductor|ciclista|peat[oó]n|motocicleta|vía|calzada|"
            + "tramo|indicaci[oó]n|dispositivo|elemento|placa|pictograma|zona|maniobra)\\b"
            + "|\\b(en|de|según) la (imagen|fotograf[ií]a|ilustraci[oó]n|figura)\\b|\\bvehículo [ABC]\\b"
            + "|\\b(el|del) (vehículo|turismo|coche) (rojo|azul|blanco|amarillo|verde|gris|negro)\\b");

    private RevistaDgtTests() {
    }

    public static void main(String[] args) throws Exception {
        int from = args.length > 0 ? Integer.parseInt(args[0]) : 261;
        int to = args.length > 1 ? Integer.parseInt(args[1]) : 279;
        Path out = Path.of(args.length > 2 ? args[2] : "assets/import/revista_dgt_bruto.json");
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20)).build();
        List<String> items = new ArrayList<>();
        int total = 0;
        int image = 0;
        for (int n = from; n <= to; n++) {
            String html = fetch(http, String.format(Locale.ROOT, BASE, n));
            if (html == null) {
                System.out.println("Número " + n + ": no existe");
                continue;
            }
            Matcher dm = DATE.matcher(html);
            String date = dm.find() ? clean(dm.group(1)) : "";
            Matcher am = ARTICLE.matcher(html);
            int q = 0;
            while (am.find()) {
                String a = am.group(1);
                Matcher tm = TITLE.matcher(a);
                Matcher ans = ANSWER.matcher(a);
                if (!tm.find() || !ans.find()) {
                    continue;
                }
                String statement = clean(tm.group(1)).replaceFirst("^\\d+\\s*[.)-]\\s*", "");
                List<String> options = new ArrayList<>();
                Matcher om = OPTION.matcher(a);
                while (om.find()) {
                    options.add(clean(om.group(2)));
                }
                if (options.size() != 3) {
                    continue;
                }
                q++;
                Matcher alt = ALT.matcher(a);
                boolean needsImage = NEEDS_IMAGE.matcher(statement).find();
                image += needsImage ? 1 : 0;
                int correct = Character.toUpperCase(ans.group(1).charAt(0)) - 'A';
                Matcher det = DETAIL.matcher(a);
                String detail = det.find() ? clean(det.group(1)) : "";
                items.add(String.format(Locale.ROOT,
                    "  {\"id\": \"dgt%d_%02d\", \"numero\": %d, \"fecha\": %s, \"url\": %s, \"enunciado\": %s, "
                        + "\"opciones\": [%s, %s, %s], \"correcta\": %d, \"detalle\": %s, \"imagenAlt\": %s, \"dependeDeImagen\": %b}",
                    n, q, n, json(date), json(String.format(Locale.ROOT, BASE, n)), json(statement),
                    json(options.get(0)), json(options.get(1)), json(options.get(2)), correct, json(detail),
                    json(alt.find() ? clean(alt.group(1)) : ""), needsImage));
            }
            total += q;
            System.out.println("Número " + n + " (" + date + "): " + q + " preguntas");
            Thread.sleep(400); // sin prisas: una petición por número
        }
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, "[\n" + String.join(",\n", items) + "\n]\n", StandardCharsets.UTF_8);
        System.out.println(total + " preguntas (" + image + " dependen de su imagen) en " + out.toAbsolutePath());
    }

    private static String fetch(HttpClient http, String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Autoescuela (app privada de estudio)").GET().build();
        HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200) {
            return null;
        }
        return new String(res.body(), StandardCharsets.UTF_8);
    }

    /** Quita etiquetas, decodifica entidades HTML y normaliza espacios. */
    private static String clean(String html) {
        String s = html.replaceAll("<[^>]+>", " ")
            .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&#39;", "'").replace("&apos;", "'");
        Matcher m = Pattern.compile("&#(\\d+);").matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1)))));
        }
        m.appendTail(sb);
        return sb.toString().replace("&amp;", "&").replaceAll("\\s+", " ").trim();
    }

    private static String json(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                default:
                    if (c < 0x20) {
                        b.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.append('"').toString();
    }
}
