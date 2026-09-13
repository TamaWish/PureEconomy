package io.github.tamawish.pureeconomy.util

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask

/**
 * Folia-aware scheduling helpers.
 *
 * When Paper/Folia region schedulers are present, every path uses them and never calls
 * [org.bukkit.scheduler.BukkitScheduler] (which throws on Folia). Spigot/Bukkit fall back to the
 * classic scheduler.
 */
object Schedulers {
    private val FOLIA_LIKE: Boolean = hasGlobalRegionScheduler()

    private fun hasGlobalRegionScheduler(): Boolean =
        try {
            Bukkit::class.java.getMethod("getGlobalRegionScheduler")
            true
        } catch (_: NoSuchMethodException) {
            false
        }

    /**
     * Returns whether Paper/Folia region schedulers are available.
     *
     * @return `true` on Paper and Folia; `false` on Spigot/Bukkit
     */
    @JvmStatic
    fun isFoliaLike(): Boolean = FOLIA_LIKE

    /**
     * Runs a task on the global region (Paper/Folia) or the main server thread (Spigot/Bukkit).
     *
     * @param plugin owning plugin
     * @param task work to execute
     */
    @JvmStatic
    fun runGlobal(
        plugin: Plugin,
        task: Runnable,
    ) {
        if (FOLIA_LIKE) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task)
        } else {
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }

    /**
     * Schedules a repeating global-region (or main-thread) timer.
     *
     * @param plugin owning plugin
     * @param task work to execute each period
     * @param delayTicks ticks before the first run
     * @param periodTicks ticks between runs
     * @return opaque task handle suitable for [cancel]
     */
    @JvmStatic
    fun runGlobalTimer(
        plugin: Plugin,
        task: Runnable,
        delayTicks: Long,
        periodTicks: Long,
    ): Any =
        if (FOLIA_LIKE) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                { task.run() },
                maxOf(1L, delayTicks),
                maxOf(1L, periodTicks),
            )
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks)
        }

    /**
     * Runs a task asynchronously for I/O or other off-thread work.
     *
     * @param plugin owning plugin
     * @param task work to execute off the server thread
     */
    @JvmStatic
    fun runAsync(
        plugin: Plugin,
        task: Runnable,
    ) {
        if (FOLIA_LIKE) {
            Bukkit.getAsyncScheduler().runNow(plugin) { task.run() }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task)
        }
    }

    /**
     * Runs a task on the player's owning region (Folia/Paper) or the main thread (Spigot/Bukkit).
     *
     * Use this when messaging or touching a player other than the current command sender so Folia
     * region affinity is respected.
     *
     * @param plugin owning plugin
     * @param player entity whose region owns the task
     * @param task work to execute
     */
    @JvmStatic
    fun runAtEntity(
        plugin: Plugin,
        player: Player,
        task: Runnable,
    ) {
        if (FOLIA_LIKE) {
            player.scheduler.run(plugin, { task.run() }, null)
        } else {
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }

    /**
     * Runs a delayed task on the player's owning region (Folia/Paper) or the main thread
     * (Spigot/Bukkit).
     *
     * @param plugin owning plugin
     * @param player entity whose region owns the task
     * @param task work to execute
     * @param delayTicks ticks to wait before running
     */
    @JvmStatic
    fun runAtEntityLater(
        plugin: Plugin,
        player: Player,
        task: Runnable,
        delayTicks: Long,
    ) {
        val delay = maxOf(1L, delayTicks)
        if (FOLIA_LIKE) {
            player.scheduler.runDelayed(plugin, { task.run() }, null, delay)
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay)
        }
    }

    /**
     * Cancels a task returned by [runGlobalTimer].
     *
     * @param task opaque Folia or Bukkit task handle; ignored when `null`
     */
    @JvmStatic
    fun cancel(task: Any?) {
        if (task == null) {
            return
        }
        try {
            task.javaClass.getMethod("cancel").invoke(task)
            return
        } catch (_: Exception) {
            // Fall through to BukkitTask handling.
        }
        if (task is BukkitTask) {
            task.cancel()
        }
    }
}
