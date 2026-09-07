package camchua.phoban.phobanpro.compat.modern;

import camchua.phoban.phobanpro.compat.api.ItemStackMetadata;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ModernItemStackMetadata implements ItemStackMetadata {

    private final JavaPlugin plugin;

    public ModernItemStackMetadata(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ItemStack setString(ItemStack item, String key, String value) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        NamespacedKey nk = new NamespacedKey(plugin, key);
        meta.getPersistentDataContainer().set(nk, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public String getString(ItemStack item, String key) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        NamespacedKey nk = new NamespacedKey(plugin, key);
        if (!meta.getPersistentDataContainer().has(nk, PersistentDataType.STRING)) {
            return null;
        }
        return meta.getPersistentDataContainer().get(nk, PersistentDataType.STRING);
    }
}
