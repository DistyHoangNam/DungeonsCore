package camchua.phoban.phobanpro.game;

import java.util.HashMap;
import java.util.UUID;

public final class GameStatistic {
    public static final HashMap<UUID, Long> protect = new HashMap<>();
    public static final HashMap<UUID, Long> entryProtectUntil = new HashMap<>();
    public static final HashMap<UUID, Long> lastDeath = new HashMap<>();

    private GameStatistic() {}
}
