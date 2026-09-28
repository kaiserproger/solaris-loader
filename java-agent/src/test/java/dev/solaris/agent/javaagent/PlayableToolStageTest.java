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

final class PlayableToolStageTest {
    @Test
    void stoneToolProgressionMinesCobblestoneAndCraftsStonePickaxeWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-05-stone-tool-progression",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertFalse(client.usedDebugSetup(), "stone progression must not fall back to debug setup");
        assertTrue(
            client.operations.contains("selectHotbar:minecraft:wooden_pickaxe:1"),
            "stone progression must use the earned wooden pickaxe"
        );
        assertTrue(
            client.operations.contains("approach:minecraft:crafting_table:table-target"),
            "stone progression must walk back into reach of the earned crafting table before reopening it"
        );
        assertTrue(
            client.operations.contains("breakVisible:minecraft:stone:minecraft:cobblestone"),
            "stone progression must break natural stone for cobblestone drops"
        );
        assertTrue(
            client.operations.contains("recipe:7:24:false"),
            "stone progression must use the embedded stone pickaxe recipe id"
        );
        assertTrue(
            client.operations.contains("waitCount:minecraft:stone_pickaxe:1"),
            "stone progression must wait for the crafted stone pickaxe"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("stone break/drop/pickup: passed")),
            "stone progression must record cobblestone mining evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("stone pickaxe recipe: passed")),
            "stone progression must record stone pickaxe crafting evidence"
        );
    }
    @Test
    void stoneToolProgressionRetriesMissedCobblestonePickupUntilInventoryTarget() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.failedCobblestonePickupsRemaining = 1;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-05-stone-tool-progression",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("stone break/drop/pickup: failed")),
            "stone progression should record a missed pickup before retrying"
        );
        assertTrue(
            client.operations.stream()
                    .filter("collect:minecraft:stone:minecraft:cobblestone:1"::equals)
                    .count() >= 4,
            "one missed cobblestone pickup should cause one extra natural stone mining attempt"
        );
    }
    @Test
    void stoneToolProgressionRetriesReachableStoneAfterFailedFarApproach() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.missingReachableStoneScansRemaining = 1;
        client.failFirstNaturalStoneFarApproach = true;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-05-stone-tool-progression",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("natural stone approach: failed")),
            "stone progression should record the failed far-stone approach"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("natural stone reachable fallback after failed approach: passed")
            ),
            "stone progression should retry a reachable stone after the failed far approach"
        );
        assertTrue(
            client.operations.contains("breakVisible:minecraft:stone:minecraft:cobblestone"),
            "stone progression should still mine cobblestone after the reachable fallback"
        );
    }
    @Test
    void stoneToolRestartBeforePersistsCraftedTableMarkerAndStonePickaxeWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();
        Path screenshotsDir = Path.of("build/tmp/playable-06-test/screenshots");

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-06-stone-tool-save-restart-before",
            screenshotsDir,
            client
        );

        assertEquals("passed", report.result());
        assertFalse(client.usedDebugSetup(), "stone restart before phase must not use debug setup");
        assertTrue(
            screenshotsDir.getParent().resolve("playable-03-save-restart-marker.properties").toFile().isFile(),
            "stone restart before phase must write the crafted table marker for after phase"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("stone pickaxe recipe: passed")),
            "stone restart before phase must craft the stone pickaxe before restart"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("restart marker placement: passed")),
            "stone restart before phase must record marker placement"
        );
    }
    @Test
    void stoneToolRestartAfterChecksPersistedMarkerAndStonePickaxeInventory() {
        FakeScenarioClient client = new FakeScenarioClient();
        Path screenshotsDir = Path.of("build/tmp/playable-06-test/screenshots");
        new PlayableRealClientLoopScenario().run(
            "playable-06-stone-tool-save-restart-before",
            screenshotsDir,
            client
        );
        client.operations.clear();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-06-stone-tool-save-restart-after",
            screenshotsDir,
            client
        );

        assertEquals("passed", report.result());
        assertEquals(List.of(
            "selected",
            "count:minecraft:oak_log",
            "count:minecraft:oak_planks",
            "count:minecraft:crafting_table",
            "count:minecraft:wooden_pickaxe",
            "waitBlock:restart-marker:minecraft:crafting_table",
            "count:minecraft:stone_pickaxe"
        ), client.operations);
        assertFalse(client.usedDebugSetup(), "stone restart after phase must not use debug setup");
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("restart marker persistence: passed")),
            "stone restart after phase must record persisted marker observation"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("stone inventory persistence: passed")),
            "stone restart after phase must record persisted stone pickaxe inventory observation"
        );
    }
    @Test
    void ironIngotProgressionMinesNaturalOreAndSmeltsWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-23-iron-ingot-progression",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "iron progression path must not use debug setup");
        assertTrue(
            client.operations.contains("recipe:7:24:false"),
            "iron progression must craft an earned stone pickaxe"
        );
        assertTrue(
            client.operations.contains("recipe:7:13:false"),
            "iron progression must craft an earned furnace"
        );
        assertTrue(
            client.operations.contains("selectHotbar:minecraft:stone_pickaxe:1"),
            "iron progression must mine ore with the earned stone pickaxe"
        );
        assertTrue(
            client.operations.stream().anyMatch(operation -> operation.startsWith(
                "findBreakable:minecraft:iron_ore|minecraft:deepslate_iron_ore:"
            )),
            "iron progression must scan for natural iron ore blocks"
        );
        assertTrue(
            client.operations.contains("breakVisible:minecraft:iron_ore:minecraft:raw_iron"),
            "iron progression must break natural iron ore into raw iron"
        );
        assertTrue(
            client.operations.contains("moveToContainer:0:minecraft:raw_iron:2"),
            "iron progression must move two earned raw iron into furnace input"
        );
        assertTrue(
            client.operations.contains("waitContainer:2:minecraft:iron_ingot:2"),
            "iron progression must wait for two ingots so vanilla 0.7 XP rounding cannot yield zero"
        );
        assertTrue(
            client.operations.contains("moveFromContainer:2:minecraft:iron_ingot:2"),
            "iron progression must move both iron ingots into inventory"
        );
        assertTrue(
            client.operations.contains("waitExperienceAbove:0"),
            "iron progression must wait for furnace experience to reach the client"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("natural iron ore break/drop/pickup: passed")),
            "iron progression must record natural ore break/drop evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("furnace iron ingot inventory: passed")),
            "iron progression must record smelted ingot inventory evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("furnace iron experience: passed")),
            "iron progression must record received furnace experience"
        );
    }
    @Test
    void ironSwordRestartBeforeWritesMarkerAndCraftsIronSwordWithoutDebugSetup() throws Exception {
        Path runDir = Files.createTempDirectory("playable-25-before");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-25-iron-sword-save-restart-before",
            screenshotsDir,
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "iron sword restart before phase must not use debug setup");
        assertTrue(
            Files.isRegularFile(runDir.resolve("playable-03-save-restart-marker.properties")),
            "iron sword restart before phase must write the crafted table marker for after phase"
        );
        assertTrue(
            client.operations.contains("recipe:7:57:false"),
            "before phase must craft the earned iron sword before restart"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("iron sword recipe: passed")),
            "before phase must record iron sword recipe evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("runner-managed restart: pending")),
            "before phase must record that the runner owns the restart boundary"
        );
    }
    @Test
    void ironSwordRestartAfterChecksPersistedMarkerAndIronSwordInventory() throws Exception {
        Path runDir = Files.createTempDirectory("playable-25-after");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient client = new FakeScenarioClient();
        ClientScenarioReport before = new PlayableRealClientLoopScenario().run(
            "playable-25-iron-sword-save-restart-before",
            screenshotsDir,
            client
        );
        assertEquals("passed", before.result());
        client.operations.clear();

        ClientScenarioReport after = new PlayableRealClientLoopScenario().run(
            "playable-25-iron-sword-save-restart-after",
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
            client.operations.contains("count:minecraft:iron_sword"),
            "after phase must read the persisted iron sword inventory count"
        );
        assertFalse(client.usedDebugSetup(), "iron sword restart after phase must not use debug setup");
        assertTrue(
            after.observations().stream().anyMatch(entry -> entry.contains("restart marker persistence: passed")),
            "after phase must record persisted marker observation"
        );
        assertTrue(
            after.observations().stream().anyMatch(entry -> entry.contains("iron sword inventory persistence: passed")),
            "after phase must record persisted iron sword inventory observation"
        );
    }
    @Test
    void ironSwordRestartAfterFailsClosedWithoutMarker() {
        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-25-iron-sword-save-restart-after",
            Path.of("build/tmp/playable-25-missing/screenshots"),
            new FakeScenarioClient()
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("missing restart marker")),
            "after phase must fail closed when marker file is absent"
        );
    }
}
