package camchua.phoban.phobanpro.utils;

import camchua.phoban.phobanpro.DungeonsCore;
import camchua.phoban.phobanpro.compat.api.CompatProvider;
import camchua.phoban.phobanpro.game.GameMob;
import camchua.phoban.phobanpro.manager.FileManager;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Utils {

    /** Minor MC version (19 = 1.19.x). Dùng cho CustomModelData, v.v. */
    private static int versionMinor = 19;

    public static void checkVersion() {
        try {
            String version = Bukkit.getBukkitVersion().split("-")[0];
            String[] parts = version.split("\\.");
            if (parts.length >= 2) {
                versionMinor = Integer.parseInt(parts[1]);
            } else {
                versionMinor = 19;
            }
            DungeonsCore.inst().getLogger().info("Detected server version: " + version + " (minor=" + versionMinor + ")");
        } catch (Exception ex) {
            versionMinor = 19;
            DungeonsCore.inst().getLogger().warning("Could not parse Bukkit version, assuming 1.19+: " + ex.getMessage());
        }
    }

    /**
     * Enchant Unbreaking — tương thích 1.19–1.21+ (API cũ {@code DURABILITY}, API mới {@code UNBREAKING}).
     */
    @SuppressWarnings("deprecation")
    public static Enchantment getUnbreakingEnchantment() {
        return CompatProvider.get().enchantments().unbreaking();
    }

    public static int getVersionMinor() {
        return versionMinor;
    }

    public static Material matchMaterial(String mat) {
        return CompatProvider.get().materials().match(mat);
    }

    public static int firstEmpty(int rows) {
        return switch (rows) {
            case 3 -> 16;
            case 4 -> 25;
            case 5 -> 34;
            default -> 43;
        };
    }

    public static boolean checkStage(List<GameMob> requireMob, HashMap<String, Integer> current) {
        HashMap<String, Integer> require = new HashMap<>();
        for (GameMob mob : requireMob) {
            require.merge(mob.getType(), mob.getAmount(), Integer::sum);
        }
        for (var entry : require.entrySet()) {
            if (current.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        }
        return true;
    }

    public static boolean isSuckBlock(Location loc) {
        return !loc.getBlock().getType().isAir() && !loc.clone().add(0, 1, 0).getBlock().getType().isAir();
    }

    public static void scanSection(FileConfiguration configScan, FileConfiguration newConfig, String key, String arenaName) {
        if (!configScan.contains(key)) return;
        var section = configScan.getConfigurationSection(key);
        if (section == null) return;
        for (String k : section.getKeys(false)) {
            String fullKey = key + "." + k;
            if (configScan.isConfigurationSection(fullKey)) {
                scanSection(configScan, newConfig, fullKey, arenaName);
            } else {
                newConfig.set(fullKey.replaceFirst(arenaName + ".", ""), configScan.get(fullKey));
            }
        }
    }

    public static boolean isJail(Player p) {
        return false;
    }

    public static int getRespawnTurn(Player p) {
        for (int i = 100; i > 0; i--) {
            if (p.hasPermission("phoban.respawn." + i)) return i;
        }
        return FileManager.getFileConfig(FileManager.Files.CONFIG).getInt("Settings.Respawn.Amount");
    }

    public static void sendError(String title, String message) {
        Bukkit.getConsoleSender().sendMessage(ColorUtils.colorize("&a&l[DungeonsCore] &4" + title + " -> &c" + message));
    }

    public static void sendTitle(Player p, String title, String subtitle) {
        p.sendTitle(ColorUtils.colorize(title), ColorUtils.colorize(subtitle), 10, 40, 10);
    }

    public static String getProgress(int current, int max) {
        FileConfiguration format = FileManager.getFileConfig(FileManager.Files.FORMAT);
        String character = format.getString("progress.char", "▬");
        int maxChar = format.getInt("progress.max-char", 10);
        String active = ColorUtils.colorize(format.getString("progress.active", "&b&l"));
        String inactive = ColorUtils.colorize(format.getString("progress.inactive", "&f&l"));
        int activeCount = (max == 0 || current == 0) ? 0 : (int) ((double) current / max * maxChar);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= maxChar; i++) {
            sb.append(i <= activeCount ? active : inactive).append(character);
        }
        return sb.toString();
    }

    public static void playSound(Player p, String strSound) {
        if (strSound == null || strSound.isEmpty()) return;
        for (String sound : strSound.split("\\|")) {
            try {
                p.playSound(p.getLocation(), Sound.valueOf(sound.trim().toUpperCase()), 1.0f, 1.0f);
                break;
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public static void spawnParticle(Location loc, String strParticle, Player run) {
        if (loc == null || strParticle == null || strParticle.isEmpty()) return;
        loc = loc.clone().add(0, 1.5, 0);
        for (String particle : strParticle.split("\\|")) {
            try {
                String[] parts = particle.trim().split(" ");
                Particle p = Particle.valueOf(parts[0].toUpperCase());
                double ox = parts.length > 1 ? Double.parseDouble(parts[1]) : 0;
                double oy = parts.length > 2 ? Double.parseDouble(parts[2]) : 0;
                double oz = parts.length > 3 ? Double.parseDouble(parts[3]) : 0;
                double speed = parts.length > 4 ? Double.parseDouble(parts[4]) : 0;
                int count = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;
                boolean force = parts.length > 6 && parts[6].equalsIgnoreCase("force");
                loc.getWorld().spawnParticle(p, loc, count, ox, oy, oz, speed, null, force);
                break;
            } catch (Exception ex) {
                try {
                    Effect e = Effect.valueOf(particle.trim().toUpperCase());
                    loc.getWorld().playEffect(loc, e, 1);
                    break;
                } catch (Exception ignored) {}
            }
        }
    }

    public static int parseInt(String strInteger) {
        if (strInteger == null || strInteger.isEmpty()) return 0;
        if (strInteger.contains(":")) {
            String[] parts = strInteger.split(":");
            int min = Integer.parseInt(parts[0]);
            int max = Integer.parseInt(parts[1]);
            if (min == max) return min;
            if (min > max) { int t = max; max = min; min = t; }
            return ThreadLocalRandom.current().nextInt(min, max + 1);
        }
        return Integer.parseInt(strInteger);
    }

    public static String randomColor() {
        String hexDigits = "0123456789abcdef";
        char color = hexDigits.charAt(ThreadLocalRandom.current().nextInt(hexDigits.length()));
        return "§" + color;
    }
}
