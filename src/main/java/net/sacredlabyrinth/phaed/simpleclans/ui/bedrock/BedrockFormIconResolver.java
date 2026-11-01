package net.sacredlabyrinth.phaed.simpleclans.ui.bedrock;

import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Icons of the Bedrock forms, read from bedrock-icons.yml so they can be changed without a new build
 */
final class BedrockFormIconResolver {

    private static final String FILE_NAME = "bedrock-icons.yml";
    private static final String STEVE = "textures/ui/icon_steve";
    private static YamlConfiguration config;

    private BedrockFormIconResolver() {
    }

    /**
     * Reloads bedrock-icons.yml on the next icon lookup
     */
    static void reload() {
        config = null;
    }

    /**
     * @param key a key of the buttons section, like "war-join"
     */
    @Nullable
    static FormImage button(@NotNull String key) {
        return image(getConfig().getString("buttons." + key));
    }

    @Nullable
    static FormImage getIcon(@NotNull ItemStack itemStack) {
        Material material = itemStack.getType();
        String path = getConfig().getString("items." + material.name());
        if (path == null) {
            path = fallbackItemPath(material);
        }
        return image(path);
    }

    /**
     * The face of the player, from the URL in player-head-url. Bedrock players have no Java skin, so they get Steve.
     */
    @Nullable
    static FormImage playerHead(@NotNull UUID uuid, @NotNull String name) {
        String url = getConfig().getString("player-head-url", "");
        if (url == null || url.isEmpty() || isFloodgateId(uuid)) {
            return image(getConfig().getString("buttons.member", STEVE));
        }
        return image(url.replace("{uuid}", uuid.toString().replace("-", "")).replace("{name}", name));
    }

    private static boolean isFloodgateId(@NotNull UUID uuid) {
        try {
            return FloodgateApi.getInstance().isFloodgateId(uuid);
        } catch (NoClassDefFoundError | IllegalStateException ex) {
            return uuid.getMostSignificantBits() == 0;
        }
    }

    @Nullable
    private static FormImage image(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        boolean url = value.startsWith("http://") || value.startsWith("https://");
        return FormImage.of(url ? FormImage.Type.URL : FormImage.Type.PATH, value);
    }

    @NotNull
    private static YamlConfiguration getConfig() {
        YamlConfiguration current = config;
        if (current != null) {
            return current;
        }
        SimpleClans plugin = SimpleClans.getInstance();
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        try {
            if (!file.exists()) {
                plugin.saveResource(FILE_NAME, false);
            }
            current = YamlConfiguration.loadConfiguration(file);
            InputStream stream = plugin.getResource(FILE_NAME);
            if (stream != null) {
                try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    // Keys missing from an older file still get their default icon
                    current.setDefaults(YamlConfiguration.loadConfiguration(reader));
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Could not load " + FILE_NAME, ex);
            current = new YamlConfiguration();
        }
        config = current;
        return current;
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
