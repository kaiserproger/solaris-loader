package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.APPROACH_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.BLOCK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.CONTAINER_SCREEN;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.INVENTORY_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableCoordinates.coordinates;

/** Before/after restart assertions for the natural generated ruin chest. */
final class PlayableGeneratedRuinStage {
    private PlayableGeneratedRuinStage() {
    }
    private static final String GENERATED_RUIN_CACHE_MARKER_FILE =
        "playable-46-generated-ruin-cache-marker.properties";

    private static final int GENERATED_RUIN_CENTER_X = 72;
    private static final int GENERATED_RUIN_CENTER_Z = 8;
    private static final int GENERATED_RUIN_CHEST_SLOT_COUNT = 27;
    private static final List<String> GENERATED_RUIN_SUPPORTED_FACES = List.of(
        "up",
        "down",
        "north",
        "south",
        "west",
        "east"
    );
    private static final List<GeneratedRuinLoot> GENERATED_RUIN_LOOT = List.of(
        new GeneratedRuinLoot("minecraft:diamond", 1, -1),
        new GeneratedRuinLoot("minecraft:lapis_lazuli", 4, -1),
        new GeneratedRuinLoot("minecraft:bread", 2, -1)
    );

    private record GeneratedRuinLoot(String itemId, int count, int slot) {}

    private record GeneratedRuinCacheMarker(
        ScenarioBlockTarget chestTarget,
        List<GeneratedRuinLoot> loot
    ) {}

    static ClientScenarioReport runBefore(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        boolean reachedCenter = client.approachPosition(
            GENERATED_RUIN_CENTER_X,
            GENERATED_RUIN_CENTER_Z,
            APPROACH_TIMEOUT
        );
        observations.add(
            "generated ruin center approach: " + (reachedCenter ? "passed" : "failed")
                + " x=" + GENERATED_RUIN_CENTER_X
                + " z=" + GENERATED_RUIN_CENTER_Z
        );
        if (!reachedCenter) {
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioBlockTarget chest = client.findLoadedBlockInColumn(
            GENERATED_RUIN_CENTER_X,
            GENERATED_RUIN_CENTER_Z,
            List.of("minecraft:chest")
        );
        boolean exactChest = chest != null
            && chest.x() == GENERATED_RUIN_CENTER_X
            && chest.z() == GENERATED_RUIN_CENTER_Z
            && "minecraft:chest".equals(chest.blockId());
        observations.add(
            "exact generated chest Y: " + (exactChest ? "passed" : "failed")
                + (chest == null ? "" : " target=" + coordinates(chest))
        );
        if (!exactChest) {
            return new ClientScenarioReport("failed", id, observations);
        }

        boolean approachedChest = client.approachBlock(chest, APPROACH_TIMEOUT);
        observations.add(
            "generated ruin chest approach: " + (approachedChest ? "passed" : "failed")
                + " target=" + coordinates(chest)
        );
        if (!approachedChest) {
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioUseResult openUse = client.useItemOn(chest, client.selectedItem());
        boolean opened = client.waitForScreenClassName(CONTAINER_SCREEN, INVENTORY_TIMEOUT);
        int containerId = opened ? client.activeContainerId() : -1;
        boolean openedFromClientState = opened && containerId > 0;
        observations.add(
            "generated ruin chest open client-state: " + (openedFromClientState ? "passed" : "failed")
                + " use_result=" + openUse.result()
                + " container_id=" + containerId
        );
        if (!openedFromClientState) {
            return new ClientScenarioReport("failed", id, observations);
        }

        List<GeneratedRuinLoot> movedLoot = new ArrayList<>();
        boolean lootMoved = true;
        for (GeneratedRuinLoot expected : GENERATED_RUIN_LOOT) {
            int inventoryBefore = client.inventoryCount(expected.itemId());
            int slot = client.findContainerSlot(expected.itemId(), expected.count());
            boolean emptyInventoryBefore = inventoryBefore == 0;
            boolean moved = emptyInventoryBefore
                && slot >= 0
                && client.quickMoveContainerSlot(slot, INVENTORY_TIMEOUT);
            boolean chestSlotEmpty = moved && client.waitForContainerSlotEmpty(slot, INVENTORY_TIMEOUT);
            boolean inventoryMatched = chestSlotEmpty
                && client.waitForInventoryCount(expected.itemId(), expected.count(), INVENTORY_TIMEOUT);
            boolean itemPassed = emptyInventoryBefore && slot >= 0 && moved && chestSlotEmpty && inventoryMatched;
            observations.add(
                "generated ruin loot quick-move: " + (itemPassed ? "passed" : "failed")
                    + " item=" + expected.itemId()
                    + " count=" + expected.count()
                    + " slot=" + slot
                    + " inventory_before=" + inventoryBefore
                    + " authoritative_move=" + moved
                    + " chest_slot_empty=" + chestSlotEmpty
                    + " inventory_matched=" + inventoryMatched
            );
            lootMoved &= itemPassed;
            movedLoot.add(new GeneratedRuinLoot(expected.itemId(), expected.count(), slot));
        }
        boolean closed = client.closeCurrentScreen(INVENTORY_TIMEOUT);
        observations.add("generated ruin chest close client-state: " + (closed ? "passed" : "failed"));
        if (!lootMoved || !closed) {
            return new ClientScenarioReport("failed", id, observations);
        }

        writeGeneratedRuinCacheMarker(
            generatedRuinCacheMarkerPath(screenshotsDir),
            new GeneratedRuinCacheMarker(chest, movedLoot)
        );
        observations.add("runner-managed restart: pending clean server restart and generated ruin cache rejoin check");
        return new ClientScenarioReport("passed", id, observations);
    }

    static ClientScenarioReport runAfter(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = generatedRuinCacheMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing generated ruin cache marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }

        GeneratedRuinCacheMarker marker = readGeneratedRuinCacheMarker(markerPath);
        boolean reachedCenter = client.approachPosition(
            GENERATED_RUIN_CENTER_X,
            GENERATED_RUIN_CENTER_Z,
            APPROACH_TIMEOUT
        );
        boolean chestPersisted = reachedCenter
            && client.waitForBlock(marker.chestTarget(), "minecraft:chest", BLOCK_TIMEOUT);
        observations.add(
            "generated ruin chest persistence client-state: " + (chestPersisted ? "passed" : "failed")
                + " target=" + coordinates(marker.chestTarget())
        );
        if (!chestPersisted || !client.approachBlock(marker.chestTarget(), APPROACH_TIMEOUT)) {
            observations.add("generated ruin chest re-approach: failed");
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioUseResult openUse = client.useItemOn(marker.chestTarget(), client.selectedItem());
        boolean opened = client.waitForScreenClassName(CONTAINER_SCREEN, INVENTORY_TIMEOUT);
        int containerId = opened ? client.activeContainerId() : -1;
        boolean openedFromClientState = opened && containerId > 0;
        observations.add(
            "generated ruin chest reopen client-state: " + (openedFromClientState ? "passed" : "failed")
                + " use_result=" + openUse.result()
                + " container_id=" + containerId
        );
        if (!openedFromClientState) {
            return new ClientScenarioReport("failed", id, observations);
        }

        boolean inventoryMatched = true;
        for (GeneratedRuinLoot expected : marker.loot()) {
            boolean itemInventoryMatched = client.waitForInventoryCount(
                expected.itemId(),
                expected.count(),
                INVENTORY_TIMEOUT
            );
            inventoryMatched &= itemInventoryMatched;
            observations.add(
                "generated ruin persistence item: "
                    + (itemInventoryMatched ? "passed" : "failed")
                    + " item=" + expected.itemId()
                    + " count=" + expected.count()
                    + " slot=" + expected.slot()
                    + " inventory_matched=" + itemInventoryMatched
            );
        }
        boolean chestSlotsEmpty = true;
        for (int slot = 0; slot < GENERATED_RUIN_CHEST_SLOT_COUNT; slot++) {
            boolean slotEmpty = client.waitForContainerSlotEmpty(slot, INVENTORY_TIMEOUT);
            chestSlotsEmpty &= slotEmpty;
            observations.add(
                "generated ruin chest slot empty: " + (slotEmpty ? "passed" : "failed")
                    + " slot=" + slot
            );
        }
        boolean closed = client.closeCurrentScreen(INVENTORY_TIMEOUT);
        boolean passed = inventoryMatched && chestSlotsEmpty && closed;
        observations.add(
            "generated ruin cache persistence: " + (passed ? "passed" : "failed")
                + " inventory_counts_matched=" + inventoryMatched
                + " chest_slots_empty=" + chestSlotsEmpty
                + " closed=" + closed
        );
        return new ClientScenarioReport(passed ? "passed" : "failed", id, observations);
    }

    private static Path generatedRuinCacheMarkerPath(Path screenshotsDir) {
        Path runDir = screenshotsDir.getParent();
        return (runDir == null ? Path.of(".") : runDir).resolve(GENERATED_RUIN_CACHE_MARKER_FILE);
    }

    private static void writeGeneratedRuinCacheMarker(Path path, GeneratedRuinCacheMarker marker)
        throws IOException {
        Files.createDirectories(path.getParent());
        StringBuilder content = new StringBuilder()
            .append("x=").append(marker.chestTarget().x()).append('\n')
            .append("y=").append(marker.chestTarget().y()).append('\n')
            .append("z=").append(marker.chestTarget().z()).append('\n')
            .append("face=").append(marker.chestTarget().face()).append('\n');
        for (GeneratedRuinLoot loot : marker.loot()) {
            String key = loot.itemId().substring("minecraft:".length());
            content.append(key).append("_count=").append(loot.count()).append('\n');
            content.append(key).append("_slot=").append(loot.slot()).append('\n');
        }
        Files.writeString(path, content.toString());
    }

    private static GeneratedRuinCacheMarker readGeneratedRuinCacheMarker(Path path) throws IOException {
        Integer x = null;
        Integer y = null;
        Integer z = null;
        String face = null;
        List<GeneratedRuinLoot> loot = new ArrayList<>();
        Map<String, String> values = new java.util.HashMap<>();
        for (String line : Files.readAllLines(path)) {
            String[] parts = line.split("=", 2);
            if (parts.length == 2) {
                values.put(parts[0], parts[1]);
            }
        }
        try {
            x = Integer.valueOf(values.get("x"));
            y = Integer.valueOf(values.get("y"));
            z = Integer.valueOf(values.get("z"));
            face = values.get("face");
            for (GeneratedRuinLoot expected : GENERATED_RUIN_LOOT) {
                String key = expected.itemId().substring("minecraft:".length());
                int count = Integer.parseInt(values.get(key + "_count"));
                int slot = Integer.parseInt(values.get(key + "_slot"));
                if (
                    count != expected.count()
                        || slot < 0
                        || slot >= GENERATED_RUIN_CHEST_SLOT_COUNT
                ) {
                    throw new IOException("invalid generated ruin loot marker entry for " + expected.itemId());
                }
                loot.add(new GeneratedRuinLoot(expected.itemId(), count, slot));
            }
        } catch (NullPointerException | NumberFormatException error) {
            throw new IOException("invalid generated ruin cache marker file: " + path, error);
        }
        if (
            x != GENERATED_RUIN_CENTER_X
                || z != GENERATED_RUIN_CENTER_Z
                || !GENERATED_RUIN_SUPPORTED_FACES.contains(face)
                || loot.stream().map(GeneratedRuinLoot::slot).distinct().count() != loot.size()
        ) {
            throw new IOException("invalid generated ruin cache marker file: " + path);
        }
        return new GeneratedRuinCacheMarker(
            new ScenarioBlockTarget(x, y, z, face, "generated-ruin-cache", "minecraft:chest"),
            List.copyOf(loot)
        );
    }
}
