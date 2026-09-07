package camchua.phoban.phobanpro.gui;

import camchua.phoban.phobanpro.DungeonsCore;
import camchua.phoban.phobanpro.compat.CompatItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class GuiMenuGuard {

    private static final String MENU_ITEM_KEY = "DungeonsCore_MenuItem";

    private GuiMenuGuard() {}

    public static ItemStack protect(ItemStack item) {
        if (isEmpty(item)) {
            return item;
        }
        return CompatItems.setKey(item, MENU_ITEM_KEY, "true");
    }

    public static boolean isProtected(ItemStack item) {
        return !isEmpty(item) && "true".equalsIgnoreCase(CompatItems.getKey(item, MENU_ITEM_KEY));
    }

    public static void cancelAndScrub(InventoryClickEvent e, Player player) {
        e.setCancelled(true);
        if (isProtected(e.getCursor()) || isProtected(e.getCurrentItem())) {
            scrubLater(player);
        }
    }

    public static void scrubLater(Player player) {
        Bukkit.getScheduler().runTask(DungeonsCore.inst(), () -> scrub(player));
    }

    public static void scrub(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (isProtected(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isProtected(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
        player.updateInventory();
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }
}
