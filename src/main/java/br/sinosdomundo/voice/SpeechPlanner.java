package br.sinosdomundo.voice;

import br.sinosdomundo.voice.VoiceBank.Clip;
import br.sinosdomundo.voice.VoiceBank.Kind;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforma a frase do config em uma sequência de áudios do banco, cada um começando num tick.
 *
 * <p>Trechos fixos da frase e nomes viram um áudio inteiro cada (soa como uma frase gerada de uma
 * vez). Uma conquista sem áudio próprio é falada palavra por palavra, como reserva.
 */
public final class SpeechPlanner {

    /** Um áudio que deve tocar {@code tick} ticks depois do começo da fala. */
    public record Cue(int tick, String soundKey) {
    }

    /** A fala inteira: os áudios e quantos ticks ela dura. */
    public record Speech(List<Cue> cues, int lengthTicks) {
        public static final Speech EMPTY = new Speech(List.of(), 0);
    }

    static final double TICK_MS = 50;
    static final int PAUSE_PIECE = 2;
    static final int PAUSE_WORD = 1;
    static final int PAUSE_COMMA = 4;
    static final int PAUSE_STOP = 7;

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(conquista|jogadores|final|achievement|players|closing)}");
    private static final Pattern PIECE = Pattern.compile("[^,.!?]+[,.!?]?|[,.!?]");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private SpeechPlanner() {
    }

    /**
     * Todos os áudios que as frases e as conquistas configuradas precisam, para gerar ao ligar.
     *
     * @param phrases frases próprias de conquistas (além da frase padrão)
     * @param spokenTexts textos falados no lugar de {conquista} e frases finais próprias de conquistas
     */
    public static Map<String, Kind> requiredTexts(VoiceSettings settings, Collection<String> phrases,
            Collection<String> spokenTexts) {
        Map<String, Kind> texts = new LinkedHashMap<>();
        List<String> allPhrases = new ArrayList<>();
        allPhrases.add(settings.phrase());
        allPhrases.addAll(phrases);
        for (String phrase : allPhrases) {
            for (String piece : fixedPieces(phrase)) {
                texts.put(piece, Kind.TEXTO);
            }
        }
        texts.put(settings.connector(), Kind.TEXTO);
        texts.put(settings.others(), Kind.TEXTO);
        for (String closing : settings.closings()) {
            texts.put(closing, Kind.TEXTO);
        }
        for (String spoken : spokenTexts) {
            if (!spoken.isBlank()) {
                texts.put(spoken, Kind.TEXTO);
            }
        }
        for (String word : settings.commonWords()) {
            texts.put(word.toLowerCase(), Kind.TEXTO);
        }
        return texts;
    }

    /**
     * @param phrase frase com {conquista}, {jogadores} e {final}
     * @param maxNames nomes falados no máximo; o resto vira "e outros jogadores"
     * @param closing frase final já escolhida (pode ser vazia)
     */
    public static Speech plan(VoiceBank bank, VoiceSettings settings, String narrator, String phrase, String conquest,
            List<String> names, int maxNames, String closing) {
        Builder speech = new Builder(bank, narrator, settings.connector(), settings.others());
        Matcher m = PLACEHOLDER.matcher(phrase);
        int last = 0;
        while (m.find()) {
            speech.fixed(phrase.substring(last, m.start()));
            switch (m.group(1)) {
                case "conquista", "achievement" -> speech.conquest(conquest);
                case "jogadores", "players" -> speech.names(names, maxNames);
                default -> speech.closing(closing);
            }
            last = m.end();
        }
        speech.fixed(phrase.substring(last));
        return speech.build();
    }

    /** Trechos fixos da frase, cortados nos nomes e nas pontuações ("Seus contribuidores foram"). */
    static List<String> fixedPieces(String phrase) {
        List<String> pieces = new ArrayList<>();
        for (String part : PLACEHOLDER.split(phrase)) {
            Matcher m = PIECE.matcher(part);
            while (m.find()) {
                String piece = m.group().trim();
                if (!stripPunctuation(piece).isEmpty()) {
                    pieces.add(piece);
                }
            }
        }
        return pieces;
    }

    private static String stripPunctuation(String text) {
        return text.replaceAll("[,.!?\\s]+$", "").replaceAll("^[,.!?\\s]+", "");
    }

    private static int pauseAfter(String piece) {
        String trimmed = piece.trim();
        if (trimmed.endsWith(",")) {
            return PAUSE_COMMA;
        }
        if (trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")) {
            return PAUSE_STOP;
        }
        return PAUSE_PIECE;
    }

    private static final class Builder {
        private final VoiceBank bank;
        private final String narrator;
        private final String connector;
        private final String others;
        private final List<Cue> cues = new ArrayList<>();
        private double time;
        private int end;

        Builder(VoiceBank bank, String narrator, String connector, String others) {
            this.bank = bank;
            this.narrator = narrator;
            this.connector = connector;
            this.others = others;
        }

        void closing(String closing) {
            if (!closing.isBlank()) {
                play(find(Kind.TEXTO, closing));
                time += PAUSE_PIECE;
            }
        }

        private Optional<Clip> find(Kind kind, String text) {
            return bank.find(narrator, kind, text);
        }

        void fixed(String text) {
            Matcher m = PIECE.matcher(text);
            while (m.find()) {
                String piece = m.group().trim();
                if (!stripPunctuation(piece).isEmpty()) {
                    play(find(Kind.TEXTO, piece));
                }
                time += pauseAfter(piece);
            }
        }

        void conquest(String conquest) {
            Optional<Clip> whole = find(Kind.TEXTO, conquest);
            if (whole.isPresent()) {
                play(whole);
                time += PAUSE_PIECE;
                return;
            }
            // Reserva: palavra por palavra, pulando as que não estão no banco
            Matcher m = WORD.matcher(conquest);
            while (m.find()) {
                play(find(Kind.TEXTO, m.group()));
                time += PAUSE_WORD;
            }
        }

        void names(List<String> all, int maxNames) {
            // Nomes ainda sem áudio (jogador que acabou de entrar) contam como "outros jogadores"
            List<Clip> spoken = all.stream().map(n -> find(Kind.NOME, n)).flatMap(Optional::stream).toList();
            boolean cut = spoken.size() > maxNames || spoken.size() < all.size();
            List<Clip> names = spoken.size() > maxNames ? spoken.subList(0, maxNames) : spoken;
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) {
                    if (i == names.size() - 1 && !cut) {
                        time += PAUSE_WORD;
                        play(find(Kind.TEXTO, connector));
                        time += PAUSE_WORD;
                    } else {
                        time += PAUSE_COMMA;
                    }
                }
                play(Optional.of(names.get(i)));
            }
            if (cut) {
                time += PAUSE_COMMA;
                play(find(Kind.TEXTO, others));
            }
            time += PAUSE_PIECE;
        }

        private void play(Optional<Clip> clip) {
            if (clip.isEmpty()) {
                return;
            }
            int start = (int) Math.round(time);
            cues.add(new Cue(start, soundKey(clip.get())));
            time += clip.get().durationMs() / TICK_MS;
            end = (int) Math.ceil(time);
        }

        Speech build() {
            return cues.isEmpty() ? Speech.EMPTY : new Speech(List.copyOf(cues), end);
        }
    }

    public static String soundKey(Clip clip) {
        return "sinos:voz." + clip.id();
    }
}
