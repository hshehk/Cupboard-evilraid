package tw.mics.spigot.plugin.cupboard.data;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;
import tw.mics.spigot.plugin.cupboard.Cupboard;
import tw.mics.spigot.plugin.cupboard.config.Config;
import tw.mics.spigot.plugin.cupboard.utils.SchedulerCompat;
import tw.mics.spigot.plugin.cupboard.utils.Util;

public class CupboardsData {
    Connection db_conn;
	final private int PROTECT_DIST = Config.CUPBOARD_PROTECT_DIST.getInt();
	final private int CUPBOARD_DIST = Config.CUPBOARD_BETWEEN_DIST.getInt();
	final int maxEntries = 10000;
	private Map<String, Boolean> check_access_cache;
	private Cupboard plugin;
	
	public CupboardsData(Cupboard p, Connection conn){
		this.plugin = p;
		db_conn = conn;
		check_access_cache = new LinkedHashMap<String, Boolean>(maxEntries*10/7, 0.7f, true) {
            private static final long serialVersionUID = 1L;
            
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > maxEntries;
            }
        };
    }

    //================================= Check limit ====================================
	public synchronized boolean putCupboard(Block b, OfflinePlayer p){
	    if(p != null && !checkAccess(b, p, CUPBOARD_DIST)) return false;
        try {
            //insert cupboard
            String sql = "INSERT INTO CUPBOARDS (WORLD, X, Y, Z, LOC) " +
                    "VALUES (?,?,?,?,?);"; 
            PreparedStatement pstmt = db_conn.prepareStatement(sql);
            pstmt.setString(1, b.getWorld().getName());
            pstmt.setInt(2,b.getX());
            pstmt.setInt(3,b.getY());
            pstmt.setInt(4,b.getZ());
            pstmt.setString(5, Util.LocToString(b.getLocation()));
            pstmt.execute();
            
            int cid = pstmt.getGeneratedKeys().getInt(1);
            pstmt.close();
            
            if(p != null){
                sql = "INSERT INTO PLAYER_OWN_CUPBOARDS (UUID, CID) " +
                        "VALUES (?,?);";
                pstmt = db_conn.prepareStatement(sql);
                pstmt.setString(1, p.getUniqueId().toString());
                pstmt.setInt(2, cid);
                pstmt.execute();
                pstmt.close();
            }
            db_conn.commit();
        } catch ( SQLException e ) {
            rollbackQuietly();
            plugin.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            plugin.getLogger().log(Level.WARNING, e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            plugin.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            check_access_cache.clear();
            return false; //寫入失敗就不要讓金磚放下去，否則會出現沒有領地資料的金磚
        }
        
        this.changelog(String.format("%s put Cupboard at %s.", p != null ? p.getName() : "SYSTEM", Util.LocToString(b.getLocation())));
        check_access_cache.clear();
    	return true;
    }
	
    public synchronized boolean removeCupboard(Block b, OfflinePlayer p){
        boolean flag = false;
        try {
            Statement stmt = db_conn.createStatement();
            String sql = String.format("SELECT CUPBOARDS.CID FROM CUPBOARDS WHERE LOC = \"%s\"", Util.LocToString(b.getLocation()));
            ResultSet rs = stmt.executeQuery(sql);
            if(rs.next()){
                int cid = rs.getInt(1);
                sql = String.format("DELETE FROM CUPBOARDS WHERE CID = %d;", cid);
                stmt.execute(sql);
                sql = String.format("DELETE FROM PLAYER_OWN_CUPBOARDS WHERE CID = %d;", cid);
                stmt.execute(sql);
                flag = true;
            } else {
                flag = false;
            }
            stmt.close();
            rs.close();
            db_conn.commit();
        } catch ( SQLException e ) {
            rollbackQuietly();
            plugin.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            plugin.getLogger().log(Level.WARNING, e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            plugin.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        } 
        
        this.changelog(String.format("%s remove Cupboard at %s.", p.getName(), Util.LocToString(b.getLocation())));
        check_access_cache.clear();
        return flag;
    }
    
	public synchronized Boolean toggleBoardAccess(OfflinePlayer p, Block b){
        Boolean access_flag = null;
        Boolean return_flag = null;
	    try {
	        //確認是否有該金磚權限
            Statement stmt = db_conn.createStatement();
            String sql = String.format( 
                    "SELECT CUPBOARDS.CID, PLAYER_OWN_CUPBOARDS.UUID FROM CUPBOARDS "
                    + "LEFT JOIN PLAYER_OWN_CUPBOARDS "
                    + "ON CUPBOARDS.CID = PLAYER_OWN_CUPBOARDS.CID "
                    + "WHERE LOC=\"%s\""
                    , Util.LocToString(b.getLocation())
            );
            ResultSet rs = stmt.executeQuery(sql);
            Integer cid = null;
            if(rs.next()){
                access_flag = false;
                cid = rs.getInt(1);
                do{
                    String uuid = rs.getString(2);
                    if(uuid != null && uuid.equals(p.getUniqueId().toString())){
                        access_flag = true;
                        break;
                    }
                }while(rs.next());
            }
            

            //執行切換動作
            if(access_flag != null){
                if(access_flag){
                    sql = String.format("DELETE FROM PLAYER_OWN_CUPBOARDS WHERE UUID=\"%s\" AND CID=%d;", 
                            p.getUniqueId().toString(), cid);
                    stmt.execute(sql);
                    return_flag = false;
                    this.changelog(String.format("%s revoke access at %s", p.getName(), Util.LocToString(b.getLocation())));
                } else {
                    sql = String.format("INSERT INTO PLAYER_OWN_CUPBOARDS (UUID, CID) " +
                            "VALUES (\"%s\", %d);", 
                            p.getUniqueId().toString(), cid);
                    stmt.execute(sql);
                    return_flag = true;
                    this.changelog(String.format("%s grant access at %s.", p.getName(), Util.LocToString(b.getLocation())));
                }
            }
            stmt.close();
            rs.close();
            db_conn.commit();
        } catch ( SQLException e ) {
            rollbackQuietly();
            plugin.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            plugin.getLogger().log(Level.WARNING, e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            plugin.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        } 
	    check_access_cache.clear();
        return return_flag;
	}

	public boolean checkExplosionAble(Location l, float radius){
        return checkAccess(l, PROTECT_DIST + ((int)Math.ceil(radius)) + 2); // 加2才不會炸到
	}
	
	public boolean checkIsLimit(Block b){
		return !checkAccess(b);
	}
	
	public boolean checkIsLimit(Location l){
		return !checkAccess(l);
	}
	
	public boolean checkIsLimit(Block b, Player p){
		return !checkAccess(b, p);
	}
	
    public boolean checkIsLimit(Location l, Player p){
        return !checkAccess(l, p);
    }

    public boolean checkIsLimitByUUIDString(Block b, String uuid){
        return !checkAccess(b, uuid);
    }
    
    public boolean checkIsLimitByUUIDString(Location l, String uuid){
        return !checkAccess(l, uuid);
    }
    
    public boolean checkDirectAccess(Block block, Player player){
        return checkDirectAccess(block.getLocation(), player.getUniqueId().toString());
    }
    
    public synchronized boolean checkDirectAccess(Location location, String uuid){
        boolean flag_access = true;
        try {
            Statement stmt = db_conn.createStatement();
            String sql = "SELECT CUPBOARDS.CID FROM CUPBOARDS "
                    + String.format(
                            "WHERE X = %d "
                          + "AND Y = %d "
                          + "AND Z = %d "
                          + "AND WORLD = \"%s\" "
                          , location.getBlockX()
                          , location.getBlockY()
                          , location.getBlockZ()
                          , location.getWorld().getName());
            sql += String.format("AND CID NOT IN (SELECT CID FROM PLAYER_OWN_CUPBOARDS WHERE UUID=\"%s\")", uuid);
            ResultSet rs = stmt.executeQuery(sql);
            if(rs.next()){
                flag_access = false;
            }
            rs.close();
            stmt.close();
        } catch ( SQLException e ) {
            plugin.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            plugin.getLogger().log(Level.WARNING, e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            plugin.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            if(uuid != null){
                Player online = plugin.getServer().getPlayer(UUID.fromString(uuid));
                if(online != null) online.sendMessage("系統嚴重錯誤, 請聯繫管理員");
            }
            flag_access = false;
        }
        return flag_access;
    }
    
    //================================= Clean Not Exist Cupboard ====================================
    public synchronized void cleanNotExistCupboard(ChunkSnapshot chunkSnapshot){
        long startTime = System.nanoTime(); //Debug
        try {
            Statement stmt = db_conn.createStatement();
            int min_x = chunkSnapshot.getX()*16;
            int max_x = min_x + 15;
            int min_z = chunkSnapshot.getZ()*16;
            int max_z = min_z + 15;
            String world = chunkSnapshot.getWorldName();
            List<Integer> remove_cid_list = new ArrayList<Integer>();
            
            //Find
            String sql = "SELECT CID, LOC FROM CUPBOARDS "
                + String.format(
                        "WHERE WORLD = \"%s\" "
                      + "AND X <= %d AND X >= %d "
                      + "AND Z <= %d AND Z >= %d "
                      , world
                      , max_x, min_x
                      , max_z, min_z);
            ResultSet rs = stmt.executeQuery(sql);
            while(rs.next()){
                int cid = rs.getInt(1);
                String loc = rs.getString(2);
                remove_cid_list.add(cid);
                changelog(String.format("Cupboard at %s removed (reason: new chunk shouldn't have gold block)", loc));
            }
            
            //Delete
            String sql_remove_cupboards = "DELETE FROM CUPBOARDS WHERE CID = ?";
            PreparedStatement pstmt_cupboards = db_conn.prepareStatement(sql_remove_cupboards);
            for(Integer cid : remove_cid_list){
                pstmt_cupboards.setInt(1, cid);
                pstmt_cupboards.addBatch();
            }
            pstmt_cupboards.executeBatch();
            pstmt_cupboards.close();
            
            sql = "DELETE FROM PLAYER_OWN_CUPBOARDS WHERE CID NOT IN (SELECT CID FROM CUPBOARDS);";
            stmt.execute(sql);
            stmt.close();
            db_conn.commit();
        } catch (SQLException e) {
            rollbackQuietly();
            e.printStackTrace();
        }
        if(Config.DEBUG.getBoolean()){
            long totalTime = (System.nanoTime() - startTime);
            if(totalTime > 1000000)plugin.logDebug("Clean Not Exist Cupboard Time: " + totalTime/1000 + " μs");
        }
    }

    //Async
    public void cleanNotExistUser() {
        SchedulerCompat.runAsync(plugin, new Runnable(){
            @Override
            public void run() {
                synchronized(CupboardsData.this) {
                List<String> remove_uuid_list = new ArrayList<String>();
                try {
                    Statement stmt = db_conn.createStatement();
                    
                    //delete duplicate
                    String sql = "CREATE TEMPORARY TABLE PLAYER_OWN_CUPBOARDS_TEMP (UUID, CID);";
                    stmt.execute(sql);
                    sql = "INSERT INTO PLAYER_OWN_CUPBOARDS_TEMP(UUID, CID) SELECT UUID, CID FROM PLAYER_OWN_CUPBOARDS GROUP BY UUID, CID;";
                    stmt.execute(sql);
                    sql = "DELETE FROM PLAYER_OWN_CUPBOARDS;";
                    stmt.execute(sql);
                    sql = "INSERT INTO PLAYER_OWN_CUPBOARDS(UUID, CID) SELECT UUID, CID FROM PLAYER_OWN_CUPBOARDS_TEMP;";
                    stmt.execute(sql);
                    sql = "DROP TABLE PLAYER_OWN_CUPBOARDS_TEMP;";
                    stmt.execute(sql);
                    
                    //find non-exist player data
                    // 改用 Bukkit API 判斷玩家是否存在過，不要自己解析 playerdata 檔案路徑，
                    // 因為世界儲存格式（例如 1.26.1 起 playerdata 移到 players/ 底下）可能會變動，
                    // 硬解路徑一旦抓不到檔案，會誤判所有玩家不存在而把授權整批刪除。
                    sql = "SELECT UUID FROM PLAYER_OWN_CUPBOARDS GROUP BY UUID;";
                    ResultSet rs = stmt.executeQuery(sql);
                    while(rs.next()){
                        String uuid_str = rs.getString(1);
                        boolean played_before;
                        try {
                            played_before = Bukkit.getOfflinePlayer(UUID.fromString(uuid_str)).hasPlayedBefore();
                        } catch (IllegalArgumentException e) {
                            // UUID 格式壞掉的舊資料，視為不存在的玩家一併清掉
                            played_before = false;
                        }
                        if(!played_before){
                            remove_uuid_list.add(uuid_str);
                        }
                    }

                    //delete non-exist player data
                    String sql_remove_player = "DELETE FROM PLAYER_OWN_CUPBOARDS WHERE UUID = ?";
                    PreparedStatement pstmt_player = db_conn.prepareStatement(sql_remove_player);
                    for(String uuid : remove_uuid_list){
                        pstmt_player.setString(1, uuid);
                        pstmt_player.addBatch();
                        changelog(String.format("Player data %s removed", uuid));
                    }
                    pstmt_player.executeBatch();
                    pstmt_player.close();
                    
                    stmt.close();
                    db_conn.commit();
                } catch (SQLException e) {
                    rollbackQuietly();
                    e.printStackTrace();
                }

                plugin.log("Cleaned %d not exist player!", remove_uuid_list.size());
                }
            }
        });
    }

    //Async
    public void findNearest(Player p){
        final Location l = p.getLocation().clone();
        final UUID playerUuid = p.getUniqueId();
        final String playerName = p.getName();

        SchedulerCompat.runAsync(plugin, new Runnable(){
            @Override
            public void run() {
                synchronized(CupboardsData.this) {
                String uuid = playerUuid.toString();
                int x = l.getBlockX();
                int y = l.getBlockY();
                int z = l.getBlockZ();
                int radius = 20;
                String world = l.getWorld().getName();
                double distance = radius * 2;
                String nearest_loc = null;

                try {
                    Statement stmt = db_conn.createStatement();
                    String sql = "SELECT CUPBOARDS.LOC FROM CUPBOARDS "
                            + String.format(
                                    "WHERE X <= %d AND X >= %d "
                                  + "AND Y <= %d AND Y >= %d "
                                  + "AND Z <= %d AND Z >= %d "
                                  + "AND WORLD = \"%s\" "
                                  , x + radius, x - radius
                                  , y + radius, y - radius
                                  , z + radius, z - radius
                                  , world);
                    sql += String.format("AND CID IN (SELECT CID FROM PLAYER_OWN_CUPBOARDS WHERE UUID=\"%s\")", uuid);
                    ResultSet rs = stmt.executeQuery(sql);
                    while(rs.next()){
                        String this_loc_str = rs.getString(1);
                        double this_distance = l.distance(Util.StringToLoc(this_loc_str));
                        if(this_distance < distance){
                            nearest_loc = this_loc_str;
                            distance = this_distance;
                        }
                    }
                    rs.close();
                    stmt.close();
                } catch ( SQLException e ) {
                    plugin.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    plugin.getLogger().log(Level.WARNING, e.getClass().getName() + ": " + e.getMessage());
                    e.printStackTrace();
                    plugin.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    final String failMessage = ChatColor.DARK_RED + "系統嚴重錯誤, 請聯繫管理員";
                    Player online = plugin.getServer().getPlayer(playerUuid);
                    if(online != null){
                        SchedulerCompat.runForEntity(plugin, online, () -> online.sendMessage(failMessage));
                    } else {
                        plugin.log("Failed to notify %s about the nearest cupboard error.", playerName);
                    }
                    return;
                }

                final String message = nearest_loc == null
                        ? ChatColor.RED + "附近沒有已授權金磚"
                        : ChatColor.GOLD + "最近的已授權金磚在 " + nearest_loc;
                Player online = plugin.getServer().getPlayer(playerUuid);
                if(online != null){
                    SchedulerCompat.runForEntity(plugin, online, () -> online.sendMessage(message));
                }
                }
            }
        });
    }

    //Async
    public void giveAcceee(String giver_uuid, String receiver_uuid, Location l){
        final Location location = l.clone();
        final String giverName = safeOfflineName(giver_uuid);
        final String receiverName = safeOfflineName(receiver_uuid);

        SchedulerCompat.runAsync(plugin, new Runnable(){
            @Override
            public void run() {
                synchronized(CupboardsData.this) {
                int x = location.getBlockX();
                int z = location.getBlockZ();
                int radius = 200;
                String world = location.getWorld().getName();

                try {
                    Statement stmt = db_conn.createStatement();
                    String sql = String.format("INSERT INTO PLAYER_OWN_CUPBOARDS (UUID, CID) "
                            + "SELECT \"%s\",CID  AS CT FROM PLAYER_OWN_CUPBOARDS  T1 "
                            + "WHERE CID IN (SELECT CID FROM CUPBOARDS "
                            + String.format("WHERE X <= %d AND X >= %d "
                                    + "AND Z <= %d AND Z >= %d "
                                    + "AND WORLD = \"%s\") "
                                    , x + radius, x - radius
                                    , z + radius, z - radius
                                    , world)
                            + "AND CID IN (SELECT CID FROM `PLAYER_OWN_CUPBOARDS` WHERE `UUID`=\"%s\") "
                            + "AND CID NOT IN (SELECT CID FROM `PLAYER_OWN_CUPBOARDS` WHERE `UUID`=\"%s\") "
                            + "GROUP BY CID",
                            receiver_uuid,
                            giver_uuid,
                            receiver_uuid);
                    stmt.execute(sql);
                    stmt.close();
                    db_conn.commit();

                    changelog(String.format("%s give his cupbaords to %s at %s",
                            giverName,
                            receiverName,
                            Util.LocToString(location)));
                } catch (SQLException e) {
                    rollbackQuietly();
                    plugin.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    plugin.getLogger().log(Level.WARNING, e.getClass().getName() + ": " + e.getMessage());
                    e.printStackTrace();
                    plugin.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    return;
                }

                check_access_cache.clear();

                Player giver = plugin.getServer().getPlayer(UUID.fromString(giver_uuid));
                Player receiver = plugin.getServer().getPlayer(UUID.fromString(receiver_uuid));
                if(giver != null){
                    SchedulerCompat.runForEntity(plugin, giver, () ->
                        giver.sendMessage(ChatColor.GREEN + "已把附近 ±200 格內已授權金磚賦予給 " + receiverName));
                }
                if(receiver != null){
                    SchedulerCompat.runForEntity(plugin, receiver, () ->
                        receiver.sendMessage(ChatColor.GREEN + " " + giverName + " 把附近 ±200 格內已授權金磚賦予給您"));
                }
                }
            }
        });
    }

    private String safeOfflineName(String uuid) {
        try {
            String name = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
            return name != null ? name : uuid;
        } catch (IllegalArgumentException e) {
            return uuid;
        }
    }

    //================================= Private function ====================================
    private boolean checkAccess(Block b){
        return checkAccess(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), null, PROTECT_DIST);
    }
    
    private boolean checkAccess(Location l){
        return checkAccess(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), null, PROTECT_DIST);
    }

    private boolean checkAccess(Block b, Player p){
        return checkAccess(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), p.getUniqueId().toString(), PROTECT_DIST);
    }
    
    private boolean checkAccess(Location l, Player p){
        return checkAccess(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), p.getUniqueId().toString(), PROTECT_DIST);
    }
    
    private boolean checkAccess(Block b, String uuid){
        return checkAccess(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), uuid, PROTECT_DIST);
    }
    
    private boolean checkAccess(Location l, String uuid){
        return checkAccess(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), uuid, PROTECT_DIST);
    }
    
    private boolean checkAccess(Block b, OfflinePlayer p, int radius){
        return checkAccess(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), p.getUniqueId().toString(), radius);
    }
    
    private boolean checkAccess(Location b, int radius){
        return checkAccess(b.getWorld().getName(), b.getBlockX(), b.getBlockY(), b.getBlockZ(), null, radius);
    }
    
    private synchronized boolean checkAccess(String world, int x, int y, int z, String uuid,int radius){
        long startTime = System.nanoTime(); //Debug
        String cache_key = String.format("%s,%d,%d,%d,%s,%d",world,x,y,z,uuid,radius);
        Boolean flag_access = check_access_cache.get(cache_key);
        if(flag_access == null){
            flag_access = true;
            try {
                Statement stmt = db_conn.createStatement();
                String sql = "SELECT CUPBOARDS.CID FROM CUPBOARDS WHERE ";
                if(uuid != null) sql += String.format("CID NOT IN (SELECT CID FROM PLAYER_OWN_CUPBOARDS WHERE UUID=\"%s\") AND ", uuid);
                sql += String.format(
                        "WORLD = \"%s\" "
                        + "AND X BETWEEN %d AND %d "
                        + "AND Y BETWEEN %d AND %d "
                        + "AND Z BETWEEN %d AND %d "
                        , world
                        , x - radius, x + radius
                        , y - radius, y + radius
                        , z - radius, z + radius);
                ResultSet rs = stmt.executeQuery(sql);
                if(rs.next()){
                    flag_access = false;
                }
                rs.close();
                stmt.close();
            } catch ( SQLException e ) {
                plugin.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                plugin.getLogger().log(Level.WARNING, e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();
                plugin.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                if(uuid != null){
                    Player online = plugin.getServer().getPlayer(UUID.fromString(uuid));
                    if(online != null) online.sendMessage("系統嚴重錯誤, 請聯繫管理員");
                }
                flag_access = false;
            }
            check_access_cache.put(cache_key, flag_access);
        }
        if(Config.DEBUG.getBoolean()){
            long totalTime = (System.nanoTime() - startTime);
            if(totalTime > 1000000)plugin.logDebug("Check Access Time: " + totalTime/1000 + " μs");
        }
        return flag_access;
    }

    private void rollbackQuietly(){
        try {
            db_conn.rollback();
        } catch (SQLException ignore) {
        }
    }

    private void changelog(String msg){
        SchedulerCompat.runAsync(plugin, new Runnable(){
            @Override
            public void run() {
                try
                {
                    File dataFolder = plugin.getDataFolder();
                    if(!dataFolder.exists()){
                        dataFolder.mkdir();
                    }
                    File saveTo = new File(plugin.getDataFolder(), "cupboard.log");
                    if (!saveTo.exists()){
                        saveTo.createNewFile();
                    }
                    FileWriter fw = new FileWriter(saveTo, true);
                    PrintWriter pw = new PrintWriter(fw);
                    DateFormat dateFormat = new SimpleDateFormat("[yyyy/MM/dd HH:mm:ss] ");
                    Calendar cal = Calendar.getInstance();
                    pw.println(dateFormat.format(cal.getTime()) + msg);
                    pw.flush();
                    pw.close();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        });
    }
}
