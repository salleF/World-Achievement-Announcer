package br.sinosdomundo.menu;

import java.util.ArrayList;
import java.util.List;
import br.sinosdomundo.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Monta os itens dos menus: nome e descrição em MiniMessage, sem o itálico padrão do Minecraft. */
public final class Items {

    private Items() {
    }

    public static ItemStack item(Material material, String name, String... lore) {
        return item(material, name, List.of(lore));
    }

    public static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(text(line));
        }
        meta.lore(lines);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /** MiniMessage para Component, traduzindo os trechos conhecidos para o idioma do servidor. */
    public static Component text(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(Lang.t(miniMessage)).decoration(TextDecoration.ITALIC, false);
    }

    /** Escapa um texto do usuário para aparecer literalmente (e sem tradução) dentro de MiniMessage. */
    public static String plain(String value) {
        return Lang.keep(MiniMessage.miniMessage().escapeTags(value == null ? "" : value));
    }

    /** Texto MiniMessage escrito pelo admin (nome de conquista): mantém as cores e não é traduzido. */
    public static String user(String miniMessage) {
        return Lang.keep(miniMessage);
    }

    /** Uma mensagem do plugin (erro, progresso) traduzida e escapada para aparecer literalmente. */
    public static String message(String value) {
        return plain(Lang.t(value));
    }

    /** Quebra um texto longo em linhas de descrição. */
    public static List<String> wrap(String prefix, String value, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : (value == null ? "" : value).split(" ")) {
            if (line.length() + word.length() + 1 > width && !line.isEmpty()) {
                lines.add(prefix + plain(line.toString()));
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(prefix + plain(line.toString()));
        }
        return lines;
    }

    public static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    public static ItemStack back() {
        return item(Material.ARROW, "<gray>Voltar");
    }

    public static ItemStack toggle(boolean on, String name, String... lore) {
        List<String> lines = new ArrayList<>(List.of(lore));
        lines.add(on ? "<green>Ligado</green> <gray>(clique para desligar)" : "<red>Desligado</red> <gray>(clique para ligar)");
        return item(on ? Material.LIME_DYE : Material.GRAY_DYE, name, lines);
    }
}
