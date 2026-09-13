package io.github.tamawish.pureeconomy.hook

import io.github.tamawish.pureeconomy.PureEconomy
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginEnableEvent

/**
 * Optional PlaceholderAPI bridge. Retries when PlaceholderAPI enables after PureEconomy's STARTUP
 * `onEnable`.
 */
class PlaceholderHook(
    private val plugin: PureEconomy,
) : Listener {
    private var expansion: PureEconomyExpansion? = null

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /** Attempts to register PureEconomy placeholders with PlaceholderAPI. */
    fun tryHook() {
        if (expansion != null) {
            return
        }
        val placeholderApi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI")
        if (placeholderApi == null || !placeholderApi.isEnabled) {
            return
        }

        expansion = PureEconomyExpansion(plugin)
        if (expansion!!.register()) {
            plugin.logger.info("Registered PlaceholderAPI placeholders for all currencies.")
        } else {
            expansion = null
            plugin.logger.warning("Could not register PlaceholderAPI placeholders.")
        }
    }

    /** Retries the hook when PlaceholderAPI enables after PureEconomy. */
    @EventHandler
    fun onPluginEnable(event: PluginEnableEvent) {
        if (expansion != null) {
            return
        }
        if (event.plugin.name == "PlaceholderAPI") {
            tryHook()
        }
    }

    /** Unregisters the PlaceholderAPI expansion when present. */
    fun unhook() {
        expansion?.unregister()
        expansion = null
    }
}
