package camchua.phoban.phobanpro.game;

import camchua.phoban.phobanpro.manager.FileManager;
import camchua.phoban.phobanpro.utils.Utils;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;

public class PlayerData {

    private static final HashMap<UUID, PlayerData> data = new HashMap<>();

    private final Player player;
    private final Location location;
    private final Game game;
    private int respawn;
    private Location lastDeathLocation;
    private boolean respawning = false;
    private int respawnCountdown = 0;
    private long lastBossFightTime = 0L;

    public static HashMap<UUID, PlayerData> data() { return data; }

    public static PlayerData get(Player p) {
        return data.get(p.getUniqueId());
    }

    public static void put(Player p, PlayerData pd) {
        data.put(p.getUniqueId(), pd);
    }

    public static void remove(Player p) {
        data.remove(p.getUniqueId());
    }

    public static boolean contains(Player p) {
        return data.containsKey(p.getUniqueId());
    }

    public PlayerData(Player p, Game g, Location loc) {
        this.player = p;
        this.location = loc;
        this.game = g;
        this.respawn = Utils.getRespawnTurn(p);
    }

    public Player getPlayer() { return player; }
    public Game getGame() { return game; }
    public Location getLocation() { return location; }
    public int getRespawn() { return respawn; }
    public boolean canRespawn() { return respawn > 0; }
    public void minusRespawn() { respawn = Math.max(0, respawn - 1); }
    public int remainRespawn() { return respawn; }
    public boolean hasDeath() { return lastDeathLocation != null; }
    public Location getLastDeath() { return lastDeathLocation; }
    public boolean isRespawning() { return respawning; }
    public int getRespawnCountdown() { return respawnCountdown; }
    public void setLastBossFightTime(long time) { lastBossFightTime = time; }

    public void beginRespawn(Location deathLoc, int countdown) {
        lastDeathLocation = deathLoc;
        respawning = true;
        respawnCountdown = Math.max(0, countdown);
    }

    public void finishRespawn() {
        respawning = false;
        respawnCountdown = 0;
    }

    public void clearDeathCheckpoint() {
        lastDeathLocation = null;
    }

    public void tickRespawnCountdown() {
        respawnCountdown = Math.max(0, respawnCountdown - 1);
    }

    public boolean isBossFightCooldown() {
        if (lastBossFightTime == 0L) return false;
        int cooldownMin = FileManager.getFileConfig(FileManager.Files.CONFIG).getInt("WaitingRoom.BossRespawnTime", 15);
        return System.currentTimeMillis() - lastBossFightTime < (long) cooldownMin * 60_000L;
    }

    public int getBossFightCooldownRemaining() {
        if (lastBossFightTime == 0L) return 0;
        int cooldownMin = FileManager.getFileConfig(FileManager.Files.CONFIG).getInt("WaitingRoom.BossRespawnTime", 15);
        long remaining = lastBossFightTime + (long) cooldownMin * 60_000L - System.currentTimeMillis();
        return remaining <= 0 ? 0 : (int) (remaining / 60_000L);
    }
}
