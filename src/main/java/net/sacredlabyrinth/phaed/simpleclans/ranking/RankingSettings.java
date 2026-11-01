package net.sacredlabyrinth.phaed.simpleclans.ranking;

import net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.*;

/**
 * Settings of the clan ranking. The defaults match config.yml, so tests can build one without a server.
 */
public final class RankingSettings {

    public double economyWeight = 0.30;
    public double combatWeight = 0.30;
    public double warsWeight = 0.25;
    public double activityWeight = 0.10;
    public double ageWeight = 0.05;

    public int minMembers = 3;
    public int minAgeDays = 3;
    public boolean requireVerified = true;
    public int inactiveDays = 14;

    public int economyMemberMinDays = 3;
    public double economyMaxPerMember = 500_000;
    public Set<String> economyExcludedPlayers = new HashSet<>();

    public int combatWindowDays = 30;
    public double combatPriorWeight = 10;
    public int combatMaxKillsPerVictimDay = 3;
    public int combatFullKillsPerClanPairDay = 10;
    public double combatExtraKillFactor = 0.25;
    public double killWeightRival = 2.0;
    public double killWeightNeutral = 1.0;
    public double killWeightCivilian = 0.0;
    public double killWeightAlly = -1.0;

    public int warsWindowDays = 30;
    public double warsHalfLifeDays = 7;
    public int warsMinKillsEachSide = 3;
    public int warsMaxPerOpponentWeek = 2;

    public int activityDays = 7;
    public int ageFullDays = 90;

    @NotNull
    public static RankingSettings from(@NotNull SettingsManager sm) {
        RankingSettings s = new RankingSettings();
        s.economyWeight = sm.getDouble(CLAN_RANKING_WEIGHT_ECONOMY);
        s.combatWeight = sm.getDouble(CLAN_RANKING_WEIGHT_COMBAT);
        s.warsWeight = sm.getDouble(CLAN_RANKING_WEIGHT_WARS);
        s.activityWeight = sm.getDouble(CLAN_RANKING_WEIGHT_ACTIVITY);
        s.ageWeight = sm.getDouble(CLAN_RANKING_WEIGHT_AGE);
        s.minMembers = sm.getInt(CLAN_RANKING_MIN_MEMBERS);
        s.minAgeDays = sm.getInt(CLAN_RANKING_MIN_AGE_DAYS);
        s.requireVerified = sm.is(CLAN_RANKING_REQUIRE_VERIFIED);
        s.inactiveDays = sm.getInt(CLAN_RANKING_INACTIVE_DAYS);
        s.economyMemberMinDays = sm.getInt(CLAN_RANKING_ECONOMY_MEMBER_MIN_DAYS);
        s.economyMaxPerMember = sm.getDouble(CLAN_RANKING_ECONOMY_MAX_PER_MEMBER);
        for (String player : sm.getStringList(CLAN_RANKING_ECONOMY_EXCLUDED_PLAYERS)) {
            s.economyExcludedPlayers.add(player.toLowerCase(Locale.ROOT));
        }
        s.combatWindowDays = sm.getInt(CLAN_RANKING_COMBAT_WINDOW_DAYS);
        s.combatPriorWeight = sm.getDouble(CLAN_RANKING_COMBAT_PRIOR_WEIGHT);
        s.combatMaxKillsPerVictimDay = sm.getInt(CLAN_RANKING_COMBAT_MAX_KILLS_PER_VICTIM_DAY);
        s.combatFullKillsPerClanPairDay = sm.getInt(CLAN_RANKING_COMBAT_FULL_KILLS_PER_CLAN_PAIR_DAY);
        s.combatExtraKillFactor = sm.getDouble(CLAN_RANKING_COMBAT_EXTRA_KILL_FACTOR);
        s.killWeightRival = sm.getDouble(KILL_WEIGHTS_RIVAL);
        s.killWeightNeutral = sm.getDouble(KILL_WEIGHTS_NEUTRAL);
        s.killWeightCivilian = sm.getDouble(KILL_WEIGHTS_CIVILIAN);
        s.killWeightAlly = sm.getDouble(KILL_WEIGHTS_ALLY);
        s.warsWindowDays = sm.getInt(CLAN_RANKING_WARS_WINDOW_DAYS);
        s.warsHalfLifeDays = sm.getDouble(CLAN_RANKING_WARS_HALF_LIFE_DAYS);
        s.warsMinKillsEachSide = sm.getInt(CLAN_RANKING_WARS_MIN_KILLS_EACH_SIDE);
        s.warsMaxPerOpponentWeek = sm.getInt(CLAN_RANKING_WARS_MAX_PER_OPPONENT_WEEK);
        s.activityDays = sm.getInt(CLAN_RANKING_ACTIVITY_DAYS);
        s.ageFullDays = sm.getInt(CLAN_RANKING_AGE_FULL_DAYS);
        return s;
    }

    double getWeightSum() {
        double sum = economyWeight + combatWeight + warsWeight + activityWeight + ageWeight;
        return sum <= 0 ? 1 : sum;
    }

    double getKillWeight(@NotNull String type) {
        switch (type) {
            case "r":
                return killWeightRival;
            case "n":
                return killWeightNeutral;
            case "c":
                return killWeightCivilian;
            case "a":
                return killWeightAlly;
            default:
                return 0;
        }
    }
}
