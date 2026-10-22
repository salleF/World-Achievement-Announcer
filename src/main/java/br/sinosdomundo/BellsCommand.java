package br.sinosdomundo;

import br.sinosdomundo.conquest.Conquest;
import br.sinosdomundo.conquest.ConquestRegistry;
import br.sinosdomundo.menu.InstallMenu;
import br.sinosdomundo.menu.MainMenu;
import br.sinosdomundo.voice.VoiceSettings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /sinos (abre o menu) | teste | anunciar | marcar | desmarcar | resetar | lista | narrador | reload. */
final class BellsCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("teste", "anunciar", "marcar", "desmarcar", "resetar", "lista", "narrador", "instalar-voz", "idioma", "reload");
    private static final int MARK_DISTANCE = 20;

    private final SinosDoMundo plugin;

    BellsCommand(SinosDoMundo plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                new MainMenu(plugin, player).open();
                return true;
            }
            return false;
        }
        ConquestRegistry registry = plugin.conquests().registry();
        switch (args[0].toLowerCase()) {
            case "teste" -> {
                // /sinos teste [nome da conquista] [por=Nome1,Nome2,...]
                List<String> words = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
                List<String> completedBy = List.of(sender.getName());
                if (!words.isEmpty() && words.get(words.size() - 1).startsWith("por=")) {
                    completedBy = names(words.remove(words.size() - 1).substring("por=".length()));
                }
                String conquest = words.isEmpty() ? "Conquista de teste" : String.join(" ", words);
                plugin.announcements().announce(conquest, completedBy);
                say(sender, "Anúncio de teste disparado.");
            }
            case "anunciar" -> {
                // /sinos anunciar <conquista> [Nome1,Nome2,...]
                Optional<Conquest> conquest = conquestArg(sender, args);
                if (conquest.isEmpty()) {
                    return true;
                }
                if (!registry.isAvailable(conquest.get())) {
                    say(sender, "Essa conquista já foi concluída. Use /sinos resetar " + conquest.get().id() + " antes.");
                    return true;
                }
                List<String> completedBy = args.length > 2 ? names(args[2]) : List.of(sender.getName());
                boolean done = plugin.conquests().complete(conquest.get(), completedBy);
                say(sender, done ? "Conquista anunciada." : "O anúncio foi cancelado por outro plugin.");
            }
            case "marcar" -> {
                if (!(sender instanceof Player player)) {
                    say(sender, "Só jogadores podem marcar mobs.");
                    return true;
                }
                Optional<Conquest> conquest = conquestArg(sender, args);
                if (conquest.isEmpty()) {
                    return true;
                }
                Entity target = player.getTargetEntity(MARK_DISTANCE);
                if (!(target instanceof LivingEntity living) || target instanceof Player) {
                    say(sender, "Olhe para um mob a até " + MARK_DISTANCE + " blocos.");
                    return true;
                }
                plugin.conquests().mark(living, conquest.get());
                say(sender, "Mob marcado: quando ele morrer, a conquista " + conquest.get().id() + " toca o sino.");
            }
            case "desmarcar" -> {
                if (!(sender instanceof Player player)) {
                    say(sender, "Só jogadores podem desmarcar mobs.");
                    return true;
                }
                Entity target = player.getTargetEntity(MARK_DISTANCE);
                boolean removed = target != null && plugin.conquests().unmark(target);
                say(sender, removed ? "Marca removida." : "Esse mob não tem marca.");
            }
            case "resetar" -> {
                Optional<Conquest> conquest = conquestArg(sender, args);
                if (conquest.isPresent()) {
                    boolean reset = registry.reset(conquest.get().id());
                    say(sender, reset ? "Conquista liberada de novo." : "Essa conquista ainda não foi concluída.");
                }
            }
            case "lista" -> {
                if (registry.all().isEmpty()) {
                    say(sender, "Nenhuma conquista no config.");
                }
                for (Conquest c : registry.all()) {
                    String status = registry.isCompleted(c.id())
                            ? "concluída por " + String.join(", ", registry.completedBy(c.id()))
                            : "disponível";
                    say(sender, "- " + c.id() + " (" + c.trigger() + (c.target().isEmpty() ? "" : " " + c.target())
                            + ", " + (c.once() ? "uma vez" : "repete") + "): " + status);
                }
            }
            case "narrador" -> {
                VoiceSettings voice = plugin.voiceSettings();
                if (args.length < 2) {
                    say(sender, "Narrador ativo: " + voice.narrator() + ". Disponíveis:");
                    for (VoiceSettings.Narrator n : voice.narrators().values()) {
                        say(sender, "- " + n.name() + (n.kind().isEmpty() ? "" : " (" + n.kind() + ")")
                                + (n.filesPresent() ? "" : " [arquivos de voz não encontrados]"));
                    }
                    return true;
                }
                VoiceSettings.Narrator chosen = voice.narrators().get(args[1]);
                if (chosen == null) {
                    say(sender, "Narrador não encontrado: " + args[1] + ". Veja com /sinos narrador.");
                    return true;
                }
                say(sender, "Gerando as falas do narrador " + chosen.name() + "...");
                plugin.setNarrator(chosen.name()).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> say(sender, 
                        "Narrador trocado para " + chosen.name() + ". O pacote foi reenviado para quem está online.")));
            }
            case "instalar-voz" -> {
                // /sinos instalar-voz mostra o que vai ser feito; /sinos instalar-voz confirmar instala
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirmar")) {
                    say(sender, "Instalação da voz: procura o Python, cria um ambiente Python em "
                            + "plugins/WorldAchievementAnnouncer/python-env e instala kokoro-onnx, soundfile e numpy (uns 150 MB, se "
                            + "faltarem) e baixa os modelos do Kokoro (uns 120 MB, se faltarem).");
                    say(sender, "Para continuar: /sinos instalar-voz confirmar");
                    return true;
                }
                if (plugin.installer().busy()) {
                    say(sender, "Já tem uma instalação em andamento.");
                    return true;
                }
                plugin.installVoice(InstallMenu.progress(sender, plugin));
            }
            case "idioma", "language" -> {
                if (args.length < 2) {
                    say(sender, "Idioma atual: " + Lang.code() + ". Use /sinos idioma pt-BR ou /sinos idioma en "
                            + "(ou o nome de um arquivo da pasta lang).");
                    return true;
                }
                plugin.getConfig().set("idioma", args[1]);
                plugin.saveAndApply(false);
                say(sender, "Idioma dos menus e mensagens: " + Lang.code() + ". Os textos dos anúncios ficam no config.yml.");
            }
            case "reload" -> {
                plugin.reloadSettings();
                say(sender, "Config do World Achievement Announcer recarregado.");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void say(CommandSender sender, String message) {
        sender.sendMessage(Lang.t(message));
    }

    private Optional<Conquest> conquestArg(CommandSender sender, String[] args) {
        if (args.length < 2) {
            say(sender, "Informe o id da conquista. Veja com /sinos lista.");
            return Optional.empty();
        }
        Optional<Conquest> conquest = plugin.conquests().registry().get(args[1]);
        if (conquest.isEmpty()) {
            say(sender, "Conquista não encontrada: " + args[1]);
        }
        return conquest;
    }

    private static List<String> names(String commaSeparated) {
        return Arrays.stream(commaSeparated.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("idioma") || args[0].equalsIgnoreCase("language"))) {
            return List.of("pt-BR", "en").stream().filter(l -> l.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("narrador")) {
            return plugin.voiceSettings().narrators().keySet().stream().filter(n -> n.startsWith(args[1])).toList();
        }
        if (args.length == 2 && List.of("anunciar", "marcar", "resetar").contains(args[0].toLowerCase())) {
            return plugin.conquests().registry().all().stream()
                    .map(Conquest::id)
                    .filter(id -> id.startsWith(args[1]))
                    .toList();
        }
        return List.of();
    }
}
