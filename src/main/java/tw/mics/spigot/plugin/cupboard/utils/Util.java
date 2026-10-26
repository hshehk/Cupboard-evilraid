package tw.mics.spigot.plugin.cupboard.utils;

import java.util.StringTokenizer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import tw.mics.spigot.plugin.cupboard.Cupboard;
import tw.mics.spigot.plugin.cupboard.config.Config;

public class Util {
    public static Player getDamager(Entity e){
        Player damager = null;
        if(e instanceof Player){
            damager = (Player) e;
        } else if(e instanceof Arrow){
            Arrow arrow = (Arrow)e;
            if(arrow.getShooter() instanceof Player){
                damager = (Player) arrow.getShooter();
            }
        } else if(e instanceof ThrownPotion){
            ThrownPotion potion = (ThrownPotion)e;
            if(potion.getShooter() instanceof Player){
                damager = (Player) potion.getShooter();
            }
        } else if(e instanceof Wolf){
            Wolf wolf = (Wolf)e;
            if(wolf.getOwner() instanceof Player){
                damager = (Player) wolf.getOwner();
            }
        }
        return damager; //return null if not player
    }
    
	public static void msgToPlayer(Player p, String str){
		p.sendMessage(str);
	}
	
	public static String LocToString(Location l){
		String str = String.format("%s,%d,%d,%d",
				l.getWorld().getName(),
				l.getBlockX(),
				l.getBlockY(),
				l.getBlockZ());
		return str;
	}
	
	public static Location StringToLoc(String str){
	    StringTokenizer st = new StringTokenizer(str, ",");
        World world = Bukkit.getWorld((st.nextToken()));
        int x = Integer.parseInt(st.nextToken());
        int y = Integer.parseInt(st.nextToken());
        int z = Integer.parseInt(st.nextToken());
        Location loc = new Location(world,x,y,z);
        return loc;
	}
	
	public static String replaceColors(String message) {
        return message.replaceAll("&((?i)[0-9a-fk-or])", "§$1");
    }
	
	// 用來標記「特殊TNT」的 PDC key，不管是物品還是實體都共用這把 key
	private static NamespacedKey specialTntKey = null;
	private static NamespacedKey getSpecialTNTKey(){
	    if(specialTntKey == null){
	        specialTntKey = new NamespacedKey(Cupboard.getInstance(), "special_tnt");
	    }
	    return specialTntKey;
	}
	
	// 把物品標記為「特殊TNT」（給 TNTCraftListener 合成出來的成品用）
	public static void markSpecialTNTItem(ItemStack item){
	    if(item == null) return;
	    ItemMeta meta = item.getItemMeta();
	    if(meta == null) return;
	    meta.getPersistentDataContainer().set(getSpecialTNTKey(), PersistentDataType.BYTE, (byte)1);
	    item.setItemMeta(meta);
	}
	
	// 判斷一個物品是不是「特殊TNT」
	public static boolean isSpecialTNTItem(ItemStack item){
	    if(item == null) return false;
	    ItemMeta meta = item.getItemMeta();
	    if(meta == null) return false;
	    return meta.getPersistentDataContainer().has(getSpecialTNTKey(), PersistentDataType.BYTE);
	}
	
	// 判斷一個已經引爆的 TNTPrimed 實體是不是「特殊TNT」轉換出來的
	public static boolean isSpecialTNTEntity(Entity e){
	    if(!(e instanceof TNTPrimed)) return false;
	    return ((TNTPrimed) e).getPersistentDataContainer().has(getSpecialTNTKey(), PersistentDataType.BYTE);
	}
	
	// 從背包扣除指定數量的物品，可堆疊物品會照數量一個一個扣，不會整組(整個slot)直接被清空
	public static void removeItemAmount(Inventory inv, Material material, int amount){
	    int remaining = amount;
	    while(remaining > 0){
	        int slot = inv.first(material);
	        if(slot < 0) break; // 東西不夠扣了（正常情況下呼叫前應該已經檢查過數量足夠）
	        ItemStack stack = inv.getItem(slot);
	        if(stack == null) break;
	        int take = Math.min(stack.getAmount(), remaining);
	        if(stack.getAmount() <= take){
	            inv.setItem(slot, null);
	        } else {
	            stack.setAmount(stack.getAmount() - take);
	        }
	        remaining -= take;
	    }
	}
	
	// 把背包裡符合條件的物品盡量疊在一起（用來繞過Geyser/Bedrock合成介面翻譯限制導致的物品散格問題）
	public static void stackSimilarItems(Inventory inv, java.util.function.Predicate<ItemStack> matcher, int maxStackSize){
	    ItemStack[] contents = inv.getStorageContents();
	    for(int i = 0; i < contents.length; i++){
	        ItemStack a = contents[i];
	        if(a == null || !matcher.test(a)) continue;
	        if(a.getAmount() >= maxStackSize) continue;
	        boolean changed = false;
	        for(int j = i+1; j < contents.length; j++){
	            ItemStack b = contents[j];
	            if(b == null || !matcher.test(b)) continue;
	            int space = maxStackSize - a.getAmount();
	            if(space <= 0) break;
	            int move = Math.min(space, b.getAmount());
	            a.setAmount(a.getAmount() + move);
	            b.setAmount(b.getAmount() - move);
	            changed = true;
	            if(b.getAmount() <= 0){
	                inv.setItem(j, null);
	            } else {
	                inv.setItem(j, b);
	            }
	            if(a.getAmount() >= maxStackSize) break;
	        }
	        if(changed){
	            inv.setItem(i, a);
	        }
	    }
	}
	
	public static void setUpTNT(Location l){
        TNTPrimed tnt = l.getWorld().spawn(l, TNTPrimed.class);
        tnt.setGravity(false);
        tnt.setGlowing(true);
        tnt.setVelocity(new Vector(0, 0, 0));
        tnt.setFuseTicks(Config.TNT_FUSETICK.getInt());
        // 標記這顆是特殊TNT轉換出來的，讓 TNTExplosionListener 只對這種TNT套用自訂爆炸邏輯
        tnt.getPersistentDataContainer().set(getSpecialTNTKey(), PersistentDataType.BYTE, (byte)1);
    }
}

