package tw.mics.spigot.plugin.cupboard;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class CupboardAPI {
    public static boolean checkIsLimit(Location l, Player p){
        return Cupboard.getInstance().cupboards.checkIsLimit(l, p);
    }
    public static boolean checkIsLimit(Location l){
        return Cupboard.getInstance().cupboards.checkIsLimit(l);
    }
    public static boolean checkIsLimit(Block b){
        return Cupboard.getInstance().cupboards.checkIsLimit(b);
    }
}
