package camchua.phoban.phobanpro.gui;

import camchua.phoban.phobanpro.compat.CompatItems;
import camchua.phoban.phobanpro.DungeonsCore;
import camchua.phoban.phobanpro.game.Game;
import camchua.phoban.phobanpro.integration.MMOItemsRewardHook;
import camchua.phoban.phobanpro.gui.EditorGui;
import camchua.phoban.phobanpro.manager.FileManager;
import camchua.phoban.phobanpro.utils.ItemBuilder;
import camchua.phoban.phobanpro.utils.Messages;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public class RewardGui
implements Listener {
    private static final HashMap<Player, String> viewers = new HashMap();
    public static final HashMap<Player, String> editor = new HashMap();

    public static void open(Player p, String name) {
        ConfigurationSection section;
        Game game = Game.getGame(name);
        FileConfiguration gui = FileManager.getFileConfig(FileManager.Files.GUI);
        File configFile = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + name + ".yml");
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        FileConfiguration room = game == null ? YamlConfiguration.loadConfiguration(configFile) : game.getConfig();
        Inventory inv = Bukkit.createInventory(null, (int)(gui.getInt("RewardGui.Rows") * 9), (String)gui.getString("RewardGui.Title").replace("&", "\u00a7").replace("<n>", name));
        HashMap<String, List<String>> replace = new HashMap<String, List<String>>();
        replace.put("<reward_amount>", Collections.singletonList(String.valueOf(room.getInt("RewardAmount", 0))));
        ConfigurationSection contentSection = gui.getConfigurationSection("RewardGui.Content");
        if (contentSection != null) {
            contentSection.getKeys(false).forEach(content -> {
                ItemStack item = ItemBuilder.build(FileManager.Files.GUI, "RewardGui.Content." + content, replace);
                ItemStack item2 = item.clone();
                if (gui.contains("RewardGui.Content." + content + ".ClickType")) {
                    item2 = CompatItems.setKey(item2, "RewardGui_ClickType", gui.getString("RewardGui.Content." + content + ".ClickType"));
                }
                item2 = GuiMenuGuard.protect(item2);
                Iterator iterator = gui.getIntegerList("RewardGui.Content." + content + ".Slot").iterator();
                while (iterator.hasNext()) {
                    int slot = (Integer)iterator.next();
                    if (slot >= gui.getInt("RewardGui.Rows") * 9) {
                        return;
                    }
                    if (slot <= -1) {
                        for (int i = 0; i < gui.getInt("RewardGui.Rows") * 9; ++i) {
                            inv.setItem(i, item2);
                        }
                        return;
                    }
                    inv.setItem(slot, item2);
                }
            });
        }
        Iterator iterator = gui.getIntegerList("RewardGui.RewardSlot").iterator();
        while (iterator.hasNext()) {
            int reward_slot = (Integer)iterator.next();
            inv.setItem(reward_slot, new ItemStack(Material.AIR));
        }
        if (room.contains("Reward") && (section = room.getConfigurationSection("Reward")) != null) {
            section.getKeys(false).forEach(key -> RewardGui.addRewardItem(room, gui, inv, name, key));
        }
        Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin)DungeonsCore.inst(), () -> {
            p.closeInventory();
            p.openInventory(inv);
            viewers.put(p, name);
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !viewers.containsKey(p)) return;
        if (GuiMenuGuard.isProtected(e.getOldCursor())) {
            e.setCancelled(true);
            GuiMenuGuard.scrubLater(p);
            return;
        }
        FileConfiguration gui = FileManager.getFileConfig(FileManager.Files.GUI);
        List<Integer> rewardSlots = gui.getIntegerList("RewardGui.RewardSlot");
        int topSize = p.getOpenInventory().getTopInventory().getSize();
        for (int slot : e.getRawSlots()) {
            if (slot < topSize && !rewardSlots.contains(slot)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Player p = (Player)e.getWhoClicked();
        if (!viewers.containsKey(p)) return;

        String name = viewers.get(p);
        Game game = Game.getGame(name);
        FileConfiguration gui = FileManager.getFileConfig(FileManager.Files.GUI);
        List<Integer> rewardSlots = gui.getIntegerList("RewardGui.RewardSlot");

        InventoryAction action = e.getAction();
        if (GuiMenuGuard.isProtected(e.getCursor())
                || (e.getClickedInventory() == p.getOpenInventory().getBottomInventory()
                && GuiMenuGuard.isProtected(e.getCurrentItem()))) {
            GuiMenuGuard.cancelAndScrub(e, p);
            return;
        }
        if (action == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);
            GuiMenuGuard.scrubLater(p);
            return;
        }

        if (e.getClickedInventory() == p.getOpenInventory().getBottomInventory()) {
            if (e.isShiftClick()) {
                e.setCancelled(true);
            }
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                e.setCancelled(true);
            }
            return;
        }

        if (e.getClickedInventory() != p.getOpenInventory().getTopInventory()) {
            e.setCancelled(true);
            return;
        }

        if (rewardSlots.contains(e.getSlot())) {
            e.setCancelled(true);

            ItemStack click = e.getCurrentItem();
            ItemStack cursor = e.getCursor();
            boolean slotEmpty = click == null || click.getType() == Material.AIR;
            boolean cursorEmpty = cursor == null || cursor.getType() == Material.AIR;

            if (slotEmpty && !cursorEmpty) {
                if (GuiMenuGuard.isProtected(cursor)) {
                    GuiMenuGuard.scrubLater(p);
                    return;
                }
                String id = UUID.randomUUID().toString();
                File configFile = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + name + ".yml");
                if (!configFile.exists()) {
                    try { configFile.createNewFile(); } catch (Exception ignored) {}
                }
                FileConfiguration room = game == null ? YamlConfiguration.loadConfiguration(configFile) : game.getConfig();
                ItemStack saveItem = cursor.clone();
                saveItem = CompatItems.setKey(saveItem, "id_item_reward", id);
                room.set("Reward." + id + ".Item", (Object)saveItem);
                room.set("Reward." + id + ".Chance", (Object)100);
                FileManager.saveFileConfig((FileConfiguration)room, configFile);
                p.setItemOnCursor(null);
                editor.put(p, name + ":editchance:" + id);
                Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin)DungeonsCore.inst(), () -> {
                    p.closeInventory();
                    p.sendMessage(Messages.get("EditChance"));
                });
                return;
            }

            if (!slotEmpty && cursorEmpty) {
                String id = CompatItems.getKey(click, "RewardGui_ID");
                if (e.isLeftClick()) {
                    editor.put(p, name + ":editchance:" + id);
                    Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin)DungeonsCore.inst(), () -> {
                        p.closeInventory();
                        p.sendMessage(Messages.get("EditChance"));
                    });
                } else if (e.isRightClick()) {
                    File configFile = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + name + ".yml");
                    if (!configFile.exists()) {
                        try { configFile.createNewFile(); } catch (Exception ignored) {}
                    }
                    FileConfiguration room = game == null ? YamlConfiguration.loadConfiguration(configFile) : game.getConfig();
                    room.set("Reward." + id, null);
                    FileManager.saveFileConfig((FileConfiguration)room, configFile);
                    Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin)DungeonsCore.inst(), () -> {
                        p.closeInventory();
                        RewardGui.open(p, name);
                    });
                }
                return;
            }
            return;
        }

        e.setCancelled(true);
        ItemStack click = e.getCurrentItem();
        if (click == null || click.getType() == Material.AIR) return;

        String clickType = CompatItems.getKey(click, "RewardGui_ClickType");
        if (clickType == null || clickType.isEmpty()) return;

        switch (clickType.toLowerCase()) {
            case "rewardamount": {
                editor.put(p, name + ":editrewardamount");
                p.closeInventory();
                p.sendMessage(Messages.get("EditRewardAmount"));
                break;
            }
            case "rewardcommand": {
                String id = UUID.randomUUID().toString();
                editor.put(p, name + ":addrewardcommand:" + id);
                p.closeInventory();
                p.sendMessage(Messages.get("AddRewardCommand"));
                break;
            }
            case "confirm": {
                EditorGui.open(p, name);
                break;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Player p = (Player)e.getPlayer();
        viewers.remove(p);
        GuiMenuGuard.scrubLater(p);
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (editor.containsKey(p)) {
            e.setCancelled(true);
            String mess = ChatColor.stripColor((String)e.getMessage());
            String[] arr = editor.get(p).split(":");
            String name = arr[0];
            String type = arr[1];
            Game game = Game.getGame(name);
            if (mess.equalsIgnoreCase("cancel") || mess.equalsIgnoreCase("complete")) {
                editor.remove(p);
                RewardGui.open(p, name);
                return;
            }
            switch (type.toLowerCase()) {
                case "editrewardamount": {
                    try {
                        int amount = Integer.parseInt(mess);
                        File configFile = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + name + ".yml");
                        if (!configFile.exists()) {
                            try {
                                configFile.createNewFile();
                            }
                            catch (Exception exception) {
                                // empty catch block
                            }
                        }
                        FileConfiguration room = game == null ? YamlConfiguration.loadConfiguration(configFile) : game.getConfig();
                        room.set("RewardAmount", (Object)amount);
                        FileManager.saveFileConfig((FileConfiguration)room, configFile);
                        editor.remove(p);
                        RewardGui.open(p, name);
                    }
                    catch (Exception ex) {
                        if (ex.getMessage().contains("For input string:")) {
                            p.sendMessage(Messages.get("NotInt"));
                            return;
                        }
                        p.sendMessage(Messages.get("Error").replace("<error>", ex.getMessage()));
                        ex.printStackTrace();
                    }
                    break;
                }
                case "addrewardcommand": {
                    String id = arr[2];
                    File configFile = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + name + ".yml");
                    if (!configFile.exists()) {
                        try {
                            configFile.createNewFile();
                        }
                        catch (Exception room) {
                            // empty catch block
                        }
                    }
                    FileConfiguration room = game == null ? YamlConfiguration.loadConfiguration(configFile) : game.getConfig();
                    room.set("Reward." + id + ".Command", (Object)mess);
                    room.set("Reward." + id + ".Chance", (Object)100);
                    FileManager.saveFileConfig((FileConfiguration)room, configFile);
                    editor.remove(p);
                    editor.put(p, name + ":editchance:" + id);
                    p.sendMessage(Messages.get("EditChance"));
                    break;
                }
                case "editchance": {
                    try {
                        String id = arr[2];
                        int chance = Integer.parseInt(mess);
                        File configFile = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + name + ".yml");
                        if (!configFile.exists()) {
                            try {
                                configFile.createNewFile();
                            }
                            catch (Exception exception) {
                                // empty catch block
                            }
                        }
                        FileConfiguration room = game == null ? YamlConfiguration.loadConfiguration(configFile) : game.getConfig();
                        room.set("Reward." + id + ".Chance", (Object)chance);
                        FileManager.saveFileConfig((FileConfiguration)room, configFile);
                        editor.remove(p);
                        RewardGui.open(p, name);
                        break;
                    }
                    catch (Exception ex) {
                        if (ex.getMessage().contains("For input string:")) {
                            p.sendMessage(Messages.get("NotInt"));
                            return;
                        }
                        p.sendMessage(Messages.get("Error").replace("<error>", ex.getMessage()));
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    private static void addRewardItem(FileConfiguration room, FileConfiguration gui, Inventory inv, String roomName, String s) {
        boolean commandReward = room.contains("Reward." + s + ".Command");
        ItemStack savedItem = room.getItemStack("Reward." + s + ".Item", new ItemStack(Material.PAPER));
        ItemStack cfgItem = commandReward
                ? new ItemStack(Material.PAPER)
                : MMOItemsRewardHook.resolve(savedItem, roomName, s);
        ItemStack item = cfgItem.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ItemStack originalItem;
            ItemMeta originalMeta;
            if (commandReward) {
                String command = room.getString("Reward." + s + ".Command");
                meta.setDisplayName("\u00a7f/" + command);
            }
            ArrayList<String> baseLore = !commandReward
                    && (originalMeta = (originalItem = cfgItem).getItemMeta()) != null
                    && originalMeta.getLore() != null
                    ? new ArrayList<>(originalMeta.getLore()) : new ArrayList<>();
            ArrayList<String> cleanedLore = new ArrayList<String>();
            for (String line : baseLore) {
                String plain = ChatColor.stripColor((String)(line == null ? "" : line)).toLowerCase();
                if (plain.isEmpty() || plain.contains("chance") && plain.contains("%") || plain.contains("left-click") || plain.contains("right-click")) continue;
                cleanedLore.add(line);
            }
            ArrayList<String> lores = new ArrayList<String>(cleanedLore);
            lores.add("\u00a7r");
            for (String format : gui.getStringList("RewardGui.Format")) {
                lores.add(format.replace("&", "\u00a7").replace("<chance>", String.valueOf(room.getInt("Reward." + s + ".Chance"))));
            }
            meta.setLore(lores);
            item.setItemMeta(meta);
            item = CompatItems.setKey(item, "id_item_reward", s);
        }
        ItemStack item2 = CompatItems.setKey(item, "RewardGui_ID", s);
        item2 = GuiMenuGuard.protect(item2);
        inv.addItem(new ItemStack[]{item2});
    }
}
