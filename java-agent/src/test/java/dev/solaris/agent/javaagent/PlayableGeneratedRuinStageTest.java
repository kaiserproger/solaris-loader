package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlayableGeneratedRuinStageTest {
    @Test
    void generatedRuinCacheBeforeQuickMovesExactLootWithoutDebugSetup() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.configureGeneratedRuinCache();
        Path screenshotsDir = Path.of("build/tmp/playable-46-before/screenshots");
        Files.deleteIfExists(screenshotsDir.getParent().resolve("playable-46-generated-ruin-cache-marker.properties"));

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-46-generated-ruin-cache-before",
            screenshotsDir,
            client
        );

        assertEquals("passed", report.result(), () -> String.join("\n", report.observations()));
        assertFalse(client.usedDebugSetup(), "generated ruin cache must not use debug setup");
        assertEquals(1, client.diamonds);
        assertEquals(4, client.lapisLazuli);
        assertEquals(2, client.breads);
        assertTrue(
            Files.isRegularFile(screenshotsDir.getParent().resolve("playable-46-generated-ruin-cache-marker.properties")),
            "before phase must persist the exact chest target and cleared loot slots"
        );
        assertTrue(client.operations.contains("approachPosition:72:8"));
        assertTrue(client.operations.contains("quickMoveContainer:0"));
        assertTrue(client.operations.contains("quickMoveContainer:1"));
        assertTrue(client.operations.contains("quickMoveContainer:2"));
    }

    @Test
    void generatedRuinCacheBeforeFailsWhenExactChestIsAbsent() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.configureGeneratedRuinCache();
        client.generatedRuinChestPresent = false;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-46-generated-ruin-cache-before",
            Path.of("build/tmp/playable-46-before-missing/screenshots"),
            client
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("exact generated chest Y: failed")),
            "before phase must fail closed when client world state does not expose the chest"
        );
    }

    @Test
    void generatedRuinCacheAfterProvesInventoryAndChestPersistence() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.configureGeneratedRuinCache();
        Path screenshotsDir = Path.of("build/tmp/playable-46-after/screenshots");
        Files.deleteIfExists(screenshotsDir.getParent().resolve("playable-46-generated-ruin-cache-marker.properties"));

        ClientScenarioReport before = new PlayableRealClientLoopScenario().run(
            "playable-46-generated-ruin-cache-before",
            screenshotsDir,
            client
        );
        ClientScenarioReport after = new PlayableRealClientLoopScenario().run(
            "playable-46-generated-ruin-cache-after",
            screenshotsDir,
            client
        );

        assertEquals("passed", before.result(), () -> String.join("\n", before.observations()));
        assertEquals("passed", after.result(), () -> String.join("\n", after.observations()));
        assertEquals(1, client.diamonds);
        assertEquals(4, client.lapisLazuli);
        assertEquals(2, client.breads);
        assertTrue(
            after.observations().stream().anyMatch(entry -> entry.contains("generated ruin cache persistence: passed")),
            "after phase must report both exact inventory counts and empty persisted slots"
        );
    }

    @Test
    void generatedRuinCacheAfterFailsWhenPersistedChestSlotIsNotEmpty() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.configureGeneratedRuinCache();
        Path screenshotsDir = Path.of("build/tmp/playable-46-after-nonempty/screenshots");
        Files.deleteIfExists(screenshotsDir.getParent().resolve("playable-46-generated-ruin-cache-marker.properties"));
        ClientScenarioReport before = new PlayableRealClientLoopScenario().run(
            "playable-46-generated-ruin-cache-before",
            screenshotsDir,
            client
        );
        client.containerItemIds[1] = "minecraft:lapis_lazuli";
        client.containerCounts[1] = 4;

        ClientScenarioReport after = new PlayableRealClientLoopScenario().run(
            "playable-46-generated-ruin-cache-after",
            screenshotsDir,
            client
        );

        assertEquals("passed", before.result(), () -> String.join("\n", before.observations()));
        assertEquals("failed", after.result());
        assertTrue(
            after.observations().stream().anyMatch(entry -> entry.contains("chest_slots_empty=false")),
            "after phase must fail closed when an expected loot slot remains populated"
        );
    }

    @Test
    void generatedRuinCacheAfterFailsWhenAnUnrecordedChestSlotIsNotEmpty() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.configureGeneratedRuinCache();
        Path screenshotsDir = Path.of("build/tmp/playable-46-after-unrecorded-slot/screenshots");
        Files.deleteIfExists(screenshotsDir.getParent().resolve("playable-46-generated-ruin-cache-marker.properties"));
        ClientScenarioReport before = new PlayableRealClientLoopScenario().run(
            "playable-46-generated-ruin-cache-before",
            screenshotsDir,
            client
        );
        client.containerItemIds[26] = "minecraft:diamond";
        client.containerCounts[26] = 1;

        ClientScenarioReport after = new PlayableRealClientLoopScenario().run(
            "playable-46-generated-ruin-cache-after",
            screenshotsDir,
            client
        );

        assertEquals("passed", before.result(), () -> String.join("\n", before.observations()));
        assertEquals("failed", after.result());
        assertTrue(
            after.observations().stream().anyMatch(entry -> entry.contains("chest_slots_empty=false")),
            "after phase must inspect all 27 chest slots, not just marker-listed loot slots"
        );
    }

    @Test
    void generatedRuinCacheAfterRejectsMarkerOutsideFixedRuinColumn() throws Exception {
        Path screenshotsDir = preparedGeneratedRuinCacheMarkerPath("marker-outside-column");
        writeGeneratedRuinCacheMarker(screenshotsDir, 73, 8, "up", 0, 1, 2);

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-46-generated-ruin-cache-after",
            screenshotsDir,
            persistedGeneratedRuinCacheClient()
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("invalid generated ruin cache marker")),
            "after phase must reject markers outside the fixed generated ruin column before interacting"
        );
    }

    @Test
    void generatedRuinCacheAfterRejectsMarkerWithUnsupportedFace() throws Exception {
        Path screenshotsDir = preparedGeneratedRuinCacheMarkerPath("marker-unsupported-face");
        writeGeneratedRuinCacheMarker(screenshotsDir, 72, 8, "northwest", 0, 1, 2);

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-46-generated-ruin-cache-after",
            screenshotsDir,
            persistedGeneratedRuinCacheClient()
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("invalid generated ruin cache marker")),
            "after phase must reject unsupported marker faces before interacting"
        );
    }

    @Test
    void generatedRuinCacheAfterRejectsMarkerSlotOutsideChestBounds() throws Exception {
        Path screenshotsDir = preparedGeneratedRuinCacheMarkerPath("marker-slot-outside-chest");
        writeGeneratedRuinCacheMarker(screenshotsDir, 72, 8, "up", 0, 1, 27);

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-46-generated-ruin-cache-after",
            screenshotsDir,
            persistedGeneratedRuinCacheClient()
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("invalid generated ruin")),
            "after phase must reject marker slots outside the 27 chest slots before interacting"
        );
    }


    private static Path preparedGeneratedRuinCacheMarkerPath(String label) throws Exception {
        Path screenshotsDir = Path.of("build/tmp/playable-46-" + label + "/screenshots");
        Files.createDirectories(screenshotsDir.getParent());
        Files.deleteIfExists(screenshotsDir.getParent().resolve("playable-46-generated-ruin-cache-marker.properties"));
        return screenshotsDir;
    }

    private static FakeScenarioClient persistedGeneratedRuinCacheClient() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.configureGeneratedRuinCache();
        client.diamonds = 1;
        client.lapisLazuli = 4;
        client.breads = 2;
        Arrays.fill(client.containerItemIds, null);
        Arrays.fill(client.containerCounts, 0);
        return client;
    }

    private static void writeGeneratedRuinCacheMarker(
        Path screenshotsDir,
        int x,
        int z,
        String face,
        int diamondSlot,
        int lapisSlot,
        int breadSlot
    ) throws Exception {
        Files.createDirectories(screenshotsDir.getParent());
        Files.writeString(
            screenshotsDir.getParent().resolve("playable-46-generated-ruin-cache-marker.properties"),
            "x=" + x + "\n"
                + "y=61\n"
                + "z=" + z + "\n"
                + "face=" + face + "\n"
                + "diamond_count=1\n"
                + "diamond_slot=" + diamondSlot + "\n"
                + "lapis_lazuli_count=4\n"
                + "lapis_lazuli_slot=" + lapisSlot + "\n"
                + "bread_count=2\n"
                + "bread_slot=" + breadSlot + "\n"
        );
    }
}
