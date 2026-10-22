package br.sinosdomundo.menu;

import br.sinosdomundo.SinosDoMundo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Editor do ritmo: o tick de cada batida, contado a partir do momento em que o sino aparece. */
public final class RhythmMenu extends Menu {

    /** Ritmo que acompanha a música padrão do plugin. */
    public static final List<Integer> DEFAULT_RHYTHM = List.of(17, 27, 37, 46, 53, 60, 66);
    public static final int DEFAULT_SPEECH_DELAY = 20;
    /** O sino leva 8 ticks para crescer; a primeira batida precisa vir pelo menos 3 ticks depois. */
    private static final int FIRST_TICK = 11;
    private static final int MAX = 36;

    public RhythmMenu(SinosDoMundo plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected String title() {
        return "<gold>Ritmo das batidas";
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void render() {
        List<Integer> ticks = new ArrayList<>(plugin.getConfig().getIntegerList("sino.ritmo"));
        ticks.sort(Integer::compare);
        icon(4, Items.item(Material.NOTE_BLOCK, "<gold>Ritmo",
                "<gray>Cada relógio é uma batida do sino.", "<gray>Ticks contados desde que o sino aparece",
                "<gray>(20 ticks = 1 segundo).", "",
                ticks.isEmpty() ? "<yellow>Vazio: usa Batidas e Intervalo" : "<green>" + ticks.size() + " batidas"));
        for (int i = 0; i < ticks.size() && i < MAX; i++) {
            int index = i;
            int tick = ticks.get(i);
            button(9 + i, Items.item(Material.CLOCK, "<yellow>Batida " + (i + 1) + ": <white>tick " + tick,
                    "<gray>" + String.format(Locale.ROOT, "%.2f", tick / 20.0) + " s depois que o sino aparece", "",
                    "<yellow>Esquerdo +1, direito -1, Shift ×10", "<yellow>Q (soltar item): <gray>remover"), click -> {
                if (click.type() == ClickType.DROP || click.type() == ClickType.CONTROL_DROP) {
                    ticks.remove(index);
                } else {
                    ticks.set(index, Math.max(FIRST_TICK, tick + click.step()));
                }
                save(ticks);
            });
        }

        button(45, Items.back(), click -> new BellMenu(plugin, viewer).open());
        if (ticks.size() < MAX) {
            button(47, Items.item(Material.EMERALD, "<green>Adicionar batida", "<gray>8 ticks depois da última."), click -> {
                ticks.add(ticks.isEmpty() ? FIRST_TICK + 6 : ticks.get(ticks.size() - 1) + 8);
                save(ticks);
            });
        }
        button(49, Items.item(Material.BARRIER, "<red>Limpar ritmo", "<gray>Volta a usar Batidas e Intervalo."),
                click -> save(new ArrayList<>()));
        button(51, Items.item(Material.MUSIC_DISC_CAT, "<yellow>Ritmo da música padrão",
                "<gray>Restaura as 7 batidas e o atraso da fala", "<gray>que acompanham a música do plugin."), click -> {
            plugin.getConfig().set("voz.atraso-ticks", DEFAULT_SPEECH_DELAY);
            save(new ArrayList<>(DEFAULT_RHYTHM));
        });
    }

    private void save(List<Integer> ticks) {
        ticks.sort(Integer::compare);
        plugin.getConfig().set("sino.ritmo", ticks);
        applyAndRefresh(false);
    }
}
