package camchua.phoban.phobanpro.manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

final class ModernOnlyGuiReset {

    private static final Set<String> OLD_MATERIAL_IDS = Set.of(
            "STAINED_GLASS_PANE",
            "INK_SACK",
            "SKULL_ITEM",
            "WATCH",
            "SIGN",
            "BED",
            "WOOL",
            "STAINED_CLAY",
            "MONSTER_EGG",
            "WOOD_BUTTON",
            "WOOD_DOOR"
    );

    private ModernOnlyGuiReset() {}

    static boolean resetIfOldMaterials(Plugin plugin, File file, String relativePath, FileConfiguration config) {
        if (!containsOldMaterial(config)) {
            return false;
        }

        try {
            backup(file);
            try (InputStream in = plugin.getResource(relativePath.replace('\\', '/'))) {
                if (in == null) {
                    plugin.getLogger().warning("Could not reset " + relativePath + ": bundled resource missing.");
                    return false;
                }
                java.nio.file.Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            plugin.getLogger().info("Modern-only GUI reset: " + relativePath.replace('\\', '/')
                    + " contained old pre-1.13 material IDs; backed up and restored bundled modern config.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not reset old GUI config "
                    + relativePath.replace('\\', '/') + ": " + e.getMessage());
            return false;
        }
    }

    private static boolean containsOldMaterial(ConfigurationSection section) {
        if (section.isString("ID") && OLD_MATERIAL_IDS.contains(normalize(section.getString("ID", "")))) {
            return true;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null && containsOldMaterial(child)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private static void backup(File file) throws Exception {
        File backup = new File(file.getParentFile(), file.getName() + ".bak-modern-reset");
        if (backup.exists()) {
            return;
        }
        java.nio.file.Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
    }
}
