package dev.solaris.agent.javaagent;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class W1FishingSmokeTest {
    private static final String ROD = "minecraft:fishing_rod";
    private static final String COD = "minecraft:cod";

    @Test
    void biteRequiresSameLiveOwnedBobbingHookAndActualBitingMetadata() {
        UUID hook = UUID.randomUUID();
        assertTrue(W1FishingSmoke.biteReceived(hook, hook, true, true, "BOBBING", true, false));
        assertFalse(W1FishingSmoke.biteReceived(hook, hook, true, true, "BOBBING", false, false));
        assertFalse(W1FishingSmoke.biteReceived(hook, hook, false, true, "BOBBING", true, false));
        assertFalse(W1FishingSmoke.biteReceived(hook, hook, true, false, "BOBBING", true, false));
        assertFalse(W1FishingSmoke.biteReceived(hook, hook, true, true, "FLYING", true, false));
        assertFalse(W1FishingSmoke.biteReceived(hook, hook, true, true, "HOOKED_IN_ENTITY", true, true));
        assertFalse(W1FishingSmoke.biteReceived(hook, hook, true, true, "BOBBING", true, true));
        assertFalse(W1FishingSmoke.biteReceived(hook, UUID.randomUUID(), true, true, "BOBBING", true, false));
        assertFalse(W1FishingSmoke.biteReceived(null, hook, true, true, "BOBBING", true, false));
        assertFalse(W1FishingSmoke.biteReceived(hook, hook, true, true, null, true, false));
    }

    @Test
    void catchRequiresExactlyOneNewLootRollNotJustExistingFish() {
        Map<String, Integer> before = Map.of(ROD, 1, COD, 3);
        assertTrue(W1FishingSmoke.exactlyOneCatch(before, Map.of(ROD, 1, COD, 4)));
        assertFalse(W1FishingSmoke.exactlyOneCatch(before, before));
        assertFalse(W1FishingSmoke.exactlyOneCatch(before, Map.of(ROD, 1, COD, 5)));
        assertFalse(W1FishingSmoke.exactlyOneCatch(before, Map.of(ROD, 1, COD, 2)));
        assertFalse(W1FishingSmoke.exactlyOneCatch(before, Map.of(ROD, 1, COD, 4, "minecraft:salmon", 1)));
    }

    @Test
    void rejectsLootCreditThatHidesLossOfRodOrExistingItems() {
        assertFalse(W1FishingSmoke.exactlyOneCatch(Map.of(ROD, 1), Map.of(COD, 1)));
        assertFalse(W1FishingSmoke.exactlyOneCatch(Map.of(), Map.of(ROD, 1)));
        assertFalse(W1FishingSmoke.exactlyOneCatch(Map.of(ROD, 2), Map.of(ROD, 2, COD, 1)));
        assertFalse(W1FishingSmoke.exactlyOneCatch(Map.of(ROD, 1, "minecraft:stick", 2),
            Map.of(ROD, 1, "minecraft:stick", 1, COD, 1)));
        assertFalse(W1FishingSmoke.exactlyOneCatch(Map.of(ROD, 1, COD, -1), Map.of(ROD, 1, COD, 0)));
        assertFalse(W1FishingSmoke.exactlyOneCatch(Map.of(ROD, 1), Map.of(ROD, 1, COD, -1)));
        assertFalse(W1FishingSmoke.exactlyOneCatch(Map.of(ROD, 1), Map.of(ROD, 1, "minecraft:diamond", 1)));
    }

    @Test
    void allowsVanillaFishTreasureAndJunkWithoutMistakingExtraRodForDuplication() {
        Map<String, Integer> before = Map.of(ROD, 1);
        for (String loot : new String[]{COD, "minecraft:salmon", "minecraft:pufferfish", "minecraft:tropical_fish",
            "minecraft:enchanted_book", "minecraft:bow", "minecraft:nautilus_shell", "minecraft:name_tag",
            "minecraft:saddle", "minecraft:bowl", "minecraft:lily_pad", "minecraft:potion", "minecraft:stick",
            "minecraft:leather_boots", "minecraft:string", "minecraft:bamboo"}) {
            assertTrue(W1FishingSmoke.exactlyOneCatch(before, Map.of(ROD, 1, loot, 1)), loot);
        }
        assertTrue(W1FishingSmoke.exactlyOneCatch(before, Map.of(ROD, 2)));
        assertFalse(W1FishingSmoke.exactlyOneCatch(before, Map.of(ROD, 3)));
    }

    @Test
    void inkSacJunkRequiresExactVanillaTenItemRollNotOneOrTwoRolls() {
        Map<String, Integer> before = Map.of(ROD, 1, "minecraft:ink_sac", 4);
        assertTrue(W1FishingSmoke.exactlyOneCatch(before, Map.of(ROD, 1, "minecraft:ink_sac", 14)));
        assertFalse(W1FishingSmoke.exactlyOneCatch(before, Map.of(ROD, 1, "minecraft:ink_sac", 5)));
        assertFalse(W1FishingSmoke.exactlyOneCatch(before, Map.of(ROD, 1, "minecraft:ink_sac", 24)));
        assertFalse(W1FishingSmoke.exactlyOneCatch(before, Map.of(ROD, 1, "minecraft:ink_sac", 14, COD, 1)));
    }
}
