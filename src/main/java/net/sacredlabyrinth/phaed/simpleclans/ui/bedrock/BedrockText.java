package net.sacredlabyrinth.phaed.simpleclans.ui.bedrock;

import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class BedrockText {

    private BedrockText() {
    }

    @NotNull
    static String lang(@NotNull String key, @NotNull Player player, Object... args) {
        return clean(SimpleClans.lang(key, player, args));
    }

    @NotNull
    static String clean(@Nullable String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text));
    }
}
