package io.github.tamawish.pureeconomy.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Folia-aware scheduling helpers.
 *
 * <p>When Paper/Folia region schedulers are present, every path uses them and never calls {@link
 * org.bukkit.scheduler.BukkitScheduler} (which throws on Folia). Spigot/Bukkit fall back to the
 * classic scheduler.
 */
public final class Schedulers {

  private static final boolean FOLIA_LIKE = hasGlobalRegionScheduler();

  private Schedulers() {}

  private static boolean hasGlobalRegionScheduler() {
    try {
      Bukkit.class.getMethod("getGlobalRegionScheduler");
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  /**
   * Returns whether Paper/Folia region schedulers are available.
   *
   * @return {@code true} on Paper and Folia; {@code false} on Spigot/Bukkit
   */
  public static boolean isFoliaLike() {
    return FOLIA_LIKE;
  }

  /**
   * Runs a task on the global region (Paper/Folia) or the main server thread (Spigot/Bukkit).
   *
   * @param plugin owning plugin
   * @param task work to execute
   */
  public static void runGlobal(Plugin plugin, Runnable task) {
    if (FOLIA_LIKE) {
      Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    } else {
      Bukkit.getScheduler().runTask(plugin, task);
    }
  }

  /**
   * Schedules a repeating global-region (or main-thread) timer.
   *
   * @param plugin owning plugin
   * @param task work to execute each period
   * @param delayTicks ticks before the first run
   * @param periodTicks ticks between runs
   * @return opaque task handle suitable for {@link #cancel(Object)}
   */
  public static Object runGlobalTimer(
      Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
    if (FOLIA_LIKE) {
      return Bukkit.getGlobalRegionScheduler()
          .runAtFixedRate(
              plugin, scheduled -> task.run(), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }
    return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
  }

  /**
   * Runs a task asynchronously for I/O or other off-thread work.
   *
   * @param plugin owning plugin
   * @param task work to execute off the server thread
   */
  public static void runAsync(Plugin plugin, Runnable task) {
    if (FOLIA_LIKE) {
      Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    } else {
      Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }
  }

  /**
   * Runs a task on the player's owning region (Folia/Paper) or the main thread (Spigot/Bukkit).
   *
   * <p>Use this when messaging or touching a player other than the current command sender so Folia
   * region affinity is respected.
   *
   * @param plugin owning plugin
   * @param player entity whose region owns the task
   * @param task work to execute
   */
  public static void runAtEntity(Plugin plugin, Player player, Runnable task) {
    if (FOLIA_LIKE) {
      player.getScheduler().run(plugin, scheduled -> task.run(), null);
    } else {
      Bukkit.getScheduler().runTask(plugin, task);
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
  public static void runAtEntityLater(
      Plugin plugin, Player player, Runnable task, long delayTicks) {
    long delay = Math.max(1L, delayTicks);
    if (FOLIA_LIKE) {
      player.getScheduler().runDelayed(plugin, scheduled -> task.run(), null, delay);
    } else {
      Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }
  }

  /**
   * Cancels a task returned by {@link #runGlobalTimer(Plugin, Runnable, long, long)}.
   *
   * @param task opaque Folia or Bukkit task handle; ignored when {@code null}
   */
  public static void cancel(Object task) {
    if (task == null) {
      return;
    }
    try {
      task.getClass().getMethod("cancel").invoke(task);
      return;
    } catch (Exception ignored) {
      // Fall through to BukkitTask handling.
    }
    if (task instanceof BukkitTask bukkitTask) {
      bukkitTask.cancel();
    }
  }
}
