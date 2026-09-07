package camchua.phoban.phobanpro.manager;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Cấu hình hook plugin (đọc từ {@link FileManager.Files#INTEGRATIONS}).
 * Chỉ bật listener/expansion khi plugin tương ứng tồn tại và flag bật.
 */
public final class IntegrationConfig {

    private IntegrationConfig() {}

    private static FileConfiguration cfg() {
        return FileManager.getFileConfig(FileManager.Files.INTEGRATIONS);
    }

    public static boolean isMythicListenerEnabled() {
        return cfg().getBoolean("Mythic.RegisterDamageListener", true);
    }

    public static boolean isSkillApiListenerEnabled() {
        return cfg().getBoolean("SkillAPI.RegisterListener", true);
    }

    public static boolean isPlaceholderApiEnabled() {
        return cfg().getBoolean("PlaceholderAPI.RegisterExpansion", true);
    }

    public static boolean isLuckPermsEnabled() {
        return cfg().getBoolean("LuckPerms.ConnectService", true);
    }
}
