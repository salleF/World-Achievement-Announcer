package br.sinosdomundo.menu;

import br.sinosdomundo.SinosDoMundo;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Um menu de baú. Cada tela desenha seus botões em {@link #render()}; cliques chegam pelo {@link MenuListener}. */
public abstract class Menu implements InventoryHolder {

    /** Um clique: o tipo (esquerdo, direito, com Shift) e quem clicou. */
    public record Click(Player player, ClickType type) {
        public boolean shift() {
            return type.isShiftClick();
        }

        public boolean right() {
            return type.isRightClick();
        }

        /** +1 / -1, ou +10 / -10 com Shift. */
        public int step() {
            int amount = shift() ? 10 : 1;
            return right() ? -amount : amount;
        }
    }

    protected final SinosDoMundo plugin;
    protected final Player viewer;
    private final Map<Integer, Consumer<Click>> actions = new HashMap<>();
    private Inventory inventory;

    protected Menu(SinosDoMundo plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    protected abstract String title();

    protected abstract int rows();

    protected abstract void render();

    public void open() {
        inventory = Bukkit.createInventory(this, rows() * 9, Items.text(title()));
        refresh();
        viewer.openInventory(inventory);
    }

    /** Redesenha a tela sem fechar (depois de mudar um valor). */
    public void refresh() {
        actions.clear();
        inventory.clear();
        render();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, Items.filler());
            }
        }
    }

    protected void button(int slot, ItemStack item, Consumer<Click> action) {
        inventory.setItem(slot, item);
        actions.put(slot, action);
    }

    protected void icon(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    /** Fecha o menu, pede um texto no chat e reabre esta tela depois. */
    protected void askText(String question, String current, Consumer<String> onAnswer) {
        plugin.chatInput().ask(viewer, question, current, answer -> {
            onAnswer.accept(answer);
            open();
        }, this::open);
    }

    /**
     * Botão de número ligado a uma chave do config: esquerdo soma, direito subtrai, Shift multiplica por 10.
     *
     * @param step quanto cada clique muda (1 para números inteiros)
     */
    protected void number(int slot, org.bukkit.Material material, String name, String path, double fallback,
            double step, double min, double max, String... help) {
        boolean integer = step == Math.rint(step);
        double value = plugin.getConfig().getDouble(path, fallback);
        java.util.List<String> lore = new java.util.ArrayList<>(java.util.List.of(help));
        lore.add("");
        String stepText = integer ? Integer.toString((int) step) : Double.toString(step);
        lore.add("<yellow>Esquerdo +" + stepText + ", direito -" + stepText + ", Shift ×10");
        button(slot, Items.item(material, name + ": <white>" + format(value, integer), lore), click -> {
            double next = Math.max(min, Math.min(max, value + click.step() * step));
            next = Math.round(next / step) * step;
            plugin.getConfig().set(path, integer ? (Object) (int) Math.round(next) : (Object) (Math.round(next * 1000) / 1000.0));
            applyAndRefresh(false);
        });
    }

    /** Liga/desliga ligado a uma chave do config. */
    protected void toggle(int slot, String name, String path, boolean fallback, String... help) {
        boolean on = plugin.getConfig().getBoolean(path, fallback);
        button(slot, Items.toggle(on, name, help), click -> {
            plugin.getConfig().set(path, !on);
            applyAndRefresh(false);
        });
    }

    /** Texto ligado a uma chave do config, editado pelo chat. */
    protected void text(int slot, org.bukkit.Material material, String name, String path, String fallback,
            String question, boolean voice, String... help) {
        String value = plugin.getConfig().getString(path, fallback);
        java.util.List<String> lore = new java.util.ArrayList<>(Items.wrap("<white>", value.isEmpty() ? "(vazio)" : value, 40));
        lore.addAll(java.util.List.of(help));
        lore.add("");
        lore.add("<yellow>Clique para editar");
        button(slot, Items.item(material, name, lore), click -> askText(question, value, v -> {
            plugin.getConfig().set(path, v);
            applyAndRefresh(voice);
        }));
    }

    /** Salva o config, aplica e redesenha a tela se ela ainda estiver aberta. */
    protected void applyAndRefresh(boolean voice) {
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

    private static String format(double value, boolean integer) {
        return integer ? Integer.toString((int) Math.round(value)) : String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    void handle(int slot, ClickType type) {
        Consumer<Click> action = actions.get(slot);
        if (action != null) {
            action.accept(new Click(viewer, type));
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
