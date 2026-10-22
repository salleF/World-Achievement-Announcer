package br.sinosdomundo.menu;

import br.sinosdomundo.SinosDoMundo;
import br.sinosdomundo.music.MusicService;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Música própria: tutorial, lista dos arquivos da pasta musicas, prévia e escolha (converter, ritmo, estender). */
public final class MusicFilesMenu extends Menu {

    private static final int FIRST_SLOT = 9;
    private static final int MAX_FILES = 35;
    private static final long BIG_FILE = 1024 * 1024;
    private static final Material[] DISCS = {Material.MUSIC_DISC_13, Material.MUSIC_DISC_BLOCKS, Material.MUSIC_DISC_FAR,
            Material.MUSIC_DISC_MALL, Material.MUSIC_DISC_MELLOHI, Material.MUSIC_DISC_STRAD, Material.MUSIC_DISC_WAIT};

    public MusicFilesMenu(SinosDoMundo plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected String title() {
        return "<light_purple>Música própria";
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void render() {
        button(4, Items.item(Material.KNOWLEDGE_BOOK, "<yellow>Como colocar uma música",
                "<gray>1. Coloque o arquivo em:", "<white>plugins/SinosDoMundo/musicas/",
                "<gray>2. Formatos: .ogg, .mp3, .wav, .flac", "<gray>3. Clique em Atualizar lista",
                "<gray>4. Clique na música para usar", "", "<yellow>Clique para ver no chat"), click -> tutorial());

        String current = plugin.getConfig().getString("musica.arquivo", "");
        button(FIRST_SLOT, Items.item(Material.MUSIC_DISC_CAT, "<yellow>Música padrão do plugin",
                current.isEmpty() ? "<green>Em uso" : "<gray>Clique para voltar a ela"), click -> {
            if (!current.isEmpty()) {
                new MusicMenu(plugin, viewer).open();
                viewer.sendMessage(Items.text("<gray>Use o botão <white>Usar a música padrão</white> na tela Música."));
            }
        });

        List<File> files = plugin.music().files();
        if (files.isEmpty()) {
            icon(22, Items.item(Material.PAPER, "<gray>Nenhuma música na pasta",
                    "<gray>Coloque arquivos em plugins/SinosDoMundo/musicas", "<gray>e clique em Atualizar lista."));
        }
        for (int i = 0; i < files.size() && i < MAX_FILES; i++) {
            File file = files.get(i);
            String ext = MusicService.extension(file.getName());
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Tamanho: <white>" + (file.length() / 1024) + " KB");
            lore.add(ext.equals("ogg") ? "<gray>OGG: será ajustada para mono" : "<gray>" + ext.toUpperCase(Locale.ROOT)
                    + ": será convertida para OGG");
            if (file.length() > BIG_FILE) {
                lore.add("<gold>Arquivo grande: o pacote fica mais pesado");
            }
            if (file.getName().equals(current)) {
                lore.add("<green>Em uso");
            }
            lore.add("");
            lore.add("<yellow>Clique: <gray>usar esta música");
            lore.add("<yellow>Shift + clique: <gray>ouvir só para você");
            button(FIRST_SLOT + 1 + i, Items.item(DISCS[i % DISCS.length], "<white>" + Items.plain(file.getName()), lore),
                    click -> {
                        if (click.shift()) {
                            preview(file);
                        } else {
                            choose(file);
                        }
                    });
        }

        button(45, Items.back(), click -> new MusicMenu(plugin, viewer).open());
        button(49, Items.item(Material.SUNFLOWER, "<green>Atualizar lista", "<gray>Lê a pasta de novo."), click -> refresh());
    }

    private void tutorial() {
        viewer.closeInventory();
        String folder = plugin.music().library().getAbsolutePath();
        viewer.sendMessage(Items.text("<gold>[Sinos] Como usar uma música própria"));
        viewer.sendMessage(Items.text("<gray>1. Coloque o arquivo de música nesta pasta do servidor:"));
        viewer.sendMessage(Items.text("<white>" + Items.plain(folder)).clickEvent(ClickEvent.copyToClipboard(folder)));
        viewer.sendMessage(Items.text("<dark_gray>(clique no caminho para copiar)"));
        viewer.sendMessage(Items.text("<gray>2. Formatos aceitos: <white>.ogg, .mp3, .wav e .flac</white>."));
        viewer.sendMessage(Items.text("<gray>3. Abra <white>/sinos > Música > Colocar música própria</white> e clique em "
                + "<white>Atualizar lista</white>."));
        viewer.sendMessage(Items.text("<gray>4. Shift + clique para ouvir; clique para usar. O plugin converte, ajusta as "
                + "batidas do sino ao ritmo e pode estender a música para cobrir a fala."));
        viewer.sendMessage(Items.text("<gray>Prefira músicas de até uns 30 s e use só músicas que você tem direito de usar."));
    }

    private void preview(File file) {
        viewer.closeInventory();
        viewer.sendMessage(Items.text("<gray>Preparando a prévia de <white>" + Items.plain(file.getName())
                + "</white>... (o seu jogo carrega um pacote pequeno)"));
        plugin.music().preview(viewer, file).exceptionally(error -> {
            Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage(Items.text("<red>Não foi possível preparar a prévia: "
                    + Items.message(rootMessage(error)))));
            return null;
        });
    }

    private void choose(File file) {
        viewer.closeInventory();
        if (file.length() > BIG_FILE) {
            viewer.sendMessage(Items.text("<gold>Aviso: arquivo grande. Todo jogador baixa a música junto com o pacote."));
        }
        viewer.sendMessage(Items.text("<gray>Analisando e convertendo <white>" + Items.plain(file.getName()) + "</white>..."));
        MusicService music = plugin.music();
        music.analyze(file).thenCompose(analysis -> music.convert(file, music.activeFile(), 0)
                        .thenApply(duration -> analysis))
                .whenComplete((analysis, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        viewer.sendMessage(Items.text("<red>Não foi possível usar a música: " + Items.message(rootMessage(error))));
                        return;
                    }
                    plugin.getConfig().set("musica.arquivo", file.getName());
                    plugin.getConfig().set("musica.duracao", analysis.duration());
                    viewer.sendMessage(Items.text("<green>Música convertida (" + analysis.duration() + " s)."));
                    offerRhythm(plugin, viewer, analysis, () -> offerExtension(file, analysis));
                }));
    }

    /** Pergunta se as batidas do sino devem seguir os ataques medidos na música. */
    static void offerRhythm(SinosDoMundo plugin, Player viewer, MusicService.Analysis analysis, Runnable next) {
        MusicService.Suggestion suggestion = MusicService.suggest(analysis, plugin.getConfig().getInt("musica.inicio-ticks", 1));
        if (suggestion.ticks().isEmpty()) {
            viewer.sendMessage(Items.text("<gray>Não encontrei ataques fortes nesta música; o ritmo do sino não mudou."));
            next.run();
            return;
        }
        String ticks = suggestion.ticks().stream().map(String::valueOf).collect(Collectors.joining(", "));
        new ConfirmMenu(plugin, viewer, "Ajustar as batidas a esta música?",
                "Encontrei " + suggestion.ticks().size() + " batidas fortes (ticks " + ticks + "). A fala começaria "
                        + String.format(Locale.ROOT, "%.1f", suggestion.speechDelay() / 20.0) + " s depois da última.",
                () -> {
                    plugin.getConfig().set("sino.ritmo", suggestion.ticks());
                    plugin.getConfig().set("voz.atraso-ticks", suggestion.speechDelay());
                    viewer.sendMessage(Items.text("<green>Batidas ajustadas ao ritmo da música."));
                    next.run();
                }, next).open();
    }

    private void offerExtension(File file, MusicService.Analysis analysis) {
        if (analysis.duration() >= MusicService.EXTENDED_SECONDS - 2) {
            finish(plugin, viewer);
            return;
        }
        new ConfirmMenu(plugin, viewer, "Estender a música?", "A música tem " + analysis.duration()
                + " s e o anúncio com a fala dura uns 15 s. Posso continuar a música com um fundo baixo feito do "
                + "próprio som dela até " + MusicService.EXTENDED_SECONDS + " s.", () -> {
                    viewer.closeInventory();
                    viewer.sendMessage(Items.text("<gray>Estendendo a música..."));
                    plugin.music().convert(file, plugin.music().activeFile(), MusicService.EXTENDED_SECONDS)
                            .whenComplete((duration, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                                if (error != null) {
                                    viewer.sendMessage(Items.text("<red>Não foi possível estender: " + Items.message(rootMessage(error))));
                                } else {
                                    plugin.getConfig().set("musica.duracao", duration);
                                    viewer.sendMessage(Items.text("<green>Música estendida para " + duration + " s."));
                                }
                                finish(plugin, viewer);
                            }));
                }, () -> finish(plugin, viewer)).open();
    }

    /** Salva, remonta o pacote e pergunta se reenvia agora para quem está online. */
    static void finish(SinosDoMundo plugin, Player viewer) {
        plugin.saveAndApply(false);
        plugin.pack().rebuild().thenAccept(changed -> Bukkit.getScheduler().runTask(plugin, () ->
                new ConfirmMenu(plugin, viewer, "Reenviar o pacote agora?",
                        "Quem está online só ouve a música nova depois de receber o pacote de novo (o jogo deles recarrega "
                                + "os recursos por alguns segundos).", () -> {
                            plugin.resendPack();
                            viewer.sendMessage(Items.text("<green>Pacote reenviado."));
                            new MusicMenu(plugin, viewer).open();
                        }, () -> new MusicMenu(plugin, viewer).open()).open()));
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
