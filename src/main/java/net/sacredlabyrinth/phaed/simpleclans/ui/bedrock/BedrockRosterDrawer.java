package net.sacredlabyrinth.phaed.simpleclans.ui.bedrock;

import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.RankPermission;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCFrame;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.InviteFrame;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.RosterFrame;
import net.sacredlabyrinth.phaed.simpleclans.utils.KDRFormat;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BedrockRosterDrawer {

    private static final int PAGE_SIZE = 8;
    private static final String MEMBER_ICON = "textures/ui/icon_steve";
    private static final SimpleClans plugin = SimpleClans.getInstance();

    private BedrockRosterDrawer() {
    }

    public static void open(@NotNull RosterFrame frame) {
        open(frame, 0);
    }

    private static void open(@NotNull RosterFrame frame, int page) {
        Player viewer = frame.getViewer();
        Clan clan = frame.getSubject();
        List<ClanPlayer> members = getSortedMembers(clan);
        int maxPage = Math.max(0, (members.size() - 1) / PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(page, maxPage));
        int from = currentPage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, members.size());
        List<Runnable> actions = new ArrayList<>();

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(BedrockText.lang("bedrock.roster.title", viewer, clan.getName()))
                .content(BedrockText.lang("bedrock.roster.content", viewer,
                        clan.getColorTag(),
                        members.size(),
                        currentPage + 1,
                        maxPage + 1));

        if (!frame.isStaff() && plugin.getPermissionsManager().has(viewer, RankPermission.INVITE, false)) {
            builder.button(BedrockText.lang("bedrock.roster.invite", viewer),
                    BedrockFormIconResolver.ui("textures/ui/color_plus"));
            actions.add(() -> InventoryDrawer.open(new InviteFrame(frame, viewer)));
        }

        for (int i = from; i < to; i++) {
            ClanPlayer member = members.get(i);
            builder.button(getMemberButton(viewer, member), BedrockFormIconResolver.ui(MEMBER_ICON));
            actions.add(() -> openMemberDetails(frame, member, currentPage));
        }

        if (currentPage > 0) {
            builder.button(BedrockText.lang("bedrock.gui.previous-page", viewer),
                    BedrockFormIconResolver.ui("textures/ui/arrow_left"));
            actions.add(() -> open(frame, currentPage - 1));
        }

        if (currentPage < maxPage) {
            builder.button(BedrockText.lang("bedrock.gui.next-page", viewer),
                    BedrockFormIconResolver.ui("textures/ui/arrow_right"));
            actions.add(() -> open(frame, currentPage + 1));
        }

        builder.button(BedrockText.lang("bedrock.gui.back", viewer),
                BedrockFormIconResolver.ui("textures/ui/arrow_left"));
        actions.add(() -> {
            SCFrame parent = frame.getParent();
            if (parent != null) {
                InventoryDrawer.open(parent);
            }
        });

        builder.validResultHandler(response -> runButton(actions, response.clickedButtonId()));
        sendForm(viewer, builder);
    }

    private static void openMemberDetails(@NotNull RosterFrame frame, @NotNull ClanPlayer member, int page) {
        Player viewer = frame.getViewer();
        Clan clan = frame.getSubject();
        ClanPlayer viewerCp = plugin.getClanManager().getCreateClanPlayer(viewer.getUniqueId());
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUniqueId());
        Player onlineMember = Bukkit.getPlayer(member.getUniqueId());
        List<Runnable> actions = new ArrayList<>();
        String online = offlinePlayer.isOnline()
                ? BedrockText.lang("bedrock.roster.status.online", viewer)
                : BedrockText.lang("bedrock.roster.status.offline", viewer);
        String role = getRole(viewer, member);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(BedrockText.lang("bedrock.roster.member.title", viewer, member.getName()))
                .content(BedrockText.lang("bedrock.roster.member.content", viewer,
                        member.getName(),
                        role,
                        online,
                        KDRFormat.format(member.getKDR()),
                        member.getTotalKills(),
                        member.getDeaths()));

        if (canLead(viewer, viewerCp, clan) && !viewer.getUniqueId().equals(member.getUniqueId())) {
            if (!member.isLeader() && plugin.getPermissionsManager().has(viewer, RankPermission.KICK, false)) {
                builder.button(BedrockText.lang("bedrock.roster.action.kick", viewer),
                        BedrockFormIconResolver.ui("textures/ui/cancel"));
                actions.add(() -> confirmAction(viewer,
                        BedrockText.lang("bedrock.roster.confirm.kick.title", viewer),
                        BedrockText.lang("bedrock.roster.confirm.kick.content", viewer, member.getName()),
                        () -> runClanCommand(viewer, "kick " + member.getName()),
                        () -> openMemberDetails(frame, member, page)));
            }

            if (!member.isLeader() && onlineMember != null
                    && plugin.getPermissionsManager().has(viewer, "simpleclans.leader.promote")
                    && plugin.getPermissionsManager().has(onlineMember, "simpleclans.leader.promotable")) {
                builder.button(BedrockText.lang("bedrock.roster.action.promote", viewer),
                        BedrockFormIconResolver.ui("textures/ui/up_arrow"));
                actions.add(() -> confirmAction(viewer,
                        BedrockText.lang("bedrock.roster.confirm.promote.title", viewer),
                        BedrockText.lang("bedrock.roster.confirm.promote.content", viewer, member.getName()),
                        () -> runClanCommand(viewer, "promote " + member.getName()),
                        () -> openMemberDetails(frame, member, page)));
            }

            if (member.isLeader() && plugin.getPermissionsManager().has(viewer, "simpleclans.leader.demote")) {
                builder.button(BedrockText.lang("bedrock.roster.action.demote", viewer),
                        BedrockFormIconResolver.ui("textures/ui/down_arrow"));
                actions.add(() -> confirmAction(viewer,
                        BedrockText.lang("bedrock.roster.confirm.demote.title", viewer),
                        BedrockText.lang("bedrock.roster.confirm.demote.content", viewer, member.getName()),
                        () -> runClanCommand(viewer, "demote " + member.getName()),
                        () -> openMemberDetails(frame, member, page)));
            }

            if (!member.isLeader() && plugin.getPermissionsManager().has(viewer, "simpleclans.leader.settrust")) {
                builder.button(BedrockText.lang(member.isTrusted()
                                ? "bedrock.roster.action.untrust"
                                : "bedrock.roster.action.trust", viewer),
                        BedrockFormIconResolver.ui("textures/ui/permissions_member_star"));
                actions.add(() -> runClanCommand(viewer, (member.isTrusted() ? "untrust " : "trust ") + member.getName()));
            }
        }

        builder.button(BedrockText.lang("bedrock.gui.back", viewer),
                BedrockFormIconResolver.ui("textures/ui/arrow_left"));
        actions.add(() -> open(frame, page));
        builder.validResultHandler(response -> runButton(actions, response.clickedButtonId()));
        sendForm(viewer, builder);
    }

    private static boolean canLead(@NotNull Player viewer, @NotNull ClanPlayer viewerCp, @NotNull Clan clan) {
        return viewerCp.getClan() != null && viewerCp.getClan().equals(clan) && viewerCp.isLeader();
    }

    @NotNull
    private static String getMemberButton(@NotNull Player viewer, @NotNull ClanPlayer member) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUniqueId());
        String online = offlinePlayer.isOnline()
                ? BedrockText.lang("bedrock.roster.status.online", viewer)
                : BedrockText.lang("bedrock.roster.status.offline", viewer);
        return BedrockText.lang("bedrock.roster.member.button", viewer,
                member.getName(),
                getRole(viewer, member),
                online,
                KDRFormat.format(member.getKDR()));
    }

    @NotNull
    private static String getRole(@NotNull Player viewer, @NotNull ClanPlayer member) {
        if (member.isLeader()) {
            return BedrockText.lang("bedrock.roster.role.leader", viewer);
        }
        if (member.isTrusted()) {
            return BedrockText.lang("bedrock.roster.role.trusted", viewer);
        }
        return BedrockText.lang("bedrock.roster.role.member", viewer);
    }

    @NotNull
    private static List<ClanPlayer> getSortedMembers(@NotNull Clan clan) {
        List<ClanPlayer> members = new ArrayList<>(clan.getMembers());
        members.sort(Comparator.comparing(BedrockRosterDrawer::memberWeight)
                .thenComparing(ClanPlayer::getName, String.CASE_INSENSITIVE_ORDER));
        return members;
    }

    private static int memberWeight(@NotNull ClanPlayer member) {
        if (member.isLeader()) {
            return 0;
        }
        if (member.isTrusted()) {
            return 1;
        }
        return 2;
    }

    private static void runButton(@NotNull List<Runnable> actions, int buttonId) {
        if (buttonId < 0 || buttonId >= actions.size()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, actions.get(buttonId));
    }

    private static void runClanCommand(@NotNull Player player, @NotNull String subcommand) {
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(
                plugin.getSettingsManager().getString(net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.COMMANDS_CLAN)
                        + " " + subcommand));
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
        plugin.getServer().getScheduler().runTask(plugin,
                () -> org.geysermc.floodgate.api.FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder));
    }

    private static void sendForm(@NotNull Player player, @NotNull ModalForm.Builder builder) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> org.geysermc.floodgate.api.FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder));
    }
}
