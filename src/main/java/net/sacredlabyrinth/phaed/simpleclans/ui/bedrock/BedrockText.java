package net.sacredlabyrinth.phaed.simpleclans.ui.bedrock;

import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BedrockText {

    private static final Pattern HEX = Pattern.compile("§x((?:§[0-9a-fA-F]){6})");
    // §m and §n are colors on Bedrock, not strikethrough and underline
    private static final Pattern JAVA_ONLY_FORMAT = Pattern.compile("§[mMnN]");
    private static final char[] LEGACY_CODES = "0123456789abcdef".toCharArray();
    private static final int[] LEGACY_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    private BedrockText() {
    }

    /**
     * A message for titles and content, keeping its colors
     */
    @NotNull
    static String lang(@NotNull String key, @NotNull Player player, Object... args) {
        return color(SimpleClans.lang(key, player, args));
    }

    /**
     * A message for buttons, with the colors adjusted to their light background
     */
    @NotNull
    static String button(@NotNull String key, @NotNull Player player, Object... args) {
        return buttonColor(SimpleClans.lang(key, player, args));
    }

    @NotNull
    static String clean(@Nullable String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text));
    }

    /**
     * Converts the text to colors Bedrock understands: {@code &} codes, hex colors to the nearest legacy color,
     * and no strikethrough or underline
     */
    @NotNull
    static String color(@Nullable String text) {
        if (text == null) {
            return "";
        }
        String colored = ChatColor.translateAlternateColorCodes('&', text);
        Matcher matcher = HEX.matcher(colored);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1).replace("§", "");
            matcher.appendReplacement(buffer, "§" + nearestLegacy(Integer.parseInt(hex, 16)));
        }
        matcher.appendTail(buffer);
        return JAVA_ONLY_FORMAT.matcher(buffer.toString()).replaceAll("");
    }

    /**
     * Like {@link #color(String)}, but light colors become their dark variant, so they can be read on a button
     */
    @NotNull
    static String buttonColor(@Nullable String text) {
        String colored = color(text);
        StringBuilder out = new StringBuilder(colored.length());
        for (int i = 0; i < colored.length(); i++) {
            char c = colored.charAt(i);
            out.append(c);
            if (c == '§' && i + 1 < colored.length()) {
                out.append(darken(colored.charAt(++i)));
            }
        }
        return out.toString();
    }

    private static char darken(char code) {
        switch (Character.toLowerCase(code)) {
            case 'f':
                return '0';
            case '7':
                return '8';
            case 'e':
                return '6';
            case 'a':
                return '2';
            case 'b':
                return '3';
            case 'd':
                return '5';
            case '9':
                return '1';
            default:
                return code;
        }
    }

    static char nearestLegacy(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < LEGACY_RGB.length; i++) {
            int dr = r - (LEGACY_RGB[i] >> 16 & 0xFF);
            int dg = g - (LEGACY_RGB[i] >> 8 & 0xFF);
            int db = b - (LEGACY_RGB[i] & 0xFF);
            long distance = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return LEGACY_CODES[best];
    }
}
