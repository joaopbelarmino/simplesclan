package net.sacredlabyrinth.phaed.simpleclans.managers;

import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.Arrays;

import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class DynamicWarManagerTest {

    @TempDir
    File dataFolder;

    private MockedStatic<Bukkit> bukkit;
    private SettingsManager settings;
    private DynamicWarManager manager;
    private Clan red;
    private Clan blue;

    @BeforeEach
    public void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));
        SimpleClans plugin = mock(SimpleClans.class);
        settings = mock(SettingsManager.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plugin.getSettingsManager()).thenReturn(settings);
        when(settings.getInt(DYNAMIC_WAR_HITS_TO_START)).thenReturn(3);
        when(settings.getInt(DYNAMIC_WAR_HITS_WINDOW)).thenReturn(10);
        when(settings.is(DYNAMIC_WAR_ENABLED)).thenReturn(true);
        manager = new DynamicWarManager(plugin);

        red = clan("RED");
        blue = clan("BLUE");
    }

    @AfterEach
    public void tearDown() {
        bukkit.close();
    }

    private static Clan clan(String tag) {
        Clan clan = mock(Clan.class);
        when(clan.getTag()).thenReturn(tag);
        when(clan.isDynamicWarEnabled()).thenReturn(true);
        return clan;
    }

    @Test
    public void needsEnoughHitsInsideTheWindow() {
        assertFalse(manager.registerHit(red, blue, 0));
        assertFalse(manager.registerHit(blue, red, 2_000));
        assertTrue(manager.registerHit(red, blue, 4_000));
    }

    @Test
    public void oldHitsLeaveTheWindow() {
        assertFalse(manager.registerHit(red, blue, 0));
        assertFalse(manager.registerHit(red, blue, 1_000));
        // The first two hits are more than 10 seconds old
        assertFalse(manager.registerHit(red, blue, 12_000));
        assertFalse(manager.registerHit(red, blue, 13_000));
        assertTrue(manager.registerHit(red, blue, 14_000));
    }

    @Test
    public void startsOnTheFirstHitWhenConfiguredSo() {
        when(settings.getInt(DYNAMIC_WAR_HITS_TO_START)).thenReturn(1);
        assertTrue(manager.registerHit(red, blue, 0));
    }

    @Test
    public void alliesAndDisabledClansCannotStartWars() {
        assertTrue(manager.canStart(red, blue));
        assertFalse(manager.canStart(red, red));

        when(red.isAlly("BLUE")).thenReturn(true);
        assertFalse(manager.canStart(red, blue));

        when(red.isAlly("BLUE")).thenReturn(false);
        when(blue.isDynamicWarEnabled()).thenReturn(false);
        assertFalse(manager.canStart(red, blue));
    }

    @Test
    public void disabledWorldsAreBlocked() {
        when(settings.getStringList(DYNAMIC_WAR_DISABLED_WORLDS)).thenReturn(Arrays.asList("spawn", "arena"));
        World spawn = mock(World.class);
        when(spawn.getName()).thenReturn("Spawn");
        World survival = mock(World.class);
        when(survival.getName()).thenReturn("world");

        assertFalse(manager.isAllowedWorld(spawn));
        assertTrue(manager.isAllowedWorld(survival));
        assertFalse(manager.isAllowedWorld(null));
    }
}
