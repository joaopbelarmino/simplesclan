package net.sacredlabyrinth.phaed.simpleclans.commands.data;

import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.Helper;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.managers.ClanRankingManager;
import net.sacredlabyrinth.phaed.simpleclans.ranking.ClanScore;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.PAGE_SEPARATOR;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.SERVER_NAME;
import static org.bukkit.ChatColor.*;

/**
 * The clan ranking in the chat, with the score parts of the sender's clan
 */
public class ClanRanking extends Sendable {

    public ClanRanking(@NotNull SimpleClans plugin, @NotNull CommandSender sender) {
        super(plugin, sender);
    }

    @Override
    public void send() {
        ClanRankingManager rankingManager = plugin.getClanRankingManager();
        List<ClanScore> ranking = rankingManager.getRanking();
        if (ranking.isEmpty()) {
            ChatBlock.sendMessage(sender, RED + lang(rankingManager.getUpdatedAt() == 0
                    ? "clan.ranking.not.ready" : "clan.ranking.empty", sender));
            return;
        }

        ChatBlock.sendBlank(sender);
        ChatBlock.saySingle(sender, sm.getColored(SERVER_NAME) + subColor + " " + lang("clan.ranking.title", sender)
                + " " + headColor + Helper.generatePageSeparator(sm.getString(PAGE_SEPARATOR)));
        ChatBlock.sendBlank(sender);
        sendOwnScore();

        chatBlock.setAlignment("c", "l", "c");
        chatBlock.addRow("  " + headColor + "#", lang("clan", sender), lang("clan.ranking.points", sender));
        for (ClanScore score : ranking) {
            Clan clan = rankingManager.getClan(score);
            if (clan == null) {
                continue;
            }
            chatBlock.addRow("  " + WHITE + score.getPosition(), clan.getColorTag() + " " + GRAY + clan.getName(),
                    YELLOW + String.valueOf(score.getTotal()));
        }
        sendBlock();
    }

    private void sendOwnScore() {
        if (!(sender instanceof Player)) {
            return;
        }
        ClanPlayer cp = cm.getClanPlayer((Player) sender);
        Clan clan = cp == null ? null : cp.getClan();
        if (clan == null) {
            return;
        }
        ClanScore score = plugin.getClanRankingManager().getScore(clan);
        if (score == null) {
            ChatBlock.sendMessage(sender, GRAY + lang("clan.ranking.not.eligible", sender));
        } else {
            ChatBlock.sendMessage(sender, AQUA + lang("clan.ranking.own", sender, score.getPosition(), score.getTotal()));
            ChatBlock.sendMessage(sender, GRAY + breakdown(sender, score));
        }
        ChatBlock.sendBlank(sender);
    }

    @NotNull
    public static String breakdown(@Nullable CommandSender sender, @NotNull ClanScore score) {
        return lang("clan.ranking.breakdown", sender, score.getEconomy(), score.getCombat(), score.getWars(),
                score.getActivity(), score.getAge());
    }
}
