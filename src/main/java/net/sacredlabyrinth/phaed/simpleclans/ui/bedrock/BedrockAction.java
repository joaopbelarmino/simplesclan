package net.sacredlabyrinth.phaed.simpleclans.ui.bedrock;

import org.bukkit.event.inventory.ClickType;

final class BedrockAction {

    private final int slot;
    private final ClickType clickType;

    BedrockAction(int slot, ClickType clickType) {
        this.slot = slot;
        this.clickType = clickType;
    }

    int getSlot() {
        return slot;
    }

    ClickType getClickType() {
        return clickType;
    }
}
