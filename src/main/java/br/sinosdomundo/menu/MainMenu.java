package br.sinosdomundo.menu;

import br.sinosdomundo.SinosDoMundo;
import br.sinosdomundo.voice.VoiceSettings;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Menu principal do /sinos: atalhos para cada parte do plugin e um resumo do estado. */
public final class MainMenu extends Menu {

    public MainMenu(SinosDoMundo plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected String title() {
        return "<dark_purple>Sinos do Mundo";
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void render() {
        icon(4, status());
        button(10, Items.item(Material.GOLDEN_HELMET, "<gold>Conquistas",
                "<gray>Criar, editar, testar e apagar", "<gray>as conquistas do servidor.",
                "", "<yellow>Clique para abrir"), click -> new ConquestListMenu(plugin, viewer).open());
        button(11, Items.item(Material.BELL, "<gold>Sino", "<gray>Aparência, batidas, ritmo, sons", "<gray>e o texto acima do sino.",
                "", "<yellow>Clique para abrir"), click -> new BellMenu(plugin, viewer).open());
        button(12, Items.item(Material.MUSIC_DISC_OTHERSIDE, "<aqua>Voz e narrador",
                "<gray>Narrador ativo, frase padrão,", "<gray>frases finais e geração dos áudios.",
                "", "<yellow>Clique para abrir"), click -> new VoiceMenu(plugin, viewer).open());
        button(13, Items.item(Material.JUKEBOX, "<light_purple>Música", "<gray>Música de vitória, volume e", "<gray>música própria.",
                "", "<yellow>Clique para abrir"), click -> new MusicMenu(plugin, viewer).open());
        button(14, Items.item(Material.IRON_SWORD, "<red>Contribuidores", "<gray>Quem entra na lista de cada", "<gray>conquista.",
                "", "<yellow>Clique para abrir"), click -> new ContributorsMenu(plugin, viewer).open());
        button(15, Items.item(Material.BOOK, "<blue>Pacote de recursos", "<gray>Porta, endereço e envio do", "<gray>pacote.",
                "", "<yellow>Clique para abrir"), click -> new PackMenu(plugin, viewer).open());
        button(16, Items.item(Material.COMPASS, "<green>Ferramentas",
                "<gray>Testar anúncio, marcar mob, reenviar", "<gray>o pacote, recarregar e limpar áudios.",
                "", "<yellow>Clique para abrir"), click -> new ToolsMenu(plugin, viewer).open());
        button(22, Items.item(Material.BARRIER, "<red>Fechar"), click -> viewer.closeInventory());
    }

    private ItemStack status() {
        VoiceSettings voice = plugin.voiceSettings();
        VoiceSettings.Narrator narrator = voice.activeNarrator();
        int online = Bukkit.getOnlinePlayers().size();
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Conquistas: <white>" + plugin.registry().all().size()
                + " <gray>(" + plugin.registry().all().stream().filter(c -> plugin.registry().isCompleted(c.id())).count()
                + " concluídas)");
        boolean speaks = narrator != null && plugin.health().canSpeak(narrator);
        lore.add("<gray>Voz: " + (!voice.enabled() ? "<red>desligada"
                : speaks ? "<green>funcionando" : "<gold>sem voz (falta Python ou arquivos, veja Voz e narrador)"));
        if (narrator != null) {
            lore.add("<gray>Narrador: <white>" + narrator.name()
                    + (narrator.filesPresent() ? "" : " <red>(arquivos de voz não encontrados)"));
        }
        lore.add("<gray>Áudios gerados: <white>" + plugin.voiceBank().size());
        int size = plugin.pack().size();
        lore.add("<gray>Pacote: " + (size > 0 ? "<white>" + (size / 1024) + " KB" : "<red>ainda não montado"));
        lore.add("<gray>Jogadores com o pacote: <white>" + plugin.playersWithPack() + "/" + online);
        int outdated = plugin.pack().outdatedCount();
        if (outdated > 0) {
            lore.add("<yellow>" + outdated + " jogador(es) com o pacote antigo");
            lore.add("<gray>(Ferramentas > Reenviar pacote)");
        }
        return Items.item(Material.NETHER_STAR, "<yellow>Situação", lore);
    }

}
