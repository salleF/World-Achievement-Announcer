package br.sinosdomundo;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Confere se saiu uma versão nova. Lê um arquivo de texto público: a primeira linha é a versão mais nova
 * e a segunda (opcional) é o endereço da página de download. A API do BuiltByBit exige token pessoal,
 * que não pode ir dentro do plugin; por isso o arquivo fica num endereço público (ex.: GitHub).
 */
public final class UpdateChecker {

    /** Arquivo com a versão mais nova. Vazio = não confere. -Dsinos.versionUrl=... troca o endereço (testes). */
    static final String VERSION_URL = System.getProperty("sinos.versionUrl",
            "https://raw.githubusercontent.com/salleF/World-Achievement-Announcer/main/version.txt");

    private final Plugin plugin;
    private volatile String latest;
    private volatile String page = "";

    public UpdateChecker(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Confere em segundo plano e avisa no console se houver versão nova. */
    public void check() {
        if (VERSION_URL.isBlank()) {
            return;
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(VERSION_URL)).timeout(Duration.ofSeconds(10))
                .header("User-Agent", "WorldAchievementAnnouncer/" + current()).GET().build();
        CompletableFuture<HttpResponse<String>> response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        response.thenAccept(r -> {
            if (r.statusCode() != 200) {
                return;
            }
            String[] lines = r.body().strip().split("\\R");
            if (lines.length == 0 || lines[0].isBlank()) {
                return;
            }
            String found = lines[0].strip();
            if (compare(found, current()) > 0) {
                latest = found;
                page = lines.length > 1 ? lines[1].strip() : "";
                plugin.getLogger().warning("Nova versão disponível: " + found + " (você usa " + current() + ")."
                        + (page.isEmpty() ? "" : " " + page));
            }
        }).exceptionally(e -> null);
    }

    /** Avisa o admin que entrou, se houver versão nova. */
    public void notify(Player player) {
        String found = latest;
        if (found == null || !player.hasPermission("sinos.admin")) {
            return;
        }
        String text = Lang.t("<gold>[Sinos]</gold> <gray>Nova versão disponível:</gray> <yellow>") + found
                + Lang.t("</yellow> <gray>(você usa ") + current() + ")</gray>"
                + (page.isEmpty() ? "" : " <click:open_url:'" + page + "'><aqua><u>" + Lang.t("baixar a versão nova") + "</u></aqua></click>");
        Bukkit.getScheduler().runTaskLater(plugin, () -> player.sendMessage(MiniMessage.miniMessage().deserialize(text)), 60L);
    }

    private String current() {
        return plugin.getPluginMeta().getVersion();
    }

    /** Compara versões numéricas ("1.2.0" &gt; "1.1.9"); partes que não são números contam como 0. */
    static int compare(String a, String b) {
        String[] x = a.split("[.-]");
        String[] y = b.split("[.-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int diff = Integer.compare(number(x, i), number(y, i));
            if (diff != 0) {
                return diff;
            }
        }
        return 0;
    }

    private static int number(String[] parts, int i) {
        if (i >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[i].replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
