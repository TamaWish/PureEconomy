package io.github.tamawish.pureeconomy.hook

import io.github.tamawish.pureeconomy.PureEconomy
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.plugin.ServicePriority

/**
 * Optional Vault bridge for the configured default currency only.
 *
 * PureEconomy loads at `STARTUP`; Vault is almost always `POSTWORLD`. The first [tryHook] therefore
 * often runs before Vault is enabled. This listener retries when Vault enables so shops still see a
 * provider.
 */
class VaultHook(
    private val plugin: PureEconomy,
) : Listener {
    private var provider: VaultEconomy? = null
    private var hooked: Boolean = false
    private var loggedMissingVault: Boolean = false

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /** Returns whether a Vault economy provider is registered. */
    fun isHooked(): Boolean = hooked

    /** Attempts to register PureEconomy as the Vault economy provider. */
    fun tryHook() {
        if (hooked) {
            return
        }
        val vault = Bukkit.getPluginManager().getPlugin("Vault")
        if (vault == null) {
            if (!loggedMissingVault) {
                plugin.logger.info(
                    "Vault not found. Economy still works; shops that need Vault will not hook.",
                )
                loggedMissingVault = true
            }
            return
        }
        if (!vault.isEnabled) {
            return
        }
        try {
            Class.forName("net.milkbowl.vault.economy.Economy")
        } catch (_: ClassNotFoundException) {
            plugin.logger.info("Vault present but Economy API missing.")
            return
        }
        provider = VaultEconomy(plugin)
        Bukkit.getServicesManager().register(Economy::class.java, provider!!, plugin, ServicePriority.Highest)
        hooked = true
        plugin.logger.info(
            "Registered Vault economy for default currency: ${plugin.economy().defaultId()}",
        )
    }

    /** Retries the hook when Vault enables after PureEconomy. */
    @EventHandler
    fun onPluginEnable(event: PluginEnableEvent) {
        if (hooked) {
            return
        }
        if (event.plugin.name == "Vault") {
            tryHook()
        }
    }

    /** Unregisters the Vault economy provider if one was hooked. */
    fun unhook() {
        val registered = provider
        if (!hooked || registered == null) {
            return
        }
        Bukkit.getServicesManager().unregister(Economy::class.java, registered)
        hooked = false
        provider = null
    }
}
