package io.github.tamawish.pureeconomy.hook;

import io.github.tamawish.pureeconomy.PureEconomy;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

/**
 * Optional. Vault only sees the configured default currency because its Economy
 * API is single-currency. PlaceholderAPI exposes the other currencies.
 *
 * <p>PureEconomy loads at {@code STARTUP}; Vault is almost always {@code POSTWORLD}.
 * The first {@link #tryHook()} therefore often runs before Vault is enabled.
 * This listener retries when Vault enables so shops and AuraUtils still see a
 * provider.
 */
public final class VaultHook implements Listener {

    private final PureEconomy plugin;
    private VaultEconomy provider;
    private boolean hooked;
    private boolean loggedMissingVault;

    public VaultHook(PureEconomy plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean isHooked() {
        return hooked;
    }

    public void tryHook() {
        if (hooked) {
            return;
        }
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null) {
            if (!loggedMissingVault) {
                plugin.getLogger().info("Vault not found. Economy still works; shops that need Vault will not hook.");
                loggedMissingVault = true;
            }
            return;
        }
        if (!vault.isEnabled()) {
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

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (hooked) {
            return;
        }
        if ("Vault".equals(event.getPlugin().getName())) {
            tryHook();
        }
    }

    public void unhook() {
        if (!hooked || provider == null) {
            return;
        }
        Bukkit.getServicesManager().unregister(Economy.class, provider);
        hooked = false;
        provider = null;
    }
}
