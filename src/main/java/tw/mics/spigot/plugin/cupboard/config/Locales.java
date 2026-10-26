package tw.mics.spigot.plugin.cupboard.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import tw.mics.spigot.plugin.cupboard.Cupboard;
import tw.mics.spigot.plugin.cupboard.utils.Util;

public enum Locales {

	OP_BYPASS("gold-block.op-bypass", "&cWARNING: YOU ARE BYPASS PROTECT AREA."),
	GOLD_PLACE("gold-block.place-gold-block", "Block of Gold is placed and got access."),
	GOLD_REMOVE("gold-block.remove-gold-block", "Block of Gold is removed."),
	GOLD_TOO_CLOSE("gold-block.gold-block-too-close-to-place", "Block of Gold is too close to place."),
	GOLD_ACCESS_BLOCKED("gold-block.access-blocked-gold-block", "It is blocked, can't access it."),
	GOLD_DATA_NOT_FOUND("gold-block.gold-data-not-found", "This Block of Gold is not place by player or data missing."),
	GOLD_GRANT_ACCESS("gold-block.grant-access", "Grant access."),
	GOLD_REVOKE_ACCESS("gold-block.revoke-access", "Revoke access."),
	NO_ACCESS("gold-block.no-access", "&4No access."),
	
	//TNT
    TNT_SPECIAL_NAME("tnt.special-name", "&4Special TNT"),
    TNT_TNT_LORE("tnt.tnt-lore", new String[] {
            "&r&6Put in portect area, will Auto Ignite.",
            "&r&6Can destory protect area.",
            "&r&6Can destory water, lava and obsidian.",
    }), 
    
    TNT_EVILESSENCE_NAME("tnt.evilessence-name", "&5Evil Essence" ), 
    
    TNT_EVILESSENCE_LORE("tnt.evilessence-lore", new String[] {
            "&r&6Evil essence.",
    }), 
    TNT_NOT_ENOUGH("tnt.tnt-not-enough", "&4on hand TNT not enough (it cost 2)"),
    TNT_EVILESSENCE_NOT_ENOUGH("tnt.evilessence-not-enough", "&4Evil Essence not enough");
	
	private final Object value;
	private final String path;
	private static YamlConfiguration cfg;
	private static final File localeFolder = new File(Cupboard.getInstance().getDataFolder().getAbsolutePath() + File.separator + "locales");
	private static final File f = new File(localeFolder, Config.LOCALE.getString() + ".yml");
	
	private Locales(String path, Object val) {
	    this.path = path;
	    this.value = val;
	}
	
	public String getPath() {
	    return path;
	}
	
	public Object getDefaultValue() {
	    return value;
	}

	public String getString() {
	    return Util.replaceColors(cfg.getString(path));
	}
	
	public List<String> getStringList() {
		List<String> strlist = new ArrayList<String>();
		for( String str : cfg.getStringList(path)){
			strlist.add(Util.replaceColors(str));
		}
	    return strlist;
	}
	
	public void send(CommandSender s) {
	    s.sendMessage(getString());
	}
	
	public void send(CommandSender s, Map<String, String> map) {
	    String msg = getString();
	    for (String string : map.keySet()) {
	        msg = msg.replaceAll(string, map.get(string));
	    }
	    s.sendMessage(msg);
	}
	
	public static void load() {
		boolean save_flag = false;
	    localeFolder.mkdirs();
	    if (!f.exists()) {
	    	try{
	    		Cupboard.getInstance().saveResource("locales" + File.separator + Config.LOCALE.getString() + ".yml", true);
	    	} catch (IllegalArgumentException ex) {
	    		Cupboard.getInstance().log("Can't find this locales file, create new file for it");
    	    }
	    } 

		cfg = YamlConfiguration.loadConfiguration(f);

        for (Locales c : values()) {
            if (!cfg.contains(c.getPath())) {
            	save_flag = true;
                c.set(c.getDefaultValue(), false);
            }
        }
        
        if(save_flag){
        	save();
    		cfg = YamlConfiguration.loadConfiguration(f);
        }
	}

	public void set(Object value, boolean save){
	    cfg.set(path, value);
	    if (save) {
            Locales.save();
	    }
	}
	
	public static void save(){
	    localeFolder.mkdirs();
		try {
			cfg.save(f);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void set(Object value) throws IOException {
	    this.set(value, true);
	}
	
	public static Locales fromPath(String path) {
	    for (Locales loc : values()) {
	        if (loc.getPath().equalsIgnoreCase(path)) return loc;
	    }
	    return null;
	}
}
