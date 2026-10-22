package br.sinosdomundo.grouping;

import java.util.ArrayList;
import java.util.List;

/**
 * Divide jogadores em grupos para que cada grupo receba um único sino.
 *
 * <p>Garantia: todo jogador fica a no máximo {@code radius} blocos da posição do sino do seu grupo.
 *
 * <p>Usa cobertura gulosa: escolhe o jogador com mais vizinhos ainda sem sino, forma o grupo com
 * ele e esses vizinhos, e repete. Juntar "quem está perto de quem" em cadeia não serve, porque uma
 * fila de jogadores viraria um grupo enorme com as pontas longe demais do sino.
 */
public final class BellGrouper {

    /** Posição de um jogador. {@code id} identifica o jogador para quem chamou. */
    public record Point(int id, double x, double y, double z) {
    }

    /** Um grupo e a posição do seu sino (antes de ajustes de altura e de blocos sólidos). */
    public record Group(Point leader, List<Point> members, double x, double y, double z) {
    }

    private BellGrouper() {
    }

    public static List<Group> group(List<Point> points, double radius) {
        if (radius <= 0) {
            throw new IllegalArgumentException("radius deve ser positivo: " + radius);
        }
        int n = points.size();
        double radiusSquared = radius * radius;

        // Vizinhos de cada jogador, calculados uma vez só
        List<List<Integer>> neighbors = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            neighbors.add(new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (distanceSquared(points.get(i), points.get(j)) <= radiusSquared) {
                    neighbors.get(i).add(j);
                    neighbors.get(j).add(i);
                }
            }
        }

        boolean[] covered = new boolean[n];
        int remaining = n;
        List<Group> groups = new ArrayList<>();

        while (remaining > 0) {
            int leader = -1;
            int bestCount = -1;
            for (int i = 0; i < n; i++) {
                if (covered[i]) {
                    continue;
                }
                int count = 0;
                for (int j : neighbors.get(i)) {
                    if (!covered[j]) {
                        count++;
                    }
                }
                if (count > bestCount) {
                    bestCount = count;
                    leader = i;
                }
            }

            List<Point> members = new ArrayList<>(bestCount + 1);
            members.add(points.get(leader));
            covered[leader] = true;
            for (int j : neighbors.get(leader)) {
                if (!covered[j]) {
                    members.add(points.get(j));
                    covered[j] = true;
                }
            }
            remaining -= members.size();

            groups.add(placeBell(points.get(leader), members, radiusSquared));
        }
        return groups;
    }

    /**
     * Sino no centro do grupo. Se algum membro ficar longe demais do centro, o sino vai para a
     * posição do líder, que está a até {@code radius} de todos os membros por construção.
     */
    private static Group placeBell(Point leader, List<Point> members, double radiusSquared) {
        double sumX = 0;
        double sumY = 0;
        double sumZ = 0;
        for (Point p : members) {
            sumX += p.x();
            sumY += p.y();
            sumZ += p.z();
        }
        Point center = new Point(-1, sumX / members.size(), sumY / members.size(), sumZ / members.size());

        for (Point p : members) {
            if (distanceSquared(p, center) > radiusSquared) {
                return new Group(leader, List.copyOf(members), leader.x(), leader.y(), leader.z());
            }
        }
        return new Group(leader, List.copyOf(members), center.x(), center.y(), center.z());
    }

    static double distanceSquared(Point a, Point b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
