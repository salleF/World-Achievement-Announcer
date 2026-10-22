package br.sinosdomundo;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;

/** Valores do config.yml, já validados. */
public record BellSettings(
        double groupRadius,
        int durationTicks,
        double heightAbove,
        double soloDistance,
        double soloHeight,
        boolean followGroup,
        float scale,
        Material item,
        NamespacedKey itemModel,
        boolean model3d,
        int rings,
        int ringInterval,
        List<Key> ringSounds,
        Key finalSound,
        float volume,
        float pitch,
        boolean ignoreSpectators,
        List<String> textLines,
        double textHeight,
        List<String> chatLines,
        boolean musicEnabled,
        float musicVolume,
        List<Integer> rhythm,
        int musicStartTicks,
        String textConnector,
        String textMore) {

    /** Som da música de fundo no resource pack do plugin. */
    public static final String MUSIC_SOUND = "sinos:musica.conquista";

    public static BellSettings load(FileConfiguration config, Logger logger) {
        Material item = Material.matchMaterial(config.getString("sino.item", "BELL"));
        if (item == null || !item.isItem()) {
            logger.warning("sino.item inválido, usando BELL");
            item = Material.BELL;
        }

        String modelId = config.getString("sino.item-model", "");
        NamespacedKey itemModel = modelId.isBlank() ? null : NamespacedKey.fromString(modelId);
        if (!modelId.isBlank() && itemModel == null) {
            logger.warning("sino.item-model inválido: " + modelId);
        }

        List<Key> ringSounds = new ArrayList<>();
        for (String id : config.getStringList("sino.sons")) {
            Key key = parseKey(id, logger);
            if (key != null) {
                ringSounds.add(key);
            }
        }
        String finalId = config.getString("sino.som-final", "");
        Key finalSound = finalId.isBlank() ? null : parseKey(finalId, logger);

        return new BellSettings(
                Math.max(1, config.getDouble("sino.raio-grupo", 24)),
                Math.max(1, config.getInt("sino.duracao-segundos", 8)) * 20,
                config.getDouble("sino.altura-acima", 4),
                config.getDouble("sino.sozinho-distancia", 5),
                config.getDouble("sino.sozinho-altura", 3),
                config.getBoolean("sino.seguir-grupo", true),
                (float) Math.max(0.1, config.getDouble("sino.escala", 2)),
                item,
                itemModel,
                config.getBoolean("sino.modelo-3d", true),
                Math.max(0, config.getInt("sino.badaladas", 3)),
                Math.max(1, config.getInt("sino.intervalo-badaladas", 8)),
                List.copyOf(ringSounds),
                finalSound,
                (float) config.getDouble("sino.volume", 1),
                (float) config.getDouble("sino.tom", 0.8),
                config.getBoolean("sino.ignorar-espectadores", true),
                List.copyOf(config.getStringList("texto.linhas")),
                config.getDouble("texto.altura-acima-do-sino", 1.6),
                List.copyOf(config.getStringList("texto.chat")),
                config.getBoolean("musica.ativada", true),
                (float) Math.max(0, Math.min(1, config.getDouble("musica.volume", 0.45))),
                config.getIntegerList("sino.ritmo").stream().sorted().toList(),
                Math.max(1, config.getInt("musica.inicio-ticks", 1)),
                config.getString("texto.conector", "e"),
                config.getString("texto.e-mais", "e mais {0}"));
    }

    /**
     * Volume que faz o som chegar a todos do grupo. No Minecraft um som é ouvido até 16 blocos vezes
     * o volume, então o raio do grupo define um mínimo.
     */
    public float effectiveVolume() {
        return Math.max(volume, (float) ((groupRadius + 8) / 16));
    }

    private static Key parseKey(String id, Logger logger) {
        try {
            return Key.key(id);
        } catch (RuntimeException e) {
            logger.warning("Som inválido no config: " + id);
            return null;
        }
    }
}
