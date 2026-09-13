package io.github.tamawish.pureeconomy.hook

import io.github.tamawish.pureeconomy.PureEconomy
import org.bstats.bukkit.Metrics
import org.bstats.charts.SimplePie

/**
 * Anonymous usage metrics via bStats. Server owners can opt out in `plugins/bStats/config.yml`.
 */
object MetricsHook {
    private const val BSTATS_PLUGIN_ID = 33797

    /** Registers bStats charts for Vault, PlaceholderAPI, and multi-currency usage. */
    @JvmStatic
    fun register(plugin: PureEconomy) {
        try {
            val metrics = Metrics(plugin, BSTATS_PLUGIN_ID)

            metrics.addCustomChart(
                SimplePie("vault_hooked") {
                    if (plugin.server.pluginManager.getPlugin("Vault") != null) "Yes" else "No"
                },
            )

            metrics.addCustomChart(
                SimplePie("placeholderapi_hooked") {
                    if (plugin.server.pluginManager.getPlugin("PlaceholderAPI") != null) "Yes" else "No"
                },
            )

            metrics.addCustomChart(
                SimplePie("multi_currency") {
                    if (plugin.economy().currencyIds().size > 1) "Yes" else "No"
                },
            )
        } catch (e: Exception) {
            plugin.logger.warning("Could not register bStats metrics: ${e.message}")
        }
    }
}
