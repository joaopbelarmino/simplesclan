package net.sacredlabyrinth.phaed.simpleclans.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.papermc.lib.PaperLib;
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
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.*;

public class DynamicWarManager {

    private static final long WARNING_COOLDOWN_MILLIS = 30_000L;
    private static final String STATE_FILE = "dynamic-wars.yml";
    private static final String STATE_KEY = "owned-wars";
    private static final int NETHER_ROOF_Y = 120;

    private final SimpleClans plugin;
    private final List<DynamicWar> wars = new ArrayList<>();
    private final Map<String, Deque<Long>> pendingHits = new HashMap<>();
    private final Map<String, Long> lastAnnounced = new HashMap<>();
    private final Map<UUID, Long> lastWarning = new HashMap<>();
    private final Map<UUID, Long> lastPvpDamage = new HashMap<>();
    private final Map<UUID, Long> lastJoin = new HashMap<>();
    private final Map<UUID, BukkitTask> pendingTeleports = new HashMap<>();
    private final Random random = new Random();
    private final File stateFile;

    public DynamicWarManager(@NotNull SimpleClans plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), STATE_FILE);
        endOrphanWars();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void handleHit(@NotNull Player attacker, @NotNull Player victim, @NotNull Clan attackerClan,
                          @NotNull Clan victimClan, @NotNull Location hitLocation) {
        if (!canStart(attackerClan, victimClan)) {
            return;
        }
        if (!isAllowedWorld(hitLocation.getWorld())) {
            warnDisabledWorld(attacker);
            return;
        }

        DynamicWar attackerWar = getWar(attackerClan);
        DynamicWar victimWar = getWar(victimClan);
        if (attackerWar != null && attackerWar == victimWar && attackerWar.hasPair(attackerClan, victimClan)) {
            attackerWar.markHit();
            return;
        }
        if (!registerHit(attackerClan, victimClan, System.currentTimeMillis())) {
            return;
        }

        DynamicWar war = attackerWar != null ? attackerWar : victimWar;
        if (war == null) {
            war = new DynamicWar(hitLocation.clone());
            if (!war.addPair(attacker, attackerClan, victimClan)) {
                return;
            }
            wars.add(war);
        } else {
            if (attackerWar != null && victimWar != null && attackerWar != victimWar) {
                war = mergeWars(attackerWar, victimWar);
            }
            if (!war.addPair(attacker, attackerClan, victimClan)) {
                return;
            }
        }
        saveState();
        announceStart(attacker, victim, attackerClan, victimClan);
        war.markHit();
    }

    public boolean canStart(@NotNull Clan attackerClan, @NotNull Clan victimClan) {
        return plugin.getSettingsManager().is(DYNAMIC_WAR_ENABLED)
                && !attackerClan.equals(victimClan)
                && !attackerClan.isAlly(victimClan.getTag())
                && attackerClan.isDynamicWarEnabled()
                && victimClan.isDynamicWarEnabled();
    }

    public boolean isAllowedWorld(@Nullable World world) {
        if (world == null) {
            return false;
        }
        for (String disabledWorld : plugin.getSettingsManager().getStringList(DYNAMIC_WAR_DISABLED_WORLDS)) {
            if (world.getName().equalsIgnoreCase(disabledWorld)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tells the player that wars are disabled in this world, at most once every few seconds
     */
    public void warnDisabledWorld(@NotNull Player player) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(player.getUniqueId());
        if (last != null && now - last < WARNING_COOLDOWN_MILLIS) {
            return;
        }
        lastWarning.put(player.getUniqueId(), now);
        ChatBlock.sendMessageKey(player, "dynamic-war.disabled-world");
    }

    /**
     * Counts a hit between two clans and returns true once enough hits happened inside the configured window
     */
    boolean registerHit(@NotNull Clan attackerClan, @NotNull Clan victimClan, long now) {
        int hitsToStart = Math.max(1, plugin.getSettingsManager().getInt(DYNAMIC_WAR_HITS_TO_START));
        if (hitsToStart == 1) {
            return true;
        }
        long window = Math.max(1, plugin.getSettingsManager().getInt(DYNAMIC_WAR_HITS_WINDOW)) * 1000L;
        Deque<Long> hits = pendingHits.computeIfAbsent(pairKey(attackerClan, victimClan), key -> new ArrayDeque<>());
        while (!hits.isEmpty() && now - hits.peekFirst() > window) {
            hits.pollFirst();
        }
        hits.addLast(now);
        if (hits.size() >= hitsToStart) {
            pendingHits.remove(pairKey(attackerClan, victimClan));
            return true;
        }
        return false;
    }

    public void recordDamage(@NotNull Player player, boolean byPlayer) {
        if (byPlayer) {
            lastPvpDamage.put(player.getUniqueId(), System.currentTimeMillis());
        }
        BukkitTask pending = pendingTeleports.remove(player.getUniqueId());
        if (pending != null) {
            pending.cancel();
            ChatBlock.sendMessageKey(player, "dynamic-war.teleport-cancelled-damage");
        }
    }

    public void forget(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask pending = pendingTeleports.remove(uuid);
        if (pending != null) {
            pending.cancel();
        }
        lastWarning.remove(uuid);
        lastPvpDamage.remove(uuid);
    }

    public boolean joinNearestWar(@NotNull Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayerUniqueId(player.getUniqueId());
        if (clan == null) {
            ChatBlock.sendMessageKey(player, "dynamic-war.not-in-clan");
            return false;
        }
        if (getWar(clan) == null) {
            ChatBlock.sendMessageKey(player, "dynamic-war.none");
            return false;
        }
        UUID uuid = player.getUniqueId();
        if (pendingTeleports.containsKey(uuid)) {
            ChatBlock.sendMessageKey(player, "dynamic-war.teleport-already-pending");
            return false;
        }
        long now = System.currentTimeMillis();
        long cooldownLeft = remainingSeconds(lastJoin.get(uuid), plugin.getSettingsManager().getInt(DYNAMIC_WAR_JOIN_COOLDOWN), now);
        if (cooldownLeft > 0) {
            ChatBlock.sendMessageKey(player, "dynamic-war.teleport-cooldown", cooldownLeft);
            return false;
        }
        long combatLeft = remainingSeconds(lastPvpDamage.get(uuid), plugin.getSettingsManager().getInt(DYNAMIC_WAR_JOIN_DAMAGE_BLOCK), now);
        if (combatLeft > 0) {
            ChatBlock.sendMessageKey(player, "dynamic-war.teleport-in-combat", combatLeft);
            return false;
        }

        int delay = Math.max(0, plugin.getSettingsManager().getInt(DYNAMIC_WAR_TELEPORT_DELAY));
        ChatBlock.sendMessageKey(player, "dynamic-war.teleport-start", delay);
        Location startLocation = player.getLocation().clone();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingTeleports.remove(uuid);
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
            if (!isAllowedWorld(player.getWorld()) || !isAllowedWorld(current.getCenter().getWorld())) {
                ChatBlock.sendMessageKey(player, "dynamic-war.disabled-world");
                return;
            }
            findSafeLocation(current.getCenter(), plugin.getSettingsManager().getInt(DYNAMIC_WAR_TELEPORT_RADIUS),
                    Math.max(1, plugin.getSettingsManager().getInt(DYNAMIC_WAR_TELEPORT_MAX_ATTEMPTS)), target -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        if (target == null) {
                            ChatBlock.sendMessageKey(player, "dynamic-war.no-safe-location");
                            return;
                        }
                        PaperLib.teleportAsync(player, target, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
                            if (success) {
                                lastJoin.put(uuid, System.currentTimeMillis());
                                ChatBlock.sendMessageKey(player, "dynamic-war.teleported");
                            }
                        });
                    });
        }, delay * 20L);
        pendingTeleports.put(uuid, task);
        return true;
    }

    private long remainingSeconds(@Nullable Long since, int seconds, long now) {
        if (since == null || seconds <= 0) {
            return 0;
        }
        long left = since + seconds * 1000L - now;
        return left <= 0 ? 0 : (long) Math.ceil(left / 1000.0);
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
        long endingMillis = plugin.getSettingsManager().getInt(DYNAMIC_WAR_IDLE_SECONDS) * 1000L;
        long endMillis = endingMillis + plugin.getSettingsManager().getInt(DYNAMIC_WAR_ENDING_SECONDS) * 1000L;
        boolean changed = false;
        Iterator<DynamicWar> iterator = wars.iterator();
        while (iterator.hasNext()) {
            DynamicWar war = iterator.next();
            long idleMillis = now - war.getLastHit();
            if (idleMillis >= endMillis) {
                war.removeBossBar();
                war.endProtectionWars();
                iterator.remove();
                changed = true;
                continue;
            }

            if (idleMillis >= endingMillis) {
                war.showEndingBar(endMillis - idleMillis);
            } else {
                war.showActiveBar(endingMillis - idleMillis, endingMillis);
            }
        }
        if (changed) {
            saveState();
        }
        pruneHits(now);
    }

    private void pruneHits(long now) {
        long window = Math.max(1, plugin.getSettingsManager().getInt(DYNAMIC_WAR_HITS_WINDOW)) * 1000L;
        pendingHits.values().removeIf(hits -> hits.isEmpty() || now - hits.peekLast() > window);
    }

    /**
     * Ends every dynamic war, so no clan stays at war after a restart
     */
    public void shutdown() {
        for (BukkitTask task : pendingTeleports.values()) {
            task.cancel();
        }
        pendingTeleports.clear();
        for (DynamicWar war : wars) {
            war.removeBossBar();
            war.endProtectionWars();
        }
        wars.clear();
        saveState();
    }

    /**
     * Ends wars that a previous run started but could not end (crash or kill)
     */
    private void endOrphanWars() {
        if (!stateFile.exists()) {
            return;
        }
        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile);
        int ended = 0;
        for (String pair : state.getStringList(STATE_KEY)) {
            String[] tags = pair.split(":", 2);
            if (tags.length != 2) {
                continue;
            }
            Clan first = plugin.getClanManager().getClan(tags[0]);
            Clan second = plugin.getClanManager().getClan(tags[1]);
            if (first == null || second == null) {
                continue;
            }
            boolean removed = first.removeWarringClan(second);
            removed |= second.removeWarringClan(first);
            if (removed) {
                ended++;
            }
        }
        if (!stateFile.delete()) {
            plugin.getLogger().warning("Could not delete " + STATE_FILE);
        }
        if (ended > 0) {
            plugin.getLogger().info(String.format("Ended %d dynamic war(s) left over from the last run", ended));
        }
    }

    private void saveState() {
        List<String> pairs = new ArrayList<>();
        for (DynamicWar war : wars) {
            pairs.addAll(war.getOwnedPairs());
        }
        if (pairs.isEmpty()) {
            if (stateFile.exists() && !stateFile.delete()) {
                plugin.getLogger().warning("Could not delete " + STATE_FILE);
            }
            return;
        }
        YamlConfiguration state = new YamlConfiguration();
        state.set(STATE_KEY, pairs);
        try {
            state.save(stateFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save " + STATE_FILE, ex);
        }
    }

    @NotNull
    private static String pairKey(@NotNull Clan first, @NotNull Clan second) {
        String a = first.getTag();
        String b = second.getTag();
        return a.compareToIgnoreCase(b) <= 0 ? a + ":" + b : b + ":" + a;
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
        attackerClan.clanAnnounce(attacker.getName(), lang("dynamic-war.attacker-clan", attacker,
                attacker.getName(), victimClan.getColorTag()));
        victimClan.clanAnnounce(victim.getName(), lang("dynamic-war.victim-clan", victim,
                victim.getName(), attackerClan.getColorTag()));

        // Global broadcast and webhook only once per pair of clans inside the cooldown
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0, plugin.getSettingsManager().getInt(DYNAMIC_WAR_ANNOUNCE_COOLDOWN)) * 60_000L;
        String key = pairKey(attackerClan, victimClan);
        Long last = lastAnnounced.get(key);
        if (last != null && now - last < cooldown) {
            return;
        }
        lastAnnounced.put(key, now);
        Bukkit.broadcastMessage(ChatUtils.parseColors(lang("dynamic-war.global", attacker, attackerClan.getColorTag(),
                victimClan.getColorTag())));
        sendWebhook(attacker, victim, attackerClan, victimClan);
    }

    private void sendWebhook(@NotNull Player attacker, @NotNull Player victim, @NotNull Clan attackerClan,
                             @NotNull Clan victimClan) {
        String webhook = plugin.getSettingsManager().getString(DYNAMIC_WAR_WEBHOOK_URL);
        if (webhook == null || webhook.isEmpty()) {
            return;
        }
        byte[] body = buildWebhookBody(attacker, victim, attackerClan, victimClan).getBytes(StandardCharsets.UTF_8);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(webhook).openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                // Discord (Cloudflare) may refuse Java's default user agent
                connection.setRequestProperty("User-Agent", "SimpleClans-DynamicWar/" + plugin.getDescription().getVersion());
                connection.setDoOutput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(body);
                }
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) {
                    plugin.getLogger().warning("Dynamic war webhook returned HTTP " + code);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not send dynamic war webhook: " + ex.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    @NotNull
    private String buildWebhookBody(@NotNull Player attacker, @NotNull Player victim, @NotNull Clan attackerClan,
                                    @NotNull Clan victimClan) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", formatWebhookText(plugin.getSettingsManager().getString(DYNAMIC_WAR_WEBHOOK_TITLE),
                attacker, victim, attackerClan, victimClan));
        embed.addProperty("description", formatWebhookText(plugin.getSettingsManager().getString(DYNAMIC_WAR_WEBHOOK_DESCRIPTION),
                attacker, victim, attackerClan, victimClan));
        embed.addProperty("color", parseWebhookColor(plugin.getSettingsManager().getString(DYNAMIC_WAR_WEBHOOK_COLOR)));
        String imageUrl = plugin.getSettingsManager().getString(DYNAMIC_WAR_WEBHOOK_IMAGE_URL);
        if (imageUrl != null && !imageUrl.isEmpty()) {
            JsonObject image = new JsonObject();
            image.addProperty("url", imageUrl);
            embed.add("image", image);
        }
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject body = new JsonObject();
        body.add("embeds", embeds);
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

    /**
     * Looks for a safe spot around the center, loading chunks asynchronously when needed
     */
    private void findSafeLocation(@NotNull Location center, int radius, int attemptsLeft,
                                  @NotNull Consumer<Location> callback) {
        World world = center.getWorld();
        if (world == null) {
            callback.accept(null);
            return;
        }
        int safeRadius = Math.max(1, radius);
        for (int i = attemptsLeft; i > 0; i--) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = random.nextDouble() * safeRadius;
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            if (world.isChunkLoaded(x >> 4, z >> 4)) {
                Location location = findSafeY(world, x, z, center.getBlockY());
                if (location != null) {
                    callback.accept(location);
                    return;
                }
                continue;
            }
            int remaining = i - 1;
            PaperLib.getChunkAtAsync(world, x >> 4, z >> 4).whenComplete((chunk, ex) -> {
                if (!plugin.isEnabled()) {
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Location location = ex == null ? findSafeY(world, x, z, center.getBlockY()) : null;
                    if (location != null) {
                        callback.accept(location);
                    } else {
                        findSafeLocation(center, radius, remaining, callback);
                    }
                });
            });
            return;
        }
        callback.accept(null);
    }

    @Nullable
    private Location findSafeY(@NotNull World world, int x, int z, int centerY) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            // getHighestBlockYAt would return the bedrock roof
            int top = Math.min(centerY + 16, NETHER_ROOF_Y);
            int bottom = Math.max(centerY - 32, 1);
            for (int y = top; y >= bottom; y--) {
                Location location = new Location(world, x + 0.5, y, z + 0.5);
                if (isSafe(location)) {
                    return location;
                }
            }
            return null;
        }
        Location location = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
        return isSafe(location) ? location : null;
    }

    private boolean isSafe(@NotNull Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block ground = feet.getRelative(BlockFace.DOWN);
        return ground.getType().isSolid() && !isHazard(ground.getType())
                && isPassable(feet.getType()) && isPassable(head.getType());
    }

    private boolean isPassable(@NotNull Material material) {
        return !material.isSolid() && !isHazard(material);
    }

    private boolean isHazard(@NotNull Material material) {
        String name = material.name();
        return name.contains("LAVA") || name.contains("WATER") || name.contains("MAGMA") || name.contains("CACTUS")
                || name.contains("FIRE") || name.contains("CAMPFIRE") || name.contains("SWEET_BERRY")
                || name.contains("WITHER_ROSE") || name.contains("POWDER_SNOW");
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

        private boolean hasPair(@NotNull Clan clanA, @NotNull Clan clanB) {
            return protectionWars.containsKey(new War(clanA, clanB));
        }

        private boolean addPair(@NotNull Player requester, @NotNull Clan clanA, @NotNull Clan clanB) {
            War war = new War(clanA, clanB);
            if (protectionWars.containsKey(war)) {
                return false;
            }
            boolean alreadyWarring = clanA.isWarring(clanB);
            if (!alreadyWarring) {
                ClanPlayer requesterCp = plugin.getClanManager().getClanPlayer(requester);
                if (requesterCp == null || !plugin.getProtectionManager().addWar(requesterCp, clanA, clanB)) {
                    return false;
                }
            }
            participants.add(clanA);
            participants.add(clanB);
            protectionWars.put(war, !alreadyWarring);
            return true;
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
            Set<Player> javaPlayers = new HashSet<>();
            Set<Player> bedrockPlayers = new HashSet<>();
            for (ClanPlayer member : getParticipants()) {
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
            syncPlayers(javaBossBar, javaPlayers);
            syncPlayers(bedrockBossBar, bedrockPlayers);
        }

        /**
         * Adds and removes only the players that changed, so the bar does not flicker
         */
        private void syncPlayers(@NotNull BossBar bossBar, @NotNull Set<Player> players) {
            for (Player player : new ArrayList<>(bossBar.getPlayers())) {
                if (!players.contains(player)) {
                    bossBar.removePlayer(player);
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

        @NotNull
        private List<String> getOwnedPairs() {
            List<String> pairs = new ArrayList<>();
            for (Map.Entry<War, Boolean> entry : protectionWars.entrySet()) {
                if (entry.getValue()) {
                    List<Clan> clans = entry.getKey().getClans();
                    pairs.add(pairKey(clans.get(0), clans.get(1)));
                }
            }
            return pairs;
        }

        private void endProtectionWars() {
            ProtectionManager protectionManager = plugin.getProtectionManager();
            for (Map.Entry<War, Boolean> entry : protectionWars.entrySet()) {
                if (!entry.getValue()) {
                    continue;
                }
                List<Clan> clans = entry.getKey().getClans();
                // Use the stored war, which holds the casualties
                War war = protectionManager.getWar(clans.get(0), clans.get(1));
                if (war != null) {
                    protectionManager.removeWar(war, WarEndEvent.Reason.EXPIRATION);
                } else {
                    clans.get(0).removeWarringClan(clans.get(1));
                    clans.get(1).removeWarringClan(clans.get(0));
                }
            }
        }
    }
}
