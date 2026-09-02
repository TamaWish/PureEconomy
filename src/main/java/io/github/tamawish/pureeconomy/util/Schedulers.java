package io.github.tamawish.pureeconomy.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Paper GlobalRegionScheduler when present (Paper + Folia), else Bukkit scheduler.
 */
public final class Schedulers {

    private static final boolean FOLIA_LIKE = hasGlobal();

    private Schedulers() {
    }

    private static boolean hasGlobal() {
        try {
            Bukkit.class.getMethod("getGlobalRegionScheduler");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public static void runGlobal(Plugin plugin, Runnable task) {
        if (FOLIA_LIKE) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static Object runGlobalTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA_LIKE) {
            return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduled -> task.run(), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        try {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public static void cancel(Object task) {
        if (task == null) {
            return;
        }
        try {
            task.getClass().getMethod("cancel").invoke(task);
            return;
        } catch (Exception ignored) {
        }
        if (task instanceof BukkitTask bukkitTask) {
            bukkitTask.cancel();
        }
    }
}
