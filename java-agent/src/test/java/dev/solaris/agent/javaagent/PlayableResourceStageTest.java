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

final class PlayableResourceStageTest {
    private static final String SUPPORTED_LOGS = String.join("|", List.of(
        "minecraft:oak_log",
        "minecraft:spruce_log",
        "minecraft:birch_log",
        "minecraft:jungle_log",
        "minecraft:acacia_log",
        "minecraft:dark_oak_log",
        "minecraft:mangrove_log",
        "minecraft:cherry_log",
        "minecraft:pale_oak_log"
    ));

    @Test
    void joinGeneratedSpawnProbePassesWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-01-join-generated-spawn",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertEquals(List.of(
            "selected",
            "count:minecraft:oak_log",
            "count:minecraft:oak_planks",
            "count:minecraft:crafting_table",
            "count:minecraft:wooden_pickaxe"
        ), client.operations);
        assertFalse(client.usedDebugSetup(), "playable join probe must not use debug setup helpers");
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("join/play-state: passed")),
            "join probe must record that the real client reached playable state"
        );
    }
    @Test
    void logToPlanksUsesNaturalLogDropAndInventoryRecipeWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-02a-natural-log-to-planks",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertEquals(List.of(
            "selected",
            "count:minecraft:oak_log",
            "count:minecraft:oak_planks",
            "count:minecraft:crafting_table",
            "count:minecraft:wooden_pickaxe",
            "findBreakable:" + SUPPORTED_LOGS + ":within-survival-reach",
            "approach:minecraft:oak_log:natural-log",
            "breakVisible:minecraft:oak_log:minecraft:oak_log",
            "collect:minecraft:oak_log:minecraft:oak_log:1",
            "count:minecraft:oak_log",
            "count:minecraft:oak_planks",
            "recipe:0:18:false",
            "waitCount:minecraft:oak_log:0",
            "waitCount:minecraft:oak_planks:4"
        ), client.operations);
        assertFalse(client.usedDebugSetup(), "wood-to-tool playable probe must not use debug setup helpers");
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("natural log break/drop/pickup: passed")),
            "wood-to-tool probe must record natural log break/drop/pickup evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("inventory recipe: passed")),
            "wood-to-tool probe must record inventory recipe evidence"
        );
    }
    @Test
    void logToPlanksWalksTowardLoadedNaturalLogBeforeBreakingIt() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.nearLogsAvailable = false;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-02a-natural-log-to-planks",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertEquals(List.of(
            "selected",
            "count:minecraft:oak_log",
            "count:minecraft:oak_planks",
            "count:minecraft:crafting_table",
            "count:minecraft:wooden_pickaxe",
            "findBreakable:" + SUPPORTED_LOGS + ":within-survival-reach",
            "findBreakable:" + SUPPORTED_LOGS + ":outside-survival-reach",
            "approach:minecraft:oak_log:far-natural-log",
            "findBreakable:" + SUPPORTED_LOGS + ":within-survival-reach",
            "approach:minecraft:oak_log:natural-log",
            "breakVisible:minecraft:oak_log:minecraft:oak_log",
            "collect:minecraft:oak_log:minecraft:oak_log:1",
            "count:minecraft:oak_log",
            "count:minecraft:oak_planks",
            "recipe:0:18:false",
            "waitCount:minecraft:oak_log:0",
            "waitCount:minecraft:oak_planks:4"
        ), client.operations);
        assertFalse(client.usedDebugSetup(), "walking to a natural log must not use debug setup helpers");
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("natural log approach: passed")),
            "wood-to-tool probe must record natural movement toward the loaded log"
        );
    }
    @Test
    void logToPlanksUsesDetectedGeneratedLogFamily() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.logItemId = "minecraft:spruce_log";
        client.planksItemId = "minecraft:spruce_planks";

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-02a-natural-log-to-planks",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertTrue(client.operations.contains("breakVisible:minecraft:spruce_log:minecraft:spruce_log"));
        assertTrue(client.operations.contains("recipe:0:20:false"));
        assertTrue(client.operations.contains("waitCount:minecraft:spruce_log:0"));
        assertTrue(client.operations.contains("waitCount:minecraft:spruce_planks:4"));
        assertFalse(client.usedDebugSetup(), "non-oak generated logs must not fall back to debug setup");
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("planks_item=minecraft:spruce_planks")),
            "scenario must record the detected planks family"
        );
    }
    @Test
    void woodToToolCraftsWoodenPickaxeInOpenedCraftingTableWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-02-natural-wood-to-tool",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertEquals(List.of(
            "selected",
            "count:minecraft:oak_log",
            "count:minecraft:oak_planks",
            "count:minecraft:crafting_table",
            "count:minecraft:wooden_pickaxe",
            "findBreakable:" + SUPPORTED_LOGS + ":within-survival-reach",
            "approach:minecraft:oak_log:natural-log",
            "breakVisible:minecraft:oak_log:minecraft:oak_log",
            "collect:minecraft:oak_log:minecraft:oak_log:1",
            "findBreakable:minecraft:oak_log:within-survival-reach",
            "approach:minecraft:oak_log:natural-log",
            "breakVisible:minecraft:oak_log:minecraft:oak_log",
            "collect:minecraft:oak_log:minecraft:oak_log:1",
            "findBreakable:minecraft:oak_log:within-survival-reach",
            "approach:minecraft:oak_log:natural-log",
            "breakVisible:minecraft:oak_log:minecraft:oak_log",
            "collect:minecraft:oak_log:minecraft:oak_log:1",
            "count:minecraft:oak_log",
            "count:minecraft:oak_planks",
            "recipe:0:18:true",
            "waitCount:minecraft:oak_log:0",
            "waitCount:minecraft:oak_planks:12",
            "count:minecraft:oak_planks",
            "count:minecraft:crafting_table",
            "recipe:0:10:false",
            "waitCount:minecraft:oak_planks:8",
            "waitCount:minecraft:crafting_table:1",
            "selectHotbar:minecraft:crafting_table:1",
            "findDry:within-survival-reach",
            "use:minecraft:crafting_table:table-clicked",
            "waitBlock:table-target:minecraft:crafting_table",
            "use:minecraft:crafting_table:table-target",
            "screen:net.minecraft.client.gui.screens.inventory.CraftingScreen",
            "containerId",
            "count:minecraft:oak_planks",
            "count:minecraft:stick",
            "recipe:7:21:false",
            "waitCount:minecraft:oak_planks:6",
            "waitCount:minecraft:stick:4",
            "count:minecraft:oak_planks",
            "count:minecraft:stick",
            "count:minecraft:wooden_pickaxe",
            "recipe:7:31:false",
            "waitCount:minecraft:oak_planks:3",
            "waitCount:minecraft:stick:2",
            "waitCount:minecraft:wooden_pickaxe:1",
            "closeScreen"
        ), client.operations);
        assertFalse(client.usedDebugSetup(), "wood-to-tool probe must not fall back to debug setup");
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("wooden pickaxe recipe: passed")),
            "wood-to-tool probe must record wooden pickaxe recipe evidence"
        );
    }
    @Test
    void logToPlanksRetriesReachableLogAfterFarApproachFailure() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.nearLogsAvailable = false;
        client.failFirstNaturalLogFarApproach = true;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-02a-natural-log-to-planks",
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
                entry.contains("natural log approach: failed")
            ),
            "scenario must record the failed far-log approach"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("natural log reachable fallback after failed approach: passed")
            ),
            "scenario must retry a reachable natural log after the failed far approach"
        );
        assertTrue(
            client.operations.contains("breakVisible:minecraft:oak_log:minecraft:oak_log"),
            "scenario must still break a natural log through the normal client path"
        );
        assertFalse(client.usedDebugSetup(), "reachable fallback must not use debug setup");
    }
    @Test
    void naturalLogPickupRetryContinuesAfterLeafPickupNoise() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.failedLogPickupsRemaining = 1;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-02a-natural-log-to-planks",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("held=minecraft:birch_leaves x5")),
            "log retry should record the noisy leaf pickup that did not satisfy log inventory truth"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("natural log break/drop/pickup: passed")),
            "log retry should continue until a real log pickup restores inventory"
        );
        assertEquals(
            2,
            client.operations.stream()
                    .filter("collect:minecraft:oak_log:minecraft:oak_log:1"::equals)
                    .count(),
            "log retry should collect again after the failed pickup"
        );
    }
    @Test
    void craftingTableOpenUsesEarnedInventoryItemWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-02b-natural-crafting-table-open",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertEquals(List.of(
            "selected",
            "count:minecraft:oak_log",
            "count:minecraft:oak_planks",
            "count:minecraft:crafting_table",
            "count:minecraft:wooden_pickaxe",
            "findBreakable:" + SUPPORTED_LOGS + ":within-survival-reach",
            "approach:minecraft:oak_log:natural-log",
            "breakVisible:minecraft:oak_log:minecraft:oak_log",
            "collect:minecraft:oak_log:minecraft:oak_log:1",
            "count:minecraft:oak_log",
            "count:minecraft:oak_planks",
            "recipe:0:18:false",
            "waitCount:minecraft:oak_log:0",
            "waitCount:minecraft:oak_planks:4",
            "count:minecraft:oak_planks",
            "count:minecraft:crafting_table",
            "recipe:0:10:false",
            "waitCount:minecraft:oak_planks:0",
            "waitCount:minecraft:crafting_table:1",
            "selectHotbar:minecraft:crafting_table:1",
            "findDry:within-survival-reach",
            "use:minecraft:crafting_table:table-clicked",
            "waitBlock:table-target:minecraft:crafting_table",
            "use:minecraft:crafting_table:table-target",
            "screen:net.minecraft.client.gui.screens.inventory.CraftingScreen",
            "closeScreen"
        ), client.operations);
        assertFalse(client.usedDebugSetup(), "crafting-table playable probe must not use debug setup helpers");
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("crafting table recipe: passed")),
            "crafting-table probe must record recipe evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("crafting table open: passed")),
            "crafting-table probe must record placed/opened table evidence"
        );
    }
    @Test
    void craftingTablePlacementFailureDoesNotIssueASecondInteraction() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.craftingTablePlacementObserved = false;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-02b-natural-crafting-table-open",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("failed", report.result());
        assertTrue(client.operations.contains("use:minecraft:crafting_table:table-clicked"));
        assertFalse(client.operations.contains("use:minecraft:crafting_table:table-target"));
        assertFalse(client.operations.contains("screen:net.minecraft.client.gui.screens.inventory.CraftingScreen"));
        assertFalse(client.operations.contains("closeScreen"));
    }
    @Test
    void craftingTablePlacementWaitsForObservedBlockAfterLocalFailureResult() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.craftingTablePlaceUseResult = "fail";

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-02b-natural-crafting-table-open",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertTrue(client.operations.contains("waitBlock:table-target:minecraft:crafting_table"));
        assertTrue(client.operations.contains("use:minecraft:crafting_table:table-target"));
    }
}
