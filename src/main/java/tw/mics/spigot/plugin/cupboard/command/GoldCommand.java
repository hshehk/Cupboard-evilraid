package tw.mics.spigot.plugin.cupboard.command;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;
import tw.mics.spigot.plugin.cupboard.Cupboard;
import tw.mics.spigot.plugin.cupboard.config.Config;
import tw.mics.spigot.plugin.cupboard.utils.SchedulerCompat;

public class GoldCommand implements CommandExecutor {
    private static final long CONFIRM_EXPIRE_TICKS = 600L;

    private final Cupboard plugin;
    private final HashMap<String, ConfirmEntry> confirmList;

    private static final class ConfirmEntry {
        private final SchedulerCompat.TaskHandle taskHandle;
        private final String receiverUuid;

        private ConfirmEntry(SchedulerCompat.TaskHandle taskHandle, String receiverUuid) {
            this.taskHandle = taskHandle;
            this.receiverUuid = receiverUuid;
        }
    }

    public GoldCommand(Cupboard i){
        this.plugin = i;
        confirmList = new HashMap<String, ConfirmEntry>();
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§4this command must run on player");
            return true;
        }

        Player playerSender = (Player) sender;
        List<String> enable_world = Config.ENABLE_WORLD.getStringList();
        if(!enable_world.contains(playerSender.getWorld().getName())){
            sender.sendMessage(ChatColor.RED + "這個世界的金磚並未啟用");
            return true;
        }
        String senderUuid = playerSender.getUniqueId().toString();
        if(args.length > 0){
            if(args[0].equals("nearest") && args.length == 1){
                sender.sendMessage(ChatColor.GREEN + "開始尋找...");
                plugin.cupboards.findNearest(playerSender);
                return true;
            }

            boolean haveConfirm = confirmList.containsKey(senderUuid);

            if(args[0].equals("confirm") && args.length == 1){
                if(!haveConfirm){
                    sender.sendMessage(ChatColor.RED + "沒有任何確認");
                    return true;
                }
                ConfirmEntry entry = confirmList.remove(senderUuid);
                if(entry != null && entry.taskHandle != null){
                    entry.taskHandle.cancel();
                }

                final String receiverUuid = entry == null ? null : entry.receiverUuid;
                if(receiverUuid == null){
                    sender.sendMessage(ChatColor.RED + "確認資料已失效");
                    return true;
                }

                SchedulerCompat.runForEntity(plugin, playerSender, () -> {
                    Player receiver = Bukkit.getPlayer(UUID.fromString(receiverUuid));
                    if(receiver == null){
                        sender.sendMessage(ChatColor.RED + "該玩家目前不在線上");
                        return;
                    }
                    sender.sendMessage(ChatColor.GREEN + "開始執行授權賦予動作...");
                    plugin.cupboards.giveAcceee(senderUuid, receiverUuid, playerSender.getLocation().clone());
                    //完成訊息由 CupboardsData.giveAcceee 在 DB 寫入成功後統一發送，這裡不再重複發
                }, 0L);
                return true;
            }

            if(args[0].equals("give") && args.length == 2){
                if(haveConfirm){
                    sender.sendMessage(ChatColor.RED + "您還有確認尚未完成, 無法使用需要確認的指令");
                    sender.sendMessage(ChatColor.RED + "請等待確認失效或執行確認.");
                    return true;
                }
                Player player = Bukkit.getPlayer(args[1]);
                if(player == null){
                    sender.sendMessage(ChatColor.RED + "該玩家目前不在線上");
                    return true;
                }
                if(player == sender){
                    sender.sendMessage(ChatColor.RED + "無法賦予給自己");
                    return true;
                }
                Location giverLocation = playerSender.getLocation();
                Location receiverLocation = player.getLocation();
                if(
                        giverLocation.getWorld() != receiverLocation.getWorld() || 
                        giverLocation.distance(receiverLocation) > 20
                ){
                    sender.sendMessage(ChatColor.RED + "賦予需要雙方在 20 格內才能進行");
                    return true;
                }
                SchedulerCompat.TaskHandle handle = SchedulerCompat.runForEntity(plugin, playerSender, () -> {
                    confirmList.remove(senderUuid);
                    sender.sendMessage(ChatColor.RED + "確認已失效");
                }, CONFIRM_EXPIRE_TICKS);
                confirmList.put(senderUuid, new ConfirmEntry(handle, player.getUniqueId().toString()));
                sender.sendMessage(ChatColor.GREEN + "即將把附近 ±200 格內已授權金磚賦予給 " + player.getDisplayName() + ", 確定要執行?");
                sender.sendMessage(ChatColor.GREEN + "如果確定執行請在 30 秒內輸入 /gold confirm");
                sender.sendMessage(ChatColor.RED + "警告: 這並不會讓自己的授權消失, 對方將擁有完整授權, 此動作無法取消.");
                return true;
            }
        }

        sender.sendMessage(ChatColor.GOLD + "金磚管理指令:");
        sender.sendMessage(ChatColor.YELLOW + "/gold give <player>  - 把附近 ±200 格內已授權金磚賦予該玩家");
        sender.sendMessage(ChatColor.YELLOW + "/gold nearest        - 找出附近 ±20 格內最近的已授權金磚");
        return true;
    }

}
