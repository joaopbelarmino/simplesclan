package net.sacredlabyrinth.phaed.simpleclans.managers;

import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.ranking.ClanRankingCalculator;
import net.sacredlabyrinth.phaed.simpleclans.ranking.ClanRankingCalculator.ClanData;
import net.sacredlabyrinth.phaed.simpleclans.ranking.ClanRankingCalculator.MemberData;
import net.sacredlabyrinth.phaed.simpleclans.ranking.ClanScore;
import net.sacredlabyrinth.phaed.simpleclans.ranking.KillRecord;
import net.sacredlabyrinth.phaed.simpleclans.ranking.RankingSettings;
import net.sacredlabyrinth.phaed.simpleclans.ranking.WarRecord;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.CLAN_RANKING_UPDATE_MINUTES;

/**
 * Keeps the clan ranking up to date. The clans and the member money are read on the main thread,
 * the database and the calculation run asynchronously, and readers only see the last finished result.
 */
public class ClanRankingManager {

    private static final long DAY = 86_400_000L;

    private final SimpleClans plugin;
    private final AtomicBoolean updating = new AtomicBoolean();
    private volatile List<ClanScore> ranking = Collections.emptyList();
    private volatile Map<String, ClanScore> byTag = Collections.emptyMap();
    private volatile long updatedAt;

    public ClanRankingManager(@NotNull SimpleClans plugin) {
        this.plugin = plugin;
        long period = Math.max(1, plugin.getSettingsManager().getInt(CLAN_RANKING_UPDATE_MINUTES)) * 60L * 20L;
        // The first run waits for the economy plugin and the clans to load
        Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L * 30, period);
    }

    /**
     * @return the ranked clans, best first
     */
    @NotNull
    public List<ClanScore> getRanking() {
        return ranking;
    }

    @Nullable
    public ClanScore getScore(@Nullable Clan clan) {
        return clan == null ? null : byTag.get(clan.getTag().toLowerCase(Locale.ROOT));
    }

    @Nullable
    public ClanScore getScore(int position) {
        List<ClanScore> current = ranking;
        return position >= 1 && position <= current.size() ? current.get(position - 1) : null;
    }

    @Nullable
    public Clan getClan(@Nullable ClanScore score) {
        return score == null ? null : plugin.getClanManager().getClan(score.getTag());
    }

    /**
     * @return when the ranking was last calculated, 0 if it was not calculated yet
     */
    public long getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Starts a new calculation, unless one is already running. Must be called on the main thread.
     */
    public void update() {
        if (!updating.compareAndSet(false, true)) {
            return;
        }
        RankingSettings settings;
        List<ClanData> clans;
        try {
            settings = RankingSettings.from(plugin.getSettingsManager());
            clans = snapshot(settings);
        } catch (RuntimeException ex) {
            updating.set(false);
            plugin.getLogger().log(Level.SEVERE, "Error reading the clans for the ranking", ex);
            return;
        }
        Map<String, Integer> previous = new HashMap<>();
        for (ClanScore score : ranking) {
            previous.put(score.getTag(), score.getPosition());
        }
        long now = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                StorageManager storage = plugin.getStorageManager();
                List<KillRecord> kills = storage.getKillsSince(LocalDateTime.now().minusDays(settings.combatWindowDays));
                List<WarRecord> wars = storage.getWarsEndedSince(now - settings.warsWindowDays * DAY);
                List<ClanScore> result = new ClanRankingCalculator(settings).calculate(clans, kills, wars, previous, now);
                Map<String, ClanScore> tags = new HashMap<>();
                for (ClanScore score : result) {
                    tags.put(score.getTag(), score);
                }
                byTag = tags;
                ranking = Collections.unmodifiableList(result);
                updatedAt = now;
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.SEVERE, "Error calculating the clan ranking", ex);
            } finally {
                updating.set(false);
            }
        });
    }

    @NotNull
    private List<ClanData> snapshot(@NotNull RankingSettings settings) {
        PermissionsManager permissions = plugin.getPermissionsManager();
        boolean economy = permissions.hasEconomy();
        long now = System.currentTimeMillis();
        List<ClanData> clans = new ArrayList<>();
        for (Clan clan : plugin.getClanManager().getClans()) {
            List<ClanPlayer> members = clan.getMembers();
            // Skip the money lookups of clans that cannot enter the ranking anyway
            boolean candidate = members.size() >= settings.minMembers && (!settings.requireVerified || clan.isVerified());
            List<MemberData> memberData = new ArrayList<>();
            for (ClanPlayer cp : members) {
                UUID uuid = cp.getUniqueId();
                if (uuid == null) {
                    continue;
                }
                boolean exempt = settings.economyExcludedPlayers.contains(cp.getName().toLowerCase(Locale.ROOT))
                        || settings.economyExcludedPlayers.contains(uuid.toString());
                boolean counted = candidate && economy && !exempt
                        && now - cp.getJoinDate() >= settings.economyMemberMinDays * DAY;
                double money = 0;
                if (counted) {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                    try {
                        money = permissions.playerGetMoney(player);
                    } catch (RuntimeException ex) {
                        // Some economy plugins fail for players they never saw
                        money = 0;
                    }
                }
                memberData.add(new MemberData(uuid, cp.getName(), cp.getJoinDate(), cp.getLastSeen(),
                        Bukkit.getPlayer(uuid) != null, money, exempt));
            }
            clans.add(new ClanData(clan.getTag(), clan.isVerified(), clan.getFounded(),
                    economy ? clan.getBalance() : 0, memberData));
        }
        return clans;
    }
}
