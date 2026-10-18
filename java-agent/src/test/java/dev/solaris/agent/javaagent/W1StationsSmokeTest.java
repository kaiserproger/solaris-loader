package dev.solaris.agent.javaagent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class W1StationsSmokeTest {
    @Test
    void nativePreviewRequiresTheExactItemAndCount() {
        assertTrue(W1StationsSmoke.exactStack("minecraft:cobblestone_slab", 2, "minecraft:cobblestone_slab", 2));
        assertFalse(W1StationsSmoke.exactStack("minecraft:cobblestone_stairs", 2, "minecraft:cobblestone_slab", 2));
        assertFalse(W1StationsSmoke.exactStack("minecraft:cobblestone_slab", 1, "minecraft:cobblestone_slab", 2));
        assertFalse(W1StationsSmoke.exactStack("minecraft:cobblestone_slab", 4, "minecraft:cobblestone_slab", 2));
        assertFalse(W1StationsSmoke.exactStack("minecraft:air", 0, "minecraft:air", 0), "empty slots are not a result");
        assertFalse(W1StationsSmoke.exactStack("minecraft:diamond_sword", 1, "minecraft:netherite_sword", 1));
        assertTrue(W1StationsSmoke.exactStack("minecraft:netherite_sword", 1, "minecraft:netherite_sword", 1));
    }

    @Test
    void stonecutterRejectsUnconsumedInputMissingSlabsAndDuplicateOutput() {
        assertTrue(W1StationsSmoke.stonecutterConserved(0, 2, true, true));
        assertFalse(W1StationsSmoke.stonecutterConserved(1, 2, true, true));
        assertFalse(W1StationsSmoke.stonecutterConserved(0, 0, true, true));
        assertFalse(W1StationsSmoke.stonecutterConserved(0, 1, true, true));
        assertFalse(W1StationsSmoke.stonecutterConserved(0, 4, true, true));
        assertFalse(W1StationsSmoke.stonecutterConserved(0, 2, false, true), "preview left in menu is not taken output");
        assertFalse(W1StationsSmoke.stonecutterConserved(0, 2, true, false));
    }

    @Test
    void smithingConsumesAllThreeInputsExactlyOnce() {
        assertTrue(W1StationsSmoke.smithingConserved(0, 0, 0, 1, true, true));
        assertFalse(W1StationsSmoke.smithingConserved(1, 0, 0, 1, true, true), "template cannot be reused");
        assertFalse(W1StationsSmoke.smithingConserved(0, 1, 0, 1, true, true), "diamond base cannot remain");
        assertFalse(W1StationsSmoke.smithingConserved(0, 0, 1, 1, true, true), "ingot cannot remain");
        assertFalse(W1StationsSmoke.smithingConserved(0, 0, 0, 0, true, true));
        assertFalse(W1StationsSmoke.smithingConserved(0, 0, 0, 2, true, true));
        assertFalse(W1StationsSmoke.smithingConserved(0, 0, 0, 1, false, true));
        assertFalse(W1StationsSmoke.smithingConserved(0, 0, 0, 1, true, false));
    }

    @Test
    void loomChecksThePatternComponentRatherThanTheBannerItemOrDisplayName() {
        assertTrue(pattern("minecraft:white_banner", 1, 1, "minecraft:stripe_bottom", "red"));
        assertFalse(pattern("minecraft:white_banner", 1, 0, "", ""), "plain banner is not patterned output");
        assertFalse(pattern("minecraft:red_banner", 1, 1, "minecraft:stripe_bottom", "red"), "base color must be retained");
        assertFalse(pattern("minecraft:white_banner", 2, 1, "minecraft:stripe_bottom", "red"));
        assertFalse(pattern("minecraft:white_banner", 1, 2, "minecraft:stripe_bottom", "red"), "extra layers must fail");
        assertFalse(pattern("minecraft:white_banner", 1, 1, "minecraft:stripe_top", "red"));
        assertFalse(pattern("minecraft:white_banner", 1, 1, "minecraft:stripe_bottom", "blue"));
        assertFalse(pattern("minecraft:white_banner", 1, 1, null, "red"));
        assertFalse(pattern("minecraft:white_banner", 1, 1, "minecraft:stripe_bottom", null));
    }

    @Test
    void loomConservationRequiresOneActualPatternedBannerAndConsumedDye() {
        assertTrue(W1StationsSmoke.loomConserved(1, 1, 0, true, true));
        assertFalse(W1StationsSmoke.loomConserved(1, 0, 0, true, true));
        assertFalse(W1StationsSmoke.loomConserved(0, 0, 0, true, true));
        assertFalse(W1StationsSmoke.loomConserved(2, 1, 0, true, true));
        assertFalse(W1StationsSmoke.loomConserved(2, 2, 0, true, true));
        assertFalse(W1StationsSmoke.loomConserved(1, 1, 1, true, true));
        assertFalse(W1StationsSmoke.loomConserved(1, 1, 0, false, true));
        assertFalse(W1StationsSmoke.loomConserved(1, 1, 0, true, false));
    }

    @Test
    void composterRequiresExactlyOneReceivedLevelIncrementAndOneConsumedItem() {
        assertTrue(W1StationsSmoke.composterIncrement(0, 1, 1, 0));
        assertTrue(W1StationsSmoke.composterIncrement(3, 4, 2, 1));
        assertTrue(W1StationsSmoke.composterIncrement(6, 7, 1, 0));
        assertFalse(W1StationsSmoke.composterIncrement(0, 0, 1, 0), "consumption alone does not prove composting");
        assertFalse(W1StationsSmoke.composterIncrement(0, 1, 1, 1), "level alone does not prove consumption");
        assertFalse(W1StationsSmoke.composterIncrement(0, 2, 1, 0), "double increment must fail");
        assertFalse(W1StationsSmoke.composterIncrement(0, 1, 2, 0), "double consumption must fail");
        assertFalse(W1StationsSmoke.composterIncrement(-1, 0, 1, 0), "wrong block sentinel must fail");
        assertFalse(W1StationsSmoke.composterIncrement(7, 8, 1, 0), "maturation is not item insertion");
        assertFalse(W1StationsSmoke.composterIncrement(8, 0, 1, 0), "emptying is not insertion");
        assertFalse(W1StationsSmoke.composterIncrement(0, 1, 0, -1), "missing input cannot be consumed");
    }

    private static boolean pattern(String item, int count, int layers, String pattern, String color) {
        return W1StationsSmoke.bannerPatternMatches(item, count, layers, pattern, color);
    }
}
