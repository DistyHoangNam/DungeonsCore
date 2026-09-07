package camchua.phoban.phobanpro.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.UUID;

public class EntityData {

    private static final HashMap<Entity, EntityData> data = new HashMap<>();
    private static final long LAST_DAMAGER_TIMEOUT_MS = 30_000L;

    private final Entity entity;
    private final Game game;
    private final String mobKey;
    private UUID lastDamagerUuid;
    private long lastDamageAt;

    public static HashMap<Entity, EntityData> data() { return data; }

    public EntityData(Entity entity, Game game) {
        this(entity, game, null);
    }

    public EntityData(Entity entity, Game game, String mobKey) {
        this.entity = entity;
        this.game = game;
        this.mobKey = mobKey;
    }

    public Entity getEntity() { return entity; }
    public Game getGame() { return game; }
    public String getMobKey() { return mobKey; }

    public void recordDamage(Player player) {
        if (player == null) return;
        PlayerData playerData = PlayerData.get(player);
        if (playerData == null || playerData.getGame() != game) return;
        lastDamagerUuid = player.getUniqueId();
        lastDamageAt = System.currentTimeMillis();
    }

    public Player getValidLastDamager() {
        if (lastDamagerUuid == null) return null;
        if (System.currentTimeMillis() - lastDamageAt > LAST_DAMAGER_TIMEOUT_MS) return null;
        Player player = Bukkit.getPlayer(lastDamagerUuid);
        if (player == null) return null;
        PlayerData playerData = PlayerData.get(player);
        if (playerData == null || playerData.getGame() != game) return null;
        return player;
    }

    public void clearDamageCredit() {
        lastDamagerUuid = null;
        lastDamageAt = 0L;
    }

    public long getLastDamageAt() { return lastDamageAt; }
}
