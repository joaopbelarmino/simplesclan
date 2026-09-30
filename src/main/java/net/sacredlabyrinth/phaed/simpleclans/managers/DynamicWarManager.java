package net.sacredlabyrinth.phaed.simpleclans.managers;

import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.War;
import net.sacredlabyrinth.phaed.simpleclans.events.WarEndEvent;
import net.sacredlabyrinth.phaed.simpleclans.ui.bedrock.BedrockFormDrawer;
import net.sacredlabyrinth.phaed.simpleclans.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.*;

public class DynamicWarManager {

    private final SimpleClans plugin;
    private final List<DynamicWar> wars = new ArrayList<>();
    private final Random random = new Random();

    public DynamicWarManager(@NotNull SimpleClans plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void handleHit(@NotNull Player attacker, @NotNull Player victim, @NotNull Clan attackerClan,
                          @NotNull Clan victimClan, @NotNull Location hitLocation) {
        if (isDisabledWorld(hitLocation.getWorld())) {
            ChatBlock.sendMessageKey(attacker, "dynamic-war.disabled-world");
            return;
        }
        if (!canStart(attackerClan, victimClan)) {
            return;
        }

        DynamicWar attackerWar = getWar(attackerClan);
        DynamicWar victimWar = getWar(victimClan);
        DynamicWar war = attackerWar != null ? attackerWar : victimWar;
        if (war == null) {
            boolean alreadyWarring = attackerClan.isWarring(victimClan);
            war = new DynamicWar(hitLocation.clone());
            ClanPlayer attackerCp = plugin.getClanManager().getClanPlayer(attacker);
            if (!alreadyWarring) {
                if (attackerCp == null || !plugin.getProtectionManager().addWar(attackerCp, attackerClan, victimClan)) {
                    return;
                }
            }
            war.addParticipant(attackerClan);
            war.addParticipant(victimClan);
            war.addProtectionWar(new War(attackerClan, victimClan), !alreadyWarring);
            wars.add(war);
            announceStart(attacker, victim, attackerClan, victimClan);
        } else {
            if (attackerWar != null && victimWar != null && attackerWar != victimWar) {
                war = mergeWars(attackerWar, victimWar);
            }
            if (war.addPair(attacker, attackerClan, victimClan)) {
                announceStart(attacker, victim, attackerClan, victimClan);
            }
        }

        war.markHit();
    }

    public boolean canStart(@NotNull Clan attackerClan, @NotNull Clan victimClan) {
        return plugin.getSettingsManager().is(DYNAMIC_WAR_ENABLED)
                && !attackerClan.equals(victimClan)
                && !attackerClan.isAlly(victimClan.getTag())
                && attackerClan.isDynamicWarEnabled()
                && victimClan.isDynamicWarEnabled();
    }

    private boolean isDisabledWorld(@Nullable World world) {
        if (world == null) {
            return true;
        }
        for (String disabledWorld : plugin.getSettingsManager().getStringList(DYNAMIC_WAR_DISABLED_WORLDS)) {
            if (world.getName().equalsIgnoreCase(disabledWorld)) {
                return true;
            }
        }
        return false;
    }

    public boolean joinNearestWar(@NotNull Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayerUniqueId(player.getUniqueId());
        if (clan == null) {
            ChatBlock.sendMessageKey(player, "dynamic-war.not-in-clan");
            return false;
        }

        DynamicWar war = getWar(clan);
        if (war == null) {
            ChatBlock.sendMessageKey(player, "dynamic-war.none");
            return false;
        }

        ChatBlock.sendMessageKey(player, "dynamic-war.teleport-start",
                plugin.getSettingsManager().getInt(DYNAMIC_WAR_TELEPORT_DELAY));
        Location startLocation = player.getLocation().clone();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (hasMovedBlock(startLocation, player.getLocation())) {
                ChatBlock.sendMessageKey(player, "dynamic-war.teleport-cancelled-moved");
                return;
            }
            DynamicWar current = getWar(clan);
            if (current == null) {
                ChatBlock.sendMessageKey(player, "dynamic-war.none");
                return;
            }
            if (isDisabledWorld(player.getWorld()) || isDisabledWorld(current.getCenter().getWorld())) {
                ChatBlock.sendMessageKey(player, "dynamic-war.disabled-world");
                return;
            }
            Location target = findSafeLocation(current.getCenter(),
                    plugin.getSettingsManager().getInt(DYNAMIC_WAR_TELEPORT_RADIUS),
                    plugin.getSettingsManager().getInt(DYNAMIC_WAR_TELEPORT_MAX_ATTEMPTS));
            if (target == null) {
                ChatBlock.sendMessageKey(player, "dynamic-war.no-safe-location");
                return;
            }
            player.teleport(target);
            ChatBlock.sendMessageKey(player, "dynamic-war.teleported");
        }, plugin.getSettingsManager().getInt(DYNAMIC_WAR_TELEPORT_DELAY) * 20L);
        return true;
    }

    private boolean hasMovedBlock(@NotNull Location start, @NotNull Location current) {
        World startWorld = start.getWorld();
        World currentWorld = current.getWorld();
        if (startWorld == null || currentWorld == null || !startWorld.equals(currentWorld)) {
            return true;
        }
        return start.getBlockX() != current.getBlockX()
                || start.getBlockY() != current.getBlockY()
                || start.getBlockZ() != current.getBlockZ();
    }

    public boolean hasActiveWar(@NotNull Clan clan) {
        return getWar(clan) != null;
    }

    @Nullable
    private DynamicWar getWar(@NotNull Clan clan) {
        for (DynamicWar war : wars) {
            if (war.involves(clan)) {
                return war;
            }
        }
        return null;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<DynamicWar> iterator = wars.iterator();
        while (iterator.hasNext()) {
            DynamicWar war = iterator.next();
            long idleMillis = now - war.getLastHit();
            long endingMillis = plugin.getSettingsManager().getInt(DYNAMIC_WAR_IDLE_SECONDS) * 1000L;
            long endMillis = endingMillis + plugin.getSettingsManager().getInt(DYNAMIC_WAR_ENDING_SECONDS) * 1000L;

            if (idleMillis >= endMillis) {
                war.removeBossBar();
                war.endProtectionWars();
                iterator.remove();
                continue;
            }

            if (idleMillis >= endingMillis) {
                war.showEndingBar(endMillis - idleMillis);
            } else {
                war.showActiveBar(endingMillis - idleMillis, endingMillis);
            }
        }
    }

    @NotNull
    private DynamicWar mergeWars(@NotNull DynamicWar first, @NotNull DynamicWar second) {
        first.absorb(second);
        second.removeBossBar();
        wars.remove(second);
        return first;
    }

    private void announceStart(@NotNull Player attacker, @NotNull Player victim, @NotNull Clan attackerClan,
                               @NotNull Clan victimClan) {
        Bukkit.broadcastMessage(ChatUtils.parseColors(lang("dynamic-war.global", attacker, attackerClan.getColorTag(),
                victimClan.getColorTag())));
        attackerClan.clanAnnounce(attacker.getName(), lang("dynamic-war.attacker-clan", attacker,
                attacker.getName(), victimClan.getColorTag()));
        victimClan.clanAnnounce(victim.getName(), lang("dynamic-war.victim-clan", victim,
                victim.getName(), attackerClan.getColorTag()));
        sendWebhook(attacker, victim, attackerClan, victimClan);
    }

    private void sendWebhook(@NotNull Player attacker, @NotNull Player victim, @NotNull Clan attackerClan,
                             @NotNull Clan victimClan) {
        String webhook = plugin.getSettingsManager().getString(DYNAMIC_WAR_WEBHOOK_URL);
        if (webhook == null || webhook.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                byte[] body = buildWebhookBody(attacker, victim, attackerClan, victimClan)
                        .getBytes(StandardCharsets.UTF_8);
                HttpURLConnection connection = (HttpURLConnection) new URL(webhook).openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(body);
                }
                connection.getResponseCode();
                connection.disconnect();
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not send dynamic war webhook: " + ex.getMessage());
            }
        });
    }

    @NotNull
    private String buildWebhookBody(@NotNull Player attacker, @NotNull Player victim, @NotNull Clan attackerClan,
                                    @NotNull Clan victimClan) {
        String title = formatWebhookText(plugin.getSettingsManager().getString(DYNAMIC_WAR_WEBHOOK_TITLE),
                attacker, victim, attackerClan, victimClan);
        String description = formatWebhookText(plugin.getSettingsManager().getString(DYNAMIC_WAR_WEBHOOK_DESCRIPTION),
                attacker, victim, attackerClan, victimClan);
        String imageUrl = plugin.getSettingsManager().getString(DYNAMIC_WAR_WEBHOOK_IMAGE_URL);
        int color = parseWebhookColor(plugin.getSettingsManager().getString(DYNAMIC_WAR_WEBHOOK_COLOR));

        StringBuilder body = new StringBuilder("{\"embeds\":[{");
        body.append("\"title\":\"").append(json(title)).append("\",");
        body.append("\"description\":\"").append(json(description)).append("\",");
        body.append("\"color\":").append(color);
        if (imageUrl != null && !imageUrl.isEmpty()) {
            body.append(",\"image\":{\"url\":\"").append(json(imageUrl)).append("\"}");
        }
        body.append("}]}");
        return body.toString();
    }

    @NotNull
    private String formatWebhookText(@Nullable String text, @NotNull Player attacker, @NotNull Player victim,
                                     @NotNull Clan attackerClan, @NotNull Clan victimClan) {
        String value = text == null ? "" : text;
        value = value.replace("\\n", "\n")
                .replace("{attacker}", attacker.getName())
                .replace("{victim}", victim.getName())
                .replace("{attacker_clan}", ChatColor.stripColor(ChatUtils.parseColors(attackerClan.getName())))
                .replace("{victim_clan}", ChatColor.stripColor(ChatUtils.parseColors(victimClan.getName())))
                .replace("{attacker_tag}", ChatColor.stripColor(ChatUtils.parseColors(attackerClan.getColorTag())))
                .replace("{victim_tag}", ChatColor.stripColor(ChatUtils.parseColors(victimClan.getColorTag())));
        return ChatColor.stripColor(ChatUtils.parseColors(value));
    }

    private int parseWebhookColor(@Nullable String color) {
        if (color == null || color.isEmpty()) {
            return 0xff3333;
        }
        try {
            String value = color.trim().toLowerCase(Locale.ROOT);
            if (value.startsWith("#")) {
                return Integer.parseInt(value.substring(1), 16);
            }
            if (value.startsWith("0x")) {
                return Integer.parseInt(value.substring(2), 16);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0xff3333;
        }
    }

    @NotNull
    private String json(@NotNull String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    @Nullable
    private Location findSafeLocation(@NotNull Location center, int radius, int maxAttempts) {
        World world = center.getWorld();
        if (world == null) {
            return null;
        }
        int safeRadius = Math.max(1, radius);
        int attempts = Math.max(1, maxAttempts);
        for (int i = 0; i < attempts; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = random.nextDouble() * safeRadius;
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location location = new Location(world, x + 0.5, y, z + 0.5);
            if (isSafe(location)) {
                return location;
            }
        }
        return null;
    }

    private boolean isSafe(@NotNull Location location) {
        Material feet = location.getBlock().getType();
        Material head = location.clone().add(0, 1, 0).getBlock().getType();
        Material ground = location.clone().add(0, -1, 0).getBlock().getType();
        return !ground.isAir() && ground.isSolid() && feet.isAir() && head.isAir();
    }

    private final class DynamicWar {
        private final Location center;
        private final Set<Clan> participants = new HashSet<>();
        private final Map<War, Boolean> protectionWars = new HashMap<>();
        private long lastHit;
        private BossBar javaBossBar;
        private BossBar bedrockBossBar;

        private DynamicWar(@NotNull Location center) {
            this.center = center;
            markHit();
        }

        private boolean addPair(@NotNull Player requester, @NotNull Clan clanA, @NotNull Clan clanB) {
            War war = new War(clanA, clanB);
            if (protectionWars.containsKey(war)) {
                addParticipant(clanA);
                addParticipant(clanB);
                return false;
            }
            boolean alreadyWarring = clanA.isWarring(clanB);
            if (!alreadyWarring) {
                ClanPlayer requesterCp = plugin.getClanManager().getClanPlayer(requester);
                if (requesterCp == null || !plugin.getProtectionManager().addWar(requesterCp, clanA, clanB)) {
                    return false;
                }
            }
            addParticipant(clanA);
            addParticipant(clanB);
            addProtectionWar(war, !alreadyWarring);
            return true;
        }

        private void addParticipant(@NotNull Clan clan) {
            participants.add(clan);
        }

        private void addProtectionWar(@NotNull War war, boolean dynamicOwned) {
            protectionWars.put(war, dynamicOwned);
        }

        private void absorb(@NotNull DynamicWar other) {
            participants.addAll(other.participants);
            protectionWars.putAll(other.protectionWars);
            lastHit = Math.max(lastHit, other.lastHit);
        }

        private void markHit() {
            lastHit = System.currentTimeMillis();
        }

        private void showActiveBar(long remainingMillis, long totalMillis) {
            double progress = totalMillis <= 0 ? 1.0 : remainingMillis / (double) totalMillis;
            updateBossBars("dynamic-war.bossbar.active.java", "dynamic-war.bossbar.active.bedrock",
                    BarColor.BLUE, progress);
        }

        private void showEndingBar(long remainingMillis) {
            int endingSeconds = Math.max(1, plugin.getSettingsManager().getInt(DYNAMIC_WAR_ENDING_SECONDS));
            updateBossBars("dynamic-war.bossbar.ending.java", "dynamic-war.bossbar.ending.bedrock",
                    BarColor.RED, remainingMillis / (endingSeconds * 1000.0));
        }

        private void updateBossBars(@NotNull String javaMessage, @NotNull String bedrockMessage,
                                    @NotNull BarColor color, double progress) {
            javaBossBar = updateBossBar(javaBossBar, javaMessage, color, progress);
            bedrockBossBar = updateBossBar(bedrockBossBar, bedrockMessage, color, progress);
            javaBossBar.removeAll();
            bedrockBossBar.removeAll();
            for (ClanPlayer member : getParticipants()) {
                Player player = member.toPlayer();
                if (player == null) {
                    continue;
                }
                if (BedrockFormDrawer.isBedrockPlayer(player)) {
                    bedrockBossBar.addPlayer(player);
                } else {
                    javaBossBar.addPlayer(player);
                }
            }
        }

        @NotNull
        private BossBar updateBossBar(@Nullable BossBar bossBar, @NotNull String message, @NotNull BarColor color,
                                      double progress) {
            String title = ChatUtils.parseColors(lang(message));
            if (bossBar == null) {
                bossBar = Bukkit.createBossBar(title, color, BarStyle.SOLID);
            } else {
                bossBar.setTitle(title);
                bossBar.setColor(color);
            }
            bossBar.setProgress(Math.max(0, Math.min(1, progress)));
            return bossBar;
        }

        private List<ClanPlayer> getParticipants() {
            List<ClanPlayer> players = new ArrayList<>();
            for (Clan clan : participants) {
                players.addAll(clan.getMembers());
            }
            return players;
        }

        private void removeBossBar() {
            if (javaBossBar != null) {
                javaBossBar.removeAll();
                javaBossBar = null;
            }
            if (bedrockBossBar != null) {
                bedrockBossBar.removeAll();
                bedrockBossBar = null;
            }
        }

        private boolean involves(@NotNull Clan clan) {
            return participants.contains(clan);
        }

        private Location getCenter() {
            return center;
        }

        private long getLastHit() {
            return lastHit;
        }

        private void endProtectionWars() {
            for (Map.Entry<War, Boolean> entry : protectionWars.entrySet()) {
                if (entry.getValue()) {
                    plugin.getProtectionManager().removeWar(entry.getKey(), WarEndEvent.Reason.EXPIRATION);
                }
            }
        }
    }
}
