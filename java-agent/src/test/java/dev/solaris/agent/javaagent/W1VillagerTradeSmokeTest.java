package dev.solaris.agent.javaagent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class W1VillagerTradeSmokeTest {
    @Test
    void requiresAffordableOneInputInStockOfferWithDistinctOutput() {
        assertTrue(W1VillagerTradeSmoke.eligibleOffer(offer(32, 0, 1, 0, 16)));
        assertTrue(W1VillagerTradeSmoke.eligibleOffer(offer(64, 0, 1, 15, 16)));
        assertFalse(W1VillagerTradeSmoke.eligibleOffer(null));
        assertFalse(W1VillagerTradeSmoke.eligibleOffer(offer(0, 0, 1, 0, 16)));
        assertFalse(W1VillagerTradeSmoke.eligibleOffer(offer(65, 0, 1, 0, 16)));
        assertFalse(W1VillagerTradeSmoke.eligibleOffer(offer(32, 1, 1, 0, 16)));
        assertFalse(W1VillagerTradeSmoke.eligibleOffer(offer(32, 0, 0, 0, 16)));
        assertFalse(W1VillagerTradeSmoke.eligibleOffer(offer(32, 0, 65, 0, 16)));
        assertFalse(W1VillagerTradeSmoke.eligibleOffer(offer(32, 0, 1, -1, 16)));
        assertFalse(W1VillagerTradeSmoke.eligibleOffer(offer(32, 0, 1, 16, 16)));
        assertFalse(W1VillagerTradeSmoke.eligibleOffer(offer(32, 0, 1, 0, 0)));
        assertFalse(W1VillagerTradeSmoke.eligibleOffer(new W1VillagerTradeSmoke.OfferSnapshot(
            "minecraft:stick", 32, 32, 64, 0, "minecraft:stick", 1, 64, 0, 16)));
        assertFalse(W1VillagerTradeSmoke.eligibleOffer(new W1VillagerTradeSmoke.OfferSnapshot(
            "minecraft:air", 32, 32, 64, 0, "minecraft:emerald", 1, 64, 0, 16)));
        assertFalse(W1VillagerTradeSmoke.eligibleOffer(new W1VillagerTradeSmoke.OfferSnapshot(
            "minecraft:stick", 32, 32, 64, 0, "minecraft:air", 1, 64, 0, 16)));
    }

    @Test
    void requiresExactlyOneInputDebitOutputCreditAndOfferUse() {
        var before = new W1VillagerTradeSmoke.TradeState(32, 0, 0, 16);
        assertTrue(W1VillagerTradeSmoke.tradedExactlyOnce(before, state(0, 1, 1, 16), 32, 1));
        // Merely moving payment from inventory into the merchant is not a completed trade.
        assertFalse(W1VillagerTradeSmoke.tradedExactlyOnce(before, state(32, 0, 0, 16), 32, 1));
        assertFalse(W1VillagerTradeSmoke.tradedExactlyOnce(before, state(32, 1, 1, 16), 32, 1));
        assertFalse(W1VillagerTradeSmoke.tradedExactlyOnce(before, state(0, 0, 1, 16), 32, 1));
        assertFalse(W1VillagerTradeSmoke.tradedExactlyOnce(before, state(0, 2, 1, 16), 32, 1));
        assertFalse(W1VillagerTradeSmoke.tradedExactlyOnce(before, state(0, 1, 0, 16), 32, 1));
        assertFalse(W1VillagerTradeSmoke.tradedExactlyOnce(before, state(0, 1, 2, 16), 32, 1));
        assertFalse(W1VillagerTradeSmoke.tradedExactlyOnce(before, state(0, 1, 1, 17), 32, 1));
        assertFalse(W1VillagerTradeSmoke.tradedExactlyOnce(before, state(-32, 2, 2, 16), 32, 1));
        assertFalse(W1VillagerTradeSmoke.tradedExactlyOnce(before, state(0, 1, 1, 16), 0, 1));
        assertFalse(W1VillagerTradeSmoke.tradedExactlyOnce(before, state(0, 1, 1, 16), 32, 0));
    }

    @Test
    void handlesMultiItemResultAndLastAvailableUseWithoutAcceptingRepeatedTrades() {
        var before = state(64, 5, 15, 16);
        assertTrue(W1VillagerTradeSmoke.tradedExactlyOnce(before, state(32, 21, 16, 16), 32, 16));
        assertFalse(W1VillagerTradeSmoke.tradedExactlyOnce(before, state(0, 37, 17, 16), 32, 16));
        assertFalse(W1VillagerTradeSmoke.tradedExactlyOnce(state(64, 5, 16, 16), state(32, 21, 17, 16), 32, 16));
        assertFalse(W1VillagerTradeSmoke.tradedExactlyOnce(state(31, 0, 0, 16), state(-1, 1, 1, 16), 32, 1));
    }

    @Test
    void freshMenuMustRetainSameRecipeAndExactlyOneUseIncludingRemainingStock() {
        var original = offer(32, 0, 1, 0, 16);
        assertTrue(W1VillagerTradeSmoke.persistedOffer(original, offer(32, 0, 1, 1, 16)));
        assertFalse(W1VillagerTradeSmoke.persistedOffer(original, null));
        assertFalse(W1VillagerTradeSmoke.persistedOffer(original, offer(32, 0, 1, 0, 16)));
        assertFalse(W1VillagerTradeSmoke.persistedOffer(original, offer(32, 0, 1, 2, 16)));
        assertFalse(W1VillagerTradeSmoke.persistedOffer(original, offer(32, 0, 1, 1, 17)));
        assertFalse(W1VillagerTradeSmoke.persistedOffer(original, offer(32, 1, 1, 1, 16)));
        assertFalse(W1VillagerTradeSmoke.persistedOffer(original, offer(32, 0, 2, 1, 16)));
        assertFalse(W1VillagerTradeSmoke.persistedOffer(original, new W1VillagerTradeSmoke.OfferSnapshot(
            "minecraft:paper", 32, 32, 64, 0, "minecraft:emerald", 1, 64, 1, 16)));
        assertFalse(W1VillagerTradeSmoke.persistedOffer(original, new W1VillagerTradeSmoke.OfferSnapshot(
            "minecraft:stick", 32, 32, 64, 0, "minecraft:arrow", 1, 64, 1, 16)));
        assertFalse(W1VillagerTradeSmoke.persistedOffer(original, new W1VillagerTradeSmoke.OfferSnapshot(
            "minecraft:stick", 32, 31, 64, 0, "minecraft:emerald", 1, 64, 1, 16)));
    }

    @Test
    void reopeningAllowsAReputationDiscountButNotAnOfferResetOrIdentityChange() {
        var original = offer(32, 0, 1, 15, 16);
        var discounted = new W1VillagerTradeSmoke.OfferSnapshot(
            "minecraft:stick", 31, 32, 64, 0, "minecraft:emerald", 1, 64, 16, 16);
        assertTrue(W1VillagerTradeSmoke.persistedOffer(original, discounted));
        assertFalse(W1VillagerTradeSmoke.persistedOffer(offer(32, 0, 1, 16, 16), discounted));
    }

    private static W1VillagerTradeSmoke.OfferSnapshot offer(int input, int secondInput, int output, int uses, int maxUses) {
        return new W1VillagerTradeSmoke.OfferSnapshot(
            "minecraft:stick", input, 32, 64, secondInput, "minecraft:emerald", output, 64, uses, maxUses);
    }

    private static W1VillagerTradeSmoke.TradeState state(int input, int output, int uses, int maxUses) {
        return new W1VillagerTradeSmoke.TradeState(input, output, uses, maxUses);
    }
}
