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

final class PlayableStorageStageTest {
    @Test
    void earnedChestStorageCraftsChestAndDepositsEarnedFoodWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-12-earned-chest-storage",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertFalse(client.usedDebugSetup(), "earned chest storage path must not use debug setup");
        assertTrue(
            client.operations.contains("recipe:7:5:false"),
            "earned chest storage path must craft chest from earned planks in the crafting table"
        );
        assertTrue(
            client.operations.contains("selectHotbar:minecraft:chest:1"),
            "earned chest storage path must select the earned chest"
        );
        assertTrue(
            client.operations.contains("use:minecraft:chest:chest-clicked"),
            "earned chest storage path must place/open the earned chest"
        );
        assertTrue(
            client.operations.contains("attackEntityDrop:minecraft:cow:entity_id=42:minecraft:beef:1"),
            "earned chest storage path must collect an earned passive food item for storage"
        );
        assertTrue(
            client.operations.contains("moveToContainer:0:minecraft:beef:1"),
            "earned chest storage path must deposit an earned item into the chest"
        );
        assertTrue(
            client.operations.contains("waitContainer:0:minecraft:beef:1"),
            "earned chest storage path must observe the deposited item in the chest slot"
        );
        assertFalse(
            client.operations.contains("approach:minecraft:chest:chest-target"),
            "earned chest storage path should place the chest near the earned item instead of returning across terrain"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("earned chest storage: passed")),
            "earned chest storage path must record container storage evidence"
        );
    }
    @Test
    void earnedChestStorageDoesNotRequireFourthUpperLog() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.fourthLogIsReachableDownFace = true;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-12-earned-chest-storage",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertFalse(
            client.operations.contains("approach:minecraft:oak_log:upper-natural-log"),
            "earned chest storage should not depend on a brittle fourth upper log"
        );
    }
    @Test
    void chestStorageSaveRestartBeforeWritesChestMarkerWithoutDebugSetup() throws Exception {
        Path runDir = Files.createTempDirectory("playable-13-before");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-13-chest-storage-save-restart-before",
            screenshotsDir,
            client
        );

        assertEquals("passed", report.result());
        assertFalse(client.usedDebugSetup(), "chest save/restart before phase must not use debug setup");
        assertTrue(
            Files.isRegularFile(runDir.resolve("playable-13-chest-storage-marker.properties")),
            "before phase must write a chest marker for the runner-managed after phase"
        );
        assertTrue(
            client.operations.contains("moveToContainer:0:minecraft:beef:1"),
            "before phase must store the earned item in the chest before restart"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("runner-managed restart: pending")),
            "before phase must record that the runner owns the restart boundary"
        );
    }
    @Test
    void chestStorageSaveRestartBeforeContinuesWhenStoredSlotIsConfirmedButScreenCloseIsPending()
        throws Exception {
        Path runDir = Files.createTempDirectory("playable-13-before-close-pending");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient client = new FakeScenarioClient();
        client.failCloseCurrentScreen = true;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-13-chest-storage-save-restart-before",
            screenshotsDir,
            client
        );

        assertEquals("passed", report.result());
        assertTrue(
            Files.isRegularFile(runDir.resolve("playable-13-chest-storage-marker.properties")),
            "before phase must write the marker once the stored chest slot is confirmed"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("slot_matched=true")),
            "before phase must keep the storage evidence even when close remains pending"
        );
    }
    @Test
    void chestStorageSaveRestartAfterVerifiesPersistedChestSlot() throws Exception {
        Path runDir = Files.createTempDirectory("playable-13-after");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient client = new FakeScenarioClient();
        ClientScenarioReport before = new PlayableRealClientLoopScenario().run(
            "playable-13-chest-storage-save-restart-before",
            screenshotsDir,
            client
        );
        assertEquals("passed", before.result());
        client.operations.clear();

        ClientScenarioReport after = new PlayableRealClientLoopScenario().run(
            "playable-13-chest-storage-save-restart-after",
            screenshotsDir,
            client
        );

        assertEquals("passed", after.result());
        assertTrue(
            client.operations.contains("waitBlock:chest-marker:minecraft:chest"),
            "after phase must verify the persisted chest block"
        );
        assertTrue(
            client.operations.contains("waitContainer:0:minecraft:beef:1"),
            "after phase must verify the persisted stored item in chest slot 0"
        );
        assertTrue(
            after.observations().stream().anyMatch(entry -> entry.contains("chest storage persistence: passed")),
            "after phase must record chest slot persistence evidence"
        );
    }
    @Test
    void chestStorageSaveRestartAfterFailsClosedWithoutMarker() {
        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-13-chest-storage-save-restart-after",
            Path.of("build/tmp/playable-13-missing/screenshots"),
            new FakeScenarioClient()
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("missing chest storage marker")),
            "after phase must fail closed when the chest marker is absent"
        );
    }
}
