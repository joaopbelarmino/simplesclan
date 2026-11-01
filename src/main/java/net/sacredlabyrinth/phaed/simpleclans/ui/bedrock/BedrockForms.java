package net.sacredlabyrinth.phaed.simpleclans.ui.bedrock;

import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.form.util.FormBuilder;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.COMMANDS_CLAN;

/**
 * What every Bedrock menu needs: sending forms on the main thread, buttons bound to actions, confirmations and
 * running clan commands. Closing a form (X) closes the whole menu; only "Back" buttons navigate.
 */
final class BedrockForms {

    private static final SimpleClans plugin = SimpleClans.getInstance();

    private BedrockForms() {
    }

    static void send(@NotNull Player player, @NotNull FormBuilder<?, ?, ?> builder) {
        runSync(() -> {
            if (player.isOnline()) {
                FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder);
            }
        });
    }

    static void runSync(@NotNull Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    /**
     * Runs a clan subcommand (English names always work, the translated ones are aliases)
     */
    static void runClanCommand(@NotNull Player player, @NotNull String subcommand) {
        runClanCommand(player, subcommand, null);
    }

    /**
     * Runs a clan subcommand, then opens the given menu again so it shows the new state
     */
    static void runClanCommand(@NotNull Player player, @NotNull String subcommand, @Nullable Runnable reopen) {
        runSync(() -> {
            player.performCommand(plugin.getSettingsManager().getString(COMMANDS_CLAN) + " " + subcommand);
            if (reopen != null) {
                // Next tick, so the command already changed what the menu shows
                Bukkit.getScheduler().runTask(plugin, reopen);
            }
        });
    }

    static void confirm(@NotNull Player player, @NotNull String title, @NotNull String content,
                        @NotNull Runnable confirm, @NotNull Runnable cancel) {
        ModalForm.Builder builder = ModalForm.builder()
                .title(title)
                .content(content)
                .button1(BedrockText.button("bedrock.confirm.yes", player))
                .button2(BedrockText.button("bedrock.confirm.no", player));
        builder.validResultHandler(response -> runSync(response.clickedFirst() ? confirm : cancel));
        send(player, builder);
    }

    /**
     * A simple form whose buttons run the action added with them
     */
    static final class Menu {
        private final Player player;
        private final SimpleForm.Builder builder;
        private final List<Runnable> actions = new ArrayList<>();

        Menu(@NotNull Player player, @NotNull String title, @NotNull String content) {
            this.player = player;
            builder = SimpleForm.builder().title(title).content(content);
        }

        /**
         * @param text already formatted button text, see {@link BedrockText#button}
         * @param icon a key of the buttons section of bedrock-icons.yml
         */
        Menu button(@NotNull String text, @NotNull String icon, @NotNull Runnable action) {
            return button(text, BedrockFormIconResolver.button(icon), action);
        }

        Menu button(@NotNull String text, @Nullable FormImage image, @NotNull Runnable action) {
            if (image == null) {
                builder.button(text);
            } else {
                builder.button(text, image);
            }
            actions.add(action);
            return this;
        }

        Menu back(@NotNull Runnable action) {
            return button(BedrockText.button("bedrock.gui.back", player), "back", action);
        }

        boolean isEmpty() {
            return actions.isEmpty();
        }

        void send() {
            builder.validResultHandler(response -> {
                int id = response.clickedButtonId();
                if (id >= 0 && id < actions.size()) {
                    runSync(actions.get(id));
                }
            });
            BedrockForms.send(player, builder);
        }
    }
}
