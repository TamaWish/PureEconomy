package io.github.tamawish.pureeconomy.hook;

import io.github.tamawish.pureeconomy.PureEconomy;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

/**
 * Anonymous usage metrics via bStats. Server owners can opt out in
 * {@code plugins/bStats/config.yml}.
 */
public final class MetricsHook {

    private static final int BSTATS_PLUGIN_ID = 33797;

    private MetricsHook() {
    }

    public static void register(PureEconomy plugin) {
        try {
            Metrics metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);

            metrics.addCustomChart(new SimplePie("vault_hooked", () ->
                    plugin.getServer().getPluginManager().getPlugin("Vault") != null ? "Yes" : "No"));

            metrics.addCustomChart(new SimplePie("placeholderapi_hooked", () ->
                    plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null ? "Yes" : "No"));

            metrics.addCustomChart(new SimplePie("multi_currency", () ->
                    plugin.economy().currencyIds().size() > 1 ? "Yes" : "No"));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not register bStats metrics: " + e.getMessage());
        }
    }
}
