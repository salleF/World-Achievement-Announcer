package br.sinosdomundo.menu;

import br.sinosdomundo.Lang;
import br.sinosdomundo.SinosDoMundo;
import br.sinosdomundo.voice.VoiceSettings;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Voz e narrador: ligar a voz, escolher e ouvir narradores, frase padrão, frases finais e geração dos áudios. */
public final class VoiceMenu extends Menu {

    private static final int[] NARRATOR_SLOTS = {19, 20, 21, 22, 23, 24, 25};

    public VoiceMenu(SinosDoMundo plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected String title() {
        return "<aqua>Voz e narrador";
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void render() {
        VoiceSettings voice = plugin.voiceSettings();
        List<String> status = new ArrayList<>();
        status.add("<gray>Narrador ativo: <white>" + voice.narrator());
        status.add("<gray>Áudios gerados: <white>" + plugin.voiceBank().size());
        status.add("<gray>Python: " + (plugin.health().pythonFound() ? "<green>" : "<red>") + Items.message(plugin.health().detail()));
        VoiceSettings.Narrator current = voice.activeNarrator();
        if (current != null && !plugin.health().canSpeak(current)) {
            status.add("");
            status.add("<gold>A voz não funciona agora. Falta:");
            for (String missing : plugin.health().missingFor(current)) {
                status.addAll(Items.wrap("<red>- ", Lang.t(missing), 40));
            }
            status.add("<gray>O sino, a música e o texto continuam.");
        }
        icon(4, Items.item(Material.MUSIC_DISC_OTHERSIDE, "<aqua>Voz", status));

        button(10, Items.toggle(plugin.getConfig().getBoolean("voz.ativada", true), "<yellow>Voz dos anúncios",
                "<gray>Desligada, o sino só toca e mostra o texto."), click -> {
            plugin.getConfig().set("voz.ativada", !plugin.getConfig().getBoolean("voz.ativada", true));
            apply(true);
        });

        icon(13, Items.item(Material.OAK_SIGN, "<yellow>Narradores (linha de baixo)",
                "<yellow>Clique: <gray>ativar este narrador", "<yellow>Shift + clique: <gray>ouvir uma amostra só para você",
                "", "<gray>Ativar gera as falas dele (o Kokoro leva", "<gray>cerca de 1 minuto) e reenvia o pacote."));
        int i = 0;
        for (VoiceSettings.Narrator n : voice.narrators().values()) {
            if (i >= NARRATOR_SLOTS.length) {
                break;
            }
            boolean active = n.name().equals(voice.narrator());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Tipo: <white>" + (n.kind().isEmpty() ? "-" : n.kind()));
            lore.add("<gray>Motor: <white>" + n.engine() + (n.voice().isEmpty() ? "" : " <dark_gray>(" + n.voice() + ")"));
            if (!n.filesPresent()) {
                lore.add("<red>Arquivos de voz não encontrados");
            }
            lore.add("");
            lore.add(active ? "<green>Narrador ativo" : "<yellow>Clique para ativar");
            lore.add("<yellow>Shift + clique para ouvir");
            button(NARRATOR_SLOTS[i++], Items.item(active ? Material.DRAGON_HEAD : Material.PLAYER_HEAD,
                    (active ? "<green>" : "<white>") + n.name(), lore), click -> narratorClick(n, click));
        }

        List<String> phraseLore = new ArrayList<>(Items.wrap("<white>", voice.phrase(), 40));
        phraseLore.add("<gray>Use {conquista}, {jogadores} e {final}.");
        phraseLore.add("");
        phraseLore.add("<yellow>Clique para editar");
        button(28, Items.item(Material.WRITABLE_BOOK, "<yellow>Frase padrão", phraseLore),
                click -> askText("Frase padrão dos anúncios:", voice.phrase(), v -> {
                    plugin.getConfig().set("voz.frase", v);
                    apply(true);
                }));

        button(29, Items.item(Material.BOOKSHELF, "<yellow>Frases finais <gray>(" + voice.closings().size() + ")",
                "<gray>Sorteadas depois dos nomes.", "", "<yellow>Clique para ver e editar"),
                click -> new StringListMenu(plugin, viewer, "<aqua>Frases finais", "voz.finais", true,
                        "Uma nova lenda acaba de nascer.", () -> new VoiceMenu(plugin, viewer).open()).open());

        int delay = plugin.getConfig().getInt("voz.atraso-ticks", 20);
        button(30, Items.item(Material.CLOCK, "<yellow>Atraso da fala: <white>" + delay + " ticks",
                "<gray>Tempo entre a última batida e a fala", "<gray>(20 ticks = 1 segundo).", "",
                "<yellow>Esquerdo +1, direito -1, Shift ±10"), click -> {
            plugin.getConfig().set("voz.atraso-ticks", Math.max(0, delay + click.step()));
            apply(false);
        });

        button(31, Items.item(Material.EXPERIENCE_BOTTLE, "<green>Gerar áudios que faltam",
                "<gray>Gera as falas que ainda não existem", "<gray>(frases, conquistas, jogadores online)."), click -> {
            viewer.sendMessage(Items.text("<gray>Gerando os áudios que faltam..."));
            plugin.saveAndApply(true).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () ->
                    viewer.sendMessage(Items.text("<green>Pronto.</green> <gray>Áudios no banco: " + plugin.voiceBank().size()))));
        });

        VoiceSettings.Narrator active = voice.activeNarrator();
        List<String> advanced = new ArrayList<>();
        advanced.add("<gray>Python: <white>" + Items.plain(voice.python()));
        if (active != null) {
            advanced.add("<gray>Modelo do narrador ativo:");
            advanced.addAll(Items.wrap("<white>", active.model().getPath(), 45));
            advanced.add(active.filesPresent() ? "<green>Arquivos encontrados" : "<red>Arquivos não encontrados");
        }
        advanced.add("");
        advanced.add("<yellow>Clique para trocar o comando do Python");
        button(32, Items.item(Material.COMMAND_BLOCK, "<yellow>Avançado", advanced),
                click -> askText("Comando ou caminho do Python com piper-tts, soundfile e kokoro-onnx:", voice.python(), v -> {
                    plugin.getConfig().set("voz.python", v);
                    apply(true);
                }));

        VoiceSettings.Narrator activeNarrator = voice.activeNarrator();
        boolean speaks = activeNarrator != null && plugin.health().canSpeak(activeNarrator);
        button(34, Items.item(speaks ? Material.LIME_BANNER : Material.ORANGE_BANNER, "<green>Instalação guiada",
                speaks ? "<green>A voz está funcionando." : "<gold>A voz ainda não funciona.",
                "<gray>Python, módulos, modelos de voz e", "<gray>teste, passo a passo.", "", "<yellow>Clique para abrir"),
                click -> new InstallMenu(plugin, viewer).open());

        button(49, Items.back(), click -> new MainMenu(plugin, viewer).open());
    }

    private void narratorClick(VoiceSettings.Narrator n, Click click) {
        if (click.shift()) {
            if (!plugin.playSample(viewer, n)) {
                viewer.sendMessage(Items.text(plugin.hasPack(viewer)
                        ? "<yellow>A amostra deste narrador ainda está sendo gerada, ou o seu pacote está desatualizado "
                                + "(Ferramentas > Reenviar pacote)."
                        : "<red>Você precisa aceitar o pacote do servidor para ouvir as amostras."));
            }
            return;
        }
        if (n.name().equals(plugin.voiceSettings().narrator())) {
            return;
        }
        viewer.sendMessage(Items.text("<gray>Gerando as falas de <white>" + n.name() + "</white>..."));
        plugin.activateNarrator(n.name()).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () ->
                viewer.sendMessage(Items.text("<green>Narrador trocado para " + n.name() + ".</green> <gray>Pacote reenviado."))));
        refresh();
    }

    private void apply(boolean voice) {
        if (voice) {
            viewer.sendMessage(Items.text("<gray>Salvando e gerando os áudios necessários..."));
        }
        plugin.saveAndApply(voice).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (voice && viewer.isOnline()) {
                viewer.sendMessage(Items.text("<green>Pronto.</green> <gray>Quem está online ouve as mudanças depois que o "
                        + "pacote for reenviado (Ferramentas > Reenviar pacote)."));
            }
        }));
        if (viewer.getOpenInventory().getTopInventory().getHolder(false) == this) {
            refresh();
        }
    }
}
