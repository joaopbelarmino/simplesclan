package net.sacredlabyrinth.phaed.simpleclans.ui.frames;

import com.cryptomorin.xseries.XMaterial;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponent;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponentImpl;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;

public class RelationsFrame extends SCFrame {

    private final Clan clan;

    public RelationsFrame(@NotNull Player viewer, @NotNull SCFrame parent, @NotNull Clan clan) {
        super(parent, viewer);
        this.clan = clan;
    }

    @Override
    public @NotNull String getTitle() {
        return lang("gui.relations.title", getViewer());
    }

    @Override
    public int getSize() {
        return 3 * 9;
    }

    @Override
    public void createComponents() {
        add(Components.getBackComponent(getParent(), 18, getViewer()));
        addAllies();
        addEnemiesInfo();
    }

    private void addAllies() {
        SCComponent allies = new SCComponentImpl(lang("gui.relations.allies.title", getViewer()),
                Collections.singletonList(lang("gui.relations.allies.lore", getViewer())),
                XMaterial.CYAN_BANNER, 11);
        allies.setListener(ClickType.LEFT, () -> InventoryDrawer.open(new AlliesFrame(getViewer(), this, clan)));
        allies.setPermission(ClickType.LEFT, "simpleclans.anyone.alliances");
        add(allies);
    }

    private void addEnemiesInfo() {
        SCComponent enemies = new SCComponentImpl(lang("gui.relations.enemies.title", getViewer()),
                Collections.singletonList(lang("gui.relations.enemies.lore", getViewer())),
                XMaterial.RED_BANNER, 15);
        add(enemies);
    }
}
