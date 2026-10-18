package dev.solaris.agent.javaagent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CombatTacticsTest {
    @Test
    void ordinaryHostilesMustBeBeyondSixBlocksFromBothPlayerAndTarget() {
        assertTrue(CombatTactics.hostileTooClose(false, 36.0, 100.0));
        assertTrue(CombatTactics.hostileTooClose(false, 100.0, 36.0));
        assertFalse(CombatTactics.hostileTooClose(false, Math.nextUp(36.0), Math.nextUp(36.0)));
        assertTrue(CombatTactics.hostileTooClose(false, 0.0, 100.0));
        assertTrue(CombatTactics.hostileTooClose(false, 100.0, 0.0));
    }

    @Test
    void creepersMustBeBeyondSevenBlocksFromBothPlayerAndTarget() {
        assertTrue(CombatTactics.hostileTooClose(true, 49.0, 100.0));
        assertTrue(CombatTactics.hostileTooClose(true, 100.0, 49.0));
        assertFalse(CombatTactics.hostileTooClose(true, Math.nextUp(49.0), Math.nextUp(49.0)));
        assertFalse(CombatTactics.hostileTooClose(false, 49.0, 49.0));
    }

    @Test
    void supportAllowsExactlyThreeBlocksDownButNotMore() {
        assertTrue(CombatTactics.safeSupport(64.0, 60));
        assertFalse(CombatTactics.safeSupport(Math.nextUp(64.0), 60));
        assertFalse(CombatTactics.safeSupport(64.0, 59));
    }

    @Test
    void supportAllowsExactlyOneBlockUpButNotMore() {
        assertTrue(CombatTactics.safeSupport(64.0, 64));
        assertTrue(CombatTactics.safeSupport(64.0, 63));
        assertFalse(CombatTactics.safeSupport(Math.nextDown(64.0), 64));
        assertFalse(CombatTactics.safeSupport(64.0, 65));
    }

    @Test
    void supportRejectsNonfiniteFeetAndDoesNotOverflowIntegerSupportTop() {
        assertFalse(CombatTactics.safeSupport(Double.NaN, 63));
        assertFalse(CombatTactics.safeSupport(Double.POSITIVE_INFINITY, 63));
        assertFalse(CombatTactics.safeSupport(Double.NEGATIVE_INFINITY, 63));
        assertTrue(CombatTactics.safeSupport(2147483648.0, Integer.MAX_VALUE));
    }

    @Test
    void readyMovementApproachesOnlyBeyondItsBoundary() {
        assertEquals(0, CombatTactics.movement(Math.nextDown(7.29), true));
        assertEquals(0, CombatTactics.movement(7.29, true));
        assertEquals(1, CombatTactics.movement(Math.nextUp(7.29), true));
        assertEquals(1, CombatTactics.movement(100.0, true));
    }

    @Test
    void notReadyMovementRetreatsOnlyInsideItsBoundary() {
        assertEquals(-1, CombatTactics.movement(Math.nextDown(12.25), false));
        assertEquals(0, CombatTactics.movement(12.25, false));
        assertEquals(0, CombatTactics.movement(Math.nextUp(12.25), false));
        assertEquals(-1, CombatTactics.movement(0.0, false));
        assertEquals(0, CombatTactics.movement(100.0, false));
    }

    @Test
    void collectionRequiresNoNearbyHostileAndAtMostThreeBlocksDown() {
        assertTrue(CombatTactics.dropChaseAllowed(64.0, 61.0, false));
        assertFalse(CombatTactics.dropChaseAllowed(Math.nextUp(64.0), 61.0, false));
        assertFalse(CombatTactics.dropChaseAllowed(64.0, 60.0, false));
        assertTrue(CombatTactics.dropChaseAllowed(64.0, 64.0, false));
        assertTrue(CombatTactics.dropChaseAllowed(64.0, 65.0, false));
        assertFalse(CombatTactics.dropChaseAllowed(64.0, 61.0, true));
        assertFalse(CombatTactics.dropChaseAllowed(64.0, 64.0, true));
        assertFalse(CombatTactics.dropChaseAllowed(64.0, 65.0, true));
    }

    @Test
    void collectionRejectsNonfinitePlayerOrDropCoordinates() {
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertFalse(CombatTactics.dropChaseAllowed(invalid, 64.0, false));
            assertFalse(CombatTactics.dropChaseAllowed(64.0, invalid, false));
        }
    }

    @Test
    void recoveryUsesTenToFourteenHealthHysteresis() {
        CombatTactics.Recovery recovery = new CombatTactics.Recovery();
        assertFalse(recovery.recovering());
        assertFalse(recovery.ateOwnedFood());
        recovery.update(Math.nextUp(10.0f), false);
        assertFalse(recovery.recovering());
        recovery.update(10.0f, false);
        assertTrue(recovery.recovering());
        recovery.update(Math.nextDown(14.0f), false);
        assertTrue(recovery.recovering());
        recovery.update(14.0f, false);
        assertFalse(recovery.recovering());
        recovery.update(12.0f, false);
        assertFalse(recovery.recovering());
    }

    @Test
    void availableOwnedFoodRequiresObservedConsumptionBeforeReengagement() {
        CombatTactics.Recovery recovery = new CombatTactics.Recovery();
        recovery.update(10.0f, true);
        recovery.update(14.0f, true);
        assertTrue(recovery.recovering());
        assertFalse(recovery.ateOwnedFood());
        recovery.update(20.0f, true);
        assertTrue(recovery.recovering());
        recovery.foodConsumed();
        assertTrue(recovery.ateOwnedFood());
        assertTrue(recovery.recovering());
        recovery.update(14.0f, true);
        assertFalse(recovery.recovering());
    }

    @Test
    void consumptionDoesNotBypassHealthThresholdOrResetDuringTheSameRecovery() {
        CombatTactics.Recovery recovery = new CombatTactics.Recovery();
        recovery.update(9.0f, true);
        recovery.foodConsumed();
        recovery.update(8.0f, true);
        assertTrue(recovery.ateOwnedFood());
        recovery.update(13.0f, true);
        assertTrue(recovery.recovering());
        recovery.update(14.0f, true);
        assertFalse(recovery.recovering());
        assertTrue(recovery.ateOwnedFood());
        recovery.update(10.0f, true);
        assertTrue(recovery.recovering());
        assertFalse(recovery.ateOwnedFood());
    }

    @Test
    void consumptionOutsideRecoveryIsNotCredited() {
        CombatTactics.Recovery recovery = new CombatTactics.Recovery();
        recovery.foodConsumed();
        assertFalse(recovery.ateOwnedFood());
        recovery.update(10.0f, true);
        recovery.update(14.0f, true);
        assertTrue(recovery.recovering());
        assertFalse(recovery.ateOwnedFood());
    }

    @Test
    void noFoodAllowsNaturalRecoveryEvenWhenPreviouslyAvailable() {
        CombatTactics.Recovery recovery = new CombatTactics.Recovery();
        recovery.update(10.0f, true);
        recovery.update(14.0f, true);
        assertTrue(recovery.recovering());
        recovery.update(14.0f, false);
        assertFalse(recovery.recovering());
        assertFalse(recovery.ateOwnedFood());
    }

    @Test
    void nonfiniteHealthFailsClosedAndPreservesConsumptionWithinRecovery() {
        for (float invalid : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            CombatTactics.Recovery recovery = new CombatTactics.Recovery();
            recovery.update(invalid, false);
            assertTrue(recovery.recovering());
            assertFalse(recovery.ateOwnedFood());
            recovery.foodConsumed();
            recovery.update(invalid, true);
            assertTrue(recovery.recovering());
            assertTrue(recovery.ateOwnedFood());
            recovery.update(14.0f, true);
            assertFalse(recovery.recovering());
            recovery.update(invalid, true);
            assertTrue(recovery.recovering());
            assertFalse(recovery.ateOwnedFood());
        }
    }
}
