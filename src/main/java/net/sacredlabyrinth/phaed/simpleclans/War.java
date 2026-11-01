package net.sacredlabyrinth.phaed.simpleclans;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class War {
    private final Map<Clan, Integer> clans = new HashMap<>();
    private final long startedAt = System.currentTimeMillis();

    public War(@NotNull Clan clan1, @NotNull Clan clan2) {
        clans.put(clan1, 0);
        clans.put(clan2, 0);
    }

    public long getStartedAt() {
        return startedAt;
    }

    public List<Clan> getClans() {
        return new ArrayList<>(clans.keySet());
    }

    public int getTotalCasualties() {
        return clans.values().stream().mapToInt(value -> value).sum();
    }

    public int getCasualties(@NotNull Clan clan) {
        return clans.getOrDefault(clan, 0);
    }

    public void increaseCasualties(@NotNull Clan clan) {
        clans.computeIfPresent(clan, (c, i) -> i + 1);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof War) {
            // Only the clans identify a war: casualties change while it is stored as a map key
            return clans.keySet().equals(((War) obj).clans.keySet());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return clans.keySet().hashCode();
    }
}
