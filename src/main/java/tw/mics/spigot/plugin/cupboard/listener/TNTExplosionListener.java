package tw.mics.spigot.plugin.cupboard.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.util.Vector;

import tw.mics.spigot.plugin.cupboard.Cupboard;
import tw.mics.spigot.plugin.cupboard.config.Config;
import tw.mics.spigot.plugin.cupboard.utils.Util;

public class TNTExplosionListener extends MyListener {
    
    class ChanceTureTo {
        ChanceTureTo(Double c, Material t){
            chance = c;
            turn_to = t;
        }
        public Double chance;
        public Material turn_to;
    }
    
    Map<Material, ChanceTureTo> other_chance_destory_block_list;
    
	public TNTExplosionListener(Cupboard instance)
	{
		super(instance);
		other_chance_destory_block_list = new HashMap<Material, ChanceTureTo>();
	    for(String str : Config.TNT_BREAKCHANCE.getStringList()){
	        String[] strs = str.split(":");
	        Material m = null;
	        if(!strs[2].equals("DROP")){
	            m = Material.valueOf(strs[2]);
	        }
            other_chance_destory_block_list.put(Material.valueOf(strs[0]), 
                    new ChanceTureTo( Double.valueOf(strs[1]), m));
	    }
	}

	//爆炸後（只處理特殊TNT轉換出來的實體，原版TNT走原版爆炸邏輯，不動它）
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplode(EntityExplodeEvent event){
        if(!Util.isSpecialTNTEntity(event.getEntity())) return;
    	event.blockList().clear();
    	Location loc = event.getLocation();
    	Integer r = Config.TNT_EXPLOSION_RADIUS.getInt();
    	//anti-tnt-explosion：保護區附近的特殊TNT不得破壞任何方塊（這個監聽器是 HIGHEST，會重算 blockList，必須在這裡再擋一次）
    	if(Config.ANTI_TNT_EXPLOSION.getBoolean()
    	        && Config.ENABLE_WORLD.getStringList().contains(loc.getWorld().getName())
    	        && !plugin.cupboards.checkExplosionAble(loc, r + 1)){
    	    return;
    	}
        for(int x = -r; x <= r; x++){
            for(int y = -r; y <= r; y++){
                for(int z = -r; z <= r; z++){
            	    Block b = loc.clone().add(x, y, z).getBlock();
            	    if(b.getType() == Material.GOLD_BLOCK) continue; //金磚不能被特殊TNT炸掉
            	    if(b.getType() == Material.TNT){
            	        b.setType(Material.AIR);
            	        Util.setUpTNT(b.getLocation().add(0.5, 0, 0.5));
            	    }
            	    ChanceTureTo ctt = other_chance_destory_block_list.get(b.getType());
            	    if(ctt != null){
            	        if(ctt.chance > new Random().nextFloat()){
            	            if(ctt.turn_to == null){
                                event.blockList().add(b);
            	            } else {
            	                b.setType(ctt.turn_to);
            	            }
            	        }
            	    } else {
            	        event.blockList().add(b);
            	    }
                }
            }
    	}
    }
    
    //爆炸前（同樣只調整特殊TNT的爆炸半徑，原版TNT維持原版半徑）
    @EventHandler
    public void onTNTPrime(ExplosionPrimeEvent e){
        if(!Util.isSpecialTNTEntity(e.getEntity())) return;
        e.setRadius(Config.TNT_EXPLOSION_RADIUS.getInt()+1);
    }
    
    //發射器禁用
    @EventHandler
    public void onDispense(BlockDispenseEvent event){
        if(event.getItem().getType() == Material.TNT){
            event.setCancelled(true);
        }
    }
    
    //禁止使用釣魚竿
    @EventHandler
    public void onFishingTNT(PlayerFishEvent event){
        if(event.getCaught() instanceof TNTPrimed){
            event.getCaught().setVelocity(new Vector());
        }
    }
}
