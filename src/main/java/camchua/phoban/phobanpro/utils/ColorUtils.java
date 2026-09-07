package camchua.phoban.phobanpro.utils;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Color helper for Minecraft 1.21.1+.
 * Supports RGB hex tags like {@code <#RRGGBB>} and Bukkit-style {@code &a} codes.
 */
public final class ColorUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final char COLOR_CHAR = '\u00A7';

    private ColorUtils() {}

    public static String colorize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return ChatColor.translateAlternateColorCodes('&', translateHex(text));
    }

    private static String translateHex(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder();
            replacement.append(COLOR_CHAR).append('x');
            for (char c : hex.toCharArray()) {
                replacement.append(COLOR_CHAR).append(c);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
