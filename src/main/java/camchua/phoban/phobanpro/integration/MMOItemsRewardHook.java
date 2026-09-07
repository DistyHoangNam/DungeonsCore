package camchua.phoban.phobanpro.integration;

import camchua.phoban.phobanpro.DungeonsCore;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Resolves saved MMOItems reward snapshots against the current MMOItems templates. */
public final class MMOItemsRewardHook {

    private MMOItemsRewardHook() {}

    public static ItemStack resolve(ItemStack snapshot, String roomName, String rewardKey) {
        if (snapshot == null) return null;

        ItemStack fallback = snapshot.clone();
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems")) return fallback;

        try {
            Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            Method getTypeName = mmoItemsClass.getMethod("getTypeName", ItemStack.class);
            Method getId = mmoItemsClass.getMethod("getID", ItemStack.class);
            String type = (String) getTypeName.invoke(null, snapshot);
            String id = (String) getId.invoke(null, snapshot);
            if (isBlank(type) || isBlank(id)) return fallback;

            Field pluginField = mmoItemsClass.getField("plugin");
            Object plugin = pluginField.get(null);
            Method getItem = mmoItemsClass.getMethod("getItem", String.class, String.class);
            ItemStack current = plugin == null ? null : (ItemStack) getItem.invoke(plugin, type, id);
            if (current == null) {
                warn(roomName, rewardKey, type, id, "template does not exist");
                return fallback;
            }

            current = current.clone();
            current.setAmount(snapshot.getAmount());
            return current;
        } catch (Exception | LinkageError ex) {
            warn(roomName, rewardKey, null, null,
                    ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage()));
            return fallback;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void warn(String roomName, String rewardKey, String type, String id, String reason) {
        DungeonsCore.inst().getLogger().warning("Could not refresh MMOItems reward; using saved snapshot."
                + " room=" + roomName
                + ", reward=" + rewardKey
                + (type == null ? "" : ", type=" + type)
                + (id == null ? "" : ", id=" + id)
                + ", reason=" + reason);
    }
}
