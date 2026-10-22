package br.sinosdomundo.contrib;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anota o dano que cada jogador causou em cada mob acompanhado e decide quem conta como
 * contribuidor quando o mob morre. Não depende do Paper, para poder ser testado sozinho.
 */
public final class ContributorLedger {

    public enum Mode { DANO, PRESENCA, AMBOS }

    /**
     * @param minPercent porcentagem mínima do dano total para contar
     * @param minDamage dano mínimo absoluto para contar (basta atingir um dos dois)
     * @param windowMs só conta dano causado neste intervalo antes da morte
     */
    public record Rules(Mode mode, double minPercent, double minDamage, long windowMs) {
    }

    /** Um jogador: id e nome. */
    public record Participant(UUID id, String name) {
    }

    private record Hit(long timeMs, double damage) {
    }

    private static final class PlayerHits {
        String name;
        final Deque<Hit> hits = new ArrayDeque<>();
    }

    private final Map<UUID, Map<UUID, PlayerHits>> byTarget = new HashMap<>();
    private final Map<UUID, Long> lastHit = new HashMap<>();

    public void record(UUID target, Participant player, double damage, long nowMs) {
        if (damage <= 0) {
            return;
        }
        PlayerHits entry = byTarget.computeIfAbsent(target, t -> new LinkedHashMap<>())
                .computeIfAbsent(player.id(), p -> new PlayerHits());
        entry.name = player.name();
        entry.hits.addLast(new Hit(nowMs, damage));
        lastHit.put(target, nowMs);
    }

    /**
     * Quem conta como contribuidor, do maior dano para o menor. Quem deu o golpe final sempre entra;
     * nos modos de presença, quem estava perto também entra.
     */
    public List<String> contributors(UUID target, Rules rules, long nowMs, Participant killer, List<Participant> present) {
        Map<UUID, Double> damage = new LinkedHashMap<>();
        Map<UUID, String> names = new HashMap<>();
        double total = 0;
        for (Map.Entry<UUID, PlayerHits> e : byTarget.getOrDefault(target, Map.of()).entrySet()) {
            double sum = 0;
            for (Hit hit : e.getValue().hits) {
                if (nowMs - hit.timeMs() <= rules.windowMs()) {
                    sum += hit.damage();
                }
            }
            if (sum > 0) {
                damage.put(e.getKey(), sum);
                names.put(e.getKey(), e.getValue().name);
                total += sum;
            }
        }

        List<UUID> chosen = new ArrayList<>();
        if (rules.mode() != Mode.PRESENCA) {
            double percentCut = total * rules.minPercent() / 100;
            damage.forEach((id, sum) -> {
                if (sum >= rules.minDamage() || sum >= percentCut) {
                    chosen.add(id);
                }
            });
        }
        if (killer != null && !chosen.contains(killer.id())) {
            chosen.add(killer.id());
            names.put(killer.id(), killer.name());
        }
        chosen.sort(Comparator.comparingDouble((UUID id) -> damage.getOrDefault(id, 0.0)).reversed());

        if (rules.mode() != Mode.DANO) {
            present.stream()
                    .filter(p -> !chosen.contains(p.id()))
                    .sorted(Comparator.comparing(Participant::name, String.CASE_INSENSITIVE_ORDER))
                    .forEach(p -> {
                        chosen.add(p.id());
                        names.put(p.id(), p.name());
                    });
        }
        return chosen.stream().map(names::get).toList();
    }

    public void forget(UUID target) {
        byTarget.remove(target);
        lastHit.remove(target);
    }

    /** Apaga mobs que não levam dano há mais de {@code idleMs}. */
    public void purge(long nowMs, long idleMs) {
        List<UUID> stale = new ArrayList<>();
        lastHit.forEach((target, time) -> {
            if (nowMs - time > idleMs) {
                stale.add(target);
            }
        });
        stale.forEach(this::forget);
    }

    public int trackedCount() {
        return byTarget.size();
    }
}
