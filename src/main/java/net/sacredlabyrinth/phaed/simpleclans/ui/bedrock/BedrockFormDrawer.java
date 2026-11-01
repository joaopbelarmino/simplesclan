package net.sacredlabyrinth.phaed.simpleclans.ui.bedrock;

import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryController;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponent;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCFrame;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.meta.ItemMeta;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Shows the Java inventories as Bedrock forms. The main screens have their own forms; the other inventories are
 * converted: clickable items become buttons and information items go to the form text.
 */
public final class BedrockFormDrawer {

    private static final SimpleClans plugin = SimpleClans.getInstance();
    private static final int MAX_LORE_LINES = 2;

    private BedrockFormDrawer() {
    }

    public static boolean isBedrockPlayer(@NotNull Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            return false;
        }
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (NoClassDefFoundError | IllegalStateException ex) {
            return false;
        }
    }

    /**
     * Reloads bedrock-icons.yml
     */
    public static void reloadIcons() {
        BedrockFormIconResolver.reload();
    }

    public static void open(@NotNull SCFrame frame) {
        Player viewer = frame.getViewer();
        Runnable back = () -> {
            if (frame.getParent() != null) {
                InventoryDrawer.open(frame.getParent());
            }
        };
        if (frame instanceof MainFrame) {
            BedrockForms.runSync(() -> BedrockClanHomeDrawer.open((MainFrame) frame));
            return;
        }
        if (frame instanceof RosterFrame) {
            BedrockForms.runSync(() -> BedrockRosterDrawer.open((RosterFrame) frame));
            return;
        }
        if (frame instanceof InviteFrame) {
            BedrockForms.runSync(() -> BedrockClanScreens.openInvite(viewer, back));
            return;
        }
        if (frame instanceof LanguageSelectorFrame) {
            BedrockForms.runSync(() -> BedrockClanScreens.openLanguage(viewer, back));
            return;
        }
        if (frame instanceof RelationsFrame) {
            BedrockForms.runSync(() -> BedrockClanScreens.openRelations(viewer, ((RelationsFrame) frame).getClan(), back));
            return;
        }
        if (frame instanceof ClanRankingFrame) {
            BedrockForms.runSync(() -> BedrockClanScreens.openRanking(viewer, back));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!InventoryDrawer.prepareComponents(frame)) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) {
                    return;
                }
                if (frame instanceof ConfirmationFrame) {
                    sendConfirmationForm(frame);
                    return;
                }
                sendSimpleFrame(frame);
            });
        });
    }

    private static void sendSimpleFrame(@NotNull SCFrame frame) {
        Player viewer = frame.getViewer();
        List<BedrockAction> actions = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        SimpleForm.Builder builder = SimpleForm.builder().title(BedrockText.color(frame.getTitle()));

        for (SCComponent component : getSortedComponents(frame)) {
            List<ClickType> clicks = getSortedClicks(component.getAvailableClicks());
            if (clicks.isEmpty()) {
                appendInfo(content, component, viewer);
                continue;
            }
            FormImage image = BedrockFormIconResolver.getIcon(component.getItem());
            String text = getButtonText(component, viewer);
            if (image == null) {
                builder.button(text);
            } else {
                builder.button(text, image);
            }
            actions.add(new BedrockAction(component.getSlot(), clicks.get(0)));
        }
        builder.content(content.length() == 0 ? BedrockText.lang("bedrock.gui.content", viewer) : content.toString().trim());

        builder.validResultHandler(response -> {
            int buttonId = response.clickedButtonId();
            if (buttonId < 0 || buttonId >= actions.size()) {
                return;
            }
            BedrockAction action = actions.get(buttonId);
            SCComponent component = frame.getComponent(action.getSlot());
            if (component == null) {
                showStaleFrameError(frame);
                return;
            }
            List<ClickType> clicks = getSortedClicks(component.getAvailableClicks());
            if (clicks.size() > 1) {
                BedrockForms.runSync(() -> sendActionPicker(frame, component));
                return;
            }
            runFreshAction(frame, action);
        });

        BedrockForms.send(viewer, builder);
    }

    private static void sendActionPicker(@NotNull SCFrame frame, @NotNull SCComponent component) {
        Player viewer = frame.getViewer();
        List<ClickType> clicks = getSortedClicks(component.getAvailableClicks());
        BedrockForms.Menu menu = new BedrockForms.Menu(viewer, getTitleText(component),
                BedrockText.lang("bedrock.gui.choose-action", viewer));
        for (ClickType click : clicks) {
            menu.button(getClickName(click, viewer), (FormImage) null,
                    () -> runFreshAction(frame, new BedrockAction(component.getSlot(), click)));
        }
        menu.back(() -> InventoryDrawer.open(frame));
        menu.send();
    }

    private static void sendConfirmationForm(@NotNull SCFrame frame) {
        Player viewer = frame.getViewer();
        List<SCComponent> components = new ArrayList<>();
        for (SCComponent component : getSortedComponents(frame)) {
            if (!component.getAvailableClicks().isEmpty()) {
                components.add(component);
            }
        }
        if (components.size() < 2) {
            sendSimpleFrame(frame);
            return;
        }

        SCComponent confirm = components.get(0);
        SCComponent cancel = components.get(1);
        ModalForm.Builder builder = ModalForm.builder()
                .title(BedrockText.color(frame.getTitle()))
                .content(BedrockText.lang("bedrock.gui.confirm-content", viewer))
                .button1(getTitleText(confirm))
                .button2(getTitleText(cancel));

        builder.validResultHandler(response -> {
            SCComponent selected = response.clickedFirst() ? confirm : cancel;
            runFreshAction(frame, new BedrockAction(selected.getSlot(), ClickType.LEFT));
        });

        BedrockForms.send(viewer, builder);
    }

    private static void runFreshAction(@NotNull SCFrame frame, @NotNull BedrockAction action) {
        BedrockForms.runSync(() -> {
            Player viewer = frame.getViewer();
            if (!viewer.isOnline()) {
                return;
            }
            if (!InventoryDrawer.prepareComponents(frame)) {
                showStaleFrameError(frame);
                return;
            }
            SCComponent freshComponent = frame.getComponent(action.getSlot());
            if (freshComponent == null || freshComponent.getListener(action.getClickType()) == null) {
                showStaleFrameError(frame);
                return;
            }
            InventoryController.handleComponentClick(viewer, frame, freshComponent, action.getClickType());
        });
    }

    private static void showStaleFrameError(@NotNull SCFrame frame) {
        Player viewer = frame.getViewer();
        viewer.sendMessage(BedrockText.lang("bedrock.gui.stale", viewer));
        BedrockForms.runSync(() -> InventoryDrawer.open(frame.getParent() != null ? frame.getParent() : frame));
    }

    @NotNull
    private static List<SCComponent> getSortedComponents(@NotNull SCFrame frame) {
        List<SCComponent> components = new ArrayList<>(frame.getComponents());
        components.removeIf(component -> component.getSlot() >= frame.getSize());
        components.sort(Comparator.comparingInt(SCComponent::getSlot));
        return components;
    }

    @NotNull
    private static List<ClickType> getSortedClicks(@NotNull Set<ClickType> clicks) {
        List<ClickType> sorted = new ArrayList<>(clicks);
        sorted.removeIf(click -> click == ClickType.UNKNOWN);
        sorted.sort(Comparator.comparingInt(BedrockFormDrawer::clickWeight));
        return sorted;
    }

    private static int clickWeight(@NotNull ClickType clickType) {
        switch (clickType) {
            case LEFT:
                return 0;
            case RIGHT:
                return 1;
            case SHIFT_LEFT:
                return 2;
            case SHIFT_RIGHT:
                return 3;
            case DROP:
                return 4;
            default:
                return 10;
        }
    }

    /**
     * Items without a click (stats, legends) are written in the form text instead of disappearing
     */
    private static void appendInfo(@NotNull StringBuilder content, @NotNull SCComponent component,
                                   @NotNull Player viewer) {
        String title = getTitleText(component);
        if (BedrockText.clean(title).trim().isEmpty()) {
            return;
        }
        content.append("§l").append(title).append("§r\n");
        for (String line : getLore(component, viewer, Integer.MAX_VALUE)) {
            content.append(BedrockText.color(line)).append("§r\n");
        }
        content.append('\n');
    }

    @NotNull
    private static String getTitleText(@NotNull SCComponent component) {
        ItemMeta meta = component.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return BedrockText.color(meta.getDisplayName());
        }
        return BedrockText.clean(component.getItem().getType().name());
    }

    /**
     * The item name plus a short lore, without the click hints that make no sense on Bedrock
     */
    @NotNull
    private static String getButtonText(@NotNull SCComponent component, @NotNull Player viewer) {
        StringBuilder text = new StringBuilder(BedrockText.buttonColor(getTitleText(component)));
        for (String line : getLore(component, viewer, MAX_LORE_LINES)) {
            text.append("§r\n").append(BedrockText.buttonColor(line));
        }
        return text.toString();
    }

    @NotNull
    private static List<String> getLore(@NotNull SCComponent component, @NotNull Player viewer, int max) {
        ItemMeta meta = component.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null) {
            return Collections.emptyList();
        }
        String[] hints = SimpleClans.lang("bedrock.gui.click-hint-words", viewer).toLowerCase(Locale.ROOT).split("\\|");
        List<String> lines = new ArrayList<>();
        for (String line : meta.getLore()) {
            String clean = BedrockText.clean(line).trim();
            if (clean.isEmpty() || isClickHint(clean.toLowerCase(Locale.ROOT), hints)) {
                continue;
            }
            lines.add(line);
            if (lines.size() >= max) {
                break;
            }
        }
        return lines;
    }

    private static boolean isClickHint(@NotNull String line, @NotNull String[] hints) {
        for (String hint : hints) {
            if (!hint.trim().isEmpty() && line.contains(hint.trim())) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    private static String getClickName(@NotNull ClickType clickType, @NotNull Player player) {
        switch (clickType) {
            case LEFT:
                return BedrockText.button("bedrock.gui.action.left", player);
            case RIGHT:
                return BedrockText.button("bedrock.gui.action.right", player);
            case SHIFT_LEFT:
                return BedrockText.button("bedrock.gui.action.shift-left", player);
            case SHIFT_RIGHT:
                return BedrockText.button("bedrock.gui.action.shift-right", player);
            case DROP:
                return BedrockText.button("bedrock.gui.action.drop", player);
            default:
                return BedrockText.clean(clickType.name());
        }
    }
}
