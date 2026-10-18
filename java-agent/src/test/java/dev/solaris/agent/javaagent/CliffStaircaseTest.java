package dev.solaris.agent.javaagent;

import dev.solaris.agent.javaagent.CliffStaircase.Step;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CliffStaircaseTest {
    @Test
    void cutsSupportedStepsThroughAThreeBlockCliffUntilObservedOpenGround() {
        Step origin = new Step(164, 74, 338);
        Predicate<Step> solid = block -> block.x() == 165 ? block.y() <= 76 : block.y() <= 77;
        assertEquals(
            List.of(new Step(165, 75, 338), new Step(166, 76, 338),
                new Step(167, 77, 338), new Step(168, 78, 338)),
            CliffStaircase.plan(origin, Direction.EAST, solid, solid.negate(), solid)
        );
    }

    @Test
    void twoBlockCliffUsesRealExistingFootingsWithoutPillaring() {
        Predicate<Step> solid = block -> block.y() <= 74;
        assertEquals(
            List.of(new Step(166, 74, 320), new Step(167, 75, 320)),
            CliffStaircase.plan(new Step(165, 73, 320), Direction.EAST, solid, solid.negate(), solid)
        );
    }

    @Test
    void refusesUnsupportedUnbreakableAndOverBudgetRoutesWithoutPartialPlan() {
        Step origin = new Step(0, 64, 0);
        assertTrue(CliffStaircase.plan(origin, Direction.EAST, block -> false, block -> true, block -> true).isEmpty());
        assertTrue(CliffStaircase.plan(origin, Direction.EAST, block -> true, block -> false, block -> false).isEmpty());
        assertTrue(CliffStaircase.plan(origin, Direction.EAST, block -> true, block -> false, block -> true).isEmpty());
    }

    @Test
    void checksThirdBlockOfJumpHeadroomAndEveryStepSupport() {
        Step origin = new Step(0, 64, 0);
        Step obstructedJump = new Step(1, 67, 0);
        assertTrue(CliffStaircase.plan(origin, Direction.EAST, block -> true,
            block -> !block.equals(obstructedJump), block -> false).isEmpty());
        assertTrue(CliffStaircase.plan(origin, Direction.EAST,
            block -> block.x() == 1, block -> block.x() > 1, block -> true).isEmpty());
    }

    @Test
    void risingRidgeUsesBoundedSupportedPassageInsteadOfInventingSurfaceArrival() {
        Step origin = new Step(164, 74, 338);
        Predicate<Step> solid = block -> block.y() <= 90;
        assertTrue(CliffStaircase.plan(origin, Direction.EAST, solid, solid.negate(), solid).isEmpty());
        assertEquals(
            List.of(new Step(165, 74, 338), new Step(166, 74, 338),
                new Step(167, 74, 338), new Step(168, 74, 338)),
            CliffStaircase.route(origin, Direction.EAST, solid, solid.negate(), solid)
        );
        assertTrue(CliffStaircase.route(origin, Direction.EAST, block -> false, block -> false, solid).isEmpty());
        assertTrue(CliffStaircase.route(origin, Direction.EAST, solid, block -> false, block -> false).isEmpty());
    }

    @Test
    void keepsSafePrefixWithoutDemandingSupportBeyondTheCaveEndpoint() {
        Step origin = new Step(170, 72, 336);
        assertEquals(
            List.of(new Step(171, 72, 336), new Step(172, 72, 336), new Step(173, 72, 336)),
            CliffStaircase.route(origin, Direction.EAST, block -> block.x() < 174,
                block -> false, block -> true)
        );
        assertEquals(
            List.of(new Step(171, 72, 336)),
            CliffStaircase.route(origin, Direction.EAST, block -> true,
                block -> false, block -> block.x() < 172)
        );
    }

    @Test
    void arbitraryDepthExitIsBoundedBySurfaceRatherThanTheShortCliffLimit() {
        Step origin = new Step(94, 43, 389);
        Predicate<Step> solid = block -> block.y() < 67;
        List<Step> steps = CliffStaircase.toSurface(origin, 67, Direction.EAST, solid, solid.negate(), solid);
        assertEquals(24, steps.size());
        assertEquals(new Step(95, 44, 389), steps.getFirst());
        assertEquals(new Step(118, 67, 389), steps.getLast());
        assertTrue(CliffStaircase.toSurface(origin, 43, Direction.EAST, solid, solid.negate(), solid).isEmpty());
    }

    @Test
    void deepExitKeepsSupportedPrefixForTurningOrEarnedPillarAtCaveGap() {
        Step origin = new Step(0, 43, 0);
        List<Step> steps = CliffStaircase.toSurface(origin, 67, Direction.EAST,
            block -> block.x() < 8, block -> false, block -> true);
        assertEquals(7, steps.size());
        assertEquals(new Step(7, 50, 0), steps.getLast());
        assertTrue(CliffStaircase.toSurface(origin, 67, Direction.EAST,
            block -> true, block -> false, block -> block.y() != 46).isEmpty());
    }

    @Test
    void jumpTakeoffClearsEveryColumnCoveredByTheActualPlayerNotOnlyBlockPosition() {
        assertEquals(List.of(new Step(103, 68, 326), new Step(104, 68, 326)),
            CliffStaircase.takeoffClearance(103.71, 326.30, 104.31, 326.90, 66));
        assertEquals(List.of(new Step(103, 68, 326), new Step(103, 68, 327),
                new Step(104, 68, 326), new Step(104, 68, 327)),
            CliffStaircase.takeoffClearance(103.70, 326.70, 104.30, 327.30, 66));
        assertEquals(List.of(new Step(104, 68, 326)),
            CliffStaircase.takeoffClearance(104.0, 326.0, 105.0, 327.0, 66));
    }

    @Test
    void buriedOreIngressChecksEveryOverburdenLayerWithoutBreakingTheOreOrBlindFalls() {
        Step ore = new Step(135, 60, 310);
        assertTrue(CliffStaircase.shaftClearable(ore, 71, block -> block.y() > 60 && block.y() < 71));
        assertFalse(CliffStaircase.shaftClearable(ore, 71, block -> block.y() != 65));
        assertFalse(CliffStaircase.shaftClearable(ore, 60, block -> true));
        assertTrue(CliffStaircase.shaftClearable(new Step(135, 16, 310), 71, block -> true));
        assertFalse(CliffStaircase.shaftClearable(ore, 133, block -> true));
    }

    @Test
    void clearShaftPillaringRequiresEnoughEarnedBlocksAndObservedFullJumpClearance() {
        Step origin = new Step(135, 16, 310);
        assertTrue(CliffStaircase.pillarFits(origin, 71, 55, block -> true));
        assertFalse(CliffStaircase.pillarFits(origin, 71, 54, block -> true));
        assertFalse(CliffStaircase.pillarFits(origin, 71, 55, block -> block.y() != 73));
        assertFalse(CliffStaircase.pillarFits(origin, 16, 55, block -> true));
    }

    @Test
    void directionUsesTargetVectorWithDeterministicCardinalTieBreaking() {
        assertEquals(Direction.EAST, CliffStaircase.toward(10, 2));
        assertEquals(Direction.WEST, CliffStaircase.toward(-10, 2));
        assertEquals(Direction.NORTH, CliffStaircase.toward(2, -10));
        assertEquals(Direction.SOUTH, CliffStaircase.toward(2, 10));
        assertEquals(Direction.EAST, CliffStaircase.toward(2, 2));
    }
}
