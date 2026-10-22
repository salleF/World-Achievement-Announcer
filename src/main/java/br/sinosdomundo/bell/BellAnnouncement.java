package br.sinosdomundo.bell;

import br.sinosdomundo.BellSettings;
import br.sinosdomundo.bell.SwingTimeline.Keyframe;
import br.sinosdomundo.pack.BellModel;
import br.sinosdomundo.voice.SpeechPlanner.Cue;
import br.sinosdomundo.voice.SpeechPlanner.Speech;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Um sino no mundo: aparece, balança batendo no badalo, fala, acompanha o grupo e some. Só os
 * membros do grupo o veem; o badalo separado só aparece para quem tem o resource pack.
 */
final class BellAnnouncement {

    private static final int GROW_TICKS = 8;
    private static final int FOLLOW_EVERY = 5;

    private final Plugin plugin;
    private final BellSettings settings;
    private final List<UUID> members;
    private final UUID leaderId;
    private final Component text;
    private final Speech speech;
    private final Predicate<Player> hasPack;
    /** Altura do eixo (topo da coroa) em frações do tamanho do sino, a partir do centro. */
    private final float pivot;
    private final boolean useClapper;
    private final List<Keyframe> frames;
    private final int totalHits;
    /** Tick em que a fala começa e tick em que o sino começa a sumir. */
    private final int speechStart;
    private final int lifeTicks;
    private final Runnable onFinish;

    private ItemDisplay bell;
    private ItemDisplay clapper;
    private TextDisplay label;
    private BukkitTask task;
    private Vector soloOffset;
    private int tick;
    private int nextFrame;
    private int hitsDone;
    private int nextCue;
    private float scale;
    private boolean finished;

    BellAnnouncement(Plugin plugin, BellSettings settings, BellModel model, Predicate<Player> hasPack,
            List<Player> members, Player leader, Component text, Speech speech, int speechDelay, Runnable onFinish) {
        this.plugin = plugin;
        this.settings = settings;
        this.members = members.stream().map(Player::getUniqueId).toList();
        this.leaderId = leader.getUniqueId();
        this.text = text;
        this.speech = speech;
        this.hasPack = hasPack;
        boolean packModel = settings.model3d() && settings.itemModel() == null && model != null;
        this.pivot = packModel ? (float) ((model.pivotY() - 8) / 16) : 0.5f;
        this.useClapper = packModel && model.hasClapper();
        double contact = useClapper ? model.contactAngle() : 0;
        // Com ritmo no config, as batidas caem nos ticks dele (contados do aparecimento do sino)
        List<Integer> rhythm = settings.rhythm().stream().map(t -> t - GROW_TICKS).filter(t -> t >= 3).toList();
        this.frames = rhythm.isEmpty()
                ? SwingTimeline.build(settings.rings(), settings.ringInterval(), contact)
                : SwingTimeline.build(rhythm, contact);
        this.totalHits = (int) frames.stream().filter(Keyframe::hit).count();
        // A fala começa depois da última batida, para não misturar com o som do sino
        this.speechStart = GROW_TICKS + SwingTimeline.lastHitTick(frames) + speechDelay;
        int swingEnd = GROW_TICKS + frames.get(frames.size() - 1).tick();
        int speechEnd = speech.cues().isEmpty() ? 0 : speechStart + speech.lengthTicks() + 20;
        this.lifeTicks = Math.max(settings.durationTicks(), Math.max(swingEnd, speechEnd));
        this.onFinish = onFinish;
    }

    void start(Location at) {
        World world = at.getWorld();
        bell = world.spawn(at, ItemDisplay.class, display -> {
            setUpDisplay(display);
            display.setItemStack(bellItem("sinos:sino"));
            display.setBillboard(Display.Billboard.VERTICAL);
            display.setTransformation(transformation(0, 0));
        });
        if (useClapper) {
            clapper = world.spawn(at, ItemDisplay.class, display -> {
                setUpDisplay(display);
                display.setItemStack(bellItem("sinos:badalo"));
                display.setBillboard(Display.Billboard.VERTICAL);
                display.setTransformation(transformation(0, 0));
            });
        }
        label = world.spawn(labelLocation(at), TextDisplay.class, display -> {
            setUpDisplay(display);
            display.text(text);
            display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setBackgroundColor(Color.fromARGB(110, 0, 0, 0));
            display.setShadowed(true);
            display.setLineWidth(260);
        });

        List<Player> online = onlineMembers();
        for (Player player : online) {
            player.showEntity(plugin, bell);
            player.showEntity(plugin, label);
            // Sem o pack, o badalo apareceria como um segundo sino comum
            if (clapper != null && hasPack.test(player)) {
                player.showEntity(plugin, clapper);
            }
        }
        if (online.size() == 1) {
            soloOffset = at.toVector().subtract(online.get(0).getLocation().toVector());
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void tick() {
        tick++;
        if (!bell.isValid() || !label.isValid() || (clapper != null && !clapper.isValid())) {
            finish();
            return;
        }

        if (tick == 1) {
            scale = settings.scale();
            animate(0, 0, GROW_TICKS);
        }
        if (tick == settings.musicStartTicks() && settings.musicEnabled()) {
            playMusic();
        }

        // Ao chegar em cada quadro: toca se for batida e manda as peças para o próximo quadro
        int swingTick = tick - GROW_TICKS;
        while (nextFrame < frames.size() && frames.get(nextFrame).tick() <= swingTick) {
            Keyframe frame = frames.get(nextFrame++);
            if (frame.hit()) {
                ring();
            }
            if (nextFrame < frames.size()) {
                Keyframe next = frames.get(nextFrame);
                animate(next.bellAngle(), next.clapperAngle(), next.tick() - frame.tick());
            }
        }

        while (nextCue < speech.cues().size() && speech.cues().get(nextCue).tick() + speechStart <= tick) {
            speak(speech.cues().get(nextCue++));
        }

        if (settings.followGroup() && tick % FOLLOW_EVERY == 0) {
            follow();
        }

        if (tick == lifeTicks) {
            scale = 0;
            animate(0, 0, GROW_TICKS);
        } else if (tick >= lifeTicks + GROW_TICKS) {
            vanishParticles();
            finish();
        }
    }

    private void ring() {
        hitsDone++;
        List<Key> sounds = new ArrayList<>(settings.ringSounds());
        if (hitsDone == totalHits && settings.finalSound() != null) {
            sounds.add(settings.finalSound());
        }
        Location at = bell.getLocation();
        for (Player player : onlineMembers()) {
            for (Key key : sounds) {
                Sound sound = Sound.sound(key, Sound.Source.MASTER, settings.effectiveVolume(), settings.pitch());
                player.playSound(sound, at.getX(), at.getY(), at.getZ());
            }
        }
    }

    /** Música da conquista: toca junto do jogador (não do sino), no volume do config. */
    private void playMusic() {
        Sound music = Sound.sound(Key.key(BellSettings.MUSIC_SOUND), Sound.Source.MASTER, settings.musicVolume(), 1f);
        for (Player player : onlineMembers()) {
            if (hasPack.test(player)) {
                player.playSound(music, Sound.Emitter.self());
            }
        }
    }

    /** Toca um pedaço da fala vindo do sino. Quem não tem o resource pack simplesmente não ouve. */
    private void speak(Cue cue) {
        Location at = bell.getLocation();
        Sound sound = Sound.sound(Key.key(cue.soundKey()), Sound.Source.MASTER, settings.effectiveVolume(), 1f);
        for (Player player : onlineMembers()) {
            player.playSound(sound, at.getX(), at.getY(), at.getZ());
        }
    }

    /** Leva o sino para o centro do grupo atual; jogador sozinho mantém a distância do começo. */
    private void follow() {
        List<Player> online = onlineMembers();
        if (online.isEmpty()) {
            return;
        }
        Location target;
        if (soloOffset != null) {
            target = online.get(0).getLocation().add(soloOffset);
            target.setYaw(0);
            target.setPitch(0);
            target = BellPlacement.safe(target, online, online.get(0));
        } else {
            Player leader = Bukkit.getPlayer(leaderId);
            if (leader == null || !online.contains(leader)) {
                leader = online.get(0);
            }
            target = BellPlacement.group(center(online, leader), online, leader, settings);
        }
        bell.teleport(target);
        if (clapper != null) {
            clapper.teleport(target);
        }
        label.teleport(labelLocation(target));
    }

    /** Média das posições; se alguém ficar longe demais dela, usa o líder (mesma regra do agrupamento). */
    private Location center(List<Player> online, Player leader) {
        Vector sum = new Vector();
        for (Player player : online) {
            sum.add(player.getLocation().toVector());
        }
        Location center = sum.multiply(1.0 / online.size()).toLocation(leader.getWorld());
        double radiusSquared = settings.groupRadius() * settings.groupRadius();
        for (Player player : online) {
            if (player.getLocation().distanceSquared(center) > radiusSquared) {
                Location leaderSpot = leader.getLocation();
                leaderSpot.setYaw(0);
                leaderSpot.setPitch(0);
                return leaderSpot;
            }
        }
        return center;
    }

    private void vanishParticles() {
        Location at = bell.getLocation();
        for (Player player : onlineMembers()) {
            player.spawnParticle(Particle.END_ROD, at, 30, 0.4, 0.6, 0.4, 0.05);
        }
    }

    void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (task != null) {
            task.cancel();
        }
        for (Display display : new Display[] {bell, clapper, label}) {
            if (display != null) {
                display.remove();
            }
        }
        onFinish.run();
    }

    /** Leva sino e badalo aos ângulos dados (graus no eixo Z) em {@code ticks} ticks, com a escala atual. */
    private void animate(float bellAngle, float clapperAngle, int ticks) {
        move(bell, bellAngle, ticks);
        if (clapper != null) {
            move(clapper, clapperAngle, ticks);
        }
    }

    private void move(ItemDisplay display, float angle, int ticks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(transformation(scale, angle));
    }

    /** Escala e giro em torno da coroa, como se estivesse pendurado. */
    private Transformation transformation(float scale, float angleDegrees) {
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.toRadians(angleDegrees));
        Vector3f axis = new Vector3f(0, scale * pivot, 0);
        Vector3f translation = new Vector3f(axis).sub(rotation.transform(new Vector3f(axis)));
        return new Transformation(translation, rotation, new Vector3f(scale), new Quaternionf());
    }

    /** @param packCase valor que o resource pack usa para escolher o modelo ("sinos:sino" ou "sinos:badalo") */
    private ItemStack bellItem(String packCase) {
        ItemStack item = new ItemStack(settings.item());
        ItemMeta meta = item.getItemMeta();
        if (settings.itemModel() != null) {
            meta.setItemModel(settings.itemModel());
        } else if (settings.model3d()) {
            // O pack troca o sino pelo modelo 3D; sem o pack, aparece o sino comum
            CustomModelDataComponent modelData = meta.getCustomModelDataComponent();
            modelData.setStrings(List.of(packCase));
            meta.setCustomModelDataComponent(modelData);
        }
        item.setItemMeta(meta);
        return item;
    }

    private Location labelLocation(Location bellLocation) {
        return bellLocation.clone().add(0, settings.textHeight() + settings.scale() * 0.5, 0);
    }

    private List<Player> onlineMembers() {
        World world = bell != null ? bell.getWorld() : null;
        List<Player> online = new ArrayList<>(members.size());
        for (UUID id : members) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && (world == null || player.getWorld().equals(world))) {
                online.add(player);
            }
        }
        return online;
    }

    private static void setUpDisplay(Display display) {
        display.setPersistent(false);
        display.setVisibleByDefault(false);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setTeleportDuration(FOLLOW_EVERY);
    }
}
