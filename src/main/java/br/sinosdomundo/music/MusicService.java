package br.sinosdomundo.music;

import br.sinosdomundo.SinosDoMundo;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Música própria do admin: arquivos em plugins/WorldAchievementAnnouncer/musicas, convertidos para OGG mono pelo
 * scripts/musica.py (Python), com análise dos ataques para sugerir o ritmo das batidas e prévia só para o admin.
 */
public final class MusicService {

    public static final Set<String> FORMATS = Set.of("ogg", "mp3", "wav", "flac");
    /** Id do pacote extra da prévia (empilhado sobre o principal, só para quem pediu). */
    public static final UUID PREVIEW_ID = UUID.nameUUIDFromBytes("SinosDoMundo-previa".getBytes(StandardCharsets.UTF_8));
    public static final String PREVIEW_SOUND = "sinos:musica.previa";
    /** Duração total quando a música é estendida para cobrir a fala. */
    public static final double EXTENDED_SECONDS = 17.5;
    private static final long TIMEOUT_SECONDS = 180;

    /** Resultado da análise: ataques [segundos, força 0-1] e o momento sugerido para a voz. */
    public record Analysis(double duration, List<double[]> onsets, double speech) {
    }

    /** Ritmo sugerido: ticks das batidas (desde que o sino aparece) e atraso da fala depois da última. */
    public record Suggestion(List<Integer> ticks, int speechDelay) {
    }

    private final SinosDoMundo plugin;
    private final Executor worker;
    private final File library;
    private final File script;
    private final Map<UUID, Boolean> waitingPreview = new ConcurrentHashMap<>();

    public MusicService(SinosDoMundo plugin, Executor worker) {
        this.plugin = plugin;
        this.worker = worker;
        this.library = new File(plugin.getDataFolder(), "musicas");
        this.script = new File(plugin.getDataFolder(), "scripts/musica.py");
    }

    public void setup() {
        library.mkdirs();
        script.getParentFile().mkdirs();
        try (InputStream in = plugin.getResource("scripts/musica.py")) {
            if (in != null) {
                Files.copy(in, script.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Não foi possível extrair musica.py: " + e.getMessage());
        }
        File readme = new File(library, "LEIA-ME.txt");
        if (!readme.exists()) {
            try {
                Files.writeString(readme.toPath(), br.sinosdomundo.Lang.t(String.join(System.lineSeparator(),
                        "Coloque aqui as músicas que quer usar nos anúncios do World Achievement Announcer.",
                        "",
                        "Formatos aceitos: .ogg, .mp3, .wav e .flac.",
                        "Depois, no jogo: /sinos > Música > Colocar música própria > Atualizar lista.",
                        "",
                        "O plugin converte a música para OGG mono (o formato do Minecraft), mede os ataques",
                        "para ajustar as batidas do sino e pode estender a música para cobrir a fala.",
                        "",
                        "Dicas: prefira músicas curtas (até uns 30 segundos); cada jogador baixa a música no",
                        "pacote. Use só músicas que você tem direito de usar.")), StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().warning("Não foi possível criar o LEIA-ME das músicas: " + e.getMessage());
            }
        }
    }

    public File library() {
        return library;
    }

    /** Música própria ativa, já convertida (a que vai no pacote). */
    public File activeFile() {
        return new File(plugin.getDataFolder(), "musica/conquista.ogg");
    }

    public List<File> files() {
        File[] found = library.listFiles((dir, name) -> FORMATS.contains(extension(name)));
        List<File> list = new ArrayList<>(found == null ? List.of() : Arrays.asList(found));
        list.sort(Comparator.comparing(f -> f.getName().toLowerCase(Locale.ROOT)));
        return list;
    }

    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public CompletableFuture<Analysis> analyze(File file) {
        return CompletableFuture.supplyAsync(() -> {
            JsonObject result = run(List.of("analisar", file.getAbsolutePath()));
            List<double[]> onsets = new ArrayList<>();
            for (JsonElement e : result.getAsJsonArray("ataques")) {
                JsonArray pair = e.getAsJsonArray();
                onsets.add(new double[] {pair.get(0).getAsDouble(), pair.get(1).getAsDouble()});
            }
            return new Analysis(result.get("duracao").getAsDouble(), onsets, result.get("fala").getAsDouble());
        }, worker);
    }

    /** Converte para OGG mono; com {@code extendTo > 0}, estende a música até essa duração. @return duração final */
    public CompletableFuture<Double> convert(File input, File output, double extendTo) {
        return CompletableFuture.supplyAsync(() -> {
            output.getParentFile().mkdirs();
            List<String> args = new ArrayList<>(List.of("converter", input.getAbsolutePath(), output.getAbsolutePath()));
            if (extendTo > 0) {
                args.addAll(List.of("--estender", Double.toString(extendTo)));
            }
            return run(args).get("duracao").getAsDouble();
        }, worker);
    }

    /**
     * Batidas nos ataques fortes da música (força a partir de 0,55), ignorando os que vêm antes de o sino
     * terminar de crescer, e a fala 1 segundo depois do último.
     */
    public static Suggestion suggest(Analysis analysis, int musicStartTick) {
        List<Integer> ticks = new ArrayList<>();
        double last = -1;
        for (double[] onset : analysis.onsets()) {
            int tick = musicStartTick + (int) Math.round(onset[0] / 0.05);
            if (onset[1] < 0.55 || tick < 11 || onset[0] > 8 || (last >= 0 && onset[0] - last < 0.25)) {
                continue;
            }
            ticks.add(tick);
            last = onset[0];
            if (ticks.size() == 12) {
                break;
            }
        }
        int speechTick = musicStartTick + (int) Math.round(analysis.speech() / 0.05);
        int delay = ticks.isEmpty() ? 20 : Math.max(5, speechTick - ticks.get(ticks.size() - 1));
        return new Suggestion(ticks, delay);
    }

    /** Prévia só para o admin: manda um pacote pequeno com a música e toca quando ele carregar. */
    public CompletableFuture<Void> preview(Player player, File file) {
        File previewFile = new File(plugin.getDataFolder(), "musica/previa/previa.ogg");
        return convert(file, previewFile, 0).thenAccept(duration -> Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Map<String, byte[]> files = new LinkedHashMap<>();
                files.put("assets/sinos/sounds/musica/previa.ogg", Files.readAllBytes(previewFile.toPath()));
                files.put("assets/sinos/sounds.json", ("{\"musica.previa\":{\"sounds\":[{\"name\":\"sinos:musica/previa\","
                        + "\"stream\":true}]}}").getBytes(StandardCharsets.UTF_8));
                waitingPreview.put(player.getUniqueId(), true);
                plugin.pack().sendExtra(player, PREVIEW_ID, "previa", files);
                // Se o cliente não avisar que carregou, toca mesmo assim depois de alguns segundos
                Bukkit.getScheduler().runTaskLater(plugin, () -> previewLoaded(player), 100);
            } catch (IOException e) {
                player.sendMessage(br.sinosdomundo.menu.Items.text("<red>Não foi possível montar a prévia: "
                        + br.sinosdomundo.menu.Items.message(e.getMessage())));
            }
        }));
    }

    /** Chamado quando o cliente avisa que carregou o pacote da prévia. */
    public void previewLoaded(Player player) {
        if (waitingPreview.remove(player.getUniqueId()) != null && player.isOnline()) {
            player.stopSound(net.kyori.adventure.sound.SoundStop.named(Key.key(PREVIEW_SOUND)));
            player.playSound(Sound.sound(Key.key(PREVIEW_SOUND), Sound.Source.MASTER, 1f, 1f), Sound.Emitter.self());
        }
    }

    private JsonObject run(List<String> args) {
        if (!plugin.health().canConvertMusic()) {
            throw new IllegalStateException("converter músicas precisa do Python com soundfile e numpy ("
                    + plugin.health().detail() + "). Sem isso, use a música padrão.");
        }
        List<String> command = new ArrayList<>(List.of(plugin.voiceSettings().python(), script.getAbsolutePath()));
        command.addAll(args);
        try {
            Process process = new ProcessBuilder(command).start();
            CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("o processamento da música passou de " + TIMEOUT_SECONDS + " s");
            }
            if (process.exitValue() != 0) {
                String error = stderr.join().trim();
                String[] lines = error.split("\\R");
                throw new IllegalStateException(lines.length == 0 ? "erro no Python" : lines[lines.length - 1]);
            }
            return new Gson().fromJson(stdout.join(), JsonObject.class);
        } catch (IOException e) {
            throw new IllegalStateException("não foi possível rodar o Python (" + plugin.voiceSettings().python() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrompido", e);
        }
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
}
