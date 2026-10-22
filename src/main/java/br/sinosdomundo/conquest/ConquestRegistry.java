package br.sinosdomundo.conquest;

import br.sinosdomundo.conquest.Conquest.Trigger;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/** Conquistas do config e quais já foram concluídas (salvas em concluidas.yml). */
public final class ConquestRegistry {

    private final File file;
    private final Logger logger;
    private final Map<String, Conquest> conquests = new LinkedHashMap<>();
    private YamlConfiguration completed;

    public ConquestRegistry(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "concluidas.yml");
        this.logger = logger;
        this.completed = YamlConfiguration.loadConfiguration(file);
    }

    public void load(FileConfiguration config) {
        conquests.clear();
        ConfigurationSection section = config.getConfigurationSection("conquistas");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection c = section.getConfigurationSection(id);
            if (c == null) {
                continue;
            }
            Trigger trigger;
            try {
                trigger = Trigger.valueOf(c.getString("gatilho", "MANUAL").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                logger.warning("Conquista " + id + ": gatilho inválido " + c.getString("gatilho"));
                continue;
            }
            String target = switch (trigger) {
                case MATAR_MOB -> c.getString("mob", "").toUpperCase(Locale.ROOT);
                case CONQUISTA -> c.getString("conquista", "");
                case DIMENSAO -> c.getString("mundo", "");
                default -> "";
            };
            if (target.isEmpty() && (trigger == Trigger.MATAR_MOB || trigger == Trigger.CONQUISTA || trigger == Trigger.DIMENSAO)) {
                logger.warning("Conquista " + id + ": falta o alvo do gatilho " + trigger);
                continue;
            }
            String display = c.getString("nome", id);
            conquests.put(id, new Conquest(id, trigger, target, c.getBoolean("uma-vez", true), display,
                    c.getString("voz", ""), c.getString("frase", ""),
                    c.getString("final", Conquest.RANDOM_CLOSING), c.getString("narrador", "")));
        }
    }

    public Optional<Conquest> get(String id) {
        return Optional.ofNullable(conquests.get(id));
    }

    public Collection<Conquest> all() {
        return List.copyOf(conquests.values());
    }

    /** Conquistas de um gatilho que ainda podem ser concluídas. */
    public List<Conquest> available(Trigger trigger) {
        return conquests.values().stream().filter(c -> c.trigger() == trigger && isAvailable(c)).toList();
    }

    public boolean isAvailable(Conquest conquest) {
        return !conquest.once() || !isCompleted(conquest.id());
    }

    public boolean isCompleted(String id) {
        return completed.contains(id);
    }

    public Optional<String> completedAt(String id) {
        return Optional.ofNullable(completed.getString(id + ".data"));
    }

    public List<String> completedBy(String id) {
        return completed.getStringList(id + ".jogadores");
    }

    public void markCompleted(Conquest conquest, List<String> players) {
        completed.set(conquest.id() + ".data", Instant.now().toString());
        completed.set(conquest.id() + ".jogadores", players);
        save();
    }

    public boolean reset(String id) {
        if (!completed.contains(id)) {
            return false;
        }
        completed.set(id, null);
        save();
        return true;
    }

    private void save() {
        try {
            completed.save(file);
        } catch (IOException e) {
            logger.warning("Não foi possível salvar concluidas.yml: " + e.getMessage());
        }
    }
}
