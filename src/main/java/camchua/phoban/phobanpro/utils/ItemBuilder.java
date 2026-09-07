package camchua.phoban.phobanpro.utils;

import camchua.phoban.phobanpro.manager.FileManager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class ItemBuilder {

    private static final String BASEHEAD_PREFIX = "basehead-";

    private Material material = Material.STONE;
    private int amount = 1;
    private String name;
    private List<String> lores;
    private int customModel = 0;
    private String skullOwner;
    private String skullBase64;
    private boolean glow = false;

    public static ItemStack build(FileManager.Files file, String path, HashMap<String, List<String>> replace) {
        FileConfiguration config = FileManager.getFileConfig(file);
        ItemBuilder builder = new ItemBuilder();
        String itemId = replacePlaceholders(config.getString(path + ".ID", "STONE"), replace);
        if (startsWithIgnoreCase(itemId, BASEHEAD_PREFIX)) {
            builder.material(Material.PLAYER_HEAD);
            builder.baseHead(itemId.substring(BASEHEAD_PREFIX.length()).trim());
        } else {
            builder.material(Utils.matchMaterial(itemId));
        }

        if (config.contains(path + ".Amount")) builder.amount(config.getInt(path + ".Amount"));

        if (config.contains(path + ".Name")) {
            String itemName = ColorUtils.colorize(config.getString(path + ".Name"));
            for (var entry : replace.entrySet()) {
                for (String value : entry.getValue()) {
                    itemName = itemName.replace(entry.getKey(), value == null ? "" : value);
                }
            }
            itemName = ColorUtils.colorize(itemName);
            builder.name(itemName);
        }

        if (config.contains(path + ".Lore")) {
            List<String> lores = new ArrayList<>();
            for (String lore : config.getStringList(path + ".Lore")) {
                String newLore = ColorUtils.colorize(lore);
                boolean handled = false;

                for (int i = 1; i <= 10; i++) {
                    if (lore.contains("<mob" + i + ">") && replace.containsKey(lore)) {
                        for (String value : replace.get(lore)) lores.add(ColorUtils.colorize(lore.replace(lore, value)));
                        handled = true;
                        break;
                    }
                }
                if (handled) continue;

                if (lore.contains("<players>") && replace.containsKey(lore)) {
                    for (String value : replace.get(lore)) lores.add(ColorUtils.colorize(lore.replace(lore, value)));
                    continue;
                }

                for (var entry : replace.entrySet()) {
                    for (String value : entry.getValue()) {
                        newLore = newLore.replace(entry.getKey(), value == null ? "" : value);
                    }
                }
                lores.add(ColorUtils.colorize(newLore));
            }
            builder.lore(lores);
        }

        if (config.contains(path + ".CustomModel")) {
            int cm = config.getInt(path + ".CustomModel", 0);
            if (cm > 0 && Utils.getVersionMinor() >= 14) builder.customModel(cm);
        }

        return builder.build();
    }

    public static ItemStack skull(ItemStack origin, String target) {
        String matName = origin.getType().name().toLowerCase();
        if (matName.contains("player_head") || matName.contains("skull_item")) {
            if (origin.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(target));
                origin.setItemMeta(meta);
            }
        }
        return origin;
    }

    public ItemBuilder material(Material m) { this.material = m; return this; }
    public ItemBuilder amount(int a) { this.amount = a; return this; }
    public ItemBuilder name(String n) { this.name = n; return this; }
    public ItemBuilder lore(List<String> l) { this.lores = l; return this; }
    public ItemBuilder customModel(int cm) { this.customModel = cm; return this; }
    public ItemBuilder skull(String s) { this.skullOwner = s; return this; }
    public ItemBuilder baseHead(String b64) { this.skullBase64 = b64; return this; }
    public ItemBuilder glow(boolean g) { this.glow = g; return this; }

    public ItemStack build() {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (name != null) meta.setDisplayName(name);
        if (lores != null) meta.setLore(lores);
        if (glow) {
            meta.addEnchant(Utils.getUnbreakingEnchantment(), 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (customModel > 0) meta.setCustomModelData(customModel);

        if (skullBase64 != null && meta instanceof SkullMeta skullMeta) {
            applyBaseHead(skullMeta, skullBase64);
        } else if (skullOwner != null && meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(skullOwner));
        }

        item.setItemMeta(meta);
        return item;
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value != null && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static String replacePlaceholders(String value, HashMap<String, List<String>> replace) {
        if (value == null || replace == null || replace.isEmpty()) return value;
        String result = value;
        for (var entry : replace.entrySet()) {
            List<String> values = entry.getValue();
            String replacement = values == null || values.isEmpty() || values.get(0) == null ? "" : values.get(0);
            result = result.replace(entry.getKey(), replacement);
        }
        return result;
    }

    private static void applyBaseHead(SkullMeta meta, String base64Texture) {
        try {
            String skinUrl = decodeSkinUrl(base64Texture);
            if (skinUrl == null || skinUrl.isBlank()) return;

            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(skinUrl));
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (Exception ignored) {
            // Invalid texture values intentionally fall back to a normal player head.
        }
    }

    private static String decodeSkinUrl(String base64Texture) {
        if (base64Texture == null || base64Texture.isBlank()) return null;

        String decoded = new String(Base64.getDecoder().decode(base64Texture.trim()), StandardCharsets.UTF_8);
        Object parsed = JSONValue.parse(decoded);
        if (!(parsed instanceof JSONObject root)) return null;

        Object texturesObj = root.get("textures");
        if (!(texturesObj instanceof JSONObject textures)) return null;

        Object skinObj = textures.get("SKIN");
        if (!(skinObj instanceof JSONObject skin)) return null;

        Object url = skin.get("url");
        return url instanceof String ? (String) url : null;
    }
}