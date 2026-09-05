package io.github.tamawish.pureeconomy.util;

import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.lang.Lang;
import io.github.tamawish.pureeconomy.permission.Permissions.Node;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional GitHub release checker. Network access always runs asynchronously.
 */
public final class UpdateChecker {

    public static final String GITHUB_PAGE = "https://github.com/TamaWish/PureEconomy";
    public static final String RELEASES_PAGE = GITHUB_PAGE + "/releases";
    private static final String API_LATEST =
            "https://api.github.com/repos/TamaWish/PureEconomy/releases/latest";
    private static final Duration TIMEOUT = Duration.ofSeconds(8);
    private static final Pattern TAG_NAME = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern HTML_URL = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SAFE_URL =
            Pattern.compile("^https://github\\.com/TamaWish/PureEconomy(/.*)?$");

    private final PureEconomy plugin;
    private volatile String latestVersion;
    private volatile String latestUrl = RELEASES_PAGE;
    private volatile boolean updateAvailable;

    public UpdateChecker(PureEconomy plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("update-checker.enabled", true);
    }

    public void checkAsync() {
        if (!isEnabled()) {
            updateAvailable = false;
            return;
        }
        Schedulers.runAsync(plugin, this::fetchLatest);
    }

    public void notifyPlayerIfNeeded(Player player) {
        if (player == null || !player.isOnline() || !isEnabled() || !updateAvailable) {
            return;
        }
        if (!plugin.permissions().has(player, Node.ADMIN)) {
            return;
        }
        Schedulers.runAtEntityLater(plugin, player, () -> sendNotice(player), 40L);
    }

    static int compareVersions(String current, String remote) {
        int[] left = versionParts(current);
        int[] right = versionParts(remote);
        int count = Math.max(left.length, right.length);
        for (int i = 0; i < count; i++) {
            int currentPart = i < left.length ? left[i] : 0;
            int remotePart = i < right.length ? right[i] : 0;
            if (currentPart != remotePart) {
                return Integer.compare(currentPart, remotePart);
            }
        }
        return 0;
    }

    private static int[] versionParts(String raw) {
        if (raw == null || raw.isBlank()) {
            return new int[0];
        }
        String value = raw.trim();
        if (value.startsWith("v") || value.startsWith("V")) {
            value = value.substring(1);
        }
        int dash = value.indexOf('-');
        if (dash >= 0) {
            value = value.substring(0, dash);
        }
        String[] pieces = value.split("\\.");
        int[] parts = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            String digits = pieces[i].replaceAll("[^0-9].*$", "");
            if (digits.isEmpty()) {
                continue;
            }
            try {
                parts[i] = Integer.parseInt(digits);
            } catch (NumberFormatException ignored) {
                parts[i] = 0;
            }
        }
        return parts;
    }

    static String firstJsonString(String json, Pattern pattern) {
        if (json == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void fetchLatest() {
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_LATEST))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "PureEconomy")
                    .GET()
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                plugin.getLogger().fine(
                        "GitHub update check returned HTTP " + response.statusCode());
                return;
            }
            applyLatest(response.body());
        } catch (Exception exception) {
            if (plugin.isEnabled()) {
                plugin.getLogger().log(Level.FINE, "GitHub update check failed", exception);
            }
        }
    }

    private void applyLatest(String body) {
        String tag = firstJsonString(body, TAG_NAME);
        if (tag == null || tag.isBlank()) {
            return;
        }
        String current = plugin.getDescription().getVersion();
        latestVersion = stripVersionPrefix(tag);
        String url = firstJsonString(body, HTML_URL);
        latestUrl = url != null && SAFE_URL.matcher(url).matches() ? url : RELEASES_PAGE;
        updateAvailable = compareVersions(current, tag) < 0;
        if (!updateAvailable || !plugin.isEnabled()) {
            return;
        }
        plugin.getLogger().info("A new PureEconomy release is available: "
                + latestVersion + " (running " + current + "). " + latestUrl);
        Schedulers.runGlobal(plugin, this::notifyOnlineAdmins);
    }

    private static String stripVersionPrefix(String version) {
        return version.startsWith("v") || version.startsWith("V")
                ? version.substring(1)
                : version;
    }

    private void notifyOnlineAdmins() {
        if (!plugin.isEnabled() || !isEnabled() || !updateAvailable) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (plugin.permissions().has(player, Node.ADMIN)) {
                Schedulers.runAtEntityLater(plugin, player, () -> sendNotice(player), 1L);
            }
        }
    }

    private void sendNotice(Player player) {
        if (!plugin.isEnabled() || !player.isOnline() || !isEnabled() || !updateAvailable) {
            return;
        }
        String current = plugin.getDescription().getVersion();
        player.sendMessage(plugin.lang().get("update-available", Lang.of(
                "latest", latestVersion != null ? latestVersion : "",
                "current", current)));

        TextComponent click = new TextComponent(Lang.color(plugin.lang().raw("update-click")));
        click.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, latestUrl));
        click.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                TextComponent.fromLegacyText(Lang.color(plugin.lang().raw("update-hover")
                        .replace("{url}", latestUrl)))));
        player.spigot().sendMessage(click);
    }
}
