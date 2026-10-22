package br.sinosdomundo.menu;

import br.sinosdomundo.SinosDoMundo;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Escolha numa lista (gatilho, mob, mundo, narrador...), com páginas de 45 itens. */
public final class ChoiceMenu<T> extends Menu {

    public record Option<T>(ItemStack icon, T value) {
    }

    private static final int PAGE = 45;

    private final String title;
    private final List<Option<T>> options;
    private final Consumer<T> onChoose;
    private final Runnable onBack;
    private int page;

    public ChoiceMenu(SinosDoMundo plugin, Player viewer, String title, List<Option<T>> options, Consumer<T> onChoose,
            Runnable onBack) {
        super(plugin, viewer);
        this.title = title;
        this.options = options;
        this.onChoose = onChoose;
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
        int start = page * PAGE;
        for (int i = 0; i < PAGE && start + i < options.size(); i++) {
            Option<T> option = options.get(start + i);
            button(i, option.icon(), click -> onChoose.accept(option.value()));
        }
        button(45, Items.back(), click -> onBack.run());
        if (page > 0) {
            button(48, Items.item(org.bukkit.Material.SPECTRAL_ARROW, "<yellow>Página anterior"), click -> {
                page--;
                refresh();
            });
        }
        if (start + PAGE < options.size()) {
            button(50, Items.item(org.bukkit.Material.SPECTRAL_ARROW, "<yellow>Próxima página"), click -> {
                page++;
                refresh();
            });
        }
    }
}
