package net.sacredlabyrinth.phaed.simpleclans.ranking;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * One row of the kills table, as used by the clan ranking
 */
public final class KillRecord {

    private final UUID attacker;
    private final String attackerTag;
    private final UUID victim;
    private final String victimTag;
    private final String type;
    private final long time;

    public KillRecord(@Nullable UUID attacker, @Nullable String attackerTag, @Nullable UUID victim,
                      @Nullable String victimTag, @NotNull String type, long time) {
        this.attacker = attacker;
        this.attackerTag = normalize(attackerTag);
        this.victim = victim;
        this.victimTag = normalize(victimTag);
        this.type = type;
        this.time = time;
    }

    @NotNull
    static String normalize(@Nullable String tag) {
        return tag == null ? "" : tag.toLowerCase(Locale.ROOT);
    }

    @Nullable
    public UUID getAttacker() {
        return attacker;
    }

    @NotNull
    public String getAttackerTag() {
        return attackerTag;
    }

    @Nullable
    public UUID getVictim() {
        return victim;
    }

    @NotNull
    public String getVictimTag() {
        return victimTag;
    }

    /**
     * @return the kill type short name (r, n, c, a)
     */
    @NotNull
    public String getType() {
        return type;
    }

    public long getTime() {
        return time;
    }
}
