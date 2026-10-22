package br.sinosdomundo.menu;

import br.sinosdomundo.Lang;
import br.sinosdomundo.SinosDoMundo;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Confirmação para ações sem volta (apagar, resetar, reenviar o pacote para todos). */
public final class ConfirmMenu extends Menu {

    private final String question;
    private final String detail;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    public ConfirmMenu(SinosDoMundo plugin, Player viewer, String question, String detail, Runnable onConfirm,
            Runnable onCancel) {
        super(plugin, viewer);
        this.question = question;
        this.detail = detail;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected String title() {
        return "<dark_red>" + question;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void render() {
        icon(4, Items.item(Material.PAPER, "<yellow>" + question, Items.wrap("<gray>", Lang.t(detail), 40)));
        button(11, Items.item(Material.LIME_CONCRETE, "<green>Confirmar"), click -> onConfirm.run());
        button(15, Items.item(Material.RED_CONCRETE, "<red>Cancelar"), click -> onCancel.run());
    }
}
