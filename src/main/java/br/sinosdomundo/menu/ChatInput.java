package br.sinosdomundo.menu;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Pede um texto ao admin pelo chat: o menu fecha, a próxima mensagem vira o valor (e não aparece para
 * ninguém), e "cancelar" desiste.
 */
public final class ChatInput implements Listener {

    private record Pending(Consumer<String> onAnswer, Runnable onCancel) {
    }

    private final Plugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInput(Plugin plugin) {
        this.plugin = plugin;
    }

    public void ask(Player player, String question, String current, Consumer<String> onAnswer, Runnable onCancel) {
        player.closeInventory();
        pending.put(player.getUniqueId(), new Pending(onAnswer, onCancel));
        player.sendMessage(Items.text("<gold>[Sinos]</gold> " + question));
        if (current != null && !current.isBlank()) {
            player.sendMessage(Items.text("<gray>Valor atual: <white>" + Items.plain(current)));
        }
        player.sendMessage(Items.text("<gray>Digite no chat, ou <red>cancelar</red> para desistir."));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Pending request = pending.remove(event.getPlayer().getUniqueId());
        if (request == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("cancelar")) {
                event.getPlayer().sendMessage(Items.text("<gray>Cancelado."));
                request.onCancel().run();
            } else {
                request.onAnswer().accept(text);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
