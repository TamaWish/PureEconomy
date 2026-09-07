package io.github.tamawish.pureeconomy.listener;

import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.util.UpdateChecker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Creates accounts on join and flushes balances on quit. */
public final class PlayerConnectionListener implements Listener {

  private final PureEconomy plugin;
  private final UpdateChecker updateChecker;

  /**
   * Creates a connection listener.
   *
   * @param plugin owning plugin instance
   * @param updateChecker optional release notifier; may be null when disabled later
   */
  public PlayerConnectionListener(PureEconomy plugin, UpdateChecker updateChecker) {
    this.plugin = plugin;
    this.updateChecker = updateChecker;
  }

  /**
   * Ensures a wallet exists for the joining player and shows update notices to admins.
   *
   * @param event join event from the server
   */
  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    if (plugin.getConfig().getBoolean("create-on-join", true)) {
      plugin.economy().ensureAccount(player.getUniqueId(), player.getName());
    }
    if (updateChecker != null) {
      updateChecker.notifyPlayerIfNeeded(player);
    }
  }

  /**
   * Persists the quitting player's account asynchronously.
   *
   * @param event quit event from the server
   */
  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    plugin.economy().savePlayerAsync(event.getPlayer().getUniqueId());
  }
}
