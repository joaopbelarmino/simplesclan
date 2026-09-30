package net.sacredlabyrinth.phaed.simpleclans.ui.bedrock;

import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer.Channel;
import net.sacredlabyrinth.phaed.simpleclans.RankPermission;
import net.sacredlabyrinth.phaed.simpleclans.Request;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.LanguageSelectorFrame;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.MainFrame;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.RelationsFrame;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.RosterFrame;
import net.sacredlabyrinth.phaed.simpleclans.utils.CurrencyFormat;
import net.sacredlabyrinth.phaed.simpleclans.utils.KDRFormat;
import net.sacredlabyrinth.phaed.simpleclans.utils.VanishUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.*;

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
        Request invite = plugin.getRequestManager().getInviteRequestFor(player);
        List<Runnable> actions = new ArrayList<>();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(BedrockText.lang("bedrock.home.no-clan.title", player))
                .content(BedrockText.lang("bedrock.home.no-clan.content", player));

        if (plugin.getPermissionsManager().has(player, "simpleclans.leader.create")) {
            builder.button(BedrockText.lang("bedrock.home.create-clan", player, getCreationPrice(player)),
                    BedrockFormIconResolver.ui("textures/ui/color_plus"));
            actions.add(() -> openCreateClanForm(player));
        }

        if (plugin.getPermissionsManager().has(player, "simpleclans.anyone.apply")) {
            builder.button(BedrockText.lang("bedrock.home.apply-clan", player),
                    BedrockFormIconResolver.ui("textures/ui/FriendsIcon"));
            actions.add(() -> openApplyClanForm(player));
        }

        if (invite != null) {
            builder.button(BedrockText.lang("bedrock.home.accept-invite", player, invite.getClan().getName()),
                    BedrockFormIconResolver.ui("textures/ui/confirm"));
            // /accept is a root command, not a /clan subcommand
            actions.add(() -> player.performCommand(plugin.getSettingsManager().getString(COMMANDS_ACCEPT)));
        }

        builder.button(BedrockText.lang("bedrock.home.settings", player),
                BedrockFormIconResolver.ui("textures/ui/settings_glyph_color_2x"));
        actions.add(() -> openSettingsMenu(player, frame));

        builder.validResultHandler(response -> runButton(actions, response.clickedButtonId()));
        sendForm(player, builder);
    }

    private static void openClanMenu(@NotNull Player player, @NotNull MainFrame frame, @NotNull ClanPlayer clanPlayer) {
        Clan clan = clanPlayer.getClan();
        List<Request> joinRequests = clanPlayer.isLeader()
                ? plugin.getRequestManager().getJoinRequestsFor(clan)
                : new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(BedrockText.lang("bedrock.home.clan.title", player, clan.getName()))
                .content(BedrockText.lang("bedrock.home.clan.content", player, clan.getColorTag(), clan.getSize()));

        if (plugin.getDynamicWarManager().hasActiveWar(clan)
                && plugin.getPermissionsManager().has(player, "simpleclans.member.dynamicwar")) {
            builder.button(BedrockText.lang("bedrock.home.war-join", player),
                    BedrockFormIconResolver.ui("textures/ui/icon_sword"));
            actions.add(() -> plugin.getDynamicWarManager().joinNearestWar(player));
        }

        builder.button(BedrockText.lang("bedrock.home.my-clan", player),
                BedrockFormIconResolver.ui("textures/ui/icon_best3"));
        actions.add(() -> openMyClanMenu(player, frame, clanPlayer));

        if (clanPlayer.isLeader() && !clan.isVerified()
                && plugin.getPermissionsManager().has(player, "simpleclans.leader.verify")) {
            builder.button(BedrockText.lang("bedrock.home.verify-clan", player),
                    BedrockFormIconResolver.ui("textures/ui/confirm"));
            actions.add(() -> runClanCommand(player, "verify"));
        }

        if (plugin.getPermissionsManager().has(player, "simpleclans.member.roster")) {
            builder.button(BedrockText.lang("bedrock.home.members", player),
                    BedrockFormIconResolver.ui("textures/ui/FriendsIcon"));
            actions.add(() -> InventoryDrawer.open(new RosterFrame(player, frame, clan)));
        }

        if (!joinRequests.isEmpty()) {
            builder.button(BedrockText.lang("bedrock.home.join-requests", player, joinRequests.size()),
                    BedrockFormIconResolver.ui("textures/ui/icon_new"));
            actions.add(() -> openJoinRequestForm(player, joinRequests.get(0)));
        }

        if (plugin.getPermissionsManager().has(player, "simpleclans.vip.resetkdr")) {
            builder.button(BedrockText.lang("bedrock.home.reset-kdr", player),
                    BedrockFormIconResolver.ui("textures/ui/refresh_light"));
            actions.add(() -> runClanCommand(player, "resetkdr"));
        }

        builder.button(BedrockText.lang("bedrock.home.settings", player),
                BedrockFormIconResolver.ui("textures/ui/settings_glyph_color_2x"));
        actions.add(() -> openSettingsMenu(player, frame));

        builder.validResultHandler(response -> runButton(actions, response.clickedButtonId()));
        sendForm(player, builder);
    }

    private static void openMyClanMenu(@NotNull Player player, @NotNull MainFrame frame, @NotNull ClanPlayer clanPlayer) {
        Clan clan = clanPlayer.getClan();
        if (clan == null) {
            open(frame);
            return;
        }

        List<Runnable> actions = new ArrayList<>();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(BedrockText.lang("bedrock.my-clan.title", player, clan.getName()))
                .content(BedrockText.lang("bedrock.my-clan.content", player,
                        clan.getColorTag(), clan.getSize(), clan.getTotalKDR(), clan.getBalanceFormatted()));

        builder.button(BedrockText.lang("bedrock.my-clan.overview", player),
                BedrockFormIconResolver.ui("textures/ui/icon_book_writable"));
        actions.add(() -> openClanOverview(player, frame, clanPlayer));

        if (plugin.getPermissionsManager().has(player, "simpleclans.member.roster")) {
            builder.button(BedrockText.lang("bedrock.my-clan.members", player),
                    BedrockFormIconResolver.ui("textures/ui/FriendsIcon"));
            actions.add(() -> InventoryDrawer.open(new RosterFrame(player, frame, clan)));
        }

        if (plugin.getPermissionsManager().has(player, "simpleclans.anyone.alliances")) {
            builder.button(BedrockText.lang("bedrock.my-clan.relations", player),
                    BedrockFormIconResolver.ui("textures/ui/multiplayer_glyph_color"));
            actions.add(() -> InventoryDrawer.open(new RelationsFrame(player, frame, clan)));
        }

        builder.button(BedrockText.lang("bedrock.my-clan.home", player),
                BedrockFormIconResolver.ui("textures/ui/icon_recipe_item"));
        actions.add(() -> openHomeActions(player, frame, clanPlayer));

        builder.button(BedrockText.lang("bedrock.gui.back", player),
                BedrockFormIconResolver.ui("textures/ui/arrow_left"));
        actions.add(() -> open(frame));

        builder.validResultHandler(response -> runButton(actions, response.clickedButtonId()));
        sendForm(player, builder);
    }

    private static void openClanOverview(@NotNull Player player, @NotNull MainFrame frame, @NotNull ClanPlayer clanPlayer) {
        Clan clan = clanPlayer.getClan();
        if (clan == null) {
            open(frame);
            return;
        }

        List<Request> joinRequests = clanPlayer.isLeader()
                ? plugin.getRequestManager().getJoinRequestsFor(clan)
                : new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        String description = clan.getDescription() == null || clan.getDescription().isEmpty()
                ? BedrockText.lang("no.description", player)
                : clan.getDescription();
        String verified = BedrockText.lang(clan.isVerified()
                ? "bedrock.overview.verified.yes"
                : "bedrock.overview.verified.no", player);
        String personalFf = BedrockText.lang(clanPlayer.isFriendlyFire()
                ? "bedrock.status.allowed"
                : "bedrock.status.auto", player);
        String clanFf = BedrockText.lang(clan.isFriendlyFire()
                ? "bedrock.status.allowed"
                : "bedrock.status.blocked", player);
        String leaders = BedrockText.clean(clan.getLeadersString("", ", "));

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(BedrockText.lang("bedrock.overview.title", player, clan.getName()))
                .content(BedrockText.lang("bedrock.overview.content", player,
                        clan.getColorTag(),
                        description,
                        verified,
                        VanishUtils.getNonVanished(player, clan).size(),
                        clan.getSize(),
                        KDRFormat.format(clan.getTotalKDR()),
                        clan.getTotalKills(),
                        clan.getTotalDeaths(),
                        clan.getBalanceFormatted(),
                        leaders,
                        personalFf,
                        clanFf));

        if (plugin.getPermissionsManager().has(player, "simpleclans.member.roster")) {
            builder.button(BedrockText.lang("bedrock.overview.members", player),
                    BedrockFormIconResolver.ui("textures/ui/FriendsIcon"));
            actions.add(() -> InventoryDrawer.open(new RosterFrame(player, frame, clan)));
        }

        if (plugin.getPermissionsManager().has(player, "simpleclans.anyone.alliances")) {
            builder.button(BedrockText.lang("bedrock.overview.relations", player),
                    BedrockFormIconResolver.ui("textures/ui/multiplayer_glyph_color"));
            actions.add(() -> InventoryDrawer.open(new RelationsFrame(player, frame, clan)));
        }

        builder.button(BedrockText.lang("bedrock.overview.home", player),
                BedrockFormIconResolver.ui("textures/ui/icon_recipe_item"));
        actions.add(() -> openHomeActions(player, frame, clanPlayer));

        if (plugin.getPermissionsManager().has(player, RankPermission.BANK_BALANCE, false)) {
            builder.button(BedrockText.lang("bedrock.overview.bank", player),
                    BedrockFormIconResolver.ui("textures/ui/store_home_icon"));
            actions.add(() -> openBankMenu(player, frame, clanPlayer));
        }

        if (plugin.getPermissionsManager().has(player, "simpleclans.member.ff")
                || plugin.getPermissionsManager().has(player, RankPermission.FRIENDLYFIRE, false)) {
            builder.button(BedrockText.lang("bedrock.overview.ff", player),
                    BedrockFormIconResolver.ui("textures/ui/icon_sword"));
            actions.add(() -> openFriendlyFireMenu(player, frame, clanPlayer));
        }

        if (plugin.getPermissionsManager().has(player, "simpleclans.member.chat")) {
            builder.button(BedrockText.lang("bedrock.overview.chat", player),
                    BedrockFormIconResolver.ui("textures/ui/comment"));
            actions.add(() -> toggleClanChat(player, frame, clanPlayer));
        }

        if (clanPlayer.isLeader() && !clan.isVerified()
                && plugin.getPermissionsManager().has(player, "simpleclans.leader.verify")) {
            builder.button(BedrockText.lang("bedrock.overview.verify", player),
                    BedrockFormIconResolver.ui("textures/ui/confirm"));
            actions.add(() -> runClanCommand(player, "verify"));
        }

        if (!joinRequests.isEmpty()) {
            builder.button(BedrockText.lang("bedrock.overview.join-requests", player, joinRequests.size()),
                    BedrockFormIconResolver.ui("textures/ui/icon_new"));
            actions.add(() -> openJoinRequestForm(player, joinRequests.get(0)));
        }

        if (clanPlayer.isLeader() && plugin.getPermissionsManager().has(player, "simpleclans.leader.joinrequests-toggle")) {
            builder.button(BedrockText.lang(clan.isJoinRequestsEnabled()
                            ? "bedrock.overview.join-toggle.enabled"
                            : "bedrock.overview.join-toggle.disabled", player),
                    BedrockFormIconResolver.ui("textures/ui/refresh_light"));
            actions.add(() -> runClanCommand(player, "toggle joinrequests"));
        }

        if (clanPlayer.isLeader() && plugin.getPermissionsManager().has(player, "simpleclans.leader.dynamicwar-toggle")) {
            builder.button(BedrockText.lang(clan.isDynamicWarEnabled()
                            ? "bedrock.overview.war-toggle.enabled"
                            : "bedrock.overview.war-toggle.disabled", player),
                    BedrockFormIconResolver.ui("textures/ui/icon_sword"));
            actions.add(() -> runClanCommand(player, "toggle warstart"));
        }

        if (plugin.getDynamicWarManager().hasActiveWar(clan)
                && plugin.getPermissionsManager().has(player, "simpleclans.member.dynamicwar")) {
            builder.button(BedrockText.lang("bedrock.overview.war-join", player),
                    BedrockFormIconResolver.ui("textures/ui/realms_slot_check"));
            actions.add(() -> Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("guerra")));
        }

        if (plugin.getPermissionsManager().has(player, "simpleclans.member.resign")) {
            builder.button(BedrockText.lang("bedrock.overview.resign", player),
                    BedrockFormIconResolver.ui("textures/ui/cancel"));
            actions.add(() -> confirmAction(player,
                    BedrockText.lang("bedrock.resign.title", player),
                    BedrockText.lang("bedrock.resign.content", player, clan.getName()),
                    () -> runClanCommand(player, "resign"),
                    () -> openClanOverview(player, frame, clanPlayer)));
        }

        if (clanPlayer.isLeader()
                && plugin.getPermissionsManager().has(player, "simpleclans.leader.disband")) {
            builder.button(BedrockText.lang("bedrock.overview.disband", player),
                    BedrockFormIconResolver.ui("textures/ui/cancel"));
            actions.add(() -> confirmAction(player,
                    BedrockText.lang("bedrock.disband.title", player),
                    BedrockText.lang("bedrock.disband.content", player, clan.getName()),
                    () -> disbandClan(player, frame, clan),
                    () -> openClanOverview(player, frame, clanPlayer)));
        }

        builder.button(BedrockText.lang("bedrock.gui.back", player),
                BedrockFormIconResolver.ui("textures/ui/arrow_left"));
        actions.add(() -> openMyClanMenu(player, frame, clanPlayer));

        builder.validResultHandler(response -> runButton(actions, response.clickedButtonId()));
        sendForm(player, builder);
    }

    private static void openBankMenu(@NotNull Player player, @NotNull MainFrame frame, @NotNull ClanPlayer clanPlayer) {
        Clan clan = clanPlayer.getClan();
        if (clan == null) {
            open(frame);
            return;
        }

        List<Runnable> actions = new ArrayList<>();
        String withdrawStatus = BedrockText.lang(clan.isAllowWithdraw() ? "bedrock.status.allowed" : "bedrock.status.blocked", player);
        String depositStatus = BedrockText.lang(clan.isAllowDeposit() ? "bedrock.status.allowed" : "bedrock.status.blocked", player);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(BedrockText.lang("bedrock.bank.title", player))
                .content(BedrockText.lang("bedrock.bank.content", player, clan.getBalanceFormatted(), depositStatus, withdrawStatus));

        if (plugin.getPermissionsManager().has(player, RankPermission.BANK_DEPOSIT, false)) {
            builder.button(BedrockText.lang("bedrock.bank.deposit", player), BedrockFormIconResolver.ui("textures/ui/color_plus"));
            actions.add(() -> openMoneyForm(player, frame, clanPlayer, "deposit", "bedrock.bank.deposit.title"));
        }

        if (plugin.getPermissionsManager().has(player, RankPermission.BANK_WITHDRAW, false)) {
            builder.button(BedrockText.lang("bedrock.bank.withdraw", player), BedrockFormIconResolver.ui("textures/ui/download_backup"));
            actions.add(() -> openMoneyForm(player, frame, clanPlayer, "withdraw", "bedrock.bank.withdraw.title"));
        }

        if (plugin.getPermissionsManager().has(player, "simpleclans.leader.deposit-toggle")) {
            builder.button(BedrockText.lang("bedrock.bank.toggle-deposit", player), BedrockFormIconResolver.ui("textures/ui/refresh_light"));
            actions.add(() -> runClanCommand(player, "toggle deposit"));
        }
        if (plugin.getPermissionsManager().has(player, "simpleclans.leader.withdraw-toggle")) {
            builder.button(BedrockText.lang("bedrock.bank.toggle-withdraw", player), BedrockFormIconResolver.ui("textures/ui/refresh_light"));
            actions.add(() -> runClanCommand(player, "toggle withdraw"));
        }

        builder.button(BedrockText.lang("bedrock.gui.back", player), BedrockFormIconResolver.ui("textures/ui/arrow_left"));
        actions.add(() -> openClanOverview(player, frame, clanPlayer));

        builder.validResultHandler(response -> runButton(actions, response.clickedButtonId()));
        sendForm(player, builder);
    }

    private static void openMoneyForm(@NotNull Player player, @NotNull MainFrame frame, @NotNull ClanPlayer clanPlayer,
                                      @NotNull String action, @NotNull String titleKey) {
        CustomForm.Builder builder = CustomForm.builder()
                .title(BedrockText.lang(titleKey, player))
                .input(BedrockText.lang("bedrock.bank.amount", player), "100");

        builder.validResultHandler(response -> {
            String amount = response.asInput(0).trim().replace(",", ".");
            if (amount.isEmpty()) {
                openBankMenu(player, frame, clanPlayer);
                return;
            }
            runClanCommand(player, "bank " + action + " " + amount);
        });
        sendForm(player, builder);
    }

    private static void openFriendlyFireMenu(@NotNull Player player, @NotNull MainFrame frame, @NotNull ClanPlayer clanPlayer) {
        Clan clan = clanPlayer.getClan();
        if (clan == null) {
            open(frame);
            return;
        }

        List<Runnable> actions = new ArrayList<>();
        String personalFf = BedrockText.lang(clanPlayer.isFriendlyFire()
                ? "bedrock.status.allowed"
                : "bedrock.status.auto", player);
        String clanFf = BedrockText.lang(clan.isFriendlyFire()
                ? "bedrock.status.allowed"
                : "bedrock.status.blocked", player);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(BedrockText.lang("bedrock.ff.title", player))
                .content(BedrockText.lang("bedrock.ff.content", player, personalFf, clanFf));

        if (plugin.getPermissionsManager().has(player, "simpleclans.member.ff")) {
            builder.button(BedrockText.lang("bedrock.ff.personal", player),
                    BedrockFormIconResolver.ui("textures/ui/icon_sword"));
            actions.add(() -> runClanCommand(player, "ff " + (clanPlayer.isFriendlyFire() ? "auto" : "allow")));
        }

        if (plugin.getPermissionsManager().has(player, RankPermission.FRIENDLYFIRE, false)) {
            builder.button(BedrockText.lang("bedrock.ff.clan", player),
                    BedrockFormIconResolver.ui("textures/ui/multiplayer_glyph_color"));
            actions.add(() -> runClanCommand(player, "clanff " + (clan.isFriendlyFire() ? "block" : "allow")));
        }

        builder.button(BedrockText.lang("bedrock.gui.back", player),
                BedrockFormIconResolver.ui("textures/ui/arrow_left"));
        actions.add(() -> openClanOverview(player, frame, clanPlayer));

        builder.validResultHandler(response -> runButton(actions, response.clickedButtonId()));
        sendForm(player, builder);
    }

    private static void toggleClanChat(@NotNull Player player, @NotNull MainFrame frame, @NotNull ClanPlayer clanPlayer) {
        if (clanPlayer.getChannel() == Channel.CLAN) {
            clanPlayer.setChannel(Channel.NONE);
        } else {
            clanPlayer.setChannel(Channel.CLAN);
        }
        openClanOverview(player, frame, clanPlayer);
    }

    private static void openHomeActions(@NotNull Player player, @NotNull MainFrame frame, @NotNull ClanPlayer clanPlayer) {
        List<Runnable> actions = new ArrayList<>();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(BedrockText.lang("bedrock.home-actions.title", player))
                .content(BedrockText.lang("bedrock.home-actions.content", player));

        if (clanPlayer.getClan() != null && clanPlayer.getClan().isVerified()
                && plugin.getPermissionsManager().has(player, RankPermission.HOME_TP, false)) {
            builder.button(BedrockText.lang("bedrock.home-actions.teleport", player), BedrockFormIconResolver.ui("textures/ui/realms_slot_check"));
            actions.add(() -> runClanCommand(player, "home"));
        }
        if (clanPlayer.getClan() != null && clanPlayer.getClan().isVerified()
                && plugin.getPermissionsManager().has(player, RankPermission.HOME_SET, false)) {
            builder.button(BedrockText.lang("bedrock.home-actions.set", player), BedrockFormIconResolver.ui("textures/ui/color_plus"));
            actions.add(() -> runClanCommand(player, "home set"));
        }
        builder.button(BedrockText.lang("bedrock.gui.back", player), BedrockFormIconResolver.ui("textures/ui/arrow_left"));
        actions.add(() -> open(frame));

        builder.validResultHandler(response -> runButton(actions, response.clickedButtonId()));
        sendForm(player, builder);
    }

    private static void openCreateClanForm(@NotNull Player player) {
        CustomForm.Builder builder = CustomForm.builder()
                .title(BedrockText.lang("bedrock.create.title", player))
                .label(BedrockText.lang("bedrock.create.price", player, getCreationPrice(player)))
                .input(BedrockText.lang("bedrock.create.tag", player), "NEX")
                .input(BedrockText.lang("bedrock.create.name", player), "Nexus");

        builder.validResultHandler(response -> {
            String tag = response.asInput(1).trim();
            String name = response.asInput(2).trim();
            if (tag.isEmpty() || name.isEmpty()) {
                player.sendMessage(lang("bedrock.create.empty", player));
                openCreateClanForm(player);
                return;
            }
            runClanCommand(player, "create " + tag.replace(" ", "") + " " + name.replace(" ", "_"));
        });
        sendForm(player, builder);
    }

    private static void openSettingsMenu(@NotNull Player player, @NotNull MainFrame frame) {
        List<Runnable> actions = new ArrayList<>();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(BedrockText.lang("bedrock.settings.title", player))
                .content(BedrockText.lang("bedrock.settings.content", player));

        if (plugin.getSettingsManager().is(LANGUAGE_SELECTOR)) {
            builder.button(BedrockText.lang("bedrock.settings.language", player),
                    BedrockFormIconResolver.ui("textures/ui/language_glyph_color"));
            actions.add(() -> InventoryDrawer.open(new LanguageSelectorFrame(frame, player)));
        }

        builder.button(BedrockText.lang("bedrock.gui.back", player),
                BedrockFormIconResolver.ui("textures/ui/arrow_left"));
        actions.add(() -> open(frame));

        builder.validResultHandler(response -> runButton(actions, response.clickedButtonId()));
        sendForm(player, builder);
    }

    @NotNull
    private static String getCreationPrice(@NotNull Player player) {
        SettingsManager settings = plugin.getSettingsManager();
        return settings.is(ECONOMY_PURCHASE_CLAN_CREATE)
                ? CurrencyFormat.format(settings.getDouble(ECONOMY_CREATION_PRICE))
                : BedrockText.lang("bedrock.home.free", player);
    }

    private static void openApplyClanForm(@NotNull Player player) {
        List<Clan> clans = plugin.getClanManager().getClans().stream()
                .filter(clan -> plugin.getRequestManager().isClanJoinRequestAvailable(clan))
                .sorted(Comparator.comparing(Clan::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        if (clans.isEmpty()) {
            player.sendMessage(lang("bedrock.apply.none-available", player));
            return;
        }

        List<String> options = clans.stream()
                .map(clan -> BedrockText.clean(clan.getTag() + " - " + clan.getName()))
                .collect(Collectors.toList());
        CustomForm.Builder builder = CustomForm.builder()
                .title(BedrockText.lang("bedrock.apply.title", player))
                .dropdown(BedrockText.lang("bedrock.apply.choose", player), options);

        builder.validResultHandler(response -> {
            int index = response.asDropdown(0);
            if (index < 0 || index >= clans.size()) {
                return;
            }
            runClanCommand(player, "apply " + clans.get(index).getTag());
        });
        sendForm(player, builder);
    }

    private static void openJoinRequestForm(@NotNull Player player, @NotNull Request request) {
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(BedrockText.lang("bedrock.join-requests.title", player))
                .content(BedrockText.lang("bedrock.join-requests.content", player, request.getTarget(), request.getClan().getName()))
                .button(BedrockText.lang("bedrock.join-requests.accept", player))
                .button(BedrockText.lang("bedrock.join-requests.deny", player));

        builder.validResultHandler(response -> {
            int button = response.clickedButtonId();
            if (button != 0 && button != 1) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                ClanPlayer leader = plugin.getClanManager().getClanPlayer(player);
                if (leader == null || !plugin.getRequestManager().decideJoinRequest(leader, request.getTarget(), button == 0)) {
                    player.sendMessage(lang("bedrock.gui.stale", player));
                }
            });
        });
        sendForm(player, builder);
    }

    private static void runButton(@NotNull List<Runnable> actions, int buttonId) {
        if (buttonId < 0 || buttonId >= actions.size()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, actions.get(buttonId));
    }

    private static void runClanCommand(@NotNull Player player, @NotNull String subcommand) {
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(
                plugin.getSettingsManager().getString(COMMANDS_CLAN) + " " + subcommand));
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
            runClanCommand(player, "disband");
            return;
        }

        String clanName = currentClan.getName();
        currentClan.disband(player, true, false);
        ChatBlock.sendMessageKey(player, "clan.has.been.disbanded", clanName);
    }

    private static void confirmAction(@NotNull Player player, @NotNull String title, @NotNull String content,
                                      @NotNull Runnable confirm, @NotNull Runnable cancel) {
        ModalForm.Builder builder = ModalForm.builder()
                .title(title)
                .content(content)
                .button1(BedrockText.lang("bedrock.confirm.yes", player))
                .button2(BedrockText.lang("bedrock.confirm.no", player));

        builder.validResultHandler(response -> {
            if (response.clickedFirst()) {
                Bukkit.getScheduler().runTask(plugin, confirm);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, cancel);
        });
        sendForm(player, builder);
    }

    private static void sendForm(@NotNull Player player, @NotNull SimpleForm.Builder builder) {
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder);
    }

    private static void sendForm(@NotNull Player player, @NotNull CustomForm.Builder builder) {
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder);
    }

    private static void sendForm(@NotNull Player player, @NotNull ModalForm.Builder builder) {
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder);
    }
}
