package camchua.phoban.phobanpro;

import camchua.phoban.phobanpro.compat.CompatBootstrap;
import camchua.phoban.phobanpro.command.TabCommand;
import camchua.phoban.phobanpro.game.Game;
import camchua.phoban.phobanpro.game.GameStatus;
import camchua.phoban.phobanpro.game.PlayerData;
import camchua.phoban.phobanpro.mythicmobs.LumineMythicEventRegistrar;
import camchua.phoban.phobanpro.game.listener.sapi.SAPIListener;
import camchua.phoban.phobanpro.gui.*;
import camchua.phoban.phobanpro.listener.FreeVoucherListener;
import camchua.phoban.phobanpro.listener.PlayerCommandPreprocessListener;
import camchua.phoban.phobanpro.listener.PlayerQuitListener;
import camchua.phoban.phobanpro.manager.FileManager;
import camchua.phoban.phobanpro.manager.IntegrationConfig;
import camchua.phoban.phobanpro.mythicmobs.BukkitAPIHelper;
import camchua.phoban.phobanpro.utils.DebugLogger;
import camchua.phoban.phobanpro.utils.Messages;
import camchua.phoban.phobanpro.utils.PhoBanExpansion;
import camchua.phoban.phobanpro.utils.Utils;

import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class DungeonsCore extends JavaPlugin {

    private static DungeonsCore plugin;
    private static LuckPerms luckperms;
    public static final HashMap<String, String> addRewardsMap = new HashMap<>();

    private BukkitAPIHelper bukkitAPIHelper;

    public static DungeonsCore inst() { return plugin; }

    @Override
    public void onEnable() {
        plugin = this;
        CompatBootstrap.init(this);
        Utils.checkVersion();
        FileManager.setup(this);
        DebugLogger.log("Plugin enable started. Debug=" + DebugLogger.statusText());
        bukkitAPIHelper = new BukkitAPIHelper();
        TabCommand tabCommand = new TabCommand();
        for (String cmd : List.of("phobanpro", "phoban")) {
            var pluginCmd = getCommand(cmd);
            if (pluginCmd != null) pluginCmd.setTabCompleter(tabCommand);
        }

        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            LicenseKey.setup(this);

            registerListeners(
                    new EditorGui(), new RewardGui(),
                    new PhoBanGui(null, -1, ""),
                    new ChooseTypeGui(null, -1),
                    new camchua.phoban.phobanpro.game.GameListener(),
                    new PlayerCommandPreprocessListener(),
                    new PlayerQuitListener(),
                    new FreeVoucherListener(),
                    new TopGui()
            );

            if (IntegrationConfig.isMythicListenerEnabled() && bukkitAPIHelper.isLumineHooked()) {
                LumineMythicEventRegistrar.register(this);
                DebugLogger.log("Registered Mythic damage listener");
            } else {
                DebugLogger.log("Skipped Mythic damage listener. enabled="
                        + IntegrationConfig.isMythicListenerEnabled()
                        + ", lumineHooked=" + bukkitAPIHelper.isLumineHooked());
            }
            if (IntegrationConfig.isSkillApiListenerEnabled()
                    && (Bukkit.getPluginManager().isPluginEnabled("SkillAPI")
                    || Bukkit.getPluginManager().isPluginEnabled("ProSkillAPI"))) {
                registerListeners(new SAPIListener());
                DebugLogger.log("Registered SkillAPI/ProSkillAPI listener");
            } else {
                DebugLogger.log("Skipped SkillAPI/ProSkillAPI listener. enabled="
                        + IntegrationConfig.isSkillApiListenerEnabled()
                        + ", SkillAPI=" + Bukkit.getPluginManager().isPluginEnabled("SkillAPI")
                        + ", ProSkillAPI=" + Bukkit.getPluginManager().isPluginEnabled("ProSkillAPI"));
            }
            if (IntegrationConfig.isPlaceholderApiEnabled()
                    && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                new PhoBanExpansion().register();
                DebugLogger.log("Registered PlaceholderAPI expansion");
            } else {
                DebugLogger.log("Skipped PlaceholderAPI expansion. enabled="
                        + IntegrationConfig.isPlaceholderApiEnabled()
                        + ", plugin=" + Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"));
            }

            setupPermissions();
            startTurnResetTask();

            Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
                Game.convertData();
                Game.load();
            }, 20L);

            for (String cmd : List.of("phobanpro", "phoban")) {
                var pluginCmd = getCommand(cmd);
                if (pluginCmd != null) pluginCmd.setExecutor(this);
            }
        });
    }

    @Override
    public void onDisable() {
        try {
            Game.shutdownAllActiveSessions(true, "&c&lServer dang tat, pho ban da bi huy.");
            addRewardsMap.clear();
            Bukkit.getScheduler().cancelTasks(this);
        } catch (Exception ignored) {}
        LicenseKey.disablePlugin(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Messages.get("NotPlayer"));
                return true;
            }
            if (!p.hasPermission("phoban.use")) {
                p.sendMessage(Messages.get("NoPermissions"));
                return true;
            }
            new ChooseTypeGui(p, 0);
            return true;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "edit" -> handleEdit(sender, args);
            case "add" -> handleAdd(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "reload" -> handleReload(sender);
            case "debug" -> handleDebug(sender, args);
            case "start" -> handleStart(sender);
            case "help" -> handleHelp(sender);
            case "leave" -> handleLeave(sender);
            case "list" -> handleList(sender);
            case "check" -> handleCheckRoom(sender, args);
            case "join" -> handleJoin(sender, args);
            case "setspawn" -> handleSetSpawn(sender);
            case "top" -> handleTop(sender);
            case "givepoint" -> handleGivePoint(sender, args);
            case "takepoint" -> handleTakePoint(sender, args);
            case "addrewards" -> handleAddRewards(sender, args);
            case "phongcho" -> handlePhongCho(sender);
            case "bosswait" -> handleBossWait(sender, args);
            case "bossfight" -> handleBossFight(sender);
            case "bossend" -> handleBossEnd(sender);
            case "kiemtraphongcho" -> handleKiemTraPhongCho(sender);
            case "taiphongcho" -> handleTaiPhongCho(sender);
            default -> {
                sender.sendMessage("§4[§c!§4] §cSử dụng /phoban help");
                yield true;
            }
        };
    }

    // --- Command handlers ---

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || !requirePlayer(sender) || args.length < 2) return true;
        Player p = (Player) sender;
        if (Game.game().containsKey(args[1])) { p.sendMessage(Messages.get("RoomExist")); return true; }
        EditorGui.open(p, args[1]);
        File file = new File(getDataFolder(), "room" + File.separator + args[1] + ".yml");
        try { file.createNewFile(); } catch (Exception ignored) {}
        return true;
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || !requirePlayer(sender) || args.length < 2) return true;
        Player p = (Player) sender;
        if (!Game.listGameWithoutCompleteSetup().contains(args[1])) { p.sendMessage(Messages.get("RoomNotExist")); return true; }
        EditorGui.open(p, args[1]);
        return true;
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length < 2) return true;
        if (args[1].equalsIgnoreCase("all") && args.length == 2) {
            List<String> types = Game.listType();
            if (types.isEmpty()) {
                sender.sendMessage(Messages.get("RoomNotExist"));
                return true;
            }
            OfflinePlayer[] players = Bukkit.getOfflinePlayers();
            int defaultTurn = FileManager.getFileConfig(FileManager.Files.CONFIG).getInt("Settings.DefaultTurn");
            Game.giveTurn(players, types, defaultTurn);
            sender.sendMessage(Messages.get("GiveTurnAll"));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage("§cSử dụng: /phoban add <player|all> <room|type> <amount>");
            return true;
        }

        String roomOrType = args[2];
        String resolvedType = resolveTypeFromRoomOrType(roomOrType);
        if (resolvedType == null) {
            sender.sendMessage(Messages.get("RoomNotExist"));
            return true;
        }

        int amount;
        try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException e) {
            sender.sendMessage(Messages.get("NotInt")); return true;
        }

        if (args[1].equalsIgnoreCase("all")) {
            List<String> onlyType = List.of(resolvedType);
            Game.giveTurn(Bukkit.getOfflinePlayers(), onlyType, amount);
            sender.sendMessage(Messages.get("GiveTurnAll"));
            return true;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) { sender.sendMessage(Messages.get("NotOnline")); return true; }
        Game.giveTurn(target, resolvedType, amount);
        sender.sendMessage(Messages.get("GiveTurn")
                .replace("<player>", playerName)
                .replace("<amount>", String.valueOf(amount)));
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 4) {
            sender.sendMessage("§cSử dụng: /phoban remove <player> <type> <amount>");
            return true;
        }
        String playerName = args[1];
        String type = args[2];
        int amount;
        try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException e) {
            sender.sendMessage(Messages.get("NotInt")); return true;
        }
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) { sender.sendMessage(Messages.get("NotOnline")); return true; }
        Game.takeTurn(target, type, amount);
        sender.sendMessage("§8[§a§l✔§8] §fBạn đã trừ §c" + amount + " lượt §fphó bản §e" + type + " §fcủa §b" + playerName);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!requireAdmin(sender)) return true;
        try {
            Game.shutdownAllActiveSessions(true, "&c&lPlugin dang reload, pho ban da bi huy.");
            addRewardsMap.clear();
            FileManager.setup(this);
            DebugLogger.log("Reload command executed by " + sender.getName());
            Game.load();
            sender.sendMessage("§2[§a!§2] §aĐã cài lại cấu hình và tải lại tất cả phòng");
        } catch (Exception ex) {
            ex.printStackTrace();
            Game.shutdownAllActiveSessions(false, null);
            sender.sendMessage("§cReload failed. Check console");
        }
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            sender.sendMessage("§8[§bDungeonsCore§8] §fDebug: §e" + DebugLogger.statusText());
            return true;
        }

        if (args[1].equalsIgnoreCase("player")) {
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /phoban debug player <player>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(Messages.get("NotOnline"));
                return true;
            }
            sender.sendMessage("§8[§bDungeonsCore Debug§8] §f" + Game.describePlayerDebug(target));
            return true;
        }

        if (args[1].equalsIgnoreCase("room")) {
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /phoban debug room <room>");
                return true;
            }
            Game game = Game.getGame(args[2]);
            if (game == null) {
                sender.sendMessage(Messages.get("RoomNotExist"));
                return true;
            }
            sender.sendMessage("§8[§bDungeonsCore Debug§8] §f" + game.debugSummary());
            return true;
        }

        if (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true")) {
            DebugLogger.setEnabled(true);
            sender.sendMessage("§8[§bDungeonsCore§8] §aDebug enabled.");
            DebugLogger.log("Debug enabled by " + sender.getName());
            return true;
        }

        if (args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("false")) {
            DebugLogger.log("Debug disabled by " + sender.getName());
            DebugLogger.setEnabled(false);
            sender.sendMessage("§8[§bDungeonsCore§8] §cDebug disabled.");
            return true;
        }

        sender.sendMessage("§cUsage: /phoban debug <on|off|status|player|room>");
        return true;
    }

    private boolean handleStart(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage(Messages.get("NotPlayer")); return true; }
        PlayerData data = PlayerData.get(p);
        if (data == null || !data.getGame().isLeader(p) || data.getGame().getStatus() != GameStatus.WAITING) return true;
        data.getGame().starting();
        return true;
    }

    private boolean handleHelp(CommandSender sender) {
        for (String msg : FileManager.getFileConfig(FileManager.Files.MESSAGE).getStringList("Help")) {
            sender.sendMessage(msg.replace("&", "§"));
        }
        if (sender.hasPermission("phoban.admin")) {
            sender.sendMessage("§f/phoban debug <on|off|status|player|room> §a- Debug plugin");
        }
        return true;
    }

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage(Messages.get("NotPlayer")); return true; }
        if (PlayerData.contains(p)) PlayerData.get(p).getGame().leave(p, true, true, false);
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!requireAdmin(sender)) return true;
        String rooms = String.join(" ", Game.listGameWithoutCompleteSetup());
        sender.sendMessage(Messages.get("ListRoom").replace("<rooms>", rooms));
        return true;
    }

    private boolean handleCheckRoom(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.get("RoomSetupCheck.Usage"));
            return true;
        }
        String roomName = args[1];
        File file = new File(getDataFolder(), "room" + File.separator + roomName + ".yml");
        if (!file.exists()) {
            sender.sendMessage(Messages.get("RoomNotExist"));
            return true;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        List<String> missing = Game.getRoomSetupMissingKeys(cfg);
        sender.sendMessage(Messages.get("RoomSetupCheck.Header").replace("<room>", roomName));
        if (missing.isEmpty()) {
            sender.sendMessage(Messages.get("RoomSetupCheck.AllOk").replace("<room>", roomName));
            return true;
        }
        for (String key : missing) {
            sender.sendMessage(Messages.get(key));
        }
        return true;
    }

    private boolean handleJoin(CommandSender sender, String[] args) {
        if (args.length < 2 || !(sender instanceof Player p)) {
            if (!(sender instanceof Player)) sender.sendMessage(Messages.get("NotPlayer"));
            return true;
        }
        if (!p.hasPermission("phoban.use")) {
            p.sendMessage(Messages.get("NoPermissions"));
            return true;
        }
        if (PlayerData.contains(p)) {
            PlayerData pd = PlayerData.get(p);
            Game cur = pd != null ? pd.getGame() : null;
            if (cur != null && (cur.getStatus() == GameStatus.PLAYING || cur.getStatus() == GameStatus.STARTING)) {
                p.sendMessage(Messages.get("CannotJoinLobbyWhilePlaying"));
                return true;
            }
        }
        Game game = Game.getGame(args[1]);
        if (game == null) { p.sendMessage(Messages.get("RoomNotExist")); return true; }
        if (game.getStatus() == GameStatus.WAITING || game.getStatus() == GameStatus.STARTING) {
            if (game.isFull()) { p.sendMessage(Messages.get("RoomFull")); return true; }
            if (!Game.canJoin(game.getConfig())) { p.sendMessage(Messages.get("JoinRoomNotConfig")); return true; }
            if (!Game.hasTurn(p, game.getType())) { p.sendMessage(Messages.get("NoTurn")); return true; }
            String denyMsg = Game.getJoinPermissionDenyMessage(p, args[1], game.getConfig());
            if (denyMsg != null) { p.sendMessage(denyMsg); return true; }
            boolean alreadyJoined = game.getPlayers().contains(p);
            if (!alreadyJoined && !game.hasDailyTicketAvailable()) {
                p.sendMessage(game.getDailyTicketLimitMessage());
                return true;
            }
            game.join(p);
            if (!alreadyJoined && game.getPlayers().contains(p)) {
                game.takeDailyTicket();
            }
            if (game.isLeader(p)) {
                if (FileManager.getFileConfig(FileManager.Files.CONFIG).getBoolean("Settings.AutoStartSingle", false)) {
                    game.starting();
                } else {
                    p.sendMessage(Messages.get("LeaderStart"));
                }
            }
        } else if (game.getStatus() == GameStatus.PLAYING) {
            p.sendMessage(Messages.get("RoomStarted"));
        }
        return true;
    }

    private boolean handleSetSpawn(CommandSender sender) {
        if (!requirePlayer(sender) || !requireAdmin(sender)) return true;
        Game.setGlobalSpawn(((Player) sender).getLocation());
        sender.sendMessage(Messages.get("GlobalSpawnSet"));
        return true;
    }

    private boolean handleTop(CommandSender sender) {
        if (sender instanceof Player p) TopGui.open(p);
        return true;
    }

    private boolean handleGivePoint(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length < 3) return true;
        String playerName = args[1];
        int point;
        try { point = Integer.parseInt(args[2]); } catch (NumberFormatException e) {
            sender.sendMessage(Messages.get("NotInt")); return true;
        }
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) { sender.sendMessage(Messages.get("NotOnline")); return true; }
        FileConfiguration data = FileManager.getFileConfig(FileManager.Files.DATA);
        String uuid = target.getUniqueId().toString();
        data.set(uuid + ".Point", data.getInt(uuid + ".Point", 0) + point);
        data.set(uuid + ".Name", target.getName());
        FileManager.saveFileConfig(data, FileManager.Files.DATA);
        sender.sendMessage(Messages.get("GivePoint").replace("<player>", playerName).replace("<amount>", String.valueOf(point)));
        return true;
    }

    private boolean handleTakePoint(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length < 3) return true;
        String playerName = args[1];
        int point;
        try { point = Integer.parseInt(args[2]); } catch (NumberFormatException e) {
            sender.sendMessage(Messages.get("NotInt")); return true;
        }
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) { sender.sendMessage(Messages.get("NotOnline")); return true; }
        FileConfiguration data = FileManager.getFileConfig(FileManager.Files.DATA);
        String uuid = target.getUniqueId().toString();
        int curPoint = data.getInt(uuid + ".Point", 0);
        if (curPoint - point < 0 && !FileManager.getFileConfig(FileManager.Files.CONFIG).getBoolean("Point.AllowNegative")) point = curPoint;
        data.set(uuid + ".Point", curPoint - point);
        data.set(uuid + ".Name", target.getName());
        FileManager.saveFileConfig(data, FileManager.Files.DATA);
        sender.sendMessage(Messages.get("TakePoint").replace("<player>", playerName).replace("<amount>", String.valueOf(point)));
        return true;
    }

    private boolean handleAddRewards(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || !requirePlayer(sender) || args.length < 2) return true;
        Player player = (Player) sender;
        ItemStack itemHand = player.getInventory().getItemInMainHand();
        if (itemHand.getType() == Material.AIR) {
            player.sendMessage(Messages.get("Error").replace("<error>", "Không có vật phẩm trên tay."));
            return true;
        }
        Game game = Game.getGame(args[1]);
        if (game == null) { sender.sendMessage(Messages.get("RoomNotExist")); return true; }
        File configFile = new File(getDataFolder(), "room" + File.separator + game.getName() + ".yml");
        if (!configFile.exists()) try { configFile.createNewFile(); } catch (Exception ignored) {}
        String id = UUID.randomUUID().toString();
        FileConfiguration room = game.getConfig();
        room.set("Reward." + id + ".Item", itemHand);
        room.set("Reward." + id + ".Chance", 100);
        FileManager.saveFileConfig(room, configFile);
        RewardGui.editor.put(player, game.getName() + ":editchance:" + id + ":2");
        player.sendMessage(Messages.get("EditChance"));
        addRewardsMap.put(player.getName(), game.getName());
        return true;
    }

    private boolean handlePhongCho(CommandSender sender) {
        if (!requireAdmin(sender) || !requirePlayer(sender)) return true;
        Game.setGlobalWaitingRoom(((Player) sender).getLocation());
        sender.sendMessage(Messages.get("WaitingRoomSet"));
        return true;
    }

    private boolean handleBossWait(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || !requirePlayer(sender)) return true;
        if (args.length < 2) { sender.sendMessage("§4[§c!§4] §cSử dụng /phoban bosswait <tên phòng>"); return true; }
        if (Game.getGame(args[1]) == null) { sender.sendMessage(Messages.get("RoomNotExist")); return true; }
        Game.setBossWaitingRoom(args[1], ((Player) sender).getLocation());
        sender.sendMessage(Messages.get("BossWaitingRoomSet"));
        return true;
    }

    private boolean handleBossFight(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage(Messages.get("NotPlayer")); return true; }
        PlayerData data = PlayerData.get(p);
        if (data == null) return true;
        if (data.getGame().isLeader(p)) data.getGame().startBossFight();
        else data.getGame().teleportToBossWaitingRoom(p);
        return true;
    }

    private boolean handleBossEnd(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage(Messages.get("NotPlayer")); return true; }
        PlayerData data = PlayerData.get(p);
        if (data == null) return true;
        if (data.getGame().isLeader(p)) data.getGame().endBossFight();
        else sender.sendMessage("§4[§c!§4] §cChỉ người lãnh đạo mới có thể kết thúc trận đánh boss!");
        return true;
    }

    private boolean handleKiemTraPhongCho(CommandSender sender) {
        if (!requireAdmin(sender)) return true;
        Location wr = Game.getGlobalWaitingRoom();
        if (wr != null) {
            sender.sendMessage("§a§lPhòng chờ tại: §f" + wr.getWorld().getName()
                    + ", X: " + Math.round(wr.getX()) + ", Y: " + Math.round(wr.getY()) + ", Z: " + Math.round(wr.getZ()));
        } else {
            sender.sendMessage("§c§lPhòng chờ chưa được thiết lập. Sử dụng /phoban phongcho");
        }
        for (var entry : Game.game().entrySet()) {
            boolean hasWR = entry.getValue().getWaitingRoom() != null;
            sender.sendMessage("§f- " + entry.getKey() + ": " + (hasWR ? "§aĐã có phòng chờ" : "§cChưa có phòng chờ"));
        }
        return true;
    }

    private boolean handleTaiPhongCho(CommandSender sender) {
        if (!requireAdmin(sender)) return true;
        Location wr = Game.getGlobalWaitingRoom();
        if (wr == null) { sender.sendMessage("§c§lPhòng chờ chưa được thiết lập."); return true; }
        for (Game g : Game.game().values()) g.setWaitingRoom(wr.clone());
        sender.sendMessage("§a§lĐã tải lại phòng chờ cho tất cả phòng phó bản!");
        return true;
    }

    // --- Helper methods ---

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("phoban.admin")) { sender.sendMessage(Messages.get("NoPermissions")); return false; }
        return true;
    }

    private boolean requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(Messages.get("NotPlayer")); return false; }
        return true;
    }

    private String resolveTypeFromRoomOrType(String roomOrType) {
        Game byRoom = Game.getGame(roomOrType);
        if (byRoom != null) {
            String roomType = byRoom.getType();
            if (roomType == null || roomType.isBlank()) {
                return null;
            }
            return roomType;
        }
        for (String type : Game.listType()) {
            if (type != null && type.equalsIgnoreCase(roomOrType)) {
                return type;
            }
        }
        return null;
    }

    private void registerListeners(Listener... listeners) {
        for (Listener l : listeners) Bukkit.getPluginManager().registerEvents(l, this);
    }

    private void setupPermissions() {
        if (!IntegrationConfig.isLuckPermsEnabled()) return;
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
            if (provider != null) luckperms = provider.getProvider();
        }
    }

    private void startTurnResetTask() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            Calendar c = Calendar.getInstance();
            FileConfiguration cfg = FileManager.getFileConfig(FileManager.Files.CONFIG);
            boolean everyDay = cfg.getString("Settings.AutoReset", "day").equalsIgnoreCase("day");
            boolean checkHour = !everyDay || c.get(Calendar.HOUR_OF_DAY) == 0;
            if (checkHour && c.get(Calendar.MINUTE) == 0 && c.get(Calendar.SECOND) == 0) {
                int defaultTurn = cfg.getInt("Settings.DefaultTurn");
                Game.giveTurnChangeDay(Bukkit.getOfflinePlayers(), Game.listType(), defaultTurn);
            }
        }, 20L, 20L);
    }

    public BukkitAPIHelper getBukkitAPIHelper() { return bukkitAPIHelper; }

    public static LuckPerms getLuckPerms() { return luckperms; }
}
