package br.sinosdomundo.menu;

import br.sinosdomundo.Lang;
import br.sinosdomundo.SinosDoMundo;
import br.sinosdomundo.bell.AnnouncementService;
import br.sinosdomundo.conquest.Conquest;
import br.sinosdomundo.voice.VoiceSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Ferramentas: testar anúncio, marcar mob, recarregar, reenviar o pacote e limpar áudios antigos. */
public final class ToolsMenu extends Menu {

    public ToolsMenu(SinosDoMundo plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected String title() {
        return "<green>Ferramentas";
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void render() {
        button(10, Items.item(Material.BELL, "<green>Testar anúncio",
                "<gray>Escolha uma conquista e ouça o anúncio", "<gray>só para você, com o seu nome."),
                click -> pickConquest("Testar qual conquista?", c -> preview(c, List.of(viewer.getName()))));

        button(11, Items.item(Material.PLAYER_HEAD, "<green>Testar com vários nomes",
                "<gray>Como o anterior, mas com você e os", "<gray>outros jogadores online como contribuidores."),
                click -> pickConquest("Testar qual conquista?", c -> {
                    List<String> names = new ArrayList<>();
                    names.add(viewer.getName());
                    Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> !n.equals(viewer.getName()))
                            .limit(4).forEach(names::add);
                    preview(c, names);
                }));

        button(12, Items.item(Material.LEAD, "<green>Marcar mob que estou olhando",
                "<gray>Olhe para o mob (até 20 blocos), clique", "<gray>e escolha a conquista."),
                click -> {
                    Entity target = viewer.getTargetEntity(20);
                    if (!(target instanceof LivingEntity living) || target instanceof Player) {
                        viewer.sendMessage(Items.text("<red>Olhe para um mob a até 20 blocos e clique de novo."));
                        return;
                    }
                    pickConquest("Marcar com qual conquista?", c -> {
                        plugin.conquests().mark(living, c);
                        viewer.closeInventory();
                        viewer.sendMessage(Items.text("<green>Mob marcado com " + c.id() + "."));
                    });
                });

        button(13, Items.item(Material.SHEARS, "<yellow>Desmarcar mob que estou olhando"), click -> {
            Entity target = viewer.getTargetEntity(20);
            boolean removed = target != null && plugin.conquests().unmark(target);
            viewer.sendMessage(Items.text(removed ? "<green>Marca removida." : "<gray>Esse mob não tem marca."));
        });

        button(14, Items.item(Material.REDSTONE, "<yellow>Recarregar config",
                "<gray>Lê de novo o config.yml (se você editou", "<gray>o arquivo à mão)."), click -> {
            viewer.performCommand("sinos reload");
            refresh();
        });

        int outdated = plugin.pack().outdatedCount();
        button(15, Items.item(Material.ENDER_PEARL, "<yellow>Reenviar pacote",
                "<gray>Manda o pacote atual para todos online.", "<gray>Jogadores com versão antiga: <white>" + outdated,
                "<red>O jogo deles recarrega os recursos."), click -> new ConfirmMenu(plugin, viewer, "Reenviar o pacote?",
                "Todos os jogadores online recebem o pacote de novo. A tela deles mostra o carregamento por alguns segundos.",
                () -> {
                    plugin.resendPack();
                    viewer.sendMessage(Items.text("<green>Pacote reenviado."));
                    open();
                }, this::open).open());

        button(16, Items.item(Material.CAULDRON, "<yellow>Limpar áudios antigos",
                "<gray>Apaga áudios de narradores que não", "<gray>existem mais e arquivos sem uso."), click -> {
            viewer.sendMessage(Items.text("<gray>Limpando..."));
            plugin.voiceBank().cleanup(plugin.voiceSettings().narrators().values().stream()
                            .map(VoiceSettings.Narrator::name).collect(Collectors.toSet()))
                    .thenCompose(deleted -> plugin.pack().rebuild().thenApply(changed -> deleted))
                    .thenAccept(deleted -> Bukkit.getScheduler().runTask(plugin, () ->
                            viewer.sendMessage(Items.text("<green>" + deleted + " arquivo(s) apagado(s)."))));
        });

        String language = Lang.code();
        button(4, Items.item(Material.GLOBE_BANNER_PATTERN, "<yellow>Idioma: <white>" + language,
                "<gray>Idioma dos menus, mensagens e console.", "<gray>Os textos dos anúncios ficam no config.yml.",
                "", "<yellow>Clique para trocar (pt-BR / en)"), click -> {
            plugin.getConfig().set("idioma", Lang.english() ? Lang.PORTUGUESE : "en");
            plugin.saveAndApply(false);
            new ToolsMenu(plugin, viewer).open();
        });

        button(22, Items.back(), click -> new MainMenu(plugin, viewer).open());
    }

    private void pickConquest(String title, Consumer<Conquest> action) {
        List<ChoiceMenu.Option<Conquest>> options = new ArrayList<>();
        for (Conquest c : plugin.registry().all()) {
            options.add(new ChoiceMenu.Option<>(Items.item(ConquestEditorMenu.triggerIcon(c), Items.user(c.display()),
                    "<dark_gray>" + c.id()), c));
        }
        new ChoiceMenu<>(plugin, viewer, "<green>" + title, options, action, this::open).open();
    }

    private void preview(Conquest c, List<String> names) {
        viewer.closeInventory();
        if (plugin.pack().isOutdated(viewer)) {
            plugin.pack().send(viewer);
            viewer.sendMessage(Items.text("<yellow>Atualizando o seu pacote para ouvir os áudios novos. "
                    + "Teste de novo em alguns segundos."));
            return;
        }
        plugin.preview(viewer, new AnnouncementService.Request(c.display(), c.spoken(), c.phrase(), c.closing(),
                c.narrator(), names));
    }
}
