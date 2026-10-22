package br.sinosdomundo.pack;

import br.sinosdomundo.voice.SpeechPlanner;
import br.sinosdomundo.voice.VoiceBank;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Monta o resource pack (sino 3D + áudios da voz), serve o zip por HTTP e o envia aos jogadores.
 * O zip é determinístico: o hash só muda quando o conteúdo muda.
 */
public final class ResourcePackService {

    /** Mesmo id sempre, para um pack novo substituir o anterior no cliente. */
    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("SinosDoMundo".getBytes(StandardCharsets.UTF_8));
    private static final int PACK_FORMAT = 46; // Minecraft 1.21.4
    private static final int MAX_FORMAT = 999;
    private static final String MODEL_FILE = "assets/sinos/models/item/sino.json";
    private static final List<String> BASE_FILES = List.of(
            "assets/minecraft/items/bell.json",
            "assets/sinos/items/sino.json",
            "assets/sinos/textures/item/sino.png");

    private final Plugin plugin;
    private final VoiceBank bank;
    private final Executor worker;
    private PackSettings settings;
    private HttpServer server;
    private volatile byte[] zip;
    private volatile String hash;
    /** Pacotes extras (prévia de música do admin), servidos por caminho. */
    private final Map<String, byte[]> extras = new ConcurrentHashMap<>();
    /** Qual versão do pacote foi mandada para cada jogador. */
    private final Map<UUID, String> sentHash = new ConcurrentHashMap<>();
    private final BellModel bellModel;

    public ResourcePackService(Plugin plugin, VoiceBank bank, Executor worker, PackSettings settings) {
        this.plugin = plugin;
        this.bank = bank;
        this.worker = worker;
        this.settings = settings;
        try (InputStream in = plugin.getResource("pacote/" + MODEL_FILE)) {
            this.bellModel = in == null ? null : BellModel.parse(in);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Modelo do sino inválido: " + MODEL_FILE, e);
        }
        if (bellModel != null) {
            plugin.getLogger().info(String.format("Modelo do sino: eixo na altura %.1f, badalo %s (batida a %.1f graus)",
                    bellModel.pivotY(), bellModel.hasClapper() ? "separado" : "ausente", bellModel.contactAngle()));
        }
    }

    /** Modelo do sino com a geometria para a animação, ou null se o pacote não tem modelo. */
    public BellModel bellModel() {
        return bellModel;
    }

    public void start() {
        if (!settings.enabled()) {
            return;
        }
        if (settings.external()) {
            plugin.getLogger().info(settings.externalUrl().isEmpty()
                    ? "Pacote em modo externo, mas pacote.url-externa está vazio: ninguém recebe o pacote."
                    : "Pacote em modo externo: " + settings.externalUrl() + " (envie o pacote.zip da pasta do plugin para lá)");
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(settings.port()), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                byte[] body = path.startsWith("/extra-") ? extras.get(path) : zip;
                if (body == null || !(path.startsWith("/pacote") || path.startsWith("/extra-"))) {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.setExecutor(Executors.newSingleThreadExecutor(r -> new Thread(r, "SinosDoMundo-pacote")));
            server.start();
            plugin.getLogger().info("Pacote servido na porta " + settings.port() + " (" + baseUrl() + ")");
        } catch (IOException e) {
            plugin.getLogger().warning("Não foi possível abrir a porta " + settings.port() + " do pacote: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public void updateSettings(PackSettings settings) {
        this.settings = settings;
    }

    /**
     * Remonta o zip fora da thread principal.
     *
     * @return true se o conteúdo mudou (jogadores online precisam receber de novo para ter a novidade)
     */
    public CompletableFuture<Boolean> rebuild() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] built = build();
                String newHash = sha1(built);
                boolean changed = !newHash.equals(hash);
                boolean firstBuild = hash == null;
                zip = built;
                hash = newHash;
                Files.write(new File(plugin.getDataFolder(), "pacote.zip").toPath(), built);
                if (changed && !firstBuild && settings.external()) {
                    plugin.getLogger().info("O pacote.zip mudou. No modo externo, envie o novo arquivo para "
                            + settings.externalUrl() + " (até lá, novidades como nomes de jogadores novos não tocam).");
                }
                return changed;
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Falha ao montar o resource pack", e);
                return false;
            }
        }, worker);
    }

    /** Tamanho do pacote atual em bytes (0 se ainda não foi montado). */
    public int size() {
        byte[] current = zip;
        return current == null ? 0 : current.length;
    }

    /** Jogadores online que receberam uma versão anterior do pacote (ou nenhuma). */
    public int outdatedCount() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!Objects.equals(sentHash.get(player.getUniqueId()), hash)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Manda a um jogador um pacote extra, empilhado sobre o principal (o principal continua carregado).
     *
     * @param id id fixo do pacote extra (o mesmo id substitui a versão anterior dele)
     * @param files caminho dentro do zip -> conteúdo; pack.mcmeta é adicionado sozinho
     */
    public void sendExtra(Player player, UUID id, String name, Map<String, byte[]> files) throws IOException {
        if (server == null) {
            throw new IOException("a prévia precisa do servidor do pacote (pacote.modo: proprio)");
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(buffer)) {
            put(out, "pack.mcmeta", packMeta("SinosDoMundo: " + name).toString().getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                put(out, file.getKey(), file.getValue());
            }
        }
        byte[] built = buffer.toByteArray();
        String extraHash = sha1(built);
        String path = "/extra-" + name + "-" + extraHash + ".zip";
        extras.keySet().removeIf(k -> k.startsWith("/extra-" + name + "-"));
        extras.put(path, built);
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo().id(id).uri(URI.create(baseUrl() + path)).hash(extraHash).build();
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest().packs(info).replace(false).required(false).build());
    }

    /** O jogador recebeu uma versão anterior do pacote (ou nenhuma). */
    public boolean isOutdated(Player player) {
        return hash != null && !Objects.equals(sentHash.get(player.getUniqueId()), hash);
    }

    public void forget(Player player) {
        sentHash.remove(player.getUniqueId());
    }

    public boolean ready() {
        if (!settings.enabled() || hash == null) {
            return false;
        }
        return settings.external() ? !settings.externalUrl().isEmpty() : server != null;
    }

    public boolean external() {
        return settings.external();
    }

    public void send(Player player) {
        if (!ready() || !player.isOnline()) {
            return;
        }
        // No modo externo o arquivo hospedado pode estar desatualizado: sem hash, o cliente aceita a versão que estiver lá
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo()
                .id(PACK_ID)
                .uri(URI.create(settings.external() ? settings.externalUrl() : baseUrl() + "/pacote-" + hash + ".zip"))
                .hash(settings.external() ? "" : hash)
                .build();
        // replace(false): o mesmo id troca só a versão anterior deste pacote; pacotes de outros plugins
        // (ItemsAdder, Oraxen, Nexo...) continuam carregados
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .replace(false)
                .required(settings.required())
                .prompt(MiniMessage.miniMessage().deserialize(settings.prompt()))
                .build());
        sentHash.put(player.getUniqueId(), hash);
    }

    private String baseUrl() {
        if (!settings.publicUrl().isEmpty()) {
            return settings.publicUrl().replaceAll("/+$", "");
        }
        String ip = Bukkit.getIp().isBlank() ? "127.0.0.1" : Bukkit.getIp();
        return "http://" + ip + ":" + settings.port();
    }

    private byte[] build() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(buffer)) {
            put(out, "pack.mcmeta", packMeta("SinosDoMundo: sino 3D e voz").toString().getBytes(StandardCharsets.UTF_8));

            for (String path : BASE_FILES) {
                try (InputStream in = plugin.getResource("pacote/" + path)) {
                    if (in == null) {
                        throw new IOException("Arquivo do pacote ausente no plugin: " + path);
                    }
                    put(out, path, in.readAllBytes());
                }
            }

            if (bellModel != null) {
                put(out, MODEL_FILE, bellModel.body().toString().getBytes(StandardCharsets.UTF_8));
                if (bellModel.hasClapper()) {
                    put(out, "assets/sinos/models/item/badalo.json", bellModel.clapper().toString().getBytes(StandardCharsets.UTF_8));
                    put(out, "assets/sinos/items/badalo.json",
                            "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"sinos:item/badalo\"}}"
                                    .getBytes(StandardCharsets.UTF_8));
                }
            }

            JsonObject sounds = new JsonObject();
            byte[] music = musicFile();
            if (music != null) {
                put(out, "assets/sinos/sounds/musica/conquista.ogg", music);
                JsonObject track = new JsonObject();
                track.addProperty("name", "sinos:musica/conquista");
                track.addProperty("stream", true);
                JsonArray tracks = new JsonArray();
                tracks.add(track);
                JsonObject event = new JsonObject();
                event.add("sounds", tracks);
                sounds.add("musica.conquista", event);
            }
            List<VoiceBank.Clip> clips = bank.clips().stream()
                    .sorted(Comparator.comparing(VoiceBank.Clip::id))
                    .toList();
            for (VoiceBank.Clip clip : clips) {
                File audio = bank.audioFile(clip);
                if (!audio.isFile()) {
                    continue;
                }
                JsonObject sound = new JsonObject();
                sound.addProperty("name", "sinos:voz/" + clip.id());
                JsonArray list = new JsonArray();
                list.add(sound);
                JsonObject event = new JsonObject();
                event.add("sounds", list);
                event.addProperty("subtitle", clip.text());
                sounds.add(SpeechPlanner.soundKey(clip).substring("sinos:".length()), event);
                put(out, "assets/sinos/sounds/voz/" + clip.id() + ".ogg", Files.readAllBytes(audio.toPath()));
            }
            put(out, "assets/sinos/sounds.json", sounds.toString().getBytes(StandardCharsets.UTF_8));
        }
        return buffer.toByteArray();
    }

    /** A música do admin (plugins/SinosDoMundo/musica/conquista.ogg) ou a padrão do plugin. */
    private byte[] musicFile() throws IOException {
        File custom = new File(plugin.getDataFolder(), "musica/conquista.ogg");
        if (custom.isFile()) {
            return Files.readAllBytes(custom.toPath());
        }
        try (InputStream in = plugin.getResource("pacote/assets/sinos/sounds/musica/conquista.ogg")) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static JsonObject packMeta(String description) {
        JsonObject meta = new JsonObject();
        meta.addProperty("pack_format", PACK_FORMAT);
        // Clientes mais novos (1.21.9+, 26.x) leem min_format/max_format; os antigos, supported_formats
        JsonObject supported = new JsonObject();
        supported.addProperty("min_inclusive", PACK_FORMAT);
        supported.addProperty("max_inclusive", MAX_FORMAT);
        meta.add("supported_formats", supported);
        meta.addProperty("min_format", PACK_FORMAT);
        meta.addProperty("max_format", MAX_FORMAT);
        meta.addProperty("description", description);
        JsonObject pack = new JsonObject();
        pack.add("pack", meta);
        return pack;
    }

    /** Endereço público do pacote principal (para mostrar ao admin). */
    public String publicUrl() {
        return settings.external() ? settings.externalUrl() : baseUrl() + "/pacote-" + hash + ".zip";
    }

    public boolean running() {
        return server != null;
    }

    private static void put(ZipOutputStream out, String path, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        entry.setTime(0); // data fixa para o hash não mudar à toa
        out.putNextEntry(entry);
        out.write(data);
        out.closeEntry();
    }

    private static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
