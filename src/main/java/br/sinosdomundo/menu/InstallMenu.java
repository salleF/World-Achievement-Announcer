package br.sinosdomundo.menu;

import br.sinosdomundo.SinosDoMundo;
import br.sinosdomundo.voice.PythonHealth;
import br.sinosdomundo.voice.VoiceInstaller;
import br.sinosdomundo.voice.VoiceSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Instalação guiada da voz em 4 passos: Python, módulos, modelos e gerar/testar. */
public final class InstallMenu extends Menu {

    public InstallMenu(SinosDoMundo plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected String title() {
        return "<aqua>Instalação da voz";
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void render() {
        PythonHealth health = plugin.health();
        VoiceInstaller installer = plugin.installer();
        VoiceSettings.Narrator active = plugin.voiceSettings().activeNarrator();
        boolean modules = health.modules().containsAll(List.of("kokoro_onnx", "soundfile", "numpy"));
        boolean models = plugin.voiceSettings().narrators().values().stream()
                .noneMatch(n -> n.engine().equals("kokoro") && !n.filesPresent());
        boolean speaks = active != null && health.canSpeak(active);
        String busy = installer.busy() ? "<gold>Instalação em andamento... acompanhe no chat." : "";

        List<String> header = new ArrayList<>();
        header.add(speaks ? "<green>A voz está funcionando." : "<gold>A voz ainda não funciona.");
        header.add("<gray>Siga os passos da esquerda para a direita,");
        header.add("<gray>ou use <white>Fazer tudo</white> embaixo.");
        header.add("<gray>Sem Python, o plugin funciona sem a voz.");
        if (!busy.isEmpty()) {
            header.add(busy);
        }
        icon(4, Items.item(speaks ? Material.LIME_BANNER : Material.ORANGE_BANNER, "<aqua>Instalação guiada", header));

        button(10, Items.item(health.pythonFound() ? Material.LIME_CONCRETE : Material.RED_CONCRETE, "<yellow>1. Python",
                health.pythonFound() ? "<green>" + Items.message(health.detail()) : "<red>" + Items.message(health.detail()),
                "<gray>Comando: <white>" + Items.plain(plugin.voiceSettings().python()), "",
                "<gray>Sem Python? Instale o Python 3.10+", "<gray>(python.org, ou python3 no Linux).", "",
                "<yellow>Clique para procurar de novo"), click -> step(() -> {
                    say("Procurando o Python...");
                    plugin.installer().detectPython(plugin.voiceSettings().python()).thenAccept(found -> sync(() -> {
                        if (found == null) {
                            say("<red>Nenhum Python encontrado.</red> Instale o Python 3.10+ e clique de novo.");
                            return;
                        }
                        plugin.getConfig().set("voz.python", found);
                        plugin.saveAndApply(true);
                        say("<green>Python encontrado:</green> " + Items.plain(found));
                    }));
                }));

        List<String> moduleLore = new ArrayList<>();
        moduleLore.add(modules ? "<green>kokoro-onnx, soundfile e numpy instalados" : "<red>Faltam módulos do Python");
        moduleLore.add("<gray>Instala num ambiente só do plugin:");
        moduleLore.add("<white>plugins/WorldAchievementAnnouncer/python-env");
        moduleLore.add("<gray>(baixa uns 150 MB da internet, PyPI)");
        moduleLore.add("");
        moduleLore.add(health.pythonFound() ? "<yellow>Clique para instalar" : "<gray>Faça o passo 1 antes");
        button(12, Items.item(modules ? Material.LIME_CONCRETE : Material.ORANGE_CONCRETE, "<yellow>2. Módulos", moduleLore),
                click -> {
                    if (!health.pythonFound()) {
                        say("<red>Faça o passo 1 antes (Python).");
                        return;
                    }
                    new ConfirmMenu(plugin, viewer, "Instalar os módulos?", "Cria o ambiente Python do plugin e baixa "
                            + "kokoro-onnx, soundfile e numpy (uns 150 MB). Pode levar alguns minutos.", () -> step(() ->
                            plugin.installer().installModules(plugin.voiceSettings().python(), progress(viewer, plugin))
                                    .whenComplete((python, error) -> sync(() -> {
                                        if (error != null) {
                                            say("<red>Falhou:</red> " + Items.message(root(error)));
                                            return;
                                        }
                                        plugin.getConfig().set("voz.python", python);
                                        plugin.saveAndApply(true);
                                        say("<green>Módulos instalados.</green> Agora o passo 3.");
                                    }))), this::open).open();
                });

        long total = VoiceInstaller.KOKORO_FILES.stream().mapToLong(VoiceInstaller.ModelFile::size).sum() / 1_000_000;
        button(14, Items.item(models ? Material.LIME_CONCRETE : Material.ORANGE_CONCRETE, "<yellow>3. Modelos de voz",
                models ? "<green>Modelos do Kokoro encontrados" : "<red>Modelos do Kokoro não encontrados",
                "<gray>Baixa " + total + " MB do GitHub para", "<white>plugins/WorldAchievementAnnouncer/vozes",
                "<gray>e confere cada arquivo (SHA-256).", "", models ? "<gray>Nada a fazer" : "<yellow>Clique para baixar"),
                click -> {
                    if (models) {
                        return;
                    }
                    new ConfirmMenu(plugin, viewer, "Baixar os modelos?", "Baixa " + total + " MB do GitHub "
                            + "(thewh1teagle/kokoro-onnx) para plugins/WorldAchievementAnnouncer/vozes.", () -> step(() ->
                            plugin.downloadModelsIfNeeded(progress(viewer, plugin)).whenComplete((v, error) -> sync(() -> {
                                if (error != null) {
                                    say("<red>Falhou:</red> " + Items.message(root(error)));
                                } else {
                                    plugin.saveAndApply(true);
                                    say("<green>Modelos prontos.</green> Agora o passo 4.");
                                }
                            }))), this::open).open();
                });

        button(16, Items.item(speaks ? Material.BELL : Material.GRAY_DYE, "<yellow>4. Gerar falas e testar",
                "<gray>Gera as falas do narrador ativo e toca", "<gray>uma amostra só para você.", "",
                speaks ? "<yellow>Clique para gerar e ouvir" : "<gray>Faça os passos anteriores"), click -> {
            if (!speaks) {
                say("<red>Ainda falta:</red> " + Items.message(active == null ? "um narrador"
                        : String.join("; ", health.missingFor(active))));
                return;
            }
            viewer.closeInventory();
            say("Gerando as falas...");
            plugin.saveAndApply(true).thenRun(() -> sync(() -> {
                if (plugin.pack().isOutdated(viewer)) {
                    plugin.pack().send(viewer);
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!plugin.playSample(viewer, active)) {
                        say("<yellow>Falas geradas. Aceite o pacote para ouvir a amostra.");
                    }
                }, 80);
            }));
        });

        button(22, Items.item(Material.NETHER_STAR, "<green>Fazer tudo",
                "<gray>Faz os 4 passos em sequência e mostra", "<gray>o progresso no chat."), click ->
                new ConfirmMenu(plugin, viewer, "Instalar a voz?", "Procura o Python, instala os módulos que faltarem (uns "
                        + "150 MB) e baixa os modelos que faltarem (" + total + " MB). Pode levar alguns minutos.",
                        () -> step(() -> plugin.installVoice(progress(viewer, plugin))), this::open).open());

        button(31, Items.back(), click -> new VoiceMenu(plugin, viewer).open());
    }

    /** Começa um passo, se nenhum outro estiver rodando. */
    private void step(Runnable action) {
        viewer.closeInventory();
        if (plugin.installer().busy()) {
            say("<yellow>Já tem uma instalação em andamento; espere ela terminar.");
            return;
        }
        action.run();
    }

    private void say(String message) {
        viewer.sendMessage(Items.text("<aqua>[Voz]</aqua> <gray>" + message));
    }

    private void sync(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    /** Mensagens de progresso, mandadas da thread principal para quem pediu (jogador ou console). */
    public static Consumer<String> progress(CommandSender to, SinosDoMundo plugin) {
        return message -> Bukkit.getScheduler().runTask(plugin, () ->
                to.sendMessage(Items.text("<aqua>[Voz]</aqua> <gray>" + Items.message(message))));
    }

    private static String root(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
