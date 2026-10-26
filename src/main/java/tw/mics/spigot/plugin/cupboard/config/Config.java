package tw.mics.spigot.plugin.cupboard.config;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import tw.mics.spigot.plugin.cupboard.Cupboard;
import tw.mics.spigot.plugin.cupboard.utils.Util;

public enum Config {

	DEBUG("debug", false, "是否顯示除錯訊息"),
	CUPBOARD_PROTECT_DIST("cupboard.protect_dist", 9, "金磚保護區範圍大小 (例如填9，代表以金磚為中心左右各9格 -> 19*19*19)"),
    CUPBOARD_BETWEEN_DIST("cupboard.between_dist", 18, "金磚與金磚之間至少要間隔幾格，才能再放下一個新的金磚"),
    CUPBOARD_PREVENT_TELEPORT_ENABLE("cupboard.prevent-teleport.enable", true, "是否阻擋玩家用終界珍珠、紫頌果傳送進入保護區"),
    CUPBOARD_PREVENT_TELEPORT_IGNORE_Y("cupboard.prevent-teleport.ignore-y", 55, "Y座標低於這個值時，傳送阻擋不生效（仍然可以傳送進去）"),
	ANTI_TNT_EXPLOSION("cupboard.anti-tnt-explosion", false, "金磚保護區是否阻擋TNT爆炸造成的破壞"),
	ANTI_OTHERS_EXPLOSION("cupboard.anti-creeper-explosion", true, "金磚保護區是否阻擋苦力怕爆炸造成的破壞"),
    OP_BYPASS("cupboard.is-op-creative-bypass", true, "OP在創造模式下是否可以無視金磚保護區限制"),
    CLEAN_UNKNOWN_PLAYERS("cupboard.clean-unknown-players", true, "啟動時是否刪除「從未進過伺服器」的玩家授權（playerdata 被重置或搬移的伺服器建議關閉，避免授權被整批清空）"),
    ANTI_DUPE_ENABLE("anti-dupe.enable", true, "是否啟用多方塊放置回滾防複製（BlockMultiPlaceEvent 被取消後，清掉該方塊座標上剛生成的掉落物）"),
    ANTI_DUPE_WINDOW_MS("anti-dupe.window-ms", 250, "防複製：事件被取消後，這個毫秒數內於受影響座標生成的掉落物會被取消"),
    ENABLE_WORLD("cupboard.enable-world", new String[]{
            "world"
    }, "哪些世界要啟用金磚保護機制"),

	TNT_SP_ENABLE("tnt.enable", true, "是否讓插件接管TNT相關事件（合成特殊TNT、放置權限、爆炸邏輯等）"),
    TNT_EXPLOSION_RADIUS("tnt.radius", 1, "特殊TNT的爆炸半徑（只能填整數）"),
    TNT_COST("tnt.cost", 2, "放置1個特殊TNT要消耗手上幾個原版TNT"),
    TNT_BREAKCHANCE("tnt.breakchance", new String[]{
            "BARRIER:0:AIR",
            "BEDROCK:0:AIR",
            "STRUCTURE_BLOCK:0:AIR",
            "STRUCTURE_VOID:0:AIR",
            "COMMAND_BLOCK:0:AIR",
            "CHAIN_COMMAND_BLOCK:0:AIR",
            "REPEATING_COMMAND_BLOCK:0:AIR",
            "END_PORTAL:0:AIR",
            "END_PORTAL_FRAME:0:AIR",
            "END_GATEWAY:0:AIR",
            "GOLD_BLOCK:0:AIR",
            "ANVIL:0.25:AIR",
            "ENCHANTING_TABLE:0.25:AIR",
            "ENDER_CHEST:0.25:AIR",
            "OBSIDIAN:0.5:COBBLESTONE"
    }, "設定特殊TNT炸到各方塊時的破壞機率，格式為 材質:機率:轉換成的材質（轉換材質填DROP代表跟原版TNT一樣直接摧毀掉落）"),
    TNT_FUSETICK("tnt.fusetick", 100, "特殊TNT的引信時間（單位tick，只能填整數）"),
    EVILESSENCE_ENABLE("evilessence.enable", true, "是否啟用邪惡精華機制"),
    EVILESSENCE_TNT_COST("evilessence.tnt-place-cost", 1, "放置1個特殊TNT要消耗的邪惡精華數量"),
    EVILESSENCE_TNT_COST_BOUNS_Y("evilessence.tnt-place-cost-bouns-y", 55, "Y座標高於這個值時，邪惡精華消耗量改用 tnt-place-cost-bouns-amount 的設定"),
    EVILESSENCE_TNT_COST_BOUNS_AMOUNT("evilessence.tnt-place-cost-bouns-amount", 2, "Y座標超過 tnt-place-cost-bouns-y 時，改消耗這個數量的邪惡精華"),
    EVILESSENCE_MATERIAL("evilessence.material", "COMMAND_BLOCK_MINECART", "邪惡精華使用的物品外觀，填 Bukkit Material 名稱 (例如 COMMAND_BLOCK_MINECART, NETHER_STAR)，建議挑一個玩家平常拿不到的材質"),
    EVILESSENCE_DROPAMOUNT("evilessence.dropamount", new String[]{
            "SPAWNER:3:5"
    }, "破壞方塊掉落邪惡精華的設定，格式為 方塊材質:最小掉落數量:最大掉落數量"),

	LOCALE("locale", "en", "語言檔檔名（對應 locales 資料夾裡的檔名，不含副檔名）");
	
	private final Object value;
	private final String path;
	private final String description;
	private static YamlConfiguration cfg;
	private static final File f = new File(Cupboard.getInstance().getDataFolder(), "config.yml");
	
	private Config(String path, Object val, String description) {
	    this.path = path;
	    this.value = val;
	    this.description = description;
	}
	
	public String getPath() {
	    return path;
	}
	
	public String getDescription() {
	    return description;
	}
	
	public Object getDefaultValue() {
	    return value;
	}

	public boolean getBoolean() {
	    return cfg.getBoolean(path);
	}
	
	public int getInt() {
	    return cfg.getInt(path);
	}
	
	public double getDouble() {
	    return cfg.getDouble(path);
	}
	
	public String getString() {
	    return Util.replaceColors(cfg.getString(path));
	}
	
	public Material getMaterial() {
	    String raw = cfg.getString(path);
	    Material m = Material.matchMaterial(raw == null ? "" : raw);
	    if (m == null) {
	        String fallback = (String) value;
	        Cupboard.getInstance().log(
	                "設定檔 %s 的值 \"%s\" 不是有效的 Material，改用預設值 %s",
	                path, raw, fallback);
	        m = Material.matchMaterial(fallback);
	    }
	    return m;
	}
	
	public List<String> getStringList() {
	    return cfg.getStringList(path);
	}
	
	public static void load() {
		boolean save_flag = false;
		
        Cupboard.getInstance().getDataFolder().mkdirs();
        String header = "";
		cfg = YamlConfiguration.loadConfiguration(f);

        for (Config c : values()) {
            if(c.getDescription().toLowerCase().equals("removed")){
                if(cfg.contains(c.getPath())){
                    save_flag = true;
                    cfg.set(c.getPath(), null);
                }
                continue;
            }
            if(!c.getDescription().isEmpty()){
                header += c.getPath() + ": " + c.getDescription() + System.lineSeparator();
            }
            if (!cfg.contains(c.getPath())) {
            	save_flag = true;
                c.set(c.getDefaultValue(), false);
            }
        }
        cfg.options().header(header);
        
        if(save_flag){
        	save();
    		cfg = YamlConfiguration.loadConfiguration(f);
        }
	}
	
	public static void save(){
		try {
			cfg.save(f);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void set(Object value, boolean save) {
	    cfg.set(path, value);
	    if (save) {
            save();
	    }
	}
}
