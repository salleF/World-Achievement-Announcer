package br.sinosdomundo.voice;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Seção "voz" do config.yml. */
public record VoiceSettings(
        boolean enabled,
        String python,
        String narrator,
        Map<String, Narrator> narrators,
        String phrase,
        String connector,
        String others,
        List<String> closings,
        List<String> commonWords,
        int delayTicks) {

    /**
     * Uma voz que pode narrar os anúncios.
     *
     * @param engine "piper" ou "kokoro"
     * @param voice só no Kokoro: uma voz ("pf_dora") ou mistura ("pf_dora:0.6,if_sara:0.4")
     * @param kind masculino, feminino ou neutro (só informativo)
     * @param language idioma da pronúncia no Kokoro (pt-br, en-us, en-gb)
     */
    public record Narrator(String name, String engine, File model, File voices, String voice, double speed, String kind,
            String language) {

        /** Argumentos do gerar_voz.py para este narrador. */
        public List<String> arguments() {
            if (engine.equals("kokoro")) {
                return List.of("--motor", "kokoro", "--modelo", model.getAbsolutePath(), "--vozes", voices.getAbsolutePath(),
                        "--voz", voice, "--velocidade", Double.toString(speed), "--idioma", language);
            }
            return List.of("--motor", "piper", "--modelo", model.getAbsolutePath());
        }

        public boolean filesPresent() {
            return model.isFile() && (!engine.equals("kokoro") || voices.isFile());
        }
    }

    public Narrator activeNarrator() {
        return narrators.get(narrator);
    }

    /** O narrador pedido, ou o ativo se o nome estiver vazio ou não existir. */
    public Narrator narratorOrDefault(String name) {
        return name != null && narrators.containsKey(name) ? narrators.get(name) : activeNarrator();
    }

    public static VoiceSettings load(FileConfiguration config, File dataFolder, Logger logger) {
        File kokoroModel = file(dataFolder, config.getString("voz.kokoro-modelo", "vozes/kokoro-v1.0.int8.onnx"));
        File kokoroVoices = file(dataFolder, config.getString("voz.kokoro-vozes", "vozes/voices-v1.0.bin"));

        Map<String, Narrator> narrators = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("voz.narradores");
        if (section != null) {
            for (String name : section.getKeys(false)) {
                ConfigurationSection n = section.getConfigurationSection(name);
                if (n == null) {
                    continue;
                }
                String engine = n.getString("motor", "piper").toLowerCase(Locale.ROOT);
                File model = engine.equals("kokoro")
                        ? kokoroModel
                        : file(dataFolder, n.getString("modelo", "vozes/pt_BR-" + name + "-medium.onnx"));
                narrators.put(name, new Narrator(name, engine, model, kokoroVoices, n.getString("voz", ""),
                        n.getDouble("velocidade", 1.0), n.getString("tipo", ""), n.getString("idioma", "pt-br")));
            }
        }

        String narrator = config.getString("voz.narrador", "cadu");
        if (!narrators.containsKey(narrator) && !narrators.isEmpty()) {
            String first = narrators.keySet().iterator().next();
            logger.warning("voz.narrador \"" + narrator + "\" não existe em voz.narradores; usando " + first);
            narrator = first;
        }
        return new VoiceSettings(
                config.getBoolean("voz.ativada", true) && !narrators.isEmpty(),
                config.getString("voz.python", "python"),
                narrator,
                narrators,
                config.getString("voz.frase", "Alerta, alerta! {conquista}. Seus contribuidores foram {jogadores}. {final}"),
                config.getString("voz.conector", "e"),
                config.getString("voz.e-outros", "e outros jogadores"),
                List.copyOf(config.getStringList("voz.finais")),
                List.copyOf(config.getStringList("voz.palavras-comuns")),
                Math.max(0, config.getInt("voz.atraso-ticks", 20)));
    }

    private static File file(File dataFolder, String path) {
        File f = new File(path);
        return f.isAbsolute() ? f : new File(dataFolder, path);
    }
}
