package camchua.phoban.phobanpro.listener;

import camchua.phoban.phobanpro.game.PlayerData;
import camchua.phoban.phobanpro.gui.ChooseTypeGui;
import camchua.phoban.phobanpro.gui.GuiAntiSpam;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        GuiAntiSpam.clear(p.getUniqueId());
        ChooseTypeGui.cancelPendingPhoBanOpen(p);
        PlayerData data = PlayerData.get(p);
        if (data != null) data.getGame().leave(p, true, true, true);
    }
}
