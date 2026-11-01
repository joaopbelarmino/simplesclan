package net.sacredlabyrinth.phaed.simpleclans.ranking;

import net.sacredlabyrinth.phaed.simpleclans.ranking.ClanRankingCalculator.ClanData;
import net.sacredlabyrinth.phaed.simpleclans.ranking.ClanRankingCalculator.MemberData;
import org.junit.jupiter.api.Test;

import java.util.*;

import static net.sacredlabyrinth.phaed.simpleclans.ranking.ClanRankingCalculator.DAY;
import static org.junit.jupiter.api.Assertions.*;

public class ClanRankingCalculatorTest {

    private static final long NOW = 1_000 * DAY;

    private final RankingSettings settings = new RankingSettings();
    private final ClanRankingCalculator calculator = new ClanRankingCalculator(settings);

    private static MemberData member(UUID uuid, double money) {
        return new MemberData(uuid, uuid.toString().substring(0, 8), NOW - 60 * DAY, NOW - DAY, false, money, false);
    }

    private static ClanData clan(String tag, double balance, MemberData... members) {
        return new ClanData(tag, true, NOW - 60 * DAY, balance, Arrays.asList(members));
    }

    private static List<UUID> uuids(int count) {
        List<UUID> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(UUID.randomUUID());
        }
        return list;
    }

    private static ClanData clanOf(String tag, double balance, List<UUID> members) {
        MemberData[] data = members.stream().map(uuid -> member(uuid, 0)).toArray(MemberData[]::new);
        return clan(tag, balance, data);
    }

    private static KillRecord kill(UUID attacker, String attackerTag, UUID victim, String victimTag, long time) {
        return new KillRecord(attacker, attackerTag, victim, victimTag, "n", time);
    }

    @Test
    public void filtersIneligibleClans() {
        List<UUID> members = uuids(3);
        ClanData ok = clanOf("ok", 0, members);
        ClanData unverified = new ClanData("unv", false, NOW - 60 * DAY, 0, ok.members);
        ClanData young = new ClanData("young", true, NOW - DAY, 0, ok.members);
        ClanData small = clanOf("small", 0, uuids(2));
        MemberData inactive = new MemberData(UUID.randomUUID(), "old", NOW - 90 * DAY, NOW - 30 * DAY, false, 0, false);
        ClanData inactiveClan = clan("inactive", 0, inactive, inactive, inactive);

        List<ClanScore> scores = calculator.calculate(Arrays.asList(ok, unverified, young, small, inactiveClan),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), NOW);

        assertEquals(1, scores.size());
        assertEquals("ok", scores.get(0).getTag());
        assertEquals(1, scores.get(0).getPosition());
    }

    @Test
    public void economyIsCappedPerMemberAndIgnoresNewAndExemptMembers() {
        UUID rich = UUID.randomUUID();
        MemberData capped = member(rich, 10_000_000);
        MemberData newcomer = new MemberData(UUID.randomUUID(), "new", NOW - DAY, NOW, true, 400_000, false);
        MemberData exempt = new MemberData(UUID.randomUUID(), "staff", NOW - 60 * DAY, NOW, true, 400_000, true);
        ClanData clan = clan("a", 1000, capped, newcomer, exempt);

        double value = calculator.economyValue(clan, NOW);

        assertEquals(Math.log10(1 + 1000 + settings.economyMaxPerMember), value, 1e-9);
    }

    @Test
    public void richestClanGetsTheWholeEconomyWeight() {
        ClanData rich = clanOf("rich", 1_000_000, uuids(3));
        ClanData poor = clanOf("poor", 10, uuids(3));

        List<ClanScore> scores = calculator.calculate(Arrays.asList(poor, rich), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyMap(), NOW);

        ClanScore first = scores.get(0);
        assertEquals("rich", first.getTag());
        assertEquals(300, first.getEconomy());
        assertTrue(scores.get(1).getEconomy() < first.getEconomy());
    }

    @Test
    public void repeatedKillsOfTheSameVictimLoseValue() {
        List<UUID> a = uuids(3);
        List<UUID> b = uuids(3);
        List<UUID> c = uuids(3);
        List<KillRecord> farm = new ArrayList<>();
        List<KillRecord> spread = new ArrayList<>();
        // Clan "farm" kills the same victim 10 times, clan "spread" kills 10 different players
        for (int i = 0; i < 10; i++) {
            farm.add(kill(a.get(0), "farm", b.get(0), "victims", NOW - DAY + i));
            UUID victim = UUID.randomUUID();
            spread.add(kill(c.get(0), "spread", victim, "other" + i, NOW - DAY + i));
        }
        List<KillRecord> kills = new ArrayList<>(farm);
        kills.addAll(spread);

        List<ClanScore> scores = calculator.calculate(
                Arrays.asList(clanOf("farm", 0, a), clanOf("victims", 0, b), clanOf("spread", 0, c)),
                kills, Collections.emptyList(), Collections.emptyMap(), NOW);

        Map<String, ClanScore> byTag = new HashMap<>();
        scores.forEach(score -> byTag.put(score.getTag(), score));
        // 1 + 0.5 + 0.25 and then nothing
        assertEquals(2, byTag.get("farm").getCombatKills());
        assertEquals(10, byTag.get("spread").getCombatKills());
    }

    @Test
    public void killsOutsideTheWindowAreIgnored() {
        List<UUID> a = uuids(3);
        List<KillRecord> kills = Collections.singletonList(
                kill(a.get(0), "a", UUID.randomUUID(), "b", NOW - (settings.combatWindowDays + 1) * DAY));

        List<ClanScore> scores = calculator.calculate(Collections.singletonList(clanOf("a", 0, a)), kills,
                Collections.emptyList(), Collections.emptyMap(), NOW);

        assertEquals(0, scores.get(0).getCombatKills());
    }

    @Test
    public void oneStrongMemberDoesNotCarryTheClan() {
        List<UUID> carried = uuids(5);
        List<UUID> balanced = uuids(5);
        List<KillRecord> kills = new ArrayList<>();
        long time = NOW - 2 * DAY;
        // One player of "carried" gets 25 kills; every player of "balanced" gets 5
        for (int i = 0; i < 25; i++) {
            kills.add(kill(carried.get(0), "carried", UUID.randomUUID(), "x" + i, time + i));
            kills.add(kill(balanced.get(i % 5), "balanced", UUID.randomUUID(), "y" + i, time + i));
        }

        List<ClanScore> scores = calculator.calculate(
                Arrays.asList(clanOf("carried", 0, carried), clanOf("balanced", 0, balanced)),
                kills, Collections.emptyList(), Collections.emptyMap(), NOW);

        assertEquals("balanced", scores.get(0).getTag());
        assertTrue(scores.get(0).getCombat() > scores.get(1).getCombat());
    }

    @Test
    public void warsNeedARealFightAndDecay() {
        WarRecord fake = new WarRecord("a", "b", 0, 1, NOW - 2 * DAY, NOW - DAY, "EXPIRATION");
        assertEquals(0, calculator.warsValue("a", Collections.singletonList(fake), Collections.emptyMap(), NOW));

        WarRecord recent = new WarRecord("a", "b", 3, 5, NOW - DAY, NOW, "EXPIRATION");
        WarRecord old = new WarRecord("a", "c", 3, 5, NOW - 15 * DAY, NOW - 14 * DAY, "EXPIRATION");
        double recentValue = calculator.warsValue("a", Collections.singletonList(recent), Collections.emptyMap(), NOW);
        double oldValue = calculator.warsValue("a", Collections.singletonList(old), Collections.emptyMap(), NOW);

        // "a" lost 3 and the opponent lost 5: a win, against an unranked opponent
        assertEquals(2.0 * 0.5, recentValue, 1e-9);
        assertEquals(recentValue / 4, oldValue, 1e-9);
    }

    @Test
    public void warsAgainstTheSameOpponentAreLimitedPerWeek() {
        List<WarRecord> wars = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            wars.add(new WarRecord("a", "b", 3, 3, NOW - 1000 - i, NOW - i, "EXPIRATION"));
        }
        double value = calculator.warsValue("a", wars, Collections.emptyMap(), NOW);

        assertEquals(settings.warsMaxPerOpponentWeek * 1.5 * 0.5, value, 1e-6);
    }

    @Test
    public void strongerOpponentsAreWorthMore() {
        Map<String, Integer> positions = new HashMap<>();
        positions.put("top", 1);
        positions.put("bottom", 4);
        positions.put("x", 2);
        positions.put("y", 3);

        assertEquals(1.0, ClanRankingCalculator.opponentStrength("top", positions), 1e-9);
        assertTrue(ClanRankingCalculator.opponentStrength("bottom", positions) < 1.0);
        assertEquals(0.5, ClanRankingCalculator.opponentStrength("unknown", positions), 1e-9);
    }

    @Test
    public void robustMeanUsesMedianForSmallClans() {
        assertEquals(2, ClanRankingCalculator.robustMean(Arrays.asList(1.0, 2.0, 100.0)), 1e-9);
        // 20% trimmed: drops 1 and 100
        assertEquals(3, ClanRankingCalculator.robustMean(Arrays.asList(1.0, 2.0, 3.0, 4.0, 100.0)), 1e-9);
        assertEquals(0, ClanRankingCalculator.robustMean(Collections.emptyList()), 1e-9);
    }

    @Test
    public void ageHasLowWeightAndIsCapped() {
        ClanData old = new ClanData("old", true, NOW - 400 * DAY, 0, clanOf("x", 0, uuids(3)).members);
        ClanData young = new ClanData("young", true, NOW - 10 * DAY, 0, clanOf("y", 0, uuids(3)).members);

        List<ClanScore> scores = calculator.calculate(Arrays.asList(young, old), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyMap(), NOW);

        Map<String, ClanScore> byTag = new HashMap<>();
        scores.forEach(score -> byTag.put(score.getTag(), score));
        assertEquals(50, byTag.get("old").getAge());
        assertTrue(byTag.get("young").getAge() < 10);
    }

    @Test
    public void tiesAreBrokenByFoundationDate() {
        List<UUID> members = uuids(3);
        ClanData older = new ClanData("b", true, NOW - 100 * DAY, 0, clanOf("x", 0, members).members);
        ClanData newer = new ClanData("a", true, NOW - 99 * DAY, 0, clanOf("y", 0, members).members);

        List<ClanScore> scores = calculator.calculate(Arrays.asList(newer, older), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyMap(), NOW);

        assertEquals(scores.get(0).getTotal(), scores.get(1).getTotal());
        assertEquals("b", scores.get(0).getTag());
    }
}
