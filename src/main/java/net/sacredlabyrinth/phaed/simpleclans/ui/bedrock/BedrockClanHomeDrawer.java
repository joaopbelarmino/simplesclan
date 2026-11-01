package net.sacredlabyrinth.phaed.simpleclans.ui.bedrock;

import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer.Channel;
import net.sacredlabyrinth.phaed.simpleclans.RankPermission;
import net.sacredlabyrinth.phaed.simpleclans.Request;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.events.PlayerResetKdrEvent;
import net.sacredlabyrinth.phaed.simpleclans.managers.PermissionsManager;
import net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager;
import net.sacredlabyrinth.phaed.simpleclans.ranking.ClanScore;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.MainFrame;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.RosterFrame;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.staff.StaffFrame;
import net.sacredlabyrinth.phaed.simpleclans.utils.ChatUtils;
import net.sacredlabyrinth.phaed.simpleclans.utils.CurrencyFormat;
import net.sacredlabyrinth.phaed.simpleclans.utils.KDRFormat;
import net.sacredlabyrinth.phaed.simpleclans.utils.VanishUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.*;

/**
 * The main Bedrock menu: Home, My clan, Manage and the screens under them
 */
public final class BedrockClanHomeDrawer {

    private static final SimpleClans plugin = SimpleClans.getInstance();

    private BedrockClanHomeDrawer() {
    }

    public static void open(@NotNull MainFrame frame) {
        Player player = frame.getViewer();
        ClanPlayer clanPlayer = plugin.getClanManager().getCreateClanPlayer(player.getUniqueId());
        if (clanPlayer.getClan() == null) {
            openCivilianMenu(player, frame);
        } else {
            openClanMenu(player, frame, clanPlayer);
        }
    }

    private static void openCivilianMenu(@NotNull Player player, @NotNull MainFrame frame) {
        PermissionsManager permissions = plugin.getPermissionsManager();
        Request invite = plugin.getRequestManager().getInviteRequestFor(player);
        BedrockForms.Menu menu = new BedrockForms.Menu(player,
                BedrockText.lang("bedrock.home.no-clan.title", player),
                BedrockText.lang("bedrock.home.no-clan.content", player));

        if (invite != null) {
            menu.button(BedrockText.button("bedrock.home.accept-invite", player, invite.getClan().getName()),
                    "accept", () -> {
                        // accept is a root command, not a clan subcommand
                        player.performCommand(plugin.getSettingsManager().getString(COMMANDS_ACCEPT));
                        Bukkit.getScheduler().runTask(plugin, () -> open(frame));
                    });
        }
        if (permissions.has(player, "simpleclans.leader.create")) {
            menu.button(BedrockText.button("bedrock.home.create-clan", player, getCreationPrice(player)),
                    "create", () -> openCreateClanForm(player, frame, "", "", null));
        }
        if (permissions.has(player, "simpleclans.anyone.apply")) {
            menu.button(BedrockText.button("bedrock.home.apply-clan", player), "apply",
                    () -> BedrockClanScreens.openApplyList(player, () -> open(frame)));
        }
        addCommonButtons(menu, player, frame);
        menu.send();
    }

    private static void openClanMenu(@NotNull Player player, @NotNull MainFrame frame, @NotNull ClanPlayer clanPlayer) {
        PermissionsManager permissions = plugin.getPermissionsManager();
        Clan clan = clanPlayer.getClan();
        if (clan == null) {
            open(frame);
            return;
        }
        ClanScore score = plugin.getClanRankingManager().getScore(clan);
        String ranking = score == null ? BedrockText.lang("bedrock.home.clan.unranked", player)
                : BedrockText.lang("bedrock.home.clan.ranked", player, score.getPosition(), score.getTotal());
        BedrockForms.Menu menu = new BedrockForms.Menu(player,
                BedrockText.lang("bedrock.home.clan.title", player, clan.getName()),
                BedrockText.lang("bedrock.home.clan.content", player, clan.getColorTag(),
                        VanishUtils.getNonVanished(player, clan).size(), clan.getSize(),
                        KDRFormat.format(clan.getTotalKDR()), ranking));

        if (plugin.getDynamicWarManager().hasActiveWar(clan) && permissions.has(player, "simpleclans.member.dynamicwar")) {
            menu.button(BedrockText.button("bedrock.home.war-join", player), "war-join",
                    () -> plugin.getDynamicWarManager().joinNearestWar(player));
        }
        menu.button(BedrockText.button("bedrock.home.my-clan", player), "my-clan",
                () -> openMyClanMenu(player, frame));
        if (permissions.has(player, "simpleclans.member.roster")) {
            menu.button(BedrockText.button("bedrock.home.members", player), "members",
                    () -> InventoryDrawer.open(new RosterFrame(player, frame, clan)));
        }
        if (clanPlayer.isLeader()) {
            List<Request> joinRequests = plugin.getRequestManager().getJoinRequestsFor(clan);
            if (!joinRequests.isEmpty()) {
                menu.button(BedrockText.button("bedrock.home.join-requests", player, joinRequests.size()),
                        "join-requests", () -> openJoinRequests(player, frame));
            }
            menu.button(BedrockText.button("bedrock.home.manage", player), "manage",
                    () -> openManageMenu(player, frame));
        }
        addCommonButtons(menu, player, frame);
        menu.send();
    }

    /**
     * Ranking, staff and settings, shown with or without a clan
     */
    private static void addCommonButtons(@NotNull BedrockForms.Menu menu, @NotNull Player player,
                                         @NotNull MainFrame frame) {
        PermissionsManager permissions = plugin.getPermissionsManager();
        if (permissions.has(player, "simpleclans.anyone.top")) {
            menu.button(BedrockText.button("bedrock.home.ranking", player), "ranking",
                    () -> BedrockClanScreens.openRanking(player, () -> open(frame)));
        }
        if (permissions.has(player, "simpleclans.mod.staffgui")) {
            menu.button(BedrockText.button("bedrock.home.staff", player), "staff",
                    () -> InventoryDrawer.open(new StaffFrame(frame, player)));
        }
        menu.button(BedrockText.button("bedrock.home.settings", player), "settings",
                () -> openSettingsMenu(player, frame));
    }

    private static void openMyClanMenu(@NotNull Player player, @NotNull MainFrame frame) {
        PermissionsManager permissions = plugin.getPermissionsManager();
        ClanPlayer clanPlayer = plugin.getClanManager().getClanPlayer(player);
        Clan clan = clanPlayer == null ? null : clanPlayer.getClan();
        if (clan == null) {
            open(frame);
            return;
        }
        String description = clan.getDescription() == null || clan.getDescription().isEmpty()
                ? BedrockText.lang("no.description", player)
                : BedrockText.color(clan.getDescription());
        String verified = BedrockText.lang(clan.isVerified()
                ? "bedrock.overview.verified.yes" : "bedrock.overview.verified.no", player);
        BedrockForms.Menu menu = new BedrockForms.Menu(player,
                BedrockText.lang("bedrock.my-clan.title", player, clan.getName()),
                BedrockText.lang("bedrock.my-clan.content", player,
                        clan.getColorTag(),
                        description,
                        verified,
                        VanishUtils.getNonVanished(player, clan).size(),
                        clan.getSize(),
                        KDRFormat.format(clan.getTotalKDR()),
                        clan.getTotalKills(),
                        clan.getTotalDeaths(),
                        clan.getBalanceFormatted(),
                        BedrockText.color(clan.getLeadersString("", ", "))));

        if (permissions.has(player, RankPermission.BANK_BALANCE, false)) {
            menu.button(BedrockText.button("bedrock.my-clan.bank", player), "bank", () -> openBankMenu(player, frame));
        }
        if (permissions.has(player, "simpleclans.anyone.alliances")) {
            menu.button(BedrockText.button("bedrock.my-clan.relations", player), "relations",
                    () -> BedrockClanScreens.openRelations(player, clan, () -> openMyClanMenu(player, frame)));
        }
        menu.button(BedrockText.button("bedrock.my-clan.home", player), "home",
                () -> openHomeActions(player, frame));
        if (permissions.has(player, "simpleclans.member.ff")
                || permissions.has(player, RankPermission.FRIENDLYFIRE, false)) {
            menu.button(BedrockText.button("bedrock.my-clan.ff", player), "ff",
                    () -> openFriendlyFireMenu(player, frame));
        }
        if (permissions.has(player, "simpleclans.member.chat")) {
            boolean inClanChat = clanPlayer.getChannel() == Channel.CLAN;
            menu.button(toggleText(player, "bedrock.my-clan.chat", inClanChat), "chat", () -> {
                clanPlayer.setChannel(inClanChat ? Channel.NONE : Channel.CLAN);
                openMyClanMenu(player, frame);
            });
        }
        if (permissions.has(player, "simpleclans.member.resign")) {
            menu.button(BedrockText.button("bedrock.my-clan.resign", player), "resign",
                    () -> BedrockForms.confirm(player,
                            BedrockText.lang("bedrock.resign.title", player),
                            BedrockText.lang("bedrock.resign.content", player, clan.getName()),
                            () -> resign(player, frame, clan),
                            () -> openMyClanMenu(player, frame)));
        }
        menu.back(() -> open(frame));
        menu.send();
    }

    private static void openManageMenu(@NotNull Player player, @NotNull MainFrame frame) {
        PermissionsManager permissions = plugin.getPermissionsManager();
        ClanPlayer clanPlayer = plugin.getClanManager().getClanPlayer(player);
        Clan clan = clanPlayer == null ? null : clanPlayer.getClan();
        if (clan == null || !clanPlayer.isLeader()) {
            open(frame);
            return;
        }
        Runnable reopen = () -> openManageMenu(player, frame);
        BedrockForms.Menu menu = new BedrockForms.Menu(player,
                BedrockText.lang("bedrock.manage.title", player, clan.getName()),
                BedrockText.lang("bedrock.manage.content", player));

        if (permissions.has(player, "simpleclans.leader.joinrequests-toggle")) {
            menu.button(toggleText(player, "bedrock.manage.join-toggle", clan.isJoinRequestsEnabled()), "toggle",
                    () -> BedrockForms.runClanCommand(player, "toggle joinrequests", reopen));
        }
        if (permissions.has(player, "simpleclans.leader.dynamicwar-toggle")) {
            menu.button(toggleText(player, "bedrock.manage.war-toggle", clan.isDynamicWarEnabled()), "war-toggle",
                    () -> BedrockForms.runClanCommand(player, "toggle warstart", reopen));
        }
        if (!clan.isVerified() && permissions.has(player, "simpleclans.leader.verify")) {
            menu.button(BedrockText.button("bedrock.manage.verify", player), "verify",
                    () -> BedrockForms.runClanCommand(player, "verify", reopen));
        }
        if (permissions.has(player, "simpleclans.leader.disband")) {
            menu.button(BedrockText.button("bedrock.manage.disband", player), "disband",
                    () -> BedrockForms.confirm(player,
                            BedrockText.lang("bedrock.disband.title", player),
                            BedrockText.lang("bedrock.disband.content", player, clan.getName()),
                            () -> disbandClan(player, frame, clan),
                            reopen));
        }
        menu.back(() -> open(frame));
        menu.send();
    }

    private static void openBankMenu(@NotNull Player player, @NotNull MainFrame frame) {
        PermissionsManager permissions = plugin.getPermissionsManager();
        ClanPlayer clanPlayer = plugin.getClanManager().getClanPlayer(player);
        Clan clan = clanPlayer == null ? null : clanPlayer.getClan();
        if (clan == null) {
            open(frame);
            return;
        }
        Runnable reopen = () -> openBankMenu(player, frame);
        String ownBalance = permissions.hasEconomy()
                ? CurrencyFormat.format(permissions.playerGetMoney(player)) : "-";
        BedrockForms.Menu menu = new BedrockForms.Menu(player,
                BedrockText.lang("bedrock.bank.title", player),
                BedrockText.lang("bedrock.bank.content", player, clan.getBalanceFormatted(), ownBalance,
                        status(player, clan.isAllowDeposit()), status(player, clan.isAllowWithdraw())));

        if (permissions.has(player, RankPermission.BANK_DEPOSIT, false)) {
            menu.button(BedrockText.button("bedrock.bank.deposit", player), "deposit",
                    () -> openAmountMenu(player, frame, true));
        }
        if (permissions.has(player, RankPermission.BANK_WITHDRAW, false)) {
            menu.button(BedrockText.button("bedrock.bank.withdraw", player), "withdraw",
                    () -> openAmountMenu(player, frame, false));
        }
        if (permissions.has(player, "simpleclans.leader.deposit-toggle")) {
            menu.button(toggleText(player, "bedrock.bank.toggle-deposit", clan.isAllowDeposit()), "toggle",
                    () -> BedrockForms.runClanCommand(player, "toggle deposit", reopen));
        }
        if (permissions.has(player, "simpleclans.leader.withdraw-toggle")) {
            menu.button(toggleText(player, "bedrock.bank.toggle-withdraw", clan.isAllowWithdraw()), "toggle",
                    () -> BedrockForms.runClanCommand(player, "toggle withdraw", reopen));
        }
        menu.back(() -> openMyClanMenu(player, frame));
        menu.send();
    }

    private static void openAmountMenu(@NotNull Player player, @NotNull MainFrame frame, boolean deposit) {
        String action = deposit ? "deposit" : "withdraw";
        Runnable bank = () -> openBankMenu(player, frame);
        BedrockForms.Menu menu = new BedrockForms.Menu(player,
                BedrockText.lang(deposit ? "bedrock.bank.deposit.title" : "bedrock.bank.withdraw.title", player),
                BedrockText.lang("bedrock.bank.choose-amount", player));
        for (int amount : new int[]{100, 1000}) {
            menu.button(BedrockText.buttonColor(CurrencyFormat.format(amount)), action,
                    () -> BedrockForms.runClanCommand(player, "bank " + action + " " + amount, bank));
        }
        menu.button(BedrockText.button("bedrock.bank.all", player), action,
                () -> BedrockForms.runClanCommand(player, "bank " + action + " all", bank));
        menu.button(BedrockText.button("bedrock.bank.other", player), "edit",
                () -> openMoneyForm(player, frame, deposit, "", null));
        menu.back(bank);
        menu.send();
    }

    private static void openMoneyForm(@NotNull Player player, @NotNull MainFrame frame, boolean deposit,
                                      @NotNull String typed, @Nullable String error) {
        CustomForm.Builder builder = CustomForm.builder()
                .title(BedrockText.lang(deposit ? "bedrock.bank.deposit.title" : "bedrock.bank.withdraw.title", player))
                .label(error == null ? BedrockText.lang("bedrock.bank.amount.help", player) : "§c" + error)
                .input(BedrockText.lang("bedrock.bank.amount", player), "100", typed);

        builder.validResultHandler(response -> BedrockForms.runSync(() -> {
            String input = response.asInput(1);
            String amountText = input == null ? "" : input.trim().replace(",", ".");
            if (amountText.isEmpty()) {
                openBankMenu(player, frame);
                return;
            }
            String problem = validateAmount(player, deposit, amountText);
            if (problem != null) {
                openMoneyForm(player, frame, deposit, amountText, problem);
                return;
            }
            BedrockForms.runClanCommand(player, "bank " + (deposit ? "deposit " : "withdraw ") + amountText,
                    () -> openBankMenu(player, frame));
        }));
        BedrockForms.send(player, builder);
    }

    @Nullable
    private static String validateAmount(@NotNull Player player, boolean deposit, @NotNull String amountText) {
        double amount;
        try {
            amount = Double.parseDouble(amountText);
        } catch (NumberFormatException ex) {
            return lang("bedrock.bank.amount.invalid", player);
        }
        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            return lang("bedrock.bank.amount.invalid", player);
        }
        ClanPlayer clanPlayer = plugin.getClanManager().getClanPlayer(player);
        Clan clan = clanPlayer == null ? null : clanPlayer.getClan();
        if (clan == null) {
            return lang("bedrock.gui.stale", player);
        }
        if (deposit && plugin.getPermissionsManager().hasEconomy()
                && amount > plugin.getPermissionsManager().playerGetMoney(player)) {
            return lang("bedrock.bank.amount.not-enough-player", player);
        }
        if (!deposit && amount > clan.getBalance()) {
            return lang("bedrock.bank.amount.not-enough-clan", player, clan.getBalanceFormatted());
        }
        return null;
    }

    private static void openFriendlyFireMenu(@NotNull Player player, @NotNull MainFrame frame) {
        PermissionsManager permissions = plugin.getPermissionsManager();
        ClanPlayer clanPlayer = plugin.getClanManager().getClanPlayer(player);
        Clan clan = clanPlayer == null ? null : clanPlayer.getClan();
        if (clan == null) {
            open(frame);
            return;
        }
        Runnable reopen = () -> openFriendlyFireMenu(player, frame);
        BedrockForms.Menu menu = new BedrockForms.Menu(player,
                BedrockText.lang("bedrock.ff.title", player),
                BedrockText.lang("bedrock.ff.content", player));

        if (permissions.has(player, "simpleclans.member.ff")) {
            boolean allowed = clanPlayer.isFriendlyFire();
            menu.button(toggleText(player, "bedrock.ff.personal", allowed), "ff",
                    () -> BedrockForms.runClanCommand(player, "ff " + (allowed ? "auto" : "allow"), reopen));
        }
        if (permissions.has(player, RankPermission.FRIENDLYFIRE, false)) {
            boolean allowed = clan.isFriendlyFire();
            menu.button(toggleText(player, "bedrock.ff.clan", allowed), "ff",
                    () -> BedrockForms.runClanCommand(player, "clanff " + (allowed ? "block" : "allow"), reopen));
        }
        menu.back(() -> openMyClanMenu(player, frame));
        menu.send();
    }

    private static void openHomeActions(@NotNull Player player, @NotNull MainFrame frame) {
        PermissionsManager permissions = plugin.getPermissionsManager();
        ClanPlayer clanPlayer = plugin.getClanManager().getClanPlayer(player);
        Clan clan = clanPlayer == null ? null : clanPlayer.getClan();
        if (clan == null) {
            open(frame);
            return;
        }
        boolean canTeleport = permissions.has(player, RankPermission.HOME_TP, false);
        boolean canSet = permissions.has(player, RankPermission.HOME_SET, false);
        String content;
        if (!clan.isVerified()) {
            content = BedrockText.lang("bedrock.home-actions.not-verified", player);
        } else if (!canTeleport && !canSet) {
            content = BedrockText.lang("bedrock.home-actions.no-permission", player);
        } else {
            content = BedrockText.lang(clan.getHomeLocation() == null
                    ? "bedrock.home-actions.content.unset" : "bedrock.home-actions.content", player);
        }
        BedrockForms.Menu menu = new BedrockForms.Menu(player,
                BedrockText.lang("bedrock.home-actions.title", player), content);

        if (clan.isVerified() && canTeleport && clan.getHomeLocation() != null) {
            menu.button(BedrockText.button("bedrock.home-actions.teleport", player), "home-teleport",
                    () -> BedrockForms.runClanCommand(player, "home"));
        }
        if (clan.isVerified() && canSet) {
            menu.button(BedrockText.button("bedrock.home-actions.set", player), "home-set",
                    () -> BedrockForms.runClanCommand(player, "home set", () -> openHomeActions(player, frame)));
        }
        menu.back(() -> openMyClanMenu(player, frame));
        menu.send();
    }

    private static void openJoinRequests(@NotNull Player player, @NotNull MainFrame frame) {
        ClanPlayer leader = plugin.getClanManager().getClanPlayer(player);
        Clan clan = leader == null ? null : leader.getClan();
        if (clan == null || !leader.isLeader()) {
            open(frame);
            return;
        }
        List<Request> requests = plugin.getRequestManager().getJoinRequestsFor(clan);
        if (requests.isEmpty()) {
            open(frame);
            return;
        }
        BedrockForms.Menu menu = new BedrockForms.Menu(player,
                BedrockText.lang("bedrock.join-requests.title", player),
                BedrockText.lang("bedrock.join-requests.list", player, requests.size()));
        for (Request request : requests) {
            String applicant = request.getTarget();
            ClanPlayer applicantCp = plugin.getClanManager().getAnyClanPlayer(Bukkit.getOfflinePlayer(applicant).getUniqueId());
            menu.button(BedrockText.button("bedrock.join-requests.button", player, applicant,
                            applicantCp == null ? "-" : KDRFormat.format(applicantCp.getKDR())),
                    applicantCp == null ? BedrockFormIconResolver.button("member")
                            : BedrockFormIconResolver.playerHead(applicantCp.getUniqueId(), applicant),
                    () -> openJoinRequest(player, frame, request));
        }
        menu.back(() -> open(frame));
        menu.send();
    }

    private static void openJoinRequest(@NotNull Player player, @NotNull MainFrame frame, @NotNull Request request) {
        String applicant = request.getTarget();
        ClanPlayer applicantCp = plugin.getClanManager().getAnyClanPlayer(Bukkit.getOfflinePlayer(applicant).getUniqueId());
        String kdr = applicantCp == null ? "-" : KDRFormat.format(applicantCp.getKDR());
        BedrockForms.Menu menu = new BedrockForms.Menu(player,
                BedrockText.lang("bedrock.join-requests.title", player),
                BedrockText.lang("bedrock.join-requests.content", player, applicant, request.getClan().getName(),
                        kdr));
        menu.button(BedrockText.button("bedrock.join-requests.accept", player), "accept",
                () -> decideJoinRequest(player, frame, applicant, true));
        menu.button(BedrockText.button("bedrock.join-requests.deny", player), "deny",
                () -> decideJoinRequest(player, frame, applicant, false));
        menu.back(() -> openJoinRequests(player, frame));
        menu.send();
    }

    private static void decideJoinRequest(@NotNull Player player, @NotNull MainFrame frame, @NotNull String applicant,
                                          boolean accept) {
        ClanPlayer leader = plugin.getClanManager().getClanPlayer(player);
        if (leader == null || !leader.isLeader()) {
            player.sendMessage(lang("bedrock.gui.stale", player));
            open(frame);
            return;
        }
        plugin.getRequestManager().decideJoinRequest(leader, applicant, accept);
        openJoinRequests(player, frame);
    }

    private static void openSettingsMenu(@NotNull Player player, @NotNull MainFrame frame) {
        BedrockForms.Menu menu = new BedrockForms.Menu(player,
                BedrockText.lang("bedrock.settings.title", player),
                BedrockText.lang("bedrock.settings.content", player));
        if (plugin.getSettingsManager().is(LANGUAGE_SELECTOR)) {
            menu.button(BedrockText.button("bedrock.settings.language", player), "language",
                    () -> BedrockClanScreens.openLanguage(player, () -> openSettingsMenu(player, frame)));
        }
        if (plugin.getPermissionsManager().has(player, "simpleclans.vip.resetkdr")) {
            menu.button(BedrockText.button("bedrock.settings.reset-kdr", player), "reset-kdr",
                    () -> BedrockForms.confirm(player,
                            BedrockText.lang("bedrock.reset-kdr.title", player),
                            BedrockText.lang("bedrock.reset-kdr.content", player),
                            () -> resetKdr(player, frame),
                            () -> openSettingsMenu(player, frame)));
        }
        menu.back(() -> open(frame));
        menu.send();
    }

    private static void resetKdr(@NotNull Player player, @NotNull MainFrame frame) {
        if (!plugin.getSettingsManager().is(ALLOW_RESET_KDR)) {
            ChatBlock.sendMessageKey(player, "disabled.command");
            return;
        }
        ClanPlayer clanPlayer = plugin.getClanManager().getCreateClanPlayer(player.getUniqueId());
        PlayerResetKdrEvent event = new PlayerResetKdrEvent(clanPlayer);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled() && plugin.getClanManager().purchaseResetKdr(player)) {
            plugin.getClanManager().resetKdr(clanPlayer);
            ChatBlock.sendMessageKey(player, "you.have.reseted.your.kdr");
        }
        openSettingsMenu(player, frame);
    }

    private static void openCreateClanForm(@NotNull Player player, @NotNull MainFrame frame, @NotNull String tag,
                                           @NotNull String name, @Nullable String error) {
        CustomForm.Builder builder = CustomForm.builder()
                .title(BedrockText.lang("bedrock.create.title", player))
                .label(error == null ? BedrockText.lang("bedrock.create.price", player, getCreationPrice(player))
                        : "§c" + error)
                .input(BedrockText.lang("bedrock.create.tag", player), "NEX", tag)
                .input(BedrockText.lang("bedrock.create.name", player), "Nexus", name);

        builder.validResultHandler(response -> BedrockForms.runSync(() -> {
            String newTag = valueOf(response.asInput(1));
            String newName = valueOf(response.asInput(2));
            String problem = validateClan(player, newTag, newName);
            if (problem != null) {
                openCreateClanForm(player, frame, newTag, newName, problem);
                return;
            }
            // The last argument of the command takes the rest of the line, so the name keeps its spaces
            BedrockForms.runClanCommand(player, "create " + newTag + " " + newName);
        }));
        BedrockForms.send(player, builder);
    }

    @NotNull
    private static String valueOf(@Nullable String input) {
        return input == null ? "" : input.trim();
    }

    @Nullable
    private static String validateClan(@NotNull Player player, @NotNull String tag, @NotNull String name) {
        if (tag.isEmpty() || name.isEmpty()) {
            return lang("bedrock.create.empty", player);
        }
        if (tag.contains(" ")) {
            return lang("bedrock.create.tag-spaces", player);
        }
        Optional<String> tagError = plugin.getTagValidator().validate(player, tag);
        if (tagError.isPresent()) {
            return BedrockText.clean(tagError.get());
        }
        if (plugin.getClanManager().isClan(ChatUtils.stripColors(tag))) {
            return lang("clan.with.this.tag.already.exists", player);
        }
        if (!plugin.getPermissionsManager().has(player, "simpleclans.mod.bypass")) {
            SettingsManager settings = plugin.getSettingsManager();
            int length = ChatUtils.stripColors(name).length();
            if (length > settings.getInt(CLAN_MAX_LENGTH)) {
                return lang("your.clan.name.cannot.be.longer.than.characters", player, settings.getInt(CLAN_MAX_LENGTH));
            }
            if (length <= settings.getInt(CLAN_MIN_LENGTH)) {
                return lang("your.clan.name.must.be.longer.than.characters", player, settings.getInt(CLAN_MIN_LENGTH));
            }
            if (name.contains("&")) {
                return lang("your.clan.name.cannot.contain.color.codes", player);
            }
        }
        return null;
    }

    @NotNull
    private static String getCreationPrice(@NotNull Player player) {
        SettingsManager settings = plugin.getSettingsManager();
        return settings.is(ECONOMY_PURCHASE_CLAN_CREATE)
                ? CurrencyFormat.format(settings.getDouble(ECONOMY_CREATION_PRICE))
                : BedrockText.lang("bedrock.home.free", player);
    }

    /**
     * "Name: on" plus what a tap does, so the button shows the current state
     */
    @NotNull
    static String toggleText(@NotNull Player player, @NotNull String key, boolean enabled) {
        return BedrockText.button(key, player, BedrockText.lang(enabled ? "bedrock.toggle.on" : "bedrock.toggle.off", player),
                BedrockText.lang(enabled ? "bedrock.toggle.tap-off" : "bedrock.toggle.tap-on", player));
    }

    @NotNull
    private static String status(@NotNull Player player, boolean allowed) {
        return BedrockText.lang(allowed ? "bedrock.status.allowed" : "bedrock.status.blocked", player);
    }

    private static void resign(@NotNull Player player, @NotNull MainFrame frame, @NotNull Clan expectedClan) {
        ClanPlayer current = plugin.getClanManager().getClanPlayer(player);
        Clan clan = current == null ? null : current.getClan();
        if (clan == null || !clan.equals(expectedClan)) {
            player.sendMessage(lang("bedrock.gui.stale", player));
            open(frame);
            return;
        }
        // Unlike the chat command, the last leader is not turned into a disband: that has its own confirmation
        if (!clan.isPermanent() && current.isLeader() && clan.getLeaders().size() == 1) {
            ChatBlock.sendMessageKey(player, "last.leader.cannot.resign.you.must.appoint.another.leader.or.disband.the.clan");
            openMyClanMenu(player, frame);
            return;
        }
        clan.addBb(current.getName(), lang("0.has.resigned", current.getName()));
        current.addResignTime(clan.getTag());
        clan.removePlayerFromClan(current.getUniqueId());
        ChatBlock.sendMessageKey(player, "resign.success");
        open(frame);
    }

    private static void disbandClan(@NotNull Player player, @NotNull MainFrame frame, @NotNull Clan expectedClan) {
        ClanPlayer current = plugin.getClanManager().getClanPlayer(player);
        Clan currentClan = current == null ? null : current.getClan();
        if (currentClan == null || !currentClan.equals(expectedClan) || !current.isLeader()
                || !plugin.getPermissionsManager().has(player, "simpleclans.leader.disband")) {
            player.sendMessage(lang("bedrock.gui.stale", player));
            open(frame);
            return;
        }

        if (currentClan.isPermanent()) {
            ChatBlock.sendMessageKey(player, "cannot.disband.permanent");
            return;
        }

        // Multiple leaders use the normal vote flow; a sole leader already confirmed through the Form.
        if (currentClan.getLeaders().size() != 1) {
            BedrockForms.runClanCommand(player, "disband");
            return;
        }

        String clanName = currentClan.getName();
        currentClan.disband(player, true, false);
        ChatBlock.sendMessageKey(player, "clan.has.been.disbanded", clanName);
    }
}
