package br.sinosdomundo.voice;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * O que o Python do servidor consegue fazer: a voz (Piper, Kokoro) e a conversão de músicas precisam
 * dele. Sem Python, o plugin continua tocando o sino, a música e o texto, só sem voz.
 *
 * @param pythonFound o comando do Python rodou
 * @param modules módulos que importaram sem erro (piper, kokoro_onnx, soundfile, numpy)
 * @param detail versão do Python, ou o motivo de não ter rodado
 */
public record PythonHealth(boolean pythonFound, Set<String> modules, String detail) {

    private static final List<String> CHECKED = List.of("piper", "kokoro_onnx", "soundfile", "numpy");
    private static final String PROBE = String.join(";",
            "import json,sys,importlib.util",
            "r={'versao':sys.version.split()[0]}",
            // Sem aspas duplas: no Windows elas se perdem ao passar o código pela linha de comando
            "[r.__setitem__(m, importlib.util.find_spec(m) is not None) for m in "
                    + CHECKED.stream().map(m -> "'" + m + "'").collect(java.util.stream.Collectors.joining(",", "[", "]")) + "]",
            "print(json.dumps(r))");

    public static PythonHealth unknown() {
        return new PythonHealth(false, Set.of(), "ainda verificando");
    }

    /** Roda o Python com um teste rápido de importação. Demora até alguns segundos; chame fora da thread principal. */
    public static PythonHealth check(String python) {
        try {
            Process process = new ProcessBuilder(python, "-c", PROBE).redirectErrorStream(true).start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new PythonHealth(false, Set.of(), "o Python não respondeu em 20 s");
            }
            String out = readAll(process.getInputStream()).trim();
            String[] lines = out.split("\\R");
            JsonObject result = new Gson().fromJson(lines[lines.length - 1], JsonObject.class);
            Set<String> modules = new LinkedHashSet<>();
            for (String m : CHECKED) {
                if (result.has(m) && result.get(m).getAsBoolean()) {
                    modules.add(m);
                }
            }
            return new PythonHealth(true, modules, "Python " + result.get("versao").getAsString());
        } catch (IOException e) {
            return new PythonHealth(false, Set.of(), "comando \"" + python + "\" não encontrado");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PythonHealth(false, Set.of(), "interrompido");
        } catch (RuntimeException e) {
            return new PythonHealth(false, Set.of(), "resposta inesperada do Python");
        }
    }

    /** O narrador consegue gerar voz (Python, módulos do motor dele e arquivos de voz). */
    public boolean canSpeak(VoiceSettings.Narrator narrator) {
        return narrator != null && missingFor(narrator).isEmpty();
    }

    /** A conversão e a análise de músicas próprias funcionam. */
    public boolean canConvertMusic() {
        return pythonFound && modules.contains("soundfile") && modules.contains("numpy");
    }

    /** O que falta para o narrador falar, em frases curtas para o admin. */
    public List<String> missingFor(VoiceSettings.Narrator narrator) {
        List<String> missing = new ArrayList<>();
        if (!pythonFound) {
            missing.add("Python: " + detail);
            return missing;
        }
        String engine = narrator.engine().equals("kokoro") ? "kokoro_onnx" : "piper";
        for (String m : List.of(engine, "soundfile", "numpy")) {
            if (!modules.contains(m)) {
                missing.add("módulo " + m + " (pip install " + pipName(m) + ")");
            }
        }
        if (!narrator.filesPresent()) {
            missing.add("arquivos de voz: " + narrator.model().getPath());
        }
        return missing;
    }

    private static String pipName(String module) {
        return switch (module) {
            case "piper" -> "piper-tts";
            case "kokoro_onnx" -> "kokoro-onnx";
            default -> module;
        };
    }

    private static String readAll(InputStream in) throws IOException {
        try (in) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            in.transferTo(buffer);
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }
}
