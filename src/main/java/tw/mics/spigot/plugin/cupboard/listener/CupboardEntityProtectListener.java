package tw.mics.spigot.plugin.cupboard.listener;

import java.util.Arrays;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import tw.mics.spigot.plugin.cupboard.Cupboard;
import tw.mics.spigot.plugin.cupboard.config.Config;
import tw.mics.spigot.plugin.cupboard.data.CupboardsData;
import tw.mics.spigot.plugin.cupboard.utils.Util;

public class CupboardEntityProtectListener extends MyListener {
	final static private Material[] protect_vehicle = {
            Material.ARMOR_STAND,
            Material.OAK_BOAT,
            Material.SPRUCE_BOAT,
            Material.BIRCH_BOAT,
            Material.JUNGLE_BOAT,
            Material.ACACIA_BOAT,
            Material.DARK_OAK_BOAT,
            Material.MINECART ,
            Config.EVILESSENCE_MATERIAL.getMaterial(),
            Material.TNT_MINECART,
            Material.HOPPER_MINECART,
            Material.FURNACE_MINECART,
            Material.CHEST_MINECART,
	};
	private CupboardsData data;

	public CupboardEntityProtectListener(Cupboard instance)
	{
		super(instance);
		
	    this.data = this.plugin.cupboards;
	}
	
	
	//保護船隻 / 礦車
	@EventHandler
	public void onVehicleDestroy(VehicleDamageEvent event) {
        if(!Config.ENABLE_WORLD.getStringList().contains(event.getVehicle().getWorld().getName()))return;
        if(event.getAttacker() instanceof Player){
    	    Location bl = event.getVehicle().getLocation().getBlock().getLocation();
            Player p = (Player) event.getAttacker();
            if(data.checkIsLimit(bl, p)){
                if(this.plugin.isOP(p)) return;
                event.setCancelled(true);
                p.updateInventory();
            }
        }
	}
    
    //保護物品展示框
    @EventHandler
    public void onPlayerHitEntity(EntityDamageByEntityEvent event){
        if(!Config.ENABLE_WORLD.getStringList().contains(event.getEntity().getWorld().getName()))return;
        if(event.getEntityType() != EntityType.ITEM_FRAME) return;
        Player damager = Util.getDamager(event.getDamager());
        if(damager != null){
            Location bl = event.getEntity().getLocation().getBlock().getLocation();
            if(data.checkIsLimit(bl, damager)){
                if(this.plugin.isOP(damager)) return;
                event.setCancelled(true);
            }
        }
    }
    
    //防止未授權玩家和物件互動
    @EventHandler
    public void onPlayerInteract(PlayerInteractEntityEvent event){
        if(!Config.ENABLE_WORLD.getStringList().contains(event.getPlayer().getWorld().getName()))return;
        if(event.getRightClicked().getType().isAlive()) return; //不阻止活著的生物
        Location bl = event.getRightClicked().getLocation().getBlock().getLocation();
        Player p = event.getPlayer();
        if(data.checkIsLimit(bl, p)){
            if(this.plugin.isOP(p)) return;
            event.setCancelled(true);
            event.getPlayer().updateInventory();
        }
    }
    
    //防止未授權玩家和物件互動
    @EventHandler
    public void onPlayerInteract(PlayerInteractAtEntityEvent event){
        if(!Config.ENABLE_WORLD.getStringList().contains(event.getPlayer().getWorld().getName()))return;
        if(event.getRightClicked().getType().isAlive()) return; //不阻止活著的生物
        Location bl = event.getRightClicked().getLocation().getBlock().getLocation();
        Player p = event.getPlayer();
        if(data.checkIsLimit(bl, p)){
            if(this.plugin.isOP(p)) return;
            event.setCancelled(true);
            event.getPlayer().updateInventory();
        }
    }


    //防止Hanging類物品被未授權玩家放置
    @EventHandler
    public void onHangingPlace(HangingPlaceEvent event) {
        if(!Config.ENABLE_WORLD.getStringList().contains(event.getPlayer().getWorld().getName()))return;
		Location bl = event.getEntity().getLocation().getBlock().getLocation();
		Player p = event.getPlayer();
		if(data.checkIsLimit(bl, p)){
			if(this.plugin.isOP(p)) return;
			event.setCancelled(true);
			event.getPlayer().updateInventory();
		}
	}
    
    //防止Hanging類物品被未授權玩家移除
    @EventHandler
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if(!Config.ENABLE_WORLD.getStringList().contains(event.getEntity().getWorld().getName()))return;
		Location bl = event.getEntity().getLocation();
    	if (event.getRemover() instanceof Player){
    		Player p = (Player) event.getRemover();
    		if(this.plugin.cupboards.checkIsLimit(bl, p)){
    			if(this.plugin.isOP(p)) return;
    			event.setCancelled(true);
    		}
    	}
	}
    

    //防止船隻/礦車/盔甲架被放置
    @EventHandler
    public void onBoatPlace(PlayerInteractEvent event){
        if(!Config.ENABLE_WORLD.getStringList().contains(event.getPlayer().getWorld().getName()))return;
    	if (
			event.getAction() == Action.RIGHT_CLICK_BLOCK &&
			event.getItem() != null &&
			(
			    Arrays.asList(protect_vehicle).contains(event.getItem().getType()) ||
			    Tag.ITEMS_BOATS.isTagged(event.getItem().getType()) ||
			    Tag.ITEMS_CHEST_BOATS.isTagged(event.getItem().getType())
			)
		){
    	Player p = event.getPlayer();
	    	if(this.plugin.cupboards.checkIsLimit(event.getClickedBlock(), p)){
	    		if(this.plugin.isOP(p))return;
	    		event.setCancelled(true);
	    		event.getPlayer().updateInventory();
	    	}
    	}
    }
    
    //防止盔甲架被移除
    @EventHandler
    public void onArmorStandDamage(EntityDamageByEntityEvent event){
        if(!Config.ENABLE_WORLD.getStringList().contains(event.getDamager().getWorld().getName()))return;
    	if (event.getEntity().getType() != EntityType.ARMOR_STAND) return;
        Player damager = Util.getDamager(event.getDamager());
        if(damager != null){
            Location bl = event.getEntity().getLocation().getBlock().getLocation();
            if(data.checkIsLimit(bl, damager)){
                if(this.plugin.isOP(damager)) return;
                event.setCancelled(true);
            }
        }
    }

    //防止歌來果/中介珍珠傳送
    @EventHandler
    public void playerTeleportEvent(PlayerTeleportEvent event) {
        if(!Config.CUPBOARD_PREVENT_TELEPORT_ENABLE.getBoolean())return;
        if(!Config.ENABLE_WORLD.getStringList().contains(event.getTo().getWorld().getName()))return;
        if(Config.CUPBOARD_PREVENT_TELEPORT_IGNORE_Y.getInt() > event.getTo().getBlockY())return;
        if(
            (
                event.getCause() ==  PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT ||
                event.getCause() ==  PlayerTeleportEvent.TeleportCause.ENDER_PEARL
            ) &&
            data.checkIsLimit(event.getTo().getBlock().getLocation(), event.getPlayer())
        ){
            event.setCancelled(true);
        }
    }

}
