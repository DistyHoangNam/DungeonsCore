package camchua.phoban.phobanpro.compat.api;

import org.bukkit.inventory.ItemStack;

public interface ItemStackMetadata {

    ItemStack setString(ItemStack item, String key, String value);

    String getString(ItemStack item, String key);

    default boolean hasString(ItemStack item, String key) {
        return getString(item, key) != null;
    }
}
