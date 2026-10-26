package tw.mics.spigot.plugin.cupboard.listener;

import java.util.Iterator;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import tw.mics.spigot.plugin.cupboard.Cupboard;
import tw.mics.spigot.plugin.cupboard.config.Locales;
import tw.mics.spigot.plugin.cupboard.utils.SchedulerCompat;
import tw.mics.spigot.plugin.cupboard.utils.Util;

public class TNTCraftListener extends MyListener {
	public TNTCraftListener(Cupboard instance)
	{
		super(instance);
	    overwriteTNTRecipes();
	}

	private void overwriteTNTRecipes(){
    	Iterator<Recipe> it = this.plugin.getServer().recipeIterator();
    	Recipe recipe;
    	
    	//remove TNT Recipes（把原版跟其他插件可能加的TNT配方都清掉，重新加我們要的兩種）
    	while(it.hasNext()){
    		recipe = it.next();
    		if (recipe != null && recipe.getResult().getType() == Material.TNT){
				it.remove();
    		}
    	}
    	
    	//setup 特殊TNT：中間原版TNT，周圍8格火藥
    	ItemStack item = new ItemStack(Material.TNT);
    	ItemMeta meta = item.getItemMeta();
    	meta.setDisplayName(Locales.TNT_SPECIAL_NAME.getString());
    	meta.setLore(Locales.TNT_TNT_LORE.getStringList());
    	item.setItemMeta(meta);
    	Util.markSpecialTNTItem(item); // 標記成特殊TNT，跟原版TNT區分，放置時才會走邪惡精華/自動引爆邏輯
    	
    	ShapedRecipe shapedRecipe = new ShapedRecipe(item);
    	shapedRecipe.shape("GGG", "GTG", "GGG");
    	shapedRecipe.setIngredient('G', Material.GUNPOWDER);
    	shapedRecipe.setIngredient('T', Material.TNT);
    	Bukkit.addRecipe(shapedRecipe);
    	
    	//setup 原版TNT：真正原版配方＆外觀，不帶任何標記
    	item = new ItemStack(Material.TNT);
    	shapedRecipe = new ShapedRecipe(item);
    	shapedRecipe.shape("GSG", "SGS", "GSG");
    	shapedRecipe.setIngredient('S', Material.SAND);
    	shapedRecipe.setIngredient('G', Material.GUNPOWDER);
    	Bukkit.addRecipe(shapedRecipe);
    }
	
	//Geyser(Bedrock)玩家合成介面跟Java協議不同，已知會有合成後物品散在不同格、不會自動疊的翻譯限制，
	//這裡在伺服器端直接整理背包繞過去，跟客戶端顯示無關，Java玩家不受影響（反正本來就會疊好）
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPrepareCraft(PrepareItemCraftEvent event){
	    ItemStack result = event.getInventory().getResult();
	    if(result == null || result.getType() != Material.TNT) return;
	    if(!Util.isSpecialTNTItem(result)) return;
	    for(HumanEntity viewer : event.getViewers()){
	        if(!(viewer instanceof Player)) continue;
	        Player p = (Player) viewer;
	        SchedulerCompat.runForEntity(this.plugin, p, () ->
	            Util.stackSimilarItems(p.getInventory(), item -> item.getType() == Material.TNT && Util.isSpecialTNTItem(item), 64));
	    }
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onCraft(CraftItemEvent event){
	    if(event.isCancelled()) return;
	    if(event.getRecipe().getResult().getType() != Material.TNT) return;
	    if(!Util.isSpecialTNTItem(event.getRecipe().getResult())) return;
	    if(!(event.getWhoClicked() instanceof Player)) return;
	    Player p = (Player) event.getWhoClicked();
	    SchedulerCompat.runForEntity(this.plugin, p, () ->
	        Util.stackSimilarItems(p.getInventory(), item -> item.getType() == Material.TNT && Util.isSpecialTNTItem(item), 64));
	}

}


