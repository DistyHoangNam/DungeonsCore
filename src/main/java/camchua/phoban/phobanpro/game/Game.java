package camchua.phoban.phobanpro.game;

import camchua.phoban.phobanpro.DungeonsCore;
import camchua.phoban.phobanpro.integration.MMOItemsRewardHook;
import camchua.phoban.phobanpro.manager.FileManager;
import camchua.phoban.phobanpro.mythicmobs.BukkitAPIHelper;
import camchua.phoban.phobanpro.utils.ColorUtils;
import camchua.phoban.phobanpro.utils.DebugLogger;
import camchua.phoban.phobanpro.utils.Messages;
import camchua.phoban.phobanpro.utils.Random;
import camchua.phoban.phobanpro.utils.Utils;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class Game {

    public static final int MAX_STAGES = 10;

    private static final LinkedHashMap<String, Game> games = new LinkedHashMap<>();
    private static final String DAILY_TICKET_DATA_PATH = "RoomDailyTickets";
    private static final Set<String> LOGGED_SPAWN_FAILURES = new HashSet<>();

    private final String name;
    private final String type;
    /** Tên hiển thị (GUI); vé/lượt vẫn dùng {@link #type}. */
    private final String typeDisplay;
    private final File configFile;
    private final List<GameMob> boss;
    private final Map<String, Integer> totalKill = new HashMap<>();
    private final Map<String, Double> totalDamage = new HashMap<>();
    private final List<GameSpawnQueue> queueList = new ArrayList<>();

    private GameStatus status;
    private GameTask task;
    private BukkitTask scheduledTask;
    private int maxTime;
    private Location spawn;
    private Location waitingRoom;
    private Location bossWaitingRoom;
    private int maxPlayers;
    private int time;
    private List<Player> players;
    private boolean bossRoomLocked = false;
    private boolean completed = false;

    @SuppressWarnings("unchecked")
    private final LinkedHashMap<String, List<GameMob>>[] stageData = new LinkedHashMap[MAX_STAGES];
    private final List<HashMap<String, List<GameMob>>> flatStages = new ArrayList<>();

    private int currentStageIndex;
    private int stageCount;
    private HashMap<String, Integer> currentProgress;
    private FileConfiguration room;
    private int realStage = 1;
    private int realTurn = 0;
    private int totalTurn = 0;
    public boolean stageCountdown = false;
    public boolean quitCountdown = false;
    private long lastSpawn = 0L;
    private boolean entryWarmup = false;
    private long entryWarmupUntil = 0L;
    private BukkitTask entryWarmupTask;

    // --- Static accessors ---

    public static LinkedHashMap<String, Game> game() {
        return games;
    }

    public static Game getGame(String name) {
        return games.get(name);
    }

    public static List<String> listGame() {
        return new ArrayList<>(games.keySet());
    }

    public static List<String> listGameWithoutCompleteSetup() {
        File folder = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator);
        if (!folder.exists()) folder.mkdirs();
        List<String> result = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null) return result;
        for (File file : files) {
            if (file.getName().endsWith(".yml")) {
                result.add(file.getName().replace(".yml", ""));
            }
        }
        return result;
    }

    public static void convertData() {
        File dataFolder = DungeonsCore.inst().getDataFolder();
        migrateRoomFile(new File(dataFolder, "room.yml"), true, false);
        migrateRoomFile(new File(dataFolder, "phoban.yml"), false, true);
        migrateRoomFile(new File(dataFolder, "game" + File.separator + "phoban.yml"), false, true);
    }

    public static List<String> listType() {
        Set<String> types = new LinkedHashSet<>();
        for (Game g : games.values()) {
            types.add(g.getType());
        }
        return new ArrayList<>(types);
    }

    public static void load() {
        File folder = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator);
        if (!folder.exists()) folder.mkdirs();
        File[] files = folder.listFiles();
        for (Game game : new ArrayList<>(games.values())) {
            game.cancelTask();
        }
        games.clear();
        if (files == null) {
            DungeonsCore.inst().getLogger().warning("Could not list room folder: " + folder.getPath());
            return;
        }
        int loaded = 0;
        for (File file : files) {
            if (!file.getName().endsWith(".yml")) continue;
            String name = file.getName().replace(".yml", "");
            YamlConfiguration room = new YamlConfiguration();
            try {
                room.load(file);
            } catch (Exception e) {
                DungeonsCore.inst().getLogger().log(Level.WARNING,
                        "Skipped room '" + name + "' because its YAML could not be loaded: " + file.getPath(), e);
                continue;
            }
            List<String> invalidReasons = getRoomLoadInvalidReasons(room);
            if (!invalidReasons.isEmpty()) {
                DungeonsCore.inst().getLogger().warning("Skipped room '" + name
                        + "' because setup is incomplete: " + String.join(", ", invalidReasons));
                DebugLogger.log("Room skipped: " + name + ", reasons=" + String.join(", ", invalidReasons));
                continue;
            }
            Game.load(name, room, file);
            loaded++;
            DungeonsCore.inst().getLogger().info("Loaded room '" + name + "' from " + file.getPath());
            DebugLogger.log("Room loaded: " + name + ", type=" + room.getString("Type", "")
                    + ", stages=" + countConfiguredStages(room)
                    + ", hasBoss=" + room.contains("Boss"));
        }
        DungeonsCore.inst().getLogger().info("Loaded " + loaded + " room(s) from " + folder.getPath());
        DebugLogger.log("Room load complete. loaded=" + loaded + ", folder=" + folder.getPath());
    }

    private static int countConfiguredStages(FileConfiguration room) {
        int count = 0;
        for (int i = 1; i <= MAX_STAGES; i++) {
            if (room.contains("Mob" + i) && room.getConfigurationSection("Mob" + i) != null
                    && !room.getConfigurationSection("Mob" + i).getKeys(false).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public static void shutdownAllActiveSessions(boolean teleportPlayers, String reason) {
        List<Game> snapshot = new ArrayList<>(games.values());
        for (Game game : snapshot) {
            try {
                game.cancelTask();
                if (teleportPlayers) {
                    if (reason != null && !reason.isBlank()) {
                        game.forceKickAll(reason);
                    } else {
                        game.forceStopAndReset();
                    }
                } else {
                    game.fullReset();
                }
            } catch (Exception ex) {
                DungeonsCore.inst().getLogger().log(Level.WARNING,
                        "Could not clean up room '" + game.getName() + "' during shutdown/reload", ex);
            }
        }

        for (EntityData entityData : new ArrayList<>(EntityData.data().values())) {
            Entity entity = entityData.getEntity();
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        EntityData.data().clear();
        PlayerData.data().clear();
        GameStatistic.protect.clear();
        GameStatistic.entryProtectUntil.clear();
        GameStatistic.lastDeath.clear();
        LOGGED_SPAWN_FAILURES.clear();
        games.clear();
    }

    public static void load(String name, FileConfiguration room, File configFile) {
        Game old = games.remove(name);
        if (old != null) old.cancelTask();

        int time = room.getInt("Time") * 60;
        Location spawn = (Location) room.get("Spawn");
        int maxPlayers = room.getInt("Player");

        List<GameMob> boss = new ArrayList<>();
        if (room.contains("Boss")) {
            String bossType = room.getString("Boss.Type");
            int bossAmount = room.getInt("Boss.Amount");
            boss.add(new GameMob("", bossType, bossAmount));
        }

        String roomType = room.getString("Type");
        String rawDisplay = room.getString("Display", "");
        if (rawDisplay == null || rawDisplay.isBlank()) {
            rawDisplay = roomType != null ? roomType : "";
        }
        Game g = new Game(name, time, spawn, maxPlayers, boss, room, roomType, rawDisplay, configFile);
        g.waitingRoom = Game.getGlobalWaitingRoom();

        for (int i = 0; i < MAX_STAGES; i++) {
            String mobKey = "Mob" + (i + 1);
            if (!room.contains(mobKey) || room.getConfigurationSection(mobKey) == null) continue;
            Set<String> mobList = room.getConfigurationSection(mobKey).getKeys(false);
            if (mobList.isEmpty()) continue;

            LinkedHashMap<String, List<GameMob>> stageHash = new LinkedHashMap<>();

            for (String key : mobList) {
                String prefix = mobKey + "." + key;
                String type = room.getString(prefix + ".Type");
                int amount = room.getInt(prefix + ".Amount");

                List<GameMob> mobs = new ArrayList<>();
                mobs.add(new GameMob(key, type, amount));

                if (room.getConfigurationSection(prefix) != null) {
                    for (String childKey : room.getConfigurationSection(prefix).getKeys(false)) {
                        if (Set.of("Location", "Type", "Amount", "Time").contains(childKey)) continue;
                        String childType = room.getString(prefix + "." + childKey + ".Type");
                        int childAmount = room.getInt(prefix + "." + childKey + ".Amount");
                        mobs.add(new GameMob(childKey, childType, childAmount));
                    }
                }

                stageHash.put(key, mobs);
            }
            g.stageData[i] = stageHash;
        }

        g.init();
        games.put(name, g);
    }

    public static void deleteRoom(String room) {
        Game old = games.remove(room);
        if (old != null) old.cancelTask();
    }

    private static void migrateRoomFile(File oldFile, boolean deleteAfter, boolean onlyRoomLikeSections) {
        if (!oldFile.exists() || !oldFile.isFile()) return;
        YamlConfiguration source = new YamlConfiguration();
        try {
            source.load(oldFile);
        } catch (Exception e) {
            DungeonsCore.inst().getLogger().log(Level.WARNING,
                    "Could not migrate old room data from " + oldFile.getPath(), e);
            return;
        }

        int candidates = 0;
        int failures = 0;
        int migrated = 0;
        for (String key : source.getKeys(false)) {
            if (source.getConfigurationSection(key) == null) continue;
            if (onlyRoomLikeSections && !looksLikeRoomSection(source, key)) continue;
            candidates++;

            File newFile = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + key + ".yml");
            if (newFile.exists()) continue;
            File parent = newFile.getParentFile();
            if (parent != null) parent.mkdirs();

            YamlConfiguration config = new YamlConfiguration();
            Utils.scanSection(source, config, key, key);
            try {
                config.save(newFile);
                migrated++;
                DungeonsCore.inst().getLogger().info("Migrated room '" + key + "' -> " + newFile.getPath());
            } catch (Exception e) {
                failures++;
                DungeonsCore.inst().getLogger().log(Level.WARNING,
                        "Could not save migrated room '" + key + "' to " + newFile.getPath(), e);
            }
        }

        if (deleteAfter && candidates > 0 && failures == 0 && !oldFile.delete()) {
            DungeonsCore.inst().getLogger().warning("Migrated room data but could not delete old file: " + oldFile.getPath());
        }
    }

    private static boolean looksLikeRoomSection(FileConfiguration source, String key) {
        if (source.contains(key + ".Prefix")
                || source.contains(key + ".Player")
                || source.contains(key + ".Time")
                || source.contains(key + ".Reward")
                || source.contains(key + ".RewardAmount")
                || source.contains(key + ".Spawn")
                || source.contains(key + ".Boss")) {
            return true;
        }
        for (int i = 1; i <= MAX_STAGES; i++) {
            if (source.contains(key + ".Mob" + i)) return true;
        }
        return false;
    }

    private static List<String> getRoomLoadInvalidReasons(FileConfiguration room) {
        List<String> reasons = getRoomSetupMissingKeys(room);
        if (reasons.isEmpty() && !(room.get("Spawn") instanceof Location)) {
            reasons.add("Spawn is not a valid Bukkit Location");
        }
        String type = room.getString("Type", "");
        if (reasons.isEmpty() && (type == null || type.isBlank())) {
            reasons.add("Type is empty");
        }
        return reasons;
    }

    public static boolean canJoin(FileConfiguration room) {
        int mobStages = 0;
        for (int i = 1; i <= MAX_STAGES; i++) {
            if (room.contains("Mob" + i) && room.getConfigurationSection("Mob" + i) != null
                    && !room.getConfigurationSection("Mob" + i).getKeys(false).isEmpty()) {
                mobStages++;
            }
        }
        if (room.contains("Boss")) mobStages++;
        return room.contains("Prefix") && room.contains("Player") && room.contains("Time")
                && room.contains("Reward") && mobStages >= 1
                && room.contains("RewardAmount") && room.contains("Spawn") && room.contains("Type");
    }

    /**
     * Danh sách key message (RoomSetupMissing.*) cho từng mục chưa đạt — trùng logic {@link #canJoin(FileConfiguration)}.
     */
    public static List<String> getRoomSetupMissingKeys(FileConfiguration room) {
        List<String> missing = new ArrayList<>();
        if (room == null) {
            missing.add("RoomSetupMissing.Invalid");
            return missing;
        }
        if (!room.contains("Prefix")) {
            missing.add("RoomSetupMissing.Prefix");
        }
        if (!room.contains("Player")) {
            missing.add("RoomSetupMissing.Player");
        }
        if (!room.contains("Time")) {
            missing.add("RoomSetupMissing.Time");
        }
        if (!room.contains("Reward")) {
            missing.add("RoomSetupMissing.Reward");
        }
        int mobStages = 0;
        for (int i = 1; i <= MAX_STAGES; i++) {
            if (room.contains("Mob" + i) && room.getConfigurationSection("Mob" + i) != null
                    && !room.getConfigurationSection("Mob" + i).getKeys(false).isEmpty()) {
                mobStages++;
            }
        }
        if (room.contains("Boss")) {
            mobStages++;
        }
        if (mobStages < 1) {
            missing.add("RoomSetupMissing.MobOrBoss");
        }
        if (!room.contains("RewardAmount")) {
            missing.add("RoomSetupMissing.RewardAmount");
        }
        if (!room.contains("Spawn")) {
            missing.add("RoomSetupMissing.Spawn");
        }
        if (!room.contains("Type")) {
            missing.add("RoomSetupMissing.Type");
        }
        return missing;
    }

    // --- Turn management (UUID-based data storage) ---

    public static int getTurn(OfflinePlayer p, String type) {
        FileConfiguration data = FileManager.getFileConfig(FileManager.Files.DATA);
        return data.getInt(p.getUniqueId() + ".Turn." + type, 1);
    }

    public static void setTurn(OfflinePlayer p, String type, int amount) {
        FileConfiguration data = FileManager.getFileConfig(FileManager.Files.DATA);
        data.set(p.getUniqueId() + ".Turn." + type, amount);
        data.set(p.getUniqueId() + ".Name", p.getName());
        FileManager.saveFileConfig(data, FileManager.Files.DATA);
    }

    public static void giveTurn(OfflinePlayer p, String type, int amount) {
        setTurn(p, type, getTurn(p, type) + amount);
    }

    public static void takeTurn(OfflinePlayer p, String type, int amount) {
        int current = getTurn(p, type);
        setTurn(p, type, Math.max(0, current - amount));
    }

    public static void giveTurn(OfflinePlayer[] listPlayer, List<String> types, int amount) {
        FileConfiguration data = FileManager.getFileConfig(FileManager.Files.DATA);
        for (OfflinePlayer player : listPlayer) {
            for (String type : types) {
                data.set(player.getUniqueId() + ".Turn." + type, getTurn(player, type) + amount);
                data.set(player.getUniqueId() + ".Name", player.getName());
            }
        }
        FileManager.saveFileConfig(data, FileManager.Files.DATA);
    }

    public static void giveTurnChangeDay(OfflinePlayer[] listPlayer, List<String> types, int amount) {
        FileConfiguration data = FileManager.getFileConfig(FileManager.Files.DATA);
        for (OfflinePlayer player : listPlayer) {
            for (String type : types) {
                int current = getTurn(player, type);
                if (current > 0) continue;
                data.set(player.getUniqueId() + ".Turn." + type, current + amount);
                data.set(player.getUniqueId() + ".Name", player.getName());
            }
        }
        FileManager.saveFileConfig(data, FileManager.Files.DATA);
    }

    public static boolean hasTurn(OfflinePlayer p, String type) {
        return getTurn(p, type) > 0;
    }

    public int getDailyTicketLimit() {
        return Math.max(0, room.getInt("DailyMaxTickets", 0));
    }

    public int getDailyTicketUsed() {
        int limit = getDailyTicketLimit();
        if (limit <= 0) return 0;
        FileConfiguration data = FileManager.getFileConfig(FileManager.Files.DATA);
        boolean changed = refreshDailyTicketDate(data);
        int used = data.getInt(getDailyTicketPath() + ".Used", 0);
        if (changed) FileManager.saveFileConfig(data, FileManager.Files.DATA);
        return Math.max(0, used);
    }

    public boolean hasDailyTicketAvailable() {
        int limit = getDailyTicketLimit();
        return limit <= 0 || getDailyTicketUsed() < limit;
    }

    public void takeDailyTicket() {
        int limit = getDailyTicketLimit();
        if (limit <= 0) return;
        FileConfiguration data = FileManager.getFileConfig(FileManager.Files.DATA);
        refreshDailyTicketDate(data);
        String path = getDailyTicketPath();
        int used = Math.max(0, data.getInt(path + ".Used", 0));
        data.set(path + ".Used", used + 1);
        FileManager.saveFileConfig(data, FileManager.Files.DATA);
    }

    public String getDailyTicketLimitMessage() {
        int max = getDailyTicketLimit();
        int used = getDailyTicketUsed();
        int left = Math.max(0, max - used);
        return Messages.get("RoomDailyTicketLimit")
                .replace("<room>", name)
                .replace("<used>", String.valueOf(used))
                .replace("<max>", String.valueOf(max))
                .replace("<left>", String.valueOf(left))
                .replace("<reset>", "00:00");
    }

    private String getDailyTicketPath() {
        return DAILY_TICKET_DATA_PATH + "." + name;
    }

    private boolean refreshDailyTicketDate(FileConfiguration data) {
        String path = getDailyTicketPath();
        String today = LocalDate.now().toString();
        if (today.equals(data.getString(path + ".Date"))) return false;
        data.set(path + ".Date", today);
        data.set(path + ".Used", 0);
        return true;
    }

    /**
     * Kiểm tra quyền vào phòng. Nếu phòng có cấu hình Permission thì player phải có perm mới được vào.
     * @return null nếu được phép vào, ngược lại trả về message từ chối (đã colorize).
     */
    public static String getJoinPermissionDenyMessage(Player p, String roomName, FileConfiguration roomConfig) {
        String perm = roomConfig == null ? null : roomConfig.getString("Permission", "");
        if (perm == null || perm.isEmpty()) return null;
        if (p.hasPermission(perm)) return null;
        String custom = roomConfig.getString("NoPermissionMessage", null);
        if (custom != null && !custom.isEmpty())
            return ColorUtils.colorize(custom.replace("<room>", roomName));
        return Messages.get("NoRoomPermission").replace("<room>", roomName);
    }

    // --- Global spawn/waiting room ---

    public static void setGlobalSpawn(Location loc) {
        FileConfiguration config = FileManager.getFileConfig(FileManager.Files.SPAWN);
        config.set("s", loc.clone());
        FileManager.saveFileConfig(config, FileManager.Files.SPAWN);
    }

    public static Location getGlobalSpawn() {
        FileConfiguration config = FileManager.getFileConfig(FileManager.Files.SPAWN);
        Object obj = config.get("s", null);
        return obj instanceof Location ? (Location) obj : null;
    }

    /**
     * Điểm đá an toàn khi không có global spawn và không còn PlayerData (reload / trạng thái lệch).
     */
    public static Location fallbackExitLocation(Player p) {
        if (p == null) return null;
        Location bed = p.getBedSpawnLocation();
        if (bed != null && bed.getWorld() != null) return bed;
        if (!Bukkit.getWorlds().isEmpty()) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        return null;
    }

    public static void setGlobalWaitingRoom(Location loc) {
        FileConfiguration config = FileManager.getFileConfig(FileManager.Files.SPAWN);
        config.set("waiting_room", loc.clone());
        FileManager.saveFileConfig(config, FileManager.Files.SPAWN);
        Bukkit.getLogger().info("[DungeonsCore] Đã thiết lập phòng chờ tại: " + loc.getWorld().getName()
                + ", X: " + Math.round(loc.getX()) + ", Y: " + Math.round(loc.getY()) + ", Z: " + Math.round(loc.getZ()));
        for (Game game : games.values()) {
            game.waitingRoom = loc.clone();
        }
    }

    public static Location getGlobalWaitingRoom() {
        FileConfiguration config = FileManager.getFileConfig(FileManager.Files.SPAWN);
        Object obj = config.get("waiting_room", null);
        return (obj instanceof Location loc) ? loc : null;
    }

    public static void setBossWaitingRoom(String roomName, Location loc) {
        File configFile = new File(DungeonsCore.inst().getDataFolder(), "room" + File.separator + roomName + ".yml");
        if (!configFile.exists()) return;
        YamlConfiguration room = YamlConfiguration.loadConfiguration(configFile);
        room.set("BossWaitingRoom", loc.clone());
        try { room.save(configFile); } catch (Exception ignored) {}
        Game g = games.get(roomName);
        if (g != null) g.bossWaitingRoom = loc.clone();
    }

    // --- Constructor ---

    public Game(String name, int time, Location spawn, int maxPlayers, List<GameMob> boss,
                FileConfiguration room, String type, String typeDisplay, File configFile) {
        this.name = name;
        this.status = GameStatus.WAITING;
        this.maxTime = time;
        this.spawn = spawn;
        this.maxPlayers = maxPlayers;
        this.time = time;
        this.players = new ArrayList<>();
        this.boss = boss;
        this.currentStageIndex = 1;
        this.stageCount = -1;
        this.currentProgress = new HashMap<>();
        this.room = room;
        this.configFile = configFile;
        this.type = type;
        this.typeDisplay = typeDisplay != null && !typeDisplay.isBlank() ? typeDisplay : (type != null ? type : "");
        this.waitingRoom = Game.getGlobalWaitingRoom();
        Object bossWaitObj = room.get("BossWaitingRoom", null);
        this.bossWaitingRoom = bossWaitObj instanceof Location ? (Location) bossWaitObj : null;
    }

    // --- Instance methods ---

    public String getName() { return name; }
    public GameStatus getStatus() { return status; }
    public void setStatus(GameStatus status) { this.status = status; }
    public List<Player> getPlayers() { return players; }
    public int getTimeLeft() { return time; }
    public FileConfiguration getConfig() { return room; }
    public void setConfig(FileConfiguration config) { this.room = config; }
    public File getConfigFile() { return configFile; }
    public String getType() { return type; }
    public boolean isCompleted() { return completed; }
    public boolean isEntryWarmup() { return entryWarmup; }
    public int getEntryWarmupRemainingSeconds() {
        long remaining = entryWarmupUntil - System.currentTimeMillis();
        return remaining <= 0L ? 0 : (int) Math.ceil(remaining / 1000.0);
    }

    /** Tên hiển thị cho GUI; nếu không cấu hình Display trong room thì trùng {@link #getType()}. */
    public String getTypeDisplay() { return typeDisplay; }

    /** Lấy tên hiển thị cho một id vé nội bộ (dùng phòng đầu tiên cùng Type). */
    public static String getTypeDisplayForInternalType(String internalType) {
        if (internalType == null) return "";
        for (Game g : games.values()) {
            if (g.type != null && g.type.equalsIgnoreCase(internalType)) {
                return g.getTypeDisplay();
            }
        }
        return internalType;
    }
    public int getRealTurn() { return realTurn; }
    public int getTotalTurn() { return totalTurn; }

    public void time() {
        if (time > 0) {
            --time;
        }
    }

    public void resetTime() { time = maxTime; }
    public void clearCurrentStage() { clearMobs(); }

    private void debugWaiting(String message) {
        boolean waitingDebug = FileManager.getFileConfig(FileManager.Files.CONFIG)
                .getBoolean("Settings.DebugWaitingRoom", false);
        DebugLogger.logWaiting(waitingDebug, "room=" + name
                + " status=" + status
                + " players=" + players.size() + "/" + maxPlayers
                + " entryWarmup=" + entryWarmup
                + " :: " + message);
    }

    private static String formatLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName()
                + " " + Math.round(loc.getX())
                + "," + Math.round(loc.getY())
                + "," + Math.round(loc.getZ());
    }

    private static boolean isNear(Location a, Location b, double maxDistance) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return false;
        if (!a.getWorld().equals(b.getWorld())) return false;
        return a.distanceSquared(b) <= maxDistance * maxDistance;
    }

    public String debugSummary() {
        return "room=" + name
                + ", status=" + status
                + ", players=" + players.size() + "/" + maxPlayers
                + ", entryWarmup=" + entryWarmup
                + ", warmupLeft=" + getEntryWarmupRemainingSeconds() + "s"
                + ", timeLeft=" + time
                + ", stage=" + getStageDebugName()
                + ", progress=" + currentProgress
                + ", queue=" + queueList.size()
                + ", waitingRoom=" + formatLoc(getWaitingRoom())
                + ", spawn=" + formatLoc(spawn)
                + ", bossWaitingRoom=" + formatLoc(bossWaitingRoom)
                + ", playersList=" + players.stream().map(Player::getName).toList();
    }

    public static String describePlayerDebug(Player player) {
        PlayerData pd = PlayerData.get(player);
        if (pd == null) {
            return "player=" + player.getName()
                    + ", hasPlayerData=false"
                    + ", loc=" + formatLoc(player.getLocation())
                    + ", protected=" + DeathHandler.isProtected(player);
        }
        Game game = pd.getGame();
        boolean listed = game != null && game.getPlayers().contains(player);
        boolean nearWaiting = game != null && isNear(player.getLocation(), game.getWaitingRoom(), 12.0);
        return "player=" + player.getName()
                + ", hasPlayerData=true"
                + ", room=" + (game != null ? game.getName() : "null")
                + ", status=" + (game != null ? game.getStatus() : "null")
                + ", listed=" + listed
                + ", loc=" + formatLoc(player.getLocation())
                + ", previousLoc=" + formatLoc(pd.getLocation())
                + ", waitingRoom=" + (game != null ? formatLoc(game.getWaitingRoom()) : "null")
                + ", nearWaitingRoom=" + nearWaiting
                + ", respawning=" + pd.isRespawning()
                + ", respawnLeft=" + pd.remainRespawn()
                + ", protected=" + DeathHandler.isProtected(player)
                + ", entryProtectMs=" + DeathHandler.getEntryProtectRemainingMillis(player);
    }

    public void auditWaitingState() {
        for (PlayerData pd : new ArrayList<>(PlayerData.data().values())) {
            if (pd == null || pd.getGame() != this) continue;
            Player player = pd.getPlayer();
            if (player != null && !players.contains(player)) {
                debugWaiting("suspicious stale PlayerData: player not in room list player=" + player.getName());
            }
        }
        if (status == GameStatus.WAITING || status == GameStatus.STARTING) {
            for (Player player : new ArrayList<>(players)) {
                if (!PlayerData.contains(player)) {
                    debugWaiting("suspicious waiting state: listed player missing PlayerData player=" + player.getName());
                    continue;
                }
                if (!isNear(player.getLocation(), getWaitingRoom(), 12.0)) {
                    debugWaiting("suspicious waiting state: player not near waiting room player="
                            + player.getName()
                            + " loc=" + formatLoc(player.getLocation())
                            + " waitingRoom=" + formatLoc(getWaitingRoom()));
                }
            }
        }
    }

    public void init() {
        initStage();
        task = new GameTask(this);
        scheduledTask = Bukkit.getScheduler().runTaskTimer(DungeonsCore.inst(), task, 20L, 20L);
    }

    private void cancelTask() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel();
        }
        cancelEntryWarmupTask();
    }

    private void initStage() {
        flatStages.clear();
        for (int i = 0; i < MAX_STAGES; i++) {
            if (stageData[i] == null) continue;
            for (String key : stageData[i].keySet()) {
                HashMap<String, List<GameMob>> h = new HashMap<>();
                h.put(key, stageData[i].get(key));
                flatStages.add(h);
            }
        }
        if (!boss.isEmpty()) {
            HashMap<String, List<GameMob>> h = new HashMap<>();
            h.put("Boss", boss);
            flatStages.add(h);
        }
        totalTurn = flatStages.size();
    }

    public void addProgress(String key, int value) {
        if (!canRunStageLogic()) return;
        if (key == null || key.isEmpty() || value <= 0) return;
        currentProgress.merge(key, value, Integer::sum);
        debugStage("progress +" + value + " " + key + " -> " + currentProgress.getOrDefault(key, 0)
                + "/" + getProgressMax(key));
    }

    public List<GameMob> getStage(int s) {
        if (s < 0 || s >= flatStages.size()) return new ArrayList<>();
        var entry = flatStages.get(s).entrySet().iterator();
        return entry.hasNext() ? entry.next().getValue() : new ArrayList<>();
    }

    public String getStageKey(int s, int i) {
        if (s < 0 || s >= flatStages.size()) return "";
        int index = 0;
        for (List<GameMob> mobs : flatStages.get(s).values()) {
            for (GameMob mob : mobs) {
                if (index == i) return mob.getKey();
                index++;
            }
        }
        return "";
    }

    public Set<String> getStageKey(int s) {
        if (s < 0 || s >= flatStages.size()) return Set.of();
        return flatStages.get(s).keySet();
    }

    public int keyToStage(String key) {
        for (int i = 0; i < MAX_STAGES; i++) {
            if (stageData[i] == null) continue;
            if (stageData[i].containsKey(key)) return i + 1;
        }
        return MAX_STAGES + 1;
    }

    public boolean hasStage(int s) {
        if (s > MAX_STAGES) return true;
        if (s < 1) return false;
        return stageData[s - 1] != null;
    }

    public int keyToTurn(String key) {
        for (int i = 0; i < MAX_STAGES; i++) {
            if (stageData[i] == null) continue;
            int turn = 0;
            for (String k : stageData[i].keySet()) {
                if (k.equals(key)) return turn + 1;
                turn++;
            }
        }
        return 0;
    }

    public void checkStage() {
        if (!canRunStageLogic()) return;
        reconcileStageProgress();
        trimExcessTrackedMobs();
        if (Utils.checkStage(getStage(stageCount), currentProgress)) {
            debugStage("stage cleared, moving to next stage");
            newStage();
        }
    }

    private void reconcileStageProgress() {
        if (stageCount < 0 || stageCount >= flatStages.size()) return;

        List<EntityData> snapshot = new ArrayList<>(EntityData.data().values());
        for (EntityData data : snapshot) {
            if (!this.equals(data.getGame())) continue;
            Entity entity = data.getEntity();
            if (entity != null && !entity.isDead() && entity.isValid()) continue;

            EntityData removed = EntityData.data().remove(entity);
            if (removed == null) continue;
            String mobKey = removed.getMobKey();
            removed.clearDamageCredit();
            respawnLostMob(mobKey);
        }
    }

    private boolean hasPendingSpawnQueue() {
        for (GameSpawnQueue queue : queueList) {
            if (queue.hasRemaining() && queue.getMobKey() != null && !queue.getMobKey().isEmpty()) return true;
        }
        return false;
    }

    private int countLiveTrackedMobs(String mobKey) {
        if (mobKey == null || mobKey.isEmpty()) return 0;
        int count = 0;
        for (EntityData data : EntityData.data().values()) {
            if (!this.equals(data.getGame())) continue;
            if (!mobKey.equals(data.getMobKey())) continue;
            Entity entity = data.getEntity();
            if (entity != null && !entity.isDead() && entity.isValid()) count++;
        }
        return count;
    }

    private int countQueuedMobs(String mobKey) {
        if (mobKey == null || mobKey.isEmpty()) return 0;
        int count = 0;
        for (GameSpawnQueue queue : queueList) {
            if (!mobKey.equals(queue.getMobKey())) continue;
            if (queue.hasRemaining()) count += queue.getRemaining();
        }
        return count;
    }

    private int getMissingMobSlots(String mobKey) {
        int max = getProgressMax(mobKey);
        int cleared = getProgressCurrent(mobKey);
        int live = countLiveTrackedMobs(mobKey);
        int queued = countQueuedMobs(mobKey);
        return Math.max(0, max - cleared - live - queued);
    }

    private void trimExcessTrackedMobs() {
        for (GameMob mob : getStage(stageCount)) {
            String mobKey = mob.getType();
            int allowedLive = Math.max(0, getProgressMax(mobKey)
                    - getProgressCurrent(mobKey)
                    - countQueuedMobs(mobKey));
            int seen = 0;
            List<Entity> overflow = new ArrayList<>();

            for (EntityData data : new ArrayList<>(EntityData.data().values())) {
                if (!this.equals(data.getGame())) continue;
                if (!Objects.equals(mobKey, data.getMobKey())) continue;
                Entity entity = data.getEntity();
                if (entity == null || entity.isDead() || !entity.isValid()) continue;
                seen++;
                if (seen > allowedLive) overflow.add(entity);
            }

            for (Entity entity : overflow) {
                EntityData removed = EntityData.data().remove(entity);
                if (removed != null) removed.clearDamageCredit();
                entity.remove();
                debugStage("removed overflow mob " + mobKey
                        + " because live tracked mobs exceeded configured amount"
                        + " allowedLive=" + allowedLive
                        + ", max=" + getProgressMax(mobKey)
                        + ", cleared=" + getProgressCurrent(mobKey)
                        + ", queued=" + countQueuedMobs(mobKey));
            }
        }
    }

    private boolean isCurrentStageMob(String mobKey) {
        if (mobKey == null || mobKey.isEmpty()) return false;
        for (GameMob mob : getStage(stageCount)) {
            if (mobKey.equals(mob.getType())) return true;
        }
        return false;
    }

    public void respawnLostMob(String mobKey) {
        if (!canRunStageLogic()) return;
        if (players == null || players.isEmpty()) return;
        if (!isCurrentStageMob(mobKey)) return;
        if (getMissingMobSlots(mobKey) <= 0) {
            debugStage("skip respawn for " + mobKey + " because amount is already satisfied"
                    + " cleared=" + getProgressCurrent(mobKey)
                    + ", live=" + countLiveTrackedMobs(mobKey)
                    + ", queued=" + countQueuedMobs(mobKey)
                    + ", max=" + getProgressMax(mobKey));
            return;
        }

        Location loc = getCurrentStageMobLocation(mobKey);
        if (loc == null) {
            DungeonsCore.inst().getLogger().warning("Room '" + name + "' could not respawn missing mob '"
                    + mobKey + "' because its current stage location was not found.");
            return;
        }

        FileConfiguration cfg = FileManager.getFileConfig(FileManager.Files.CONFIG);
        int radius = cfg.getInt("Settings.SpawnRadius");
        long mobSpawnDelay = (long) (cfg.getDouble("Settings.MobSpawnDelay", 1.5) * 1000);
        debugStage("tracked mob lost without valid player kill, respawning " + mobKey);
        if (mobSpawnDelay > 0 && hasPendingSpawnQueue()) {
            queueList.add(new GameSpawnQueue(mobKey, 1, loc));
        } else {
            spawnMobs(mobKey, 1, loc, radius, DungeonsCore.inst().getBukkitAPIHelper());
        }
    }

    private Location getCurrentStageMobLocation(String mobKey) {
        List<GameMob> currentStage = getStage(stageCount);
        if (currentStage.isEmpty()) return null;

        String mobPrefix = (currentStageIndex <= MAX_STAGES) ? "Mob" + currentStageIndex : "Boss";
        String firstKeyy = mobPrefix.equals("Boss") ? "" : "." + getStageKey(stageCount, 0);

        for (int index = 0; index < currentStage.size(); index++) {
            GameMob mob = currentStage.get(index);
            if (!Objects.equals(mob.getType(), mobKey)) continue;

            String keyy = mobPrefix.equals("Boss") ? "" : "." + getStageKey(stageCount, index);
            String path = (index == 0 || mobPrefix.equals("Boss")) ? mobPrefix + keyy : mobPrefix + firstKeyy + keyy;
            Location loc = (Location) room.get(path + ".Location");
            return loc != null ? loc.clone() : null;
        }

        return null;
    }

    public int getProgressLeft() {
        return getProgressMax() - getProgressCurrent();
    }

    public int getProgressCurrent() {
        var it = currentProgress.values().iterator();
        return it.hasNext() ? it.next() : 0;
    }

    public int getProgressCurrent(String k) {
        return currentProgress.getOrDefault(k, 0);
    }

    public int getProgressMax() {
        List<GameMob> requireMob = getStage(stageCount);
        int total = 0;
        for (GameMob mob : requireMob) total += mob.getAmount();
        return total;
    }

    public int getProgressMax(String k) {
        int max = 0;
        for (GameMob mob : getStage(stageCount)) {
            if (Objects.equals(mob.getType(), k)) max += mob.getAmount();
        }
        return max;
    }

    public int newStage() {
        if (!canRunStageLogic()) return 1;
        if (stageCount + 1 >= flatStages.size()) {
            complete();
            return 1;
        }

        String nextStageKey = getStageKey(stageCount + 1, 0);
        int nextStage = keyToStage(nextStageKey);
        while (!hasStage(currentStageIndex)) currentStageIndex++;

        if (nextStage > currentStageIndex) {
            stageCountdown = true;
            realStage++;
            return 2;
        }

        stageCountdown = false;
        stageCount++;
        currentProgress = new HashMap<>();
        queueList.clear();
        debugStage("starting stage");

        FileConfiguration cfg = FileManager.getFileConfig(FileManager.Files.CONFIG);
        int radius = cfg.getInt("Settings.SpawnRadius");
        BukkitAPIHelper mm = DungeonsCore.inst().getBukkitAPIHelper();
        boolean mobStartGlow = cfg.getBoolean("Settings.MobStartGlow", true);
        int startGlowFade = cfg.getInt("Settings.StartGlowFade", 15);
        long mobSpawnDelay = (long) (cfg.getDouble("Settings.MobSpawnDelay", 1.5) * 1000);

        Map<String, Integer> mobMap = new LinkedHashMap<>();
        List<GameMob> currentStage = getStage(stageCount);
        String firstKeyy = "";
        int index = 0;

        for (GameMob gameMob : currentStage) {
            String key = gameMob.getType();
            int amount = gameMob.getAmount();
            debugStage("spawning " + amount + "x " + key);

            String mobPrefix = (currentStageIndex <= MAX_STAGES) ? "Mob" + currentStageIndex : "Boss";
            String keyy = mobPrefix.equals("Boss") ? "" : "." + getStageKey(stageCount, index);
            if (index == 0) firstKeyy = keyy;
            String path = (index == 0) ? mobPrefix + keyy : mobPrefix + firstKeyy + keyy;

            Location loc = (Location) room.get(path + ".Location");
            if (loc == null) { index++; continue; }
            loc.add(0, 1, 0);

            if (cfg.getBoolean("Settings.TeleportNewStage")) {
                for (Player player : players) player.teleport(loc);
            }
            loc.subtract(0, 1, 0);

            String displayName = mm.getMythicMobDisplayNameGet(key);
            mobMap.put(Utils.randomColor() + displayName, amount);
            for (Player p : players) {
                Utils.playSound(p, cfg.getString("Sound.TurnStart", ""));
            }

            try {
                if (mobSpawnDelay <= 0) {
                    Entity firstEntity = spawnMobs(key, amount, loc, radius, mm);
                    applyGlow(firstEntity, mobStartGlow, startGlowFade);
                } else {
                    lastSpawn = System.currentTimeMillis() - 50L;
                    boolean shouldSpawnFirst = queueList.isEmpty();
                    queueList.add(new GameSpawnQueue(key, amount - (shouldSpawnFirst ? 1 : 0), loc));
                    if (shouldSpawnFirst) {
                        Entity firstEntity = spawnMobs(key, 1, loc, radius, mm);
                        applyGlow(firstEntity, mobStartGlow, startGlowFade);
                    }
                }
            } catch (Exception e) {
                DungeonsCore.inst().getLogger().log(Level.WARNING, "Error spawning mobs in " + name, e);
            }
            index++;
        }

        sendInfo(mobMap);
        realTurn++;
        return 0;
    }

    private void applyGlow(Entity entity, boolean mobStartGlow, int startGlowFade) {
        if (!mobStartGlow || entity == null) return;
        entity.setGlowing(true);
        if (startGlowFade > 0) {
            new BukkitRunnable() {
                @Override public void run() {
                    if (!entity.isDead()) entity.setGlowing(false);
                }
            }.runTaskLater(DungeonsCore.inst(), (long) startGlowFade * 20L);
        }
    }

    private Entity spawnMobs(String key, int amount, Location loc, int radius, BukkitAPIHelper mm) {
        Entity firstSpawn = null;
        amount = Math.min(Math.max(amount, 1), getMissingMobSlots(key));
        if (amount <= 0) {
            debugStage("spawn skipped for " + key + " because amount is already satisfied"
                    + " cleared=" + getProgressCurrent(key)
                    + ", live=" + countLiveTrackedMobs(key)
                    + ", queued=" + countQueuedMobs(key)
                    + ", max=" + getProgressMax(key));
            return null;
        }
        for (int i = 0; i < amount; i++) {
            if (!canRunStageLogic()) break;
            double origin = -radius;
            double bound = radius + 0.1;
            Location spawnLoc = loc.clone().add(
                    ThreadLocalRandom.current().nextDouble(origin, bound), 1,
                    ThreadLocalRandom.current().nextDouble(origin, bound));
            int attempts = 0;
            while (Utils.isSuckBlock(spawnLoc) && attempts++ < 10) {
                spawnLoc = loc.clone().add(
                        ThreadLocalRandom.current().nextDouble(origin, bound), 1,
                        ThreadLocalRandom.current().nextDouble(origin, bound));
            }
            if (!spawnLoc.getChunk().isLoaded()) spawnLoc.getChunk().load();
            Entity entity = mm.spawnMythicMob(key, spawnLoc);
            if (i == 0) firstSpawn = entity;
            if (entity != null) {
                prepareDungeonMob(entity);
                EntityData.data().put(entity, new EntityData(entity, this, key));
                DebugLogger.log("Mob spawned. room=" + name
                        + ", mob=" + key
                        + ", entity=" + entity.getUniqueId()
                        + ", world=" + spawnLoc.getWorld().getName()
                        + ", x=" + Math.round(spawnLoc.getX())
                        + ", y=" + Math.round(spawnLoc.getY())
                        + ", z=" + Math.round(spawnLoc.getZ()));
            } else {
                addProgress(key, 1);
                debugStage("spawn failed for " + key + ", counted as cleared");
                String warningKey = name + ":" + key;
                if (LOGGED_SPAWN_FAILURES.add(warningKey)) {
                    DungeonsCore.inst().getLogger().warning("MythicMob '" + key + "' failed to spawn in room '"
                            + name + "'. Counting failed spawn as cleared so the dungeon cannot get stuck.");
                }
            }
            Utils.spawnParticle(spawnLoc,
                    FileManager.getFileConfig(FileManager.Files.CONFIG).getString("Particle.TurnStart"),
                    players.isEmpty() ? null : players.get(0));
        }
        return firstSpawn;
    }

    private void prepareDungeonMob(Entity entity) {
        entity.setPersistent(true);
        if (entity instanceof LivingEntity living) {
            living.setRemoveWhenFarAway(false);
        }
    }

    public void nextStage() { currentStageIndex++; }

    public void start() {
        String type = room.getString("Type");
        for (Player player : players) takeTurn(player, type, 1);
        status = GameStatus.PLAYING;
        DebugLogger.log("Room started. room=" + name
                + ", type=" + type
                + ", players=" + players.size()
                + ", totalStages=" + flatStages.size()
                + ", hasBoss=" + !boss.isEmpty());
        completed = false;
        currentStageIndex = 1;
        stageCount = -1;
        realStage = 1;
        realTurn = 0;
        stageCountdown = false;
        quitCountdown = false;
        lastSpawn = 0L;
        totalKill.clear();
        totalDamage.clear();
        queueList.clear();
        currentProgress = new HashMap<>();
        unlockBossRoom();
        beginEntryWarmup();
    }

    private void beginEntryWarmup() {
        cancelEntryWarmupTask();
        FileConfiguration cfg = FileManager.getFileConfig(FileManager.Files.CONFIG);
        int protectSeconds = Math.max(0, cfg.getInt("Settings.EntryProtect", 5));
        int spawnDelaySeconds = Math.max(0, cfg.getInt("Settings.EntrySpawnDelay", 3));
        long now = System.currentTimeMillis();
        entryWarmup = true;
        entryWarmupUntil = now + spawnDelaySeconds * 1000L;

        for (Player p : new ArrayList<>(players)) {
            if (!PlayerData.contains(p)) {
                debugWaiting("suspicious start state: player missing PlayerData player=" + p.getName());
                continue;
            }
            if (protectSeconds > 0) {
                GameStatistic.entryProtectUntil.put(p.getUniqueId(), now + protectSeconds * 1000L);
            }
            p.setGameMode(GameMode.SURVIVAL);
            p.setFoodLevel(20);
            if (spawn != null) {
                if (!spawn.getChunk().isLoaded()) spawn.getChunk().load();
                p.teleport(spawn);
            }
            debugWaiting("entry warmup player=" + p.getName()
                    + " loc=" + formatLoc(p.getLocation())
                    + " protect=" + protectSeconds + "s"
                    + " spawnDelay=" + spawnDelaySeconds + "s");
        }

        if (spawnDelaySeconds <= 0) {
            finishEntryWarmup();
        } else {
            entryWarmupTask = Bukkit.getScheduler().runTaskLater(DungeonsCore.inst(),
                    this::finishEntryWarmup, spawnDelaySeconds * 20L);
        }
    }

    private void finishEntryWarmup() {
        entryWarmupTask = null;
        if (status != GameStatus.PLAYING || completed || quitCountdown) {
            debugWaiting("entry warmup skipped finish due to room state");
            entryWarmup = false;
            entryWarmupUntil = 0L;
            return;
        }
        if (players.isEmpty()) {
            debugWaiting("entry warmup expired with no players");
            entryWarmup = false;
            entryWarmupUntil = 0L;
            fullReset();
            return;
        }
        entryWarmup = false;
        entryWarmupUntil = 0L;
        if (stageCount >= 0) {
            debugWaiting("entry warmup expired but first stage was already spawned stageCount=" + stageCount);
            return;
        }
        debugWaiting("entry warmup finished, spawning first stage");
        newStage();
    }

    private void cancelEntryWarmupTask() {
        if (entryWarmupTask != null && !entryWarmupTask.isCancelled()) {
            entryWarmupTask.cancel();
        }
        entryWarmupTask = null;
    }

    /**
     * Kick toàn bộ người chơi về spawn khi hết time hoặc lỗi, sau đó reset room.
     */
    public void forceStopAndReset() {
        List<Player> snapshot = new ArrayList<>(players);
        Location globalSpawn = getGlobalSpawn();
        for (Player p : snapshot) {
            PlayerData pd = PlayerData.get(p);
            Location tp = globalSpawn;
            if (tp == null && pd != null) {
                tp = pd.getLocation();
            }
            if (tp == null) {
                tp = fallbackExitLocation(p);
            }
            PlayerData.remove(p);
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
            GameStatistic.protect.remove(p.getUniqueId());
            GameStatistic.entryProtectUntil.remove(p.getUniqueId());
            GameStatistic.lastDeath.remove(p.getUniqueId());
            if (tp != null) {
                p.teleport(tp);
            }
        }
        fullReset();
    }

    /**
     * Kick toàn bộ kèm thông báo tùy chỉnh (dùng cho waiting kick).
     */
    public void forceKickAll(String message) {
        List<Player> snapshot = new ArrayList<>(players);
        String msg = ColorUtils.colorize(message);
        Location globalSpawn = getGlobalSpawn();
        for (Player p : snapshot) {
            p.sendMessage(msg);
            PlayerData pd = PlayerData.get(p);
            PlayerData.remove(p);
            p.setGameMode(GameMode.SURVIVAL);
            GameStatistic.protect.remove(p.getUniqueId());
            GameStatistic.entryProtectUntil.remove(p.getUniqueId());
            GameStatistic.lastDeath.remove(p.getUniqueId());
            if (globalSpawn != null) {
                p.teleport(globalSpawn);
            } else if (pd != null) {
                p.teleport(pd.getLocation());
            }
        }
        fullReset();
    }

    /**
     * Reset hoàn toàn room về trạng thái ban đầu, sẵn sàng cho lượt tiếp theo.
     */
    public void fullReset() {
        cancelEntryWarmupTask();
        clearMobs();
        for (Player player : new ArrayList<>(players)) {
            PlayerData.remove(player);
            GameStatistic.protect.remove(player.getUniqueId());
            GameStatistic.entryProtectUntil.remove(player.getUniqueId());
            GameStatistic.lastDeath.remove(player.getUniqueId());
        }
        players = new ArrayList<>();
        currentStageIndex = 1;
        stageCount = -1;
        realStage = 1;
        realTurn = 0;
        stageCountdown = false;
        quitCountdown = false;
        completed = false;
        entryWarmup = false;
        entryWarmupUntil = 0L;
        lastSpawn = 0L;
        totalKill.clear();
        totalDamage.clear();
        queueList.clear();
        currentProgress = new HashMap<>();
        status = GameStatus.WAITING;
        time = maxTime;
    }

    public void forceStop() { leaveAllAfterComplete(); }

    public void join(Player p) {
        if (PlayerData.contains(p)) {
            PlayerData pd = PlayerData.get(p);
            Game oldGame = pd != null ? pd.getGame() : null;
            if (oldGame != null && oldGame != this) {
                GameStatus st = oldGame.getStatus();
                if (st == GameStatus.PLAYING || st == GameStatus.STARTING) {
                    p.sendMessage(Messages.get("CannotJoinLobbyWhilePlaying"));
                    return;
                }
                oldGame.getPlayers().remove(p);
                PlayerData.remove(p);
            }
        }
        for (Game g : games.values()) {
            if (g != this) {
                g.getPlayers().remove(p);
            }
        }

        if (players.contains(p)) return;
        Location prevLoc = p.getLocation().clone();
        Location waitingRoomLoc = Game.getGlobalWaitingRoom();
        debugWaiting("join requested player=" + p.getName()
                + " from=" + formatLoc(prevLoc)
                + " waitingRoom=" + formatLoc(waitingRoomLoc));
        if (waitingRoomLoc != null) {
            if (!waitingRoomLoc.getChunk().isLoaded()) waitingRoomLoc.getChunk().load();
            p.teleport(waitingRoomLoc);
            String joinMsg = FileManager.getFileConfig(FileManager.Files.CONFIG)
                    .getString("WaitingRoom.JoinMessage", "&a&lBạn đã vào phòng chờ.");
            p.sendMessage(ColorUtils.colorize(joinMsg));
        } else {
            p.teleport(spawn);
        }
        p.setGameMode(GameMode.SURVIVAL);
        PlayerData.put(p, new PlayerData(p, this, prevLoc));
        players.add(p);
        if (!isNear(p.getLocation(), getWaitingRoom(), 12.0)) {
            debugWaiting("suspicious join state: player not near waiting room player="
                    + p.getName() + " loc=" + formatLoc(p.getLocation())
                    + " waitingRoom=" + formatLoc(getWaitingRoom()));
        }
        DebugLogger.log("Player joined room. room=" + name
                + ", player=" + p.getName()
                + ", players=" + players.size() + "/" + maxPlayers);
        p.sendMessage(Messages.get("LeaveOnJoin"));
        String msg = Messages.get("PlayerJoin")
                .replace("<player>", p.getName())
                .replace("<joined>", String.valueOf(players.size()))
                .replace("<max>", String.valueOf(maxPlayers));
        players.forEach(player -> player.sendMessage(msg));
        totalKill.put(p.getName(), 0);
        totalDamage.put(p.getName(), 0.0);
    }

    public void leave(Player p, boolean message, boolean pointLose, boolean quit) {
        if (!PlayerData.contains(p)) return;
        debugWaiting("leave requested player=" + p.getName()
                + " loc=" + formatLoc(p.getLocation())
                + " pointLose=" + pointLose
                + " quit=" + quit);
        DebugLogger.log("Player leaving room. room=" + name
                + ", player=" + p.getName()
                + ", status=" + status
                + ", pointLose=" + pointLose
                + ", quit=" + quit);
        players.remove(p);
        Location location = getGlobalSpawn();
        if (location == null) {
            PlayerData pd = PlayerData.get(p);
            if (pd != null) location = pd.getLocation();
        }
        PlayerData.remove(p);
        GameStatistic.protect.remove(p.getUniqueId());
        GameStatistic.entryProtectUntil.remove(p.getUniqueId());
        GameStatistic.lastDeath.remove(p.getUniqueId());

        if (location != null) {
            if (quit) {
                p.teleport(location);
            } else {
                Location finalLoc = location;
                Bukkit.getScheduler().scheduleSyncDelayedTask(DungeonsCore.inst(), () -> p.teleport(finalLoc), 1L);
            }
        }

        p.setGameMode(GameMode.SURVIVAL);
        if (status == GameStatus.PLAYING && pointLose) {
            FileConfiguration dataF = FileManager.getFileConfig(FileManager.Files.DATA);
            FileConfiguration cfg = FileManager.getFileConfig(FileManager.Files.CONFIG);
            String uuid = p.getUniqueId().toString();
            int curPoint = dataF.getInt(uuid + ".Point", 0);
            int point = Utils.parseInt(cfg.getString("Point.Lose", "0"));
            if (point > 0) {
                if (curPoint - point < 0 && !cfg.getBoolean("Point.AllowNegative")) point = Math.max(0, curPoint);
                dataF.set(uuid + ".Point", curPoint - point);
                dataF.set(uuid + ".Name", p.getName());
                FileManager.saveFileConfig(dataF, FileManager.Files.DATA);
                p.sendMessage(Messages.get("PointLose").replace("<point>", String.valueOf(point)));
            }
        }

        if (message) {
            String msg = Messages.get("PlayerQuit")
                    .replace("<player>", p.getName())
                    .replace("<joined>", String.valueOf(players.size()))
                    .replace("<max>", String.valueOf(maxPlayers));
            players.forEach(player -> player.sendMessage(msg));
        }

        if (players.isEmpty() && status == GameStatus.PLAYING) {
            DebugLogger.log("Room reset because all players left while playing. room=" + name);
            fullReset();
        }
    }

    private void clearMobs() {
        List<EntityData> snapshot = new ArrayList<>(EntityData.data().values());
        for (EntityData e : snapshot) {
            if (e == null || !this.equals(e.getGame())) continue;
            Entity entity = e.getEntity();
            EntityData removed = EntityData.data().remove(entity);
            if (removed != null) removed.clearDamageCredit();
            if (entity != null && !entity.isDead() && entity.isValid()) {
                entity.remove();
            }
        }
    }

    public void restore() {
        fullReset();
    }

    public void complete() {
        if (completed || status != GameStatus.PLAYING) return;
        List<Player> snapshot = new ArrayList<>(players);
        if (snapshot.isEmpty()) { fullReset(); return; }
        DebugLogger.log("Room completed. room=" + name
                + ", players=" + snapshot.size()
                + ", totalKill=" + totalKill
                + ", totalDamage=" + totalDamage);
        completed = true;
        quitCountdown = true;
        stageCountdown = false;
        queueList.clear();
        clearMobs();

        Bukkit.broadcastMessage(Messages.get("BroadcastComplete")
                .replace("<player>", snapshot.get(0).getName())
                .replace("<prefix>", ColorUtils.colorize(room.getString("Prefix", ""))));

        FileConfiguration data = FileManager.getFileConfig(FileManager.Files.DATA);
        for (Player p : snapshot) {
            int curPoint = data.getInt(p.getUniqueId() + ".Point", 0);
            int point = Utils.parseInt(FileManager.getFileConfig(FileManager.Files.CONFIG).getString("Point.Win", "0"));
            data.set(p.getUniqueId() + ".Point", curPoint + point);
            data.set(p.getUniqueId() + ".Name", p.getName());
            sendStatistic(p);
            p.sendMessage(Messages.get("PointWin").replace("<point>", String.valueOf(point)));
            p.sendMessage(Messages.get("Complete"));
            reward(p);
        }
        FileManager.saveFileConfig(data, FileManager.Files.DATA);
        task.setCountdown(FileManager.getFileConfig(FileManager.Files.CONFIG).getInt("Settings.QuitCountdown"));
    }

    public void leaveAllAfterComplete() {
        List<Player> snapshot = new ArrayList<>(players);
        for (Player p : snapshot) leave(p, false, false, false);
        fullReset();
    }

    public void reward(Player p) {
        var rewardSection = room.getConfigurationSection("Reward");
        if (rewardSection == null) {
            DebugLogger.log("Reward skipped because room has no Reward section. room=" + name
                    + ", player=" + p.getName());
            return;
        }
        DebugLogger.log("Reward started. room=" + name + ", player=" + p.getName());
        Random random = new Random();
        for (String key : rewardSection.getKeys(false)) {
            int chance = room.getInt("Reward." + key + ".Chance");
            if (room.contains("Reward." + key + ".Command")) {
                String command = room.getString("Reward." + key + ".Command", "");
                if (chance >= 100) {
                    DebugLogger.log("Reward guaranteed command. room=" + name
                            + ", player=" + p.getName()
                            + ", key=" + key
                            + ", command=" + command);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("<player>", p.getName()));
                } else {
                    random.addChance(command, chance);
                    DebugLogger.log("Reward random command queued. room=" + name
                            + ", player=" + p.getName()
                            + ", key=" + key
                            + ", chance=" + chance);
                }
            } else if (room.contains("Reward." + key + ".Item")) {
                ItemStack item = MMOItemsRewardHook.resolve(
                        room.getItemStack("Reward." + key + ".Item"), name, key);
                if (chance >= 100) {
                    DebugLogger.log("Reward guaranteed item. room=" + name
                            + ", player=" + p.getName()
                            + ", key=" + key
                            + ", item=" + (item != null ? item.getType() : "null"));
                    p.getInventory().addItem(item);
                } else {
                    random.addChance(item, chance);
                    DebugLogger.log("Reward random item queued. room=" + name
                            + ", player=" + p.getName()
                            + ", key=" + key
                            + ", item=" + (item != null ? item.getType() : "null")
                            + ", chance=" + chance);
                }
            }
        }

        int rewardAmount = Math.min(room.getInt("RewardAmount"), random.getChoices());
        for (int i = 0; i < rewardAmount; i++) {
            Object reward = random.getRandomElement();
            if (reward == null) break;
            if (reward instanceof String command) {
                random.removeChance(command);
                DebugLogger.log("Reward random command selected. room=" + name
                        + ", player=" + p.getName()
                        + ", command=" + command);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("<player>", p.getName()));
            } else if (reward instanceof ItemStack item) {
                random.removeChance(item);
                DebugLogger.log("Reward random item selected. room=" + name
                        + ", player=" + p.getName()
                        + ", item=" + item.getType());
                p.getInventory().addItem(item);
            }
        }

        for (String cmd : FileManager.getFileConfig(FileManager.Files.CONFIG).getStringList("Settings.RewardCommand")) {
            DebugLogger.log("Reward global command. room=" + name
                    + ", player=" + p.getName()
                    + ", command=" + cmd);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("<player>", p.getName()));
        }
        DebugLogger.log("Reward finished. room=" + name + ", player=" + p.getName());
    }

    public void starting() {
        status = GameStatus.STARTING;
        debugWaiting("room starting requested players=" + players.stream().map(Player::getName).toList());
        for (Player player : new ArrayList<>(players)) {
            PlayerData data = PlayerData.get(player);
            if (data == null || data.getGame() != this) {
                debugWaiting("suspicious starting state: stale/missing PlayerData player=" + player.getName());
            }
            if (!isNear(player.getLocation(), getWaitingRoom(), 12.0)) {
                debugWaiting("suspicious starting state: player not near waiting room player="
                        + player.getName()
                        + " loc=" + formatLoc(player.getLocation())
                        + " waitingRoom=" + formatLoc(getWaitingRoom()));
            }
        }
    }
    public boolean isFull() { return players.size() >= maxPlayers; }
    public void setMaxPlayer(int max) { maxPlayers = max; }
    public void setMaxTime(int time) { maxTime = time; }
    public void setSpawn(Location spawn) { this.spawn = spawn; }
    public boolean isLeader(Player p) { return !players.isEmpty() && players.get(0).getName().equals(p.getName()); }

    public Location mobLocation(int index) {
        String mobPrefix = (currentStageIndex <= MAX_STAGES) ? "Mob" + currentStageIndex : "Boss";
        String keyy = mobPrefix.equals("Boss") ? "" : "." + getStageKey(stageCount, index);
        return (Location) room.get(mobPrefix + keyy + ".Location");
    }

    public void sendInfo(Map<String, Integer> mobMap) {
        int turn = keyToTurn(getStageKey(stageCount, 0));
        int max = totalTurn - (boss.isEmpty() ? 0 : 1);
        int current = realTurn;
        String progress = Utils.getProgress(current + (boss.isEmpty() ? 1 : 0), max);
        int percent = max == 0 ? 0 : (int) ((double) (current + (boss.isEmpty() ? 1 : 0)) / max * 100);

        for (String message : FileManager.getFileConfig(FileManager.Files.FORMAT).getStringList("format")) {
            message = message.replace("<stage>", current == max && !boss.isEmpty() ? "Boss" : String.valueOf(realStage))
                    .replace("<turn>", String.valueOf(turn))
                    .replace("<progress>", progress)
                    .replace("<percent>", String.valueOf(percent));
            if (message.toLowerCase().contains("<display>")) {
                for (var entry : mobMap.entrySet()) {
                    String finalMsg = ColorUtils.colorize(message.replace("<display>", entry.getKey())
                            .replace("<amount>", String.valueOf(entry.getValue())));
                    players.forEach(p -> p.sendMessage(finalMsg));
                }
            } else {
                String finalMsg = ColorUtils.colorize(message);
                players.forEach(p -> p.sendMessage(finalMsg));
            }
        }
    }

    public void nextStageParticle(boolean playing) {
        if (!playing) {
            currentStageIndex = 1;
            stageCount = -1;
        }
        List<GameMob> nextStage = getStage(stageCount + 1);
        if (nextStage.isEmpty()) return;
        Set<String> keys = getStageKey(stageCount + 1);
        String stageKey = keys.iterator().next();

        String mobPrefix = "Boss";
        for (int i = 1; i <= MAX_STAGES; i++) {
            if (room.contains("Mob" + i + "." + stageKey)) {
                mobPrefix = "Mob" + i;
                break;
            }
        }

        String keyy = mobPrefix.equals("Boss") ? "" : "." + stageKey;
        Location loc = (Location) room.get(mobPrefix + keyy + ".Location");
        if (loc == null) return;
        Player firstPlayer = players.isEmpty() ? null : players.get(0);
        Utils.spawnParticle(loc, FileManager.getFileConfig(FileManager.Files.CONFIG).getString("Particle.NextTurn"), firstPlayer);

        if (room.getConfigurationSection(mobPrefix + keyy) != null) {
            for (String childKey : room.getConfigurationSection(mobPrefix + keyy).getKeys(false)) {
                if (Set.of("Location", "Type", "Amount", "Time").contains(childKey)) continue;
                Location childLoc = (Location) room.get(mobPrefix + keyy + "." + childKey + ".Location");
                Utils.spawnParticle(childLoc, FileManager.getFileConfig(FileManager.Files.CONFIG).getString("Particle.NextTurn"), firstPlayer);
            }
        }
    }

    public void checkSpawnMobs() {
        if (!canRunStageLogic()) return;
        long mobSpawnDelay = (long) FileManager.getFileConfig(FileManager.Files.CONFIG).getInt("Settings.MobSpawnDelay", 2) * 1000L;
        long current = System.currentTimeMillis();
        Iterator<GameSpawnQueue> it = queueList.iterator();
        while (it.hasNext()) {
            GameSpawnQueue sq = it.next();
            if (!sq.hasRemaining() || sq.getMobKey() == null || sq.getMobKey().isEmpty() || sq.getLocation() == null) {
                it.remove();
                continue;
            }
            if (current - lastSpawn < mobSpawnDelay) continue;
            lastSpawn = System.currentTimeMillis() - 50L;
            sq.decrementRemaining();
            int radius = FileManager.getFileConfig(FileManager.Files.CONFIG).getInt("Settings.SpawnRadius");
            debugStage("spawning queued mob " + sq.getMobKey() + ", left after this=" + sq.getRemaining());
            spawnMobs(sq.getMobKey(), 1, sq.getLocation(), radius, DungeonsCore.inst().getBukkitAPIHelper());
            break;
        }
    }

    private void debugStage(String message) {
        boolean stageDebug = FileManager.getFileConfig(FileManager.Files.CONFIG)
                .getBoolean("Settings.DebugStageProgress", false);
        DebugLogger.logStage(stageDebug, "[StageProgress] room=" + name
                + " stage=" + getStageDebugName()
                + " progress=" + currentProgress
                + " queue=" + queueList.size()
                + " :: " + message);
    }

    private boolean canRunStageLogic() {
        return status == GameStatus.PLAYING && !completed && !quitCountdown && !entryWarmup;
    }

    private String getStageDebugName() {
        if (stageCount < 0) return "pre-start";
        if (stageCount >= flatStages.size()) return "complete";
        String key = getStageKey(stageCount, 0);
        int stage = keyToStage(key);
        return stage > MAX_STAGES ? "Boss" : String.valueOf(stage);
    }

    public void addKill(String name, int kill) {
        totalKill.merge(name, kill, Integer::sum);
    }

    public void addDamage(String name, double damage) {
        totalDamage.merge(name, damage, Double::sum);
    }

    public void sendStatistic(Player p) {
        List<Map.Entry<String, Integer>> killList = new ArrayList<>(totalKill.entrySet());
        List<Map.Entry<String, Double>> damageList = new ArrayList<>(totalDamage.entrySet());
        killList.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        damageList.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        Set<String> sent = new LinkedHashSet<>();
        for (String message : FileManager.getFileConfig(FileManager.Files.FORMAT).getStringList("format-complete")) {
            if (message.toLowerCase().contains("<statistic>")) {
                int stt = 1;
                for (var entry : killList) {
                    if (sent.contains(entry.getKey())) continue;
                    int damage = (int) totalDamage.getOrDefault(entry.getKey(), 0.0).doubleValue();
                    p.sendMessage(formatStatistic(stt, entry.getKey(), entry.getValue(), damage));
                    sent.add(entry.getKey());
                    stt++;
                }
                for (var entry : damageList) {
                    if (sent.contains(entry.getKey())) continue;
                    int kill = totalKill.getOrDefault(entry.getKey(), 0);
                    p.sendMessage(formatStatistic(stt, entry.getKey(), kill, (int) entry.getValue().doubleValue()));
                    sent.add(entry.getKey());
                    stt++;
                }
            } else {
                p.sendMessage(ColorUtils.colorize(message));
            }
        }
    }

    private String formatStatistic(int stt, String player, int killed, int damage) {
        return ColorUtils.colorize(FileManager.getFileConfig(FileManager.Files.FORMAT).getString("statistic", "")
                .replace("<stt>", String.valueOf(stt))
                .replace("<player>", player)
                .replace("<killed>", String.valueOf(killed))
                .replace("<damage>", String.valueOf(damage)));
    }

    public void glowAllMob() {
        for (var entry : EntityData.data().entrySet()) {
            if (!entry.getValue().getGame().equals(this)) continue;
            entry.getKey().setGlowing(true);
        }
    }

    public Location getWaitingRoom() {
        if (waitingRoom == null) waitingRoom = getGlobalWaitingRoom();
        return waitingRoom;
    }

    public void setWaitingRoom(Location location) { waitingRoom = location; }
    public Location getBossWaitingRoom() { return bossWaitingRoom; }
    public boolean hasBossWaitingRoom() { return bossWaitingRoom != null; }
    public boolean isBossRoomLocked() { return bossRoomLocked; }
    public void lockBossRoom() { bossRoomLocked = true; }
    public void unlockBossRoom() { bossRoomLocked = false; }

    public void teleportToBossWaitingRoom(Player player) {
        if (!hasBossWaitingRoom()) {
            player.sendMessage(Messages.get("NoBossWaitingRoom"));
            return;
        }
        FileConfiguration cfg = FileManager.getFileConfig(FileManager.Files.CONFIG);
        if (isBossRoomLocked()) {
            player.sendMessage(ColorUtils.colorize(cfg.getString("WaitingRoom.BossRoomLockedMessage",
                    "&c&lKhu vực boss đã bị khóa.")));
            return;
        }
        PlayerData pd = PlayerData.get(player);
        if (pd != null && pd.isBossFightCooldown()) {
            player.sendMessage(ColorUtils.colorize(cfg.getString("WaitingRoom.BossRoomCooldownMessage",
                    "&c&lBạn cần đợi thêm &f<time> &c&lphút."))
                    .replace("<time>", String.valueOf(pd.getBossFightCooldownRemaining())));
            return;
        }
        player.teleport(bossWaitingRoom);
        player.sendMessage(ColorUtils.colorize(cfg.getString("WaitingRoom.BossRoomUnlockedMessage",
                "&a&lKhu vực boss đã mở.")));
    }

    public void startBossFight() {
        lockBossRoom();
        for (Player player : players) {
            if (player.getLocation().distance(bossWaitingRoom) > 20) continue;
            PlayerData pd = PlayerData.get(player);
            if (pd != null) pd.setLastBossFightTime(System.currentTimeMillis());
        }
        String msg = ColorUtils.colorize("&c&lTrận chiến với boss đã bắt đầu!");
        players.forEach(p -> p.sendMessage(msg));
    }

    public void endBossFight() {
        unlockBossRoom();
        String msg = ColorUtils.colorize("&a&lBoss đã bị đánh bại! Người chơi mới có thể tham gia.");
        players.forEach(p -> p.sendMessage(msg));
    }
}
