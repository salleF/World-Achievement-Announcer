package br.sinosdomundo.menu;

import br.sinosdomundo.SinosDoMundo;
import br.sinosdomundo.bell.AnnouncementService;
import br.sinosdomundo.conquest.Conquest;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Sino: aparência, tempo e batidas, sons e o texto acima dele. */
public final class BellMenu extends Menu {

    public BellMenu(SinosDoMundo plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected String title() {
        return "<gold>Sino";
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void render() {
        icon(4, Items.item(Material.BELL, "<gold>Sino", "<gray>Linha 2: aparência", "<gray>Linha 3: tempo e batidas",
                "<gray>Linha 4: sons", "<gray>Linha 5: texto"));

        number(10, Material.SLIME_BALL, "<yellow>Tamanho", "sino.escala", 2.0, 0.1, 0.2, 6, "<gray>Escala do sino 3D.");
        number(11, Material.LADDER, "<yellow>Altura acima do grupo", "sino.altura-acima", 4.0, 0.5, 0, 20,
                "<gray>Blocos acima da altura média do grupo.");
        number(12, Material.SPYGLASS, "<yellow>Distância (jogador sozinho)", "sino.sozinho-distancia", 5.0, 0.5, 1, 15,
                "<gray>Blocos à frente de onde ele olha.");
        number(13, Material.FEATHER, "<yellow>Altura (jogador sozinho)", "sino.sozinho-altura", 3.0, 0.5, -5, 10,
                "<gray>Blocos acima dos olhos.");
        toggle(14, "<yellow>Sino 3D do pacote", "sino.modelo-3d", true, "<gray>Desligado, aparece o sino comum do jogo.");
        toggle(15, "<yellow>Seguir o grupo", "sino.seguir-grupo", true, "<gray>O sino acompanha os jogadores enquanto toca.");
        number(16, Material.COMPASS, "<yellow>Raio do grupo", "sino.raio-grupo", 24, 1, 4, 128,
                "<gray>Jogadores a até esta distância", "<gray>dividem o mesmo sino.");

        number(19, Material.CLOCK, "<yellow>Duração mínima (s)", "sino.duracao-segundos", 8, 1, 2, 60,
                "<gray>O sino espera a fala terminar se ela for mais longa.");
        boolean rhythm = !plugin.getConfig().getIntegerList("sino.ritmo").isEmpty();
        number(20, Material.BELL, "<yellow>Batidas" + (rhythm ? " <dark_gray>(ritmo ativo)" : ""), "sino.badaladas", 3, 1, 0, 20,
                rhythm ? "<gray>Ignorado enquanto houver ritmo." : "<gray>Quantas vezes o badalo bate.");
        number(21, Material.REPEATER, "<yellow>Intervalo entre batidas" + (rhythm ? " <dark_gray>(ritmo ativo)" : ""),
                "sino.intervalo-badaladas", 8, 1, 3, 40, "<gray>Em ticks (20 = 1 segundo).");
        button(22, Items.item(Material.NOTE_BLOCK, "<yellow>Ritmo das batidas",
                rhythm ? "<green>Ativo: " + plugin.getConfig().getIntegerList("sino.ritmo").size() + " batidas" : "<gray>Desligado",
                "<gray>Batidas em momentos escolhidos, para", "<gray>seguir a música.", "", "<yellow>Clique para editar"),
                click -> new RhythmMenu(plugin, viewer).open());
        button(23, Items.item(Material.GOLD_INGOT, "<green>Testar o sino", "<gray>Toca um anúncio só para você."),
                click -> test());

        button(28, Items.item(Material.JUKEBOX, "<yellow>Sons de cada batida",
                Items.wrap("<white>", String.join(", ", plugin.getConfig().getStringList("sino.sons")), 40)),
                click -> new StringListMenu(plugin, viewer, "<gold>Sons da batida", "sino.sons", false,
                        "minecraft:block.bell.use", () -> new BellMenu(plugin, viewer).open()).open());
        text(29, Material.AMETHYST_SHARD, "<yellow>Som final", "sino.som-final", "minecraft:block.bell.resonate",
                "Som tocado na última batida (ex.: minecraft:block.bell.resonate):", false, "<gray>Toca junto da última batida.");
        number(30, Material.NOTE_BLOCK, "<yellow>Volume", "sino.volume", 1.0, 0.1, 0, 4,
                "<gray>Fica mais alto sozinho se o raio do grupo exigir.");
        number(31, Material.TRIPWIRE_HOOK, "<yellow>Tom", "sino.tom", 0.8, 0.05, 0.5, 2, "<gray>1 = normal, menor = mais grave.");
        toggle(32, "<yellow>Ignorar espectadores", "sino.ignorar-espectadores", true,
                "<gray>Espectadores só recebem a mensagem no chat.");

        button(37, Items.item(Material.OAK_SIGN, "<yellow>Linhas acima do sino",
                "<gray>Use &lt;conquista&gt;, &lt;jogadores&gt; e &lt;final&gt;.", "", "<yellow>Clique para editar"),
                click -> new StringListMenu(plugin, viewer, "<gold>Linhas acima do sino", "texto.linhas", false,
                        "<gold><bold>CONQUISTA MUNDIAL", () -> new BellMenu(plugin, viewer).open()).open());
        button(38, Items.item(Material.WRITTEN_BOOK, "<yellow>Mensagem no chat",
                "<gray>Enviada a todos quando o sino toca.", "", "<yellow>Clique para editar"),
                click -> new StringListMenu(plugin, viewer, "<gold>Mensagem no chat", "texto.chat", false,
                        "<gold>[Sino]</gold> <conquista>", () -> new BellMenu(plugin, viewer).open()).open());
        number(39, Material.LADDER, "<yellow>Altura do texto", "texto.altura-acima-do-sino", 1.6, 0.1, 0, 5,
                "<gray>Blocos acima do sino.");

        button(49, Items.back(), click -> new MainMenu(plugin, viewer).open());
    }

    private void test() {
        viewer.closeInventory();
        if (plugin.pack().isOutdated(viewer)) {
            plugin.pack().send(viewer);
            viewer.sendMessage(Items.text("<yellow>Atualizando o seu pacote. Teste de novo em alguns segundos."));
            return;
        }
        plugin.preview(viewer, new AnnouncementService.Request("<aqua>Teste do sino", "", "", Conquest.RANDOM_CLOSING, "",
                List.of(viewer.getName())));
    }
}
