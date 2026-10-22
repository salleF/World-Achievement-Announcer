package br.sinosdomundo.menu;

import br.sinosdomundo.SinosDoMundo;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Contribuidores: quem entra na lista de cada conquista e quantos nomes aparecem. */
public final class ContributorsMenu extends Menu {

    private static final List<String> MODES = List.of("dano", "presenca", "ambos");

    public ContributorsMenu(SinosDoMundo plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected String title() {
        return "<red>Contribuidores";
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void render() {
        icon(4, Items.item(Material.IRON_SWORD, "<red>Contribuidores",
                "<gray>Quem aparece como autor quando um mob", "<gray>de conquista morre, e quantos nomes", "<gray>são falados e mostrados."));

        String mode = plugin.getConfig().getString("contribuidores.modo", "dano").toLowerCase(Locale.ROOT);
        String label = switch (mode) {
            case "presenca" -> "presença (quem estava perto)";
            case "ambos" -> "dano + presença";
            default -> "dano (quem bateu no mob)";
        };
        button(10, Items.item(Material.COMPARATOR, "<yellow>Modo: <white>" + label,
                "<gray>dano: quem causou dano suficiente", "<gray>presença: quem estava perto na morte",
                "<gray>ambos: qualquer um dos dois", "", "<yellow>Clique para trocar"), click -> {
            int next = (MODES.indexOf(mode) + 1) % MODES.size();
            plugin.getConfig().set("contribuidores.modo", MODES.get(next));
            applyAndRefresh(false);
        });
        number(11, Material.GOLDEN_SWORD, "<yellow>Dano mínimo (%)", "contribuidores.dano-minimo-porcentagem", 2, 0.5, 0, 50,
                "<gray>Porcentagem do dano total para contar.");
        number(12, Material.IRON_AXE, "<yellow>Dano mínimo", "contribuidores.dano-minimo", 10, 1, 0, 1000,
                "<gray>Ou este dano absoluto (basta um dos dois).");
        number(13, Material.CLOCK, "<yellow>Janela (minutos)", "contribuidores.janela-minutos", 5, 1, 1, 60,
                "<gray>Só conta dano destes últimos minutos.");
        number(14, Material.COMPASS, "<yellow>Raio de presença", "contribuidores.raio-presenca", 64, 4, 4, 256,
                "<gray>Blocos, para os modos presença e ambos.");
        toggle(15, "<yellow>Ignorar criativo e espectador", "contribuidores.ignorar-criativo", true,
                "<gray>Jogadores nesses modos não contam.");
        number(19, Material.NOTE_BLOCK, "<yellow>Nomes falados", "contribuidores.max-nomes-voz", 5, 1, 1, 20,
                "<gray>Depois disso, a voz diz \"e outros jogadores\".");
        number(20, Material.OAK_SIGN, "<yellow>Nomes no texto", "contribuidores.max-nomes-texto", 10, 1, 1, 50,
                "<gray>Depois disso, o texto diz \"e mais N\".");
        number(21, Material.OBSIDIAN, "<yellow>Janela da dimensão (s)", "contribuidores.janela-dimensao-segundos", 5, 1, 1, 60,
                "<gray>Primeira entrada num mundo: quem entrar", "<gray>nestes segundos também conta.");

        button(31, Items.back(), click -> new MainMenu(plugin, viewer).open());
    }
}
