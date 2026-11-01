package net.sacredlabyrinth.phaed.simpleclans.ranking;

import org.jetbrains.annotations.NotNull;

/**
 * The ranking score of one clan. Each part is already in points (they add up to the total).
 */
public final class ClanScore {

    private final String tag;
    private final double economy;
    private final double combat;
    private final double wars;
    private final double activity;
    private final double age;
    private final int combatKills;
    private final long founded;
    private int position;

    public ClanScore(@NotNull String tag, double economy, double combat, double wars, double activity, double age,
                     int combatKills, long founded) {
        this.tag = tag;
        this.economy = economy;
        this.combat = combat;
        this.wars = wars;
        this.activity = activity;
        this.age = age;
        this.combatKills = combatKills;
        this.founded = founded;
    }

    /**
     * @return the clan tag, in lower case
     */
    @NotNull
    public String getTag() {
        return tag;
    }

    public int getTotal() {
        return (int) Math.round(economy + combat + wars + activity + age);
    }

    public int getEconomy() {
        return (int) Math.round(economy);
    }

    public int getCombat() {
        return (int) Math.round(combat);
    }

    public int getWars() {
        return (int) Math.round(wars);
    }

    public int getActivity() {
        return (int) Math.round(activity);
    }

    public int getAge() {
        return (int) Math.round(age);
    }

    /**
     * @return the weighted kills counted for this clan inside the combat window, used to break ties
     */
    public int getCombatKills() {
        return combatKills;
    }

    public long getFounded() {
        return founded;
    }

    /**
     * @return the position in the ranking, starting at 1
     */
    public int getPosition() {
        return position;
    }

    void setPosition(int position) {
        this.position = position;
    }
}
