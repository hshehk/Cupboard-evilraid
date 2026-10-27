package tw.mics.spigot.plugin.cupboard.listener;

import java.util.HashSet;
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
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemSpawnEvent;

import tw.mics.spigot.plugin.cupboard.Cupboard;
import tw.mics.spigot.plugin.cupboard.config.Config;

/**
 * 防止「取消方塊放置」造成的物品複製（包含 BlockMultiPlaceEvent 的多方塊結構）。
 *
 * 原版放置流程是「先真的把方塊放下去，再呼叫 BlockPlaceEvent，被取消才用快照還原」。
 * 放下去的當下，被取代位置上的容器會先把內容物以掉落物噴出來（ItemSpawnEvent 在事件之前就發生了），
 * 取消後快照還原又把容器與內容物放回去 —— 每取消一次就多出一份物品。
 * 只做 setCancelled(true) 的保護插件都會踩到（Residence、Dominion 皆可重現），不是單一插件的問題。
 *
 * 所以只在事件之後才處理掉落物是來不及的，這裡分兩段：
 *  1. 持續記錄近期生成的掉落物（只留一小段時間、一個上限數量）。
 *  2. 放置事件「最終仍被取消」（MONITOR）時：
 *     a. 回頭把「事件前」已經在受影響座標生成的掉落物移除（這是主要的複製來源）；
 *     b. 並在極短時間窗內，取消這些座標上之後才生成的掉落物（還原過程中才噴出來的情況）。
 *
 * 取捨：時間窗內剛好有玩家在這些座標丟東西也會被吃掉，所以窗口很短（預設 250ms，可在 config 調整）。
 * Folia 下各 region 同時處理事件，因此用併發集合與 nanoTime，不依賴全域 tick；
 * 被移除的掉落物與事件座標屬於同一個 region，移除操作在同一執行緒進行。
 */
public class MultiPlaceRollbackGuard extends MyListener {
    private static final int MAX_RECENT = 4096;

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

    //1. 記錄剛生成的掉落物（等放置事件被取消時才知道要不要回頭移除）
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawnRecord(ItemSpawnEvent event) {
        Location l = event.getLocation();
        World w = l.getWorld();
        if (w == null) return;
        long now = System.nanoTime();
        prune(now);
        recent.addLast(new Spawn(key(w, l.getBlockX(), l.getBlockY(), l.getBlockZ()), now, event.getEntity()));
    }

    //2b. 事件取消之後、時間窗內才生成的掉落物直接擋掉
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemSpawnBlock(ItemSpawnEvent event) {
        if (pending.isEmpty()) return;
        Location l = event.getLocation();
        World w = l.getWorld();
        if (w == null) return;
        String k = key(w, l.getBlockX(), l.getBlockY(), l.getBlockZ());
        Long expire = pending.get(k);
        if (expire == null) return;
        if (expire < System.nanoTime()) {
            pending.remove(k);
            return;
        }
        event.setCancelled(true);
        plugin.logDebug("Blocked rollback drop at %s,%d,%d,%d (cancelled place)",
                w.getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    //最後才看結果：其他插件在更早階段取消的事件也要納入（BlockMultiPlaceEvent 繼承 BlockPlaceEvent，同樣會進到這裡）
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlaceCancelled(BlockPlaceEvent event) {
        if (!event.isCancelled()) return;

        Set<String> keys = new HashSet<String>();
        Block placed = event.getBlockPlaced();
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

        long now = System.nanoTime();
        prune(now);

        //2b. 登記時間窗，擋掉還原過程中才生成的掉落物
        long expire = now + windowNanos;
        for (String k : keys) {
            pending.put(k, expire);
        }

        //2a. 回頭移除事件前已經噴出來的掉落物
        int removed = 0;
        for (Spawn s : recent) {
            if (now - s.time > windowNanos) continue;
            if (!keys.contains(s.key)) continue;
            if (s.item.isValid()) {
                s.item.remove();
                removed++;
            }
        }
        if (removed > 0) {
            plugin.logDebug("Removed %d dropped item(s) spawned before cancelled place at %s",
                    removed, keys.iterator().next());
        }
    }
}
