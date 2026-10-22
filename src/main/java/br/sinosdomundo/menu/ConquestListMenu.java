package br.sinosdomundo.menu;

import br.sinosdomundo.SinosDoMundo;
import br.sinosdomundo.conquest.Conquest;
import br.sinosdomundo.conquest.ConquestRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Lista das conquistas, com filtro, páginas e o botão de criar. */
public final class ConquestListMenu extends Menu {

    private enum Filter {
        TODAS("Todas"), DISPONIVEIS("Disponíveis"), CONCLUIDAS("Concluídas");

        final String label;

        Filter(String label) {
            this.label = label;
        }
    }

    private static final int PAGE = 45;
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_-]{1,32}");

    private Filter filter = Filter.TODAS;
    private int page;

    public ConquestListMenu(SinosDoMundo plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected String title() {
        return "<gold>Conquistas";
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void render() {
        ConquestRegistry registry = plugin.registry();
        List<Conquest> shown = new ArrayList<>();
        for (Conquest c : registry.all()) {
            boolean done = registry.isCompleted(c.id());
            if (filter == Filter.TODAS || (filter == Filter.CONCLUIDAS) == done) {
                shown.add(c);
            }
        }
        int start = page * PAGE;
        for (int i = 0; i < PAGE && start + i < shown.size(); i++) {
            Conquest conquest = shown.get(start + i);
            button(i, icon(conquest, registry), click -> new ConquestEditorMenu(plugin, viewer, conquest.id()).open());
        }
        if (shown.isEmpty()) {
            icon(22, Items.item(Material.PAPER, "<gray>Nenhuma conquista aqui", "<gray>Use o botão Criar conquista."));
        }

        button(45, Items.back(), click -> new MainMenu(plugin, viewer).open());
        button(47, Items.item(Material.HOPPER, "<yellow>Filtro: " + filter.label,
                "<gray>Clique para trocar entre", "<gray>todas, disponíveis e concluídas."), click -> {
            filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
            page = 0;
            refresh();
        });
        if (page > 0) {
            button(48, Items.item(Material.SPECTRAL_ARROW, "<yellow>Página anterior"), click -> {
                page--;
                refresh();
            });
        }
        button(49, Items.item(Material.EMERALD, "<green>Criar conquista",
                "<gray>Você escolhe um id (ex.: primeiro-golem)", "<gray>e depois edita o resto no editor."), click -> create());
        if (start + PAGE < shown.size()) {
            button(50, Items.item(Material.SPECTRAL_ARROW, "<yellow>Próxima página"), click -> {
                page++;
                refresh();
            });
        }
    }

    private void create() {
        askText("Id da nova conquista (letras minúsculas, números e hífen, ex.: <white>primeiro-golem</white>):", "", id -> {
            String clean = id.trim().toLowerCase();
            if (!VALID_ID.matcher(clean).matches()) {
                viewer.sendMessage(Items.text("<red>Id inválido. Use só letras minúsculas, números, - e _."));
                return;
            }
            if (plugin.registry().get(clean).isPresent()) {
                viewer.sendMessage(Items.text("<red>Já existe uma conquista com esse id."));
                return;
            }
            String path = "conquistas." + clean;
            plugin.getConfig().set(path + ".gatilho", Conquest.Trigger.MANUAL.name());
            plugin.getConfig().set(path + ".uma-vez", true);
            plugin.getConfig().set(path + ".nome", "<gold>" + clean);
            plugin.getConfig().set(path + ".voz", "");
            plugin.saveAndApply(false);
            viewer.sendMessage(Items.text("<green>Conquista criada. Agora ajuste o gatilho, o nome e a voz."));
            // Abre o editor da nova conquista depois que este menu reabrir
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> new ConquestEditorMenu(plugin, viewer, clean).open());
        });
    }

    /** Ícone da conquista conforme o gatilho, com o resumo na descrição. */
    static ItemStack icon(Conquest c, ConquestRegistry registry) {
        java.util.List<String> lore = new ArrayList<>(ConquestEditorMenu.summary(c, registry));
        lore.add("");
        lore.add("<yellow>Clique para editar");
        return Items.item(ConquestEditorMenu.triggerIcon(c), Items.user(c.display()) + " <dark_gray>(" + Items.plain(c.id()) + ")", lore);
    }
}
