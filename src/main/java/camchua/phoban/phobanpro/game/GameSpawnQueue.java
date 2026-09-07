package camchua.phoban.phobanpro.game;

import org.bukkit.Location;

public class GameSpawnQueue {

    private final String mobKey;
    private int remaining;
    private final Location location;

    public GameSpawnQueue(String mobKey, int remaining, Location location) {
        this.mobKey = mobKey;
        this.remaining = Math.max(0, remaining);
        this.location = location;
    }

    public void decrementRemaining() { remaining = Math.max(0, remaining - 1); }
    public boolean hasRemaining() { return remaining > 0; }
    public String getMobKey() { return mobKey; }
    public int getRemaining() { return remaining; }
    public Location getLocation() { return location; }
}
