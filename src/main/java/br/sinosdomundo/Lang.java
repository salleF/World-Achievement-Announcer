package br.sinosdomundo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Idioma dos menus, mensagens e do console.
 *
 * <p>O código escreve os textos em português; o arquivo lang/&lt;idioma&gt;.yml liga cada trecho em português
 * à tradução ("Narrador ativo: ": "Active narrator: "). Na hora de mostrar, cada trecho conhecido é trocado,
 * do mais longo para o mais curto. Textos que vieram do admin ou dos jogadores (nomes de conquistas, frases,
 * arquivos) ficam entre marcadores e nunca são traduzidos.
 *
 * <p>Para criar outro idioma: copie lang/en.yml, traduza os valores e use idioma: &lt;nome do arquivo&gt;.
 */
public final class Lang {

    /** Marcadores invisíveis em volta de texto do usuário (área de uso privado do Unicode). */
    public static final char KEEP_START = '';
    public static final char KEEP_END = '';
    public static final String PORTUGUESE = "pt-BR";

    private record Entry(String from, String to) {
    }

    private static volatile List<Entry> entries = List.of();
    private static volatile String code = PORTUGUESE;

    private Lang() {
    }

    public static String code() {
        return code;
    }

    public static boolean english() {
        return code.toLowerCase().startsWith("en");
    }

    /**
     * Carrega o idioma. Os arquivos que vêm no plugin são copiados para plugins/SinosDoMundo/lang na primeira vez;
     * traduções que faltarem no arquivo do admin vêm do arquivo do plugin.
     */
    public static void load(Plugin plugin, String language) {
        Logger logger = plugin.getLogger();
        code = language == null || language.isBlank() ? PORTUGUESE : language;
        if (code.equalsIgnoreCase(PORTUGUESE)) {
            entries = List.of();
            return;
        }
        File file = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
        if (!file.exists() && plugin.getResource("lang/" + code + ".yml") != null) {
            plugin.saveResource("lang/" + code + ".yml", false);
        }
        // Traduções do plugin primeiro; as do arquivo do admin valem por cima, trecho a trecho. Assim uma versão
        // nova do plugin traz os trechos novos mesmo para quem já tem o arquivo copiado.
        Map<String, String> pairs = new LinkedHashMap<>();
        try (InputStream in = plugin.getResource("lang/" + code + ".yml")) {
            if (in != null) {
                readPairs(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)), pairs);
            }
        } catch (IOException e) {
            logger.warning("Could not read the bundled language file: " + e.getMessage());
        }
        if (file.isFile()) {
            readPairs(YamlConfiguration.loadConfiguration(file), pairs);
        }
        if (pairs.isEmpty()) {
            logger.warning("Language \"" + code + "\" not found (lang/" + code + ".yml); using Portuguese.");
            code = PORTUGUESE;
            entries = List.of();
            return;
        }
        List<Entry> loaded = new ArrayList<>();
        pairs.forEach((from, to) -> loaded.add(new Entry(from, to)));
        loaded.sort(Comparator.comparingInt((Entry e) -> e.from().length()).reversed());
        entries = List.copyOf(loaded);
    }

    /** Chaves com "." viram seções no YAML do Bukkit; por isso os trechos ficam numa lista de pares. */
    private static void readPairs(YamlConfiguration yaml, Map<String, String> into) {
        for (Map<?, ?> pair : yaml.getMapList("traducoes")) {
            Object from = pair.get("pt");
            Object to = pair.get("tr");
            if (from != null && to != null && !from.toString().isEmpty()) {
                into.put(from.toString(), to.toString());
            }
        }
    }

    /** Para testes: usa estes trechos (português -> tradução) sem ler arquivos. */
    static void useForTests(String language, Map<String, String> translations) {
        code = language;
        List<Entry> loaded = new ArrayList<>();
        translations.forEach((from, to) -> loaded.add(new Entry(from, to)));
        loaded.sort(Comparator.comparingInt((Entry e) -> e.from().length()).reversed());
        entries = List.copyOf(loaded);
    }

    /** Traduz os trechos conhecidos de um texto e tira os marcadores de texto do usuário. */
    public static String t(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        List<Entry> current = entries;
        if (current.isEmpty()) {
            return unmark(text);
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        int i = 0;
        while (i < text.length()) {
            int start = text.indexOf(KEEP_START, i);
            int stop = start < 0 ? text.length() : start;
            out.append(translatePart(text.substring(i, stop), current));
            if (start < 0) {
                break;
            }
            int end = text.indexOf(KEEP_END, start);
            if (end < 0) {
                end = text.length() - 1;
            }
            out.append(text, start + 1, Math.min(end, text.length()));
            i = end + 1;
        }
        return out.toString();
    }

    /** Marca um texto do usuário para não ser traduzido. */
    public static String keep(String userText) {
        return userText == null ? "" : KEEP_START + userText + KEEP_END;
    }

    private static String unmark(String text) {
        return text.indexOf(KEEP_START) < 0 ? text : text.replace(String.valueOf(KEEP_START), "").replace(String.valueOf(KEEP_END), "");
    }

    private static String translatePart(String part, List<Entry> current) {
        if (part.isEmpty()) {
            return part;
        }
        // Trocas já feitas viram marcadores, para uma tradução não ser traduzida de novo por um trecho menor
        List<String> done = new ArrayList<>();
        String work = part;
        for (Entry e : current) {
            if (work.contains(e.from())) {
                work = work.replace(e.from(), "" + done.size() + "");
                done.add(e.to());
            }
        }
        if (done.isEmpty()) {
            return part;
        }
        for (int k = done.size() - 1; k >= 0; k--) {
            work = work.replace("" + k + "", done.get(k));
        }
        return work;
    }
}
