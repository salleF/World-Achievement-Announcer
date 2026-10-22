package br.sinosdomundo.menu;

import br.sinosdomundo.SinosDoMundo;
import br.sinosdomundo.bell.AnnouncementService;
import br.sinosdomundo.conquest.Conquest;
import br.sinosdomundo.conquest.Conquest.Trigger;
import br.sinosdomundo.conquest.ConquestRegistry;
import br.sinosdomundo.voice.VoiceSettings;
import io.papermc.paper.advancement.AdvancementDisplay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Editor de uma conquista: todos os campos do config, teste só para o admin, marcar mob, resetar e apagar. */
public final class ConquestEditorMenu extends Menu {

    private final String id;

    public ConquestEditorMenu(SinosDoMundo plugin, Player viewer, String id) {
        super(plugin, viewer);
        this.id = id;
    }

    @Override
    protected String title() {
        return "<gold>Conquista: " + id;
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected void render() {
        Optional<Conquest> found = plugin.registry().get(id);
        if (found.isEmpty()) {
            icon(22, Items.item(Material.BARRIER, "<red>Conquista não encontrada", "<gray>Ela pode ter sido apagada."));
            button(40, Items.back(), click -> new ConquestListMenu(plugin, viewer).open());
            return;
        }
        Conquest c = found.get();
        ConquestRegistry registry = plugin.registry();
        icon(4, Items.item(triggerIcon(c), Items.user(c.display()), summary(c, registry)));

        List<String> nameLore = new ArrayList<>(Items.wrap("<white>", c.display(), 40));
        nameLore.add("<gray>Aparece no sino e no chat. Aceita cores");
        nameLore.add("<gray>MiniMessage, ex.: <gold>&lt;gold&gt;texto");
        nameLore.add("");
        nameLore.add("<yellow>Clique para editar");
        button(10, Items.item(Material.NAME_TAG, "<yellow>Nome mostrado", nameLore),
                click -> askText("Nome mostrado da conquista:", c.display(), v -> set("nome", v, false)));

        button(11, Items.item(Material.REPEATER, "<yellow>Gatilho: <white>" + triggerName(c.trigger()),
                "<gray>" + triggerHelp(c.trigger()), "", "<yellow>Clique para trocar"), click -> chooseTrigger());

        button(12, targetItem(c), click -> chooseTarget(c.trigger()));

        List<String> spokenLore = new ArrayList<>(c.spoken().isBlank()
                ? List.of("<red>Vazio: a voz não fala o nome da conquista")
                : Items.wrap("<white>", c.spoken(), 40));
        spokenLore.add("<gray>O que a voz fala no lugar de {conquista}.");
        spokenLore.add("");
        spokenLore.add("<yellow>Clique para editar");
        button(13, Items.item(Material.NOTE_BLOCK, "<yellow>Texto falado", spokenLore),
                click -> askText("Texto que a voz fala para esta conquista:", c.spoken(), v -> set("voz", v, true)));

        List<String> phraseLore = new ArrayList<>(c.phrase().isBlank()
                ? List.of("<white>Usando a frase padrão")
                : Items.wrap("<white>", c.phrase(), 40));
        phraseLore.add("<gray>Use {conquista}, {jogadores} e {final}.");
        phraseLore.add("");
        phraseLore.add("<yellow>Clique para editar <gray>(digite <white>padrao</white> para voltar à padrão)");
        button(14, Items.item(Material.WRITABLE_BOOK, "<yellow>Frase própria", phraseLore),
                click -> askText("Frase falada desta conquista (ou <white>padrao</white>):", c.phrase(),
                        v -> set("frase", v.equalsIgnoreCase("padrao") ? null : v, true)));

        button(15, closingItem(c), click -> {
            if (click.right()) {
                askText("Frase final própria desta conquista:", c.customClosing(), v -> set("final", v, true));
            } else {
                String next = c.closing().equalsIgnoreCase(Conquest.RANDOM_CLOSING) ? Conquest.NO_CLOSING : Conquest.RANDOM_CLOSING;
                set("final", next, false);
            }
        });

        String narrator = c.narrator().isBlank() ? "o ativo (" + plugin.voiceSettings().narrator() + ")" : c.narrator();
        button(16, Items.item(Material.PLAYER_HEAD, "<yellow>Narrador: <white>" + narrator,
                "<gray>Voz que narra só esta conquista.", "", "<yellow>Clique para escolher"), click -> chooseNarrator(c));

        button(21, Items.toggle(c.once(), "<yellow>Só uma vez no servidor",
                "<gray>Ligado: depois de concluída, não toca mais", "<gray>(até ser resetada)."),
                click -> set("uma-vez", !c.once(), false));

        button(22, Items.item(Material.BELL, "<green>Testar aqui",
                "<gray>Toca o anúncio só para você, com o seu", "<gray>nome. Não conclui a conquista."), click -> test(c));

        button(23, Items.item(Material.LEAD, "<green>Marcar o mob que estou olhando",
                "<gray>Quando ele morrer, esta conquista toca", "<gray>com quem ajudou a matar. Olhe para o mob",
                "<gray>(até 20 blocos) e clique."), click -> markTarget(c));

        button(30, Items.item(Material.CLOCK, "<gold>Resetar conclusão",
                registry.isCompleted(c.id()) ? "<gray>Libera a conquista para tocar de novo." : "<gray>Ainda não foi concluída."),
                click -> {
                    if (!registry.isCompleted(c.id())) {
                        return;
                    }
                    new ConfirmMenu(plugin, viewer, "Resetar " + c.id() + "?",
                            "A conquista volta a ficar disponível. Quem concluiu antes é esquecido.", () -> {
                                registry.reset(c.id());
                                open();
                            }, this::open).open();
                });

        button(32, Items.item(Material.LAVA_BUCKET, "<red>Apagar conquista", "<gray>Remove do config. Não tem volta."),
                click -> new ConfirmMenu(plugin, viewer, "Apagar " + c.id() + "?",
                        "A conquista some do config.yml e o registro de quem concluiu é apagado.", () -> {
                            plugin.getConfig().set("conquistas." + c.id(), null);
                            registry.reset(c.id());
                            plugin.saveAndApply(false);
                            new ConquestListMenu(plugin, viewer).open();
                        }, this::open).open());

        button(40, Items.back(), click -> new ConquestListMenu(plugin, viewer).open());
    }

    /** Salva um campo da conquista. Campos que mudam o que a voz fala geram os áudios novos em seguida. */
    private void set(String field, Object value, boolean voice) {
        plugin.getConfig().set("conquistas." + id + "." + field, value);
        if (voice) {
            viewer.sendMessage(Items.text("<gray>Gerando os áudios novos..."));
        }
        plugin.saveAndApply(voice).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (voice && viewer.isOnline()) {
                viewer.sendMessage(Items.text("<green>Áudios prontos.</green> <gray>Quem está online ouve depois que o pacote "
                        + "for reenviado (Ferramentas > Reenviar pacote)."));
            }
        }));
        if (viewer.getOpenInventory().getTopInventory().getHolder(false) == this) {
            refresh();
        }
    }

    private void chooseTrigger() {
        List<ChoiceMenu.Option<Trigger>> options = new ArrayList<>();
        for (Trigger t : Trigger.values()) {
            options.add(new ChoiceMenu.Option<>(Items.item(triggerMaterial(t), "<yellow>" + triggerName(t),
                    "<gray>" + triggerHelp(t)), t));
        }
        new ChoiceMenu<>(plugin, viewer, "<gold>Gatilho", options, t -> {
            plugin.getConfig().set("conquistas." + id + ".gatilho", t.name());
            plugin.saveAndApply(false);
            if (t == Trigger.MATAR_MOB || t == Trigger.CONQUISTA || t == Trigger.DIMENSAO) {
                chooseTarget(t);
            } else {
                open();
            }
        }, this::open).open();
    }

    private void chooseTarget(Trigger trigger) {
        switch (trigger) {
            case MATAR_MOB -> {
                List<ChoiceMenu.Option<String>> options = new ArrayList<>();
                for (EntityType type : EntityType.values()) {
                    if (type.isAlive() && type.isSpawnable() && type != EntityType.PLAYER) {
                        options.add(new ChoiceMenu.Option<>(Items.item(mobIcon(type), "<yellow>" + pretty(type.name()),
                                "<dark_gray>" + type.name()), type.name()));
                    }
                }
                options.sort(Comparator.comparing(o -> o.value()));
                new ChoiceMenu<>(plugin, viewer, "<gold>Qual mob?", options, v -> {
                    set("mob", v, false);
                    open();
                }, this::open).open();
            }
            case CONQUISTA -> {
                List<ChoiceMenu.Option<String>> options = new ArrayList<>();
                Iterator<Advancement> it = Bukkit.advancementIterator();
                while (it.hasNext()) {
                    Advancement adv = it.next();
                    AdvancementDisplay display = adv.getDisplay();
                    if (display == null) {
                        continue;
                    }
                    String title = PlainTextComponentSerializer.plainText().serialize(display.title());
                    options.add(new ChoiceMenu.Option<>(Items.item(display.icon().getType(), "<yellow>" + Items.plain(title),
                            "<dark_gray>" + adv.getKey()), adv.getKey().toString()));
                }
                options.sort(Comparator.comparing(o -> o.value()));
                new ChoiceMenu<>(plugin, viewer, "<gold>Qual advancement?", options, v -> {
                    set("conquista", v, false);
                    open();
                }, this::open).open();
            }
            case DIMENSAO -> {
                List<ChoiceMenu.Option<String>> options = new ArrayList<>();
                for (World world : Bukkit.getWorlds()) {
                    Material icon = switch (world.getEnvironment()) {
                        case NETHER -> Material.NETHERRACK;
                        case THE_END -> Material.END_STONE;
                        default -> Material.GRASS_BLOCK;
                    };
                    options.add(new ChoiceMenu.Option<>(Items.item(icon, "<yellow>" + world.getName()), world.getName()));
                }
                new ChoiceMenu<>(plugin, viewer, "<gold>Qual mundo?", options, v -> {
                    set("mundo", v, false);
                    open();
                }, this::open).open();
            }
            default -> viewer.sendMessage(Items.text("<gray>Esse gatilho não usa alvo."));
        }
    }

    private void chooseNarrator(Conquest c) {
        List<ChoiceMenu.Option<String>> options = new ArrayList<>();
        options.add(new ChoiceMenu.Option<>(Items.item(Material.NETHER_STAR, "<yellow>Usar o narrador ativo",
                "<gray>Agora: " + plugin.voiceSettings().narrator()), ""));
        for (VoiceSettings.Narrator n : plugin.voiceSettings().narrators().values()) {
            options.add(new ChoiceMenu.Option<>(Items.item(Material.PLAYER_HEAD, "<yellow>" + n.name(),
                    "<gray>" + n.kind() + " <dark_gray>(" + n.engine() + ")"), n.name()));
        }
        new ChoiceMenu<>(plugin, viewer, "<gold>Narrador", options, v -> {
            set("narrador", v.isEmpty() ? null : v, true);
            open();
        }, this::open).open();
    }

    private void test(Conquest c) {
        if (plugin.pack().isOutdated(viewer)) {
            plugin.pack().send(viewer);
            viewer.sendMessage(Items.text("<yellow>Atualizando o seu pacote para ouvir os áudios novos. "
                    + "Teste de novo em alguns segundos."));
            viewer.closeInventory();
            return;
        }
        viewer.closeInventory();
        plugin.preview(viewer, new AnnouncementService.Request(c.display(), c.spoken(), c.phrase(), c.closing(),
                c.narrator(), List.of(viewer.getName())));
    }

    private void markTarget(Conquest c) {
        Entity target = viewer.getTargetEntity(20);
        if (!(target instanceof LivingEntity living) || target instanceof Player) {
            viewer.sendMessage(Items.text("<red>Olhe para um mob a até 20 blocos e clique de novo."));
            return;
        }
        plugin.conquests().mark(living, c);
        viewer.closeInventory();
        viewer.sendMessage(Items.text("<green>Mob marcado.</green> <gray>Quando ele morrer, " + c.id() + " toca o sino."));
    }

    private org.bukkit.inventory.ItemStack targetItem(Conquest c) {
        return switch (c.trigger()) {
            case MATAR_MOB -> Items.item(Material.ZOMBIE_SPAWN_EGG, "<yellow>Mob: <white>" + pretty(c.target()),
                    "<gray>A primeira morte deste mob dispara.", "", "<yellow>Clique para escolher");
            case CONQUISTA -> Items.item(Material.KNOWLEDGE_BOOK, "<yellow>Advancement: <white>" + c.target(),
                    "<gray>Quem completar este advancement dispara.", "", "<yellow>Clique para escolher");
            case DIMENSAO -> Items.item(Material.OBSIDIAN, "<yellow>Mundo: <white>" + c.target(),
                    "<gray>A primeira entrada neste mundo dispara.", "", "<yellow>Clique para escolher");
            default -> Items.item(Material.STRUCTURE_VOID, "<gray>Alvo: não usa",
                    "<gray>Este gatilho não precisa de alvo.");
        };
    }

    private static org.bukkit.inventory.ItemStack closingItem(Conquest c) {
        String current = c.closing().equalsIgnoreCase(Conquest.RANDOM_CLOSING) ? "sorteada da lista"
                : c.closing().equalsIgnoreCase(Conquest.NO_CLOSING) ? "nenhuma" : "própria";
        List<String> lore = new ArrayList<>();
        if (!c.customClosing().isEmpty()) {
            lore.addAll(Items.wrap("<white>", c.customClosing(), 40));
        }
        lore.add("<gray>Frase dita depois dos nomes.");
        lore.add("");
        lore.add("<yellow>Esquerdo: <gray>sorteada / nenhuma");
        lore.add("<yellow>Direito: <gray>escrever uma frase própria");
        return Items.item(Material.PAPER, "<yellow>Frase final: <white>" + current, lore);
    }

    static List<String> summary(Conquest c, ConquestRegistry registry) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Gatilho: <white>" + triggerName(c.trigger()) + (c.target().isEmpty() ? "" : " <gray>(" + c.target() + ")"));
        lore.add("<gray>Só uma vez: <white>" + (c.once() ? "sim" : "não"));
        lore.add("<gray>Narrador: <white>" + (c.narrator().isBlank() ? "o ativo" : c.narrator()));
        if (registry.isCompleted(c.id())) {
            lore.add("<green>Concluída por: <white>" + Items.plain(String.join(", ", registry.completedBy(c.id()))));
            registry.completedAt(c.id()).ifPresent(d -> lore.add("<dark_gray>em " + d.substring(0, Math.min(10, d.length()))));
        } else {
            lore.add("<yellow>Disponível");
        }
        return lore;
    }

    static Material triggerIcon(Conquest c) {
        if (c.trigger() == Trigger.MATAR_MOB) {
            try {
                return mobIcon(EntityType.valueOf(c.target()));
            } catch (IllegalArgumentException e) {
                return Material.ZOMBIE_HEAD;
            }
        }
        return triggerMaterial(c.trigger());
    }

    private static Material triggerMaterial(Trigger t) {
        return switch (t) {
            case MATAR_MOB -> Material.IRON_SWORD;
            case MOB_MARCADO -> Material.LEAD;
            case CONQUISTA -> Material.KNOWLEDGE_BOOK;
            case DIMENSAO -> Material.OBSIDIAN;
            case MANUAL -> Material.LEVER;
        };
    }

    private static Material mobIcon(EntityType type) {
        Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        if (egg != null) {
            return egg;
        }
        return switch (type) {
            case ENDER_DRAGON -> Material.DRAGON_HEAD;
            case WITHER -> Material.WITHER_SKELETON_SKULL;
            default -> Material.NAME_TAG;
        };
    }

    static String triggerName(Trigger t) {
        return switch (t) {
            case MATAR_MOB -> "Matar mob";
            case MOB_MARCADO -> "Mob marcado";
            case CONQUISTA -> "Advancement";
            case DIMENSAO -> "Entrar num mundo";
            case MANUAL -> "Manual";
        };
    }

    private static String triggerHelp(Trigger t) {
        return switch (t) {
            case MATAR_MOB -> "A primeira morte de um tipo de mob.";
            case MOB_MARCADO -> "Morte de um mob marcado (Marcar mob).";
            case CONQUISTA -> "Alguém completa um advancement do jogo.";
            case DIMENSAO -> "A primeira entrada num mundo (Nether, End...).";
            case MANUAL -> "Só por /sinos anunciar ou pela API.";
        };
    }

    private static String pretty(String enumName) {
        if (enumName == null || enumName.isEmpty()) {
            return "(nenhum)";
        }
        String s = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
