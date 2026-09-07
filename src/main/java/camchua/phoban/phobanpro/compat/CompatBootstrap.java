package camchua.phoban.phobanpro.compat;

import camchua.phoban.phobanpro.compat.api.CompatProvider;
import camchua.phoban.phobanpro.compat.modern.ModernEnchantmentBridge;
import camchua.phoban.phobanpro.compat.modern.ModernItemStackMetadata;
import camchua.phoban.phobanpro.compat.modern.ModernMaterialBridge;

import org.bukkit.plugin.java.JavaPlugin;

public final class CompatBootstrap {

    private CompatBootstrap() {}

    public static void init(JavaPlugin plugin) {
        CompatProvider.init(new CompatProvider(
                plugin,
                new ModernItemStackMetadata(plugin),
                new ModernMaterialBridge(),
                new ModernEnchantmentBridge()
        ));
        plugin.getLogger().info("Compat: MODERN_ONLY_1_21_PLUS - item metadata: PDC");
    }
}
