package br.sinosdomundo.bell;

import java.util.ArrayList;
import java.util.List;

/**
 * Quadros-chave do balanço: o sino gira para um lado e o badalo para o outro, em torno da coroa.
 * Num quadro de batida, a diferença entre os dois ângulos é exatamente o ângulo em que o badalo
 * encosta na borda, e é nesse tick que o som toca.
 */
public final class SwingTimeline {

    /** Ângulos em graus no eixo Z. {@code hit}: o badalo encosta no sino neste quadro. */
    public record Keyframe(int tick, float bellAngle, float clapperAngle, boolean hit) {
    }

    /** Parte do ângulo de batida que vem do sino; o resto vem do badalo indo para o lado oposto. */
    static final double BELL_SHARE = 0.6;
    /** Sem badalo, o sino balança só ele, com esta amplitude. */
    static final float SOLO_AMPLITUDE = 22;

    private SwingTimeline() {
    }

    /**
     * @param hits quantas batidas
     * @param interval ticks entre uma batida e a próxima (lado a lado)
     * @param contactAngle ângulo entre sino e badalo na batida; 0 = modelo sem badalo
     */
    public static List<Keyframe> build(int hits, int interval, double contactAngle) {
        // A primeira ida, do repouso até a batida, é mais curta que um balanço inteiro
        List<Integer> ticks = new ArrayList<>();
        for (int i = 0, tick = Math.max(3, interval * 2 / 3); i < hits; i++, tick += interval) {
            ticks.add(tick);
        }
        return build(ticks, contactAngle);
    }

    /**
     * Batidas em ticks escolhidos (por exemplo, no ritmo de uma música).
     *
     * @param hitTicks tick de cada batida, em ordem crescente, contado do início do balanço
     */
    public static List<Keyframe> build(List<Integer> hitTicks, double contactAngle) {
        float bellAmp = contactAngle > 0 ? (float) (contactAngle * BELL_SHARE) : SOLO_AMPLITUDE;
        float clapperAmp = contactAngle > 0 ? (float) (contactAngle - bellAmp) : 0;
        List<Keyframe> frames = new ArrayList<>();
        frames.add(new Keyframe(0, 0, 0, false));
        if (hitTicks.isEmpty()) {
            return frames;
        }

        int side = 1;
        for (int hit : hitTicks) {
            frames.add(new Keyframe(hit, side * bellAmp, -side * clapperAmp, true));
            side = -side;
        }
        // Amortece e volta ao repouso, no ritmo do último balanço
        int last = hitTicks.get(hitTicks.size() - 1);
        int interval = hitTicks.size() > 1 ? Math.max(4, last - hitTicks.get(hitTicks.size() - 2)) : 8;
        int tick = last + interval;
        frames.add(new Keyframe(tick, side * bellAmp * 0.45f, -side * clapperAmp * 0.45f, false));
        tick += interval;
        side = -side;
        frames.add(new Keyframe(tick, side * bellAmp * 0.15f, -side * clapperAmp * 0.15f, false));
        tick += Math.max(2, interval / 2);
        frames.add(new Keyframe(tick, 0, 0, false));
        return frames;
    }

    public static int lastHitTick(List<Keyframe> frames) {
        return frames.stream().filter(Keyframe::hit).mapToInt(Keyframe::tick).max().orElse(0);
    }
}
