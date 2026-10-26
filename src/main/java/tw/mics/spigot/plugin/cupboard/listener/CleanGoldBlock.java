package tw.mics.spigot.plugin.cupboard.listener;

import org.bukkit.ChunkSnapshot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.world.ChunkLoadEvent;

import tw.mics.spigot.plugin.cupboard.Cupboard;
import tw.mics.spigot.plugin.cupboard.utils.SchedulerCompat;

public class CleanGoldBlock extends MyListener {
    public CleanGoldBlock(Cupboard instance) {
        super(instance);
    }
    
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event){
        if(event.isNewChunk()){
            final ChunkSnapshot snapshot = event.getChunk().getChunkSnapshot();
            SchedulerCompat.runAsync(Cupboard.getInstance(), new Runnable(){
                @Override
                public void run() {
                    plugin.cupboards.cleanNotExistCupboard(snapshot);
                }
            });
        }
    }
}
