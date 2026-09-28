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

final class PlayableRestStageTest {
    @Test
    void earnedBedSleepCraftsWhiteBedAndSkipsNightWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-14-earned-bed-sleep",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertFalse(client.usedDebugSetup(), "earned bed sleep path must not use debug setup");
        assertTrue(
            client.operations.contains("findEntity:minecraft:sheep:outside-survival-reach"),
            "earned bed path must scan for natural sheep as the wool source"
        );
        assertTrue(
            client.operations.contains("attackEntityDrop:minecraft:sheep:entity_id=43:minecraft:white_wool:1"),
            "earned bed path must collect wool through a natural sheep drop"
        );
        assertTrue(
            client.operations.contains("recipe:7:34:false"),
            "earned bed path must use the appended embedded white-bed recipe display id"
        );
        assertTrue(
            client.operations.contains("selectHotbar:minecraft:white_bed:1"),
            "earned bed path must select the crafted bed"
        );
        assertTrue(
            client.operations.contains("use:minecraft:white_bed:bed-clicked"),
            "earned bed path must place the crafted bed"
        );
        assertTrue(
            client.operations.contains("waitNight"),
            "earned bed path must wait for natural night instead of setting time by command"
        );
        assertTrue(
            client.operations.contains("waitMorning"),
            "earned bed path must observe the server time skip back to morning"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("bed sleep skip: passed")),
            "earned bed path must record night-to-morning sleep evidence"
        );
    }
    @Test
    void earnedBedSleepSkipsObservedBlackSheepForMatchingWhiteBedWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.firstObservedSheepWoolItemId = "minecraft:black_wool";

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-14-earned-bed-sleep",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result(), () -> String.join("\n", report.observations()));
        assertFalse(client.usedDebugSetup(), "black sheep fallback must not use debug setup");
        assertTrue(
            client.operations.contains("findSheepWool:minecraft:white_wool:outside-survival-reach"),
            "earned bed path must select only sheep with white wool after observing another color"
        );
        assertTrue(
            client.operations.contains("attackEntityDrop:minecraft:sheep:entity_id=100:minecraft:white_wool:1"),
            "earned bed path must collect three matching white wool drops"
        );
        assertEquals(
            3L,
            client.operations.stream()
                .filter(operation -> operation.startsWith("attackEntityDrop:minecraft:sheep:"))
                .count(),
            "earned bed path must collect exactly three matching white wool drops"
        );
        assertTrue(
            client.operations.contains("recipe:7:34:false"),
            "earned bed path must use the normal recipe for the matching white bed"
        );
        assertTrue(
            client.operations.contains("selectHotbar:minecraft:white_bed:1"),
            "earned bed path must select the crafted matching bed"
        );
        assertFalse(
            client.operations.stream().anyMatch(operation ->
                operation.startsWith("attackEntityDrop:") && operation.contains(":entity_id=43:")
            ),
            "earned bed path must not attack sheep whose wool cannot craft the selected bed"
        );
        for (int entityId : List.of(100, 101, 102)) {
            assertTrue(
                client.operations.contains(
                    "attackEntityDrop:minecraft:sheep:entity_id="
                        + entityId
                        + ":minecraft:white_wool:1"
                ),
                "earned bed path must attack selected white sheep entity_id=" + entityId
            );
        }
        int thirdWhiteAttack = client.operations.indexOf(
            "attackEntityDrop:minecraft:sheep:entity_id=102:minecraft:white_wool:1"
        );
        int stableRecipeSubmission = client.operations.indexOf("recipe:7:34:false");
        assertTrue(
            thirdWhiteAttack < stableRecipeSubmission,
            "stable white-bed recipe display id must be submitted only after three white wool drops"
        );
        assertFalse(
            client.operations.stream().anyMatch(operation -> operation.startsWith("findBedRecipe:")),
            "earned bed path must not depend on dynamic recipe-book lookup"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("sheep wool scan: skipped") && entry.contains("wool_item=minecraft:black_wool")
            ),
            "earned bed path must record the observed black sheep metadata before selecting white wool"
        );
        assertFalse(
            client.operations.stream().anyMatch(operation -> operation.startsWith("wait_ticks:")),
            "earned bed color handling must not use tick guesses"
        );
    }
}
