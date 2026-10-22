package br.sinosdomundo.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import br.sinosdomundo.voice.VoiceSettings.Narrator;
import org.bukkit.plugin.Plugin;

/**
 * Banco de áudios da voz. Cada áudio é um trecho de frase, uma palavra ou um nome, gerado inteiro
 * pelo Piper (script gerar_voz.py, rodado como programa separado) e guardado em voz/audio.
 */
public final class VoiceBank {

    public enum Kind { TEXTO, NOME }

    /** Um áudio do banco. {@code id} vira o nome do arquivo e do som no resource pack. */
    public record Clip(String id, String narrator, Kind kind, String text, int durationMs) {
    }

    private record Request(String id, Kind kind, String text) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long TIMEOUT_SECONDS = 120;

    private final Plugin plugin;
    private final Logger logger;
    private final Executor worker;
    private final File folder;
    private final File audioFolder;
    private final File indexFile;
    private final File script;
    private final Map<String, Clip> clips = new ConcurrentHashMap<>();

    public VoiceBank(Plugin plugin, Executor worker) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.worker = worker;
        this.folder = new File(plugin.getDataFolder(), "voz");
        this.audioFolder = new File(folder, "audio");
        this.indexFile = new File(folder, "indice.json");
        this.script = new File(folder, "gerar_voz.py");
    }

    public void load() {
        audioFolder.mkdirs();
        // Sempre atualiza o script, para acompanhar a versão do plugin
        try (InputStream in = plugin.getResource("voz/gerar_voz.py")) {
            if (in != null) {
                Files.copy(in, script.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Não foi possível extrair gerar_voz.py", e);
        }
        if (indexFile.isFile()) {
            try {
                Map<String, Clip> saved = GSON.fromJson(Files.readString(indexFile.toPath()),
                        new TypeToken<Map<String, Clip>>() { }.getType());
                if (saved != null) {
                    saved.values().stream()
                            .filter(c -> c.narrator() != null && new File(audioFolder, c.id() + ".ogg").isFile())
                            .forEach(c -> clips.put(c.id(), c));
                }
            } catch (IOException | RuntimeException e) {
                logger.log(Level.WARNING, "Índice da voz ilegível; os áudios serão gerados de novo", e);
            }
        }
    }

    public Optional<Clip> find(String narrator, Kind kind, String text) {
        return Optional.ofNullable(clips.get(idFor(narrator, kind, text)));
    }

    public Collection<Clip> clips() {
        return List.copyOf(clips.values());
    }

    /**
     * Apaga áudios de narradores que não existem mais e arquivos .ogg que nenhum áudio usa.
     *
     * @return quantos arquivos foram apagados
     */
    public CompletableFuture<Integer> cleanup(Set<String> narrators) {
        return CompletableFuture.supplyAsync(() -> {
            clips.values().removeIf(c -> !narrators.contains(c.narrator()));
            int deleted = 0;
            File[] files = audioFolder.listFiles((dir, name) -> name.endsWith(".ogg"));
            if (files != null) {
                for (File file : files) {
                    String id = file.getName().substring(0, file.getName().length() - 4);
                    if (!clips.containsKey(id) && file.delete()) {
                        deleted++;
                    }
                }
            }
            try {
                save();
            } catch (IOException e) {
                logger.warning("Não foi possível salvar o índice da voz: " + e.getMessage());
            }
            return deleted;
        }, worker);
    }

    public int size() {
        return clips.size();
    }

    public File audioFile(Clip clip) {
        return new File(audioFolder, clip.id() + ".ogg");
    }

    /**
     * Gera os áudios que ainda não existem para um narrador. Roda fora da thread principal.
     *
     * @return true se algum áudio novo foi criado
     */
    public CompletableFuture<Boolean> ensure(VoiceSettings settings, Narrator narrator, Map<String, Kind> texts) {
        List<Request> missing = new ArrayList<>();
        Map<String, Request> unique = new LinkedHashMap<>();
        texts.forEach((text, kind) -> {
            String clean = normalize(text);
            if (!clean.isEmpty()) {
                String id = idFor(narrator.name(), kind, clean);
                if (!clips.containsKey(id)) {
                    unique.putIfAbsent(id, new Request(id, kind, clean));
                }
            }
        });
        missing.addAll(unique.values());
        if (missing.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> generate(settings, narrator, missing), worker);
    }

    private boolean generate(VoiceSettings settings, Narrator narrator, List<Request> requests) {
        if (!narrator.filesPresent()) {
            logger.warning("Arquivos de voz do narrador " + narrator.name() + " não encontrados: " + narrator.model());
            return false;
        }
        JsonArray input = new JsonArray();
        for (Request r : requests) {
            JsonObject o = new JsonObject();
            o.addProperty("id", r.id());
            o.addProperty("texto", r.text());
            o.addProperty("tipo", r.kind() == Kind.NOME ? "nome" : "texto");
            input.add(o);
        }
        try {
            List<String> command = new ArrayList<>(List.of(settings.python(), script.getAbsolutePath()));
            command.addAll(narrator.arguments());
            command.addAll(List.of("--saida", audioFolder.getAbsolutePath()));
            Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.PIPE).start();
            try (OutputStream out = process.getOutputStream()) {
                out.write(input.toString().getBytes(StandardCharsets.UTF_8));
            }
            CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                logger.warning("Geração da voz passou de " + TIMEOUT_SECONDS + " s e foi cancelada");
                return false;
            }
            String errors = stderr.join().trim();
            if (process.exitValue() != 0) {
                logger.warning("Geração da voz falhou (código " + process.exitValue() + "): " + errors);
                return false;
            }
            if (!errors.isEmpty()) {
                logger.info("Piper: " + errors);
            }

            Map<String, Request> byId = new LinkedHashMap<>();
            requests.forEach(r -> byId.put(r.id(), r));
            int created = 0;
            for (JsonElement element : GSON.fromJson(stdout.join(), JsonArray.class)) {
                JsonObject o = element.getAsJsonObject();
                Request r = byId.get(o.get("id").getAsString());
                if (r != null) {
                    clips.put(r.id(), new Clip(r.id(), narrator.name(), r.kind(), r.text(), o.get("duracao_ms").getAsInt()));
                    created++;
                }
            }
            save();
            logger.info("Voz (" + narrator.name() + "): " + created + " áudio(s) gerado(s)");
            return created > 0;
        } catch (IOException e) {
            logger.warning("Não foi possível rodar o Python da voz (" + settings.python() + "): " + e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException e) {
            logger.log(Level.WARNING, "Resposta inválida do gerador de voz", e);
            return false;
        }
    }

    private void save() throws IOException {
        Map<String, Clip> sorted = new java.util.TreeMap<>(clips);
        Files.writeString(indexFile.toPath(), GSON.toJson(sorted));
    }

    private static String readAll(InputStream in) {
        try (in) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            in.transferTo(buffer);
            return buffer.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    static String normalize(String text) {
        return String.join(" ", text.trim().split("\\s+"));
    }

    /** Id estável: o mesmo narrador e texto geram sempre o mesmo arquivo. Maiúsculas não importam. */
    static String idFor(String narrator, Kind kind, String text) {
        String key = narrator + ":" + kind.name() + ":" + normalize(text).toLowerCase(Locale.ROOT);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
