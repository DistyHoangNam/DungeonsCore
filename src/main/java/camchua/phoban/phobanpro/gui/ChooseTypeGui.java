/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitRunnable
 */
package camchua.phoban.phobanpro.gui;

import camchua.phoban.phobanpro.compat.CompatItems;
import camchua.phoban.phobanpro.DungeonsCore;
import camchua.phoban.phobanpro.game.Game;
import camchua.phoban.phobanpro.gui.PhoBanGui;
import camchua.phoban.phobanpro.manager.FileManager;
import camchua.phoban.phobanpro.utils.ColorUtils;
import camchua.phoban.phobanpro.utils.ItemBuilder;
import camchua.phoban.phobanpro.utils.Messages;
import camchua.phoban.phobanpro.utils.Utils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class ChooseTypeGui
implements Listener {
    /** Hủy mở PhoBanGui cũ nếu spam chọn type khác trước khi hết 1 tick. */
    private static final Map<UUID, BukkitTask> PENDING_PHOBAN_OPEN = new ConcurrentHashMap<>();

    public static void cancelPendingPhoBanOpen(Player p) {
        if (p == null) return;
        BukkitTask t = PENDING_PHOBAN_OPEN.remove(p.getUniqueId());
        if (t != null && !t.isCancelled()) {
            t.cancel();
        }
    }

    public static HashMap<Player, ChooseTypeGui> viewers = new HashMap();
    public ArrayList<Inventory> pages = new ArrayList();
    public int curpage = 0;

    public ChooseTypeGui(Player p, int pg) {
        if (p == null || pg < 0) {
            return;
        }
        this.curpage = pg;
        Inventory page = this.gui();
        List<Integer> typeSlots = this.getTypeSlots(page.getSize());
        int slotIndex = 0;
        for (String t : this.getSortedTypes()) {
            String status = !Game.hasTurn((OfflinePlayer)p, t) ? ColorUtils.colorize(FileManager.getFileConfig(FileManager.Files.GUI).getString("ChooseTypeGui.Format.NoTurn", "")) : ColorUtils.colorize(FileManager.getFileConfig(FileManager.Files.GUI).getString("ChooseTypeGui.Format.Join", ""));
            HashMap<String, List<String>> replace = new HashMap<String, List<String>>();
            replace.put("<type>", Collections.singletonList(t));
            replace.put("<display>", Collections.singletonList(Game.getTypeDisplayForInternalType(t)));
            replace.put("<status>", Collections.singletonList(status));
            ItemStack item = null;
            String key = this.parseType(t);
            item = key.equals("deo-co-con-cac-gi-o-day-het") ? ItemBuilder.build(FileManager.Files.GUI, "ChooseTypeGui.TypeFormat", replace) : ItemBuilder.build(FileManager.Files.PHOBAN, key, replace);
            ItemStack item2 = CompatItems.setKey(item.clone(), "ChooseTypeGui_ClickType", "ChooseType");
            item2 = CompatItems.setKey(item2, "ChooseTypeGui_Type", t);
            item2 = GuiMenuGuard.protect(item2);
            if (typeSlots.isEmpty()) {
                continue;
            }
            if (slotIndex >= typeSlots.size()) {
                this.pages.add(page);
                page = this.gui();
                slotIndex = 0;
            }
            page.setItem(typeSlots.get(slotIndex++), item2);
        }
        this.pages.add(page);
        if (this.curpage >= this.pages.size()) {
            this.curpage = Math.max(0, this.pages.size() - 1);
        }
        p.openInventory(this.pages.get(this.curpage));
        viewers.put(p, this);
    }

    private List<String> getSortedTypes() {
        FileConfiguration phoban = FileManager.getFileConfig(FileManager.Files.PHOBAN);
        ArrayList<String> types = new ArrayList<String>();
        for (String name : Game.listGame()) {
            Game game = Game.getGame(name);
            if (game == null || game.getType() == null || types.contains(game.getType())) continue;
            types.add(game.getType());
        }
        types.sort((left, right) -> {
            String leftKey = this.parseType(left);
            String rightKey = this.parseType(right);
            int priorityCompare = Integer.compare(phoban.getInt(leftKey + ".Priority", 100), phoban.getInt(rightKey + ".Priority", 100));
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            int naturalCompare = Integer.compare(ChooseTypeGui.naturalTypeOrder(left), ChooseTypeGui.naturalTypeOrder(right));
            if (naturalCompare != 0) {
                return naturalCompare;
            }
            return left.compareToIgnoreCase(right);
        });
        return types;
    }

    private List<Integer> getTypeSlots(int inventorySize) {
        ArrayList<Integer> slots = new ArrayList<Integer>();
        for (Integer slot : FileManager.getFileConfig(FileManager.Files.GUI).getIntegerList("ChooseTypeGui.TypeSlot")) {
            if (slot != null && slot >= 0 && slot < inventorySize) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private static int naturalTypeOrder(String value) {
        if (value == null) {
            return Integer.MAX_VALUE;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (digits.length() > 0) {
                break;
            }
        }
        if (digits.length() == 0) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private Inventory gui() {
        FileConfiguration gui = FileManager.getFileConfig(FileManager.Files.GUI);
        int rows = gui.getInt("ChooseTypeGui.Rows");
        if (rows < 3) {
            rows = 3;
        }
        Inventory inv = Bukkit.createInventory(null, (int)(rows * 9), ColorUtils.colorize(gui.getString("ChooseTypeGui.Title", "")));
        ItemStack blank = GuiMenuGuard.protect(ItemBuilder.build(FileManager.Files.GUI, "ChooseTypeGui.Blank", new HashMap<String, List<String>>()));
        Iterator iterator = gui.getIntegerList("ChooseTypeGui.Blank.Slot").iterator();
        while (iterator.hasNext()) {
            int slot = (Integer)iterator.next();
            if (slot >= gui.getInt("ChooseTypeGui.Rows") * 9) continue;
            if (slot <= -1) {
                for (int i = 0; i < gui.getInt("ChooseTypeGui.Rows") * 9; ++i) {
                    inv.setItem(i, blank.clone());
                }
                break;
            }
            inv.setItem(slot, blank.clone());
        }
        ItemStack nextpage = ItemBuilder.build(FileManager.Files.GUI, "ChooseTypeGui.NextPage", new HashMap<String, List<String>>());
        ItemStack nextPage2 = CompatItems.setKey(nextpage.clone(), "ChooseTypeGui_ClickType", "NextPage");
        nextPage2 = GuiMenuGuard.protect(nextPage2);
        Iterator i = gui.getIntegerList("ChooseTypeGui.NextPage.Slot").iterator();
        while (i.hasNext()) {
            int slot = (Integer)i.next();
            if (slot >= gui.getInt("ChooseTypeGui.Rows") * 9) continue;
            if (slot <= -1) {
                for (int i2 = 0; i2 < gui.getInt("ChooseTypeGui.Rows") * 9; ++i2) {
                    inv.setItem(i2, nextPage2);
                }
                break;
            }
            inv.setItem(slot, nextPage2);
        }
        ItemStack previouspage = ItemBuilder.build(FileManager.Files.GUI, "ChooseTypeGui.PreviousPage", new HashMap<String, List<String>>());
        ItemStack previousPage2 = CompatItems.setKey(previouspage.clone(), "ChooseTypeGui_ClickType", "PreviousPage");
        previousPage2 = GuiMenuGuard.protect(previousPage2);
        Iterator iterator2 = gui.getIntegerList("ChooseTypeGui.PreviousPage.Slot").iterator();
        while (iterator2.hasNext()) {
            int slot = (Integer)iterator2.next();
            if (slot >= gui.getInt("ChooseTypeGui.Rows") * 9) continue;
            if (slot <= -1) {
                for (int i3 = 0; i3 < gui.getInt("ChooseTypeGui.Rows") * 9; ++i3) {
                    inv.setItem(i3, previousPage2);
                }
                break;
            }
            inv.setItem(slot, previousPage2);
        }
        iterator2 = gui.getIntegerList("ChooseTypeGui.TypeSlot").iterator();
        while (iterator2.hasNext()) {
            int room_slot = (Integer)iterator2.next();
            inv.setItem(room_slot, new ItemStack(Material.AIR));
        }
        return inv;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && viewers.containsKey(p)) {
            e.setCancelled(true);
            if (GuiMenuGuard.isProtected(e.getOldCursor())) {
                GuiMenuGuard.scrubLater(p);
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        final Player p = (Player)e.getWhoClicked();
        if (viewers.containsKey(p)) {
            GuiMenuGuard.cancelAndScrub(e, p);
            if (e.getClickedInventory() == p.getOpenInventory().getBottomInventory()) {
                return;
            }
            ItemStack click = e.getCurrentItem();
            if (click == null) {
                return;
            }
            if (click.getType().equals((Object)Material.AIR)) {
                return;
            }
            String keyClick = CompatItems.getKey(click, "ChooseTypeGui_ClickType");
            final String typeClick = CompatItems.getKey(click, "ChooseTypeGui_Type");
            if (keyClick == null || keyClick.isEmpty()) {
                return;
            }
            switch (keyClick.toLowerCase()) {
                case "nextpage": {
                    if (!GuiAntiSpam.allow(p, 200L)) {
                        return;
                    }
                    ChooseTypeGui inv = viewers.get(p);
                    if (inv.curpage >= inv.pages.size() - 1) {
                        return;
                    }
                    new ChooseTypeGui(p, inv.curpage + 1);
                    return;
                }
                case "previouspage": {
                    if (!GuiAntiSpam.allow(p, 200L)) {
                        return;
                    }
                    ChooseTypeGui inv = viewers.get(p);
                    if (inv.curpage > 0) {
                        new ChooseTypeGui(p, inv.curpage - 1);
                    }
                    return;
                }
                case "choosetype": {
                    if (typeClick == null || typeClick.isEmpty()) {
                        return;
                    }
                    if (!GuiAntiSpam.allow(p, 350L)) {
                        return;
                    }
                    if (!Game.hasTurn((OfflinePlayer)p, typeClick)) {
                        p.sendMessage(Messages.get("NoTurn"));
                        return;
                    }
                    cancelPendingPhoBanOpen(p);
                    p.closeInventory();
                    BukkitTask task = new BukkitRunnable(){

                        public void run() {
                            PENDING_PHOBAN_OPEN.remove(p.getUniqueId());
                            if (!p.isOnline()) {
                                return;
                            }
                            new PhoBanGui(p, 0, typeClick);
                        }
                    }.runTaskLater((Plugin)DungeonsCore.inst(), 1L);
                    PENDING_PHOBAN_OPEN.put(p.getUniqueId(), task);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Player p = (Player)e.getPlayer();
        viewers.remove(p);
        GuiMenuGuard.scrubLater(p);
    }

    public String parseType(String type) {
        FileConfiguration phoban = FileManager.getFileConfig(FileManager.Files.PHOBAN);
        for (String str : phoban.getKeys(false)) {
            if (!phoban.contains(str + ".Type") || !phoban.getString(str + ".Type", "").equalsIgnoreCase(type)) continue;
            return str;
        }
        return "deo-co-con-cac-gi-o-day-het";
    }

}
