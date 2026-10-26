package tw.mics.spigot.plugin.cupboard.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

import tw.mics.spigot.plugin.cupboard.Cupboard;

public class Database {
    private Cupboard plugin;
    
    private File dbfile;
    Connection db_conn;
    
    public Database(Cupboard p, File dataFolder){
        this.plugin = p;
        dbfile = new File(dataFolder, "database.db");
        this.initDatabase();
    }
    
    public Connection getConnection(){
        return db_conn;
    }
    
    private void initDatabase() {
        try {
          Class.forName("org.sqlite.JDBC");
          db_conn = DriverManager.getConnection("jdbc:sqlite:"+dbfile.getPath());
          db_conn.setAutoCommit(false);
       
          //新增表格
          Statement stmt = db_conn.createStatement();
          String sql = "CREATE TABLE IF NOT EXISTS CUPBOARDS " +
                  "(CID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                  " WORLD   TEXT     NOT NULL," +
                  " X       INTEGER  NOT NULL," +
                  " Y       INTEGER  NOT NULL," +
                  " Z       INTEGER  NOT NULL," +
                  " LOC     TEXT     UNIQUE NOT NULL)";
          stmt.executeUpdate(sql);

          sql = "CREATE TABLE IF NOT EXISTS PLAYER_OWN_CUPBOARDS " +
                  "(UUID TEXT NOT NULL," +
                  " CID  INTEGER NOT NULL)";
          stmt.executeUpdate(sql);

          sql = "CREATE INDEX IF NOT EXISTS player_own_cupboards_uuid_index " +
                  "on PLAYER_OWN_CUPBOARDS (UUID)";
          stmt.executeUpdate(sql);

          sql = "CREATE INDEX IF NOT EXISTS cupboards_cid_world_x_index " +
                  "on CUPBOARDS (CID, WORLD, X)";
          stmt.executeUpdate(sql);
          sql = "CREATE INDEX IF NOT EXISTS cupboards_cid_world_y_index " +
                  "on CUPBOARDS (CID, WORLD, Y)";
          stmt.executeUpdate(sql);
          sql = "CREATE INDEX IF NOT EXISTS cupboards_cid_world_z_index " +
                  "on CUPBOARDS (CID, WORLD, Z)";
          stmt.executeUpdate(sql);
          
          sql = "CREATE INDEX IF NOT EXISTS cupboards_world_x_index " +
                  "on CUPBOARDS (WORLD, X)";
          stmt.executeUpdate(sql);
          sql = "CREATE INDEX IF NOT EXISTS cupboards_world_z_index " +
                  "on CUPBOARDS (WORLD, Z)";
          stmt.executeUpdate(sql);
          sql = "CREATE INDEX IF NOT EXISTS cupboards_world_xzy_index " +
                  "on CUPBOARDS (WORLD, X, Z, Y)";
          stmt.executeUpdate(sql);
          stmt.close();
          db_conn.commit();
          
        } catch ( SQLException | ClassNotFoundException e ) {
            plugin.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            plugin.getLogger().log(Level.WARNING, e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            plugin.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
        plugin.logDebug("Opened database successfully");
    }
    
    public void close(){
        try {
            db_conn.close();
        } catch ( SQLException e ) {
            plugin.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            plugin.getLogger().log(Level.WARNING, e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            plugin.log("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
    }
}
