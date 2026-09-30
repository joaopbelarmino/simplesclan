package net.sacredlabyrinth.phaed.simpleclans.commands.general;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.bukkit.entity.Player;

@CommandAlias("guerra")
public class DynamicWarCommand extends BaseCommand {

    @CommandPermission("simpleclans.member.dynamicwar")
    @Default
    @Description("{@@command.description.dynamic-war.join}")
    public void join(Player player) {
        SimpleClans.getInstance().getDynamicWarManager().joinNearestWar(player);
    }
}
