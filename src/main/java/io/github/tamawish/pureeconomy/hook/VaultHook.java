package io.github.tamawish.pureeconomy.hook;

import io.github.tamawish.pureeconomy.PureEconomy;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

/**
 * Optional. Vault only sees the configured default currency because its Economy
 * API is single-currency. PlaceholderAPI exposes the other currencies.
 */
public final class VaultHook {

    private final PureEconomy plugin;
    private VaultEconomy provider;
    private boolean hooked;

    public VaultHook(PureEconomy plugin) {
        this.plugin = plugin;
    }

    public void tryHook() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found. Economy still works; shops that need Vault will not hook.");
            return;
        }
        try {
            Class.forName("net.milkbowl.vault.economy.Economy");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("Vault present but Economy API missing.");
            return;
        }
        provider = new VaultEconomy(plugin);
        Bukkit.getServicesManager().register(Economy.class, provider, plugin, ServicePriority.Highest);
        hooked = true;
        plugin.getLogger().info("Registered Vault economy for default currency: " + plugin.economy().defaultId());
    }

    public void unhook() {
        if (!hooked || provider == null) {
            return;
        }
        Bukkit.getServicesManager().unregister(Economy.class, provider);
        hooked = false;
    }
}
