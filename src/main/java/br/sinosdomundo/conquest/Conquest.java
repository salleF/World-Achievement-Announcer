package br.sinosdomundo.conquest;

/**
 * Uma conquista do config.
 *
 * @param target o que dispara: tipo do mob (MATAR_MOB), id do advancement (CONQUISTA) ou nome do mundo (DIMENSAO)
 * @param display nome mostrado no sino e no chat (MiniMessage)
 * @param spoken texto falado pela voz no lugar de {conquista}
 * @param phrase frase falada própria; vazio usa a frase padrão da voz
 * @param closing frase final: "aleatoria" (sorteia da lista voz.finais), "nenhuma" ou um texto próprio
 * @param narrator narrador próprio; vazio usa o narrador ativo
 */
public record Conquest(String id, Trigger trigger, String target, boolean once, String display, String spoken,
        String phrase, String closing, String narrator) {

    public enum Trigger { MATAR_MOB, MOB_MARCADO, CONQUISTA, DIMENSAO, MANUAL }

    public static final String RANDOM_CLOSING = "aleatoria";
    public static final String NO_CLOSING = "nenhuma";

    /** Frase final própria desta conquista (precisa de áudio), ou vazio se for sorteada ou nenhuma. */
    public String customClosing() {
        return closing.equalsIgnoreCase(RANDOM_CLOSING) || closing.equalsIgnoreCase(NO_CLOSING) ? "" : closing;
    }
}
