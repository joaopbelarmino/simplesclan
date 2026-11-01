package net.sacredlabyrinth.phaed.simpleclans.ui.frames;

import com.cryptomorin.xseries.XMaterial;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.managers.ClanRankingManager;
import net.sacredlabyrinth.phaed.simpleclans.ranking.ClanScore;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponent;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponentImpl;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCFrame;
import net.sacredlabyrinth.phaed.simpleclans.utils.Paginator;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;

public class ClanRankingFrame extends SCFrame {

    private final ClanRankingManager rankingManager;
    private final List<ClanScore> ranking;
    private final Paginator paginator;

    public ClanRankingFrame(Player viewer, SCFrame parent) {
        super(parent, viewer);
        rankingManager = SimpleClans.getInstance().getClanRankingManager();
        ranking = rankingManager.getRanking();
        paginator = new Paginator(getSize() - 9, ranking);
    }

    @Override
    public void createComponents() {
        for (int slot = 0; slot < 9; slot++) {
            if (slot == 2 || slot == 6 || slot == 7)
                continue;
            add(Components.getPanelComponent(slot));
        }
        add(Components.getBackComponent(getParent(), 2, getViewer()));

        add(Components.getPreviousPageComponent(6, this::previousPage, paginator, getViewer()));
        add(Components.getNextPageComponent(7, this::nextPage, paginator, getViewer()));

        int slot = 9;
        for (int i = paginator.getMinIndex(); paginator.isValidIndex(i); i++) {
            ClanScore score = ranking.get(i);
            Clan clan = rankingManager.getClan(score);
            if (clan == null) {
                continue;
            }
            SCComponent c = new SCComponentImpl(
                    lang("gui.clanranking.clan.title", getViewer(), score.getPosition(), clan.getColorTag(),
                            clan.getName()),
                    Arrays.asList(
                            lang("gui.clanranking.clan.lore.points", getViewer(), score.getTotal()),
                            lang("gui.clanranking.clan.lore.economy", getViewer(), score.getEconomy()),
                            lang("gui.clanranking.clan.lore.combat", getViewer(), score.getCombat()),
                            lang("gui.clanranking.clan.lore.wars", getViewer(), score.getWars()),
                            lang("gui.clanranking.clan.lore.activity", getViewer(), score.getActivity()),
                            lang("gui.clanranking.clan.lore.age", getViewer(), score.getAge())),
                    getMaterial(score.getPosition()), slot);
            c.setListener(ClickType.LEFT, () -> InventoryDrawer.open(new ClanDetailsFrame(this, getViewer(), clan)));
            add(c);
            slot++;
        }
    }

    @NotNull
    private static XMaterial getMaterial(int position) {
        switch (position) {
            case 1:
                return XMaterial.GOLD_BLOCK;
            case 2:
                return XMaterial.IRON_BLOCK;
            case 3:
                return XMaterial.ORANGE_TERRACOTTA;
            default:
                return XMaterial.WHITE_BANNER;
        }
    }

    private void previousPage() {
        if (paginator.previousPage()) {
            InventoryDrawer.open(this);
        }
    }

    private void nextPage() {
        if (paginator.nextPage()) {
            InventoryDrawer.open(this);
        }
    }

    @Override
    public @NotNull String getTitle() {
        return lang("gui.clanranking.title", getViewer(), ranking.size());
    }

    @Override
    public int getSize() {
        return 6 * 9;
    }
}
