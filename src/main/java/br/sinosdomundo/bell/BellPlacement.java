package br.sinosdomundo.bell;

import br.sinosdomundo.BellSettings;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Escolhe onde o sino flutua, evitando que ele fique dentro de blocos sólidos. */
final class BellPlacement {

    private BellPlacement() {
    }

    /** Jogador sozinho: à frente de onde ele olha, acima dos olhos, para o sino cair na tela. */
    static Location solo(Player player, BellSettings settings) {
        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().setY(0);
        if (forward.lengthSquared() < 1e-6) {
            // Olhando reto para cima ou para baixo: usa a direção do corpo
            forward = new Vector(-Math.sin(Math.toRadians(eye.getYaw())), 0, Math.cos(Math.toRadians(eye.getYaw())));
        }
        forward.normalize().multiply(settings.soloDistance());
        Location target = eye.clone().add(forward).add(0, settings.soloHeight(), 0);
        return safe(target, List.of(player), player);
    }

    /** Grupo: o ponto calculado pelo agrupamento, erguido acima do grupo. */
    static Location group(Location bellPoint, List<Player> members, Player leader, BellSettings settings) {
        return safe(bellPoint.clone().add(0, settings.heightAbove(), 0), members, leader);
    }

    /**
     * Se o ponto cair dentro de bloco sólido (caverna, montanha), desce até a altura dos olhos do
     * jogador mais próximo; se ainda for sólido, fica 2 blocos acima do líder.
     */
    static Location safe(Location target, List<Player> members, Player leader) {
        if (isFree(target)) {
            return target;
        }
        Player nearest = leader;
        double best = Double.MAX_VALUE;
        for (Player p : members) {
            double d = p.getLocation().distanceSquared(target);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        Location lowered = target.clone();
        lowered.setY(nearest.getEyeLocation().getY());
        if (isFree(lowered)) {
            return lowered;
        }
        return leader.getLocation().add(0, 2, 0);
    }

    private static boolean isFree(Location location) {
        return location.getBlock().isPassable();
    }
}
