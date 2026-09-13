package io.github.tamawish.pureeconomy.util

import io.github.tamawish.pureeconomy.PureEconomy
import io.github.tamawish.pureeconomy.lang.Lang
import io.github.tamawish.pureeconomy.permission.Permissions.Node
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.entity.Player
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.logging.Level
import java.util.regex.Pattern

/** Optional GitHub release checker. Network access always runs asynchronously. */
class UpdateChecker(
    private val plugin: PureEconomy,
) {
    @Volatile
    private var latestVersion: String? = null

    @Volatile
    private var latestUrl: String = RELEASES_PAGE

    @Volatile
    private var updateAvailable: Boolean = false

    /** Returns whether the checker is enabled in config. */
    fun isEnabled(): Boolean = plugin.settings().getBoolean("update-checker.enabled", true)

    /** Fetches the latest GitHub release on an async dispatcher when enabled. */
    fun checkAsync() {
        if (!isEnabled()) {
            updateAvailable = false
            return
        }
        Schedulers.runAsync(plugin) { fetchLatest() }
    }

    /** Notifies an online admin about a known newer release after a short delay. */
    fun notifyPlayerIfNeeded(player: Player?) {
        if (player == null || !player.isOnline || !isEnabled() || !updateAvailable) {
            return
        }
        if (!plugin.permissions().has(player, Node.ADMIN)) {
            return
        }
        Schedulers.runAtEntityLater(plugin, player, { sendNotice(player) }, 40L)
    }

    private fun fetchLatest() {
        if (!plugin.isEnabled) {
            return
        }
        try {
            val client =
                HttpClient
                    .newBuilder()
                    .connectTimeout(TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
            val request =
                HttpRequest
                    .newBuilder(URI.create(API_LATEST))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "PureEconomy")
                    .GET()
                    .build()

            // Blocking HTTP on async dispatcher (not Dispatchers.IO).
            @Suppress("BlockingMethodInNonBlockingContext")
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                plugin.logger.fine("GitHub update check returned HTTP ${response.statusCode()}")
                return
            }
            applyLatest(response.body())
        } catch (exception: Exception) {
            if (plugin.isEnabled) {
                plugin.logger.log(Level.FINE, "GitHub update check failed", exception)
            }
        }
    }

    private fun applyLatest(body: String) {
        val tag = firstJsonString(body, TAG_NAME)
        if (tag.isNullOrBlank()) {
            return
        }
        val current = plugin.description.version
        latestVersion = stripVersionPrefix(tag)
        val url = firstJsonString(body, HTML_URL)
        latestUrl = if (url != null && SAFE_URL.matcher(url).matches()) url else RELEASES_PAGE
        updateAvailable = compareVersions(current, tag) < 0
        if (!updateAvailable || !plugin.isEnabled) {
            return
        }
        plugin.logger.info(
            "A new PureEconomy release is available: $latestVersion (running $current). $latestUrl",
        )
        Schedulers.runGlobal(plugin, Runnable { notifyOnlineAdmins() })
    }

    private fun notifyOnlineAdmins() {
        if (!plugin.isEnabled || !isEnabled() || !updateAvailable) {
            return
        }
        for (player in plugin.server.onlinePlayers) {
            if (plugin.permissions().has(player, Node.ADMIN)) {
                Schedulers.runAtEntityLater(plugin, player, { sendNotice(player) }, 1L)
            }
        }
    }

    private fun sendNotice(player: Player) {
        if (!plugin.isEnabled || !player.isOnline || !isEnabled() || !updateAvailable) {
            return
        }
        val current = plugin.description.version
        player.sendMessage(
            plugin.lang().component(
                "update-available",
                Lang.of("latest", latestVersion ?: "", "current", current),
            ),
        )

        val click =
            plugin
                .lang()
                .component("update-click", null)
                .clickEvent(ClickEvent.openUrl(latestUrl))
                .hoverEvent(
                    HoverEvent.showText(
                        plugin.lang().component("update-hover", Lang.of("url", latestUrl)),
                    ),
                )
        player.sendMessage(click)
    }

    companion object {
        /** Public GitHub repository page for PureEconomy. */
        const val GITHUB_PAGE: String = "https://github.com/TamaWish/PureEconomy"

        /** Public releases page used when the API omits a safe URL. */
        const val RELEASES_PAGE: String = "$GITHUB_PAGE/releases"

        private const val API_LATEST =
            "https://api.github.com/repos/TamaWish/PureEconomy/releases/latest"
        private val TIMEOUT: Duration = Duration.ofSeconds(8)
        private val TAG_NAME: Pattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"")
        private val HTML_URL: Pattern = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"")
        private val SAFE_URL: Pattern =
            Pattern.compile("^https://github\\.com/TamaWish/PureEconomy(/.*)?$")

        /**
         * Compares two dotted version strings, ignoring a leading `v` and any `-` suffix.
         *
         * @return negative when [current] is older, zero when equal, positive when newer
         */
        @JvmStatic
        fun compareVersions(
            current: String?,
            remote: String?,
        ): Int {
            val left = versionParts(current)
            val right = versionParts(remote)
            val count = maxOf(left.size, right.size)
            for (i in 0 until count) {
                val currentPart = if (i < left.size) left[i] else 0
                val remotePart = if (i < right.size) right[i] else 0
                if (currentPart != remotePart) {
                    return currentPart.compareTo(remotePart)
                }
            }
            return 0
        }

        private fun versionParts(raw: String?): IntArray {
            if (raw.isNullOrBlank()) {
                return IntArray(0)
            }
            var value = raw.trim()
            if (value.startsWith("v") || value.startsWith("V")) {
                value = value.substring(1)
            }
            val dash = value.indexOf('-')
            if (dash >= 0) {
                value = value.substring(0, dash)
            }
            val pieces = value.split(".")
            val parts = IntArray(pieces.size)
            for (i in pieces.indices) {
                val digits = pieces[i].replace(Regex("[^0-9].*$"), "")
                if (digits.isEmpty()) {
                    continue
                }
                parts[i] =
                    try {
                        digits.toInt()
                    } catch (_: NumberFormatException) {
                        0
                    }
            }
            return parts
        }

        /**
         * Extracts the first capturing group for a pattern from JSON text.
         *
         * @return matched group, or `null` when missing
         */
        @JvmStatic
        fun firstJsonString(
            json: String?,
            pattern: Pattern,
        ): String? {
            if (json == null) {
                return null
            }
            val matcher = pattern.matcher(json)
            return if (matcher.find()) matcher.group(1) else null
        }

        private fun stripVersionPrefix(version: String): String =
            if (version.startsWith("v") || version.startsWith("V")) version.substring(1) else version
    }
}
