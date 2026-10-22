package br.sinosdomundo.bell;

import br.sinosdomundo.BellSettings;
import br.sinosdomundo.conquest.Conquest;
import br.sinosdomundo.contrib.ContributorSettings;
import br.sinosdomundo.grouping.BellGrouper;
import br.sinosdomundo.pack.BellModel;
import br.sinosdomundo.voice.SpeechPlanner;
import br.sinosdomundo.voice.SpeechPlanner.Speech;
import br.sinosdomundo.voice.VoiceBank;
import br.sinosdomundo.voice.VoiceSettings;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Dispara anúncios: agrupa os jogadores de cada mundo e coloca um sino por grupo. Anúncios que
 * chegam enquanto outro está tocando entram numa fila.
 */
public final class AnnouncementService {

    /**
     * Um anúncio.
     *
     * @param display nome da conquista no sino e no chat (MiniMessage)
     * @param spoken texto falado no lugar de {conquista}
     * @param phrase frase falada; vazio usa a frase padrão da voz
     * @param closing "aleatoria", "nenhuma" ou um texto próprio
     * @param narrator narrador; vazio usa o ativo
     * @param completedBy nomes de quem concluiu, do maior para o menor dano
     */
    public record Request(String display, String spoken, String phrase, String closing, String narrator,
            List<String> completedBy, Set<UUID> audience) {
        public Request {
            completedBy = List.copyOf(completedBy);
        }

        /** Anúncio para todos os jogadores. */
        public Request(String display, String spoken, String phrase, String closing, String narrator,
                List<String> completedBy) {
            this(display, spoken, phrase, closing, narrator, completedBy, null);
        }

        /** O mesmo anúncio só para estes jogadores (teste do admin): ninguém mais vê, ouve ou lê no chat. */
        public Request onlyFor(Set<UUID> players) {
            return new Request(display, spoken, phrase, closing, narrator, completedBy, Set.copyOf(players));
        }
    }

    private final Plugin plugin;
    private final Supplier<BellSettings> settings;
    private final Supplier<VoiceSettings> voiceSettings;
    private final VoiceBank voiceBank;
    private final Supplier<ContributorSettings> contributorSettings;
    private final Supplier<BellModel> bellModel;
    private final Predicate<Player> hasPack;
    private final Queue<Request> queue = new ArrayDeque<>();
    private final Set<BellAnnouncement> active = new HashSet<>();
    private boolean shuttingDown;

    public AnnouncementService(Plugin plugin, Supplier<BellSettings> settings, Supplier<VoiceSettings> voiceSettings,
            Supplier<ContributorSettings> contributorSettings, VoiceBank voiceBank, Supplier<BellModel> bellModel,
            Predicate<Player> hasPack) {
        this.contributorSettings = contributorSettings;
        this.bellModel = bellModel;
        this.hasPack = hasPack;
        this.plugin = plugin;
        this.settings = settings;
        this.voiceSettings = voiceSettings;
        this.voiceBank = voiceBank;
    }

    /** Anúncio avulso: o texto mostrado também é o falado (palavra por palavra, se não tiver áudio próprio). */
    public void announce(String conquest, List<String> completedBy) {
        String spoken = PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(conquest));
        announce(new Request(conquest, spoken, "", Conquest.RANDOM_CLOSING, "", completedBy));
    }

    public void announce(Request request) {
        queue.add(request);
        if (active.isEmpty()) {
            startNext();
        }
    }

    public void shutdown() {
        shuttingDown = true;
        queue.clear();
        for (BellAnnouncement announcement : new ArrayList<>(active)) {
            announcement.finish();
        }
        active.clear();
    }

    private void startNext() {
        // Laço em vez de recursão: um anúncio sem ninguém para ver o sino passa direto
        while (active.isEmpty() && !queue.isEmpty()) {
            Request next = queue.poll();
            ContributorSettings contributors = contributorSettings.get();
            VoiceSettings voice = voiceSettings.get();
            String closing = chooseClosing(next.closing(), voice);
            BellSettings current = settings.get();
            String names = joinNames(next.completedBy(), contributors.maxShownNames(), current.textConnector(),
                    current.textMore());
            TagResolver placeholders = TagResolver.resolver(
                    Placeholder.parsed("conquista", next.display()),
                    Placeholder.parsed("achievement", next.display()),
                    Placeholder.unparsed("jogadores", names),
                    Placeholder.unparsed("players", names),
                    Placeholder.unparsed("final", closing),
                    Placeholder.unparsed("closing", closing));

            sendChat(current, placeholders, next.audience());
            Component text = render(current.textLines(), placeholders);
            Speech speech = Speech.EMPTY;
            if (voice.enabled()) {
                String phrase = next.phrase().isBlank() ? voice.phrase() : next.phrase();
                String narrator = voice.narratorOrDefault(next.narrator()).name();
                speech = SpeechPlanner.plan(voiceBank, voice, narrator, phrase, next.spoken(), next.completedBy(),
                        contributors.maxSpokenNames(), closing);
            }
            for (World world : Bukkit.getWorlds()) {
                spawnBells(world, current, text, speech, voice.delayTicks(), next.audience());
            }
        }
    }

    private void spawnBells(World world, BellSettings current, Component text, Speech speech, int speechDelay,
            Set<UUID> audience) {
        List<Player> players = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (audience != null && !audience.contains(player.getUniqueId())) {
                continue;
            }
            if (audience == null && current.ignoreSpectators() && player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            players.add(player);
        }
        if (players.isEmpty()) {
            return;
        }

        List<BellGrouper.Point> points = new ArrayList<>(players.size());
        for (int i = 0; i < players.size(); i++) {
            Location at = players.get(i).getLocation();
            points.add(new BellGrouper.Point(i, at.getX(), at.getY(), at.getZ()));
        }

        for (BellGrouper.Group group : BellGrouper.group(points, current.groupRadius())) {
            List<Player> members = group.members().stream().map(p -> players.get(p.id())).toList();
            Player leader = players.get(group.leader().id());
            Location spot = members.size() == 1
                    ? BellPlacement.solo(leader, current)
                    : BellPlacement.group(new Location(world, group.x(), group.y(), group.z()), members, leader, current);

            BellAnnouncement[] holder = new BellAnnouncement[1];
            holder[0] = new BellAnnouncement(plugin, current, bellModel.get(), hasPack, members, leader, text, speech, speechDelay,
                    () -> onBellFinished(holder[0]));
            active.add(holder[0]);
            holder[0].start(spot);
        }
    }

    private void onBellFinished(BellAnnouncement announcement) {
        active.remove(announcement);
        if (!shuttingDown && active.isEmpty()) {
            startNext();
        }
    }

    private void sendChat(BellSettings current, TagResolver placeholders, Set<UUID> audience) {
        if (current.chatLines().isEmpty()) {
            return;
        }
        Component message = render(current.chatLines(), placeholders);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (audience == null || audience.contains(player.getUniqueId())) {
                player.sendMessage(message);
            }
        }
        if (audience == null) {
            Bukkit.getConsoleSender().sendMessage(message);
        }
    }

    private static Component render(List<String> lines, TagResolver placeholders) {
        return MiniMessage.miniMessage().deserialize(String.join("<newline>", lines), placeholders);
    }

    /** Sorteia uma frase da lista, não usa nenhuma, ou usa o texto próprio da conquista. */
    private static String chooseClosing(String mode, VoiceSettings voice) {
        if (mode.equalsIgnoreCase(Conquest.NO_CLOSING)) {
            return "";
        }
        if (mode.isBlank() || mode.equalsIgnoreCase(Conquest.RANDOM_CLOSING)) {
            List<String> list = voice.closings();
            return list.isEmpty() ? "" : list.get(ThreadLocalRandom.current().nextInt(list.size()));
        }
        return mode;
    }

    /**
     * "A", "A e B", "A, B e C"; passando do limite, "A, B e mais 3".
     *
     * @param connector "e" / "and"
     * @param more "e mais {0}" / "and {0} more"
     */
    static String joinNames(List<String> names, int max, String connector, String more) {
        if (names.isEmpty()) {
            return "?";
        }
        if (names.size() > max) {
            return String.join(", ", names.subList(0, max)) + " " + more.replace("{0}", Integer.toString(names.size() - max));
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " " + connector + " " + names.get(names.size() - 1);
    }
}
