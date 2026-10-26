package tw.mics.spigot.plugin.cupboard.listener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.entity.ItemSpawnEvent;

import tw.mics.spigot.plugin.cupboard.Cupboard;
import tw.mics.spigot.plugin.cupboard.config.Config;

/**
 * 防止「取消多方塊放置」造成的物品複製。
 *
 * 問題：BlockMultiPlaceEvent（床、門、高花、末地傳送門框等多方塊結構）被取消時，
 * 伺服器是把 replacedBlockStates 一個個還原。還原過程中若原位置有容器被「覆蓋/破壞」，
 * 它的內容物會先以掉落物生成，方塊狀態卻又被回滾回去 —— 每取消一次就多出一份物品。
 * 這不是單一保護插件的問題，只要有插件只做 setCancelled(true) 就會踩到。
 *
 * 作法：在 MONITOR 階段看到「最終仍被取消」的多方塊放置，就把受影響的方塊座標登記一個極短的時間窗，
 * 時間窗內這些座標上生成的掉落物（ItemSpawnEvent）一律取消。
 *
 * 取捨：時間窗內剛好有玩家在這些座標丟東西也會被吃掉，所以窗口很短（預設 250ms，可在 config 調整）。
 * Folia 下事件各 region 同時跑，因此用 ConcurrentHashMap 與 nanoTime，不依賴全域 tick。
 */
public class MultiPlaceRollbackGuard extends MyListener {
    private final Map<String, Long> pending = new ConcurrentHashMap<String, Long>();
    private final long windowNanos;

    public MultiPlaceRollbackGuard(Cupboard instance) {
        super(instance);
        long ms = Math.max(50, Math.min(2000, Config.ANTI_DUPE_WINDOW_MS.getInt()));
        this.windowNanos = ms * 1_000_000L;
    }

    private static String key(World w, int x, int y, int z) {
        return w.getUID() + ":" + x + "," + y + "," + z;
    }

    //最後才看結果：其他插件在更早的階段取消的事件也要納入
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMultiPlace(BlockMultiPlaceEvent event) {
        if (!event.isCancelled()) return;

        long now = System.nanoTime();
        long expire = now + windowNanos;

        //順手清掉過期項目，避免 map 無限增長
        pending.values().removeIf(t -> t < now);

        World world = event.getBlock().getWorld();
        pending.put(key(world, event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ()), expire);
        for (BlockState state : event.getReplacedBlockStates()) {
            pending.put(key(state.getWorld(), state.getX(), state.getY(), state.getZ()), expire);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (pending.isEmpty()) return;
        Location l = event.getLocation();
        World w = l.getWorld();
        if (w == null) return;

        Long expire = pending.get(key(w, l.getBlockX(), l.getBlockY(), l.getBlockZ()));
        if (expire == null) return;
        if (expire < System.nanoTime()) {
            pending.remove(key(w, l.getBlockX(), l.getBlockY(), l.getBlockZ()));
            return;
        }

        event.setCancelled(true);
        plugin.logDebug("Blocked rollback drop at %s,%d,%d,%d (cancelled multi-place)",
                w.getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }
}
