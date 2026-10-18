package dev.solaris.agent.javaagent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class W1BrewingSmokeTest {
    @Test
    void activeProgressRequiresARealFuelChargeAndOneBrewConsumption() {
        assertTrue(W1BrewingSmoke.activeBrew(400, 19));
        assertTrue(W1BrewingSmoke.activeBrew(1, 19));
        assertFalse(W1BrewingSmoke.activeBrew(0, 19), "idle stand is not active brewing");
        assertFalse(W1BrewingSmoke.activeBrew(-1, 19));
        assertFalse(W1BrewingSmoke.activeBrew(401, 19));
        assertFalse(W1BrewingSmoke.activeBrew(400, 0), "progress alone does not prove received fuel");
        assertFalse(W1BrewingSmoke.activeBrew(400, 20), "loaded but unspent fuel is not a started brew");
        assertFalse(W1BrewingSmoke.activeBrew(400, 18), "a second fuel use violates the exact one-brew fixture");
    }

    @Test
    void potionOracleReadsActualPotionTypeNotNameColorOrTheBottleItemAlone() {
        assertTrue(potion("minecraft:potion", 1, "minecraft:awkward", 0));
        assertFalse(potion("minecraft:potion", 1, "minecraft:water", 0), "unbrewed water must fail");
        assertFalse(potion("minecraft:potion", 1, "minecraft:mundane", 0));
        assertFalse(potion("minecraft:potion", 1, "", 0), "missing PotionContents type must fail");
        assertFalse(potion("minecraft:potion", 1, null, 0));
        assertFalse(potion("minecraft:glass_bottle", 1, "minecraft:awkward", 0));
        assertFalse(potion("minecraft:splash_potion", 1, "minecraft:awkward", 0));
        assertFalse(potion("minecraft:potion", 0, "minecraft:awkward", 0));
        assertFalse(potion("minecraft:potion", 2, "minecraft:awkward", 0), "duplicate result must fail");
        assertFalse(potion("minecraft:potion", 1, "minecraft:awkward", 1), "unexpected custom effects must fail");
        assertTrue(W1BrewingSmoke.potionMatches("minecraft:potion", 1, "minecraft:water", 0, "minecraft:water"));
    }

    @Test
    void completedBrewRequiresTakenResultAndExactConsumption() {
        assertTrue(completed(0, 19, 0, 0, 1, 1, true, true));
        assertFalse(completed(1, 19, 0, 0, 1, 1, true, true), "result before completion is insufficient");
        assertFalse(completed(0, 20, 0, 0, 1, 1, true, true));
        assertFalse(completed(0, 18, 0, 0, 1, 1, true, true));
        assertFalse(completed(0, 19, 1, 0, 1, 1, true, true), "wart must be consumed");
        assertFalse(completed(0, 19, 0, 1, 1, 1, true, true), "blaze powder must be consumed");
        assertFalse(completed(0, 19, 0, 0, 0, 0, true, true), "lost bottle is not conservation");
        assertFalse(completed(0, 19, 0, 0, 2, 1, true, true), "extra water bottle must fail");
        assertFalse(completed(0, 19, 0, 0, 2, 2, true, true), "duplicated awkward output must fail");
        assertFalse(completed(0, 19, 0, 0, 1, 0, true, true), "ordinary potion item is not awkward");
        assertFalse(completed(0, 19, 0, 0, 1, 1, false, true), "residual menu stacks must fail");
        assertFalse(completed(0, 19, 0, 0, 1, 1, true, false), "carried cursor item is not inventory conservation");
    }

    private static boolean potion(String item, int count, String potion, int effects) {
        return W1BrewingSmoke.potionMatches(item, count, potion, effects, "minecraft:awkward");
    }

    private static boolean completed(int ticks, int fuel, int wart, int powder, int bottles, int awkward,
                                     boolean empty, boolean cursorEmpty) {
        return W1BrewingSmoke.completedBrew(ticks, fuel, wart, powder, bottles, awkward, empty, cursorEmpty);
    }
}
