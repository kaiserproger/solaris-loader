package dev.solaris.agent.javaagent;

import net.minecraft.world.inventory.SimpleContainerData;
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
    void ownedPillarBudgetPreservesCobblestoneRequiredForTheEarnedRecipe() {
        assertEquals(4, MinecraftScenarioClient.availablePillarBlocks(4, 8, 8));
        assertEquals(6, MinecraftScenarioClient.availablePillarBlocks(4, 10, 8));
        assertEquals(12, MinecraftScenarioClient.availablePillarBlocks(4, 8, 0));
        assertEquals(4, MinecraftScenarioClient.availablePillarBlocks(4, 6, 8));
    }

    @Test
    void furnaceSnapshotReadsReceivedRawIndicesWithoutConfusingTotalWithRemainingBurn() {
        SimpleContainerData data = new SimpleContainerData(4);
        data.set(0, 17);
        data.set(1, 300);
        data.set(2, 90);
        data.set(3, 200);
        assertEquals(new ScenarioFurnaceState(17, 300, 90, 200, "minecraft:acacia_planks", 2),
            MinecraftScenarioClient.furnaceSnapshot(data, "minecraft:acacia_planks", 2));
        data.set(0, 0);
        data.set(2, 0);
        ScenarioFurnaceState expired = MinecraftScenarioClient.furnaceSnapshot(data, "minecraft:air", 0);
        assertEquals(0, expired.burnRemaining());
        assertEquals(300, expired.burnTotal());
        assertEquals(0, expired.fuelCount());
    }

    @Test
    void shaftAndPillarCenteringRequiresStoppedGroundedHitboxInsideTheActualColumn() {
        assertTrue(MinecraftScenarioClient.groundedInsideColumn(
            135.18, 310.18, 135.78, 310.78, 0, 0, 135, 310, true));
        assertFalse(MinecraftScenarioClient.groundedInsideColumn(
            134.93, 310.48, 135.53, 311.08, 0, 0, 135, 310, true));
        assertFalse(MinecraftScenarioClient.groundedInsideColumn(
            135.18, 310.18, 135.78, 310.78, 0.02, 0, 135, 310, true));
        assertFalse(MinecraftScenarioClient.groundedInsideColumn(
            135.18, 310.18, 135.78, 310.78, 0, 0, 135, 310, false));
    }

    @Test
    void excavationRequiresTheActualNaturalBlockDropRatherThanAssumingEveryRockIsCobblestone() {
        assertEquals("minecraft:cobblestone", MinecraftScenarioClient.excavationDrop("minecraft:stone"));
        assertEquals("minecraft:cobbled_deepslate", MinecraftScenarioClient.excavationDrop("minecraft:deepslate"));
        assertEquals("minecraft:dirt", MinecraftScenarioClient.excavationDrop("minecraft:grass_block"));
        assertEquals("minecraft:dirt", MinecraftScenarioClient.excavationDrop("minecraft:dirt"));
        assertEquals("minecraft:andesite", MinecraftScenarioClient.excavationDrop("minecraft:andesite"));
        assertEquals("minecraft:cobblestone", MinecraftScenarioClient.excavationDrop("minecraft:cobblestone"));
    }

    @Test
    void stonePickupDeclinesDeepDropsInsteadOfDescendingNaturalShafts() {
        assertTrue(MinecraftScenarioClient.shallowStonePickup(66, 66.2));
        assertTrue(MinecraftScenarioClient.shallowStonePickup(66, 64.75));
        assertFalse(MinecraftScenarioClient.shallowStonePickup(66, 64.74));
        assertFalse(MinecraftScenarioClient.shallowStonePickup(66, 43));
        assertFalse(MinecraftScenarioClient.shallowStonePickup(66, Double.NaN));
    }

    @Test
    void deepExcavationBudgetScalesWithRealPlannedBreaksAndWalking() {
        assertEquals(Duration.ofSeconds(120).toNanos(),
            MinecraftScenarioClient.excavationBudgetNanos(Duration.ofSeconds(120), 2));
        assertEquals(Duration.ofSeconds(1200).toNanos(),
            MinecraftScenarioClient.excavationBudgetNanos(Duration.ofSeconds(120), 24));
        assertEquals(Duration.ofSeconds(1500).toNanos(),
            MinecraftScenarioClient.excavationBudgetNanos(Duration.ofSeconds(1500), 24));
    }

    @Test
    void pillarRequiresOwnedBlockConsumptionRealPlacementAndGroundedRise() {
        assertTrue(MinecraftScenarioClient.pillarPlacementConfirmed(
            "minecraft:dirt", "minecraft:dirt", 4, 3, true, 44, 43));
        assertFalse(MinecraftScenarioClient.pillarPlacementConfirmed(
            "minecraft:dirt", "minecraft:air", 4, 3, true, 44, 43));
        assertFalse(MinecraftScenarioClient.pillarPlacementConfirmed(
            "minecraft:dirt", "minecraft:dirt", 4, 4, true, 44, 43));
        assertFalse(MinecraftScenarioClient.pillarPlacementConfirmed(
            "minecraft:dirt", "minecraft:dirt", 0, -1, true, 44, 43));
        assertFalse(MinecraftScenarioClient.pillarPlacementConfirmed(
            "minecraft:dirt", "minecraft:dirt", 4, 3, false, 44.2, 43));
        assertFalse(MinecraftScenarioClient.pillarPlacementConfirmed(
            "minecraft:dirt", "minecraft:dirt", 4, 3, true, 43, 43));
    }

    @Test
    void pillarCollectsTheCeilingDropBeforeMeasuringPlacementConsumption() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/dev/solaris/agent/javaagent/MinecraftScenarioClient.java"
        ));
        String pillar = source.substring(source.indexOf("private boolean pillarOneBlock("),
            source.indexOf("private boolean clearExcavationBlock("));
        int pickupWait = pillar.indexOf("waitForInventoryPickup(drop, beforeCeilingPickup, 1, ceiling, deadlineNanos)");
        assertTrue(pickupWait >= 0 && pickupWait < pillar.indexOf("int before = inventoryCount(material)"));

        InventoryPickupProbe ceilingDrop = new InventoryPickupProbe(
            new MinecraftScenarioClient.InventoryPickupSample(5, true),
            new MinecraftScenarioClient.InventoryPickupSample(6, false)
        );
        assertTrue(MinecraftScenarioClient.waitForInventoryPickup(
            ceilingDrop, 5, 1, Long.MAX_VALUE).confirmed());
        assertEquals(1, ceilingDrop.awaitCalls);
        // Taking the baseline before this pickup hides a real 5 -> 4 -> 5 placement.
        assertFalse(MinecraftScenarioClient.pillarPlacementConfirmed(
            "minecraft:dirt", "minecraft:dirt", 5, 5, true, 67, 66));
        assertTrue(MinecraftScenarioClient.pillarPlacementConfirmed(
            "minecraft:dirt", "minecraft:dirt", 6, 5, true, 67, 66));
    }

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
    void shieldProbeUsesAnAppliedOwnEntityBlockSoundRatherThanWearOrUnchangedHealthAlone() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/dev/solaris/agent/javaagent/MinecraftScenarioClient.java"
        ));
        String shield = source.substring(source.indexOf("public ScenarioShieldBlockResult blockAttackWithSelectedShield("),
            source.indexOf("public boolean quickEquipSelectedArmor("));
        assertTrue(shield.contains("sample.shieldBlockVersion() > initial.shieldBlockVersion()"));
        assertFalse(shield.contains("sample.shieldDamage() > initial.shieldDamage()"));
        assertTrue(shield.contains("sample.health() < initial.health()"));
        String packetHook = java.nio.file.Files.readString(java.nio.file.Path.of(
            "../fabric-agent/src/main/java/dev/solaris/agent/mixin/ClientPacketListenerMixin.java"
        ));
        assertTrue(packetHook.contains("@Inject(method = \"handleSoundEntityEvent\", at = @At(\"RETURN\"))"));
        assertTrue(packetHook.contains("packet.getId(), minecraft.player.getId(), packet.getSound().value().location()"));
    }

    @Test
    void hostileApproachAndBothAttackLoopsUseTheSameLiveSafetyPolicy() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/dev/solaris/agent/javaagent/MinecraftScenarioClient.java"));
        String approach = source.substring(source.indexOf("public boolean approachEntity("),
            source.indexOf("public ScenarioBreakResult attackEntityUntilDropCollected("));
        assertTrue(approach.contains("driveCombatOnClientThread(minecraft, current, true, combat)"));
        for (String method : new String[] {
            "public ScenarioBreakResult attackEntityUntilDropCollected(", "public boolean attackEntityUntilRemoved("
        }) {
            String attack = source.substring(source.indexOf(method));
            int safety = attack.indexOf("mayAttack = driveCombatOnClientThread(minecraft, current, mayAttack, combat)");
            int send = attack.indexOf("minecraft.gameMode.attack(minecraft.player, current)");
            assertTrue(safety >= 0 && safety < send);
            assertTrue(attack.indexOf("if (mayAttack)", safety) < send);
        }
        String policy = source.substring(source.indexOf("private static boolean driveCombatOnClientThread("),
            source.indexOf("private static boolean hostileNearbyOnClientThread("));
        assertTrue(policy.contains("creeper || retreatToEat"));
        assertFalse(policy.contains("crowded) {"), "a crowd alone must not trigger a pointless retreat");
        assertTrue(policy.indexOf("if (inMelee)") < policy.indexOf("creeper || retreatToEat"),
            "a hostile already inside melee reach must be fought in place before any retreat branch");
        assertTrue(policy.contains("hostileNearbyOnClientThread(minecraft, target, target.position())"));
        assertTrue(policy.contains("eyeToBoxDistanceSquared(minecraft, target) > 9.0"));
        assertTrue(policy.contains("minecraft.player.hasLineOfSight(target)"));
        assertTrue(policy.contains("selectedItemOnClientThread().matches(combat.weaponId, 1)"));
    }

    @Test
    void hostileDropsAndMovementRequireObservedSafeTerrainAndThreatClearance() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/dev/solaris/agent/javaagent/MinecraftScenarioClient.java"));
        String attack = source.substring(source.indexOf("public ScenarioBreakResult attackEntityUntilDropCollected("),
            source.indexOf("public boolean attackEntityUntilRemoved("));
        assertTrue(attack.contains("CombatTactics.dropChaseAllowed(playerPosition.y, dropPosition.y, nearby)"));
        assertTrue(attack.contains("moveCombatSafelyOnClientThread(minecraft, dropPosition, combat)"));
        String footing = source.substring(source.indexOf("private static boolean safeCombatFooting("),
            source.indexOf("private static void retreatFromHostilesOnClientThread("));
        assertTrue(footing.contains("step <= 4"));
        assertTrue(footing.contains("!minecraft.level.hasChunkAt(pos)"));
        assertTrue(footing.contains("!minecraft.level.getFluidState(pos).isEmpty()"));
        assertTrue(footing.contains("getCollisionShape(minecraft.level, pos)"));
        assertTrue(footing.contains("top >= feetY - 3.0"));
        String scan = source.substring(source.indexOf("private static ScenarioEntityObservation visibleEntityOnClientThread("),
            source.indexOf("private static ScenarioEntityObservation visibleSheepWithWoolOnClientThread("));
        assertTrue(scan.contains("entity instanceof Creeper"));
        assertTrue(scan.contains("skipCrowdedHostiles && hostileNearbyOnClientThread"));
    }

    @Test
    void combatRecoveryRequiresRealOwnedFoodConsumptionAndRestoresTheWeapon() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/dev/solaris/agent/javaagent/MinecraftScenarioClient.java"));
        String recovery = source.substring(source.indexOf("private void eatOwnedFoodForCombatRecovery("),
            source.indexOf("private static final class CombatAttempt"));
        assertTrue(recovery.contains("selectHotbarItem(food, 1,"));
        assertTrue(recovery.contains("minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND)"));
        assertTrue(recovery.contains("sample.foodLevel() > initial.foodLevel() && sample.itemCount() < initial.itemCount()"));
        assertTrue(recovery.indexOf("sample.itemCount() < initial.itemCount()")
            < recovery.indexOf("combat.recovery.foodConsumed()"));
        assertTrue(recovery.contains("selectHotbarItem(combat.weaponId, 1,"));
        assertTrue(recovery.contains("safeToEatOnClientThread(minecraft, 7.0)"));
        assertFalse(recovery.contains("sendCommand"));
    }

    @Test
    void staircaseArrivalAcceptsActualRaisedFootingWithoutDemandingColumnCenter() {
        assertTrue(MinecraftScenarioClient.positionReached(
            104.7, 67.0, 327.7, 104.5, 327.5, 0.25, 66.9
        ));
        assertFalse(MinecraftScenarioClient.positionReached(
            104.7, 66.0, 327.7, 104.5, 327.5, 0.25, 66.9
        ));
        assertFalse(MinecraftScenarioClient.positionReached(
            105.2, 67.0, 327.7, 104.5, 327.5, 0.25, 66.9
        ));
        assertFalse(MinecraftScenarioClient.positionReached(
            104.7, 67.0, 327.7, 104.5, 327.5, 0.04, Double.NEGATIVE_INFINITY
        ));
    }

    @Test
    void staircaseArrivalWaitsForLandingAfterTheJumpReachesTheSurface() {
        boolean reachedSurface = MinecraftScenarioClient.positionReached(
            96.55, 72.0, 328.5, 96.5, 328.5, 0.25, 71.9);
        assertTrue(reachedSurface);
        assertFalse(MinecraftScenarioClient.positionArrivalConfirmed(reachedSurface, 71.9, false));
        assertTrue(MinecraftScenarioClient.positionArrivalConfirmed(reachedSurface, 71.9, true));
        assertFalse(MinecraftScenarioClient.positionArrivalConfirmed(false, 71.9, true));
        assertTrue(MinecraftScenarioClient.positionArrivalConfirmed(true, Double.NEGATIVE_INFINITY, false));
    }

    @Test
    void onlyActualCliffExcavationGetsItsSeparateBoundedMiningBudget() {
        assertEquals(Duration.ofSeconds(30).toNanos(),
            MinecraftScenarioClient.approachBudgetNanos(Duration.ofSeconds(30), false));
        assertEquals(Duration.ofSeconds(120).toNanos(),
            MinecraftScenarioClient.approachBudgetNanos(Duration.ofSeconds(30), true));
        assertEquals(Duration.ofSeconds(150).toNanos(),
            MinecraftScenarioClient.approachBudgetNanos(Duration.ofSeconds(150), true));
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
    void passivePickupStepsOffRaisedFootingInsteadOfCirclingTheLowerDrop() {
        assertEquals(156.5, MinecraftScenarioClient.pickupApproachCoordinate(70.0, 69.0, 156.8));
        assertEquals(319.5, MinecraftScenarioClient.pickupApproachCoordinate(70.0, 69.0, 319.009));
        assertEquals(-1.5, MinecraftScenarioClient.pickupApproachCoordinate(70.0, 69.0, -1.2));
        assertEquals(156.8, MinecraftScenarioClient.pickupApproachCoordinate(69.5, 69.0, 156.8));
    }

    @Test
    void passivePickupStopsOnlyInsideTheActualVanillaItemVolume() {
        assertFalse(MinecraftScenarioClient.pickupVolumeIntersects(
            156.5, 70.0, 318.7, 157.1, 71.8, 319.3, 156.8, 69.0, 319.009
        ));
        assertFalse(MinecraftScenarioClient.pickupVolumeIntersects(
            156.247, 69.766, 318.695, 156.847, 71.566, 319.295, 156.8, 69.0, 319.009
        ));
        assertTrue(MinecraftScenarioClient.pickupVolumeIntersects(
            156.206, 69.536, 318.693, 156.806, 71.336, 319.293, 156.8, 69.0, 319.009
        ));
        assertFalse(MinecraftScenarioClient.pickupVolumeIntersects(
            156.5, 69.0, 318.7, 157.1, 70.8, 319.3, 158.225, 69.0, 319.009
        ));
    }

    @Test
    void genericPickupKeepsApproachingWhenCloseButOutsideTheVanillaVolume() {
        // The old squared-distance threshold stopped 1.9 blocks away.
        assertFalse(MinecraftScenarioClient.pickupVolumeIntersects(
            -0.3, 69.0, -0.3, 0.3, 70.8, 0.3, 1.9, 69.0, 0.0
        ));
        // Scenario18 cobble fell to y77 while the player remained at y80.
        assertFalse(MinecraftScenarioClient.pickupVolumeIntersects(
            168.065, 80.0, 338.547, 168.665, 81.8, 339.147, 169.5, 77.0, 338.5
        ));
        assertEquals(169.5, MinecraftScenarioClient.pickupApproachCoordinate(80.0, 77.0, 169.5));
        assertEquals(338.5, MinecraftScenarioClient.pickupApproachCoordinate(80.0, 77.0, 338.5));
        assertTrue(MinecraftScenarioClient.pickupVolumeIntersects(
            169.2, 77.5, 338.2, 169.8, 79.3, 338.8, 169.5, 77.0, 338.5
        ));
    }

    @Test
    void passiveDropRemovalStillRequiresTheFollowingInventoryApplication() throws Exception {
        InventoryPickupProbe probe = new InventoryPickupProbe(
            new MinecraftScenarioClient.InventoryPickupSample(0, false),
            new MinecraftScenarioClient.InventoryPickupSample(0, false),
            new MinecraftScenarioClient.InventoryPickupSample(1, false)
        );
        MinecraftScenarioClient.InventoryPickupResult result =
            MinecraftScenarioClient.waitForInventoryPickup(probe, 0, 1, Long.MAX_VALUE);
        assertTrue(result.confirmed());
        assertEquals(2, probe.awaitCalls);
        assertEquals(3, probe.sampleCalls);

        InventoryPickupProbe missing = new InventoryPickupProbe(
            new MinecraftScenarioClient.InventoryPickupSample(0, false)
        );
        assertFalse(
            MinecraftScenarioClient.waitForInventoryPickup(missing, 0, 1, 0L).confirmed()
        );
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
    void entityAttackWaitsForObservedFullVanillaCooldown() {
        assertFalse(AttackCadence.ready(0.89F));
        assertFalse(AttackCadence.ready(0.90F));
        assertFalse(AttackCadence.ready(0.999F));
        assertTrue(AttackCadence.ready(1.0F));
        assertFalse(AttackCadence.ready(Float.NaN));
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
