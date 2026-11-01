package net.sacredlabyrinth.phaed.simpleclans.ui.bedrock;

import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.RankPermission;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCFrame;
import net.sacredlabyrinth.phaed.simpleclans.ui.frames.RosterFrame;
import net.sacredlabyrinth.phaed.simpleclans.utils.KDRFormat;
import net.sacredlabyrinth.phaed.simpleclans.utils.VanishUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BedrockRosterDrawer {

    private static final SimpleClans plugin = SimpleClans.getInstance();

    private BedrockRosterDrawer() {
    }

    public static void open(@NotNull RosterFrame frame) {
        open(frame, 0);
    }

    private static void open(@NotNull RosterFrame frame, int page) {
        Player viewer = frame.getViewer();
        Clan clan = frame.getSubject();
        List<ClanPlayer> members = getSortedMembers(viewer, clan);
        int pageSize = BedrockClanScreens.PAGE_SIZE;
        int maxPage = Math.max(0, (members.size() - 1) / pageSize);
        int currentPage = Math.max(0, Math.min(page, maxPage));
        int from = currentPage * pageSize;
        int to = Math.min(from + pageSize, members.size());
        long online = members.stream().filter(member -> isOnline(viewer, member)).count();

        BedrockForms.Menu menu = new BedrockForms.Menu(viewer,
                BedrockText.lang("bedrock.roster.title", viewer, clan.getName()),
                BedrockText.lang("bedrock.roster.content", viewer, clan.getColorTag(), online, members.size(),
                        currentPage + 1, maxPage + 1));

        if (!frame.isStaff() && plugin.getPermissionsManager().has(viewer, RankPermission.INVITE, false)) {
            menu.button(BedrockText.button("bedrock.roster.invite", viewer), "invite",
                    () -> BedrockClanScreens.openInvite(viewer, () -> open(frame, currentPage)));
        }
        for (int i = from; i < to; i++) {
            ClanPlayer member = members.get(i);
            menu.button(getMemberButton(viewer, member),
                    BedrockFormIconResolver.playerHead(member.getUniqueId(), member.getName()),
                    () -> openMemberDetails(frame, member, currentPage));
        }
        BedrockClanScreens.addPages(menu, viewer, currentPage, maxPage, p -> open(frame, p));
        menu.back(() -> {
            SCFrame parent = frame.getParent();
            if (parent != null) {
                InventoryDrawer.open(parent);
            }
        });
        menu.send();
    }

    private static void openMemberDetails(@NotNull RosterFrame frame, @NotNull ClanPlayer member, int page) {
        Player viewer = frame.getViewer();
        Clan clan = frame.getSubject();
        ClanPlayer viewerCp = plugin.getClanManager().getCreateClanPlayer(viewer.getUniqueId());
        Player onlineMember = Bukkit.getPlayer(member.getUniqueId());
        Runnable reopen = () -> openMemberDetails(frame, member, page);
        BedrockForms.Menu menu = new BedrockForms.Menu(viewer,
                BedrockText.lang("bedrock.roster.member.title", viewer, member.getName()),
                BedrockText.lang("bedrock.roster.member.content", viewer,
                        member.getName(),
                        getRole(viewer, member),
                        getStatus(viewer, member),
                        KDRFormat.format(member.getKDR()),
                        member.getTotalKills(),
                        member.getDeaths(),
                        member.getJoinDateString()));

        if (canLead(viewerCp, clan) && !viewer.getUniqueId().equals(member.getUniqueId())) {
            if (!member.isLeader() && plugin.getPermissionsManager().has(viewer, RankPermission.KICK, false)) {
                menu.button(BedrockText.button("bedrock.roster.action.kick", viewer), "kick",
                        () -> BedrockForms.confirm(viewer,
                                BedrockText.lang("bedrock.roster.confirm.kick.title", viewer),
                                BedrockText.lang("bedrock.roster.confirm.kick.content", viewer, member.getName()),
                                () -> BedrockForms.runClanCommand(viewer, "kick " + member.getName(),
                                        () -> open(frame, page)),
                                reopen));
            }
            if (!member.isLeader() && onlineMember != null
                    && plugin.getPermissionsManager().has(viewer, "simpleclans.leader.promote")
                    && plugin.getPermissionsManager().has(onlineMember, "simpleclans.leader.promotable")) {
                menu.button(BedrockText.button("bedrock.roster.action.promote", viewer), "promote",
                        () -> BedrockForms.confirm(viewer,
                                BedrockText.lang("bedrock.roster.confirm.promote.title", viewer),
                                BedrockText.lang("bedrock.roster.confirm.promote.content", viewer, member.getName()),
                                () -> BedrockForms.runClanCommand(viewer, "promote " + member.getName(), reopen),
                                reopen));
            }
            if (member.isLeader() && plugin.getPermissionsManager().has(viewer, "simpleclans.leader.demote")) {
                menu.button(BedrockText.button("bedrock.roster.action.demote", viewer), "demote",
                        () -> BedrockForms.confirm(viewer,
                                BedrockText.lang("bedrock.roster.confirm.demote.title", viewer),
                                BedrockText.lang("bedrock.roster.confirm.demote.content", viewer, member.getName()),
                                () -> BedrockForms.runClanCommand(viewer, "demote " + member.getName(), reopen),
                                reopen));
            }
            if (!member.isLeader() && plugin.getPermissionsManager().has(viewer, "simpleclans.leader.settrust")) {
                menu.button(BedrockText.button(member.isTrusted()
                                ? "bedrock.roster.action.untrust" : "bedrock.roster.action.trust", viewer), "trust",
                        () -> BedrockForms.runClanCommand(viewer,
                                (member.isTrusted() ? "untrust " : "trust ") + member.getName(), reopen));
            }
        }
        menu.back(() -> open(frame, page));
        menu.send();
    }

    private static boolean canLead(@NotNull ClanPlayer viewerCp, @NotNull Clan clan) {
        return viewerCp.getClan() != null && viewerCp.getClan().equals(clan) && viewerCp.isLeader();
    }

    @NotNull
    private static String getMemberButton(@NotNull Player viewer, @NotNull ClanPlayer member) {
        return BedrockText.button("bedrock.roster.member.button", viewer,
                member.getName(),
                getRole(viewer, member),
                getStatus(viewer, member),
                KDRFormat.format(member.getKDR()));
    }

    @NotNull
    private static String getStatus(@NotNull Player viewer, @NotNull ClanPlayer member) {
        if (isOnline(viewer, member)) {
            return BedrockText.lang("bedrock.roster.status.online", viewer);
        }
        return BedrockText.lang("bedrock.roster.status.last-seen", viewer, member.getLastSeenString(viewer));
    }

    private static boolean isOnline(@NotNull Player viewer, @NotNull ClanPlayer member) {
        Player player = Bukkit.getPlayer(member.getUniqueId());
        return player != null && !VanishUtils.isVanished(viewer, player);
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
    private static List<ClanPlayer> getSortedMembers(@NotNull Player viewer, @NotNull Clan clan) {
        List<ClanPlayer> members = new ArrayList<>(clan.getMembers());
        members.sort(Comparator.comparing((ClanPlayer member) -> !isOnline(viewer, member))
                .thenComparing(BedrockRosterDrawer::memberWeight)
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
}
