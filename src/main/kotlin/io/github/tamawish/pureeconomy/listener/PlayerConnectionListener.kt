package io.github.tamawish.pureeconomy.listener

import io.github.tamawish.pureeconomy.PureEconomy
import io.github.tamawish.pureeconomy.util.UpdateChecker
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/** Creates accounts on join and flushes balances on quit. */
class PlayerConnectionListener(
    private val plugin: PureEconomy,
    private val updateChecker: UpdateChecker?,
) : Listener {
    /** Ensures a wallet exists for the joining player and shows update notices to admins. */
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (plugin.settings().getBoolean("create-on-join", true)) {
            plugin.economy().ensureAccountAsync(player.uniqueId, player.name)
        }
        updateChecker?.notifyPlayerIfNeeded(player)
    }

    /** Persists the quitting player's account asynchronously. */
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        plugin.economy().unloadPlayer(event.player.uniqueId)
    }
}
