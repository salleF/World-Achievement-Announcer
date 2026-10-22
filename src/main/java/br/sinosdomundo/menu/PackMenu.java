package br.sinosdomundo.menu;

import br.sinosdomundo.SinosDoMundo;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Pacote de recursos: servidor HTTP, endereço, obrigatoriedade, mensagem e reenvio. */
public final class PackMenu extends Menu {

    public PackMenu(SinosDoMundo plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected String title() {
        return "<blue>Pacote de recursos";
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void render() {
        List<String> status = new ArrayList<>();
        status.add(plugin.pack().external()
                ? "<gray>Hospedagem: <white>externa"
                : "<gray>Servidor do pacote: " + (plugin.pack().running() ? "<green>rodando" : "<red>parado"));
        status.add("<gray>Tamanho: <white>" + (plugin.pack().size() / 1024) + " KB");
        status.add("<gray>Áudios de voz: <white>" + plugin.voiceBank().size());
        status.add("<gray>Jogadores com o pacote: <white>" + plugin.playersWithPack() + "/" + Bukkit.getOnlinePlayers().size());
        status.add("<gray>Com versão antiga: <white>" + plugin.pack().outdatedCount());
        status.add("");
        status.add("<yellow>Clique para ver o endereço no chat");
        button(4, Items.item(Material.BOOK, "<blue>Pacote", status), click -> {
            String url = plugin.pack().publicUrl();
            viewer.sendMessage(Items.text("<gray>Endereço do pacote: <white>" + Items.plain(url))
                    .clickEvent(ClickEvent.copyToClipboard(url)));
            viewer.sendMessage(Items.text("<dark_gray>(clique para copiar; abra no navegador de outro PC para testar)"));
        });

        boolean external = plugin.pack().external();
        button(19, Items.item(external ? Material.CHEST_MINECART : Material.FURNACE_MINECART,
                "<yellow>Hospedagem: <white>" + (external ? "externa" : "pelo plugin"),
                external ? "<gray>Você hospeda o pacote.zip em outro lugar." : "<gray>O plugin serve o pacote na porta abaixo.",
                "<gray>Use externa quando a hospedagem só", "<gray>libera a porta do jogo.", "",
                "<yellow>Clique para trocar"), click -> {
            plugin.getConfig().set("pacote.modo", external ? "proprio" : "externo");
            restart();
        });
        if (external) {
            text(20, Material.FILLED_MAP, "<yellow>Endereço do pacote hospedado", "pacote.url-externa", "",
                    "Link direto do pacote.zip hospedado (ex.: https://meusite.com/pacote.zip):", false,
                    "<gray>Envie para lá o arquivo", "<white>plugins/SinosDoMundo/pacote.zip",
                    "<gray>sempre que o console avisar que mudou.");
        }
        button(10, Items.toggle(plugin.getConfig().getBoolean("pacote.ativado", true), "<yellow>Servir o pacote",
                "<gray>Desligado, ninguém recebe o sino 3D,", "<gray>a voz nem a música."), click -> {
            plugin.getConfig().set("pacote.ativado", !plugin.getConfig().getBoolean("pacote.ativado", true));
            restart();
        });
        int port = plugin.getConfig().getInt("pacote.porta", 8164);
        button(11, Items.item(Material.IRON_DOOR, "<yellow>Porta: <white>" + port,
                "<gray>Porta HTTP de onde os jogadores baixam", "<gray>o pacote. Precisa estar liberada.",
                "", "<yellow>Clique para mudar"), click -> askText("Nova porta do pacote (ex.: 8164):", Integer.toString(port), v -> {
            try {
                int value = Integer.parseInt(v.trim());
                if (value < 1 || value > 65535) {
                    throw new NumberFormatException();
                }
                plugin.getConfig().set("pacote.porta", value);
                restart();
            } catch (NumberFormatException e) {
                viewer.sendMessage(Items.text("<red>Porta inválida."));
            }
        }));
        text(12, Material.FILLED_MAP, "<yellow>Endereço público", "pacote.url-publica", "",
                "Endereço que os jogadores usam (ex.: http://SEU-IP:8164). Digite <white>-</white> para automático:", false,
                "<gray>Vazio = automático (IP do servidor).", "<gray>Em hospedagem, use o IP ou domínio público.");
        toggle(13, "<yellow>Pacote obrigatório", "pacote.obrigatorio", false,
                "<gray>Ligado, quem recusar o pacote é", "<gray>desconectado.");
        text(14, Material.NAME_TAG, "<yellow>Mensagem ao jogador", "pacote.mensagem", "",
                "Mensagem mostrada quando o jogo pergunta se aceita o pacote:", false, "<gray>Aceita cores MiniMessage.");

        int outdated = plugin.pack().outdatedCount();
        button(16, Items.item(Material.ENDER_PEARL, "<yellow>Reenviar pacote",
                "<gray>Jogadores com versão antiga: <white>" + outdated, "<red>O jogo deles recarrega os recursos."),
                click -> new ConfirmMenu(plugin, viewer, "Reenviar o pacote?",
                        "Todos os jogadores online recebem o pacote de novo.", () -> {
                            plugin.resendPack();
                            viewer.sendMessage(Items.text("<green>Pacote reenviado."));
                            open();
                        }, this::open).open());

        button(31, Items.back(), click -> new MainMenu(plugin, viewer).open());
    }

    @Override
    protected void applyAndRefresh(boolean voice) {
        // "-" no endereço público volta para o automático
        if ("-".equals(plugin.getConfig().getString("pacote.url-publica", "").trim())) {
            plugin.getConfig().set("pacote.url-publica", "");
        }
        super.applyAndRefresh(voice);
    }

    /** Porta e ativação só valem reabrindo o servidor HTTP do pacote. */
    private void restart() {
        plugin.saveAndApply(false);
        plugin.restartPackServer();
        viewer.sendMessage(Items.text(plugin.pack().external()
                ? "<green>Modo externo.</green> <gray>Envie o plugins/SinosDoMundo/pacote.zip para o endereço configurado."
                : plugin.pack().running() ? "<green>Servidor do pacote reiniciado.</green>" : "<yellow>Servidor do pacote parado."));
        if (viewer.getOpenInventory().getTopInventory().getHolder(false) == this) {
            refresh();
        }
    }
}
