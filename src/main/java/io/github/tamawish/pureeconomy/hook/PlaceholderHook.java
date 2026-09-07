package io.github.tamawish.pureeconomy.hook;

import io.github.tamawish.pureeconomy.PureEconomy;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

/**
 * Optional PlaceholderAPI bridge. Retries when PlaceholderAPI enables after PureEconomy's STARTUP
 * {@code onEnable}.
 */
public final class PlaceholderHook implements Listener {

  private final PureEconomy plugin;
  private PureEconomyExpansion expansion;

  /**
   * Creates the hook and registers the late-enable listener.
   *
   * @param plugin owning plugin
   */
  public PlaceholderHook(PureEconomy plugin) {
    this.plugin = plugin;
    plugin.getServer().getPluginManager().registerEvents(this, plugin);
  }

  /** Attempts to register PureEconomy placeholders with PlaceholderAPI. */
  public void tryHook() {
    if (expansion != null) {
      return;
    }
    Plugin placeholderApi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
    if (placeholderApi == null || !placeholderApi.isEnabled()) {
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

  /**
   * Retries the hook when PlaceholderAPI enables after PureEconomy.
   *
   * @param event plugin enable event
   */
  @EventHandler
  public void onPluginEnable(PluginEnableEvent event) {
    if (expansion != null) {
      return;
    }
    if ("PlaceholderAPI".equals(event.getPlugin().getName())) {
      tryHook();
    }
  }

  /** Unregisters the PlaceholderAPI expansion when present. */
  public void unhook() {
    if (expansion != null) {
      expansion.unregister();
      expansion = null;
    }
  }
}
