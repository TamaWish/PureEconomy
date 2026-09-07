package io.github.tamawish.pureeconomy.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Folia-aware scheduling helpers.
 *
 * <p>Uses Paper {@code GlobalRegionScheduler} / {@code AsyncScheduler} / entity schedulers when
 * present, and falls back to the Bukkit scheduler on Spigot.
 */
public final class Schedulers {

  private static final boolean FOLIA_LIKE = hasGlobal();

  private Schedulers() {}

  private static boolean hasGlobal() {
    try {
      Bukkit.class.getMethod("getGlobalRegionScheduler");
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  /**
   * Runs a task on the global region (Paper/Folia) or the main server thread (Spigot).
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
    try {
      Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    } catch (NoSuchMethodError | NoClassDefFoundError e) {
      Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }
  }

  /**
   * Runs a delayed task on the player's owning region (Folia) or the main thread (Spigot).
   *
   * @param plugin owning plugin
   * @param player entity whose region owns the task
   * @param task work to execute
   * @param delayTicks ticks to wait before running
   */
  public static void runAtEntityLater(
      Plugin plugin, Player player, Runnable task, long delayTicks) {
    try {
      player
          .getScheduler()
          .runDelayed(plugin, scheduled -> task.run(), null, Math.max(1L, delayTicks));
    } catch (NoSuchMethodError | NoClassDefFoundError e) {
      Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks));
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
