package net.sacredlabyrinth.phaed.simpleclans;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class WarTest {

    @Test
    public void staysFoundAsAMapKeyAfterCasualties() {
        Clan first = mock(Clan.class);
        Clan second = mock(Clan.class);
        War war = new War(first, second);
        Map<War, String> wars = new HashMap<>();
        wars.put(war, "task");

        war.increaseCasualties(first);
        war.increaseCasualties(second);
        war.increaseCasualties(second);

        assertTrue(wars.containsKey(war));
        assertEquals("task", wars.remove(war));
        assertEquals(1, war.getCasualties(first));
        assertEquals(2, war.getCasualties(second));
    }

    @Test
    public void isIdentifiedByItsClans() {
        Clan first = mock(Clan.class);
        Clan second = mock(Clan.class);
        Clan third = mock(Clan.class);

        assertEquals(new War(first, second), new War(second, first));
        assertNotEquals(new War(first, second), new War(first, third));
    }
}
