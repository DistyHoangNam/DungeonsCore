package camchua.phoban.phobanpro.gui;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giới hạn spam click GUI (join / chọn type).
 */
public final class GuiAntiSpam {

    private static final Map<UUID, Long> LAST_ACTION_MS = new ConcurrentHashMap<>();

    private GuiAntiSpam() {}

    /** @return true nếu được phép xử lý (đã ghi nhận thời điểm) */
    public static boolean allow(Player p, long cooldownMs) {
        long now = System.currentTimeMillis();
        UUID u = p.getUniqueId();
        Long last = LAST_ACTION_MS.get(u);
        if (last != null && now - last < cooldownMs) {
            return false;
        }
        LAST_ACTION_MS.put(u, now);
        return true;
    }

    public static void clear(UUID uuid) {
        LAST_ACTION_MS.remove(uuid);
    }
}
