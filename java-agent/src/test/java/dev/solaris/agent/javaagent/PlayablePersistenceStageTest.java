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

final class PlayablePersistenceStageTest {
    @Test
    void saveRestartBeforeUsesNaturalCraftedTableAsPersistenceMarker() {
        FakeScenarioClient client = new FakeScenarioClient();
        Path screenshotsDir = Path.of("build/tmp/playable-03-test/screenshots");

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-03-save-restart-before",
            screenshotsDir,
            client
        );

        assertEquals("passed", report.result());
        assertFalse(client.usedDebugSetup(), "playable save/restart before phase must not use debug setup");
        assertTrue(
            screenshotsDir.getParent().resolve("playable-03-save-restart-marker.properties").toFile().isFile(),
            "before phase must write marker coordinates for after phase"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("restart marker placement: passed")),
            "before phase must record marker placement"
        );
    }
    @Test
    void saveRestartAfterChecksPersistedMarkerAndWoodenPickaxeInventory() {
        FakeScenarioClient client = new FakeScenarioClient();
        Path screenshotsDir = Path.of("build/tmp/playable-03-test/screenshots");
        new PlayableRealClientLoopScenario().run(
            "playable-03-save-restart-before",
            screenshotsDir,
            client
        );
        client.operations.clear();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-03-save-restart-after",
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
            "count:minecraft:wooden_pickaxe"
        ), client.operations);
        assertFalse(client.usedDebugSetup(), "playable save/restart after phase must not use debug setup");
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("restart marker persistence: passed")),
            "after phase must record persisted marker observation"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("inventory persistence: passed")),
            "after phase must record persisted wooden pickaxe inventory observation"
        );
    }
    @Test
    void saveRestartAfterFailsClosedWithoutMarker() {
        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-03-save-restart-after",
            Path.of("build/tmp/playable-03-missing/screenshots"),
            new FakeScenarioClient()
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("missing restart marker")),
            "after phase must fail closed when marker file is absent"
        );
    }
}
