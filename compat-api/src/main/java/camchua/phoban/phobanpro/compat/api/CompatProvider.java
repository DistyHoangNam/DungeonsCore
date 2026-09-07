package camchua.phoban.phobanpro.compat.api;

import org.bukkit.plugin.java.JavaPlugin;

public final class CompatProvider {

    private static CompatProvider instance;

    private final JavaPlugin plugin;
    private final ItemStackMetadata itemMetadata;
    private final MaterialBridge materials;
    private final EnchantmentBridge enchantments;

    public CompatProvider(
            JavaPlugin plugin,
            ItemStackMetadata itemMetadata,
            MaterialBridge materials,
            EnchantmentBridge enchantments
    ) {
        this.plugin = plugin;
        this.itemMetadata = itemMetadata;
        this.materials = materials;
        this.enchantments = enchantments;
    }

    public static void init(CompatProvider provider) {
        instance = provider;
    }

    public static CompatProvider get() {
        if (instance == null) {
            throw new IllegalStateException("CompatProvider not initialized");
        }
        return instance;
    }

    public static boolean isReady() {
        return instance != null;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public ItemStackMetadata itemMetadata() {
        return itemMetadata;
    }

    public MaterialBridge materials() {
        return materials;
    }

    public EnchantmentBridge enchantments() {
        return enchantments;
    }
}
