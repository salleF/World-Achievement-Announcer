package br.sinosdomundo.conquest;

import br.sinosdomundo.conquest.Conquest.Trigger;
import br.sinosdomundo.contrib.ContributorLedger;
import br.sinosdomundo.contrib.ContributorLedger.Participant;
import br.sinosdomundo.contrib.ContributorSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ComplexEntityPart;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;

/** Detecta as conquistas: morte de mob (com contribuidores), mob marcado, advancement e dimensão. */
public final class TriggerListener implements Listener {

    private static final long IGNITER_MEMORY_MS = 30_000;
    private static final long IDLE_TARGET_MS = 10 * 60_000;

    private record Credit(Participant player, long timeMs) {
    }

    private final Plugin plugin;
    private final ConquestService conquests;
    private final Supplier<ContributorSettings> settings;
    private final ContributorLedger ledger = new ContributorLedger();
    /** Quem quebrou cada cristal do End, e quem colocou fogo em cada mob. */
    private final Map<UUID, Credit> crystalBreakers = new HashMap<>();
    private final Map<UUID, Credit> igniters = new HashMap<>();
    /** Conquistas de dimensão esperando a janela fechar, com quem já entrou. */
    private final Map<String, List<String>> dimensionWindows = new HashMap<>();

    public TriggerListener(Plugin plugin, ConquestService conquests, Supplier<ContributorSettings> settings) {
        this.plugin = plugin;
        this.conquests = conquests;
        this.settings = settings;
    }

    /** Limpa registros de mobs que pararam de levar dano. Chamar a cada minuto. */
    public void purge() {
        long now = System.currentTimeMillis();
        ledger.purge(now, IDLE_TARGET_MS);
        crystalBreakers.values().removeIf(c -> now - c.timeMs() > IGNITER_MEMORY_MS);
        igniters.values().removeIf(c -> now - c.timeMs() > IGNITER_MEMORY_MS);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (victim instanceof EnderCrystal crystal) {
            responsible(event.getDamager()).ifPresent(p -> crystalBreakers.put(crystal.getUniqueId(),
                    new Credit(p, System.currentTimeMillis())));
            return;
        }
        LivingEntity target = trackedTarget(victim);
        if (target == null) {
            return;
        }
        responsible(event.getDamager()).ifPresent(p -> record(target, p, event.getFinalDamage()));
    }

    /** Dano de fogo vai para quem colocou fogo (Aspecto Flamejante, flecha em chamas). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFireDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.FIRE_TICK
                && event.getCause() != EntityDamageEvent.DamageCause.FIRE) {
            return;
        }
        LivingEntity target = trackedTarget(event.getEntity());
        Credit credit = target == null ? null : igniters.get(target.getUniqueId());
        if (credit != null) {
            record(target, credit.player(), event.getFinalDamage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        LivingEntity target = trackedTarget(event.getEntity());
        if (target != null) {
            responsible(event.getCombuster()).ifPresent(p -> igniters.put(target.getUniqueId(),
                    new Credit(p, System.currentTimeMillis())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Optional<Conquest> conquest = conquests.markedConquest(entity)
                .or(() -> killConquest(entity));
        UUID id = entity.getUniqueId();
        if (conquest.isPresent()) {
            ContributorSettings current = settings.get();
            Player killer = entity.getKiller();
            Participant killerP = killer != null && counts(killer) ? new Participant(killer.getUniqueId(), killer.getName()) : null;
            List<Participant> present = new ArrayList<>();
            double radiusSquared = current.presenceRadius() * current.presenceRadius();
            for (Player p : entity.getWorld().getPlayers()) {
                if (counts(p) && p.getLocation().distanceSquared(entity.getLocation()) <= radiusSquared) {
                    present.add(new Participant(p.getUniqueId(), p.getName()));
                }
            }
            List<String> names = ledger.contributors(id, current.rules(), System.currentTimeMillis(), killerP, present);
            conquests.complete(conquest.get(), names);
        }
        ledger.forget(id);
        igniters.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        if (!counts(player)) {
            return;
        }
        String key = event.getAdvancement().getKey().toString();
        for (Conquest conquest : conquests.registry().available(Trigger.CONQUISTA)) {
            if (conquest.target().equalsIgnoreCase(key)) {
                conquests.complete(conquest, List.of(player.getName()));
            }
        }
    }

    /** Primeira entrada numa dimensão: espera uns segundos para incluir quem entrou junto. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!counts(player)) {
            return;
        }
        String world = player.getWorld().getName();
        for (Conquest conquest : conquests.registry().available(Trigger.DIMENSAO)) {
            if (!conquest.target().equalsIgnoreCase(world)) {
                continue;
            }
            List<String> window = dimensionWindows.get(conquest.id());
            if (window != null) {
                if (!window.contains(player.getName())) {
                    window.add(player.getName());
                }
                continue;
            }
            List<String> names = new ArrayList<>(List.of(player.getName()));
            dimensionWindows.put(conquest.id(), names);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                dimensionWindows.remove(conquest.id());
                conquests.complete(conquest, names);
            }, settings.get().dimensionWindowTicks());
        }
    }

    private void record(LivingEntity target, Participant player, double damage) {
        // Não conta dano além da vida que o mob ainda tinha
        double remaining = target.getHealth() + target.getAbsorptionAmount();
        ledger.record(target.getUniqueId(), player, Math.min(damage, remaining), System.currentTimeMillis());
    }

    /** O mob que deve ter o dano anotado (a parte do Ender Dragon conta como o dragão), ou null. */
    private LivingEntity trackedTarget(Entity entity) {
        if (entity instanceof ComplexEntityPart part) {
            entity = part.getParent();
        }
        if (!(entity instanceof LivingEntity living) || entity instanceof Player) {
            return null;
        }
        boolean tracked = conquests.markedConquest(living).isPresent() || killConquest(living).isPresent();
        return tracked ? living : null;
    }

    private Optional<Conquest> killConquest(LivingEntity entity) {
        String type = entity.getType().name();
        return conquests.registry().available(Trigger.MATAR_MOB).stream()
                .filter(c -> c.target().equals(type))
                .findFirst();
    }

    /** Quem recebe o crédito por um dano: o jogador, quem atirou, quem acendeu a TNT, o dono do pet... */
    private Optional<Participant> responsible(Entity damager) {
        Player player = null;
        if (damager instanceof Player p) {
            player = p;
        } else if (damager instanceof Projectile projectile) {
            player = asPlayer(projectile.getShooter());
        } else if (damager instanceof TNTPrimed tnt) {
            player = tnt.getSource() instanceof Player p ? p : null;
        } else if (damager instanceof AreaEffectCloud cloud) {
            player = asPlayer(cloud.getSource());
        } else if (damager instanceof Tameable pet && pet.getOwner() != null) {
            AnimalTamer owner = pet.getOwner();
            player = Bukkit.getPlayer(owner.getUniqueId());
        } else if (damager instanceof EnderCrystal crystal) {
            Credit credit = crystalBreakers.get(crystal.getUniqueId());
            return credit == null ? Optional.empty() : Optional.of(credit.player());
        }
        if (player == null || !counts(player)) {
            return Optional.empty();
        }
        return Optional.of(new Participant(player.getUniqueId(), player.getName()));
    }

    private static Player asPlayer(ProjectileSource source) {
        return source instanceof Player p ? p : null;
    }

    private boolean counts(Player player) {
        GameMode mode = player.getGameMode();
        return !settings.get().ignoreCreative() || (mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR);
    }
}
