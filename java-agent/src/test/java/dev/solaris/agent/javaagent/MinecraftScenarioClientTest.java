package dev.solaris.agent.javaagent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinecraftScenarioClientTest {
    @Test
    void healthThresholdUsesStrictNumericOrdering() {
        assertTrue(MinecraftScenarioClient.healthBelow(0.0F, 0.001F));
        assertTrue(MinecraftScenarioClient.healthBelow(0.0F, Double.MIN_VALUE));
        assertTrue(MinecraftScenarioClient.healthBelow(10.0F, 10.0000001D));
        assertTrue(MinecraftScenarioClient.healthBelow(9.999F, 10.0F));
        assertFalse(MinecraftScenarioClient.healthBelow(10.0F, 10.0F));
        assertFalse(MinecraftScenarioClient.healthBelow(10.001F, 10.0F));
        assertFalse(MinecraftScenarioClient.healthBelow(Float.NaN, 10.0F));
        assertFalse(MinecraftScenarioClient.healthBelow(0.0F, Double.NaN));
    }

    @Test
    void directMovementDoesNotDetourWithoutCollision() {
        assertEquals(
            0,
            MovementDetour.choose(0, 1, false, true, true, true)
        );
    }

    @Test
    void collisionChoosesThePreferredOpenSide() {
        assertEquals(
            -1,
            MovementDetour.choose(0, -1, true, false, true, true)
        );
        assertEquals(
            -1,
            MovementDetour.choose(0, 1, true, false, true, false)
        );
    }

    @Test
    void detourPersistsUntilTheDirectPathIsClear() {
        assertEquals(
            1,
            MovementDetour.choose(1, -1, true, false, true, true)
        );
        assertEquals(
            -1,
            MovementDetour.choose(1, -1, true, false, true, false)
        );
        assertEquals(
            0,
            MovementDetour.choose(1, -1, false, true, true, true)
        );
    }

    @Test
    void blockNavigationAcceptsOnlyObservedGroundedCollisionFreeArrival() {
        assertTrue(BlockNavigation.arrived(10.5, 64.0, -2.5, 10, 64, -3, true, true));
        assertFalse(BlockNavigation.arrived(10.5, 64.0, -2.5, 10, 64, -3, false, true));
        assertFalse(BlockNavigation.arrived(10.5, 64.0, -2.5, 10, 64, -3, true, false));
        assertFalse(BlockNavigation.arrived(12.1, 64.0, -2.5, 10, 64, -3, true, true));
    }

    @Test
    void blockNavigationRejectsTargetsOutsideItsBoundedRoute() {
        assertTrue(BlockNavigation.withinBounds(0.0, 64.0, 0.0, 47, 64, 0));
        assertFalse(BlockNavigation.withinBounds(0.0, 64.0, 0.0, 49, 64, 0));
        assertFalse(BlockNavigation.withinBounds(0.0, 64.0, 0.0, 0, 73, 0));
    }

    @Test
    void blockNavigationDeclaresACompletelyBlockedRouteUnreachable() {
        assertTrue(BlockNavigation.unreachable(new MovementClearance(false, false, false, false)));
        assertFalse(BlockNavigation.unreachable(new MovementClearance(false, true, false, false)));
        assertFalse(BlockNavigation.unreachable(new MovementClearance(false, false, false, true)));
    }

    @Test
    void placementRejectsNeighbourBlockIntersectingPlayerHitbox() {
        assertTrue(BlockPlacementClearance.intersects(
            26.78, 76.0, 8.61,
            27.38, 77.8, 9.21,
            26, 76, 8
        ));
        assertFalse(BlockPlacementClearance.intersects(
            26.78, 76.0, 8.61,
            27.38, 77.8, 9.21,
            25, 76, 8
        ));
    }

    @Test
    void fullBlockPlacementAcceptsConstrainedButUnobstructedSpace() {
        assertTrue(BlockPlacementClearance.allowsFullBlockPlacement(true, true));
    }

    @Test
    void fullBlockPlacementRejectsEntityObstruction() {
        assertFalse(BlockPlacementClearance.allowsFullBlockPlacement(true, false));
    }

    @Test
    void blockPickupUsesTotalInventoryDeltaInsteadOfTheSelectedStack() {
        assertTrue(MinecraftScenarioClient.pickupCountReached(0, 1, 1));
        assertTrue(MinecraftScenarioClient.pickupCountReached(7, 8, 1));
        assertTrue(MinecraftScenarioClient.pickupCountReached(7, 9, 1));
        assertFalse(MinecraftScenarioClient.pickupCountReached(7, 7, 1));
        assertFalse(MinecraftScenarioClient.pickupCountReached(7, 8, 2));
    }

    @Test
    void respawnRequiresHealthAppliedAfterDispatch() throws Exception {
        RespawnWaitProbe probe = new RespawnWaitProbe();
        probe.healthVersion = 4;
        probe.activePlayer = true;

        assertFalse(MinecraftScenarioClient.waitForRespawnConfirmation(
            probe,
            4,
            Long.MAX_VALUE
        ));
        assertEquals(1, probe.healthWaits);
        assertEquals(0, probe.stateWaits);
    }

    @Test
    void respawnWaitsForLiveStateAfterAuthoritativeHealth() throws Exception {
        RespawnWaitProbe probe = new RespawnWaitProbe();
        probe.healthChangeOnWait = true;
        probe.activateOnStateWait = true;

        assertTrue(MinecraftScenarioClient.waitForRespawnConfirmation(
            probe,
            0,
            Long.MAX_VALUE
        ));
        assertEquals(1, probe.healthWaits);
        assertEquals(1, probe.stateWaits);
    }

    @Test
    void blockPickupAcceptsAnInventoryEventAppliedBeforeItsFirstSample() throws Exception {
        InventoryPickupProbe probe = new InventoryPickupProbe(
            new MinecraftScenarioClient.InventoryPickupSample(8, false)
        );

        MinecraftScenarioClient.InventoryPickupResult result =
            MinecraftScenarioClient.waitForInventoryPickup(probe, 7, 1, Long.MAX_VALUE);

        assertTrue(result.confirmed());
        assertFalse(result.sawDrop());
        assertEquals(0, probe.awaitCalls);
    }

    @Test
    void blockPickupKeepsDropEvidenceAcrossUnrelatedAppliedEvents() throws Exception {
        InventoryPickupProbe probe = new InventoryPickupProbe(
            new MinecraftScenarioClient.InventoryPickupSample(7, false),
            new MinecraftScenarioClient.InventoryPickupSample(7, true),
            new MinecraftScenarioClient.InventoryPickupSample(8, false)
        );

        MinecraftScenarioClient.InventoryPickupResult result =
            MinecraftScenarioClient.waitForInventoryPickup(probe, 7, 1, Long.MAX_VALUE);

        assertTrue(result.confirmed());
        assertTrue(result.sawDrop());
        assertEquals(2, probe.awaitCalls);
        assertEquals(3, probe.sampleCalls);
    }

    @Test
    void blockPickupTimeoutIsFailure() throws Exception {
        InventoryPickupProbe probe = new InventoryPickupProbe(
            new MinecraftScenarioClient.InventoryPickupSample(7, true)
        );

        MinecraftScenarioClient.InventoryPickupResult result =
            MinecraftScenarioClient.waitForInventoryPickup(probe, 7, 1, 0L);

        assertFalse(result.confirmed());
        assertTrue(result.sawDrop());
        assertEquals(1, probe.awaitCalls);
        assertEquals(1, probe.sampleCalls);
    }

    @Test
    void experienceWaitUsesAppliedPacketEvents() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/dev/solaris/agent/javaagent/MinecraftScenarioClient.java"
        ));
        String method = source.substring(
            source.indexOf("public int waitForTotalExperienceAbove("),
            source.indexOf("public boolean waitForDayTimeAtOrAfter(")
        );

        assertTrue(method.contains("ClientStateEvents.version()"));
        assertTrue(method.contains("awaitClientStateChange(observedVersion, deadlineNanos)"));
    }


    @Test
    void entityAttackWaitsForVanillaCooldown() {
        assertFalse(AttackCadence.ready(0.89F));
        assertTrue(AttackCadence.ready(0.90F));
        assertTrue(AttackCadence.ready(1.0F));
    }

    @Test
    void entityReachPartitionsAtOneExactSquaredBoundary() {
        double boundary = 20.25;
        double immediatelyOutside = Math.nextUp(boundary);

        assertTrue(ScenarioReach.WITHIN_SURVIVAL_REACH.includes(boundary));
        assertFalse(ScenarioReach.OUTSIDE_SURVIVAL_REACH.includes(boundary));
        assertFalse(ScenarioReach.WITHIN_SURVIVAL_REACH.includes(immediatelyOutside));
        assertTrue(ScenarioReach.OUTSIDE_SURVIVAL_REACH.includes(immediatelyOutside));
    }

    @Test
    void blockUseMapsBothVanillaInteractionHands() {
        assertEquals(
            net.minecraft.world.InteractionHand.MAIN_HAND,
            MinecraftScenarioClient.interactionHand("main_hand")
        );
        assertEquals(
            net.minecraft.world.InteractionHand.OFF_HAND,
            MinecraftScenarioClient.interactionHand("off_hand")
        );
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> MinecraftScenarioClient.interactionHand("left")
        );
    }

    @Test
    void mapsAllVanillaDyeColorsToWoolItems() throws Exception {
        Map<String, String> expected = Map.ofEntries(
            Map.entry("WHITE", "minecraft:white_wool"),
            Map.entry("ORANGE", "minecraft:orange_wool"),
            Map.entry("MAGENTA", "minecraft:magenta_wool"),
            Map.entry("LIGHT_BLUE", "minecraft:light_blue_wool"),
            Map.entry("YELLOW", "minecraft:yellow_wool"),
            Map.entry("LIME", "minecraft:lime_wool"),
            Map.entry("PINK", "minecraft:pink_wool"),
            Map.entry("GRAY", "minecraft:gray_wool"),
            Map.entry("LIGHT_GRAY", "minecraft:light_gray_wool"),
            Map.entry("CYAN", "minecraft:cyan_wool"),
            Map.entry("PURPLE", "minecraft:purple_wool"),
            Map.entry("BLUE", "minecraft:blue_wool"),
            Map.entry("BROWN", "minecraft:brown_wool"),
            Map.entry("GREEN", "minecraft:green_wool"),
            Map.entry("RED", "minecraft:red_wool"),
            Map.entry("BLACK", "minecraft:black_wool")
        );
        assertEquals(16, expected.size());
        for (Map.Entry<String, String> mapping : expected.entrySet()) {
            assertEquals(mapping.getValue(), SheepWoolColor.itemId(mapping.getKey()), mapping.getKey());
        }
    }


    private static final class InventoryPickupProbe
        implements MinecraftScenarioClient.InventoryPickupWaitSource {
        private final MinecraftScenarioClient.InventoryPickupSample[] samples;
        private int index;
        private int sampleCalls;
        private int awaitCalls;

        private InventoryPickupProbe(MinecraftScenarioClient.InventoryPickupSample... samples) {
            this.samples = samples;
        }

        @Override
        public long stateVersion() {
            return index;
        }

        @Override
        public MinecraftScenarioClient.InventoryPickupSample sample() {
            sampleCalls += 1;
            return samples[index];
        }

        @Override
        public boolean awaitStateChange(long observedVersion, long deadlineNanos) {
            awaitCalls += 1;
            if (index + 1 >= samples.length) {
                return false;
            }
            index += 1;
            return true;
        }
    }


    private static final class RespawnWaitProbe implements MinecraftScenarioClient.RespawnWaitSource {
        private long healthVersion;
        private long tickVersion;
        private long stateVersion;
        private boolean activePlayer;
        private boolean healthChangeOnWait;
        private boolean activateOnStateWait;
        private int healthWaits;
        private int stateWaits;

        @Override
        public long healthVersion() {
            return healthVersion;
        }

        @Override
        public long tickVersion() {
            return tickVersion;
        }

        @Override
        public long stateVersion() {
            return stateVersion;
        }

        @Override
        public boolean activePlayer() {
            return activePlayer;
        }

        @Override
        public boolean awaitHealthChange(long observedVersion, long deadlineNanos) {
            healthWaits += 1;
            if (!healthChangeOnWait) {
                return false;
            }
            healthVersion = observedVersion + 1;
            return true;
        }

        @Override
        public boolean awaitTickOrStateChange(
            long observedTickVersion,
            long observedStateVersion,
            long deadlineNanos
        ) {
            stateWaits += 1;
            if (!activateOnStateWait) {
                return false;
            }
            tickVersion = observedTickVersion + 1;
            stateVersion = observedStateVersion + 1;
            activePlayer = true;
            return true;
        }
    }

    @Test
    void containerMutationRequiresAuthoritativeServerStateAdvance() {
        assertFalse(
            ScenarioClient.authoritativeContainerUpdateMatches(4, 7, 4, 7, true),
            "the locally predicted slot mutation must not count as a server commit"
        );
        assertFalse(
            ScenarioClient.authoritativeContainerUpdateMatches(4, 7, 5, 8, true),
            "an update for a different open container must not confirm the click"
        );
        assertFalse(
            ScenarioClient.authoritativeContainerUpdateMatches(4, 7, 4, 8, false),
            "a server response with the wrong slot contents must fail closed"
        );
        assertTrue(
            ScenarioClient.authoritativeContainerUpdateMatches(4, 7, 4, 8, true),
            "the expected slot after a server state advance confirms the click"
        );
    }
}
