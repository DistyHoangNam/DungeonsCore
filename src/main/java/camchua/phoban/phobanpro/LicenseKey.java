package camchua.phoban.phobanpro;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class LicenseKey {

    private static final String NAME = "DungeonsCore";

    public static boolean setup(Plugin plugin) {
        String border = ChatColor.DARK_GREEN + "============================";
        Bukkit.getConsoleSender().sendMessage(border);
        Bukkit.getConsoleSender().sendMessage(ChatColor.WHITE + "| Product: " + ChatColor.GOLD + NAME);
        Bukkit.getConsoleSender().sendMessage(ChatColor.WHITE + "| Version: " + ChatColor.GREEN + plugin.getDescription().getVersion());
        Bukkit.getConsoleSender().sendMessage(ChatColor.WHITE + "| Support: " + ChatColor.GREEN + "Bukkit, Spigot, PaperMC, Purpur");
        Bukkit.getConsoleSender().sendMessage(border);
        Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "[" + NAME + "] " + ChatColor.GREEN + "Plugin Active.");
        return true;
    }

    public static void disablePlugin(Plugin plugin) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.YELLOW + "[" + NAME + "] " + ChatColor.GREEN + "Plugin disabled.");
    }
}
