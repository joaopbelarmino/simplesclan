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
import org.bukkit.block.Block;
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

    private static final int VERTICAL_SEARCH = 12;
    private static final int NETHER_ROOF_LIMIT = 125;
    private static final Set<String> UNSAFE_GROUND = new HashSet<>(Arrays.asList(
            "LAVA", "MAGMA_BLOCK", "CACTUS", "CAMPFIRE", "SOUL_CAMPFIRE", "FIRE", "SOUL_FIRE",
            "SWEET_BERRY_BUSH", "POWDER_SNOW", "POINTED_DRIPSTONE", "WITHER_ROSE", "BEDROCK"));

    private final SimpleClans plugin;
    private final List<DynamicWar> wars = new ArrayList<>();
    private final Map<String, Long> lastGlobalAnnounce = new HashMap<>();
    private final Map<UUID, Long> teleportCooldowns = new HashMap<>();
    private final Set<UUID> pendingTeleports = new HashSet<>();
    private final Random random = new Random();

    public DynamicWarManager(@NotNull SimpleClans plugin) {
        this.plugin = plugin;
        cleanupLeftoverWars();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /**
     * Handles a hit between members of two different clans, starting or extending a dynamic war.
     *
     * @return true if the clans are at war after this hit
     */
    public boolean handleHit(@NotNull Player attacker, @NotNull Player victim, @NotNull Clan attackerClan,
                             @NotNull Clan victimClan, @NotNull Location hitLocation) {
        if (!canStart(attackerClan, victimClan) || isDisabledWorld(hitLocation.getWorld())) {
            return false;
        }

        DynamicWar attackerWar = getWar(attackerClan);
        DynamicWar victimWar = getWar(victimClan);
        DynamicWar war;
        if (attackerWar != null && victimWar != null && attackerWar != victimWar) {
            war = mergeWars(attackerWar, victimWar);
        } else {
            war = attackerWar != null ? attackerWar : victimWar;
        }

        boolean newWar = war == null;
        if (newWar) {
            war = new DynamicWar(hitLocation);
        }

        PairResult result = war.addPair(attackerClan, victimClan);
        if (result == PairResult.FAILED) {
            return false;
        }
        if (newWar) {
            wars.add(war);
        }
        war.markHit(hitLocation);
        if (result == PairResult.ADDED) {
            announceStart(attacker, victim, attackerClan, victimClan);
        }
        return true;
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
        if (isDisabledWorld(player.getWorld()) || isDisabledWorld(war.getCenter().getWorld())) {
            ChatBlock.sendMessageKey(player, "dynamic-war.disabled-world");
            return false;
        }

        UUID uniqueId = player.getUniqueId();
        if (pendingTeleports.contains(uniqueId)) {
            ChatBlock.sendMessageKey(player, "dynamic-war.teleport-pending");
            return false;
        }
        long cooldownLeft = getTeleportCooldownSeconds(uniqueId);
        if (cooldownLeft > 0) {
            ChatBlock.sendMessageKey(player, "dynamic-war.teleport-cooldown", cooldownLeft);
            return false;
        }

        int delay = Math.max(0, plugin.getSettingsManager().getInt(DYNAMIC_WAR_TELEPORT_DELAY));
        ChatBlock.sendMessageKey(player, "dynamic-war.teleport-start", delay);
        Location startLocation = player.getLocation().clone();
        pendingTeleports.add(uniqueId);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingTeleports.remove(uniqueId);
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
            if (player.teleport(target)) {
                long cooldown = Math.max(0, plugin.getSettingsManager().getInt(DYNAMIC_WAR_TELEPORT_COOLDOWN));
                teleportCooldowns.put(uniqueId, System.currentTimeMillis() + cooldown * 1000L);
                ChatBlock.sendMessageKey(player, "dynamic-war.teleported");
            }
        }, delay * 20L);
        return true;
    }

    private long getTeleportCooldownSeconds(@NotNull UUID uniqueId) {
        Long until = teleportCooldowns.get(uniqueId);
        if (until == null) {
            return 0;
        }
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            teleportCooldowns.remove(uniqueId);
            return 0;
        }
        return (left + 999) / 1000;
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

    /**
     * Ends every dynamic war, so no clan is left at war when the server stops.
     */
    public void shutdown() {
        for (DynamicWar war : wars) {
            war.removeBossBars();
            war.endOwnedWars();
        }
        wars.clear();
        pendingTeleports.clear();
    }

    /**
     * Wars created by dynamic wars are persisted in the clans. If the server stopped without
     * {@link #shutdown()} (crash, kill), they would stay forever, so they are ended on startup.
     */
    private void cleanupLeftoverWars() {
        int cleaned = 0;
        for (Clan clan : plugin.getClanManager().getClans()) {
            for (String tag : clan.getDynamicWarringTags()) {
                Clan other = plugin.getClanManager().getClan(tag);
                if (other != null) {
                    endOwnedWar(clan, other);
                } else {
                    clan.removeDynamicWarring(tag);
                }
                cleaned++;
            }
        }
        if (cleaned > 0) {
            plugin.getLogger().info("Ended " + cleaned + " leftover dynamic war(s) from the last session.");
        }
    }

    private void endOwnedWar(@NotNull Clan clan, @NotNull Clan other) {
        clan.removeDynamicWarring(other.getTag());
        other.removeDynamicWarring(clan.getTag());
        War war = plugin.getProtectionManager().getWar(clan, other);
        if (war != null) {
            plugin.getProtectionManager().removeWar(war, WarEndEvent.Reason.EXPIRATION);
        } else {
            clan.removeWarringClan(other);
            other.removeWarringClan(clan);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long endingMillis = plugin.getSettingsManager().getInt(DYNAMIC_WAR_IDLE_SECONDS) * 1000L;
        long endMillis = endingMillis + plugin.getSettingsManager().getInt(DYNAMIC_WAR_ENDING_SECONDS) * 1000L;

        Iterator<DynamicWar> iterator = wars.iterator();
        while (iterator.hasNext()) {
            DynamicWar war = iterator.next();
            long idleMillis = now - war.getLastHit();

            if (idleMillis >= endMillis) {
                war.removeBossBars();
                war.endOwnedWars();
                iterator.remove();
                continue;
            }

            if (idleMillis >= endingMillis) {
                war.showEndingBar(endMillis - idleMillis);
            } else {
                war.showActiveBar(endingMillis - idleMillis, endingMillis);
            }
        }

        long announceCooldown = plugin.getSettingsManager().getInt(DYNAMIC_WAR_ANNOUNCE_COOLDOWN) * 1000L;
        lastGlobalAnnounce.values().removeIf(time -> now - time >= announceCooldown);
        teleportCooldowns.values().removeIf(until -> until <= now);
    }

    @NotNull
    private DynamicWar mergeWars(@NotNull DynamicWar first, @NotNull DynamicWar second) {
        first.absorb(second);
        second.removeBossBars();
        wars.remove(second);
        return first;
    }

    private void announceStart(@NotNull Player attacker, @NotNull Player victim, @NotNull Clan attackerClan,
                               @NotNull Clan victimClan) {
        attackerClan.clanAnnounce(attacker.getName(), lang("dynamic-war.attacker-clan", attacker,
                attacker.getName(), victimClan.getColorTag()));
        victimClan.clanAnnounce(victim.getName(), lang("dynamic-war.victim-clan", victim,
                victim.getName(), attackerClan.getColorTag()));

        if (!shouldAnnounceGlobally(attackerClan, victimClan)) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(ChatUtils.parseColors(lang("dynamic-war.global", online,
                    attackerClan.getColorTag(), victimClan.getColorTag())));
        }
        Bukkit.getConsoleSender().sendMessage(ChatUtils.parseColors(lang("dynamic-war.global", (Player) null,
                attackerClan.getColorTag(), victimClan.getColorTag())));
        sendWebhook(attacker, victim, attackerClan, victimClan);
    }

    /**
     * The same pair of clans only triggers a new global message (and webhook) after the cooldown.
     */
    private boolean shouldAnnounceGlobally(@NotNull Clan first, @NotNull Clan second) {
        long now = System.currentTimeMillis();
        long cooldown = plugin.getSettingsManager().getInt(DYNAMIC_WAR_ANNOUNCE_COOLDOWN) * 1000L;
        String key = pairKey(first, second);
        Long last = lastGlobalAnnounce.get(key);
        if (last != null && now - last < cooldown) {
            return false;
        }
        lastGlobalAnnounce.put(key, now);
        return plugin.getSettingsManager().is(DYNAMIC_WAR_ANNOUNCE_GLOBAL);
    }

    @NotNull
    private static String pairKey(@NotNull Clan first, @NotNull Clan second) {
        String a = first.getTag();
        String b = second.getTag();
        return a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a;
    }

    private void sendWebhook(@NotNull Player attacker, @NotNull Player victim, @NotNull Clan attackerClan,
                             @NotNull Clan victimClan) {
        String webhook = plugin.getSettingsManager().getString(DYNAMIC_WAR_WEBHOOK_URL);
        if (webhook == null || webhook.isEmpty()) {
            return;
        }
        String body = buildWebhookBody(attacker, victim, attackerClan, victimClan);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(webhook).openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(body.getBytes(StandardCharsets.UTF_8));
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

    /**
     * Looks for a safe spot around the war, close to the height where the fight is happening.
     * Never uses the highest block of the column, which in the Nether is the bedrock roof.
     */
    @Nullable
    private Location findSafeLocation(@NotNull Location center, int radius, int maxAttempts) {
        World world = center.getWorld();
        if (world == null) {
            return null;
        }
        int safeRadius = Math.max(1, radius);
        int attempts = Math.max(1, maxAttempts);
        int maxY = world.getEnvironment() == World.Environment.NETHER ? NETHER_ROOF_LIMIT : world.getMaxHeight() - 2;
        int centerY = Math.min(center.getBlockY(), maxY);

        for (int i = 0; i < attempts; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = random.nextDouble() * safeRadius;
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            for (int offset = 0; offset <= VERTICAL_SEARCH * 2; offset++) {
                // 0, +1, -1, +2, -2... closest to the fight height first
                int dy = (offset + 1) / 2 * (offset % 2 == 1 ? 1 : -1);
                int y = centerY + dy;
                if (y > maxY) {
                    continue;
                }
                if (isSafe(world, x, y, z)) {
                    return new Location(world, x + 0.5, y, z + 0.5, center.getYaw(), 0);
                }
            }
        }
        return null;
    }

    private boolean isSafe(@NotNull World world, int x, int y, int z) {
        Block feet = world.getBlockAt(x, y, z);
        Material ground = feet.getRelative(0, -1, 0).getType();
        Material head = feet.getRelative(0, 1, 0).getType();
        return ground.isSolid()
                && !UNSAFE_GROUND.contains(ground.name())
                && feet.getType().isAir()
                && head.isAir();
    }

    private enum PairResult {
        ALREADY_AT_WAR, ADDED, FAILED
    }

    private static final class ClanPair {
        private final Clan first;
        private final Clan second;
        private final boolean owned;

        private ClanPair(@NotNull Clan first, @NotNull Clan second, boolean owned) {
            this.first = first;
            this.second = second;
            this.owned = owned;
        }
    }

    private final class DynamicWar {
        private Location center;
        private final Set<Clan> participants = new HashSet<>();
        private final Map<String, ClanPair> pairs = new HashMap<>();
        private long lastHit;
        private BossBar javaBossBar;
        private BossBar bedrockBossBar;

        private DynamicWar(@NotNull Location center) {
            this.center = center.clone();
            this.lastHit = System.currentTimeMillis();
        }

        @NotNull
        private PairResult addPair(@NotNull Clan clanA, @NotNull Clan clanB) {
            String key = pairKey(clanA, clanB);
            boolean warring = clanA.isWarring(clanB);
            if (pairs.containsKey(key) && warring) {
                return PairResult.ALREADY_AT_WAR;
            }

            boolean owned = false;
            if (!warring) {
                // no requester: dynamic wars don't write "at war" entries in the clans' bulletin board
                if (!plugin.getProtectionManager().addWar(null, clanA, clanB)) {
                    return PairResult.FAILED;
                }
                clanA.addDynamicWarring(clanB.getTag());
                clanB.addDynamicWarring(clanA.getTag());
                owned = true;
            }
            participants.add(clanA);
            participants.add(clanB);
            pairs.put(key, new ClanPair(clanA, clanB, owned));
            return PairResult.ADDED;
        }

        private void absorb(@NotNull DynamicWar other) {
            participants.addAll(other.participants);
            pairs.putAll(other.pairs);
            if (other.lastHit > lastHit) {
                lastHit = other.lastHit;
                center = other.center;
            }
        }

        private void markHit(@NotNull Location location) {
            lastHit = System.currentTimeMillis();
            center = location.clone();
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

            Set<Player> javaPlayers = new HashSet<>();
            Set<Player> bedrockPlayers = new HashSet<>();
            for (Clan clan : participants) {
                for (ClanPlayer member : clan.getMembers()) {
                    Player player = member.toPlayer();
                    if (player == null) {
                        continue;
                    }
                    if (BedrockFormDrawer.isBedrockPlayer(player)) {
                        bedrockPlayers.add(player);
                    } else {
                        javaPlayers.add(player);
                    }
                }
            }
            // only send add/remove when something changed, re-adding every second makes the bar flicker
            syncPlayers(javaBossBar, javaPlayers);
            syncPlayers(bedrockBossBar, bedrockPlayers);
        }

        private void syncPlayers(@NotNull BossBar bossBar, @NotNull Set<Player> players) {
            for (Player current : new ArrayList<>(bossBar.getPlayers())) {
                if (!players.contains(current)) {
                    bossBar.removePlayer(current);
                }
            }
            List<Player> current = bossBar.getPlayers();
            for (Player player : players) {
                if (!current.contains(player)) {
                    bossBar.addPlayer(player);
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
                if (!title.equals(bossBar.getTitle())) {
                    bossBar.setTitle(title);
                }
                if (bossBar.getColor() != color) {
                    bossBar.setColor(color);
                }
            }
            bossBar.setProgress(Math.max(0, Math.min(1, progress)));
            return bossBar;
        }

        private void removeBossBars() {
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

        @NotNull
        private Location getCenter() {
            return center;
        }

        private long getLastHit() {
            return lastHit;
        }

        private void endOwnedWars() {
            for (ClanPair pair : pairs.values()) {
                if (pair.owned) {
                    endOwnedWar(pair.first, pair.second);
                }
            }
        }
    }
}
