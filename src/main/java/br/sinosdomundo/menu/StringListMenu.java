package br.sinosdomundo.menu;

import br.sinosdomundo.SinosDoMundo;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Lista de textos de uma chave do config (frases finais, linhas do sino, mensagem do chat): adicionar, editar, remover. */
public final class StringListMenu extends Menu {

    private static final int MAX = 45;

    private final String title;
    private final String path;
    private final boolean voice;
    private final String example;
    private final Runnable onBack;

    /**
     * @param voice true se os textos são falados (gera áudio ao mudar)
     * @param example exemplo mostrado no botão de adicionar
     */
    public StringListMenu(SinosDoMundo plugin, Player viewer, String title, String path, boolean voice, String example,
            Runnable onBack) {
        super(plugin, viewer);
        this.title = title;
        this.path = path;
        this.voice = voice;
        this.example = example;
        this.onBack = onBack;
    }

    @Override
    protected String title() {
        return title;
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void render() {
        List<String> values = new ArrayList<>(plugin.getConfig().getStringList(path));
        for (int i = 0; i < values.size() && i < MAX; i++) {
            int index = i;
            List<String> lore = new ArrayList<>(Items.wrap("<white>", values.get(i), 40));
            lore.add("");
            lore.add("<yellow>Esquerdo: <gray>editar");
            lore.add("<yellow>Shift + direito: <gray>remover");
            button(i, Items.item(Material.PAPER, "<yellow>Linha " + (i + 1), lore), click -> {
                if (click.shift() && click.right()) {
                    values.remove(index);
                    save(values);
                } else if (!click.right()) {
                    askText("Nova versão do texto:", values.get(index), v -> {
                        values.set(index, v);
                        save(values);
                    });
                }
            });
        }
        button(45, Items.back(), click -> onBack.run());
        if (values.size() < MAX) {
            button(49, Items.item(Material.EMERALD, "<green>Adicionar", "<gray>Ex.: " + Items.plain(example)),
                    click -> askText("Novo texto:", "", v -> {
                        values.add(v);
                        save(values);
                    }));
        }
    }

    private void save(List<String> values) {
        plugin.getConfig().set(path, values);
        applyAndRefresh(voice);
    }
}
