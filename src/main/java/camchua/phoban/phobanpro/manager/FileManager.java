package camchua.phoban.phobanpro.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.logging.Level;

/**
 * Runtime files under {@code plugins/DungeonsCore/}.
 * Requires Minecraft 1.21.1+ and always uses the modern resource set.
 */
public class FileManager {

    private static final EnumMap<Files, File> fileMap = new EnumMap<>(Files.class);
    private static final EnumMap<Files, FileConfiguration> configMap = new EnumMap<>(Files.class);

    public static void setup(Plugin plugin) {
        fileMap.clear();
        configMap.clear();
        for (Files f : Files.values()) {
            String name = f.getFileName();
            File fl = new File(plugin.getDataFolder(), name);
            if (!fl.exists()) {
                if (!migrateFromFlatRoot(plugin, fl, name)) {
                    File parent = fl.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    try {
                        plugin.saveResource(name, false);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Could not save resource: " + name, e);
                    }
                }
            }

            var config = loadConfig(plugin, fl, name);
            if (f == Files.GUI || f == Files.GUI_TOP) {
                boolean reset = ModernOnlyGuiReset.resetIfOldMaterials(plugin, fl, name, config);
                if (reset) {
                    config = loadConfig(plugin, fl, name);
                }
            }
            fileMap.put(f, fl);
            configMap.put(f, config);
        }
        plugin.getLogger().info("Resource set: MODERN_ONLY_1_21_PLUS -> "
                + Files.GUI.getFileName() + " / " + Files.GUI_TOP.getFileName());
    }

    private static FileConfiguration loadConfig(Plugin plugin, File file, String relativePath) {
        var config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load config: " + relativePath, e);
        }
        return config;
    }

    private static boolean migrateFromFlatRoot(Plugin plugin, File destination, String relativePath) {
        String basename = new File(relativePath).getName();
        File flat = new File(plugin.getDataFolder(), basename);
        if (!flat.isFile()) {
            return false;
        }
        try {
            File parent = destination.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            java.nio.file.Files.copy(flat.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Migrated config: " + basename + " -> " + relativePath.replace('\\', '/'));
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not migrate " + basename + " to " + relativePath, e);
            return false;
        }
    }

    public static FileConfiguration getFileConfig(Files f) {
        return configMap.get(f);
    }

    public static void saveFileConfig(FileConfiguration data, Files f) {
        try { data.save(fileMap.get(f)); } catch (Exception ignored) {}
    }

    public static void saveFileConfig(FileConfiguration data, File f) {
        try { data.save(f); } catch (Exception ignored) {}
    }

    public enum Files {
        CONFIG("config/config.yml"),
        MESSAGE("lang/message.yml"),
        GUI("gui/gui.yml"),
        DATA("storage/data.yml"),
        PHOBAN("game/phoban.yml"),
        SPAWN("game/spawn.yml"),
        FORMAT("config/format.yml"),
        GUI_TOP("gui/gui-top.yml"),
        INTEGRATIONS("config/config-integrations.yml");

        private final String fileName;

        Files(String fileName) {
            this.fileName = fileName;
        }

        public String getFileName() {
            return fileName;
        }
    }
}
