package br.sinosdomundo.contrib;

import br.sinosdomundo.contrib.ContributorLedger.Mode;
import br.sinosdomundo.contrib.ContributorLedger.Rules;
import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;

/** Seção "contribuidores" do config.yml. */
public record ContributorSettings(Rules rules, double presenceRadius, boolean ignoreCreative, int maxSpokenNames,
        int maxShownNames, int dimensionWindowTicks) {

    public static ContributorSettings load(FileConfiguration config, Logger logger) {
        Mode mode;
        try {
            mode = Mode.valueOf(config.getString("contribuidores.modo", "DANO").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("contribuidores.modo inválido, usando DANO");
            mode = Mode.DANO;
        }
        Rules rules = new Rules(mode,
                config.getDouble("contribuidores.dano-minimo-porcentagem", 2),
                config.getDouble("contribuidores.dano-minimo", 10),
                Math.max(1, config.getLong("contribuidores.janela-minutos", 5)) * 60_000);
        return new ContributorSettings(rules,
                config.getDouble("contribuidores.raio-presenca", 64),
                config.getBoolean("contribuidores.ignorar-criativo", true),
                Math.max(1, config.getInt("contribuidores.max-nomes-voz", 5)),
                Math.max(1, config.getInt("contribuidores.max-nomes-texto", 10)),
                Math.max(1, config.getInt("contribuidores.janela-dimensao-segundos", 5)) * 20);
    }
}
