package br.sinosdomundo.menu;

import br.sinosdomundo.BellSettings;
import br.sinosdomundo.SinosDoMundo;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Música: ligar, volume, início, ouvir, voltar à padrão e colocar uma música própria. */
public final class MusicMenu extends Menu {

    public MusicMenu(SinosDoMundo plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected String title() {
        return "<light_purple>Música";
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void render() {
        String current = plugin.getConfig().getString("musica.arquivo", "");
        boolean custom = !current.isEmpty() && plugin.music().activeFile().isFile();
        List<String> status = new ArrayList<>();
        status.add("<gray>Em uso: <white>" + (custom ? Items.plain(current) : "música padrão do plugin"));
        if (custom) {
            status.add("<gray>Duração: <white>" + plugin.getConfig().getDouble("musica.duracao", 0) + " s");
        }
        status.add("<gray>Começa no tick <white>" + plugin.getConfig().getInt("musica.inicio-ticks", 1)
                + "<gray> depois que o sino aparece.");
        icon(4, Items.item(Material.JUKEBOX, "<light_purple>Música da conquista", status));

        toggle(10, "<yellow>Música ligada", "musica.ativada", true, "<gray>Só quem tem o pacote ouve.");
        number(11, Material.NOTE_BLOCK, "<yellow>Volume", "musica.volume", 0.45, 0.05, 0, 1,
                "<gray>Volume da música (a voz não muda).");
        number(12, Material.CLOCK, "<yellow>Início", "musica.inicio-ticks", 1, 1, 1, 200,
                "<gray>Tick em que a música começa, contado", "<gray>desde que o sino aparece.");
        button(13, Items.item(Material.MUSIC_DISC_CHIRP, "<green>Ouvir agora", "<gray>Toca a música em uso só para você."),
                click -> listen());
        button(14, Items.item(Material.MUSIC_DISC_CAT, "<yellow>Usar a música padrão",
                custom ? "<gray>Volta para a música que vem com o plugin." : "<green>Já está em uso."), click -> {
            if (custom) {
                useDefault();
            }
        });
        button(15, Items.item(Material.WRITABLE_BOOK, "<green>Colocar música própria",
                "<gray>Mostra como colocar o arquivo na pasta", "<gray>e lista as músicas para escolher."),
                click -> new MusicFilesMenu(plugin, viewer).open());
        button(16, Items.item(Material.REPEATER, "<yellow>Ajustar batidas à música em uso",
                "<gray>Mede os ataques da música e coloca as", "<gray>batidas do sino neles."), click -> syncRhythm(custom));

        button(31, Items.back(), click -> new MainMenu(plugin, viewer).open());
    }

    private void listen() {
        viewer.closeInventory();
        if (plugin.pack().isOutdated(viewer)) {
            plugin.pack().send(viewer);
            viewer.sendMessage(Items.text("<yellow>Atualizando o seu pacote. Tente de novo em alguns segundos."));
            return;
        }
        viewer.stopSound(net.kyori.adventure.sound.SoundStop.named(Key.key(BellSettings.MUSIC_SOUND)));
        viewer.playSound(Sound.sound(Key.key(BellSettings.MUSIC_SOUND), Sound.Source.MASTER,
                (float) plugin.getConfig().getDouble("musica.volume", 0.45), 1f), Sound.Emitter.self());
    }

    private void useDefault() {
        File active = plugin.music().activeFile();
        if (active.isFile() && !active.delete()) {
            viewer.sendMessage(Items.text("<red>Não foi possível apagar " + active.getPath()));
            return;
        }
        plugin.getConfig().set("musica.arquivo", "");
        plugin.getConfig().set("musica.duracao", null);
        new ConfirmMenu(plugin, viewer, "Restaurar o ritmo padrão?",
                "As batidas do sino e o atraso da fala voltam a acompanhar a música padrão.", () -> {
                    plugin.getConfig().set("sino.ritmo", RhythmMenu.DEFAULT_RHYTHM);
                    plugin.getConfig().set("voz.atraso-ticks", RhythmMenu.DEFAULT_SPEECH_DELAY);
                    MusicFilesMenu.finish(plugin, viewer);
                }, () -> MusicFilesMenu.finish(plugin, viewer)).open();
    }

    private void syncRhythm(boolean custom) {
        if (!custom) {
            plugin.getConfig().set("sino.ritmo", RhythmMenu.DEFAULT_RHYTHM);
            plugin.getConfig().set("voz.atraso-ticks", RhythmMenu.DEFAULT_SPEECH_DELAY);
            applyAndRefresh(false);
            viewer.sendMessage(Items.text("<green>Ritmo da música padrão restaurado."));
            return;
        }
        viewer.sendMessage(Items.text("<gray>Medindo os ataques da música..."));
        plugin.music().analyze(plugin.music().activeFile()).whenComplete((analysis, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        viewer.sendMessage(Items.text("<red>Não foi possível analisar: " + Items.message(error.getMessage())));
                        return;
                    }
                    MusicFilesMenu.offerRhythm(plugin, viewer, analysis, () -> new MusicMenu(plugin, viewer).open());
                }));
    }
}
