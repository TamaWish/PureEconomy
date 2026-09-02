package io.github.tamawish.pureeconomy.hook;

import io.github.tamawish.pureeconomy.PureEconomy;
import org.bukkit.Bukkit;

public final class PlaceholderHook {

    private final PureEconomy plugin;
    private PureEconomyExpansion expansion;

    public PlaceholderHook(PureEconomy plugin) {
        this.plugin = plugin;
    }

    public void tryHook() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }

        expansion = new PureEconomyExpansion(plugin);
        if (expansion.register()) {
            plugin.getLogger().info("Registered PlaceholderAPI placeholders for all currencies.");
        } else {
            expansion = null;
            plugin.getLogger().warning("Could not register PlaceholderAPI placeholders.");
        }
    }

    public void unhook() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }
}
