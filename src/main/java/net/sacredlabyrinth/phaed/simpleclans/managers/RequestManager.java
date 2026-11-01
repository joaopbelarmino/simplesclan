package net.sacredlabyrinth.phaed.simpleclans.managers;

import net.sacredlabyrinth.phaed.simpleclans.*;
import net.sacredlabyrinth.phaed.simpleclans.events.RequestEvent;
import net.sacredlabyrinth.phaed.simpleclans.events.RequestFinishedEvent;
import net.sacredlabyrinth.phaed.simpleclans.events.WarEndEvent;
import net.sacredlabyrinth.phaed.simpleclans.utils.ChatUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.*;
import static org.bukkit.ChatColor.RED;

/**
 * @author phaed
 */
public final class RequestManager {
    private final SimpleClans plugin;
    private final HashMap<String, Request> requests = new HashMap<>();
    // Concurrent: the asker task reads it from another thread
    private final Map<String, Request> joinRequests = new ConcurrentHashMap<>();

    /**
     *
     */
    public RequestManager() {
        plugin = SimpleClans.getInstance();
        askerTask();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean hasRequest(String tag) {
        return requests.containsKey(tag);
    }

    public void addDemoteRequest(ClanPlayer requester, String demotedName, Clan clan) {
        if (requests.containsKey(clan.getTag())) {
            return;
        }
        String msg = MessageFormat.format(lang("asking.for.the.demotion"), requester.getName(), demotedName);

        ClanPlayer demotedTp = plugin.getClanManager().getAnyClanPlayer(demotedName);

        List<ClanPlayer> acceptors = Helper.stripOffLinePlayers(clan.getLeaders());
        acceptors.remove(demotedTp);

        Request req = new Request(ClanRequest.DEMOTE, acceptors, requester, demotedName, clan, msg);
        req.vote(requester.getName(), VoteResult.ACCEPT);
        requests.put(req.getClan().getTag(), req);
        ask(req);
    }

    /**
     * This method asks <i>all</i> leaders about
     * some action <i>inside</i> their clan.
     * <p>
     * Example of possible requests:
     * </p>
     * <ul>
     *     <li>Disband request can be asked from all leaders</li>
     *     <li>Rename request can be asked from all leaders</li>
     *     <li>Promote request can be asked from all leaders</li>
     * </ul>
     *
     * <p>
     * Examples of incompatible requests:
     * </p>
     * <ul>
     *      <li>Demote request can be asked from all leaders,
     *      <b>except the demoted one.</b></li>
     *      <li>Invite request has to ask someone <b>outside</b> of leaders clan</li>
     * </ul>
     *
     * @param requester the clan player, who sent the request
     * @param request   the type of request, see: {@link ClanRequest}
     * @param target    the target which will be used in request processing
     * @param key       the language key that would be translated and send the message to all leaders
     * @param args      the language objects, requires in some language strings.
     * @throws IllegalArgumentException if passed incompatible request
     */
    public void requestAllLeaders(@NotNull ClanPlayer requester, @NotNull ClanRequest request,
                                  @NotNull String target, @NotNull String key, @Nullable Object... args) {
        if (request.equals(ClanRequest.INVITE) || request.equals(ClanRequest.DEMOTE)) {
            throw new IllegalArgumentException("Unsupported request: " + request.name());
        }

        Clan clan = requester.getClan();
        if (clan == null || requests.containsKey(clan.getTag())) {
            return;
        }

        String msg = lang(key, args);
        List<ClanPlayer> acceptors = Helper.stripOffLinePlayers(clan.getLeaders());

        Request req = new Request(request, acceptors, requester, target, clan, msg);
        requests.put(clan.getTag(), req);
        req.vote(requester.getName(), VoteResult.ACCEPT);

        ask(req);
    }

    /**
     * Add a member invite request
     *
     * @param requester   the requester
     * @param invitedName the invited Player
     * @param clan        the Clan
     */
    public void addInviteRequest(ClanPlayer requester, String invitedName, Clan clan) {
        if (requests.containsKey(invitedName.toLowerCase())) {
            return;
        }
        Player player = Bukkit.getPlayer(invitedName);
        if (player == null) {
            return;
        }

        String msg = lang("inviting.you.to.join", player, requester.getName(), clan.getName());
        Request req = new Request(ClanRequest.INVITE, null, requester, invitedName, clan, msg);
        requests.put(invitedName.toLowerCase(), req);
        ask(req);
    }

    /**
     * Join requests live apart from the other requests (keyed by clan tag), so one pending application does not
     * block alliances, wars or other applications to the same clan
     */
    public void addJoinRequest(@NotNull ClanPlayer requester, @NotNull Clan clan) {
        Player player = requester.toPlayer();
        if (player == null || requester.getClan() != null) {
            return;
        }

        if (!clan.isJoinRequestsEnabled()) {
            ChatBlock.sendMessageKey(player, "join.request.disabled", clan.getName());
            return;
        }

        String key = joinKey(clan, requester.getName());
        if (joinRequests.containsKey(key)) {
            ChatBlock.sendMessageKey(player, "join.request.already.pending", clan.getName());
            return;
        }

        List<ClanPlayer> acceptors = Helper.stripOffLinePlayers(clan.getLeaders());
        if (acceptors.isEmpty()) {
            ChatBlock.sendMessageKey(player, "join.request.no.leaders.online", clan.getName());
            return;
        }

        String msg = lang("asking.to.join.clan", player, requester.getName(), clan.getName());
        Request req = new Request(ClanRequest.JOIN, acceptors, requester, requester.getName(), clan, msg);
        joinRequests.put(key, req);
        askJoin(req);
        ChatBlock.sendMessageKey(player, "join.request.sent", clan.getName());
    }

    public boolean isClanJoinRequestAvailable(@NotNull Clan clan) {
        return clan.isJoinRequestsEnabled() && hasLeaderOnline(clan);
    }

    public boolean hasLeaderOnline(@NotNull Clan clan) {
        return !Helper.stripOffLinePlayers(clan.getLeaders()).isEmpty();
    }

    @Nullable
    public Request getInviteRequestFor(@NotNull Player player) {
        Request request = requests.get(player.getName().toLowerCase());
        return request != null && request.getType() == ClanRequest.INVITE ? request : null;
    }

    @NotNull
    public List<Request> getJoinRequestsFor(@NotNull Clan clan) {
        List<Request> result = new ArrayList<>();
        for (Request request : joinRequests.values()) {
            if (request.getClan().equals(clan)) {
                result.add(request);
            }
        }
        result.sort(Comparator.comparing(Request::getTarget, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /**
     * Accepts or denies one specific application. Any leader of the clan can decide it.
     *
     * @return false if there was no such application
     */
    public boolean decideJoinRequest(@NotNull ClanPlayer leader, @NotNull String applicant, boolean accept) {
        Clan clan = leader.getClan();
        Player leaderPlayer = leader.toPlayer();
        if (clan == null || !leader.isLeader()) {
            return false;
        }
        Request req = joinRequests.remove(joinKey(clan, applicant));
        if (req == null) {
            if (leaderPlayer != null) {
                ChatBlock.sendMessageKey(leaderPlayer, "join.request.not.found", applicant);
            }
            return false;
        }
        processJoin(req, clan, leader.getName(), accept);
        Bukkit.getPluginManager().callEvent(new RequestFinishedEvent(req));
        return true;
    }

    private void processJoin(@NotNull Request req, @NotNull Clan clan, @NotNull String leaderName, boolean accept) {
        Player joining = Bukkit.getPlayerExact(req.getTarget());
        if (!accept) {
            if (joining != null) {
                ChatBlock.sendMessageKey(joining, "join.request.denied", clan.getName());
            }
            clan.leaderAnnounce(RED + lang("join.request.denied.leaders", leaderName, req.getTarget()));
            return;
        }
        if (joining == null) {
            clan.leaderAnnounce(RED + lang("join.request.applicant.offline", req.getTarget()));
            return;
        }

        ClanPlayer cp = plugin.getClanManager().getCreateClanPlayer(joining.getUniqueId());
        if (cp.getClan() != null) {
            clan.leaderAnnounce(RED + lang("join.request.applicant.has.clan", req.getTarget()));
            return;
        }

        int maxMembers = !clan.isVerified() ? plugin.getSettingsManager().getInt(CLAN_UNVERIFIED_MAX_MEMBERS)
                : plugin.getSettingsManager().getInt(CLAN_MAX_MEMBERS);
        if (maxMembers > 0 && clan.getSize() >= maxMembers) {
            ChatBlock.sendMessageKey(joining, "this.clan.has.reached.the.member.limit");
            clan.leaderAnnounce(RED + lang("this.clan.has.reached.the.member.limit"));
            return;
        }

        ChatBlock.sendMessageKey(joining, "join.request.accepted", clan.getName());
        clan.addBb(lang("joined.the.clan", joining.getName()));
        plugin.getClanManager().serverAnnounce(lang("has.joined", joining.getName(), clan.getName()));
        clan.addPlayerToClan(cp);
        // Other clans' pending applications from this player are now pointless
        joinRequests.values().removeIf(other -> other.getTarget().equalsIgnoreCase(joining.getName()));
    }

    /**
     * Sends the application to the clan leaders that are online, with buttons for this specific player
     */
    private void askJoin(@NotNull Request req) {
        String clanCommand = plugin.getSettingsManager().getString(COMMANDS_CLAN);
        for (ClanPlayer leader : req.getClan().getLeaders()) {
            Player recipient = leader.toPlayer();
            if (recipient == null) {
                continue;
            }
            ComponentBuilder builder = new ComponentBuilder("");
            builder.append(TextComponent.fromLegacyText(ChatUtils.parseColors(lang("join.request.message", recipient,
                    req.getClan().getColorTag(), req.getMsg()))));
            builder.append(" ").reset();
            builder.append(TextComponent.fromLegacyText(ChatUtils.parseColors(lang("clickable.accept", recipient))))
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + clanCommand + " joinaccept " + req.getTarget()));
            builder.append(" ").reset();
            builder.append(TextComponent.fromLegacyText(ChatUtils.parseColors(lang("clickable.deny", recipient))))
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + clanCommand + " joindeny " + req.getTarget()));
            recipient.spigot().sendMessage(builder.create());
        }
    }

    @NotNull
    private static String joinKey(@NotNull Clan clan, @NotNull String applicant) {
        return clan.getTag().toLowerCase(Locale.ROOT) + ":" + applicant.toLowerCase(Locale.ROOT);
    }

    public void addWarStartRequest(ClanPlayer requester, Clan warClan, Clan requestingClan) {
        if (requests.containsKey(warClan.getTag())) {
            return;
        }
        String msg = MessageFormat.format(lang("proposing.war"), requestingClan.getName(), ChatUtils.stripColors(warClan.getColorTag()));

        List<ClanPlayer> acceptors = Helper.stripOffLinePlayers(warClan.getLeaders());
        acceptors.remove(requester);

        Request req = new Request(ClanRequest.START_WAR, acceptors, requester, warClan.getTag(), requestingClan, msg);
        requests.put(req.getTarget(), req);
        ask(req);
    }

    public void addWarEndRequest(ClanPlayer requester, Clan warClan, Clan requestingClan) {
        if (requests.containsKey(warClan.getTag())) {
            return;
        }
        String msg = MessageFormat.format(lang("proposing.to.end.the.war"), requestingClan.getName(), ChatUtils.stripColors(warClan.getColorTag()));

        List<ClanPlayer> acceptors = Helper.stripOffLinePlayers(warClan.getLeaders());
        acceptors.remove(requester);

        Request req = new Request(ClanRequest.END_WAR, acceptors, requester, warClan.getTag(), requestingClan, msg);
        requests.put(req.getTarget(), req);
        ask(req);
    }

    public void addAllyRequest(ClanPlayer requester, Clan allyClan, Clan requestingClan) {
        if (requests.containsKey(allyClan.getTag())) {
            return;
        }
        String msg = MessageFormat.format(lang("proposing.an.alliance"), requestingClan.getName(), ChatUtils.stripColors(allyClan.getColorTag()));

        List<ClanPlayer> acceptors = Helper.stripOffLinePlayers(allyClan.getLeaders());
        acceptors.remove(requester);

        Request req = new Request(ClanRequest.CREATE_ALLY, acceptors, requester, allyClan.getTag(), requestingClan, msg);
        requests.put(req.getTarget(), req);
        ask(req);
    }

    public void addRivalryBreakRequest(ClanPlayer requester, Clan rivalClan, Clan requestingClan) {
        if (requests.containsKey(rivalClan.getTag())) {
            return;
        }
        String msg = MessageFormat.format(lang("proposing.to.end.the.rivalry"), requestingClan.getName(), ChatUtils.stripColors(rivalClan.getColorTag()));

        List<ClanPlayer> acceptors = Helper.stripOffLinePlayers(rivalClan.getLeaders());
        acceptors.remove(requester);

        Request req = new Request(ClanRequest.BREAK_RIVALRY, acceptors, requester, rivalClan.getTag(), requestingClan, msg);
        requests.put(req.getTarget(), req);
        ask(req);
    }

    public void accept(ClanPlayer cp) {
        Request req = requests.get(cp.getTag());

        if (req != null) {
            req.vote(cp.getName(), VoteResult.ACCEPT);
            processResults(req);
        } else {
            req = requests.get(cp.getCleanName());

            if (req != null) {
                processInvite(req, VoteResult.ACCEPT);
            } else {
                decideSingleJoinRequest(cp, true);
            }
        }
    }

    public void deny(ClanPlayer cp) {
        Request req = requests.get(cp.getTag());

        if (req != null) {
            req.vote(cp.getName(), VoteResult.DENY);
            processResults(req);
        } else {
            req = requests.get(cp.getCleanName());

            if (req != null) {
                processInvite(req, VoteResult.DENY);
            } else {
                decideSingleJoinRequest(cp, false);
            }
        }
    }

    /**
     * Keeps /accept and /deny working for applications: with a single one pending it is decided, with more the
     * leader is told to pick one
     */
    private void decideSingleJoinRequest(@NotNull ClanPlayer leader, boolean accept) {
        Clan clan = leader.getClan();
        if (clan == null || !leader.isLeader()) {
            return;
        }
        List<Request> pending = getJoinRequestsFor(clan);
        if (pending.size() == 1) {
            decideJoinRequest(leader, pending.get(0).getTarget(), accept);
            return;
        }
        Player player = leader.toPlayer();
        if (player != null && !pending.isEmpty()) {
            String names = pending.stream().map(Request::getTarget).collect(Collectors.joining(", "));
            String clanCommand = plugin.getSettingsManager().getString(COMMANDS_CLAN);
            ChatBlock.sendMessageKey(player, "join.request.choose", names, "/" + clanCommand + " joinaccept",
                    "/" + clanCommand + " joindeny");
        }
    }

    public void processInvite(Request req, VoteResult vote) {
        requests.remove(req.getTarget().toLowerCase());

        Clan clan = req.getClan();
        Player invited = Bukkit.getPlayerExact(req.getTarget());
        if (invited == null) {
            return;
        }

        if (vote.equals(VoteResult.ACCEPT)) {
            ClanPlayer cp = plugin.getClanManager().getCreateClanPlayer(invited.getUniqueId());
            int maxMembers = !clan.isVerified() ? plugin.getSettingsManager().getInt(CLAN_UNVERIFIED_MAX_MEMBERS) : plugin.getSettingsManager().getInt(CLAN_MAX_MEMBERS);

            if (maxMembers > 0 && maxMembers > clan.getSize()) {
                ChatBlock.sendMessageKey(invited, "accepted.invitation", clan.getName());
                clan.addBb(lang("joined.the.clan", invited.getName()));
                plugin.getClanManager().serverAnnounce(lang("has.joined", invited.getName(), clan.getName()));
                clan.addPlayerToClan(cp);
            } else {
                ChatBlock.sendMessageKey(invited, "this.clan.has.reached.the.member.limit");
            }
        } else {
            ChatBlock.sendMessageKey(invited, "denied.invitation", clan.getName());
            clan.leaderAnnounce(RED + lang("membership.invitation", invited.getName()));
        }
    }


    public void processResults(Request req) {
        Clan requestClan = req.getClan();
        ClanPlayer requester = req.getRequester();

        String target = req.getTarget();

        @Nullable
        Clan targetClan = plugin.getClanManager().getClan(target);

        ClanPlayer targetCp = plugin.getClanManager().getAnyClanPlayer(target);
        @Nullable
        UUID targetUuid = targetCp != null ? targetCp.getUniqueId() : null;

        List<String> accepts = req.getAccepts();
        List<String> denies = req.getDenies();

        switch (req.getType()) {
            case START_WAR:
                processStartWar(requester, requestClan, targetClan, accepts, denies);
                break;
            case END_WAR:
                processEndWar(requester, requestClan, targetClan, accepts, denies);
                break;
            case CREATE_ALLY:
                processCreateAlly(requester, requestClan, targetClan, accepts, denies);
                break;
            case BREAK_RIVALRY:
                processBreakRivalry(requester, requestClan, targetClan, accepts, denies);
                break;
            case DEMOTE:
            case PROMOTE:
                if (!req.votingFinished() || targetUuid == null) {
                    return;
                }
                target = requestClan.getTag();

                if (req.getType() == ClanRequest.DEMOTE) {
                    processDemote(req, requestClan, targetUuid, denies);
                }
                if (req.getType() == ClanRequest.PROMOTE) {
                    processPromote(req, requestClan, targetUuid, denies);
                }
                break;
            case DISBAND:
                if (!req.votingFinished()) {
                    return;
                }
                processDisband(requester, requestClan, denies);
                break;
            case RENAME:
                if (!req.votingFinished()) {
                    return;
                }

                processRename(req);
                break;
            default:
                return;
        }

        requests.remove(target);
        SimpleClans.getInstance().getServer().getPluginManager().callEvent(new RequestFinishedEvent(req));
        req.cleanVotes();
    }

    private void processRename(Request request) {
        if (request.getDenies().isEmpty()) {
            request.getClan().setName(request.getTarget());
        } else {
            String deniers = String.join(", ", request.getDenies());
            request.getClan().leaderAnnounce(RED + lang("rename.refused", deniers));
        }
    }

    private void processDisband(ClanPlayer requester, Clan requestClan, List<String> denies) {
        if (denies.isEmpty()) {
            requestClan.disband(requester.toPlayer(), true, false);
        } else {
            String deniers = String.join(", ", denies);
            requestClan.leaderAnnounce(RED + lang("clan.deletion", deniers));
        }
    }

    private void processPromote(Request req, Clan requestClan, UUID targetPlayer, List<String> denies) {
        String promotedName = req.getTarget();
        if (denies.isEmpty()) {
            requestClan.addBb(lang("leaders"), lang("promoted.to.leader", promotedName));
            requestClan.promote(targetPlayer);
        } else {
            String deniers = String.join(", ", denies);
            requestClan.leaderAnnounce(RED + lang("denied.the.promotion", deniers, promotedName));
        }
    }

    private void processDemote(Request req, Clan requestClan, UUID targetPlayer, List<String> denies) {
        String demotedName = req.getTarget();
        if (denies.isEmpty()) {
            requestClan.addBb(lang("leaders"), lang("demoted.back.to.member", demotedName));
            requestClan.demote(targetPlayer);
        } else {
            String deniers = String.join(", ", denies);
            requestClan.leaderAnnounce(
                    RED + lang("denied.demotion", deniers, demotedName));
        }
    }

    private void processBreakRivalry(ClanPlayer requester, Clan requestClan, @Nullable Clan targetClan,
                                     List<String> accepts, List<String> denies) {
        if (targetClan != null && requestClan != null) {
            if (!accepts.isEmpty()) {
                requestClan.removeRival(targetClan);
                targetClan.addBb(requester.getName(), lang("broken.the.rivalry", accepts.get(0), requestClan.getName()));
                requestClan.addBb(requester.getName(), lang("broken.the.rivalry.with", requester.getName(), targetClan.getName()));
            } else {
                targetClan.addBb(requester.getName(), lang("denied.to.make.peace", denies.get(0), requestClan.getName()));
                requestClan.addBb(requester.getName(), lang("peace.agreement.denied", targetClan.getName()));
            }
        }
    }

    private void processCreateAlly(ClanPlayer requester, Clan requestClan, @Nullable Clan targetClan,
                                   List<String> accepts, List<String> denies) {
        if (targetClan != null && requestClan != null) {
            if (!accepts.isEmpty()) {
                requestClan.addAlly(targetClan);

                targetClan.addBb(requester.getName(), lang("accepted.an.alliance", accepts.get(0), requestClan.getName()));
                requestClan.addBb(requester.getName(), lang("created.an.alliance", requester.getName(), targetClan.getName()));
            } else {
                targetClan.addBb(requester.getName(), lang("denied.an.alliance", denies.get(0), requestClan.getName()));
                requestClan.addBb(requester.getName(), lang("the.alliance.was.denied", targetClan.getName()));
            }
        }
    }

    private void processEndWar(ClanPlayer requester, Clan requestClan, @Nullable Clan targetClan, List<String> accepts,
                               List<String> denies) {
        if (requestClan != null && targetClan != null) {
            if (!accepts.isEmpty()) {
                War war = plugin.getProtectionManager().getWar(requestClan, targetClan);
                plugin.getProtectionManager().removeWar(war, WarEndEvent.Reason.REQUEST);
                requestClan.removeWarringClan(targetClan);
                targetClan.removeWarringClan(requestClan);

                targetClan.addBb(requester.getName(), lang("you.are.no.longer.at.war", accepts.get(0), requestClan.getColorTag()));
                requestClan.addBb(requester.getName(), lang("you.are.no.longer.at.war", requestClan.getName(), targetClan.getColorTag()));
            } else {
                targetClan.addBb(requester.getName(), lang("denied.war.end", denies.get(0), requestClan.getName()));
                requestClan.addBb(requester.getName(), lang("end.war.denied", targetClan.getName()));
            }
        }
    }

    private void processStartWar(ClanPlayer requester, Clan requestClan, @Nullable Clan targetClan,
                                 List<String> accepts, List<String> denies) {
        if (requestClan != null && targetClan != null) {
            if (!accepts.isEmpty()) {
                plugin.getProtectionManager().addWar(requester, requestClan, targetClan);
            } else {
                targetClan.addBb(requester.getName(), lang("denied.war.req", denies.get(0),
                        requestClan.getName()));
                requestClan.addBb(requester.getName(), lang("end.war.denied",
                        targetClan.getName()));
            }
        }
    }

    /**
     * End a pending request prematurely
     *
     * @param playerName the Player signing off
     */
    public void endPendingRequest(String playerName) {
        for (Request req : new LinkedList<>(requests.values())) {
            for (ClanPlayer cp : req.getAcceptors()) {
                if (cp.getName().equalsIgnoreCase(playerName)) {
                    req.getClan().leaderAnnounce(lang("signed.off.request.cancelled", RED + playerName, req.getType()));
                    requests.remove(req.getClan().getTag());
                    break;
                }
            }
        }

    }

    public void removeRequest(@NotNull String keyOrTarget) {
        Iterator<Map.Entry<String, Request>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Request> entry = iterator.next();
            final String requester = entry.getKey();
            final String target = entry.getValue().getTarget();
            if (keyOrTarget.equals(requester) || keyOrTarget.equals(target)) {
                entry.getValue().cleanVotes();
                iterator.remove();
            }
        }
    }

    /**
     * Starts the task that asks for the votes of all requests
     */
    public void askerTask() {
        new BukkitRunnable() {

            @Override
            public void run() {
                for (Iterator<Map.Entry<String, Request>> iter = requests.entrySet().iterator(); iter.hasNext(); ) {
                    Request req = iter.next().getValue();

                    if (req == null) {
                        continue;
                    }

                    if (req.reachedRequestLimit()) {
                        iter.remove();
                    }

                    ask(req);
                    req.incrementAskCount();
                }

                for (Iterator<Request> iter = joinRequests.values().iterator(); iter.hasNext(); ) {
                    Request req = iter.next();
                    if (req.reachedRequestLimit()) {
                        iter.remove();
                        Player applicant = Bukkit.getPlayerExact(req.getTarget());
                        if (applicant != null) {
                            ChatBlock.sendMessageKey(applicant, "join.request.expired", req.getClan().getName());
                        }
                        continue;
                    }
                    askJoin(req);
                    req.incrementAskCount();
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0, plugin.getSettingsManager().getSeconds(REQUEST_FREQUENCY));
    }

    /**
     * Asks a request to players for votes
     *
     * @param req the Request
     */
    public void ask(final Request req) {
        String message = lang("request.message", req.getClan().getColorTag(), req.getMsg());
        ArrayList<Player> recipients = new ArrayList<>();
        if (req.getType() == ClanRequest.INVITE) {
            recipients.add(Bukkit.getPlayerExact(req.getTarget()));
        } else {
            for (ClanPlayer cp : req.getAcceptors()) {
                if (cp.getVote() == null) {
                    recipients.add(cp.toPlayer());
                }
            }
        }

        for (Player recipient : recipients) {
            if (recipient != null) {
                recipient.spigot().sendMessage(ChatUtils.toBaseComponents(recipient, message));
            }
        }

        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().callEvent(new RequestEvent(req)));
    }
}
