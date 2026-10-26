package tw.mics.spigot.plugin.cupboard.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Small compatibility layer for Bukkit / Paper / Purpur / Folia scheduling.
 *
 * The implementation prefers Folia's schedulers when they exist, and falls back
 * to the classic Bukkit scheduler otherwise.
 *
 * IMPORTANT (Folia threading model):
 * - {@link #runGlobal} / {@link #runGlobalLater} run on the GlobalRegionScheduler.
 *   This MUST NOT touch any world/entity/location state (blocks, players, locations...).
 *   Only use it for truly global work (e.g. plugin-wide bookkeeping, file IO callbacks
 *   that don't touch the game state).
 * - {@link #runForEntity} runs on the given entity's own region via its EntityScheduler.
 *   Use this whenever a task needs to read/touch a specific player or entity
 *   (sendMessage, inventory, location, etc).
 * - {@link #runForLocation} runs on whichever region owns the given location.
 *   Use this whenever a task needs to read/touch blocks at a specific location.
 * - {@link #runAsync} is truly asynchronous (off any region thread) and is safe
 *   everywhere, but like before, must not touch world/entity state directly.
 *
 * On non-Folia servers all of the above simply run synchronously on the main thread
 * (or asynchronously for runAsync), so this class is safe to use unconditionally.
 */
public final class SchedulerCompat {
    private SchedulerCompat() {
    }

    public interface TaskHandle {
        void cancel();
    }

    private static final TaskHandle NOOP_HANDLE = () -> { };

    private static final class BukkitTaskHandle implements TaskHandle {
        private final BukkitTask task;

        private BukkitTaskHandle(BukkitTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            task.cancel();
        }
    }

    private static final class ReflectionTaskHandle implements TaskHandle {
        private final Object handle;
        private final Method cancelMethod;

        private ReflectionTaskHandle(Object handle) throws NoSuchMethodException {
            this.handle = handle;
            this.cancelMethod = handle.getClass().getMethod("cancel");
        }

        @Override
        public void cancel() {
            try {
                cancelMethod.invoke(handle);
            } catch (IllegalAccessException | InvocationTargetException e) {
                // Best-effort cancel only.
            }
        }
    }

    public static boolean isFolia() {
        try {
            Bukkit.class.getMethod("getGlobalRegionScheduler");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------
    // Global region scheduler (Folia) — DO NOT touch world/entity state in these!
    // ---------------------------------------------------------------

    /** @deprecated kept only for truly global, state-free work. Prefer {@link #runForEntity} or {@link #runForLocation}. */
    @Deprecated
    public static TaskHandle runGlobalLater(Plugin plugin, Runnable runnable, long delayTicks) {
        if (isFolia()) {
            Object scheduler = getGlobalRegionScheduler();
            Object handle = invokeGlobalScheduler(scheduler, "runDelayed", plugin, runnable, delayTicks);
            if (handle != null) {
                return toHandle(handle);
            }
        }
        return new BukkitTaskHandle(Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks));
    }

    /** @deprecated kept only for truly global, state-free work. Prefer {@link #runForEntity} or {@link #runForLocation}. */
    @Deprecated
    public static TaskHandle runGlobal(Plugin plugin, Runnable runnable) {
        if (isFolia()) {
            Object scheduler = getGlobalRegionScheduler();
            Object handle = invokeGlobalScheduler(scheduler, "runNow", plugin, runnable);
            if (handle != null) {
                return toHandle(handle);
            }
        }
        return new BukkitTaskHandle(Bukkit.getScheduler().runTask(plugin, runnable));
    }

    // ---------------------------------------------------------------
    // Async scheduler — safe everywhere, but never touch world/entity state directly
    // ---------------------------------------------------------------

    public static TaskHandle runAsync(Plugin plugin, Runnable runnable) {
        if (isFolia()) {
            Object scheduler = getAsyncScheduler();
            Object handle = invokeGlobalScheduler(scheduler, "runNow", plugin, runnable);
            if (handle != null) {
                return toHandle(handle);
            }
        }
        return new BukkitTaskHandle(Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    // ---------------------------------------------------------------
    // Per-entity scheduler — use for anything touching a specific player/entity
    // ---------------------------------------------------------------

    public static TaskHandle runForEntity(Plugin plugin, Entity entity, Runnable runnable) {
        return runForEntity(plugin, entity, runnable, 0L);
    }

    public static TaskHandle runForEntity(Plugin plugin, Entity entity, Runnable runnable, long delayTicks) {
        Object entityScheduler = invokeNoArg(entity, "getScheduler");
        if (entityScheduler != null) {
            Object handle = invokeEntityScheduler(entityScheduler, plugin, runnable, delayTicks);
            if (handle != null) {
                return toHandle(handle);
            }
            // Entity scheduler exists but the task couldn't be scheduled
            // (e.g. the entity is already retired/removed) — nothing to run or cancel.
            return NOOP_HANDLE;
        }
        // Not Folia: entity.getScheduler() doesn't exist, everything happens on the main thread anyway.
        if (delayTicks > 0) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks));
        }
        return new BukkitTaskHandle(Bukkit.getScheduler().runTask(plugin, runnable));
    }

    // ---------------------------------------------------------------
    // Per-location (region) scheduler — use for anything touching blocks at a location
    // ---------------------------------------------------------------

    public static TaskHandle runForLocation(Plugin plugin, Location location, Runnable runnable) {
        return runForLocation(plugin, location, runnable, 0L);
    }

    public static TaskHandle runForLocation(Plugin plugin, Location location, Runnable runnable, long delayTicks) {
        if (isFolia()) {
            Object scheduler = getRegionScheduler();
            Object handle = invokeRegionScheduler(scheduler, plugin, location, runnable, delayTicks);
            if (handle != null) {
                return toHandle(handle);
            }
        }
        if (delayTicks > 0) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks));
        }
        return new BukkitTaskHandle(Bukkit.getScheduler().runTask(plugin, runnable));
    }

    // ---------------------------------------------------------------
    // reflection helpers
    // ---------------------------------------------------------------

    private static Object getGlobalRegionScheduler() {
        try {
            Method method = Bukkit.class.getMethod("getGlobalRegionScheduler");
            return method.invoke(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object getAsyncScheduler() {
        try {
            Method method = Bukkit.class.getMethod("getAsyncScheduler");
            return method.invoke(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object getRegionScheduler() {
        try {
            Method method = Bukkit.class.getMethod("getRegionScheduler");
            return method.invoke(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    // GlobalRegionScheduler / AsyncScheduler: run(Plugin, Consumer) / runDelayed(Plugin, Consumer, long) / runNow(Plugin, Consumer)
    private static Object invokeGlobalScheduler(Object scheduler, String methodName, Plugin plugin, Runnable runnable, long... args) {
        if (scheduler == null) {
            return null;
        }
        try {
            Consumer<Object> consumer = ignored -> runnable.run();
            if (args.length == 0) {
                Method method = scheduler.getClass().getMethod(methodName, Plugin.class, Consumer.class);
                return method.invoke(scheduler, plugin, consumer);
            }
            Method method = scheduler.getClass().getMethod(methodName, Plugin.class, Consumer.class, long.class);
            return method.invoke(scheduler, plugin, consumer, args[0]);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    // EntityScheduler: run(Plugin, Consumer, Runnable retired) / runDelayed(Plugin, Consumer, Runnable retired, long)
    private static Object invokeEntityScheduler(Object scheduler, Plugin plugin, Runnable runnable, long delayTicks) {
        if (scheduler == null) {
            return null;
        }
        try {
            Consumer<Object> consumer = ignored -> runnable.run();
            Runnable retired = () -> { }; // entity removed/invalid before the task ran — best effort no-op
            if (delayTicks <= 0) {
                Method method = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
                return method.invoke(scheduler, plugin, consumer, retired);
            }
            Method method = scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
            return method.invoke(scheduler, plugin, consumer, retired, delayTicks);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    // RegionScheduler: run(Plugin, Location, Consumer) / runDelayed(Plugin, Location, Consumer, long)
    private static Object invokeRegionScheduler(Object scheduler, Plugin plugin, Location location, Runnable runnable, long delayTicks) {
        if (scheduler == null) {
            return null;
        }
        try {
            Consumer<Object> consumer = ignored -> runnable.run();
            if (delayTicks <= 0) {
                Method method = scheduler.getClass().getMethod("run", Plugin.class, Location.class, Consumer.class);
                return method.invoke(scheduler, plugin, location, consumer);
            }
            Method method = scheduler.getClass().getMethod("runDelayed", Plugin.class, Location.class, Consumer.class, long.class);
            return method.invoke(scheduler, plugin, location, consumer, delayTicks);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static TaskHandle toHandle(Object handle) {
        try {
            return new ReflectionTaskHandle(handle);
        } catch (NoSuchMethodException e) {
            return NOOP_HANDLE;
        }
    }
}
