package tw.mics.spigot.plugin.cupboard.listener;

import java.util.Iterator;
import java.util.LinkedList;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause;

import tw.mics.spigot.plugin.cupboard.Cupboard;
import tw.mics.spigot.plugin.cupboard.config.Config;
import tw.mics.spigot.plugin.cupboard.data.CupboardsData;
import tw.mics.spigot.plugin.cupboard.utils.Util;

public class CupboardExplosionProtectListener extends MyListener {
	private CupboardsData data;

    private LinkedList<Integer> disable_explosion_id;

    public CupboardExplosionProtectListener(Cupboard instance)
    {
        super(instance);
        this.data = this.plugin.cupboards;
        disable_explosion_id = new LinkedList<Integer>();
    }
    
    
    @EventHandler
    public void onExplosion(ExplosionPrimeEvent e){
        Entity entity = e.getEntity();
        if(!Config.ENABLE_WORLD.getStringList().contains(entity.getWorld().getName()))return;
        if(entity instanceof TNTPrimed && !Config.ANTI_TNT_EXPLOSION.getBoolean()) return;
        if(!(entity instanceof TNTPrimed) && !Config.ANTI_OTHERS_EXPLOSION.getBoolean()) return;
        if(!data.checkExplosionAble(e.getEntity().getLocation(), e.getRadius())){
            disable_explosion_id.push(e.getEntity().getEntityId());
        }
        
        //保存50個爆炸id 應該不會有50個爆炸同時發生吧....
        while(disable_explosion_id.size() > 50){
            disable_explosion_id.pollLast();
        }
    }
    
    //防止插件爆炸
    @EventHandler
    public void onExplode(BlockExplodeEvent event){
        if(!Config.ENABLE_WORLD.getStringList().contains(event.getBlock().getWorld().getName()))return;
        event.blockList().removeIf(b -> b.getType() == Material.GOLD_BLOCK); //金磚不能被炸掉
        if(!Config.ANTI_OTHERS_EXPLOSION.getBoolean()) return;
        float distance_longest = 0;
        Iterator<Block> itr = event.blockList().iterator();
        while(itr.hasNext()){
            Block b = itr.next();
            float dis_now = (float) b.getLocation().distance(event.getBlock().getLocation());
            if(dis_now > distance_longest){
                distance_longest = dis_now;
            }
        }
        if(!data.checkExplosionAble(event.getBlock().getLocation(), distance_longest)){
            event.blockList().clear();
        }
    }
	
    //TNT or Creeper爆炸
    @EventHandler(priority = EventPriority.LOWEST)
    public void onExplode(EntityExplodeEvent event){
        if(!Config.ENABLE_WORLD.getStringList().contains(event.getEntity().getWorld().getName()))return;
        if((
                Config.ANTI_OTHERS_EXPLOSION.getBoolean() && 
                event.getEntityType() == EntityType.MINECART_TNT
            ) || (
                disable_explosion_id.contains(event.getEntity().getEntityId())
            )){
            event.blockList().clear();
            return;
        }
        //金磚是保護區的授權依據，不管什麼爆炸(原版TNT、Creeper等)都不能被炸掉
        event.blockList().removeIf(b -> b.getType() == Material.GOLD_BLOCK);
        
        //原版TNT：如果引爆點不在任何保護區內，代表這是從外部炸過來的（沒權限的人在保護區外引爆），
        //要把爆炸範圍內屬於任何保護區的方塊都排除掉，不能讓外部爆炸波及保護區
        //（如果引爆點本身就在保護區內，代表點燃時就已經通過權限檢查了，不用再過濾）
        if(event.getEntity() instanceof TNTPrimed && !Util.isSpecialTNTEntity(event.getEntity())){
            if(!data.checkIsLimit(event.getEntity().getLocation())){
                event.blockList().removeIf(b -> data.checkIsLimit(b.getLocation()));
            }
        }
    }

    //防止Armor stand被炸毀
    @EventHandler(priority = EventPriority.LOWEST)
    public void onArmorStandExplosion(EntityDamageByEntityEvent e){
        if(!Config.ENABLE_WORLD.getStringList().contains(e.getEntity().getWorld().getName()))return;
    	if(
    		e.getEntity().getType() == EntityType.ARMOR_STAND &&
    		e.getCause() == DamageCause.ENTITY_EXPLOSION
		){        
    	    if(disable_explosion_id.contains(e.getDamager().getEntityId())){
                e.setCancelled(true);
            }
    	}
    }
    
    //防止Hanging類物品 被Creeper炸掉 / 被TNT炸掉
    @EventHandler
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if(!Config.ENABLE_WORLD.getStringList().contains(e.getEntity().getWorld().getName()))return;
        //TODO fix TNT can destory (低重要度)
		if(e.getCause() == RemoveCause.ENTITY){ //by Entity
		    if(disable_explosion_id.contains(e.getRemover().getEntityId())){
                e.setCancelled(true);
            }
		}
	}
}
