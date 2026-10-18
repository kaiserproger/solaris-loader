package dev.solaris.agent.javaagent;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class W1RangedSmokeTest {
    @Test
    void healthDropRequiresSameLivingMobAndFiniteStrictDecrease() {
        UUID mob = UUID.randomUUID();
        assertTrue(W1RangedSmoke.healthDropped(mob, mob, 100, 91));
        assertFalse(W1RangedSmoke.healthDropped(mob, mob, 100, 100));
        assertFalse(W1RangedSmoke.healthDropped(mob, mob, 91, 100));
        assertFalse(W1RangedSmoke.healthDropped(mob, UUID.randomUUID(), 100, 91));
        assertFalse(W1RangedSmoke.healthDropped(null, mob, 100, 91));
        assertFalse(W1RangedSmoke.healthDropped(mob, mob, 100, 0));
        assertFalse(W1RangedSmoke.healthDropped(mob, mob, 100, Float.NaN));
        assertFalse(W1RangedSmoke.healthDropped(mob, mob, Float.POSITIVE_INFINITY, 91));
    }

    @Test
    void arrowPickupRequiresOneShotOneCreditAndEntityRemoval() {
        assertTrue(W1RangedSmoke.exactArrowPickup(3, 2, 3, true));
        assertTrue(W1RangedSmoke.exactArrowPickup(1, 0, 1, true));
        assertFalse(W1RangedSmoke.exactArrowPickup(3, 3, 3, true));
        assertFalse(W1RangedSmoke.exactArrowPickup(3, 2, 2, true));
        assertFalse(W1RangedSmoke.exactArrowPickup(3, 2, 4, true));
        assertFalse(W1RangedSmoke.exactArrowPickup(3, 1, 2, true));
        assertFalse(W1RangedSmoke.exactArrowPickup(3, 2, 3, false));
        assertFalse(W1RangedSmoke.exactArrowPickup(0, -1, 0, true));
    }

    @Test
    void crossbowLoadRequiresExactArrowComponentAndExactAmmoConsumption() {
        assertTrue(W1RangedSmoke.crossbowLoaded(0, 1, true, 3, 2));
        assertFalse(W1RangedSmoke.crossbowLoaded(1, 1, true, 3, 2));
        assertFalse(W1RangedSmoke.crossbowLoaded(0, 0, true, 3, 2));
        assertFalse(W1RangedSmoke.crossbowLoaded(0, 3, true, 3, 2));
        assertFalse(W1RangedSmoke.crossbowLoaded(0, 1, false, 3, 2));
        assertFalse(W1RangedSmoke.crossbowLoaded(0, 1, true, 3, 3));
        assertFalse(W1RangedSmoke.crossbowLoaded(0, 1, true, 3, 1));
        assertFalse(W1RangedSmoke.crossbowLoaded(0, 1, true, 0, -1));
    }

    @Test
    void crossbowFireRequiresClearedChargeNewProjectileAndNoSecondAmmoDebit() {
        assertTrue(W1RangedSmoke.crossbowFired(1, 0, true, 2, 2));
        assertTrue(W1RangedSmoke.crossbowFired(1, 0, true, 0, 0));
        assertFalse(W1RangedSmoke.crossbowFired(0, 0, true, 2, 2));
        assertFalse(W1RangedSmoke.crossbowFired(1, 1, true, 2, 2));
        assertFalse(W1RangedSmoke.crossbowFired(1, 0, false, 2, 2));
        assertFalse(W1RangedSmoke.crossbowFired(1, 0, true, 2, 1));
        assertFalse(W1RangedSmoke.crossbowFired(1, 0, true, 2, 3));
        assertFalse(W1RangedSmoke.crossbowFired(1, 0, true, -1, -1));
    }

    @Test
    void loyaltyReturnRejectsCreditWithoutThrowReturnStateRemovalOrPreservedEnchantment() {
        assertTrue(W1RangedSmoke.loyaltyReturned(1, 0, 1, true, true, 3));
        assertFalse(W1RangedSmoke.loyaltyReturned(1, 1, 1, true, true, 3));
        assertFalse(W1RangedSmoke.loyaltyReturned(2, 0, 1, true, true, 3));
        assertFalse(W1RangedSmoke.loyaltyReturned(1, 0, 0, true, true, 3));
        assertFalse(W1RangedSmoke.loyaltyReturned(1, 0, 2, true, true, 3));
        assertFalse(W1RangedSmoke.loyaltyReturned(1, 0, 1, false, true, 3));
        assertFalse(W1RangedSmoke.loyaltyReturned(1, 0, 1, true, false, 3));
        assertFalse(W1RangedSmoke.loyaltyReturned(1, 0, 1, true, true, 0));
    }

    @Test
    void pearlTeleportRejectsFallingDriftUnconsumedPearlsAndActiveProjectiles() {
        assertTrue(W1RangedSmoke.pearlTeleport(0, 80, 0, 3, 80, 0, true, 1, 0));
        assertTrue(W1RangedSmoke.pearlTeleport(0, 80, 0, -5, 79, 5, true, 1, 0));
        assertFalse(W1RangedSmoke.pearlTeleport(0, 80, 0, 0, 70, 0, true, 1, 0));
        assertFalse(W1RangedSmoke.pearlTeleport(0, 80, 0, 2.99, 80, 0, true, 1, 0));
        assertFalse(W1RangedSmoke.pearlTeleport(0, 80, 0, 5, 80, 0, false, 1, 0));
        assertFalse(W1RangedSmoke.pearlTeleport(0, 80, 0, 5, 80, 0, true, 1, 1));
        assertFalse(W1RangedSmoke.pearlTeleport(0, 80, 0, 5, 80, 0, true, 2, 0));
        assertFalse(W1RangedSmoke.pearlTeleport(0, 80, 0, Double.NaN, 80, 0, true, 1, 0));
        assertFalse(W1RangedSmoke.pearlTeleport(0, 80, 0, 5, Double.POSITIVE_INFINITY, 0, true, 1, 0));
    }
}
