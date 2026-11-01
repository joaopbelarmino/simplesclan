package net.sacredlabyrinth.phaed.simpleclans.ui.bedrock;

import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.RankPermission;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.language.LanguageResource;
import net.sacredlabyrinth.phaed.simpleclans.managers.ClanRankingManager;
import net.sacredlabyrinth.phaed.simpleclans.ranking.ClanScore;
import net.sacredlabyrinth.phaed.simpleclans.utils.CurrencyFormat;
import net.sacredlabyrinth.phaed.simpleclans.utils.KDRFormat;
import net.sacredlabyrinth.phaed.simpleclans.utils.VanishUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.ECONOMY_INVITE_PRICE;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.ECONOMY_PURCHASE_CLAN_INVITE;

/**
 * Bedrock screens that replace Java inventories which do not convert well: ranking, applying to a clan,
 * relations, language and invites
 */
public final class BedrockClanScreens {

    static final int PAGE_SIZE = 30;
    private static final int RANKING_SIZE = 50;
    private static final SimpleClans plugin = SimpleClans.getInstance();

    private BedrockClanScreens() {
    }

    public static void openRanking(@NotNull Player player, @NotNull Runnable back) {
        ClanRankingManager rankingManager = plugin.getClanRankingManager();
        List<ClanScore> ranking = rankingManager.getRanking();
        StringBuilder content = new StringBuilder();
        ClanPlayer cp = plugin.getClanManager().getClanPlayer(player);
        Clan own = cp == null ? null : cp.getClan();
        if (own != null) {
            ClanScore score = rankingManager.getScore(own);
            content.append(score == null ? BedrockText.lang("clan.ranking.not.eligible", player)
                    : BedrockText.lang("clan.ranking.own", player, score.getPosition(), score.getTotal()))
                    .append("\n\n");
        }
        if (ranking.isEmpty()) {
            content.append(BedrockText.lang(rankingManager.getUpdatedAt() == 0
                    ? "clan.ranking.not.ready" : "clan.ranking.empty", player));
        } else {
            content.append(BedrockText.lang("bedrock.ranking.content", player));
        }
        BedrockForms.Menu menu = new BedrockForms.Menu(player, BedrockText.lang("bedrock.ranking.title", player),
                content.toString());
        Runnable reopen = () -> openRanking(player, back);
        for (ClanScore score : ranking.subList(0, Math.min(RANKING_SIZE, ranking.size()))) {
            Clan clan = rankingManager.getClan(score);
            if (clan == null) {
                continue;
            }
            menu.button(BedrockText.button("bedrock.ranking.button", player, score.getPosition(),
                            BedrockText.buttonColor(clan.getColorTag()), clan.getName(), score.getTotal()),
                    score.getPosition() <= 3 ? "ranking-top" : "ranking-clan",
                    () -> openClanDetails(player, clan, reopen, false));
        }
        menu.back(back);
        menu.send();
    }

    /**
     * Clans that accept join requests, with their ranking, then their details and the request button
     */
    public static void openApplyList(@NotNull Player player, @NotNull Runnable back) {
        ClanRankingManager rankingManager = plugin.getClanRankingManager();
        List<Clan> clans = plugin.getClanManager().getClans().stream()
                .filter(Clan::isJoinRequestsEnabled)
                .sorted(Comparator.comparingInt((Clan clan) -> {
                    ClanScore score = rankingManager.getScore(clan);
                    return score == null ? Integer.MAX_VALUE : score.getPosition();
                }).thenComparing(Clan::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        if (clans.isEmpty()) {
            player.sendMessage(BedrockText.lang("bedrock.apply.none-available", player));
            back.run();
            return;
        }
        BedrockForms.Menu menu = new BedrockForms.Menu(player, BedrockText.lang("bedrock.apply.title", player),
                BedrockText.lang("bedrock.apply.content", player));
        Runnable reopen = () -> openApplyList(player, back);
        for (Clan clan : clans) {
            ClanScore score = rankingManager.getScore(clan);
            String points = score == null ? "-" : String.valueOf(score.getTotal());
            String verified = BedrockText.lang(clan.isVerified() ? "bedrock.apply.verified" : "bedrock.apply.unverified", player);
            menu.button(BedrockText.button("bedrock.apply.button", player, BedrockText.buttonColor(clan.getColorTag()),
                            clan.getName(), clan.getSize(), points, verified),
                    "apply", () -> openClanDetails(player, clan, reopen, true));
        }
        menu.back(back);
        menu.send();
    }

    private static void openClanDetails(@NotNull Player player, @NotNull Clan clan, @NotNull Runnable back,
                                        boolean applying) {
        ClanScore score = plugin.getClanRankingManager().getScore(clan);
        String ranking = score == null ? BedrockText.lang("bedrock.home.clan.unranked", player)
                : BedrockText.lang("bedrock.clan-details.ranking", player, score.getPosition(), score.getTotal(),
                score.getEconomy(), score.getCombat(), score.getWars(), score.getActivity(), score.getAge());
        String description = clan.getDescription() == null || clan.getDescription().isEmpty()
                ? BedrockText.lang("no.description", player) : BedrockText.color(clan.getDescription());
        boolean leaderOnline = plugin.getRequestManager().hasLeaderOnline(clan);
        StringBuilder content = new StringBuilder(BedrockText.lang("bedrock.clan-details.content", player,
                clan.getColorTag(), description,
                BedrockText.lang(clan.isVerified() ? "bedrock.overview.verified.yes" : "bedrock.overview.verified.no", player),
                VanishUtils.getNonVanished(player, clan).size(), clan.getSize(),
                KDRFormat.format(clan.getTotalKDR()), BedrockText.color(clan.getLeadersString("", ", ")), ranking));
        if (applying && !leaderOnline) {
            content.append("\n\n").append(BedrockText.lang("bedrock.apply.no-leader", player));
        }

        BedrockForms.Menu menu = new BedrockForms.Menu(player, BedrockText.color(clan.getName()), content.toString());
        if (applying && leaderOnline && clan.isJoinRequestsEnabled()
                && plugin.getPermissionsManager().has(player, "simpleclans.anyone.apply")) {
            menu.button(BedrockText.button("bedrock.apply.send", player), "accept",
                    () -> BedrockForms.runClanCommand(player, "apply " + clan.getTag()));
        }
        menu.back(back);
        menu.send();
    }

    public static void openRelations(@NotNull Player player, @NotNull Clan clan, @NotNull Runnable back) {
        Runnable reopen = () -> openRelations(player, clan, back);
        String none = BedrockText.lang("bedrock.relations.none", player);
        String allies = names(clan.getAllies(), none);
        String rivals = names(clan.getRivals(), none);
        String wars = clan.getWarringClans().isEmpty() ? none : clan.getWarringClans().stream()
                .map(c -> BedrockText.color(c.getColorTag())).collect(Collectors.joining("§r, "));
        BedrockForms.Menu menu = new BedrockForms.Menu(player,
                BedrockText.lang("bedrock.relations.title", player, clan.getName()),
                BedrockText.lang("bedrock.relations.content", player, allies, rivals, wars));

        ClanPlayer cp = plugin.getClanManager().getClanPlayer(player);
        boolean ownClan = cp != null && clan.equals(cp.getClan());
        if (ownClan && clan.isVerified()) {
            if (plugin.getPermissionsManager().has(player, RankPermission.ALLY_ADD, false)) {
                menu.button(BedrockText.button("bedrock.relations.ally-add", player), "ally-add",
                        () -> pickClan(player, BedrockText.lang("bedrock.relations.ally-add.title", player), clan,
                                other -> !clan.isAlly(other.getTag()) && other.isVerified(),
                                other -> BedrockForms.runClanCommand(player, "ally add " + other.getTag(), reopen), reopen));
            }
            if (!clan.getAllies().isEmpty() && plugin.getPermissionsManager().has(player, RankPermission.ALLY_REMOVE, false)) {
                menu.button(BedrockText.button("bedrock.relations.ally-remove", player), "ally-remove",
                        () -> pickClan(player, BedrockText.lang("bedrock.relations.ally-remove.title", player), clan,
                                other -> clan.isAlly(other.getTag()),
                                other -> BedrockForms.runClanCommand(player, "ally remove " + other.getTag(), reopen), reopen));
            }
            if (plugin.getPermissionsManager().has(player, RankPermission.RIVAL_ADD, false)) {
                menu.button(BedrockText.button("bedrock.relations.rival-add", player), "rival-add",
                        () -> pickClan(player, BedrockText.lang("bedrock.relations.rival-add.title", player), clan,
                                other -> !clan.isRival(other.getTag()) && !clan.isAlly(other.getTag()) && other.isVerified(),
                                other -> BedrockForms.runClanCommand(player, "rival add " + other.getTag(), reopen), reopen));
            }
            if (!clan.getRivals().isEmpty() && plugin.getPermissionsManager().has(player, RankPermission.RIVAL_REMOVE, false)) {
                menu.button(BedrockText.button("bedrock.relations.rival-remove", player), "rival-remove",
                        () -> pickClan(player, BedrockText.lang("bedrock.relations.rival-remove.title", player), clan,
                                other -> clan.isRival(other.getTag()),
                                other -> BedrockForms.runClanCommand(player, "rival remove " + other.getTag(), reopen), reopen));
            }
        }
        menu.back(back);
        menu.send();
    }

    private static void pickClan(@NotNull Player player, @NotNull String title, @NotNull Clan own,
                                 @NotNull Predicate<Clan> filter, @NotNull Consumer<Clan> action,
                                 @NotNull Runnable back) {
        List<Clan> clans = plugin.getClanManager().getClans().stream()
                .filter(other -> !other.equals(own))
                .filter(filter)
                .sorted(Comparator.comparing(Clan::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        BedrockForms.Menu menu = new BedrockForms.Menu(player, title, BedrockText.lang(clans.isEmpty()
                ? "bedrock.relations.pick.empty" : "bedrock.relations.pick.content", player));
        for (Clan clan : clans) {
            menu.button(BedrockText.buttonColor(clan.getColorTag() + " §r" + clan.getName()), "ranking-clan",
                    () -> action.accept(clan));
        }
        menu.back(back);
        menu.send();
    }

    @NotNull
    private static String names(@NotNull List<String> tags, @NotNull String none) {
        if (tags.isEmpty()) {
            return none;
        }
        List<String> names = new ArrayList<>();
        for (String tag : tags) {
            Clan other = plugin.getClanManager().getClan(tag);
            names.add(other == null ? tag : BedrockText.color(other.getColorTag()));
        }
        return String.join("§r, ", names);
    }

    public static void openLanguage(@NotNull Player player, @NotNull Runnable back) {
        ClanPlayer cp = plugin.getClanManager().getCreateClanPlayer(player.getUniqueId());
        Locale current = cp.getLocale() == null ? plugin.getSettingsManager().getLanguage() : cp.getLocale();
        BedrockForms.Menu menu = new BedrockForms.Menu(player, BedrockText.lang("bedrock.language.title", player),
                BedrockText.lang("bedrock.language.content", player, current.toLanguageTag().replace("-", "_")));
        for (Locale locale : LanguageResource.getAvailableLocales()) {
            String tag = locale.toLanguageTag().replace("-", "_");
            String key = locale.equals(current) ? "bedrock.language.button.current" : "bedrock.language.button";
            menu.button(BedrockText.button(key, player, tag, LanguageResource.getTranslationStatus(locale)), "language",
                    () -> {
                        cp.setLocale(locale);
                        plugin.getStorageManager().updateClanPlayer(cp);
                        openLanguage(player, back);
                    });
        }
        menu.back(back);
        menu.send();
    }

    public static void openInvite(@NotNull Player player, @NotNull Runnable back) {
        openInvite(player, back, 0);
    }

    private static void openInvite(@NotNull Player player, @NotNull Runnable back, int page) {
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> plugin.getClanManager().getClanPlayer(p) == null)
                .filter(p -> !VanishUtils.isVanished(player, p))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        int maxPage = Math.max(0, (players.size() - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, maxPage));
        String price = plugin.getSettingsManager().is(ECONOMY_PURCHASE_CLAN_INVITE)
                ? CurrencyFormat.format(plugin.getSettingsManager().getDouble(ECONOMY_INVITE_PRICE))
                : BedrockText.lang("bedrock.home.free", player);
        BedrockForms.Menu menu = new BedrockForms.Menu(player, BedrockText.lang("bedrock.invite.title", player),
                BedrockText.lang(players.isEmpty() ? "bedrock.invite.empty" : "bedrock.invite.content", player,
                        price, current + 1, maxPage + 1));
        Runnable reopen = () -> openInvite(player, back, current);
        for (Player target : players.subList(current * PAGE_SIZE, Math.min(players.size(), (current + 1) * PAGE_SIZE))) {
            menu.button(BedrockText.buttonColor(target.getName()),
                    BedrockFormIconResolver.playerHead(target.getUniqueId(), target.getName()),
                    () -> BedrockForms.runClanCommand(player, "invite " + target.getName(), reopen));
        }
        addPages(menu, player, current, maxPage, p -> openInvite(player, back, p));
        menu.back(back);
        menu.send();
    }

    static void addPages(@NotNull BedrockForms.Menu menu, @NotNull Player player, int current, int maxPage,
                         @NotNull IntConsumer open) {
        if (current > 0) {
            menu.button(BedrockText.button("bedrock.gui.previous-page", player), "previous", () -> open.accept(current - 1));
        }
        if (current < maxPage) {
            menu.button(BedrockText.button("bedrock.gui.next-page", player), "next", () -> open.accept(current + 1));
        }
    }
}
