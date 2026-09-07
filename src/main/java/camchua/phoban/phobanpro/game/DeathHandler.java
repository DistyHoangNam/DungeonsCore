package camchua.phoban.phobanpro.game;

import camchua.phoban.phobanpro.manager.FileManager;
import camchua.phoban.phobanpro.utils.Messages;
import camchua.phoban.phobanpro.utils.Utils;
import camchua.phoban.phobanpro.DungeonsCore;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class DeathHandler {

    private DeathHandler() {}

    public static boolean isProtected(Player p) {
        if (isEntryProtected(p)) return true;
        UUID uuid = p.getUniqueId();
        if (!GameStatistic.protect.containsKey(uuid)) return false;
        long elapsed = (System.currentTimeMillis() - GameStatistic.protect.get(uuid)) / 1000L;
        FileConfiguration cfg = FileManager.getFileConfig(FileManager.Files.CONFIG);
        int protectTime = cfg.getInt("Settings.RespawnProtect") + cfg.getInt("Settings.Respawn.Countdown", 3);
        return elapsed <= protectTime;
    }

    public static boolean isEntryProtected(Player p) {
        Long until = GameStatistic.entryProtectUntil.get(p.getUniqueId());
        if (until == null) return false;
        if (System.currentTimeMillis() <= until) return true;
        GameStatistic.entryProtectUntil.remove(p.getUniqueId());
        return false;
    }

    public static long getEntryProtectRemainingMillis(Player p) {
        Long until = GameStatistic.entryProtectUntil.get(p.getUniqueId());
        if (until == null) return 0L;
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0L) {
            GameStatistic.entryProtectUntil.remove(p.getUniqueId());
            return 0L;
        }
        return remaining;
    }

    public static boolean isDuplicateDeath(Player p) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        if (!GameStatistic.lastDeath.containsKey(uuid)) {
            GameStatistic.lastDeath.put(uuid, now);
            return false;
        }
        if (now - GameStatistic.lastDeath.get(uuid) < 1000L) return true;
        GameStatistic.lastDeath.put(uuid, now);
        return false;
    }

    public static void handleDeath(Player p, Runnable cancelEvent) {
        if (!PlayerData.contains(p)) return;
        if (isDuplicateDeath(p)) return;

        FileConfiguration cfg = FileManager.getFileConfig(FileManager.Files.CONFIG);
        if (cfg.getBoolean("Settings.Respawn.Enable", true)) {
            handleRespawn(p, cancelEvent);
        } else {
            handleNoRespawn(p, cancelEvent);
        }
    }

    private static void handleRespawn(Player p, Runnable cancelEvent) {
        PlayerData data = PlayerData.get(p);
        if (data == null) return;
        Game game = data.getGame();
        Location deathLocation = p.getLocation().clone();

        if (!data.canRespawn()) {
            cancelEvent.run();
            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
            p.setGameMode(GameMode.SURVIVAL);
            deductPoints(p, "Point.Lose");
            p.sendMessage(Messages.get("NoMoreRespawn"));
            game.leave(p, false, false, false);
            sendToSpawnCommand(p);
            return;
        }

        deductPoints(p, "Point.Death");
        cancelEvent.run();
        data.minusRespawn();
        data.beginRespawn(deathLocation,
                FileManager.getFileConfig(FileManager.Files.CONFIG).getInt("Settings.Respawn.Countdown", 3));
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setGameMode(GameMode.SPECTATOR);
        p.teleport(data.getLastDeath());
        GameStatistic.protect.put(p.getUniqueId(), System.currentTimeMillis());
    }

    private static void handleNoRespawn(Player p, Runnable cancelEvent) {
        cancelEvent.run();
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        PlayerData data = PlayerData.get(p);
        if (data != null) {
            deductPoints(p, "Point.Lose");
            data.getGame().leave(p, false, false, false);
        }
        sendToSpawnCommand(p);
    }

    private static void sendToSpawnCommand(Player p) {
        Bukkit.getScheduler().runTaskLater(DungeonsCore.inst(),
                () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + p.getName()), 1L);
    }

    /**
     * Trừ điểm và gửi message cho player.
     * @param configKey "Point.Death" hoặc "Point.Lose"
     */
    private static void deductPoints(Player p, String configKey) {
        FileConfiguration cfg = FileManager.getFileConfig(FileManager.Files.CONFIG);
        FileConfiguration dataF = FileManager.getFileConfig(FileManager.Files.DATA);
        String uuid = p.getUniqueId().toString();
        int curPoint = dataF.getInt(uuid + ".Point", 0);
        int point = Utils.parseInt(cfg.getString(configKey, "0"));
        if (point <= 0) return;
        if (curPoint - point < 0 && !cfg.getBoolean("Point.AllowNegative")) point = Math.max(0, curPoint);
        dataF.set(uuid + ".Point", curPoint - point);
        dataF.set(uuid + ".Name", p.getName());
        FileManager.saveFileConfig(dataF, FileManager.Files.DATA);

        String msgKey = configKey.equals("Point.Lose") ? "PointLose" : "PointDeath";
        p.sendMessage(Messages.get(msgKey).replace("<point>", String.valueOf(point)));
    }
}
