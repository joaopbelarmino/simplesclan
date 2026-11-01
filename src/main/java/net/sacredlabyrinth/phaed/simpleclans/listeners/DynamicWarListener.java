package net.sacredlabyrinth.phaed.simpleclans.listeners;

import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

public class DynamicWarListener extends SCListener {

    public DynamicWarListener(@NotNull SimpleClans plugin) {
        super(plugin);
    }

    /**
     * The only place where hits are counted, after every other plugin decided whether the damage happens
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Player attacker = Events.getAttacker(event);
        boolean byPlayer = attacker != null && !attacker.getUniqueId().equals(victim.getUniqueId());
        plugin.getDynamicWarManager().recordDamage(victim, byPlayer);
        if (!byPlayer || isBlacklistedWorld(victim)) {
            return;
        }
        Clan attackerClan = plugin.getClanManager().getClanByPlayerUniqueId(attacker.getUniqueId());
        Clan victimClan = plugin.getClanManager().getClanByPlayerUniqueId(victim.getUniqueId());
        if (attackerClan == null || victimClan == null) {
            return;
        }
        plugin.getDynamicWarManager().handleHit(attacker, victim, attackerClan, victimClan, victim.getLocation());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getDynamicWarManager().forget(event.getPlayer());
    }
}
