/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.block.Block
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.event.player.AsyncPlayerChatEvent
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemFlag
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
import camchua.phoban.phobanpro.gui.RewardGui;
import camchua.phoban.phobanpro.manager.FileManager;
import camchua.phoban.phobanpro.utils.ItemBuilder;
import camchua.phoban.phobanpro.utils.Messages;
import camchua.phoban.phobanpro.utils.Utils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class EditorGui
implements Listener {
    private static final HashMap<Player, String> viewers = new HashMap();
    private static final HashMap<Player, String> editor = new HashMap();

    public static void open(Player p, String name) {
        File configFile;
        FileConfiguration gui = FileManager.getFileConfig(FileManager.Files.GUI);
        Game game = Game.getGame(name);
        File roomFolder = new File(DungeonsCore.inst().getDataFolder(), "room");
        if (!roomFolder.exists()) {
            roomFolder.mkdirs();
        }
        if (!(configFile = new File(roomFolder, name + ".yml")).exists()) {
            try {
                configFile.createNewFile();
            }
            catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        FileConfiguration room = game == null ? YamlConfiguration.loadConfiguration(configFile) : game.getConfig();
        Inventory inv = Bukkit.createInventory(null, (int)(gui.getInt("EditorGui.Rows") * 9), (String)gui.getString("EditorGui.Title", "").replace("&", "\u00a7").replace("<n>", name));
        HashMap<String, List<String>> replace = new HashMap<>();
        replace.put("<max_players>", Collections.singletonList(String.valueOf(room.getInt("Player", 0))));
        replace.put("<prefix>", Collections.singletonList(room.getString("Prefix", "").replace("&", "\u00a7")));
        replace.put("<time>", Collections.singletonList(String.valueOf(room.getInt("Time", 0))));
        for (int i = 1; i <= 10; ++i) {
            ArrayList<String> lores = new ArrayList<String>();
            if (room.contains("Mob" + i)) {
                for (String s : room.getConfigurationSection("Mob" + i).getKeys(false)) {
                    String type = room.getString("Mob" + i + "." + s + ".Type", "null");
                    int amount = room.getInt("Mob" + i + "." + s + ".Amount", 0);
                    String format = gui.getString("EditorGui.MobLoreFormat", "").replace("&", "\u00a7").replace("<mobs>", type).replace("<amount>", String.valueOf(amount));
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append(format).append("\u00a7f, \u00a7r");
                    for (String child : room.getConfigurationSection("Mob" + i + "." + s).getKeys(false)) {
                        if (child.equalsIgnoreCase("Location") || child.equalsIgnoreCase("Type") || child.equalsIgnoreCase("Amount") || child.equalsIgnoreCase("Time")) continue;
                        String childType = room.getString("Mob" + i + "." + s + "." + child + ".Type");
                        int childAmount = room.getInt("Mob" + i + "." + s + "." + child + ".Amount");
                        String childFormat = gui.getString("EditorGui.SecondaryMobLoreFormat", "&fx<amount> &e<mobs>").replace("&", "\u00a7").replace("<mobs>", childType).replace("<amount>", "" + childAmount);
                        stringBuilder.append(childFormat).append("\u00a7f, \u00a7r");
                    }
                    String result = stringBuilder.toString();
                    lores.add(result.substring(0, result.length() - 6));
                }
            }
            replace.put("<mob" + i + ">", lores);
        }
        replace.put("<boss_type>", Collections.singletonList(room.getString("Boss.Type", "null")));
        replace.put("<boss_amount>", Collections.singletonList(String.valueOf(room.getInt("Boss.Amount", 0))));
        Location spawn = (Location)room.get("Spawn");
        replace.put("<spawn>", Collections.singletonList(spawn == null ? "" : spawn.getBlockX() + "," + spawn.getBlockY() + "," + spawn.getBlockZ() + "," + spawn.getWorld().getName()));
        String typeStr = room.getString("Type", "");
        String dispStr = room.getString("Display", "");
        if (dispStr == null || dispStr.isBlank()) {
            dispStr = typeStr;
        }
        replace.put("<type>", Arrays.asList(typeStr, ""));
        replace.put("<display>", Arrays.asList(dispStr, ""));
        ConfigurationSection section = gui.getConfigurationSection("EditorGui.Content");
        if (section != null) {
            section.getKeys(false).forEach(content -> {
                ItemStack item = ItemBuilder.build(FileManager.Files.GUI, "EditorGui.Content." + content, replace);
                if (gui.contains("EditorGui.Content." + content + ".ClickType")) {
                    ItemMeta meta;
                    String mob;
                    String clickType = gui.getString("EditorGui.Content." + content + ".ClickType");
                    if (clickType != null && clickType.contains("EditMob") && EditorGui.isEdited(name, mob = clickType.replace("Edit", ""))) {
                        item = ItemBuilder.build(FileManager.Files.GUI, "EditorGui.Content." + content + ".Edited", replace);
                    }
                    if (clickType != null && clickType.equalsIgnoreCase("EditBoss") && (meta = item.getItemMeta()) != null) {
                        meta.addEnchant(Utils.getUnbreakingEnchantment(), 10, true);
                        meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
                        item.setItemMeta(meta);
                    }
                }
                ItemStack item2 = item.clone();
                if (gui.contains("EditorGui.Content." + content + ".ClickType")) {
                    item2 = CompatItems.setKey(item2, "EditorGui_ClickType", gui.getString("EditorGui.Content." + content + ".ClickType"));
                }
                item2 = GuiMenuGuard.protect(item2);
                Iterator iterator = gui.getIntegerList("EditorGui.Content." + content + ".Slot").iterator();
                while (iterator.hasNext()) {
                    int slot = (Integer)iterator.next();
                    if (slot >= gui.getInt("EditorGui.Rows") * 9) continue;
                    if (slot <= -1) {
                        for (int i = 0; i < gui.getInt("EditorGui.Rows") * 9; ++i) {
                            inv.setItem(i, item2);
                        }
                        break;
                    }
                    inv.setItem(slot, item2);
                }
            });
        }
        Bukkit.getScheduler().scheduleSyncDelayedTask((Plugin)DungeonsCore.inst(), () -> {
            p.closeInventory();
            p.openInventory(inv);
            viewers.put(p, name);
        });
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
        Player p = (Player)e.getWhoClicked();
        if (viewers.containsKey(p)) {
            GuiMenuGuard.cancelAndScrub(e, p);
            if (e.getClickedInventory() == p.getOpenInventory().getBottomInventory()) {
                return;
            }
            Game game2;
            String room = viewers.get(p);
            ItemStack click = e.getCurrentItem();
            if (click == null) {
                return;
            }
            if (click.getType().equals((Object)Material.AIR)) {
                return;
            }
            String keyClick = CompatItems.getKey(click, "EditorGui_ClickType");
            if (keyClick == null || keyClick.isEmpty()) {
                return;
            }
            for (int i = 1; i <= 10; ++i) {
                if (!keyClick.equalsIgnoreCase("editmob" + i)) continue;
                if (e.isRightClick()) {
                    File file = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + room + ".yml");
                    YamlConfiguration config = YamlConfiguration.loadConfiguration((File)file);
                    if (!config.contains("Mob" + i)) break;
                    AtomicReference<String> toRemove = new AtomicReference<String>("");
                    ConfigurationSection section = config.getConfigurationSection("Mob" + i);
                    if (section != null) {
                        section.getKeys(false).forEach(toRemove::set);
                    }
                    if (toRemove.get().isEmpty()) break;
                    config.set("Mob" + i + "." + String.valueOf(toRemove), null);
                    FileManager.saveFileConfig((FileConfiguration)config, file);
                    p.sendMessage(Messages.get("RemoveMob"));
                    game2 = Game.getGame(room);
                    if (game2 != null) {
                        game2.setConfig((FileConfiguration)config);
                    }
                    EditorGui.open(p, room);
                    break;
                }
                p.closeInventory();
                p.getInventory().addItem(new ItemStack[]{this.superultrablazerod(room, "Mob" + i)});
                p.sendMessage(Messages.get("EditMob_Step1"));
                break;
            }
            switch (keyClick.toLowerCase()) {
                case "editplayer": {
                    editor.put(p, room + ":editplayer");
                    p.closeInventory();
                    p.sendMessage(Messages.get("EditPlayer"));
                    return;
                }
                case "editprefix": {
                    editor.put(p, room + ":editprefix");
                    p.closeInventory();
                    p.sendMessage(Messages.get("EditPrefix"));
                    return;
                }
                case "edittime": {
                    editor.put(p, room + ":edittime");
                    p.closeInventory();
                    p.sendMessage(Messages.get("EditTime"));
                    return;
                }
                case "editreward": {
                    p.closeInventory();
                    RewardGui.open(p, room);
                    return;
                }
                case "editboss": {
                    p.closeInventory();
                    p.getInventory().addItem(new ItemStack[]{this.superultrablazerod(room, "Boss")});
                    p.sendMessage(Messages.get("EditBoss_Step1"));
                    return;
                }
                case "editspawn": {
                    p.closeInventory();
                    Location loc = p.getLocation();
                    File file = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + room + ".yml");
                    YamlConfiguration rooms = YamlConfiguration.loadConfiguration((File)file);
                    rooms.set("Spawn", (Object)loc);
                    FileManager.saveFileConfig((FileConfiguration)rooms, file);
                    try {
                        game2 = Game.getGame(room);
                        if (game2 != null) {
                            game2.setSpawn(loc);
                        }
                    }
            catch (Exception ex2) {
                // empty catch block
            }
            EditorGui.open(p, room);
            return;
        }
        case "edittype": {
                    editor.put(p, room + ":edittype");
                    p.closeInventory();
                    p.sendMessage(Messages.get("EditType"));
                    return;
                }
                case "editdisplay": {
                    editor.put(p, room + ":editdisplay");
                    p.closeInventory();
                    p.sendMessage(Messages.get("EditDisplay"));
                    return;
                }
                case "deleteroom": {
                    File file = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + room + ".yml");
                    file.delete();
                    Game.deleteRoom(room);
                    p.closeInventory();
                    p.sendMessage(Messages.get("DeleteRoom"));
                    return;
                }
                case "confirm": {
                    File file = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + room + ".yml");
                    YamlConfiguration rooms = YamlConfiguration.loadConfiguration((File)file);
                    if (!Game.canJoin((FileConfiguration)rooms)) {
                        p.sendMessage(Messages.get("RoomSetupCheck.Header").replace("<room>", room));
                        for (String key : Game.getRoomSetupMissingKeys((FileConfiguration)rooms)) {
                            p.sendMessage(Messages.get(key));
                        }
                        p.sendMessage(Messages.get("RoomSetupCheck.EditHint"));
                        return;
                    }
                    Game.load(room, (FileConfiguration)rooms, file);
                    p.closeInventory();
                    p.sendMessage(Messages.get("ConfigDone"));
                    String type = rooms.getString("Type");
                    String result = EditorGui.containsPhobanType(type);
                    if (result.equals("deo-co-con-cac-gi-o-day-het")) {
                        FileConfiguration phoban = FileManager.getFileConfig(FileManager.Files.PHOBAN);
                        FileConfiguration gui = FileManager.getFileConfig(FileManager.Files.GUI);
                        phoban.set(type + "Room.Type", (Object)type);
                        phoban.set(type + "Room.Priority", (Object)100);
                        phoban.set(type + "Room.ID", (Object)gui.getString("ChooseTypeGui.TypeFormat.ID"));
                        phoban.set(type + "Room.Name", (Object)gui.getString("ChooseTypeGui.TypeFormat.Name"));
                        phoban.set(type + "Room.Lore", (Object)gui.getStringList("ChooseTypeGui.TypeFormat.Lore"));
                        FileManager.saveFileConfig(phoban, FileManager.Files.PHOBAN);
                    }
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

    @EventHandler(priority=EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        final Player p = e.getPlayer();
        if (editor.containsKey(p)) {
            YamlConfiguration room9;
            File file;
            YamlConfiguration room2;
            Object file2;
            String id;
            e.setCancelled(true);
            String[] parts = editor.get(p).split(":");
            if (parts.length < 2) {
                return;
            }
            final String name = parts[0];
            String type = parts[1];
            String mess = ChatColor.stripColor((String)e.getMessage());
            if ((mess.equalsIgnoreCase("cancel") || mess.equalsIgnoreCase("complete")) && !type.toLowerCase().contains("editmob") && !type.toLowerCase().contains("editboss")) {
                this.clearBlazeRod(p);
                editor.remove(p);
                EditorGui.open(p, name);
                return;
            }
            Game game = Game.getGame(name);
            for (int i = 1; i <= 10; ++i) {
                if (type.equalsIgnoreCase("editmob" + i + "_control")) {
                    String id2 = parts.length > 2 ? parts[2] : "";
                    switch (mess.toLowerCase()) {
                        case "add": {
                            editor.remove(p);
                            editor.put(p, name + ":editmob" + i + "_type:" + id2 + ":no-parent");
                            p.sendMessage(Messages.get("EditMob_Step3"));
                            return;
                        }
                        case "remove": {
                            File file3 = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + name + ".yml");
                            YamlConfiguration room3 = YamlConfiguration.loadConfiguration((File)file3);
                            room3.set("Mob" + i, null);
                            FileManager.saveFileConfig((FileConfiguration)room3, file3);
                            p.sendMessage(Messages.get("RemoveMob"));
                            editor.remove(p);
                            EditorGui.open(p, name);
                            return;
                        }
                        case "exit": {
                            File file4 = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + name + ".yml");
                            YamlConfiguration room4 = YamlConfiguration.loadConfiguration((File)file4);
                            room4.set("Mob" + i + "." + id2, null);
                            FileManager.saveFileConfig((FileConfiguration)room4, file4);
                            editor.remove(p);
                            EditorGui.open(p, name);
                            return;
                        }
                    }
                    p.sendMessage(Messages.get("EditMob_Step2"));
                    return;
                }
                if (type.equalsIgnoreCase("editmob" + i + "_type")) {
                    String id3 = parts.length > 2 ? parts[2] : "";
                    String parent = parts.length > 3 ? parts[3] : "no-parent";
                    File file5 = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + name + ".yml");
                    YamlConfiguration room5 = YamlConfiguration.loadConfiguration((File)file5);
                    Object path = "";
                    path = parent.equalsIgnoreCase("no-parent") ? "Mob" + i + "." + id3 : "Mob" + i + "." + parent + "." + id3;
                    room5.set((String)path + ".Type", (Object)mess);
                    FileManager.saveFileConfig((FileConfiguration)room5, file5);
                    editor.remove(p);
                    editor.put(p, name + ":editmob" + i + "_amount:" + id3 + ":" + parent);
                    p.sendMessage(Messages.get("EditMob_Step4"));
                    return;
                }
                if (type.equalsIgnoreCase("editmob" + i + "_amount")) {
                    try {
                        int amount = Integer.parseInt(mess);
                        String id4 = parts.length > 2 ? parts[2] : "";
                        String parent = parts.length > 3 ? parts[3] : "no-parent";
                        File file6 = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + name + ".yml");
                        YamlConfiguration room6 = YamlConfiguration.loadConfiguration((File)file6);
                        Object path = "";
                        path = parent.equalsIgnoreCase("no-parent") ? "Mob" + i + "." + id4 : "Mob" + i + "." + parent + "." + id4;
                        room6.set((String)path + ".Amount", (Object)amount);
                        room6.set((String)path + ".Time", null);
                        FileManager.saveFileConfig((FileConfiguration)room6, file6);
                        if (game != null) {
                            game.setConfig((FileConfiguration)room6);
                        }
                        editor.remove(p);
                        editor.put(p, name + ":waiting-new-mob");
                        p.getInventory().addItem(new ItemStack[]{this.superultrablazerod(name, "Mob" + i,
                                parent.equalsIgnoreCase("no-parent") ? id4 : parent)});
                        p.sendMessage(Messages.get("AddNewMob"));
                    }
                    catch (Exception ex) {
                        if (ex.getMessage().contains("For input string:")) {
                            p.sendMessage(Messages.get("NotInt"));
                            return;
                        }
                        p.sendMessage(Messages.get("Error").replace("<error>", ex.getMessage()));
                        ex.printStackTrace();
                    }
                    return;
                }
            }
            switch (type.toLowerCase()) {
                case "editboss_control": {
                    id = parts.length > 2 ? parts[2] : "";
                    switch (mess.toLowerCase()) {
                        case "add": {
                            editor.remove(p);
                            editor.put(p, name + ":editboss_type:" + id);
                            p.sendMessage(Messages.get("EditBoss_Step3"));
                            return;
                        }
                        case "remove": {
                            File file8 = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + name + ".yml");
                            YamlConfiguration room8 = YamlConfiguration.loadConfiguration((File)file8);
                            room8.set("Boss", null);
                            FileManager.saveFileConfig((FileConfiguration)room8, file8);
                            p.sendMessage(Messages.get("RemoveMob"));
                            editor.remove(p);
                            new BukkitRunnable(){

                                public void run() {
                                    EditorGui.open(p, name);
                                }
                            }.runTaskLater((Plugin)DungeonsCore.inst(), 0L);
                            return;
                        }
                        case "exit": {
                            editor.remove(p);
                            new BukkitRunnable(){

                                public void run() {
                                    EditorGui.open(p, name);
                                }
                            }.runTaskLater((Plugin)DungeonsCore.inst(), 0L);
                            return;
                        }
                    }
                    p.sendMessage(Messages.get("EditBoss_Step2"));
                    return;
                }
                case "editboss_type": {
                    id = editor.get(p).split(":")[2];
                    file2 = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + name + ".yml");
                    room2 = YamlConfiguration.loadConfiguration((File)file2);
                    room2.set("Boss.Type", (Object)mess);
                    FileManager.saveFileConfig((FileConfiguration)room2, (File)file2);
                    editor.remove(p);
                    editor.put(p, name + ":editboss_amount:" + id);
                    p.sendMessage(Messages.get("EditBoss_Step4"));
                    return;
                }
                case "editboss_amount": {
                    try {
                        id = editor.get(p).split(":")[2];
                        int amount = Integer.parseInt(mess);
                        file = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + name + ".yml");
                        room9 = YamlConfiguration.loadConfiguration((File)file);
                        room9.set("Boss.Amount", (Object)amount);
                        room9.set("Boss.Time", null);
                        FileManager.saveFileConfig((FileConfiguration)room9, file);
                        if (game != null) {
                            game.setConfig((FileConfiguration)room9);
                        }
                        editor.remove(p);
                        new BukkitRunnable(){

                            public void run() {
                                EditorGui.open(p, name);
                            }
                        }.runTaskLater((Plugin)DungeonsCore.inst(), 0L);
                    }
                    catch (Exception ex) {
                        if (ex.getMessage() == null) {
                            new BukkitRunnable(){

                                public void run() {
                                    EditorGui.open(p, name);
                                }
                            }.runTaskLater((Plugin)DungeonsCore.inst(), 0L);
                            return;
                        }
                        if (ex.getMessage().contains("For input string:")) {
                            p.sendMessage(Messages.get("NotInt"));
                            return;
                        }
                        p.sendMessage(Messages.get("Error").replace("<error>", ex.getMessage()));
                        ex.printStackTrace();
                    }
                    return;
                }
            }
            switch (type.toLowerCase()) {
                case "waiting-new-mob": {
                    p.sendMessage(Messages.get("AddNewMob"));
                    return;
                }
                case "editplayer": {
                    try {
                        int player = (int)Long.parseLong(mess);
                        File file9 = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + name + ".yml");
                        room2 = YamlConfiguration.loadConfiguration((File)file9);
                        room2.set("Player", (Object)player);
                        FileManager.saveFileConfig((FileConfiguration)room2, file9);
                        try {
                            game.setMaxPlayer(player);
                        }
                        catch (Exception ignored1) {
                            // empty catch block
                        }
                        editor.remove(p);
                        new BukkitRunnable(){

                            public void run() {
                                EditorGui.open(p, name);
                            }
                        }.runTaskLater((Plugin)DungeonsCore.inst(), 0L);
                    }
                    catch (Exception ex) {
                        if (ex.getMessage() == null) {
                            new BukkitRunnable(){

                                public void run() {
                                    EditorGui.open(p, name);
                                }
                            }.runTaskLater((Plugin)DungeonsCore.inst(), 0L);
                            return;
                        }
                        if (ex.getMessage().contains("For input string:")) {
                            p.sendMessage(Messages.get("NotInt"));
                            return;
                        }
                        p.sendMessage(Messages.get("Error").replace("<error>", ex.getMessage()));
                        ex.printStackTrace();
                    }
                    return;
                }
                case "editprefix": {
                    File file10 = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + name + ".yml");
                    YamlConfiguration room10 = YamlConfiguration.loadConfiguration((File)file10);
                    room10.set("Prefix", (Object)mess);
                    FileManager.saveFileConfig((FileConfiguration)room10, file10);
                    editor.remove(p);
                    new BukkitRunnable(){

                        public void run() {
                            EditorGui.open(p, name);
                        }
                    }.runTaskLater((Plugin)DungeonsCore.inst(), 0L);
                    return;
                }
                case "edittime": {
                    try {
                        int time = Integer.parseInt(mess);
                        File file11 = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + name + ".yml");
                        room2 = YamlConfiguration.loadConfiguration((File)file11);
                        room2.set("Time", (Object)time);
                        FileManager.saveFileConfig((FileConfiguration)room2, file11);
                        try {
                            game.setMaxTime(time);
                        }
                        catch (Exception ignored2) {
                            // empty catch block
                        }
                        editor.remove(p);
                        new BukkitRunnable(){

                            public void run() {
                                EditorGui.open(p, name);
                            }
                        }.runTaskLater((Plugin)DungeonsCore.inst(), 0L);
                    }
                    catch (Exception ex) {
                        if (ex.getMessage() == null) {
                            new BukkitRunnable(){

                                public void run() {
                                    EditorGui.open(p, name);
                                }
                            }.runTaskLater((Plugin)DungeonsCore.inst(), 0L);
                            return;
                        }
                        if (ex.getMessage().contains("For input string:")) {
                            p.sendMessage(Messages.get("NotInt"));
                            return;
                        }
                        p.sendMessage(Messages.get("Error").replace("<error>", ex.getMessage()));
                        ex.printStackTrace();
                    }
                    return;
                }
                case "edittype": {
                    File file14 = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + name + ".yml");
                    YamlConfiguration room11 = YamlConfiguration.loadConfiguration((File)file14);
                    room11.set("Type", (Object)mess);
                    FileManager.saveFileConfig((FileConfiguration)room11, file14);
                    editor.remove(p);
                    new BukkitRunnable(){

                        public void run() {
                            YamlConfiguration r = YamlConfiguration.loadConfiguration(file14);
                            Game.load(name, r, file14);
                            EditorGui.open(p, name);
                        }
                    }.runTask((Plugin)DungeonsCore.inst());
                    return;
                }
                case "editdisplay": {
                    File fileDisp = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + name + ".yml");
                    YamlConfiguration roomDisp = YamlConfiguration.loadConfiguration(fileDisp);
                    if (mess.equalsIgnoreCase("clear") || mess.equals("-")) {
                        roomDisp.set("Display", null);
                    } else {
                        roomDisp.set("Display", (Object)mess);
                    }
                    FileManager.saveFileConfig((FileConfiguration)roomDisp, fileDisp);
                    editor.remove(p);
                    new BukkitRunnable(){

                        public void run() {
                            YamlConfiguration r = YamlConfiguration.loadConfiguration(fileDisp);
                            Game.load(name, r, fileDisp);
                            EditorGui.open(p, name);
                        }
                    }.runTask((Plugin)DungeonsCore.inst());
                    return;
                }
            }
        }
    }

    private ItemStack superultrablazerod(String room, String mob) {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("\u00a7ePhoban Rod - " + mob);
            meta.setLore(Arrays.asList("\u00a7fRoom: \u00a7e" + room, "\u00a7fMob: \u00a7e" + mob));
            item.setItemMeta(meta);
        }
        item = CompatItems.setKey(item, "PhoBanRod", "true");
        item = CompatItems.setKey(item, "PhoBanRod_Room", room);
        item = CompatItems.setKey(item, "PhoBanRod_Mob", mob);
        return item;
    }

    private ItemStack superultrablazerod(String room, String mob, String parent) {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("\u00a7ePhoban Rod \u00a7f- \u00a7aChild \u00a7f- \u00a7d" + mob);
            meta.setLore(Arrays.asList("\u00a7fRoom: \u00a7e" + room, "\u00a7fParent: \u00a7e" + parent, "\u00a7fChild: \u00a7e" + mob));
            item.setItemMeta(meta);
        }
        item = CompatItems.setKey(item, "PhoBanRod", "true");
        item = CompatItems.setKey(item, "PhoBanRod_Room", room);
        item = CompatItems.setKey(item, "PhoBanRod_Mob", mob);
        item = CompatItems.setKey(item, "PhoBanRod_Parent", parent);
        return item;
    }

    private void clearBlazeRod(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != Material.BLAZE_ROD || CompatItems.getKey(item, "PhoBanRod") == null) continue;
            player.getInventory().remove(item);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || CompatItems.getKey(item, "PhoBanRod") == null) {
            return;
        }
        e.setCancelled(true);
        String room = CompatItems.getKey(item, "PhoBanRod_Room");
        String mob = CompatItems.getKey(item, "PhoBanRod_Mob");
        String parent = CompatItems.getKey(item, "PhoBanRod_Parent");
        Block b = e.getClickedBlock();
        if (room == null || room.isEmpty() || mob == null || mob.isEmpty() || b == null) {
            return;
        }
        if (mob.contains("ob")) {
            int mobsid = 1;
            Object mobidpath;
            File file = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + room + ".yml");
            YamlConfiguration config = YamlConfiguration.loadConfiguration((File)file);
            if (parent == null) {
                ConfigurationSection section;
                if (config.contains(mob) && (section = config.getConfigurationSection(mob)) != null) {
                    mobsid = section.getKeys(false).size() + 1;
                }
                mobidpath = mob + "." + mobsid;
            } else {
                String parentPath = mob + "." + parent;
                ConfigurationSection parentSection = config.getConfigurationSection(parentPath);
                int nextChildId = (parentSection == null) ? 1 : (parentSection.getKeys(false).size() + 1);
                mobsid = nextChildId;
                mobidpath = parentPath + "." + nextChildId;
            }
            Location loc = b.getLocation();
            config.set((String)mobidpath + ".Location", (Object)loc);
            FileManager.saveFileConfig((FileConfiguration)config, file);
            String stageNum = mob.replaceAll("[^0-9]", "");
            if (stageNum.isEmpty()) {
                stageNum = "1";
            }
            String controlId = (parent == null || parent.isEmpty()) ? String.valueOf(mobsid) : (parent + "." + mobsid);
            editor.put(p, room + ":editmob" + stageNum + "_control:" + controlId);
            p.sendMessage(Messages.get("EditMob_Step2"));
            return;
        }
        if (mob.equalsIgnoreCase("Boss")) {
            Location loc = b.getLocation();
            File file = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + room + ".yml");
            YamlConfiguration config = YamlConfiguration.loadConfiguration((File)file);
            config.set("Boss.Location", (Object)loc);
            FileManager.saveFileConfig((FileConfiguration)config, file);
            editor.put(p, room + ":editboss_control:boss");
            p.sendMessage(Messages.get("EditBoss_Step2"));
        }
    }

    private static boolean isEdited(String room, String mob) {
        ConfigurationSection section;
        File file = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + room + ".yml");
        YamlConfiguration rooms = YamlConfiguration.loadConfiguration((File)file);
        if (rooms.contains(mob) && (section = rooms.getConfigurationSection(mob)) != null) {
            return section.getKeys(false).stream().anyMatch(arg_0 -> EditorGui.hasMobConfig(rooms, mob, arg_0));
        }
        return false;
    }

    private static String containsPhobanType(String type) {
        if (type == null || type.isBlank()) {
            return "deo-co-con-cac-gi-o-day-het";
        }
        FileConfiguration phoban = FileManager.getFileConfig(FileManager.Files.PHOBAN);
        for (String key : phoban.getKeys(false)) {
            if (!phoban.contains(key + ".Type") || !phoban.getString(key + ".Type", "").equalsIgnoreCase(type)) continue;
            return key;
        }
        return "deo-co-con-cac-gi-o-day-het";
    }

    private static boolean hasMobConfig(FileConfiguration rooms, String mob, String s) {
        return rooms.contains(mob + "." + s + ".Type") && rooms.contains(mob + "." + s + ".Amount") && rooms.contains(mob + "." + s + ".Location");
    }
}
