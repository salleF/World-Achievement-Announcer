package br.sinosdomundo.conquest;

import br.sinosdomundo.api.ConquestAnnounceEvent;
import br.sinosdomundo.bell.AnnouncementService;
import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Conclui conquistas: confere se ainda valem, avisa outros plugins, salva e manda tocar o sino. */
public final class ConquestService {

    private final ConquestRegistry registry;
    private final AnnouncementService announcements;
    private final NamespacedKey markKey;

    public ConquestService(Plugin plugin, ConquestRegistry registry, AnnouncementService announcements) {
        this.registry = registry;
        this.announcements = announcements;
        this.markKey = new NamespacedKey(plugin, "conquista");
    }

    public ConquestRegistry registry() {
        return registry;
    }

    /** @return false se a conquista já foi concluída (uma-vez) ou se outro plugin cancelou */
    public boolean complete(Conquest conquest, List<String> contributors) {
        if (!registry.isAvailable(conquest) || contributors.isEmpty()) {
            return false;
        }
        ConquestAnnounceEvent event = new ConquestAnnounceEvent(conquest.id(), conquest.display(), contributors);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getContributors().isEmpty()) {
            return false;
        }
        List<String> names = List.copyOf(event.getContributors());
        registry.markCompleted(conquest, names);
        announcements.announce(new AnnouncementService.Request(conquest.display(), conquest.spoken(), conquest.phrase(),
                conquest.closing(), conquest.narrator(), names));
        return true;
    }

    /** Marca um mob: quando ele morrer, a conquista é concluída com quem ajudou. */
    public void mark(LivingEntity entity, Conquest conquest) {
        entity.getPersistentDataContainer().set(markKey, PersistentDataType.STRING, conquest.id());
        entity.setRemoveWhenFarAway(false);
    }

    public boolean unmark(Entity entity) {
        if (!entity.getPersistentDataContainer().has(markKey)) {
            return false;
        }
        entity.getPersistentDataContainer().remove(markKey);
        return true;
    }

    public Optional<Conquest> markedConquest(Entity entity) {
        String id = entity.getPersistentDataContainer().get(markKey, PersistentDataType.STRING);
        return id == null ? Optional.empty() : registry.get(id);
    }
}
