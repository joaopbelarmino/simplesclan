package net.sacredlabyrinth.phaed.simpleclans.ui.bedrock;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.geysermc.cumulus.util.FormImage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class BedrockFormIconResolver {

    private static final Map<String, String> ICONS = new HashMap<>();

    static {
        ICONS.put("BOOK", "textures/items/book_normal");
        ICONS.put("PAINTING", "textures/items/painting");
        ICONS.put("ANVIL", "textures/blocks/anvil_top_damaged_0");
        ICONS.put("GOLD_INGOT", "textures/items/gold_ingot");
        ICONS.put("DIAMOND", "textures/items/diamond");
        ICONS.put("EMERALD", "textures/items/emerald");
        ICONS.put("MAP", "textures/items/map_empty");
        ICONS.put("LIME_WOOL", "textures/blocks/wool_colored_lime");
        ICONS.put("RED_WOOL", "textures/blocks/wool_colored_red");
        ICONS.put("WHITE_WOOL", "textures/blocks/wool_colored_white");
        ICONS.put("PURPLE_BANNER", "textures/items/banner_base");
        ICONS.put("CYAN_BANNER", "textures/items/banner_base");
        ICONS.put("RED_BANNER", "textures/items/banner_base");
        ICONS.put("PLAYER_HEAD", "textures/items/skull_steve");
        ICONS.put("SKELETON_SKULL", "textures/items/skull_skeleton");
        ICONS.put("COMMAND_BLOCK", "textures/blocks/command_block");
        ICONS.put("LEVER", "textures/blocks/lever");
    }

    private BedrockFormIconResolver() {
    }

    @Nullable
    static FormImage getIcon(@NotNull ItemStack itemStack) {
        Material material = itemStack.getType();
        String path = ICONS.get(material.name());
        if (path == null) {
            path = fallbackItemPath(material);
        }
        if (path == null) {
            return null;
        }
        return FormImage.of(FormImage.Type.PATH, path);
    }

    @NotNull
    static FormImage ui(@NotNull String path) {
        return FormImage.of(FormImage.Type.PATH, path);
    }

    @Nullable
    private static String fallbackItemPath(@NotNull Material material) {
        String name = material.name().toLowerCase(Locale.ROOT);
        if (name.endsWith("_banner")) {
            return "textures/items/banner_base";
        }
        if (name.endsWith("_wool")) {
            return "textures/blocks/wool_colored_white";
        }
        return null;
    }
}
