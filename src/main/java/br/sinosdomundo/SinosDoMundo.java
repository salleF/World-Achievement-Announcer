package br.sinosdomundo;

import br.sinosdomundo.api.ConquestAnnounceEvent;
import br.sinosdomundo.api.SinosApi;
import br.sinosdomundo.bell.AnnouncementService;
import br.sinosdomundo.conquest.Conquest;
import br.sinosdomundo.conquest.ConquestRegistry;
import br.sinosdomundo.conquest.ConquestService;
import br.sinosdomundo.conquest.TriggerListener;
import br.sinosdomundo.contrib.ContributorSettings;
import br.sinosdomundo.menu.ChatInput;
import br.sinosdomundo.menu.MenuListener;
import br.sinosdomundo.music.MusicService;
import br.sinosdomundo.pack.PackSettings;
import br.sinosdomundo.pack.ResourcePackService;
import br.sinosdomundo.voice.PythonHealth;
import br.sinosdomundo.voice.SpeechPlanner;
import br.sinosdomundo.voice.VoiceInstaller;
import br.sinosdomundo.voice.VoiceBank;
import br.sinosdomundo.voice.VoiceSettings;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class SinosDoMundo extends JavaPlugin implements Listener, SinosApi {

    private static final long PURGE_PERIOD_TICKS = 20 * 60;
    private static final UUID NIL_UUID = new UUID(0, 0);
    private static final int BSTATS_ID = 34873;

    private BellSettings settings;
    private VoiceSettings voiceSettings;
    private ContributorSettings contributorSettings;
    private AnnouncementService announcements;
    private ConquestRegistry registry;
    private ConquestService conquests;
    private ExecutorService worker;
    private VoiceBank voiceBank;
    private ResourcePackService pack;
    private ChatInput chatInput;
    private MusicService music;
    private volatile PythonHealth health = PythonHealth.unknown();
    private VoiceInstaller installer;
    private UpdateChecker updates;
    /** Jogadores que carregaram o pacote do plugin (só eles veem o badalo separado). */
    private final Set<UUID> withPack = ConcurrentHashMap.newKeySet();

    /** Nome antigo do plugin: a pasta plugins/SinosDoMundo é levada para a pasta nova na primeira vez. */
    private static final String OLD_FOLDER = "SinosDoMundo";

    @Override
    public void onLoad() {
        File old = new File(getDataFolder().getParentFile(), OLD_FOLDER);
        if (!old.isDirectory() || getDataFolder().exists()) {
            return;
        }
        if (!old.renameTo(getDataFolder())) {
            getLogger().warning("Não foi possível mover " + old + " para " + getDataFolder() + "; mova a pasta manualmente.");
            return;
        }
        // Caminhos gravados no config (Python do ambiente, modelos da voz) apontavam para a pasta antiga
        File config = new File(getDataFolder(), "config.yml");
        try {
            String text = Files.readString(config.toPath(), StandardCharsets.UTF_8);
            String moved = text.replaceAll("(?<=[/\\\\])" + OLD_FOLDER + "(?=[/\\\\])", getDataFolder().getName());
            if (!moved.equals(text)) {
                Files.writeString(config.toPath(), moved, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            getLogger().warning("Não foi possível atualizar os caminhos do config.yml: " + e.getMessage());
        }
        getLogger().info("Pasta antiga " + OLD_FOLDER + " movida para " + getDataFolder().getName() + ".");
    }

    @Override
    public void onEnable() {
        // O console também fala o idioma do servidor
        getLogger().setFilter(record -> {
            record.setMessage(Lang.t(record.getMessage()));
            return true;
        });
        createConfigIfMissing();
        registry = new ConquestRegistry(getDataFolder(), getLogger());
        loadSettings();

        // Uma thread só para gerar voz e montar o pack, em ordem
        worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "SinosDoMundo-trabalho"));
        voiceBank = new VoiceBank(this, worker);
        voiceBank.load();
        pack = new ResourcePackService(this, voiceBank, worker, PackSettings.load(getConfig()));
        pack.start();
        music = new MusicService(this, worker);
        music.setup();
        installer = new VoiceInstaller(getDataFolder());
        announcements = new AnnouncementService(this, () -> settings, () -> voiceSettings, () -> contributorSettings,
                voiceBank, pack::bellModel, this::hasPack);
        conquests = new ConquestService(this, registry, announcements);
        chatInput = new ChatInput(this);

        BellsCommand command = new BellsCommand(this);
        Objects.requireNonNull(getCommand("sinos")).setExecutor(command);
        Objects.requireNonNull(getCommand("sinos")).setTabCompleter(command);
        TriggerListener triggers = new TriggerListener(this, conquests, () -> contributorSettings);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(triggers, this);
        getServer().getPluginManager().registerEvents(chatInput, this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        Bukkit.getScheduler().runTaskTimer(this, triggers::purge, PURGE_PERIOD_TICKS, PURGE_PERIOD_TICKS);

        SinosApi.Holder.set(this);
        checkHealth().thenCompose(v -> prepareVoiceAndPack(true));
        // Estatísticas anônimas (bStats); o admin desliga em plugins/bStats/config.yml
        Metrics metrics = new Metrics(this, BSTATS_ID);
        metrics.addCustomChart(new SimplePie("language", Lang::code));
        metrics.addCustomChart(new SimplePie("voice", () -> voiceSettings.enabled() && health.canSpeak(voiceSettings.activeNarrator())
                ? "enabled" : "disabled"));
        metrics.addCustomChart(new SimplePie("pack_hosting", () -> getConfig().getString("pacote.modo", "proprio")));
        updates = new UpdateChecker(this);
        if (getConfig().getBoolean("verificar-atualizacoes", true)) {
            updates.check();
        }
    }

    @Override
    public void onDisable() {
        SinosApi.Holder.set(null);
        if (installer != null) {
            installer.shutdown();
        }
        if (announcements != null) {
            announcements.shutdown();
        }
        if (pack != null) {
            pack.stop();
        }
        if (worker != null) {
            worker.shutdown();
            try {
                worker.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public AnnouncementService announcements() {
        return announcements;
    }

    public ConquestService conquests() {
        return conquests;
    }

    public ConquestRegistry registry() {
        return registry;
    }

    public VoiceBank voiceBank() {
        return voiceBank;
    }

    public ResourcePackService pack() {
        return pack;
    }

    public MusicService music() {
        return music;
    }

    /** Reabre o servidor HTTP do pacote (depois de mudar a porta ou ligar/desligar). */
    public void restartPackServer() {
        pack.stop();
        pack.updateSettings(PackSettings.load(getConfig()));
        pack.start();
    }

    public ChatInput chatInput() {
        return chatInput;
    }

    public VoiceSettings voiceSettings() {
        return voiceSettings;
    }

    public boolean hasPack(Player player) {
        return withPack.contains(player.getUniqueId());
    }

    public int playersWithPack() {
        return withPack.size();
    }

    void reloadSettings() {
        reloadConfig();
        loadSettings();
        restartPackServer();
        checkHealth().thenCompose(v -> prepareVoiceAndPack(true));
    }

    public PythonHealth health() {
        return health;
    }

    public VoiceInstaller installer() {
        return installer;
    }

    /** Testa o Python de novo (depois de instalar algo) e devolve o resultado. */
    public CompletableFuture<PythonHealth> recheckHealth() {
        return checkHealth().thenApply(v -> health);
    }

    /**
     * Instalação completa da voz: acha o Python, instala os módulos num ambiente do plugin se faltarem,
     * baixa os modelos do Kokoro se faltarem e gera as falas. O progresso vai para {@code progress}.
     *
     * @return true se a voz terminou funcionando
     */
    public CompletableFuture<Boolean> installVoice(Consumer<String> progress) {
        progress.accept("Procurando o Python...");
        return installer.detectPython(voiceSettings.python()).thenCompose(python -> {
            if (python == null) {
                progress.accept("Python não encontrado. Instale o Python 3.10 ou mais novo (python.org, ou python3 no "
                        + "Linux) e rode de novo. Em hospedagens sem Python, a voz não funciona; o resto do plugin sim.");
                return CompletableFuture.completedFuture(false);
            }
            progress.accept("Python encontrado: " + python);
            PythonHealth found = PythonHealth.check(python);
            boolean modulesOk = found.modules().containsAll(List.of("kokoro_onnx", "soundfile", "numpy"));
            CompletableFuture<String> modules = modulesOk
                    ? CompletableFuture.completedFuture(python)
                    : installer.installModules(python, progress);
            return modules.thenCompose(ready -> runOnMain(() -> {
                getConfig().set("voz.python", ready);
                saveConfig();
                loadSettings();
            })).thenCompose(v -> downloadModelsIfNeeded(progress))
                    .thenCompose(v -> {
                        progress.accept("Gerando as falas (o Kokoro leva cerca de 1 minuto)...");
                        return runOnMain(() -> { }).thenCompose(x -> saveAndApply(true));
                    })
                    .thenApply(v -> {
                        VoiceSettings.Narrator active = voiceSettings.activeNarrator();
                        boolean speaks = active != null && health.canSpeak(active);
                        progress.accept(speaks ? "Pronto! A voz está funcionando com o narrador " + active.name()
                                + ". Reenvie o pacote para quem está online ouvir."
                                : "A instalação terminou, mas ainda falta: "
                                        + (active == null ? "um narrador" : String.join("; ", health.missingFor(active))));
                        return speaks;
                    });
        }).exceptionally(error -> {
            Throwable cause = error;
            while (cause.getCause() != null && cause.getMessage() != null && cause.getMessage().equals(cause.getCause().toString())) {
                cause = cause.getCause();
            }
            progress.accept("A instalação parou: " + (cause.getMessage() == null ? cause.toString() : cause.getMessage()));
            return false;
        });
    }

    /**
     * Baixa os modelos do Kokoro para a pasta vozes do plugin se os narradores Kokoro não os encontram,
     * e aponta o config para eles.
     */
    public CompletableFuture<Void> downloadModelsIfNeeded(Consumer<String> progress) {
        boolean kokoroMissing = voiceSettings.narrators().values().stream()
                .anyMatch(n -> n.engine().equals("kokoro") && !n.filesPresent());
        if (!kokoroMissing) {
            progress.accept("Modelos de voz já instalados.");
            return CompletableFuture.completedFuture(null);
        }
        return installer.downloadModels(progress).thenCompose(v -> runOnMain(() -> {
            getConfig().set("voz.kokoro-modelo", "vozes/" + VoiceInstaller.KOKORO_FILES.get(0).name());
            getConfig().set("voz.kokoro-vozes", "vozes/" + VoiceInstaller.KOKORO_FILES.get(1).name());
            saveConfig();
            loadSettings();
        }));
    }

    private CompletableFuture<Void> runOnMain(Runnable task) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                task.run();
                done.complete(null);
            } catch (RuntimeException e) {
                done.completeExceptionally(e);
            }
        });
        return done;
    }

    /** Testa o Python do config e avisa uma vez, no console, o que falta para a voz. */
    private CompletableFuture<Void> checkHealth() {
        String python = voiceSettings.python();
        return CompletableFuture.supplyAsync(() -> PythonHealth.check(python), worker).thenAccept(result -> {
            health = result;
            VoiceSettings.Narrator active = voiceSettings.activeNarrator();
            if (!getConfig().getBoolean("voz.ativada", true)) {
                return;
            }
            if (active != null && result.canSpeak(active)) {
                getLogger().info("Voz pronta: " + result.detail() + ", narrador " + active.name() + ".");
                return;
            }
            getLogger().warning("A voz dos anúncios está desligada porque falta: "
                    + (active == null ? "um narrador em voz.narradores" : String.join("; ", result.missingFor(active)))
                    + ". O sino, a música e o texto continuam funcionando. Para instalar a voz: /sinos > Voz e narrador >"
                    + " Instalação guiada, ou /sinos instalar-voz no console.");
        });
    }

    /**
     * Salva o que o menu mudou no config.yml e aplica na hora.
     *
     * @param voice true se a mudança pode precisar de áudios novos (textos, narrador)
     * @return termina quando os áudios novos estiverem prontos; o pacote não é reenviado (use {@link #resendPack()})
     */
    public CompletableFuture<Void> saveAndApply(boolean voice) {
        saveConfig();
        loadSettings();
        pack.updateSettings(PackSettings.load(getConfig()));
        return voice ? checkHealth().thenCompose(v -> prepareVoiceAndPack(false)) : CompletableFuture.completedFuture(null);
    }

    /** Manda o pacote atual para todos os jogadores online (o jogo deles recarrega os recursos). */
    public void resendPack() {
        Bukkit.getOnlinePlayers().forEach(pack::send);
    }

    /**
     * Primeira instalação: config em português se o sistema estiver em português; senão, em inglês.
     */
    private void createConfigIfMissing() {
        if (new java.io.File(getDataFolder(), "config.yml").exists()) {
            return;
        }
        if (java.util.Locale.getDefault().getLanguage().equals("pt")) {
            getDataFolder().mkdirs();
            try (java.io.InputStream in = getResource("config-pt-BR.yml")) {
                if (in != null) {
                    java.nio.file.Files.copy(in, new java.io.File(getDataFolder(), "config.yml").toPath());
                    reloadConfig();
                    return;
                }
            } catch (java.io.IOException e) {
                getLogger().warning("Não foi possível criar o config em português: " + e.getMessage());
            }
        }
        saveDefaultConfig();
    }

    private void loadSettings() {
        Lang.load(this, getConfig().getString("idioma", Lang.PORTUGUESE));
        settings = BellSettings.load(getConfig(), getLogger());
        voiceSettings = VoiceSettings.load(getConfig(), getDataFolder(), getLogger());
        contributorSettings = ContributorSettings.load(getConfig(), getLogger());
        registry.load(getConfig());
    }

    /** Narradores usados: o ativo e os que alguma conquista escolheu. */
    private Set<VoiceSettings.Narrator> narratorsInUse() {
        Set<VoiceSettings.Narrator> used = new LinkedHashSet<>();
        used.add(voiceSettings.activeNarrator());
        for (Conquest conquest : registry.all()) {
            used.add(voiceSettings.narratorOrDefault(conquest.narrator()));
        }
        return used;
    }

    /** Frase curta que cada narrador fala quando o admin pede uma amostra no menu. */
    public static String sampleText(VoiceSettings.Narrator narrator) {
        String name = Character.toUpperCase(narrator.name().charAt(0)) + narrator.name().substring(1);
        if (narrator.language().toLowerCase(java.util.Locale.ROOT).startsWith("en")) {
            return "Hello! I am " + name + ", and I will narrate this server's achievements.";
        }
        return "Olá! Eu sou " + name + ", e vou narrar as conquistas deste servidor.";
    }

    /**
     * Gera os áudios que faltam (frases, conquistas, jogadores online e a amostra de cada narrador) e
     * monta o pack.
     *
     * @param resend true para mandar o pack de novo a quem está online, se ele mudou
     */
    CompletableFuture<Void> prepareVoiceAndPack(boolean resend) {
        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(false);
        if (voiceSettings.enabled()) {
            List<String> phrases = registry.all().stream().map(Conquest::phrase).filter(p -> !p.isBlank()).toList();
            List<String> spoken = new ArrayList<>();
            for (Conquest conquest : registry.all()) {
                spoken.add(conquest.spoken());
                spoken.add(conquest.customClosing());
            }
            Map<String, VoiceBank.Kind> texts = new LinkedHashMap<>(SpeechPlanner.requiredTexts(voiceSettings, phrases, spoken));
            for (Player player : Bukkit.getOnlinePlayers()) {
                texts.put(player.getName(), VoiceBank.Kind.NOME);
            }
            Set<VoiceSettings.Narrator> inUse = narratorsInUse();
            for (VoiceSettings.Narrator narrator : voiceSettings.narrators().values()) {
                if (!health.canSpeak(narrator)) {
                    continue;
                }
                Map<String, VoiceBank.Kind> forNarrator = new LinkedHashMap<>();
                if (inUse.contains(narrator)) {
                    forNarrator.putAll(texts);
                }
                forNarrator.put(sampleText(narrator), VoiceBank.Kind.TEXTO);
                chain = chain.thenCompose(changed -> voiceBank.ensure(voiceSettings, narrator, forNarrator)
                        .thenApply(created -> changed || created));
            }
        }
        return chain.thenCompose(changed -> pack.rebuild())
                .thenAccept(changed -> {
                    if (resend && changed) {
                        Bukkit.getScheduler().runTask(this, this::resendPack);
                    }
                });
    }

    /** Troca o narrador ativo, salva no config, gera os áudios dele e reenvia o pacote. */
    CompletableFuture<Void> setNarrator(String name) {
        getConfig().set("voz.narrador", name);
        saveConfig();
        loadSettings();
        return prepareVoiceAndPack(true);
    }

    /** Menu: troca o narrador ativo (mesmo que o comando /sinos narrador). */
    public CompletableFuture<Void> activateNarrator(String name) {
        return setNarrator(name);
    }

    /** Toca a amostra de um narrador só para este jogador. @return false se ela ainda não existe no pacote dele */
    public boolean playSample(Player player, VoiceSettings.Narrator narrator) {
        Optional<VoiceBank.Clip> clip = voiceBank.find(narrator.name(), VoiceBank.Kind.TEXTO, sampleText(narrator));
        if (clip.isEmpty() || !hasPack(player)) {
            return false;
        }
        player.playSound(Sound.sound(Key.key(SpeechPlanner.soundKey(clip.get())), Sound.Source.MASTER, 1f, 1f),
                Sound.Emitter.self());
        return true;
    }

    /** Toca o anúncio só para este jogador, sem concluir a conquista. */
    public void preview(Player player, AnnouncementService.Request request) {
        announcements.announce(request.onlyFor(Set.of(player.getUniqueId())));
    }

    /**
     * Jogador novo: gera o áudio do nome antes de mandar o pack, para ele já receber o pack com o
     * próprio nome. Quem já estava online só recebe esse nome quando entrar de novo.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        updates.notify(player);
        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(false);
        if (voiceSettings.enabled()) {
            for (VoiceSettings.Narrator narrator : narratorsInUse()) {
                if (health.canSpeak(narrator)) {
                    chain = chain.thenCombine(
                            voiceBank.ensure(voiceSettings, narrator, Map.of(player.getName(), VoiceBank.Kind.NOME)),
                            (a, b) -> a || b);
                }
            }
        }
        chain.thenCompose(changed -> changed ? pack.rebuild() : CompletableFuture.completedFuture(false))
                .thenRun(() -> Bukkit.getScheduler().runTask(this, () -> pack.send(player)));
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        UUID id = event.getID();
        if (MusicService.PREVIEW_ID.equals(id)) {
            if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
                music.previewLoaded(event.getPlayer());
            }
            return;
        }
        // Alguns clientes e proxies respondem com o id zerado em vez do id do pacote
        if (!ResourcePackService.PACK_ID.equals(id) && !NIL_UUID.equals(id)) {
            return;
        }
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> withPack.add(event.getPlayer().getUniqueId());
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED ->
                    withPack.remove(event.getPlayer().getUniqueId());
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        withPack.remove(event.getPlayer().getUniqueId());
        pack.forget(event.getPlayer());
    }

    @Override
    public boolean announce(String display, String spoken, List<String> contributors) {
        ConquestAnnounceEvent event = new ConquestAnnounceEvent("", display, contributors);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        announcements.announce(new AnnouncementService.Request(display, spoken, "", Conquest.RANDOM_CLOSING, "",
                event.getContributors()));
        return true;
    }

    @Override
    public boolean completeConquest(String conquestId, List<String> contributors) {
        return registry.get(conquestId).map(c -> conquests.complete(c, contributors)).orElse(false);
    }

    @Override
    public CompletableFuture<Void> registerPhrase(String spoken) {
        if (!voiceSettings.enabled()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(false);
        for (VoiceSettings.Narrator narrator : narratorsInUse()) {
            if (health.canSpeak(narrator)) {
                chain = chain.thenCombine(voiceBank.ensure(voiceSettings, narrator, Map.of(spoken, VoiceBank.Kind.TEXTO)),
                        (a, b) -> a || b);
            }
        }
        return chain.thenCompose(changed -> changed ? pack.rebuild() : CompletableFuture.completedFuture(false))
                .thenAccept(changed -> { });
    }
}
