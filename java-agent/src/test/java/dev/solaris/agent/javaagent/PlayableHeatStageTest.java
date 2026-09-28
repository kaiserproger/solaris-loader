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

final class PlayableHeatStageTest {
    @Test
    void furnacePlacementCraftsPlacesAndOpensEarnedFurnaceWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-07-furnace-placement-open",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertFalse(client.usedDebugSetup(), "furnace placement must not use debug setup");
        assertTrue(
            client.operations.contains("recipe:7:13:false"),
            "furnace placement must use the embedded furnace recipe id"
        );
        assertTrue(
            client.operations.contains("waitCount:minecraft:furnace:1"),
            "furnace placement must wait for the crafted furnace item"
        );
        assertTrue(
            client.operations.contains("use:minecraft:furnace:furnace-clicked"),
            "furnace placement must place the earned furnace item"
        );
        assertTrue(
            client.operations.contains("waitBlock:furnace-target:minecraft:furnace"),
            "furnace placement must wait for the furnace world block"
        );
        assertTrue(
            client.operations.contains("screen:net.minecraft.client.gui.screens.inventory.FurnaceScreen"),
            "furnace placement must open the furnace UI"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("furnace recipe: passed")),
            "furnace placement must record furnace recipe evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("furnace open: passed")),
            "furnace placement must record placed/opened furnace evidence"
        );
    }
    @Test
    void furnaceCharcoalSmeltsEarnedLogWithWoodFuelWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-08-furnace-charcoal-smelt",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertFalse(client.usedDebugSetup(), "furnace charcoal smelt must not use debug setup");
        assertTrue(
            client.operations.contains("breakVisible:minecraft:oak_log:minecraft:oak_log"),
            "charcoal smelt must collect an extra natural log as furnace input"
        );
        assertTrue(
            client.operations.contains("moveToContainer:0:minecraft:oak_log:1"),
            "charcoal smelt must move the earned log into furnace input slot 0"
        );
        assertTrue(
            client.operations.contains("moveToContainer:1:minecraft:oak_planks:1"),
            "charcoal smelt must move earned planks into furnace fuel slot 1"
        );
        assertTrue(
            client.operations.contains("moveFromContainer:1:minecraft:oak_planks:1"),
            "charcoal smelt must clear leftover planks from furnace fuel slot 1"
        );
        assertTrue(
            client.operations.contains("waitContainer:2:minecraft:charcoal:1"),
            "charcoal smelt must wait for furnace output slot 2"
        );
        assertTrue(
            client.operations.contains("moveFromContainer:2:minecraft:charcoal:1"),
            "charcoal smelt must move the cooked charcoal into inventory"
        );
        assertTrue(
            client.operations.contains("waitCount:minecraft:charcoal:1"),
            "charcoal smelt must wait for charcoal inventory convergence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("furnace input transfer: passed")),
            "charcoal smelt must record input transfer evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("furnace charcoal output: passed")),
            "charcoal smelt must record cooked output evidence"
        );
    }
    @Test
    void furnaceCharcoalSmeltStillBreaksReachableInputLogWhenCloseApproachFails() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.failPostFurnaceLogApproach = true;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-08-furnace-charcoal-smelt",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("furnace input log close approach: failed")
            ),
            "scenario must record that movement toward the already reachable input log did not improve position"
        );
        assertTrue(
            client.operations.contains("breakVisible:minecraft:oak_log:minecraft:oak_log"),
            "scenario must still attempt the reachable log break after close-approach failure"
        );
        assertFalse(client.usedDebugSetup(), "reachable fallback must not use debug setup");
    }
    @Test
    void torchCraftPlaceUsesCookedCharcoalAndPlacesEarnedTorchWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-09-torch-craft-place",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertFalse(client.usedDebugSetup(), "torch craft/place must not use debug setup");
        assertTrue(
            client.operations.contains("waitCount:minecraft:charcoal:1"),
            "torch craft/place must reuse the cooked charcoal from the furnace path"
        );
        assertTrue(
            client.operations.contains("recipe:0:27:false"),
            "torch craft/place must use the embedded torch recipe id"
        );
        assertTrue(
            client.operations.contains("waitCount:minecraft:charcoal:0"),
            "torch craft/place must consume one charcoal"
        );
        assertTrue(
            client.operations.contains("waitCount:minecraft:stick:1"),
            "torch craft/place must consume one earned stick"
        );
        assertTrue(
            client.operations.contains("waitCount:minecraft:torch:4"),
            "torch craft/place must create four torches"
        );
        assertTrue(
            client.operations.contains("selectHotbar:minecraft:torch:4"),
            "torch craft/place must select the earned torches from hotbar"
        );
        assertTrue(
            client.operations.contains("use:minecraft:torch:torch-clicked"),
            "torch craft/place must place the earned torch item"
        );
        assertTrue(
            client.operations.contains("waitBlock:torch-target:minecraft:torch"),
            "torch craft/place must wait for the torch world block"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("torch recipe: passed")),
            "torch craft/place must record torch recipe evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("torch placement: passed")),
            "torch craft/place must record placed torch evidence"
        );
    }
}
