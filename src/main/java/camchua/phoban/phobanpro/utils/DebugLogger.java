package camchua.phoban.phobanpro.utils;

import camchua.phoban.phobanpro.DungeonsCore;
import camchua.phoban.phobanpro.manager.FileManager;
import org.bukkit.configuration.file.FileConfiguration;

public final class DebugLogger {

    private static final String PREFIX = "[DungeonsCore Debug] ";

    private DebugLogger() {}

    public static boolean isEnabled() {
        FileConfiguration cfg = FileManager.getFileConfig(FileManager.Files.CONFIG);
        if (cfg == null) return false;
        Object raw = cfg.get("Debug");
        if (raw instanceof Boolean value) return value;
        if (raw == null) return false;
        String text = raw.toString().trim();
        return text.equalsIgnoreCase("on")
                || text.equalsIgnoreCase("true")
                || text.equalsIgnoreCase("yes")
                || text.equalsIgnoreCase("1");
    }

    public static void setEnabled(boolean enabled) {
        FileConfiguration cfg = FileManager.getFileConfig(FileManager.Files.CONFIG);
        if (cfg == null) return;
        cfg.set("Debug", enabled ? "on" : "off");
        FileManager.saveFileConfig(cfg, FileManager.Files.CONFIG);
    }

    public static String statusText() {
        return isEnabled() ? "on" : "off";
    }

    public static void log(String message) {
        if (!isEnabled()) return;
        DungeonsCore.inst().getLogger().info(PREFIX + message);
    }

    public static void logWaiting(boolean waitingDebugEnabled, String message) {
        if (!isEnabled() && !waitingDebugEnabled) return;
        DungeonsCore.inst().getLogger().info(PREFIX + "[WaitingRoom] " + message);
    }

    public static void logStage(boolean stageDebugEnabled, String message) {
        if (!isEnabled() && !stageDebugEnabled) return;
        DungeonsCore.inst().getLogger().info(PREFIX + message);
    }
}
