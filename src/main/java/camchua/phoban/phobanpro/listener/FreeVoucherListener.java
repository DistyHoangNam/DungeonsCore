package camchua.phoban.phobanpro.listener;

import camchua.phoban.phobanpro.DungeonsCore;
import camchua.phoban.phobanpro.game.Game;
import camchua.phoban.phobanpro.manager.FileManager;
import camchua.phoban.phobanpro.utils.ColorUtils;
import camchua.phoban.phobanpro.utils.Messages;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tặng lượt/vé miễn phí theo chu kỳ kể từ khi người chơi vào server (config FreeVoucher).
 */
public class FreeVoucherListener implements Listener {

    private static final Map<UUID, List<BukkitTask>> PLAYER_TASKS = new ConcurrentHashMap<>();

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        FileConfiguration cfg = FileManager.getFileConfig(FileManager.Files.CONFIG);
        ConfigurationSection root = cfg.getConfigurationSection("FreeVoucher");
        if (root == null) return;

        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        cancelTasks(uuid);

        List<BukkitTask> scheduled = new ArrayList<>();
        for (String voucherId : root.getKeys(false)) {
            ConfigurationSection sub = root.getConfigurationSection(voucherId);
            if (sub == null) continue;
            if (!sub.getBoolean("enable", true)) continue;

            String type = sub.getString("type", "");
            if (type == null || type.isBlank()) continue;

            long periodTicks = parseTimeToTicks(sub.getString("time", "1h"));
            if (periodTicks <= 0) continue;

            int amount = Math.max(1, sub.getInt("amount", 1));
            String customMsg = sub.getString("message", null);

            long delayTicks = sub.getBoolean("immediate", false) ? 0L : periodTicks;

            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline()) {
                        cancel();
                        return;
                    }
                    Game.giveTurn(p, type, amount);
                    if (customMsg != null && !customMsg.isBlank()) {
                        String m = ColorUtils.colorize(customMsg
                                .replace("<id>", voucherId)
                                .replace("<type>", type)
                                .replace("<amount>", String.valueOf(amount)));
                        p.sendMessage(m);
                    } else {
                        p.sendMessage(Messages.get("FreeVoucherReceived")
                                .replace("<id>", voucherId)
                                .replace("<type>", type)
                                .replace("<amount>", String.valueOf(amount)));
                    }
                }
            }.runTaskTimer(DungeonsCore.inst(), delayTicks, periodTicks);

            scheduled.add(task);
        }

        if (!scheduled.isEmpty()) {
            PLAYER_TASKS.put(uuid, scheduled);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        cancelTasks(e.getPlayer().getUniqueId());
    }

    private static void cancelTasks(UUID uuid) {
        List<BukkitTask> list = PLAYER_TASKS.remove(uuid);
        if (list == null) return;
        for (BukkitTask t : list) {
            if (t != null && !t.isCancelled()) t.cancel();
        }
    }

    /**
     * Chuỗi dạng: 30s, 5m, 1h, 12h, 1d hoặc số thuần (giây).
     */
    static long parseTimeToTicks(String raw) {
        if (raw == null || raw.isBlank()) return 72000L;
        String s = raw.trim().toLowerCase();
        try {
            long ms;
            if (s.endsWith("ms")) {
                ms = Long.parseLong(s.substring(0, s.length() - 2).trim());
            } else if (s.endsWith("s")) {
                ms = Long.parseLong(s.substring(0, s.length() - 1).trim()) * 1000L;
            } else if (s.endsWith("m")) {
                ms = Long.parseLong(s.substring(0, s.length() - 1).trim()) * 60_000L;
            } else if (s.endsWith("h")) {
                ms = Long.parseLong(s.substring(0, s.length() - 1).trim()) * 3_600_000L;
            } else if (s.endsWith("d")) {
                ms = Long.parseLong(s.substring(0, s.length() - 1).trim()) * 86_400_000L;
            } else {
                ms = Long.parseLong(s) * 1000L;
            }
            long ticks = ms / 50L;
            return Math.max(1L, ticks);
        } catch (NumberFormatException ex) {
            return 72000L;
        }
    }
}
