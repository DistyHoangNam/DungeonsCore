package camchua.phoban.phobanpro.gui;

import camchua.phoban.phobanpro.manager.FileManager;
import camchua.phoban.phobanpro.utils.ColorUtils;
import camchua.phoban.phobanpro.utils.ItemBuilder;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class TopGui implements Listener {

    private static final List<Player> viewers = new ArrayList<>();

    public static void open(Player p) {
        FileConfiguration gui = FileManager.getFileConfig(FileManager.Files.GUI_TOP);
        FileConfiguration data = FileManager.getFileConfig(FileManager.Files.DATA);
        Inventory inv = Bukkit.createInventory(null, gui.getInt("Rows") * 9,
                ColorUtils.colorize(gui.getString("Title")));

        for (String content : gui.getConfigurationSection("Content").getKeys(false)) {
            ItemStack item = ItemBuilder.build(FileManager.Files.GUI_TOP, "Content." + content, new HashMap<>());
            item = GuiMenuGuard.protect(item);
            for (int slot : gui.getIntegerList("Content." + content + ".Slot")) {
                if (slot >= gui.getInt("Rows") * 9) continue;
                if (slot <= -1) {
                    for (int i = 0; i < gui.getInt("Rows") * 9; i++) inv.setItem(i, item);
                    break;
                }
                inv.setItem(slot, item);
            }
        }

        Map<String, Integer> pointData = new LinkedHashMap<>();
        Map<String, String> nameMap = new HashMap<>();
        for (String uuid : data.getKeys(false)) {
            int point = data.getInt(uuid + ".Point", 0);
            String name = data.getString(uuid + ".Name", uuid);
            if (point <= 0) continue;
            pointData.put(uuid, point);
            nameMap.put(uuid, name);
        }

        List<Map.Entry<String, Integer>> pointList = new ArrayList<>(pointData.entrySet());
        pointList.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        HashMap<String, List<String>> replace = new HashMap<>();
        List<Integer> slots = gui.getIntegerList("TopSlot");
        for (int i = 0; i < Math.min(slots.size(), pointList.size()); i++) {
            replace.clear();
            int slot = slots.get(i);
            String uuid = pointList.get(i).getKey();
            String name = nameMap.getOrDefault(uuid, uuid);
            int point = pointList.get(i).getValue();
            replace.put("<top>", List.of(String.valueOf(i + 1)));
            replace.put("<player>", List.of(name));
            replace.put("<point>", List.of(String.valueOf(point)));
            ItemStack item = ItemBuilder.build(FileManager.Files.GUI_TOP, "TopFormat", replace);
            inv.setItem(slot, GuiMenuGuard.protect(ItemBuilder.skull(item, name)));
        }

        replace.clear();
        String playerUuid = p.getUniqueId().toString();
        int rank = 0;
        if (pointData.containsKey(playerUuid)) {
            for (int i = 0; i < pointList.size(); i++) {
                if (pointList.get(i).getKey().equals(playerUuid)) { rank = i + 1; break; }
            }
        }
        replace.put("<top>", List.of(String.valueOf(rank)));
        replace.put("<player>", List.of(p.getName()));
        replace.put("<point>", List.of(String.valueOf(data.getInt(playerUuid + ".Point", 0))));
        ItemStack item = ItemBuilder.build(FileManager.Files.GUI_TOP, "MyTopFormat", replace);
        inv.setItem(gui.getInt("MyTopSlot"), GuiMenuGuard.protect(item));

        p.openInventory(inv);
        if (!viewers.contains(p)) viewers.add(p);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && viewers.contains(p)) {
            e.setCancelled(true);
            if (GuiMenuGuard.isProtected(e.getOldCursor())) {
                GuiMenuGuard.scrubLater(p);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && viewers.contains(p)) {
            GuiMenuGuard.cancelAndScrub(e, p);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            viewers.remove(p);
            GuiMenuGuard.scrubLater(p);
        }
    }
}
