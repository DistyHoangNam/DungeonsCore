package camchua.phoban.phobanpro.utils;

import camchua.phoban.phobanpro.manager.FileManager;

public class Messages {

    public static String get(String key) {
        String msg = FileManager.getFileConfig(FileManager.Files.MESSAGE).getString(key, key);
        return ColorUtils.colorize(msg);
    }

    public static boolean has(String key) {
        return FileManager.getFileConfig(FileManager.Files.MESSAGE).contains(key);
    }
}
