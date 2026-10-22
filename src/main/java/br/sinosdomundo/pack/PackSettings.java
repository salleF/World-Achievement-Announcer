package br.sinosdomundo.pack;

import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Seção "pacote" do config.yml.
 *
 * @param external true quando o pacote é hospedado fora (hospedagens que só liberam a porta do jogo)
 * @param externalUrl endereço do pacote.zip hospedado fora
 */
public record PackSettings(boolean enabled, int port, String publicUrl, boolean required, String prompt, boolean external,
        String externalUrl) {

    public static PackSettings load(FileConfiguration config) {
        return new PackSettings(
                config.getBoolean("pacote.ativado", true),
                config.getInt("pacote.porta", 8164),
                config.getString("pacote.url-publica", "").trim(),
                config.getBoolean("pacote.obrigatorio", false),
                config.getString("pacote.mensagem", "<gold>Pacote do World Achievement Announcer</gold>: sino 3D e voz dos anúncios."),
                config.getString("pacote.modo", "proprio").trim().toLowerCase(Locale.ROOT).equals("externo"),
                config.getString("pacote.url-externa", "").trim());
    }
}
