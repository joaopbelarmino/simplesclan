package net.sacredlabyrinth.phaed.simpleclans.ui.bedrock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BedrockTextTest {

    @Test
    public void keepsLegacyColors() {
        assertEquals("§c[ABC] §fName", BedrockText.color("&c[ABC] &fName"));
    }

    @Test
    public void convertsHexToTheNearestLegacyColor() {
        // §x§f§f§5§5§5§5 is #FF5555, the same as §c
        assertEquals("§cRed", BedrockText.color("§x§f§f§5§5§5§5Red"));
        assertEquals('6', BedrockText.nearestLegacy(0xFFA500));
    }

    @Test
    public void removesStrikethroughAndUnderline() {
        // On Bedrock §m and §n are colors
        assertEquals("§cText", BedrockText.color("§c§m§nText"));
    }

    @Test
    public void darkensLightColorsOnButtons() {
        assertEquals("§0White §6Yellow §2Green §4Red", BedrockText.buttonColor("&fWhite &eYellow &aGreen &4Red"));
    }

    @Test
    public void cleanRemovesEverything() {
        assertEquals("Name", BedrockText.clean("&a&lName"));
        assertEquals("", BedrockText.clean(null));
    }
}
