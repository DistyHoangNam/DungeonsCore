package camchua.phoban.phobanpro.compat;

import camchua.phoban.phobanpro.compat.api.CompatProvider;

import org.bukkit.inventory.ItemStack;

public final class CompatItems {

    private CompatItems() {}

    public static ItemStack setKey(ItemStack item, String key, String value) {
        return CompatProvider.get().itemMetadata().setString(item, key, value);
    }

    public static String getKey(ItemStack item, String key) {
        return CompatProvider.get().itemMetadata().getString(item, key);
    }
}
