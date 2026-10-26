package tw.mics.spigot.plugin.cupboard.listener;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import tw.mics.spigot.plugin.cupboard.Cupboard;
import tw.mics.spigot.plugin.cupboard.config.Config;
import tw.mics.spigot.plugin.cupboard.config.Locales;
import tw.mics.spigot.plugin.cupboard.data.CupboardsData;
import tw.mics.spigot.plugin.cupboard.utils.Util;

public class CupboardBlockProtectListener extends MyListener {
	private CupboardsData data;
    
	public CupboardBlockProtectListener(Cupboard instance)
	{
	    super(instance);
	    this.data = this.plugin.cupboards;
	}
    
    private static boolean isDoor(Material m){
        return Tag.DOORS.isTagged(m);
    }

    private static boolean isSign(Material m){
        return Tag.ALL_SIGNS.isTagged(m) || Tag.ALL_HANGING_SIGNS.isTagged(m);
    }

    //防止其他玩家破壞方塊
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent e){
        Player p = e.getPlayer();
        Block b = e.getBlock();
        if(!Config.ENABLE_WORLD.getStringList().contains(b.getWorld().getName()))return;
        if( p != null){
            if(
                    (
                        b.getType() == Material.GOLD_BLOCK &&
                        !data.checkDirectAccess(b, p)
                    ) || (
                        b.getType() != Material.GOLD_BLOCK &&
                        data.checkIsLimit(b, p)
                    )
            ){
                if(this.plugin.isOP(p))return;
                e.setCancelled(true);
                p.sendMessage(Locales.NO_ACCESS.getString());
                //DOOR
                if(isDoor(b.getType())){
                    Location resend_location = b.getLocation().add(0, 1, 0);
                    p.sendBlockChange(resend_location, resend_location.getBlock().getBlockData());
                }
                
                //SIGN
                if(isSign(b.getType())){
                    Location resend_location = b.getLocation();
                    p.sendBlockChange(resend_location, resend_location.getBlock().getBlockData());
                }
            }
        } else {
            if(data.checkIsLimit(b)){
                e.setCancelled(true);
            }
        }
    }
    
    //防止其他玩家放置方塊
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent e){
        Player p = e.getPlayer();
        Block b = e.getBlock();
        if(!Config.ENABLE_WORLD.getStringList().contains(b.getWorld().getName()))return;
        if( p != null){
            ItemStack itemInHand = null;
            switch(e.getHand() == null ? EquipmentSlot.HAND : e.getHand()){
            case OFF_HAND:
                itemInHand = p.getInventory().getItemInOffHand();
                break;
            default:
                itemInHand = p.getInventory().getItemInMainHand();
                break;
            }
            if(Config.TNT_SP_ENABLE.getBoolean() && e.getBlockPlaced().getType().equals(Material.TNT) && Util.isSpecialTNTItem(itemInHand)){
                //先在方塊上做記號，因為消耗掉最後一個手持物品後，MONITOR階段再讀手上的物品會讀到空氣，判斷不出來
                e.getBlockPlaced().setMetadata("cupboard_special_tnt", new FixedMetadataValue(this.plugin, true));
                
                //確定邪惡精華足夠（創造模式不用消耗資源，直接跳過扣除，但轉換行為照樣生效）
                if(p.getGameMode() == GameMode.SURVIVAL){
                    Integer evilessence_cost = null;
                    if(Config.EVILESSENCE_ENABLE.getBoolean()){
                        if(e.getBlock().getLocation().getBlockY() > Config.EVILESSENCE_TNT_COST_BOUNS_Y.getInt()){
                            evilessence_cost = Config.EVILESSENCE_TNT_COST_BOUNS_AMOUNT.getInt();
                        } else {
                            evilessence_cost = Config.EVILESSENCE_TNT_COST.getInt();
                        }
                        if(!p.getInventory().contains(Config.EVILESSENCE_MATERIAL.getMaterial(), evilessence_cost)){
                            p.sendMessage(Locales.TNT_EVILESSENCE_NOT_ENOUGH.getString());
                            e.setCancelled(true);
                            return;
                        }
                    }
                    //確定 TNT 數量足夠（固定消耗 tnt.cost 個，不分高度）
                    ItemStack tnt;
                    if(e.getHand() == null) return;
                    switch(e.getHand()){
                    default:
                    case HAND:
                        tnt = p.getInventory().getItemInMainHand();
                        break;
                    case OFF_HAND:
                        tnt = p.getInventory().getItemInOffHand();
                        break;
                    }
                    if(tnt.getAmount() >= Config.TNT_COST.getInt() ){
                        tnt.setAmount(tnt.getAmount() - Config.TNT_COST.getInt());
                    } else {
                        p.sendMessage(Locales.TNT_NOT_ENOUGH.getString());
                        e.setCancelled(true);
                        return;
                    }
                    
                    Inventory inv = p.getInventory();
                    if(evilessence_cost != null){
                        Util.removeItemAmount(inv, Config.EVILESSENCE_MATERIAL.getMaterial(), evilessence_cost);
                    }
                }
                
                return;
            } else if(data.checkIsLimit(b, p)){
                if(this.plugin.isOP(p))return;
                p.sendMessage(Locales.NO_ACCESS.getString());
                e.setCancelled(true);
            }
        }
    }
    
    //TNT放置 (特殊TNT才會立即轉換成自訂爆炸；原版TNT保留成普通方塊)
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTNTPlace(BlockPlaceEvent e){
        Block block = e.getBlockPlaced();
        //事件被取消（材料不足、被其他插件擋下等）時，必須清掉 HIGH 階段留下的記號，
        //否則同一格之後放一般 TNT 會被誤當成特殊 TNT（免費轉換）
        if(e.isCancelled()){
            block.removeMetadata("cupboard_special_tnt", this.plugin);
            return;
        }
        if(!Config.TNT_SP_ENABLE.getBoolean()) return;
        if(!block.getType().equals(Material.TNT)) return;
        
        //讀取onBlockPlace階段留下的記號，而不是重新去讀手上的物品
        //（如果手上剛好只剩最後一個，這時候已經被消耗成空氣了，讀不到）
        if(!block.hasMetadata("cupboard_special_tnt")) return; //原版TNT，維持普通方塊，不轉換
        block.removeMetadata("cupboard_special_tnt", this.plugin);
        
        block.setType(Material.AIR);
        Location loc = block.getLocation();
        loc.add(0.5,0,0.5);
        Util.setUpTNT(loc);
    }
    
    //原版TNT點燃保護：沒有金磚領地權限的人無法用打火石/火焰彈點燃領地內的TNT
    @EventHandler(priority = EventPriority.HIGH)
    public void onTNTIgnite(PlayerInteractEvent event){
        if(event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = event.getClickedBlock();
        if(b == null || b.getType() != Material.TNT) return;
        Player p = event.getPlayer();
        if(!Config.ENABLE_WORLD.getStringList().contains(b.getWorld().getName())) return;
        ItemStack item = event.getItem();
        if(item == null) return;
        if(item.getType() != Material.FLINT_AND_STEEL && item.getType() != Material.FIRE_CHARGE) return;
        
        if(data.checkIsLimit(b, p)){
            if(this.plugin.isOP(p)) return;
            p.sendMessage(Locales.NO_ACCESS.getString());
            event.setCancelled(true);
        }
    }
    
   //防止玩家使用水桶
    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent e){
        Player p = e.getPlayer();
        if(!Config.ENABLE_WORLD.getStringList().contains(p.getWorld().getName()))return;
        Block b = e.getBlockClicked().getLocation()
                .add(e.getBlockFace().getModX(),e.getBlockFace().getModY(),e.getBlockFace().getModZ())
                .getBlock();
        if( p != null){
            if(data.checkIsLimit(b, p)){
                e.setCancelled(true);
                p.updateInventory();
            }       
        }
    }
    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent e){
        Player p = e.getPlayer();
        if(!Config.ENABLE_WORLD.getStringList().contains(p.getWorld().getName()))return;
        Block b = e.getBlockClicked();
        if( p != null){
            if(data.checkIsLimit(b, p)){
                e.setCancelled(true);
                p.updateInventory();
            }
        }
    }
    
    //需要保護的互動方塊（用 Tag 涵蓋所有木材種類，新版本新增的木種不必再手動補）
    private static boolean isLimitInteractBlock(Material m){
        if(Tag.DOORS.isTagged(m)
                || Tag.TRAPDOORS.isTagged(m)
                || Tag.FENCE_GATES.isTagged(m)
                || Tag.BUTTONS.isTagged(m)
                || Tag.BEDS.isTagged(m)
                || Tag.ANVIL.isTagged(m)) return true;
        switch(m){
        case LEVER:
        case REPEATER:
        case COMPARATOR:
        case DAYLIGHT_DETECTOR:
        case ENDER_CHEST:
        case CAKE:
            return true;
        default:
            return false;
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event){
        Block b = event.getClickedBlock();
        Player p = event.getPlayer();
        if(!Config.ENABLE_WORLD.getStringList().contains(p.getWorld().getName()))return;
        if(event.getAction() == Action.RIGHT_CLICK_BLOCK){
            if(!(
                    (b.getState() instanceof InventoryHolder) ||
                    isLimitInteractBlock(b.getType())
            )) return;
            if (p.isSneaking() && p.getInventory().getItemInMainHand().getType() == Material.TNT) return; //TNT 蹲下右鍵則無視

            //禁止玩家與限制區域互動
            if(data.checkIsLimit(b, p)){
                if(this.plugin.isOP(p)) return;
                p.sendMessage(Locales.NO_ACCESS.getString());
                if( b.getType() == Material.WATER || b.getType() == Material.LAVA)p.updateInventory();
                event.setCancelled(true);
                return;
            }
        } else if(event.getAction() == Action.PHYSICAL){
            if(data.checkIsLimit(b, p)){
                if(b.getType() == Material.TRIPWIRE || b.getType() == Material.TRIPWIRE_HOOK) return;
                if(this.plugin.isOP(p, false)) return;
                event.setCancelled(true);
                return;
            }
        }
    }
    
    //禁止物件和限制區域互動
    @EventHandler
    public void onEntryInteract(EntityInteractEvent event){
        Block b = event.getBlock();
        if(!Config.ENABLE_WORLD.getStringList().contains(b.getWorld().getName()))return;
        Entity e = event.getEntity();
        Player p = null;
        
        if(b.getType() == Material.TRIPWIRE || b.getType() == Material.TRIPWIRE_HOOK) return;
        
        //Maybe can add others
        if(e.getPassenger() instanceof Player){
            p = (Player) e.getPassenger();
        }
        
        if (p != null){
            if(data.checkIsLimit(b, p)){
                if(this.plugin.isOP(p)) return;
                event.setCancelled(true);
            }
        } else {
            if(data.checkIsLimit(b)) event.setCancelled(true);
        }
    }
    
    // 防止怪物破壞方塊
    final static EntityType[] cant_destory_block_entity = {
            EntityType.WITHER,
            EntityType.ENDERMAN
    };
    @EventHandler
    void onEntityBreakBlock(EntityChangeBlockEvent e){
        Block b = e.getBlock();
        if(!Config.ENABLE_WORLD.getStringList().contains(b.getWorld().getName()))return;
        if(!Arrays.asList(cant_destory_block_entity).contains(e.getEntityType()))return; //不是凋零或 Enderman 就  bypass
        
        if(data.checkIsLimit(b)){
            e.setCancelled(true);
        }
    }
    
    // 防止火焰
    @EventHandler
    void onBlockBurnDamage(BlockBurnEvent e){
    	Block b = e.getBlock();
        if(!Config.ENABLE_WORLD.getStringList().contains(b.getWorld().getName()))return;
    	if(data.checkIsLimit(b)){
    		e.setCancelled(true);
    		Location l = e.getBlock().getLocation();
    		if(Math.random() < 0.1){ //當物品要損壞時10%機率火焰消失
    			if(l.add(1,0,0).getBlock().getType().equals(Material.FIRE))
    				l.getBlock().setType(Material.AIR);
    			if(l.add(-2,0,0).getBlock().getType().equals(Material.FIRE))
    				l.getBlock().setType(Material.AIR);
    			if(l.add(1,1,0).getBlock().getType().equals(Material.FIRE))
    				l.getBlock().setType(Material.AIR);
    			if(l.add(0,-2,0).getBlock().getType().equals(Material.FIRE))
    				l.getBlock().setType(Material.AIR);
    			if(l.add(0,1,1).getBlock().getType().equals(Material.FIRE))
    				l.getBlock().setType(Material.AIR);
    			if(l.add(0,0,-2).getBlock().getType().equals(Material.FIRE))
    				l.getBlock().setType(Material.AIR);
    		}
    	}
    }

    
    @EventHandler
    void onFireSpread(BlockSpreadEvent e){
        if(!Config.ENABLE_WORLD.getStringList().contains(e.getBlock().getWorld().getName()))return;
    	if(e.getSource().getType() != Material.FIRE) return;
    	if(data.checkIsLimit(e.getBlock())){
    		e.setCancelled(true);
    	}
    }
    
    //防止活塞
    @EventHandler
    void onPistonExtend(BlockPistonExtendEvent e){
        if(!Config.ENABLE_WORLD.getStringList().contains(e.getBlock().getWorld().getName()))return;
        e.setCancelled(checkPiston(e.getBlocks(), e.getBlock(), true, e.getDirection()));
    }

    @EventHandler
    void onPistonRetract(BlockPistonRetractEvent e){
        if(!Config.ENABLE_WORLD.getStringList().contains(e.getBlock().getWorld().getName()))return;
        e.setCancelled(checkPiston(e.getBlocks(), e.getBlock()));
    }
    
    private boolean checkPiston(List<Block> blocks, Block piston){
        return checkPiston(blocks, piston, false, null);
    }
    
    private boolean checkPiston(List<Block> blocks, Block piston, Boolean push, BlockFace direction){
        boolean check_flag = false;
        String uuid = null;
        if(piston.hasMetadata("owner_uuid")){
            uuid = piston.getMetadata("owner_uuid").get(0).asString();
            check_flag = true;
        }
        if(push && check_flag){
            Location l = piston.getLocation().clone();
            switch(direction){
            case UP:
                l.add(0, (blocks.size()+1), 0);
                break;
            case DOWN:
                l.add(0, -(blocks.size()+1), 0);
                break;
            case WEST:
                l.add(-(blocks.size()+1), 0, 0);
                break;
            case EAST:
                l.add((blocks.size()+1), 0, 0);
                break;
            case NORTH:
                l.add(0, 0, -(blocks.size()+1));
                break;
            case SOUTH:
                l.add(0, 0, (blocks.size()+1));
                break;
            default:
                break;
            }
            if(plugin.cupboards.checkIsLimitByUUIDString(l, uuid)){
                return true;
            }
        }
        for(Block block : blocks){
            if(block.getType().equals(Material.GOLD_BLOCK)){
                return true;
            }
            if(check_flag){
                if(plugin.cupboards.checkIsLimitByUUIDString(block, uuid)){
                    return true;
                } else {
                    block.setMetadata("owner_uuid", new FixedMetadataValue(plugin, uuid));
                }
            }
        }
        return false;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPistonPlace(BlockPlaceEvent e){
        if(!Config.ENABLE_WORLD.getStringList().contains(e.getBlock().getWorld().getName()))return;
        Player p = e.getPlayer();
        Block b = e.getBlock();
        if( p != null){
            if(!e.isCancelled()){
                if(b.getType().equals(Material.PISTON) || b.getType().equals(Material.STICKY_PISTON)){
                    b.setMetadata("owner_uuid", new FixedMetadataValue(plugin, p.getUniqueId().toString()));
                }
            }
        }
    }
}
