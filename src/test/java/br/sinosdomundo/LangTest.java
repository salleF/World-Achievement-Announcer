package br.sinosdomundo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LangTest {

    @AfterEach
    void reset() {
        Lang.useForTests(Lang.PORTUGUESE, Map.of());
    }

    @Test
    void portuguesSoTiraOsMarcadores() {
        Lang.useForTests(Lang.PORTUGUESE, Map.of());
        assertEquals("Conquista: dragão", Lang.t("Conquista: " + Lang.keep("dragão")));
    }

    @Test
    void trechoMaisLongoVence() {
        Lang.useForTests("en", Map.of("Conquista ", "Achievement ", "Conquista anunciada.", "Achievement announced."));
        assertEquals("Achievement announced.", Lang.t("Conquista anunciada."));
        assertEquals("Achievement x", Lang.t("Conquista x"));
    }

    @Test
    void textoDoUsuarioNaoETraduzido() {
        Lang.useForTests("en", Map.of("Conquista não encontrada: ", "Achievement not found: ", "não", "no"));
        assertEquals("Achievement not found: não-existe", Lang.t("Conquista não encontrada: " + Lang.keep("não-existe")));
    }

    @Test
    void traducaoNaoETraduzidaDeNovo() {
        // "Batidas" vira "Strikes"; o trecho "Strikes" (se existisse) não pode mexer no resultado
        Lang.useForTests("en", Map.of("<yellow>Batidas", "<yellow>Strikes", "Strikes", "ERRADO"));
        assertEquals("<yellow>Strikes: 3", Lang.t("<yellow>Batidas: 3"));
    }

    @Test
    void variasPartesComValoresNoMeio() {
        Lang.useForTests("en", Map.of("<gray>Conquistas: <white>", "<gray>Achievements: <white>", " concluídas)", " completed)"));
        assertEquals("<gray>Achievements: <white>9 <gray>(3 completed)", Lang.t("<gray>Conquistas: <white>9 <gray>(3 concluídas)"));
    }
}
