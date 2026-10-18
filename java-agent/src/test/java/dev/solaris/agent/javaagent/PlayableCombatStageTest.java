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

final class PlayableCombatStageTest {
    @Test
    void earnedToolZombieCombatKillsNaturalHostileWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-21-earned-tool-zombie-combat",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "earned hostile combat path must not use debug setup");
        assertTrue(
            client.operations.contains("waitNight"),
            "earned hostile combat path must wait for natural night instead of summoning"
        );
        assertTrue(
            client.operations.contains("findEntity:minecraft:zombie:outside-survival-reach"),
            "earned hostile combat path must scan for a naturally loaded zombie"
        );
        assertTrue(
            client.operations.contains("selectHotbar:minecraft:wooden_pickaxe:1"),
            "earned hostile combat path must fight with the crafted tool"
        );
        assertTrue(
            client.operations.contains("attackEntityDrop:minecraft:zombie:entity_id=99:minecraft:rotten_flesh:1"),
            "earned hostile combat path must kill the zombie and collect its drop"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("zombie combat drop: passed")),
            "earned hostile combat path must record hostile kill/drop evidence"
        );
    }
    @Test
    void earnedToolZombieCombatPrefersBestAlreadyEarnedSword() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.woodenSwords = 1;
        client.stoneSwords = 1;
        client.ironSwords = 1;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-21-earned-tool-zombie-combat",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result(), () -> String.join("\n", report.observations()));
        assertFalse(client.usedDebugSetup());
        assertTrue(client.operations.contains("selectHotbar:minecraft:iron_sword:1"));
        assertTrue(report.observations().stream().anyMatch(entry -> entry.contains("weapon=minecraft:iron_sword")));
    }

    @Test
    void naturalZombieScanRemainsOutsideReachUntilSurfaceSpawnsCanAppear() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.minimumZombieScanTimeout = Duration.ofSeconds(25);

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-21-earned-tool-zombie-combat",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result(), () -> String.join("\n", report.observations()));
        assertFalse(client.usedDebugSetup());
        assertTrue(client.operations.contains(
            "attackEntityDrop:minecraft:zombie:entity_id=99:minecraft:rotten_flesh:1"
        ));
    }
    @Test
    void earnedToolZombieCombatFailsClosedIfPlayerDiesDuringCombat() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.healthAfterHostileCombat = 0.0F;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-21-earned-tool-zombie-combat",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "failed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("zombie combat survival: failed")),
            "earned hostile combat path must not pass when the server leaves the player dead"
        );
    }
    @Test
    void stoneSwordZombieCombatCraftsWeaponAndKillsNaturalHostileWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-22-stone-sword-zombie-combat",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "stone sword hostile combat path must not use debug setup");
        assertTrue(
            client.operations.contains("recipe:7:26:false"),
            "stone sword combat must craft the embedded stone sword recipe"
        );
        assertTrue(
            client.operations.contains("waitCount:minecraft:stone_sword:1"),
            "stone sword combat must wait for the crafted stone sword"
        );
        assertTrue(
            client.operations.contains("selectHotbar:minecraft:stone_sword:1"),
            "stone sword combat must fight with the crafted weapon"
        );
        assertTrue(
            client.operations.contains("findEntity:minecraft:zombie:outside-survival-reach"),
            "stone sword combat path must scan for a naturally loaded zombie"
        );
        assertTrue(
            client.operations.contains("attackEntityDrop:minecraft:zombie:entity_id=99:minecraft:rotten_flesh:1"),
            "stone sword combat path must kill the zombie and collect its drop"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("stone sword recipe: passed")),
            "stone sword combat must record stone sword recipe evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("stone sword zombie combat: passed")),
            "stone sword combat must record hostile kill/drop evidence"
        );
    }
    @Test
    void ironSwordZombieCombatMinesSmeltsCraftsAndFightsWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-24-iron-sword-zombie-combat",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "iron sword combat path must not use debug setup");
        assertTrue(
            client.operations.contains("recipe:7:24:false"),
            "iron sword combat must craft an earned stone pickaxe"
        );
        assertTrue(
            client.operations.contains("recipe:7:13:false"),
            "iron sword combat must craft an earned furnace"
        );
        assertTrue(
            client.operations.stream()
                    .filter("breakVisible:minecraft:iron_ore:minecraft:raw_iron"::equals)
                    .count() >= 2,
            "iron sword combat must mine two natural iron ore blocks"
        );
        assertTrue(
            client.operations.stream()
                    .filter("waitContainer:2:minecraft:iron_ingot:1"::equals)
                    .count() >= 2,
            "iron sword combat must smelt two earned iron ingots"
        );
        assertTrue(
            client.operations.contains("recipe:7:57:false"),
            "iron sword combat must place the earned iron sword recipe"
        );
        assertTrue(
            client.operations.contains("waitCount:minecraft:iron_sword:1"),
            "iron sword combat must wait for the crafted iron sword"
        );
        assertTrue(
            client.operations.contains("selectHotbar:minecraft:iron_sword:1"),
            "iron sword combat must fight with the crafted iron sword"
        );
        assertTrue(
            client.operations.contains("attackEntityDrop:minecraft:zombie:entity_id=99:minecraft:rotten_flesh:1"),
            "iron sword combat path must kill a natural zombie and collect its drop"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("iron sword recipe: passed")),
            "iron sword combat must record iron sword recipe evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("iron sword zombie combat: passed")),
            "iron sword combat must record hostile kill/drop evidence"
        );
    }
    @Test
    void shieldZombieBlockCraftsEarnedShieldAndHoldsUseWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-26-earned-shield-zombie-block",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "shield block path must not use debug setup");
        assertTrue(
            client.operations.contains("recipe:7:58:false"),
            "shield block path must craft the earned shield recipe"
        );
        assertTrue(
            client.operations.contains("waitCount:minecraft:shield:1"),
            "shield block path must wait for the crafted shield"
        );
        assertTrue(
            client.operations.contains("selectHotbar:minecraft:shield:1"),
            "shield block path must select the earned shield"
        );
        assertTrue(
            client.operations.contains("findEntity:minecraft:zombie:outside-survival-reach"),
            "shield block path must scan for a naturally loaded zombie"
        );
        assertTrue(
            client.operations.contains("blockAttackWithSelectedShield:minecraft:shield"),
            "shield block path must observe a blocked attack with the earned shield"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("shield recipe: passed")),
            "shield block path must record shield recipe evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("shield zombie block: passed")),
            "shield block path must record shield use survival evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("blocked_attack_observed=true")),
            "shield block path must record a positive blocked-attack event"
        );
    }
    @Test
    void shieldZombieBlockFailsWhenNoBlockedAttackWasObserved() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.shieldBlockedAttackObserved = false;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-26-earned-shield-zombie-block",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "failed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
    }
    @Test
    void ironChestplateEquipCraftsEarnedChestplateAndQuickEquipsWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-27-earned-iron-chestplate-equip",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "iron chestplate equip path must not use debug setup");
        assertTrue(
            client.operations.contains("recipe:7:59:false"),
            "iron chestplate equip path must craft the earned chestplate recipe"
        );
        assertTrue(
            client.operations.contains("waitCount:minecraft:iron_chestplate:1"),
            "iron chestplate equip path must wait for the crafted chestplate"
        );
        assertTrue(
            client.operations.contains("selectHotbar:minecraft:iron_chestplate:1"),
            "iron chestplate equip path must select the crafted chestplate"
        );
        assertTrue(
            client.operations.contains("quickEquip:minecraft:iron_chestplate:chest"),
            "iron chestplate equip path must equip through the normal inventory quick-move path"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("iron chestplate recipe: passed")),
            "iron chestplate equip path must record chestplate recipe evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("iron chestplate equip: passed")),
            "iron chestplate equip path must record equipped armor evidence"
        );
    }
    @Test
    void ironChestplateZombieMitigationEquipsEarnedChestplateAndMeasuresNaturalHitWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-28-earned-iron-chestplate-zombie-mitigation",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "iron chestplate mitigation path must not use debug setup");
        assertTrue(
            client.operations.contains("quickEquip:minecraft:iron_chestplate:chest"),
            "iron chestplate mitigation path must equip through the normal inventory quick-move path"
        );
        assertTrue(
            client.operations.contains("findEntity:minecraft:zombie:outside-survival-reach"),
            "iron chestplate mitigation path must scan for a naturally loaded zombie"
        );
        assertTrue(
            client.operations.contains("waitHealthBelow:20.0"),
            "iron chestplate mitigation path must wait for natural zombie damage"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("iron chestplate zombie mitigation: passed")),
            "iron chestplate mitigation path must record reduced natural zombie damage"
        );
    }
    @Test
    void ironChestplateRestartBeforeWritesMarkerAndEquipsChestplateWithoutDebugSetup() throws Exception {
        Path runDir = Files.createTempDirectory("playable-29-before");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-29-iron-chestplate-save-restart-mitigation-before",
            screenshotsDir,
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "iron chestplate restart before phase must not use debug setup");
        assertTrue(
            Files.isRegularFile(runDir.resolve("playable-03-save-restart-marker.properties")),
            "iron chestplate restart before phase must write the crafted table marker for after phase"
        );
        assertTrue(
            client.operations.contains("quickEquip:minecraft:iron_chestplate:chest"),
            "before phase must equip the earned iron chestplate before restart"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("iron chestplate equip: passed")),
            "before phase must record chestplate equip evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("runner-managed restart: pending")),
            "before phase must record that the runner owns the restart boundary"
        );
    }
    @Test
    void ironChestplateRestartAfterChecksPersistedArmorAndMitigationWithoutDebugSetup()
        throws Exception {
        Path runDir = Files.createTempDirectory("playable-29-after");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient client = new FakeScenarioClient();
        ClientScenarioReport before = new PlayableRealClientLoopScenario().run(
            "playable-29-iron-chestplate-save-restart-mitigation-before",
            screenshotsDir,
            client
        );
        assertEquals("passed", before.result());
        client.operations.clear();

        ClientScenarioReport after = new PlayableRealClientLoopScenario().run(
            "playable-29-iron-chestplate-save-restart-mitigation-after",
            screenshotsDir,
            client
        );

        assertEquals(
            "passed",
            after.result(),
            () -> String.join("\n", after.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            client.operations.contains("waitBlock:restart-marker:minecraft:crafting_table"),
            "after phase must verify the persisted crafting table marker"
        );
        assertTrue(
            client.operations.contains("equippedArmor:chest"),
            "after phase must read the persisted chest armor slot"
        );
        assertTrue(
            client.operations.contains("findEntity:minecraft:zombie:outside-survival-reach"),
            "after phase must scan for a naturally loaded zombie after restart"
        );
        assertTrue(
            client.operations.contains("waitHealthBelow:20.0"),
            "after phase must wait for natural zombie damage after restart"
        );
        assertFalse(client.usedDebugSetup(), "iron chestplate restart after phase must not use debug setup");
        assertTrue(
            after.observations().stream().anyMatch(entry -> entry.contains("restart marker persistence: passed")),
            "after phase must record persisted marker observation"
        );
        assertTrue(
            after.observations().stream().anyMatch(entry -> entry.contains("iron chestplate armor persistence: passed")),
            "after phase must record persisted equipped armor observation"
        );
        assertTrue(
            after.observations().stream().anyMatch(entry -> entry.contains("iron chestplate restarted zombie mitigation: passed")),
            "after phase must record post-restart armor mitigation evidence"
        );
    }
    @Test
    void ironChestplateRestartAfterFailsClosedWithoutMarker() {
        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-29-iron-chestplate-save-restart-mitigation-after",
            Path.of("build/tmp/playable-29-missing/screenshots"),
            new FakeScenarioClient()
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("missing restart marker")),
            "after phase must fail closed when marker file is absent"
        );
    }
    @Test
    void ironChestplateEquipCarriesEnoughPlanksForEightSmeltsWithoutPostFurnaceLogTrip() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.failPostFurnaceLogApproach = true;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-27-earned-iron-chestplate-equip",
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
                entry.contains("iron chestplate fuel planks: passed")
                    && entry.contains("planks_count=11") && entry.contains("expected_at_least=8")),
            "iron chestplate progression should carry enough planks for eight smelts"
        );
        assertFalse(
            report.observations().stream().anyMatch(entry -> entry.contains("natural log approach: failed")),
            "iron chestplate progression should not need a post-furnace log trip for fuel"
        );
        assertEquals(
            8,
            client.operations.stream()
                    .filter("moveToContainer:1:minecraft:oak_planks:1"::equals)
                    .count(),
            "iron chestplate progression should refuel every expired burn without a post-furnace log trip"
        );
    }
    @Test
    void ironChestplateEquipRetriesMissedRawIronPickupUntilIngotTarget() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.failedRawIronPickupsRemaining = 1;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-27-earned-iron-chestplate-equip",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("natural iron ore break/drop/pickup: failed")),
            "iron chestplate progression should record a missed raw iron pickup before retrying"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains(
                "pickup_detail=player=(10.0,79.0,7.0)"
            )),
            "a missed pickup must retain the final player/drop diagnostic from the client"
        );
        assertTrue(
            client.operations.stream()
                    .filter("breakVisible:minecraft:iron_ore:minecraft:raw_iron"::equals)
                    .count() >= 9,
            "one missed raw iron pickup should cause one extra natural iron ore mining attempt"
        );
    }
    @Test
    void ironChestplateEquipHandlesStackedRawIronPickupDuringSmelting() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.rawIronPickupBatchSize = 4;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-27-earned-iron-chestplate-equip",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("raw_iron_after=4")),
            "regression setup must prove one pickup can restore a stacked raw iron count"
        );
        assertFalse(
            report.observations().stream().anyMatch(entry -> entry.contains("furnace raw iron input remainder clear: failed")),
            "stacked raw iron in the furnace input must not fail a one-ingot smelt"
        );
        assertTrue(
            client.operations.contains("moveFromContainer:0:minecraft:raw_iron:1"),
            "stacked raw iron remainder should be restored from the furnace input before taking the ingot"
        );
    }
}
