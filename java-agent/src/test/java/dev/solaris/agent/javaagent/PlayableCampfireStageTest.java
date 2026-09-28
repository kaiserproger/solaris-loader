package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlayableCampfireStageTest {
    @Test
    void earnedCampfireCooksPassiveFoodWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.logItemId = "minecraft:birch_log";
        client.planksItemId = "minecraft:birch_planks";

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-18-earned-campfire-cooking",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "earned campfire path must not use debug setup");
        assertTrue(
            client.operations.contains("recipe:7:53:false"),
            "earned campfire path must craft campfire with the appended embedded recipe display id"
        );
        assertTrue(
            client.operations.contains("moveFromContainer:0:minecraft:birch_log:1"),
            "earned campfire path must return furnace input log remainder before crafting the campfire"
        );
        assertTrue(
            client.operations.contains("waitCount:minecraft:campfire:1"),
            "earned campfire path must wait for crafted campfire inventory"
        );
        assertTrue(
            client.operations.contains("selectHotbar:minecraft:campfire:1"),
            "earned campfire path must select the crafted campfire"
        );
        assertTrue(
            client.operations.contains("use:minecraft:campfire:campfire-clicked"),
            "earned campfire path must place the crafted campfire"
        );
        assertTrue(
            client.operations.contains("waitBlock:campfire-target:minecraft:campfire"),
            "earned campfire path must observe the placed campfire block"
        );
        assertTrue(
            client.operations.contains("selectHotbar:minecraft:beef:1"),
            "earned campfire path must select the raw passive food drop"
        );
        assertTrue(
            client.operations.contains("use:minecraft:beef:campfire-target"),
            "earned campfire path must use raw food on the placed campfire"
        );
        assertTrue(
            client.operations.contains("waitDrop:minecraft:cooked_beef:campfire-target"),
            "earned campfire path must wait for cooked item entity output"
        );
        assertTrue(
            client.operations.contains("collect:minecraft:campfire:minecraft:cooked_beef:1"),
            "earned campfire path must collect the campfire cooked output"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("campfire recipe: passed")),
            "earned campfire path must record campfire recipe evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("campfire cooking output: passed")),
            "earned campfire path must record cooked item output evidence"
        );
    }
    @Test
    void earnedCampfirePlacesSpareCraftingTableWhenOriginalTableIsOutOfReach() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.logItemId = "minecraft:birch_log";
        client.planksItemId = "minecraft:birch_planks";
        client.failCraftingTableApproachForCampfire = true;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-18-earned-campfire-cooking",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            client.operations.stream().filter("recipe:0:10:false"::equals).count() >= 2,
            "campfire path must craft a spare table from earned planks when the original table cannot be reached"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("crafting table approach for campfire: failed")),
            "campfire path must record the failed original-table approach"
        );
        assertTrue(
            client.operations.contains("recipe:7:53:false"),
            "campfire path must still craft the campfire after opening the spare table"
        );
    }
    @Test
    void earnedCampfireRetriesReachableReserveLogAfterFarApproachFailure() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.logItemId = "minecraft:birch_log";
        client.planksItemId = "minecraft:birch_planks";
        client.failFirstCampfireReserveLogApproach = true;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-18-earned-campfire-cooking",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("campfire reserve log approach: failed")
            ),
            "scenario must record the failed far-log approach"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("campfire reserve log reachable fallback after failed approach: passed")
            ),
            "scenario must retry a reachable log after the failed far approach"
        );
    }
    @Test
    void earnedCampfireDeathRespawnsWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.logItemId = "minecraft:birch_log";
        client.planksItemId = "minecraft:birch_planks";

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-19-earned-campfire-death-respawn",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "campfire death/respawn path must not use debug setup");
        assertTrue(
            client.operations.contains("standOnBlockUntilDeath:campfire-target:minecraft:campfire"),
            "campfire death/respawn path must wait for natural campfire contact damage"
        );
        assertTrue(
            client.operations.contains("respawn"),
            "campfire death/respawn path must perform a vanilla respawn packet"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("campfire hazard death: passed")),
            "campfire death/respawn path must record death-screen evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("campfire respawn: passed")),
            "campfire death/respawn path must record respawn evidence"
        );
    }
    @Test
    void campfireDeathDropRecoveryReturnsEarnedItemAfterRespawn() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.logItemId = "minecraft:birch_log";
        client.planksItemId = "minecraft:birch_planks";

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-20-campfire-death-drop-recovery",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "campfire death-drop recovery must not use debug setup");
        assertTrue(
            client.operations.contains("standOnBlockUntilDeath:campfire-target:minecraft:campfire"),
            "death-drop recovery must still use natural campfire contact damage"
        );
        assertTrue(
            client.operations.contains("respawn"),
            "death-drop recovery must perform vanilla respawn before pickup"
        );
        assertTrue(
            client.operations.contains("approach:minecraft:campfire:campfire-target"),
            "death-drop recovery must walk back toward the death site after respawn"
        );
        assertTrue(
            client.operations.contains(
                "collectIdentity:minecraft:wooden_pickaxe:701:00000000-0000-0000-0000-000000000701"
            ),
            "death-drop recovery must collect the wooden-pickaxe entity observed after death"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("campfire death-drop recovery: passed")),
            "death-drop recovery must record pickup evidence"
        );
    }
    @Test
    void campfireDeathDropRecoveryRejectsInventoryGainWithoutPickupVisibility() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.logItemId = "minecraft:birch_log";
        client.planksItemId = "minecraft:birch_planks";
        client.woodenPickaxePickupObserved = false;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-20-campfire-death-drop-recovery",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("campfire death-drop recovery: failed")
                    && entry.contains("pickup_visible=false")
            ),
            "recovery must require visible wooden-pickaxe pickup evidence, not just an inventory increase"
        );
    }
    @Test
    void campfireDeathDropRecoveryFailsWithoutPostDeathItemEntityVisibility() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.logItemId = "minecraft:birch_log";
        client.planksItemId = "minecraft:birch_planks";
        client.postDeathWoodenPickaxeEntityVisible = false;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-20-campfire-death-drop-recovery",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("campfire wooden pickaxe death drop: failed")
                    && entry.contains("identity=missing")
            ),
            "recovery must fail when no new wooden-pickaxe entity is observed after death"
        );
    }
    @Test
    void campfireDeathDropRecoveryFailsWhenMatchedItemEntityDoesNotDisappear() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.logItemId = "minecraft:birch_log";
        client.planksItemId = "minecraft:birch_planks";
        client.woodenPickaxeEntityDisappeared = false;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-20-campfire-death-drop-recovery",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("campfire death-drop recovery: failed")
                    && entry.contains("pickup_disappeared=false")
            ),
            "recovery must require disappearance of the matched post-death entity"
        );
    }
    @Test
    void campfireDeathDropRecoveryRejectsPreexistingItemEntityIdentity() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.logItemId = "minecraft:birch_log";
        client.planksItemId = "minecraft:birch_planks";
        client.preexistingWoodenPickaxeEntityId = 700;
        client.returnPreexistingWoodenPickaxeEntity = true;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-20-campfire-death-drop-recovery",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("campfire wooden pickaxe death drop: failed")
                    && entry.contains("entityId=700")
                    && entry.contains("uuid=00000000-0000-0000-0000-000000000700")
            ),
            "recovery must not bind to a wooden-pickaxe entity visible before death"
        );
    }
    @Test
    void campfireDeathDropRecoveryDistinguishesReusedEntityIdByUuid() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.logItemId = "minecraft:birch_log";
        client.planksItemId = "minecraft:birch_planks";
        client.preexistingWoodenPickaxeEntityId = 701;
        client.deathDropWoodenPickaxeEntityId = 701;
        client.preexistingWoodenPickaxeEntityUuid = UUID.fromString("00000000-0000-0000-0000-000000000700");
        client.deathDropWoodenPickaxeEntityUuid = UUID.fromString("00000000-0000-0000-0000-000000000701");

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-20-campfire-death-drop-recovery",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            client.operations.contains(
                "collectIdentity:minecraft:wooden_pickaxe:701:00000000-0000-0000-0000-000000000701"
            ),
            "collection must bind to the post-death UUID when the numeric entity id is reused"
        );
    }
    @Test
    void campfireDeathDropRecoveryRejectsIdentityLossPlusUnrelatedPickup() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.logItemId = "minecraft:birch_log";
        client.planksItemId = "minecraft:birch_planks";
        client.woodenPickaxeIdentityLostBeforePickup = true;
        client.unrelatedWoodenPickaxePickedUpAfterIdentityLoss = true;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-20-campfire-death-drop-recovery",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("campfire death-drop recovery: failed")
                    && entry.contains("pickup_disappeared=true")
                    && entry.contains("pickup_restored=false")
            ),
            "an unrelated inventory gain after identity loss must not confirm pickup"
        );
    }
}
