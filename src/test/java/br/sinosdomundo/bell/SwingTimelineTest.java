package br.sinosdomundo.bell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.sinosdomundo.bell.SwingTimeline.Keyframe;
import java.util.List;
import org.junit.jupiter.api.Test;

class SwingTimelineTest {

    @Test
    void naBatidaSinoEBadaloFicamNoAnguloDeContato() {
        List<Keyframe> frames = SwingTimeline.build(3, 8, 20.5);
        List<Keyframe> hits = frames.stream().filter(Keyframe::hit).toList();

        assertEquals(3, hits.size());
        for (Keyframe hit : hits) {
            assertEquals(20.5, Math.abs(hit.bellAngle() - hit.clapperAngle()), 1e-4);
            // Sino e badalo vão para lados opostos
            assertTrue(Math.signum(hit.bellAngle()) == -Math.signum(hit.clapperAngle()));
        }
    }

    @Test
    void batidasAlternamOLado() {
        List<Keyframe> hits = SwingTimeline.build(3, 8, 20).stream().filter(Keyframe::hit).toList();

        assertTrue(hits.get(0).bellAngle() > 0);
        assertTrue(hits.get(1).bellAngle() < 0);
        assertTrue(hits.get(2).bellAngle() > 0);
        assertEquals(8, hits.get(1).tick() - hits.get(0).tick());
    }

    @Test
    void comecaETerminaEmRepouso() {
        List<Keyframe> frames = SwingTimeline.build(2, 8, 20);

        assertEquals(new Keyframe(0, 0, 0, false), frames.get(0));
        Keyframe last = frames.get(frames.size() - 1);
        assertEquals(0, last.bellAngle());
        assertEquals(0, last.clapperAngle());
        for (int i = 1; i < frames.size(); i++) {
            assertTrue(frames.get(i).tick() > frames.get(i - 1).tick(), "ticks em ordem");
        }
    }

    @Test
    void batidasNosTicksDoRitmo() {
        List<Integer> ritmo = List.of(9, 19, 29, 38, 45, 52, 58);
        List<Keyframe> frames = SwingTimeline.build(ritmo, 20.5);

        assertEquals(ritmo, frames.stream().filter(Keyframe::hit).map(Keyframe::tick).toList());
        assertEquals(58, SwingTimeline.lastHitTick(frames));
        for (int i = 1; i < frames.size(); i++) {
            assertTrue(frames.get(i).tick() > frames.get(i - 1).tick(), "ticks em ordem");
        }
    }

    @Test
    void semBadaloOSinoBalancaSozinho() {
        List<Keyframe> hits = SwingTimeline.build(1, 8, 0).stream().filter(Keyframe::hit).toList();

        assertEquals(SwingTimeline.SOLO_AMPLITUDE, hits.get(0).bellAngle());
        assertEquals(0, hits.get(0).clapperAngle(), 1e-6);
    }
}
