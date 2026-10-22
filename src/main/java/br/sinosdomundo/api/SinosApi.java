package br.sinosdomundo.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * API para outros plugins. Use {@code SinosApi.get()} depois que o World Achievement Announcer estiver ativo
 * (coloque {@code depend} ou {@code softdepend: [WorldAchievementAnnouncer]} no seu plugin.yml).
 *
 * <pre>{@code
 * SinosApi sinos = SinosApi.get();
 * // ao ligar o seu plugin: registra a frase para ela sair com voz natural
 * sinos.registerPhrase("O Rei Goblin foi derrotado na masmorra três");
 * // quando acontecer:
 * sinos.announce("<red>O Rei Goblin caiu!</red>", "O Rei Goblin foi derrotado na masmorra três",
 *         List.of("Ana", "Beto"));
 * }</pre>
 */
public interface SinosApi {

    static SinosApi get() {
        SinosApi api = Holder.instance;
        if (api == null) {
            throw new IllegalStateException("World Achievement Announcer não está ativo");
        }
        return api;
    }

    /**
     * Toca o sino para todos os jogadores.
     *
     * @param display texto mostrado no sino e no chat (MiniMessage)
     * @param spoken texto falado no lugar de {conquista}; sem áudio registrado, é falado palavra por palavra
     * @param contributors nomes de quem concluiu
     * @return false se outro plugin cancelou o anúncio
     */
    boolean announce(String display, String spoken, List<String> contributors);

    /**
     * Conclui uma conquista do config.yml (como se o gatilho dela tivesse acontecido).
     *
     * @return false se ela não existe, já foi concluída (uma-vez) ou foi cancelada
     */
    boolean completeConquest(String conquestId, List<String> contributors);

    /**
     * Gera o áudio de uma frase para ela ser falada com voz natural. Jogadores online só recebem o
     * novo áudio quando entrarem de novo, então registre as frases ao ligar o seu plugin.
     */
    CompletableFuture<Void> registerPhrase(String spoken);

    /** Guarda a instância ativa. Uso interno do plugin. */
    final class Holder {
        private static volatile SinosApi instance;

        private Holder() {
        }

        public static void set(SinosApi api) {
            instance = api;
        }
    }
}
