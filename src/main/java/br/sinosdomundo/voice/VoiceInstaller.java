package br.sinosdomundo.voice;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Instalação guiada da voz: acha um Python, cria um ambiente Python só do plugin, instala os módulos
 * e baixa os modelos do Kokoro, conferindo o SHA-256. Cada passo informa o progresso em frases curtas.
 */
public final class VoiceInstaller {

    /** Um arquivo de modelo: de onde baixar, onde gravar e como conferir. */
    public record ModelFile(String name, String url, long size, String sha256) {
    }

    public static final List<ModelFile> KOKORO_FILES = List.of(
            new ModelFile("kokoro-v1.0.int8.onnx",
                    "https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/kokoro-v1.0.int8.onnx",
                    92_361_271L, "6e742170d309016e5891a994e1ce1559c702a2ccd0075e67ef7157974f6406cb"),
            new ModelFile("voices-v1.0.bin",
                    "https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/voices-v1.0.bin",
                    28_214_398L, "bca610b8308e8d99f32e6fe4197e7ec01679264efed0cac9140fe9c29f1fbf7d"));

    /** Módulos que o plugin precisa (voz Kokoro e conversão de músicas). */
    public static final List<String> PACKAGES = List.of("kokoro-onnx", "soundfile", "numpy");

    private static final List<String> PYTHON_CANDIDATES = List.of("python3", "python", "py");
    private static final long PIP_TIMEOUT_MINUTES = 20;

    private final File dataFolder;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "SinosDoMundo-instalador"));
    private volatile boolean busy;

    public VoiceInstaller(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public boolean busy() {
        return busy;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    /** Pasta do ambiente Python do plugin. */
    public File envFolder() {
        return new File(dataFolder, "python-env");
    }

    /** O Python de dentro do ambiente do plugin (existe depois de {@link #createEnv}). */
    public File envPython() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        return new File(envFolder(), windows ? "Scripts/python.exe" : "bin/python");
    }

    public File voicesFolder() {
        return new File(dataFolder, "vozes");
    }

    /** Os modelos do Kokoro já estão na pasta vozes, com o tamanho certo. */
    public boolean modelsPresent() {
        for (ModelFile model : KOKORO_FILES) {
            File file = new File(voicesFolder(), model.name());
            if (!file.isFile() || file.length() != model.size()) {
                return false;
            }
        }
        return true;
    }

    /** Procura um Python que funcione: o do config, o do ambiente do plugin, depois python3, python e py. */
    public CompletableFuture<String> detectPython(String configured) {
        return run(() -> {
            List<String> candidates = new ArrayList<>();
            candidates.add(configured);
            if (envPython().isFile()) {
                candidates.add(envPython().getAbsolutePath());
            }
            candidates.addAll(PYTHON_CANDIDATES);
            for (String candidate : candidates) {
                if (PythonHealth.check(candidate).pythonFound()) {
                    return candidate;
                }
            }
            return null;
        });
    }

    /**
     * Cria o ambiente Python do plugin (se ainda não existe) e instala os módulos nele.
     *
     * @return o caminho do Python do ambiente, para gravar em voz.python
     */
    public CompletableFuture<String> installModules(String basePython, Consumer<String> progress) {
        return run(() -> {
            if (!envPython().isFile()) {
                progress.accept("Criando o ambiente Python do plugin em " + envFolder().getPath() + "...");
                exec(List.of(basePython, "-m", "venv", envFolder().getAbsolutePath()), progress, 5,
                        "Não foi possível criar o ambiente Python. Em Debian/Ubuntu instale o pacote python3-venv.");
            }
            String python = envPython().getAbsolutePath();
            progress.accept("Atualizando o pip...");
            exec(List.of(python, "-m", "pip", "install", "--upgrade", "pip"), line -> { }, 5, "Falha ao atualizar o pip.");
            progress.accept("Instalando " + String.join(", ", PACKAGES) + " (pode levar alguns minutos)...");
            List<String> command = new ArrayList<>(List.of(python, "-m", "pip", "install"));
            command.addAll(PACKAGES);
            int[] collected = {0};
            exec(command, line -> {
                // O pip lista cada pacote; o admin vê só um resumo a cada 10
                if (line.startsWith("Collecting ") && ++collected[0] % 10 == 0) {
                    progress.accept(collected[0] + " pacotes baixados...");
                } else if (line.startsWith("Successfully installed")) {
                    progress.accept("Módulos instalados (" + (line.split(" ").length - 2) + " pacotes).");
                }
            }, PIP_TIMEOUT_MINUTES, "Falha ao instalar os módulos. Veja o console do servidor.");
            return python;
        });
    }

    /** Baixa os modelos do Kokoro que faltam, mostrando o progresso, e confere o SHA-256 de cada um. */
    public CompletableFuture<Void> downloadModels(Consumer<String> progress) {
        return run(() -> {
            voicesFolder().mkdirs();
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(20)).build();
            for (ModelFile model : KOKORO_FILES) {
                File target = new File(voicesFolder(), model.name());
                if (target.isFile() && target.length() == model.size() && sha256(target).equals(model.sha256())) {
                    progress.accept(model.name() + " já está baixado.");
                    continue;
                }
                File partial = new File(voicesFolder(), model.name() + ".parcial");
                progress.accept("Baixando " + model.name() + " (" + model.size() / 1_000_000 + " MB)...");
                HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder(URI.create(model.url()))
                        .header("User-Agent", "SinosDoMundo").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    throw new IOException("o download de " + model.name() + " respondeu " + response.statusCode());
                }
                long done = 0;
                int lastPercent = 0;
                try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(partial.toPath())) {
                    byte[] buffer = new byte[1 << 16];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                        done += read;
                        int percent = (int) (done * 100 / model.size());
                        if (percent >= lastPercent + 20) {
                            lastPercent = percent - percent % 20;
                            progress.accept(model.name() + ": " + lastPercent + "%");
                        }
                    }
                }
                if (!sha256(partial).equals(model.sha256())) {
                    Files.deleteIfExists(partial.toPath());
                    throw new IOException("o arquivo " + model.name() + " baixado está corrompido (SHA-256 não confere)");
                }
                Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                progress.accept(model.name() + " pronto.");
            }
            return null;
        });
    }

    private interface Step<T> {
        T call() throws Exception;
    }

    /** Roda um passo na thread do instalador, um de cada vez. */
    private <T> CompletableFuture<T> run(Step<T> step) {
        return CompletableFuture.supplyAsync(() -> {
            busy = true;
            try {
                return step.call();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("instalação interrompida", e);
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            } finally {
                busy = false;
            }
        }, executor);
    }

    private static void exec(List<String> command, Consumer<String> output, long timeoutMinutes, String failure)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        List<String> last = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.accept(line.trim());
                last.add(line);
                if (last.size() > 5) {
                    last.remove(0);
                }
            }
        }
        if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IOException(failure + " (passou de " + timeoutMinutes + " min)");
        }
        if (process.exitValue() != 0) {
            throw new IOException(failure + " Última saída: " + String.join(" / ", last).trim());
        }
    }

    private static String sha256(File file) throws IOException {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
