package tw.mics.spigot.plugin.cupboard.listener;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import tw.mics.spigot.plugin.cupboard.Cupboard;
import tw.mics.spigot.plugin.cupboard.config.Config;
import tw.mics.spigot.plugin.cupboard.config.Locales;

public class EvilEssenceListener extends MyListener {
    class ChanceToDrop {
        ChanceToDrop(Double min, Double max){
            min_chance = min;
            max_chance = max;
        }
        public Double min_chance, max_chance;
    }
    
    Map<Material, ChanceToDrop> evilessence_drop_chance;
    
    public EvilEssenceListener(Cupboard instance) {
        super(instance);
        evilessence_drop_chance = new HashMap<Material, ChanceToDrop>();
        for(String str : Config.EVILESSENCE_DROPAMOUNT.getStringList()){
            String[] strs = str.split(":");
            evilessence_drop_chance.put(
                    Material.valueOf(strs[0]), 
                    new ChanceToDrop( Double.valueOf(strs[1]), Double.valueOf(strs[2]))
            );
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e){
        if(e.getPlayer().getGameMode() != GameMode.SURVIVAL)return;
        if(evilessence_drop_chance.containsKey(e.getBlock().getType())){
            ChanceToDrop ctd = evilessence_drop_chance.get(e.getBlock().getType());
            Double drop_amount = Math.random() * (ctd.max_chance - ctd.min_chance) + ctd.min_chance;
            Integer drop_amount_int = (int) Math.round(drop_amount);
            if(drop_amount_int >= 1){
                e.getBlock().setType(Material.AIR);
                for(int i=0; i < drop_amount_int; i++){
                    e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), getEvilEssence());
                }
            }
        }
    }

    // 防止邪惡精華被放置
    @EventHandler
    public void onRightClick(PlayerInteractEvent event){
        if(event.getPlayer().getGameMode() != GameMode.SURVIVAL)return;
        if(event.getHand() == null) return;
        switch(event.getHand()){
        case HAND:
            if(event.getPlayer().getInventory().getItemInMainHand().getType() != Config.EVILESSENCE_MATERIAL.getMaterial()) return;
            break;
        case OFF_HAND:
            if(event.getPlayer().getInventory().getItemInOffHand().getType() != Config.EVILESSENCE_MATERIAL.getMaterial()) return;
            break;
        default:
            return;
        }
        if(event.getAction() == Action.RIGHT_CLICK_BLOCK){
            event.setCancelled(true);
        }
    }

    //製造邪惡精華物品
    static public ItemStack getEvilEssence(){
        ItemStack item = new ItemStack(Config.EVILESSENCE_MATERIAL.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Locales.TNT_EVILESSENCE_NAME.getString());
        meta.setLore(Locales.TNT_EVILESSENCE_LORE.getStringList());
        meta.addEnchant(Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft("power")), 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }
}
