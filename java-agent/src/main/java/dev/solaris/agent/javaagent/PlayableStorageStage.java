package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static dev.solaris.agent.javaagent.PlayableCoordinates.coordinates;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.APPROACH_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.BLOCK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.CHEST_RECIPE_RESULT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.CONTAINER_SCREEN;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.HOTBAR_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.INVENTORY_TIMEOUT;

/** Earned chest storage plus save/restart marker assertions. */
final class PlayableStorageStage {
    private PlayableStorageStage() {
    }

    private static final String CHEST_STORAGE_MARKER_FILE = "playable-13-chest-storage-marker.properties";

    record ChestStorageResult(
        ClientScenarioReport report,
        ScenarioBlockTarget chestTarget,
        String itemId,
        int count
    ) {}

    record ChestStorageMarker(ScenarioBlockTarget chestTarget, String itemId, int count) {}

    static ClientScenarioReport runEarnedChestStorage(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        return storeEarnedItemInChest(id, observations, client, true).report();
    }

    static ChestStorageResult storeEarnedItemInChest(
        String id,
        List<String> observations,
        ScenarioClient client,
        boolean requireScreenClosed
    ) throws Exception {
        PlayableResourceStage.LogToPlanksResult planks = PlayableResourceStage.runLogToPlanks(id, observations, client, 3, true, false);
        if (!"passed".equals(planks.report().result())) {
            return new ChestStorageResult(planks.report(), null, null, 0);
        }
        PlayableResourceStage.CraftingTableOpenResult table = PlayableResourceStage.craftPlaceAndOpenTable(
            id,
            observations,
            client,
            planks.planks().planksItemId(),
            false
        );
        if (!"passed".equals(table.report().result())) {
            return new ChestStorageResult(table.report(), null, null, 0);
        }

        int planksCount = client.inventoryCount(planks.planks().planksItemId());
        int chestCount = client.inventoryCount("minecraft:chest");
        if (planksCount < 8) {
            observations.add("chest recipe: failed fewer than eight earned planks available");
            return new ChestStorageResult(new ClientScenarioReport("failed", id, observations), null, null, 0);
        }
        int expectedPlanksAfterChest = planksCount - 8;
        int expectedChestCount = chestCount + 1;
        int containerId = client.activeContainerId();
        client.placeRecipe(containerId, client.requireRecipeDisplayId(CHEST_RECIPE_RESULT), false);
        boolean planksConsumed = client.waitForInventoryCount(
            planks.planks().planksItemId(),
            expectedPlanksAfterChest,
            INVENTORY_TIMEOUT
        );
        boolean chestCreated = client.waitForInventoryCount(
            "minecraft:chest",
            expectedChestCount,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "chest recipe: " + (planksConsumed && chestCreated ? "passed" : "failed")
                + " container_id=" + containerId
                + " recipe_result=" + CHEST_RECIPE_RESULT
                + " planks_item=" + planks.planks().planksItemId()
                + " planks_expected_count=" + expectedPlanksAfterChest
                + " planks_count_matched=" + planksConsumed
                + " chest_expected_count=" + expectedChestCount
                + " chest_count_matched=" + chestCreated
        );
        if (!planksConsumed || !chestCreated) {
            return new ChestStorageResult(new ClientScenarioReport("failed", id, observations), null, null, 0);
        }
        boolean craftingClosed = client.closeCurrentScreen(INVENTORY_TIMEOUT);
        observations.add("crafting table screen close after chest: " + (craftingClosed ? "passed" : "failed"));
        if (!craftingClosed) {
            return new ChestStorageResult(new ClientScenarioReport("failed", id, observations), null, null, 0);
        }

        PlayableFoodStage.PassiveFoodDropResult drop = PlayableFoodStage.collectPassiveFoodDrop(id, observations, client);
        if (!"passed".equals(drop.report().result())) {
            return new ChestStorageResult(drop.report(), null, null, 0);
        }

        ScenarioHeldItem chest = client.selectHotbarItem("minecraft:chest", 1, HOTBAR_TIMEOUT);
        if (!chest.matches("minecraft:chest", 1)) {
            observations.add("blocked: earned chest exists but is not selectable from hotbar");
            return new ChestStorageResult(new ClientScenarioReport("blocked", id, observations), null, null, 0);
        }
        ScenarioBlockPair pair = client.findDryPlaceablePair(ScenarioReach.WITHIN_SURVIVAL_REACH);
        if (pair == null) {
            observations.add("blocked: no loaded dry target found for chest placement");
            return new ChestStorageResult(new ClientScenarioReport("blocked", id, observations), null, null, 0);
        }
        ScenarioUseResult placeUse = client.useItemOn(pair.clicked(), chest);
        boolean placed = client.waitForBlock(pair.target(), "minecraft:chest", BLOCK_TIMEOUT);
        ScenarioBlockTarget chestTarget = new ScenarioBlockTarget(
            pair.target().x(),
            pair.target().y(),
            pair.target().z(),
            "up",
            "chest-target",
            "minecraft:chest"
        );

        ScenarioHeldItem storedItem = client.selectHotbarItem(drop.dropItemId(), 1, HOTBAR_TIMEOUT);
        if (!storedItem.matches(drop.dropItemId(), 1)) {
            observations.add("blocked: earned chest storage item exists but is not selectable from hotbar");
            return new ChestStorageResult(new ClientScenarioReport("blocked", id, observations), chestTarget, null, 0);
        }

        ScenarioUseResult openUse = client.useItemOn(chestTarget, storedItem);
        boolean opened = client.waitForScreenClassName(CONTAINER_SCREEN, INVENTORY_TIMEOUT);
        observations.add(
            "chest placement/open: " + (placed && opened ? "passed" : "failed")
                + " place_use_result=" + placeUse.result()
                + " placed=" + placed
                + " open_use_result=" + openUse.result()
                + " screen=" + CONTAINER_SCREEN
                + " screen_matched=" + opened
                + " target=" + coordinates(chestTarget)
        );
        if (!placed || !opened) {
            return new ChestStorageResult(
                new ClientScenarioReport("failed", id, observations),
                chestTarget,
                drop.dropItemId(),
                1
            );
        }

        boolean deposited = client.moveSelectedItemToContainerSlot(
            0,
            drop.dropItemId(),
            1,
            INVENTORY_TIMEOUT
        );
        boolean slotMatched = deposited
            && client.waitForContainerSlot(0, drop.dropItemId(), 1, INVENTORY_TIMEOUT);
        boolean closed = client.closeCurrentScreen(INVENTORY_TIMEOUT);
        boolean passed = deposited && slotMatched && (closed || !requireScreenClosed);
        observations.add(
            "earned chest storage: " + (passed ? "passed" : "failed")
                + " slot=0"
                + " item=" + drop.dropItemId()
                + " moved=" + deposited
                + " slot_matched=" + slotMatched
                + " closed=" + closed
                + " close_required=" + requireScreenClosed
        );
        return new ChestStorageResult(
            new ClientScenarioReport(passed ? "passed" : "failed", id, observations),
            chestTarget,
            drop.dropItemId(),
            1
        );
    }

    static ClientScenarioReport runChestStorageSaveRestartBefore(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        ChestStorageResult stored = storeEarnedItemInChest(id, observations, client, false);
        if (!"passed".equals(stored.report().result())) {
            return stored.report();
        }
        if (stored.chestTarget() == null || stored.itemId() == null || stored.count() <= 0) {
            observations.add("chest storage marker: failed missing stored chest target or item");
            return new ClientScenarioReport("failed", id, observations);
        }

        writeChestStorageMarker(chestStorageMarkerPath(screenshotsDir), stored);
        observations.add(
            "chest storage marker: passed target=" + coordinates(stored.chestTarget())
                + " item=" + stored.itemId()
                + " count=" + stored.count()
        );
        observations.add("runner-managed restart: pending clean server restart and chest storage rejoin check");
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport("passed", id, observations);
    }

    static ClientScenarioReport runChestStorageSaveRestartAfter(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = chestStorageMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing chest storage marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }

        ChestStorageMarker marker = readChestStorageMarker(markerPath);
        boolean chestPersisted = client.waitForBlock(marker.chestTarget(), "minecraft:chest", BLOCK_TIMEOUT);
        observations.add(
            "chest block persistence: " + (chestPersisted ? "passed" : "failed")
                + " target=" + coordinates(marker.chestTarget())
        );
        ScenarioUseResult openUse = new ScenarioUseResult("skipped");
        boolean opened = false;
        boolean slotMatched = false;
        boolean closed = false;
        if (chestPersisted) {
            openUse = client.useItemOn(marker.chestTarget(), client.selectedItem());
            opened = client.waitForScreenClassName(CONTAINER_SCREEN, INVENTORY_TIMEOUT);
            slotMatched = opened
                && client.waitForContainerSlot(0, marker.itemId(), marker.count(), INVENTORY_TIMEOUT);
            closed = opened && client.closeCurrentScreen(INVENTORY_TIMEOUT);
        }
        observations.add(
            "chest storage reopen: " + (opened ? "passed" : "failed")
                + " open_use_result=" + openUse.result()
                + " screen=" + CONTAINER_SCREEN
                + " screen_matched=" + opened
        );
        boolean passed = chestPersisted && opened && slotMatched && closed;
        observations.add(
            "chest storage persistence: " + (passed ? "passed" : "failed")
                + " slot=0"
                + " item=" + marker.itemId()
                + " count=" + marker.count()
                + " slot_matched=" + slotMatched
                + " closed=" + closed
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport(passed ? "passed" : "failed", id, observations);
    }

    static Path chestStorageMarkerPath(Path screenshotsDir) {
        Path runDir = screenshotsDir.getParent();
        return (runDir == null ? Path.of(".") : runDir).resolve(CHEST_STORAGE_MARKER_FILE);
    }

    static void writeChestStorageMarker(Path path, ChestStorageResult stored) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(
            path,
            "x=" + stored.chestTarget().x() + "\n"
                + "y=" + stored.chestTarget().y() + "\n"
                + "z=" + stored.chestTarget().z() + "\n"
                + "face=" + stored.chestTarget().face() + "\n"
                + "item=" + stored.itemId() + "\n"
                + "count=" + stored.count() + "\n"
        );
    }

    static ChestStorageMarker readChestStorageMarker(Path path) throws IOException {
        return readChestStorageMarker(path, "chest-marker");
    }

    static ChestStorageMarker readSharedChestMarker(Path path) throws IOException {
        return readChestStorageMarker(path, "playable-two-client-chest-marker");
    }

    static ChestStorageMarker readChestStorageMarker(Path path, String label) throws IOException {
        String item = null;
        Integer count = null;
        for (String line : Files.readAllLines(path)) {
            String[] parts = line.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            switch (parts[0]) {
                case "item" -> item = parts[1];
                case "count" -> count = Integer.parseInt(parts[1]);
                default -> {
                }
            }
        }
        if (item == null || count == null || count <= 0) {
            throw new IOException("invalid chest storage marker file: " + path);
        }
        return new ChestStorageMarker(PlayablePersistenceStage.readMarker(path, label, "minecraft:chest"), item, count);
    }
}
