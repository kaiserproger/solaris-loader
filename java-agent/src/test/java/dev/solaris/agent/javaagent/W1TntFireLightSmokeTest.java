package dev.solaris.agent.javaagent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class W1TntFireLightSmokeTest {
    @Test
    void acceptsReceivedBlockLightRiseWithTorchPresent() {
        assertTrue(W1TntFireLightSmoke.lightRose(0, 13, true));
        assertTrue(W1TntFireLightSmoke.lightRose(8, 13, true));
    }

    @Test
    void rejectsTorchPlacementWithoutAnyLightUpdate() {
        assertFalse(W1TntFireLightSmoke.lightRose(0, 0, true));
        assertFalse(W1TntFireLightSmoke.lightRose(13, 13, true));
        assertFalse(W1TntFireLightSmoke.lightRose(13, 12, true));
        assertFalse(W1TntFireLightSmoke.lightRose(0, 13, false));
    }

    @Test
    void rejectsInvalidLightSamplesAndSaturatedBaseline() {
        assertFalse(W1TntFireLightSmoke.lightRose(-1, 13, true));
        assertFalse(W1TntFireLightSmoke.lightRose(0, 16, true));
        assertFalse(W1TntFireLightSmoke.lightRose(15, 15, true));
        assertFalse(W1TntFireLightSmoke.lightRose(16, 17, true));
    }

    @Test
    void requiresReceivedPrimingThenRemovalOfTheWholeBlastFixture() {
        assertTrue(W1TntFireLightSmoke.explosionRemovedFixture(true, true, 3, 3, true));
        assertFalse(W1TntFireLightSmoke.explosionRemovedFixture(false, true, 3, 3, true));
        assertFalse(W1TntFireLightSmoke.explosionRemovedFixture(true, false, 3, 3, true));
        assertFalse(W1TntFireLightSmoke.explosionRemovedFixture(true, true, 3, 3, false));
    }

    @Test
    void rejectsOnlyTheIgnitedTntDisappearingAndPartialFixtureDamage() {
        assertFalse(W1TntFireLightSmoke.explosionRemovedFixture(true, true, 3, 0, true));
        assertFalse(W1TntFireLightSmoke.explosionRemovedFixture(true, true, 3, 1, true));
        assertFalse(W1TntFireLightSmoke.explosionRemovedFixture(true, true, 3, 2, true));
        assertFalse(W1TntFireLightSmoke.explosionRemovedFixture(true, true, 3, 4, true));
    }

    @Test
    void rejectsEmptyOrSingleBlockBlastFixture() {
        assertFalse(W1TntFireLightSmoke.explosionRemovedFixture(true, true, 0, 0, true));
        assertFalse(W1TntFireLightSmoke.explosionRemovedFixture(true, true, 1, 1, true));
        assertFalse(W1TntFireLightSmoke.explosionRemovedFixture(true, true, -1, -1, true));
    }

    @Test
    void acceptsNewFireOnlyOnAnInitiallyUnlitNeighborAfterNativeIgnition() {
        assertTrue(W1TntFireLightSmoke.fireSpread(true, true, true, 1, true));
        assertFalse(W1TntFireLightSmoke.fireSpread(false, true, true, 1, true));
        assertFalse(W1TntFireLightSmoke.fireSpread(true, false, true, 1, true));
        assertFalse(W1TntFireLightSmoke.fireSpread(true, true, false, 1, true));
        assertFalse(W1TntFireLightSmoke.fireSpread(true, true, true, 1, false));
    }

    @Test
    void rejectsSourceFlameAndUnrelatedDistantFireAsSpread() {
        assertFalse(W1TntFireLightSmoke.fireSpread(true, true, true, 0, true));
        assertFalse(W1TntFireLightSmoke.fireSpread(true, true, true, 2, true));
        assertFalse(W1TntFireLightSmoke.fireSpread(true, true, true, 8, true));
        assertFalse(W1TntFireLightSmoke.fireSpread(true, true, true, -1, true));
    }
}
