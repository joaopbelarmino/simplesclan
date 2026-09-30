package net.sacredlabyrinth.phaed.simpleclans.listeners;

import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;

public class DynamicWarListener extends SCListener {

    public DynamicWarListener(@NotNull SimpleClans plugin) {
        super(plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || isBlacklistedWorld(event.getEntity())) {
            return;
        }
        Player victim = (Player) event.getEntity();
        Player attacker = Events.getAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        Clan attackerClan = plugin.getClanManager().getClanByPlayerUniqueId(attacker.getUniqueId());
        Clan victimClan = plugin.getClanManager().getClanByPlayerUniqueId(victim.getUniqueId());
        if (attackerClan == null || victimClan == null) {
            return;
        }
        plugin.getDynamicWarManager().handleHit(attacker, victim, attackerClan, victimClan, victim.getLocation());
    }
}
