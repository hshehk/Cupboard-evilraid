package tw.mics.spigot.plugin.cupboard.listener;


import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import tw.mics.spigot.plugin.cupboard.Cupboard;
import tw.mics.spigot.plugin.cupboard.config.Config;
import tw.mics.spigot.plugin.cupboard.config.Locales;
import tw.mics.spigot.plugin.cupboard.utils.Util;

public class GoldBlockListener extends MyListener {
	public GoldBlockListener(Cupboard instance)
	{
	    super(instance);
	}
	
	//移除金磚
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGoldBlockBreak(BlockBreakEvent event){
    	if(event.isCancelled())return;
    	List<String> enable_world = Config.ENABLE_WORLD.getStringList();
        if(!enable_world.contains(event.getBlock().getWorld().getName()))return;
    	if(event.getBlock().getType() == Material.GOLD_BLOCK){
    		Player p = event.getPlayer();
    		if(plugin.cupboards.removeCupboard(event.getBlock(), event.getPlayer())){
    		    Util.msgToPlayer(p, Locales.GOLD_REMOVE.getString());
    		}
    	}
    }
    
    //放置金磚
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGoldBlockPlace(BlockPlaceEvent event){
    	if(event.isCancelled())return;
    	List<String> enable_world = Config.ENABLE_WORLD.getStringList();
        if(!enable_world.contains(event.getBlock().getWorld().getName()))return;
    	if(event.getBlockPlaced().getType() == Material.GOLD_BLOCK){
    		Player p = event.getPlayer();
    		if(!plugin.cupboards.putCupboard(event.getBlockPlaced(), p)){
    			Util.msgToPlayer(p,Locales.GOLD_TOO_CLOSE.getString());
    			event.setCancelled(true);
    			return;
    		}
    		Util.msgToPlayer(p, Locales.GOLD_PLACE.getString());
    	}
    }
    
    //右鍵金磚 授權/取消授權
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent event){
        if (event.isCancelled()) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;                    		// off hand packet, ignore.
    	if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.GOLD_BLOCK) return;       	// 非黃金磚則無視
    	if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return; 						// 非右鍵方塊則無視
        Player p = event.getPlayer();
    	if (p.isSneaking()) return;                                    //蹲下放方塊無視
        List<String> enable_world = Config.ENABLE_WORLD.getStringList();
        if(!enable_world.contains(event.getClickedBlock().getWorld().getName()))return;
    	Location front_block_loc = event.getClickedBlock().getLocation().clone();
    	switch(event.getBlockFace()){
    	case EAST:
    		front_block_loc.add(1,0,0);
    		break;
    	case WEST:
    		front_block_loc.add(-1,0,0);
    		break;
    	case SOUTH:
    		front_block_loc.add(0,0,1);
    		break;
    	case NORTH:
    		front_block_loc.add(0,0,-1);
    		break;
    	case UP:
    		front_block_loc.add(0,1,0);
    		break;
    	case DOWN:
    		front_block_loc.add(0,-1,0);
    		break;
		default:
			break;
    	}
    	if(front_block_loc.getBlock().getType().isSolid()){
    		p.sendMessage(Locales.GOLD_ACCESS_BLOCKED.getString());
    		return;
    	}
    	
		String str;
        Boolean flag = plugin.cupboards.toggleBoardAccess(p, event.getClickedBlock());
		if(flag == null){
			str=Locales.GOLD_DATA_NOT_FOUND.getString();
		} else if(flag){
			str=Locales.GOLD_GRANT_ACCESS.getString();
		} else {
			str=Locales.GOLD_REVOKE_ACCESS.getString();
		}
		event.setCancelled(true);
		p.updateInventory();
		Util.msgToPlayer(p, str);
    }
}
