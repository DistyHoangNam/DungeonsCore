package camchua.phoban.phobanpro.compat.modern;

import camchua.phoban.phobanpro.compat.api.MaterialBridge;

import org.bukkit.Material;

import java.util.Locale;

public final class ModernMaterialBridge implements MaterialBridge {

    @Override
    public Material match(String name) {
        if (name == null) {
            return Material.STONE;
        }
        String normalized = name.trim();
        if (normalized.isEmpty()) {
            return Material.STONE;
        }

        normalized = normalized.toUpperCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
        Material m = Material.matchMaterial(normalized);
        return m != null ? m : Material.STONE;
    }
}
