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

final class PlayableFixtureStageTest {
    @Test
    void earnedDoorCraftsPlacesAndTogglesMatchingWoodenDoorWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.logItemId = "minecraft:birch_log";
        client.planksItemId = "minecraft:birch_planks";

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-16-earned-door-place-toggle",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertFalse(client.usedDebugSetup(), "earned door path must not use debug setup");
        assertTrue(
            client.operations.contains("recipe:7:36:false"),
            "earned door path must use the matching birch door recipe display id"
        );
        assertTrue(
            client.operations.contains("waitCount:minecraft:birch_door:3"),
            "earned door path must wait for the crafted birch door stack"
        );
        assertTrue(
            client.operations.contains("selectHotbar:minecraft:birch_door:1"),
            "earned door path must select the matching crafted door"
        );
        assertTrue(
            client.operations.contains("use:minecraft:birch_door:door-clicked"),
            "earned door path must place the crafted door"
        );
        assertTrue(
            client.operations.contains("waitBlock:door-target:minecraft:birch_door"),
            "earned door path must wait for the lower door block"
        );
        assertTrue(
            client.operations.stream().filter("use:minecraft:birch_door:door-target"::equals).count() >= 2,
            "earned door path must use the placed door twice to prove open and close interactions"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("door recipe: passed")),
            "earned door path must record matching door recipe evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("door toggle close: passed")),
            "earned door path must record open/close toggle evidence"
        );
    }
    @Test
    void earnedSignCraftsPlacesAndEditsMatchingWoodenSignWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.logItemId = "minecraft:birch_log";
        client.planksItemId = "minecraft:birch_planks";

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-17-earned-sign-place-edit",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertFalse(client.usedDebugSetup(), "earned sign path must not use debug setup");
        assertTrue(
            client.operations.contains("recipe:7:21:false"),
            "earned sign path must craft sticks from earned planks in the opened table"
        );
        assertTrue(
            client.operations.contains("recipe:7:45:false"),
            "earned sign path must use the matching birch sign recipe display id"
        );
        assertTrue(
            client.operations.contains("waitCount:minecraft:birch_sign:3"),
            "earned sign path must wait for the crafted birch sign stack"
        );
        assertTrue(
            client.operations.contains("selectHotbar:minecraft:birch_sign:1"),
            "earned sign path must select the matching crafted sign"
        );
        assertTrue(
            client.operations.contains("use:minecraft:birch_sign:sign-clicked"),
            "earned sign path must place the crafted sign"
        );
        assertTrue(
            client.operations.contains("waitBlock:sign-target:minecraft:birch_sign"),
            "earned sign path must wait for the placed sign block"
        );
        assertTrue(
            client.operations.contains("signEditor:sign-target"),
            "earned sign path must observe the vanilla sign editor"
        );
        assertTrue(
            client.operations.contains("signText:sign-target:Solaris|P17|NoDebug|OK"),
            "earned sign path must update the placed sign text through the client packet"
        );
        assertTrue(
            client.operations.contains("waitSignText:sign-target:Solaris|P17|NoDebug|OK"),
            "earned sign path must observe the edited sign text"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("sign recipe: passed")),
            "earned sign path must record matching sign recipe evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("sign text update: passed")),
            "earned sign path must record sign text update evidence"
        );
    }
}
