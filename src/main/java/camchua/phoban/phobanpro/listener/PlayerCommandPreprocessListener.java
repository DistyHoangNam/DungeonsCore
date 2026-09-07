package camchua.phoban.phobanpro.listener;

import camchua.phoban.phobanpro.DungeonsCore;
import camchua.phoban.phobanpro.game.Game;
import camchua.phoban.phobanpro.game.PlayerData;
import camchua.phoban.phobanpro.gui.RewardGui;
import camchua.phoban.phobanpro.manager.FileManager;
import camchua.phoban.phobanpro.utils.Messages;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.regex.Pattern;

public class PlayerCommandPreprocessListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandAlias(PlayerCommandPreprocessEvent e) {
        String[] parts = e.getMessage().substring(1).split(" ", 2);
        String cmd = parts[0];
        String rest = parts.length > 1 ? parts[1] : "";
        Player p = e.getPlayer();

        String addRewardName = DungeonsCore.addRewardsMap.get(p.getName());
        if (addRewardName != null) {
            if (cmd.equalsIgnoreCase("addRewards")) {
                e.setMessage("/phoban addrewards " + addRewardName);
                return;
            } else if (cmd.equalsIgnoreCase("complete")) {
                RewardGui.open(p, addRewardName);
                DungeonsCore.addRewardsMap.remove(p.getName());
                e.setCancelled(true);
                return;
            }
        }

        for (String alias : FileManager.getFileConfig(FileManager.Files.CONFIG).getStringList("Settings.CommandAliases")) {
            if (cmd.equalsIgnoreCase(alias)) {
                e.setMessage(rest.isEmpty() ? "/phobanpro" : "/phobanpro " + rest);
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandBlock(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!PlayerData.contains(p)) return;

        PlayerData data = PlayerData.get(p);
        if (isStaleDungeonState(p, data)) {
            PlayerData.remove(p);
            return;
        }

        String cmd = e.getMessage().substring(1).toLowerCase();
        String[] parts = cmd.split("\\s+");
        if (isDungeonControlCommand(parts)) return;

        boolean blocked = true;
        for (String whitelist : FileManager.getFileConfig(FileManager.Files.CONFIG).getStringList("Settings.CommandWhitelist")) {
            if (Pattern.compile(whitelist.toLowerCase()).matcher(cmd).matches()) {
                blocked = false;
                break;
            }
        }
        if (blocked) {
            e.setCancelled(true);
            p.sendMessage(Messages.get("NoCommand"));
        }
    }

    private boolean isStaleDungeonState(Player p, PlayerData data) {
        if (data == null) return true;
        Game game = data.getGame();
        return game == null || !Game.game().containsValue(game) || !game.getPlayers().contains(p);
    }

    private boolean isDungeonControlCommand(String[] parts) {
        if (parts.length != 2) return false;
        if (!parts[0].equals("phoban") && !parts[0].equals("phobanpro")) return false;
        return parts[1].equals("start") || parts[1].equals("leave");
    }
}
