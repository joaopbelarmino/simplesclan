package net.sacredlabyrinth.phaed.simpleclans.ranking;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Turns a snapshot of the clans, their kills and their wars into ranking scores.
 * It has no Bukkit dependency, so it can run off the main thread and be unit tested.
 */
public final class ClanRankingCalculator {

    static final long DAY = 86_400_000L;
    private static final long WEEK = 7 * DAY;

    private final RankingSettings settings;

    public ClanRankingCalculator(@NotNull RankingSettings settings) {
        this.settings = settings;
    }

    public static final class MemberData {
        final UUID uuid;
        final String name;
        final long joinDate;
        final long lastSeen;
        final boolean online;
        final double money;
        final boolean exempt;

        public MemberData(@NotNull UUID uuid, @NotNull String name, long joinDate, long lastSeen, boolean online,
                          double money, boolean exempt) {
            this.uuid = uuid;
            this.name = name;
            this.joinDate = joinDate;
            this.lastSeen = lastSeen;
            this.online = online;
            this.money = money;
            this.exempt = exempt;
        }
    }

    public static final class ClanData {
        final String tag;
        final boolean verified;
        final long founded;
        final double balance;
        final List<MemberData> members;

        public ClanData(@NotNull String tag, boolean verified, long founded, double balance,
                        @NotNull List<MemberData> members) {
            this.tag = KillRecord.normalize(tag);
            this.verified = verified;
            this.founded = founded;
            this.balance = balance;
            this.members = members;
        }
    }

    /**
     * @param previousPositions positions of the last calculation (tag in lower case), used for opponent strength
     * @return the eligible clans, best first, with positions set
     */
    @NotNull
    public List<ClanScore> calculate(@NotNull List<ClanData> clans, @NotNull List<KillRecord> kills,
                                     @NotNull List<WarRecord> wars, @NotNull Map<String, Integer> previousPositions,
                                     long now) {
        List<ClanData> eligible = new ArrayList<>();
        for (ClanData clan : clans) {
            if (isEligible(clan, now)) {
                eligible.add(clan);
            }
        }

        CombatStats combatStats = new CombatStats(kills, now);
        Map<String, Double> economy = new HashMap<>();
        Map<String, Double> combat = new HashMap<>();
        Map<String, Double> warValues = new HashMap<>();
        for (ClanData clan : eligible) {
            economy.put(clan.tag, economyValue(clan, now));
            combat.put(clan.tag, combatStats.clanValue(clan));
            warValues.put(clan.tag, warsValue(clan.tag, wars, previousPositions, now));
        }
        double maxEconomy = max(economy.values());
        double maxCombat = max(combat.values());
        double maxWars = max(warValues.values());

        double unit = 1000 / settings.getWeightSum();
        List<ClanScore> scores = new ArrayList<>();
        for (ClanData clan : eligible) {
            scores.add(new ClanScore(clan.tag,
                    unit * settings.economyWeight * ratio(economy.get(clan.tag), maxEconomy),
                    unit * settings.combatWeight * ratio(combat.get(clan.tag), maxCombat),
                    unit * settings.warsWeight * ratio(warValues.get(clan.tag), maxWars),
                    unit * settings.activityWeight * activityValue(clan, now),
                    unit * settings.ageWeight * ageValue(clan, now),
                    (int) Math.round(combatStats.clanKills(clan)),
                    clan.founded));
        }

        scores.sort(Comparator.comparingInt(ClanScore::getTotal).reversed()
                .thenComparing(Comparator.comparingInt(ClanScore::getCombatKills).reversed())
                .thenComparingLong(ClanScore::getFounded)
                .thenComparing(ClanScore::getTag));
        for (int i = 0; i < scores.size(); i++) {
            scores.get(i).setPosition(i + 1);
        }
        return scores;
    }

    boolean isEligible(@NotNull ClanData clan, long now) {
        if (settings.requireVerified && !clan.verified) {
            return false;
        }
        if (clan.members.size() < settings.minMembers) {
            return false;
        }
        if (now - clan.founded < settings.minAgeDays * DAY) {
            return false;
        }
        for (MemberData member : clan.members) {
            if (member.online || now - member.lastSeen <= settings.inactiveDays * DAY) {
                return true;
            }
        }
        return false;
    }

    /**
     * Clan bank plus capped member money, on a logarithmic scale so one fortune does not decide the ranking
     */
    double economyValue(@NotNull ClanData clan, long now) {
        double total = Math.max(0, clan.balance);
        for (MemberData member : clan.members) {
            if (member.exempt || now - member.joinDate < settings.economyMemberMinDays * DAY) {
                continue;
            }
            double money = Math.max(0, member.money);
            if (settings.economyMaxPerMember > 0) {
                money = Math.min(money, settings.economyMaxPerMember);
            }
            total += money;
        }
        return Math.log10(1 + total);
    }

    double activityValue(@NotNull ClanData clan, long now) {
        if (clan.members.isEmpty()) {
            return 0;
        }
        int active = 0;
        for (MemberData member : clan.members) {
            if (member.online || now - member.lastSeen <= settings.activityDays * DAY) {
                active++;
            }
        }
        return active / (double) clan.members.size();
    }

    double ageValue(@NotNull ClanData clan, long now) {
        if (settings.ageFullDays <= 0) {
            return 1;
        }
        double days = Math.max(0, now - clan.founded) / (double) DAY;
        return Math.min(1, days / settings.ageFullDays);
    }

    double warsValue(@NotNull String tag, @NotNull List<WarRecord> wars, @NotNull Map<String, Integer> previousPositions,
                     long now) {
        long windowStart = now - settings.warsWindowDays * DAY;
        Map<String, List<Double>> perOpponentWeek = new HashMap<>();
        for (WarRecord war : wars) {
            if (!war.involves(tag) || war.getEndedAt() < windowStart) {
                continue;
            }
            String opponent = war.getOpponent(tag);
            int kills = war.getKillsBy(tag);
            int opponentKills = war.getKillsBy(opponent);
            // A war that ended without a real fight is not worth points
            if (kills < settings.warsMinKillsEachSide || opponentKills < settings.warsMinKillsEachSide) {
                continue;
            }
            double daysAgo = Math.max(0, now - war.getEndedAt()) / (double) DAY;
            double decay = settings.warsHalfLifeDays <= 0 ? 1 : Math.pow(0.5, daysAgo / settings.warsHalfLifeDays);
            double result = kills > opponentKills ? 2.0 : kills == opponentKills ? 1.5 : 1.0;
            double value = decay * result * opponentStrength(opponent, previousPositions);
            String key = opponent + "|" + (war.getEndedAt() / WEEK);
            perOpponentWeek.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        double total = 0;
        for (List<Double> values : perOpponentWeek.values()) {
            values.sort(Comparator.reverseOrder());
            int limit = settings.warsMaxPerOpponentWeek <= 0 ? values.size()
                    : Math.min(values.size(), settings.warsMaxPerOpponentWeek);
            for (int i = 0; i < limit; i++) {
                total += values.get(i);
            }
        }
        return total;
    }

    /**
     * 1.0 for the first clan of the last ranking, going down to 0.5 for the last one and for unranked clans
     */
    static double opponentStrength(@NotNull String opponent, @NotNull Map<String, Integer> previousPositions) {
        Integer position = previousPositions.get(opponent);
        if (position == null || previousPositions.isEmpty()) {
            return 0.5;
        }
        int size = previousPositions.size();
        return 0.5 + 0.5 * (1 - (position - 1) / (double) size);
    }

    private static double ratio(@Nullable Double value, double max) {
        if (value == null || max <= 0) {
            return 0;
        }
        return Math.max(0, value) / max;
    }

    private static double max(@NotNull Collection<Double> values) {
        double max = 0;
        for (double value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    /**
     * Median for small clans, 20% trimmed mean for bigger ones, so a single player does not carry the clan
     */
    static double robustMean(@NotNull List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n < 5) {
            return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
        }
        int trim = (int) Math.floor(n * 0.2);
        double sum = 0;
        for (int i = trim; i < n - trim; i++) {
            sum += sorted.get(i);
        }
        return sum / (n - 2 * trim);
    }

    /**
     * Kills and deaths per member, counted only while the member was in the clan and inside the window
     */
    private final class CombatStats {
        private final Map<String, Double> kills = new HashMap<>();
        private final Map<String, Integer> deaths = new HashMap<>();
        private final double serverAverage;

        private CombatStats(@NotNull List<KillRecord> records, long now) {
            long windowStart = now - settings.combatWindowDays * DAY;
            List<KillRecord> sorted = new ArrayList<>();
            for (KillRecord record : records) {
                if (record.getTime() >= windowStart) {
                    sorted.add(record);
                }
            }
            sorted.sort(Comparator.comparingLong(KillRecord::getTime));

            Map<String, Integer> perVictimDay = new HashMap<>();
            Map<String, Integer> perClanPairDay = new HashMap<>();
            double totalKills = 0;
            int totalDeaths = 0;
            for (KillRecord record : sorted) {
                long day = record.getTime() / DAY;
                if (record.getVictim() != null && !record.getVictimTag().isEmpty()) {
                    deaths.merge(key(record.getVictimTag(), record.getVictim()), 1, Integer::sum);
                    totalDeaths++;
                }
                if (record.getAttacker() == null || record.getAttackerTag().isEmpty()) {
                    continue;
                }
                int victimCount = perVictimDay.merge(record.getAttacker() + "|" + record.getVictim() + "|" + day,
                        1, Integer::sum);
                int pairCount = perClanPairDay.merge(record.getAttackerTag() + "|" + record.getVictimTag() + "|" + day,
                        1, Integer::sum);
                double value = settings.getKillWeight(record.getType())
                        * victimFactor(victimCount) * pairFactor(pairCount);
                kills.merge(key(record.getAttackerTag(), record.getAttacker()), value, Double::sum);
                totalKills += Math.max(0, value);
            }
            double average = totalDeaths == 0 ? 1 : totalKills / totalDeaths;
            serverAverage = Math.max(0.1, Math.min(10, average));
        }

        private double victimFactor(int count) {
            int max = settings.combatMaxKillsPerVictimDay;
            if (max <= 0) {
                return 1;
            }
            return count > max ? 0 : Math.pow(0.5, count - 1);
        }

        private double pairFactor(int count) {
            int full = settings.combatFullKillsPerClanPairDay;
            if (full <= 0 || count <= full) {
                return 1;
            }
            return settings.combatExtraKillFactor;
        }

        private double clanValue(@NotNull ClanData clan) {
            List<Double> kdrs = new ArrayList<>();
            double prior = settings.combatPriorWeight;
            for (MemberData member : clan.members) {
                String key = key(clan.tag, member.uuid);
                double memberKills = Math.max(0, kills.getOrDefault(key, 0.0));
                int memberDeaths = deaths.getOrDefault(key, 0);
                double kdr = prior > 0
                        ? (memberKills + prior * serverAverage) / (memberDeaths + prior)
                        : memberKills / Math.max(1, memberDeaths);
                kdrs.add(kdr);
            }
            return robustMean(kdrs);
        }

        private double clanKills(@NotNull ClanData clan) {
            double total = 0;
            for (MemberData member : clan.members) {
                total += Math.max(0, kills.getOrDefault(key(clan.tag, member.uuid), 0.0));
            }
            return total;
        }

        @NotNull
        private String key(@NotNull String tag, @NotNull UUID uuid) {
            return tag + "|" + uuid;
        }
    }
}
