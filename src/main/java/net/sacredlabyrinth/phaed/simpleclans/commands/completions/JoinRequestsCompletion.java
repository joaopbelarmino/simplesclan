package net.sacredlabyrinth.phaed.simpleclans.commands.completions;

import co.aikar.commands.BukkitCommandCompletionContext;
import co.aikar.commands.InvalidCommandArgument;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.Request;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class JoinRequestsCompletion extends AbstractSyncCompletion {

    public JoinRequestsCompletion(@NotNull SimpleClans plugin) {
        super(plugin);
    }

    @Override
    public Collection<String> getCompletions(BukkitCommandCompletionContext c) throws InvalidCommandArgument {
        Player player = c.getPlayer();
        if (player != null) {
            Clan clan = clanManager.getClanByPlayerUniqueId(player.getUniqueId());
            if (clan != null) {
                return plugin.getRequestManager().getJoinRequestsFor(clan).stream().map(Request::getTarget)
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    @Override
    public @NotNull String getId() {
        return "join_requests";
    }
}
