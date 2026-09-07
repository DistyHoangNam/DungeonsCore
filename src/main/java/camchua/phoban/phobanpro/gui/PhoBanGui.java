package camchua.phoban.phobanpro.gui;

import camchua.phoban.phobanpro.compat.CompatItems;
import camchua.phoban.phobanpro.game.Game;
import camchua.phoban.phobanpro.game.GameStatus;
import camchua.phoban.phobanpro.game.PlayerData;
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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class PhoBanGui
implements Listener {
    public static HashMap<Player, PhoBanGui> viewers = new HashMap();
    public ArrayList<Inventory> pages = new ArrayList();
    public int curPage = 0;
    public String type;

    public PhoBanGui(Player p, int pg, String type) {
        if (p == null || pg < 0) {
            return;
        }
        this.type = type;
        this.curPage = pg;
        Inventory page = this.gui();
        List<Integer> roomSlots = this.getRoomSlots(page.getSize());
        int slotIndex = 0;
        for (String name : this.getSortedRooms(type)) {
            Game g = Game.getGame(name);
            if (g == null) continue;
            FileConfiguration room = g.getConfig();
            HashMap<String, List<String>> replace = new HashMap<String, List<String>>();
            replace.put("<prefix>", Collections.singletonList(ColorUtils.colorize(room.getString("Prefix", ""))));
            replace.put("<display>", Collections.singletonList(ColorUtils.colorize(g.getTypeDisplay())));
            replace.put("<type>", Collections.singletonList(g.getType() != null ? g.getType() : ""));
            replace.put("<current>", Collections.singletonList(String.valueOf(g.getPlayers().size())));
            replace.put("<max>", Collections.singletonList(String.valueOf(room.getInt("Player"))));
            ArrayList<String> lores = new ArrayList<String>();
            for (Player player : g.getPlayers()) {
                lores.add(ColorUtils.colorize(FileManager.getFileConfig(FileManager.Files.GUI).getString("PhoBanGui.PlayerFormat", "").replace("<player>", player.getName())));
            }
            replace.put("<players>", lores);
            replace.put("<time>", Collections.singletonList(PhoBanGui.timeFormat(g.getTimeLeft())));
            replace.put("<status>", Collections.singletonList(ColorUtils.colorize(FileManager.getFileConfig(FileManager.Files.GUI).getString("PhoBanGui.StatusFormat." + g.getStatus().toString(), ""))));
            ItemStack item = g.getStatus().equals((Object)GameStatus.WAITING) ? ItemBuilder.build(FileManager.Files.GUI, "PhoBanGui.WaitingRoom", replace) : (g.getStatus().equals((Object)GameStatus.STARTING) ? ItemBuilder.build(FileManager.Files.GUI, "PhoBanGui.StartingRoom", replace) : ItemBuilder.build(FileManager.Files.GUI, "PhoBanGui.PlayingRoom", replace));
            ItemStack item2 = CompatItems.setKey(item.clone(), "PhoBanGui_ClickType", "JoinRoom");
            item2 = CompatItems.setKey(item2, "PhoBanGui_Room", name);
            item2 = GuiMenuGuard.protect(item2);
            if (roomSlots.isEmpty()) {
                continue;
            }
            if (slotIndex >= roomSlots.size()) {
                this.pages.add(page);
                page = this.gui();
                slotIndex = 0;
            }
            page.setItem(roomSlots.get(slotIndex++), item2);
        }
        this.pages.add(page);
        if (viewers.containsKey(p)) {
            viewers.remove(p);
        }
        if (this.curPage >= this.pages.size()) {
            this.curPage = Math.max(0, this.pages.size() - 1);
        }
        p.openInventory(this.pages.get(this.curPage));
        viewers.put(p, this);
    }

    private List<String> getSortedRooms(String type) {
        FileConfiguration phoban = FileManager.getFileConfig(FileManager.Files.PHOBAN);
        ArrayList<String> rooms = new ArrayList<String>();
        for (String name : Game.listGame()) {
            Game g = Game.getGame(name);
            String t;
            if (g == null || (t = g.getType()) == null || !t.equalsIgnoreCase(type)) continue;
            rooms.add(name);
        }
        rooms.sort((left, right) -> {
            int priorityCompare = Integer.compare(phoban.getInt(left + ".Priority", 100), phoban.getInt(right + ".Priority", 100));
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            Game leftGame = Game.getGame(left);
            Game rightGame = Game.getGame(right);
            String leftType = leftGame != null && leftGame.getType() != null ? leftGame.getType() : left;
            String rightType = rightGame != null && rightGame.getType() != null ? rightGame.getType() : right;
            int naturalCompare = Integer.compare(PhoBanGui.naturalRoomOrder(leftType), PhoBanGui.naturalRoomOrder(rightType));
            if (naturalCompare != 0) {
                return naturalCompare;
            }
            return left.compareToIgnoreCase(right);
        });
        return rooms;
    }

    private List<Integer> getRoomSlots(int inventorySize) {
        ArrayList<Integer> slots = new ArrayList<Integer>();
        for (Integer slot : FileManager.getFileConfig(FileManager.Files.GUI).getIntegerList("PhoBanGui.RoomSlot")) {
            if (slot != null && slot >= 0 && slot < inventorySize) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private static int naturalRoomOrder(String value) {
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
        int rows = gui.getInt("PhoBanGui.Rows");
        if (rows < 3) {
            rows = 3;
        }
        Inventory inv = Bukkit.createInventory(null, (int)(rows * 9), ColorUtils.colorize(gui.getString("PhoBanGui.Title")));
        ItemStack blank = GuiMenuGuard.protect(ItemBuilder.build(FileManager.Files.GUI, "PhoBanGui.Blank", new HashMap<String, List<String>>()));
        Iterator iterator = gui.getIntegerList("PhoBanGui.Blank.Slot").iterator();
        while (iterator.hasNext()) {
            int slot = (Integer)iterator.next();
            if (slot >= gui.getInt("PhoBanGui.Rows") * 9) continue;
            if (slot <= -1) {
                for (int i = 0; i < gui.getInt("PhoBanGui.Rows") * 9; ++i) {
                    inv.setItem(i, blank.clone());
                }
                break;
            }
            inv.setItem(slot, blank.clone());
        }
        ItemStack nextpage = ItemBuilder.build(FileManager.Files.GUI, "PhoBanGui.NextPage", new HashMap<String, List<String>>());
        ItemStack nextPage2 = CompatItems.setKey(nextpage.clone(), "PhoBanGui_ClickType", "NextPage");
        nextPage2 = GuiMenuGuard.protect(nextPage2);
        Iterator i = gui.getIntegerList("PhoBanGui.NextPage.Slot").iterator();
        while (i.hasNext()) {
            int slot = (Integer)i.next();
            if (slot >= gui.getInt("PhoBanGui.Rows") * 9) continue;
            if (slot <= -1) {
                for (int i2 = 0; i2 < gui.getInt("PhoBanGui.Rows") * 9; ++i2) {
                    inv.setItem(i2, nextPage2);
                }
                break;
            }
            inv.setItem(slot, nextPage2);
        }
        ItemStack previouspage = ItemBuilder.build(FileManager.Files.GUI, "PhoBanGui.PreviousPage", new HashMap<String, List<String>>());
        ItemStack previousPage2 = CompatItems.setKey(previouspage.clone(), "PhoBanGui_ClickType", "PreviousPage");
        previousPage2 = GuiMenuGuard.protect(previousPage2);
        Iterator iterator2 = gui.getIntegerList("PhoBanGui.PreviousPage.Slot").iterator();
        while (iterator2.hasNext()) {
            int slot = (Integer)iterator2.next();
            if (slot >= gui.getInt("PhoBanGui.Rows") * 9) continue;
            if (slot <= -1) {
                for (int i3 = 0; i3 < gui.getInt("PhoBanGui.Rows") * 9; ++i3) {
                    inv.setItem(i3, previousPage2);
                }
                break;
            }
            inv.setItem(slot, previousPage2);
        }
        iterator2 = gui.getIntegerList("PhoBanGui.RoomSlot").iterator();
        while (iterator2.hasNext()) {
            int room_slot = (Integer)iterator2.next();
            inv.setItem(room_slot, new ItemStack(Material.AIR));
        }
        return inv;
    }

    public static String timeFormat(int time) {
        int minute = time / 60;
        int second = time % 60;
        FileConfiguration gui = FileManager.getFileConfig(FileManager.Files.GUI);
        StringBuilder sb = new StringBuilder();
        if (minute > 0) {
            sb.append(ColorUtils.colorize(gui.getString("PhoBanGui.TimeFormat.Minute").replace("<minute>", "" + minute)));
            sb.append(" ");
        }
        if (second > 0) {
            sb.append(ColorUtils.colorize(gui.getString("PhoBanGui.TimeFormat.Second").replace("<second>", "" + second)));
        }
        return sb.toString();
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
            ItemStack click = e.getCurrentItem();
            if (click == null) {
                return;
            }
            if (click.getType().equals((Object)Material.AIR)) {
                return;
            }
            String keyClick = CompatItems.getKey(click, "PhoBanGui_ClickType");
            if (keyClick == null || keyClick.isEmpty()) {
                return;
            }
            switch (keyClick.toLowerCase()) {
                case "nextpage": {
                    if (!GuiAntiSpam.allow(p, 200L)) {
                        return;
                    }
                    PhoBanGui inv = viewers.get(p);
                    if (inv.curPage >= inv.pages.size() - 1) {
                        return;
                    }
                    new PhoBanGui(p, inv.curPage + 1, inv.type);
                    return;
                }
                case "previouspage": {
                    if (!GuiAntiSpam.allow(p, 200L)) {
                        return;
                    }
                    PhoBanGui inv = viewers.get(p);
                    if (inv.curPage > 0) {
                        new PhoBanGui(p, inv.curPage - 1, inv.type);
                    }
                    return;
                }
                case "joinroom": {
                    if (!GuiAntiSpam.allow(p, 400L)) {
                        return;
                    }
                    String name = CompatItems.getKey(click, "PhoBanGui_Room");
                    Game game = Game.getGame(name);
                    if (game != null && game.getStatus().equals((Object)GameStatus.WAITING)) {
                        if (game.isFull()) {
                            p.sendMessage(Messages.get("RoomFull"));
                            return;
                        }
                        if (!Game.canJoin(game.getConfig())) {
                            p.sendMessage(Messages.get("JoinRoomNotConfig"));
                            return;
                        }
                        if (!Game.hasTurn((org.bukkit.OfflinePlayer)p, game.getType())) {
                            p.sendMessage(Messages.get("NoTurn"));
                            return;
                        }
                        String denyMsg = Game.getJoinPermissionDenyMessage(p, name, game.getConfig());
                        if (denyMsg != null) {
                            p.sendMessage(denyMsg);
                            return;
                        }
                        boolean alreadyJoined = game.getPlayers().contains(p);
                        if (!alreadyJoined && !game.hasDailyTicketAvailable()) {
                            p.sendMessage(game.getDailyTicketLimitMessage());
                            return;
                        }
                        game.join(p);
                        if (!alreadyJoined && game.getPlayers().contains(p)) {
                            game.takeDailyTicket();
                        }
                        if (PlayerData.contains(p) && PlayerData.get(p).getGame() == game) {
                            viewers.remove(p);
                            p.closeInventory();
                        }
                        if (game.isLeader(p)) {
                            p.sendMessage(Messages.get("LeaderStart"));
                        }
                        return;
                    }
                    if ((game == null || !game.getStatus().equals((Object)GameStatus.STARTING)) && (game == null || !game.getStatus().equals((Object)GameStatus.PLAYING))) break;
                    p.sendMessage(Messages.get("RoomStarted"));
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Player p = (Player)e.getPlayer();
        if (viewers.containsKey(p)) {
            viewers.remove(p);
        }
        GuiMenuGuard.scrubLater(p);
    }

}
