package camchua.phoban.phobanpro.compat.modern;

import camchua.phoban.phobanpro.compat.api.EnchantmentBridge;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

public final class ModernEnchantmentBridge implements EnchantmentBridge {

    @SuppressWarnings("deprecation")
    @Override
    public Enchantment unbreaking() {
        Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
        if (e != null) {
            return e;
        }
        return Enchantment.DURABILITY;
    }
}
