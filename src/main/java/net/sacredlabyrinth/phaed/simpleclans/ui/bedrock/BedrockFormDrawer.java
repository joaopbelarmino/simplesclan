package net.sacredlabyrinth.phaed.simpleclans.ui.bedrock;

import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryController;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponent;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCFrame;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.ConfirmationFrame;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.MainFrame;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.RosterFrame;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.meta.ItemMeta;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class BedrockFormDrawer {

    private static final SimpleClans plugin = SimpleClans.getInstance();

    private BedrockFormDrawer() {
    }

    public static boolean isBedrockPlayer(@NotNull Player player) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (NoClassDefFoundError | IllegalStateException ex) {
            return false;
        }
    }

    public static void open(@NotNull SCFrame frame) {
        if (frame instanceof MainFrame) {
            Bukkit.getScheduler().runTask(plugin, () -> BedrockClanHomeDrawer.open((MainFrame) frame));
            return;
        }
        if (frame instanceof RosterFrame) {
            Bukkit.getScheduler().runTask(plugin, () -> BedrockRosterDrawer.open((RosterFrame) frame));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!InventoryDrawer.prepareComponents(frame)) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!frame.getViewer().isOnline()) {
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
        List<SCComponent> components = getSortedComponents(frame);
        List<BedrockAction> actions = new ArrayList<>();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(BedrockText.clean(frame.getTitle()))
                .content(BedrockText.lang("bedrock.gui.content", viewer));

        for (SCComponent component : components) {
            List<ClickType> clicks = getSortedClicks(component.getAvailableClicks());
            if (clicks.isEmpty()) {
                continue;
            }
            String buttonText = getButtonText(component);
            FormImage image = BedrockFormIconResolver.getIcon(component.getItem());
            builder.button(buttonText, image);
            actions.add(new BedrockAction(component.getSlot(), clicks.get(0)));
        }

        builder.closedOrInvalidResultHandler(() -> {
            if (frame.getParent() != null) {
                Bukkit.getScheduler().runTask(plugin, () -> InventoryDrawer.open(frame.getParent()));
            }
        });
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
                Bukkit.getScheduler().runTask(plugin, () -> sendActionPicker(frame, component));
                return;
            }
            runFreshAction(frame, action);
        });

        sendForm(viewer, builder);
    }

    private static void sendActionPicker(@NotNull SCFrame frame, @NotNull SCComponent component) {
        Player viewer = frame.getViewer();
        List<ClickType> clicks = getSortedClicks(component.getAvailableClicks());
        List<BedrockAction> actions = new ArrayList<>();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(getButtonText(component))
                .content(BedrockText.lang("bedrock.gui.choose-action", viewer));

        for (ClickType click : clicks) {
            builder.button(getClickName(click, viewer));
            actions.add(new BedrockAction(component.getSlot(), click));
        }

        builder.button(BedrockText.lang("bedrock.gui.back", viewer));
        builder.closedOrInvalidResultHandler(() -> Bukkit.getScheduler().runTask(plugin, () -> InventoryDrawer.open(frame)));
        builder.validResultHandler(response -> {
            int buttonId = response.clickedButtonId();
            if (buttonId == actions.size()) {
                Bukkit.getScheduler().runTask(plugin, () -> InventoryDrawer.open(frame));
                return;
            }
            if (buttonId < 0 || buttonId >= actions.size()) {
                return;
            }
            runFreshAction(frame, actions.get(buttonId));
        });

        sendForm(viewer, builder);
    }

    private static void sendConfirmationForm(@NotNull SCFrame frame) {
        Player viewer = frame.getViewer();
        List<SCComponent> components = getSortedComponents(frame);
        if (components.size() < 2) {
            sendSimpleFrame(frame);
            return;
        }

        SCComponent confirm = components.get(0);
        SCComponent cancel = components.get(1);
        ModalForm.Builder builder = ModalForm.builder()
                .title(BedrockText.clean(frame.getTitle()))
                .content(BedrockText.lang("bedrock.gui.confirm-content", viewer))
                .button1(getButtonText(confirm))
                .button2(getButtonText(cancel));

        builder.closedOrInvalidResultHandler(() -> Bukkit.getScheduler().runTask(plugin, () -> InventoryDrawer.open(frame.getParent())));
        builder.validResultHandler(response -> {
            SCComponent selected = response.clickedFirst() ? confirm : cancel;
            runFreshAction(frame, new BedrockAction(selected.getSlot(), ClickType.LEFT));
        });

        sendForm(viewer, builder);
    }

    private static void runFreshAction(@NotNull SCFrame frame, @NotNull BedrockAction action) {
        Bukkit.getScheduler().runTask(plugin, () -> {
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
        viewer.sendMessage(SimpleClans.lang("bedrock.gui.stale", viewer));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (frame.getParent() != null) {
                InventoryDrawer.open(frame.getParent());
            } else {
                InventoryDrawer.open(frame);
            }
        });
    }

    private static void sendForm(@NotNull Player player, @NotNull Object builder) {
        if (builder instanceof SimpleForm.Builder) {
            FloodgateApi.getInstance().sendForm(player.getUniqueId(), (SimpleForm.Builder) builder);
            return;
        }
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), (ModalForm.Builder) builder);
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

    @NotNull
    private static String getButtonText(@NotNull SCComponent component) {
        ItemMeta meta = component.getItemMeta();
        StringBuilder text = new StringBuilder();
        if (meta != null && meta.hasDisplayName()) {
            text.append(BedrockText.clean(meta.getDisplayName()));
        } else {
            text.append(BedrockText.clean(component.getItem().getType().name()));
        }
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore() == null ? Collections.emptyList() : meta.getLore();
            for (String line : lore) {
                text.append("\n").append(BedrockText.clean(line));
            }
        }
        return text.toString();
    }

    @NotNull
    private static String getClickName(@NotNull ClickType clickType, @NotNull Player player) {
        switch (clickType) {
            case LEFT:
                return BedrockText.lang("bedrock.gui.action.left", player);
            case RIGHT:
                return BedrockText.lang("bedrock.gui.action.right", player);
            case SHIFT_LEFT:
                return BedrockText.lang("bedrock.gui.action.shift-left", player);
            case SHIFT_RIGHT:
                return BedrockText.lang("bedrock.gui.action.shift-right", player);
            case DROP:
                return BedrockText.lang("bedrock.gui.action.drop", player);
            default:
                return BedrockText.clean(clickType.name());
        }
    }
}
