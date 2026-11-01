package net.sacredlabyrinth.phaed.simpleclans.ranking;

import org.jetbrains.annotations.NotNull;

/**
 * A finished war between two clans. Casualties are the deaths each clan suffered.
 */
public final class WarRecord {

    private final String clanA;
    private final String clanB;
    private final int casualtiesA;
    private final int casualtiesB;
    private final long startedAt;
    private final long endedAt;
    private final String reason;

    public WarRecord(@NotNull String clanA, @NotNull String clanB, int casualtiesA, int casualtiesB,
                     long startedAt, long endedAt, @NotNull String reason) {
        this.clanA = KillRecord.normalize(clanA);
        this.clanB = KillRecord.normalize(clanB);
        this.casualtiesA = casualtiesA;
        this.casualtiesB = casualtiesB;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.reason = reason;
    }

    @NotNull
    public String getClanA() {
        return clanA;
    }

    @NotNull
    public String getClanB() {
        return clanB;
    }

    public int getCasualtiesA() {
        return casualtiesA;
    }

    public int getCasualtiesB() {
        return casualtiesB;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getEndedAt() {
        return endedAt;
    }

    @NotNull
    public String getReason() {
        return reason;
    }

    public boolean involves(@NotNull String tag) {
        return clanA.equals(tag) || clanB.equals(tag);
    }

    @NotNull
    public String getOpponent(@NotNull String tag) {
        return clanA.equals(tag) ? clanB : clanA;
    }

    /**
     * @return the kills the given clan made in this war (the casualties of the other side)
     */
    public int getKillsBy(@NotNull String tag) {
        return clanA.equals(tag) ? casualtiesB : casualtiesA;
    }
}
