package tw.mics.spigot.plugin.cupboard.listener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import tw.mics.spigot.plugin.cupboard.Cupboard;
import tw.mics.spigot.plugin.cupboard.config.Config;

/**
 * 防止「取消方塊放置」造成的物品複製（包含 BlockMultiPlaceEvent 的多方塊結構）。
 *
 * 原版放置流程是「先真的把方塊放下去，再呼叫 BlockPlaceEvent，被取消才用快照還原」。
 * 放下去的當下，被取代的方塊、被連帶破壞的鄰近方塊（容器、需要支撐的方塊等）會先把內容物噴成掉落物，
 * 這些掉落物在事件之前就生成了；取消後快照還原又把方塊與內容物放回去 —— 每取消一次就多出一份。
 * 掉落物的座標不一定落在放置/被取代的方塊上（鄰近方塊被連帶破壞時就不是），
 * 而且漏斗不受撿拾延遲限制，只要掉落物還在，漏斗馬上就能吸走，所以不能只靠座標比對。
 *
 * 作法（依「同一執行緒、同一個呼叫堆疊」來關聯，不依賴座標）：
 *  1. PlayerInteractEvent（右鍵方塊，最早的階段）開始記錄這個執行緒上之後生成的掉落物。
 *     玩家放置從互動到 BlockPlaceEvent 是同一個同步呼叫堆疊，中間生成的掉落物都是這次放置的副作用。
 *  2. BlockPlaceEvent 在 MONITOR 階段仍被取消時，立刻把記錄到的掉落物全部移除（此時還沒有任何 tick，漏斗拿不到）；
 *     接著同一堆疊內快照還原時才生成的掉落物，在放置點附近一律取消。
 *  3. 非玩家互動觸發的放置事件（其他插件手動呼叫等）沒有第 1 步的記錄，改用座標比對兜底。
 *
 * Folia 下事件在各 region 執行緒處理，ThreadLocal 與座標所屬 region 一致，移除掉落物是安全的。
 * 取捨：互動與放置之間，其他插件於同一堆疊內刻意生成的掉落物（例如右鍵商店噴物品）若同時遇到放置被取消，也會被一併移除，這種情況很罕見。
 */
public class MultiPlaceRollbackGuard extends MyListener {
    private static final int MAX_RECENT = 4096;
    private static final int MAX_TRACE_ITEMS = 256;
    private static final double SUPPRESS_RADIUS_SQ = 6.0 * 6.0;
    //同一個呼叫堆疊通常只要毫秒級；這裡放寬是為了避免剛好遇到卡頓（GC 等）時關聯失效
    private static final long STACK_WINDOW_NANOS = 200_000_000L;

    private static final class Spawn {
        final String key;
        final long time;
        final Item item;
        Spawn(String key, long time, Item item) {
            this.key = key;
            this.time = time;
            this.item = item;
        }
    }

    private static final class Trace {
        final long start;
        final List<Item> spawned = new ArrayList<Item>();
        boolean cancelled = false;
        long suppressUntil = 0L;
        World world;
        double x, y, z;
        Trace(long start) {
            this.start = start;
        }
    }

    private final ThreadLocal<Trace> trace = new ThreadLocal<Trace>();
    private final Map<String, Long> pending = new ConcurrentHashMap<String, Long>();
    private final ConcurrentLinkedDeque<Spawn> recent = new ConcurrentLinkedDeque<Spawn>();
    private final long windowNanos;

    public MultiPlaceRollbackGuard(Cupboard instance) {
        super(instance);
        long ms = Math.max(50, Math.min(2000, Config.ANTI_DUPE_WINDOW_MS.getInt()));
        this.windowNanos = ms * 1_000_000L;
    }

    private static String key(World w, int x, int y, int z) {
        return w.getUID() + ":" + x + "," + y + "," + z;
    }

    private void prune(long now) {
        Spawn s;
        while ((s = recent.peekFirst()) != null && (now - s.time > windowNanos || recent.size() > MAX_RECENT)) {
            recent.pollFirst();
        }
        pending.values().removeIf(t -> t < now);
    }

    //1. 最早的階段開始記錄（LOWEST：在任何其他插件處理之前，避免漏記它們造成的掉落物）
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractStart(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null) return;
        trace.set(new Trace(System.nanoTime()));
    }

    //記錄剛生成的掉落物
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawnRecord(ItemSpawnEvent event) {
        Location l = event.getLocation();
        World w = l.getWorld();
        if (w == null) return;
        long now = System.nanoTime();

        Trace t = trace.get();
        if (t != null && !t.cancelled && now - t.start <= STACK_WINDOW_NANOS && t.spawned.size() < MAX_TRACE_ITEMS) {
            t.spawned.add(event.getEntity());
        }

        prune(now);
        recent.addLast(new Spawn(key(w, l.getBlockX(), l.getBlockY(), l.getBlockZ()), now, event.getEntity()));
    }

    //放置被取消之後才生成的掉落物（快照還原過程）直接擋掉
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemSpawnBlock(ItemSpawnEvent event) {
        Location l = event.getLocation();
        World w = l.getWorld();
        if (w == null) return;
        long now = System.nanoTime();

        Trace t = trace.get();
        if (t != null && t.cancelled && now < t.suppressUntil && w.equals(t.world)) {
            double dx = l.getX() - t.x, dy = l.getY() - t.y, dz = l.getZ() - t.z;
            if (dx * dx + dy * dy + dz * dz <= SUPPRESS_RADIUS_SQ) {
                event.setCancelled(true);
                plugin.logDebug("Blocked rollback drop near %s,%d,%d,%d (same-stack suppress)",
                        w.getName(), (int) t.x, (int) t.y, (int) t.z);
                return;
            }
        }

        if (pending.isEmpty()) return;
        String k = key(w, l.getBlockX(), l.getBlockY(), l.getBlockZ());
        Long expire = pending.get(k);
        if (expire == null) return;
        if (expire < now) {
            pending.remove(k);
            return;
        }
        event.setCancelled(true);
        plugin.logDebug("Blocked rollback drop at %s,%d,%d,%d (cancelled place)",
                w.getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    //2. 最後才看結果：其他插件在更早階段取消的事件也要納入（BlockMultiPlaceEvent 繼承 BlockPlaceEvent，同樣會進到這裡）
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlaceCancelled(BlockPlaceEvent event) {
        if (!event.isCancelled()) return;

        long now = System.nanoTime();
        Block placed = event.getBlockPlaced();

        //2a. 同一堆疊內、互動之後到現在生成的掉落物，全部是這次放置的副作用，直接移除
        int stackRemoved = 0;
        Trace t = trace.get();
        if (t != null && !t.cancelled && now - t.start <= STACK_WINDOW_NANOS) {
            for (Item item : t.spawned) {
                if (item.isValid()) {
                    item.remove();
                    stackRemoved++;
                }
            }
            t.spawned.clear();
            t.cancelled = true;
            t.suppressUntil = now + STACK_WINDOW_NANOS;
            t.world = placed.getWorld();
            t.x = placed.getX() + 0.5;
            t.y = placed.getY() + 0.5;
            t.z = placed.getZ() + 0.5;
        }

        //3. 座標比對兜底（沒有互動記錄的放置事件，或掉落物剛好在受影響方塊上）
        Set<String> keys = new HashSet<String>();
        keys.add(key(placed.getWorld(), placed.getX(), placed.getY(), placed.getZ()));
        BlockState replaced = event.getBlockReplacedState();
        if (replaced != null) {
            keys.add(key(replaced.getWorld(), replaced.getX(), replaced.getY(), replaced.getZ()));
        }
        if (event instanceof BlockMultiPlaceEvent) {
            for (BlockState state : ((BlockMultiPlaceEvent) event).getReplacedBlockStates()) {
                keys.add(key(state.getWorld(), state.getX(), state.getY(), state.getZ()));
            }
        }

        prune(now);
        long expire = now + windowNanos;
        for (String k : keys) {
            pending.put(k, expire);
        }

        int matched = 0;
        int coordRemoved = 0;
        for (Spawn s : recent) {
            if (now - s.time > windowNanos) continue;
            if (!keys.contains(s.key)) continue;
            matched++;
            if (s.item.isValid()) {
                s.item.remove();
                coordRemoved++;
            }
        }
        plugin.logDebug("Place cancelled at %s (%d affected block(s), trace=%s): %d removed by stack trace, %d coord match / %d removed by coord",
                keys.iterator().next(), keys.size(), t != null, stackRemoved, matched, coordRemoved);
    }
}
