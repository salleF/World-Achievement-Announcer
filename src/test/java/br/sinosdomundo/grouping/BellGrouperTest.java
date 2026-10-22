package br.sinosdomundo.grouping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.sinosdomundo.grouping.BellGrouper.Group;
import br.sinosdomundo.grouping.BellGrouper.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BellGrouperTest {

    private static final double RADIUS = 24;

    @Test
    void semJogadoresNaoHaGrupos() {
        assertTrue(BellGrouper.group(List.of(), RADIUS).isEmpty());
    }

    @Test
    void jogadorSozinhoTemUmSinoNaPropriaPosicao() {
        List<Group> groups = BellGrouper.group(List.of(new Point(0, 10, 64, 10)), RADIUS);

        assertEquals(1, groups.size());
        assertEquals(10, groups.get(0).x());
        assertEquals(64, groups.get(0).y());
    }

    @Test
    void tresJogadoresJuntosDividemUmSino() {
        List<Point> points = List.of(
                new Point(0, 0, 64, 0),
                new Point(1, 3, 64, 2),
                new Point(2, -2, 64, 4));

        List<Group> groups = BellGrouper.group(points, RADIUS);

        assertEquals(1, groups.size());
        assertEquals(3, groups.get(0).members().size());
        assertEquals(1.0 / 3, groups.get(0).x(), 1e-9);
        assertEquals(2, groups.get(0).z(), 1e-9);
    }

    @Test
    void jogadoresLongeTemUmSinoCada() {
        List<Point> points = List.of(
                new Point(0, 0, 64, 0),
                new Point(1, 500, 64, 0));

        assertEquals(2, BellGrouper.group(points, RADIUS).size());
    }

    @Test
    void filaDeJogadoresNaoViraUmGrupoSo() {
        // 10 jogadores, cada um a 20 blocos do próximo: em cadeia viraria um grupo de 180 blocos
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            points.add(new Point(i, i * 20.0, 64, 0));
        }

        List<Group> groups = BellGrouper.group(points, RADIUS);

        assertTrue(groups.size() > 1);
        assertCoverage(points, groups);
    }

    @Test
    void jogadoresAleatoriosFicamTodosPertoDoSino() {
        Random random = new Random(42);
        for (int round = 0; round < 200; round++) {
            List<Point> points = new ArrayList<>();
            int count = 1 + random.nextInt(60);
            for (int i = 0; i < count; i++) {
                points.add(new Point(i, random.nextDouble() * 200, 40 + random.nextDouble() * 60,
                        random.nextDouble() * 200));
            }

            assertCoverage(points, BellGrouper.group(points, RADIUS));
        }
    }

    @Test
    void raioInvalidoEhRecusado() {
        assertThrows(IllegalArgumentException.class, () -> BellGrouper.group(List.of(), 0));
    }

    /** Cada jogador está em exatamente um grupo e a até RADIUS do sino desse grupo. */
    private static void assertCoverage(List<Point> points, List<Group> groups) {
        Set<Integer> seen = new HashSet<>();
        for (Group group : groups) {
            Point bell = new Point(-1, group.x(), group.y(), group.z());
            for (Point member : group.members()) {
                assertTrue(seen.add(member.id()), "jogador em dois grupos: " + member.id());
                double distance = Math.sqrt(BellGrouper.distanceSquared(member, bell));
                assertTrue(distance <= RADIUS + 1e-9, "jogador " + member.id() + " a " + distance + " blocos do sino");
            }
        }
        assertEquals(points.size(), seen.size());
    }
}
