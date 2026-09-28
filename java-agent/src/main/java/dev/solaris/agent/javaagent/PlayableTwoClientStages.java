package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;
import dev.solaris.agent.javaagent.PlayableCampfireStage.CampfireDeathRespawnResult;
import dev.solaris.agent.javaagent.PlayableStorageStage.ChestStorageMarker;
import dev.solaris.agent.javaagent.PlayableStorageStage.ChestStorageResult;
import dev.solaris.agent.javaagent.PlayableResourceStage.CraftingTableOpenResult;
import dev.solaris.agent.javaagent.PlayableResourceStage.LogToPlanksResult;
import dev.solaris.agent.javaagent.PlayableHeatStage.TorchPlacementResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static dev.solaris.agent.javaagent.PlayableCoordinates.coordinates;
import static dev.solaris.agent.javaagent.PlayableCoordinates.horizontalDistance;

import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.*;
import static dev.solaris.agent.javaagent.PlayableResourceStage.SUPPORTED_LOG_BLOCK_IDS;

/** Runner-coordinated primary/secondary phases sharing only explicit marker files. */
final class PlayableTwoClientStages {

    static boolean supports(String id) {
        return TWO_CLIENT_SHARED_LOG_DROP_PICKUP_ID.equals(id)
            || TWO_CLIENT_SHARED_LOG_DROP_BREAK_ID.equals(id)
            || TWO_CLIENT_SHARED_LOG_DROP_OBSERVE_ID.equals(id)
            || TWO_CLIENT_SHARED_LOG_PICKUP_COLLECT_ID.equals(id)
            || TWO_CLIENT_SHARED_LOG_PICKUP_GONE_OBSERVE_ID.equals(id)
            || TWO_CLIENT_EARNED_SHARED_CHEST_ID.equals(id)
            || TWO_CLIENT_EARNED_SHARED_CHEST_DEPOSIT_ID.equals(id)
            || TWO_CLIENT_EARNED_SHARED_CHEST_WITHDRAW_ID.equals(id)
            || TWO_CLIENT_EARNED_SHARED_CHEST_OBSERVE_EMPTY_ID.equals(id)
            || TWO_CLIENT_EARNED_TORCH_BLOCK_EDIT_ID.equals(id)
            || TWO_CLIENT_EARNED_TORCH_PLACE_ID.equals(id)
            || TWO_CLIENT_EARNED_TORCH_OBSERVE_ID.equals(id)
            || TWO_CLIENT_EARNED_TORCH_BREAK_ID.equals(id)
            || TWO_CLIENT_EARNED_TORCH_GONE_OBSERVE_ID.equals(id)
            || TWO_CLIENT_PLAYER_VISIBILITY_MOVEMENT_ID.equals(id)
            || TWO_CLIENT_PLAYER_OBSERVE_ID.equals(id)
            || TWO_CLIENT_PLAYER_MOVED_OBSERVE_ID.equals(id)
            || TWO_CLIENT_CHAT_MESSAGE_ID.equals(id)
            || TWO_CLIENT_CHAT_SEND_ID.equals(id)
            || TWO_CLIENT_CHAT_OBSERVE_ID.equals(id)
            || TWO_CLIENT_PLAYER_DISCONNECT_REMOVAL_ID.equals(id)
            || TWO_CLIENT_PLAYER_DISCONNECT_VISIBLE_ID.equals(id)
            || TWO_CLIENT_PLAYER_GONE_OBSERVE_ID.equals(id)
            || TWO_CLIENT_PLAYER_RECONNECT_CLEANUP_ID.equals(id)
            || TWO_CLIENT_PLAYER_RECONNECT_VISIBLE_ID.equals(id)
            || TWO_CLIENT_PLAYER_RECONNECT_GONE_OBSERVE_ID.equals(id)
            || TWO_CLIENT_PLAYER_RECONNECTED_OBSERVE_ID.equals(id)
            || TWO_CLIENT_PLAYER_DEATH_RESPAWN_VISIBILITY_ID.equals(id)
            || TWO_CLIENT_PLAYER_DEATH_BASELINE_ID.equals(id)
            || TWO_CLIENT_CAMPFIRE_DEATH_RESPAWN_ID.equals(id)
            || TWO_CLIENT_PLAYER_POST_RESPAWN_MOVED_OBSERVE_ID.equals(id)
            || TWO_CLIENT_INVENTORY_DROP_HANDOFF_ID.equals(id)
            || TWO_CLIENT_INVENTORY_DROP_PRIMARY_ID.equals(id)
            || TWO_CLIENT_INVENTORY_DROP_OBSERVE_ID.equals(id)
            || TWO_CLIENT_INVENTORY_DROP_SECONDARY_PICKUP_ID.equals(id)
            || TWO_CLIENT_INVENTORY_DROP_GONE_OBSERVE_ID.equals(id);
    }

    ClientScenarioReport run(String id, List<String> observations, Path screenshotsDir, ScenarioClient client)
        throws Exception {
            if (TWO_CLIENT_SHARED_LOG_DROP_PICKUP_ID.equals(id)) {
                return new ClientScenarioReport(
                    "blocked",
                    id,
                    List.of("blocked: playable-30 requires runner-managed primary and secondary real-client phases")
                );
            }
            if (TWO_CLIENT_SHARED_LOG_DROP_BREAK_ID.equals(id)) {
                return runTwoClientSharedLogDropBreak(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_SHARED_LOG_DROP_OBSERVE_ID.equals(id)) {
                return runTwoClientSharedLogDropObserve(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_SHARED_LOG_PICKUP_COLLECT_ID.equals(id)) {
                return runTwoClientSharedLogPickupCollect(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_SHARED_LOG_PICKUP_GONE_OBSERVE_ID.equals(id)) {
                return runTwoClientSharedLogPickupGoneObserve(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_EARNED_SHARED_CHEST_ID.equals(id)) {
                return new ClientScenarioReport(
                    "blocked",
                    id,
                    List.of("blocked: playable-31 requires runner-managed primary and secondary real-client phases")
                );
            }
            if (TWO_CLIENT_EARNED_SHARED_CHEST_DEPOSIT_ID.equals(id)) {
                return runTwoClientEarnedSharedChestDeposit(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_EARNED_SHARED_CHEST_WITHDRAW_ID.equals(id)) {
                return runTwoClientEarnedSharedChestWithdraw(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_EARNED_SHARED_CHEST_OBSERVE_EMPTY_ID.equals(id)) {
                return runTwoClientEarnedSharedChestObserveEmpty(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_EARNED_TORCH_BLOCK_EDIT_ID.equals(id)) {
                return new ClientScenarioReport(
                    "blocked",
                    id,
                    List.of("blocked: playable-32 requires runner-managed primary and secondary real-client phases")
                );
            }
            if (TWO_CLIENT_EARNED_TORCH_PLACE_ID.equals(id)) {
                return runTwoClientEarnedTorchPlace(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_EARNED_TORCH_OBSERVE_ID.equals(id)) {
                return runTwoClientEarnedTorchObserve(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_EARNED_TORCH_BREAK_ID.equals(id)) {
                return runTwoClientEarnedTorchBreak(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_EARNED_TORCH_GONE_OBSERVE_ID.equals(id)) {
                return runTwoClientEarnedTorchGoneObserve(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_PLAYER_VISIBILITY_MOVEMENT_ID.equals(id)) {
                return new ClientScenarioReport(
                    "blocked",
                    id,
                    List.of("blocked: playable-33 requires runner-managed primary and secondary real-client phases")
                );
            }
            if (TWO_CLIENT_PLAYER_OBSERVE_ID.equals(id)) {
                return runTwoClientPlayerObserve(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_PLAYER_MOVED_OBSERVE_ID.equals(id)) {
                return runTwoClientPlayerMovedObserve(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_CHAT_MESSAGE_ID.equals(id)) {
                return new ClientScenarioReport(
                    "blocked",
                    id,
                    List.of("blocked: playable-34 requires runner-managed primary and secondary real-client phases")
                );
            }
            if (TWO_CLIENT_CHAT_SEND_ID.equals(id)) {
                return runTwoClientChatSend(id, observations, client);
            }
            if (TWO_CLIENT_CHAT_OBSERVE_ID.equals(id)) {
                return runTwoClientChatObserve(id, observations, client);
            }
            if (TWO_CLIENT_PLAYER_DISCONNECT_REMOVAL_ID.equals(id)) {
                return new ClientScenarioReport(
                    "blocked",
                    id,
                    List.of("blocked: playable-35 requires runner-managed primary and secondary real-client phases")
                );
            }
            if (TWO_CLIENT_PLAYER_DISCONNECT_VISIBLE_ID.equals(id)) {
                return runTwoClientPlayerDisconnectVisible(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_PLAYER_GONE_OBSERVE_ID.equals(id)) {
                return runTwoClientPlayerGoneObserve(id, observations, client);
            }
            if (TWO_CLIENT_PLAYER_RECONNECT_CLEANUP_ID.equals(id)) {
                return new ClientScenarioReport(
                    "blocked",
                    id,
                    List.of("blocked: playable-36 requires runner-managed primary reconnect and secondary real-client phases")
                );
            }
            if (TWO_CLIENT_PLAYER_RECONNECT_VISIBLE_ID.equals(id)) {
                return runTwoClientPlayerReconnectVisible(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_PLAYER_RECONNECT_GONE_OBSERVE_ID.equals(id)) {
                return runTwoClientPlayerReconnectGoneObserve(id, observations, client);
            }
            if (TWO_CLIENT_PLAYER_RECONNECTED_OBSERVE_ID.equals(id)) {
                return runTwoClientPlayerReconnectedObserve(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_PLAYER_DEATH_RESPAWN_VISIBILITY_ID.equals(id)) {
                return new ClientScenarioReport(
                    "blocked",
                    id,
                    List.of("blocked: playable-37 requires runner-managed primary death/respawn and secondary real-client phases")
                );
            }
            if (TWO_CLIENT_PLAYER_DEATH_BASELINE_ID.equals(id)) {
                return runTwoClientPlayerDeathBaseline(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_CAMPFIRE_DEATH_RESPAWN_ID.equals(id)) {
                return runTwoClientCampfireDeathRespawn(id, observations, client);
            }
            if (TWO_CLIENT_PLAYER_POST_RESPAWN_MOVED_OBSERVE_ID.equals(id)) {
                return runTwoClientPlayerPostRespawnMovedObserve(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_INVENTORY_DROP_HANDOFF_ID.equals(id)) {
                return new ClientScenarioReport(
                    "blocked",
                    id,
                    List.of("blocked: playable-38 requires runner-managed primary and secondary inventory-drop phases")
                );
            }
            if (TWO_CLIENT_INVENTORY_DROP_PRIMARY_ID.equals(id)) {
                return runTwoClientInventoryDropPrimary(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_INVENTORY_DROP_OBSERVE_ID.equals(id)) {
                return runTwoClientInventoryDropObserve(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_INVENTORY_DROP_SECONDARY_PICKUP_ID.equals(id)) {
                return runTwoClientInventoryDropSecondaryPickup(id, observations, screenshotsDir, client);
            }
            if (TWO_CLIENT_INVENTORY_DROP_GONE_OBSERVE_ID.equals(id)) {
                return runTwoClientInventoryDropGoneObserve(id, observations, screenshotsDir, client);
            }
        throw new IllegalArgumentException("unsupported two-client scenario id: " + id);
    }

    private static final String SHARED_LOG_DROP_MARKER_FILE = "playable-30-shared-log-drop-marker.properties";
    private static final String SHARED_CHEST_MARKER_FILE = "playable-31-shared-chest-marker.properties";
    private static final String SHARED_BLOCK_EDIT_MARKER_FILE =
        "playable-32-shared-block-edit-marker.properties";
    private static final String PLAYER_VISIBILITY_MARKER_FILE =
        "playable-33-player-visibility-marker.properties";
    private static final String INVENTORY_DROP_MARKER_FILE =
        "playable-38-inventory-drop-marker.properties";
    private static final String PRIMARY_CLIENT_USERNAME = "SolarisPrimary";
    private static final String TWO_CLIENT_CHAT_MESSAGE_TEXT = "p34 hello from primary";
    private static final double PLAYER_MOVEMENT_MIN_HORIZONTAL_DELTA = 0.05;

    private record SharedLogDropMarker(ScenarioBlockTarget target, String itemId) {}

    private record SharedBlockEditMarker(
        ScenarioBlockTarget target,
        ScenarioBlockTarget approachTarget,
        String blockId,
        String itemId
    ) {}

    private record PlayerVisibilityMarker(ScenarioPlayerObservation observation) {}

    private ClientScenarioReport runTwoClientSharedLogDropBreak(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        ScenarioBlockTarget log = findReachableNaturalLog(id, observations, client, "two-client shared log");
        if (log == null) {
            return new ClientScenarioReport("blocked", id, observations);
        }

        boolean closeApproached = "down".equals(log.face()) || client.approachBlock(log, APPROACH_TIMEOUT);
        observations.add(
            "two-client shared log close approach: " + (closeApproached ? "passed" : "failed")
                + " target=" + coordinates(log)
        );
        if (!closeApproached) {
            return new ClientScenarioReport("blocked", id, observations);
        }

        ScenarioBreakResult broke = client.breakBlockUntilDropVisible(log, log.blockId(), BREAK_TIMEOUT);
        boolean dropped = broke.started() && broke.becameAir() && broke.sawDrop();
        observations.add(
            "two-client shared log drop break: " + (dropped ? "passed" : "failed")
                + " target=" + coordinates(log)
                + " item=" + log.blockId()
                + " break_started=" + broke.started()
                + " became_air=" + broke.becameAir()
                + " saw_drop=" + broke.sawDrop()
        );
        if (!dropped) {
            return new ClientScenarioReport("failed", id, observations);
        }

        writeSharedLogDropMarker(sharedLogDropMarkerPath(screenshotsDir), log, log.blockId());
        observations.add("shared log drop marker: passed target=" + coordinates(log) + " item=" + log.blockId());
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport("passed", id, observations);
    }

    private ScenarioBlockTarget findReachableNaturalLog(
        String id,
        List<String> observations,
        ScenarioClient client,
        String label
    ) throws Exception {
        ScenarioBlockTarget log = client.findBreakableBlock(
            SUPPORTED_LOG_BLOCK_IDS,
            ScenarioReach.WITHIN_SURVIVAL_REACH
        );
        if (log != null) {
            return log;
        }

        ScenarioBlockTarget farLog = client.findBreakableBlock(
            SUPPORTED_LOG_BLOCK_IDS,
            ScenarioReach.OUTSIDE_SURVIVAL_REACH
        );
        if (farLog == null) {
            observations.add("blocked: no loaded supported natural log found for " + id);
            return null;
        }
        boolean approached = client.approachBlock(farLog, APPROACH_TIMEOUT);
        observations.add(
            label + " approach: " + (approached ? "passed" : "failed")
                + " target=" + coordinates(farLog)
        );
        log = client.findBreakableBlock(SUPPORTED_LOG_BLOCK_IDS, ScenarioReach.WITHIN_SURVIVAL_REACH);
        if (!approached && log == null) {
            return null;
        }
        if (log == null) {
            observations.add("blocked: supported natural log remained outside survival reach after approach");
            return null;
        }
        return log;
    }

    private ClientScenarioReport runTwoClientSharedLogDropObserve(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = sharedLogDropMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing shared log drop marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }

        SharedLogDropMarker marker = readSharedLogDropMarker(markerPath);
        boolean visible = client.waitForVisibleItemDrop(marker.itemId(), marker.target(), PICKUP_TIMEOUT);
        observations.add(
            "two-client shared log drop visibility: " + (visible ? "passed" : "failed")
                + " target=" + coordinates(marker.target())
                + " item=" + marker.itemId()
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport(visible ? "passed" : "failed", id, observations);
    }

    private ClientScenarioReport runTwoClientSharedLogPickupCollect(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = sharedLogDropMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing shared log drop marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }

        SharedLogDropMarker marker = readSharedLogDropMarker(markerPath);
        ScenarioBreakResult pickup = client.collectVisibleItemDrop(
            marker.target(),
            marker.itemId(),
            1,
            PICKUP_TIMEOUT
        );
        boolean collected = pickup.becameAir() && pickup.pickupRestored();
        observations.add(
            "two-client shared log pickup: " + (collected ? "passed" : "failed")
                + " target=" + coordinates(marker.target())
                + " item=" + marker.itemId()
                + " saw_drop=" + pickup.sawDrop()
                + " drop_gone=" + pickup.becameAir()
                + " pickup_restored=" + pickup.pickupRestored()
                + " held=" + pickup.selectedItem().itemId() + " x" + pickup.selectedItem().count()
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport(collected ? "passed" : "failed", id, observations);
    }

    private ClientScenarioReport runTwoClientSharedLogPickupGoneObserve(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = sharedLogDropMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing shared log drop marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }

        SharedLogDropMarker marker = readSharedLogDropMarker(markerPath);
        boolean removed = client.waitForNoVisibleItemDrop(marker.itemId(), marker.target(), PICKUP_TIMEOUT);
        observations.add(
            "two-client shared log pickup removal: " + (removed ? "passed" : "failed")
                + " target=" + coordinates(marker.target())
                + " item=" + marker.itemId()
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport(removed ? "passed" : "failed", id, observations);
    }

    private ClientScenarioReport runTwoClientEarnedSharedChestDeposit(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        LogToPlanksResult planks = PlayableResourceStage.runLogToPlanks(id, observations, client, 4, true, false);
        if (!"passed".equals(planks.report().result())) {
            return planks.report();
        }
        CraftingTableOpenResult table = PlayableResourceStage.craftPlaceAndOpenTable(
            id,
            observations,
            client,
            planks.planks().planksItemId(),
            false
        );
        if (!"passed".equals(table.report().result())) {
            return table.report();
        }

        int planksCount = client.inventoryCount(planks.planks().planksItemId());
        int chestCount = client.inventoryCount("minecraft:chest");
        if (planksCount < 8) {
            observations.add("two-client shared chest recipe: failed fewer than eight earned planks available");
            return new ClientScenarioReport("failed", id, observations);
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
            "two-client shared chest recipe: " + (planksConsumed && chestCreated ? "passed" : "failed")
                + " container_id=" + containerId
                + " recipe_result=" + CHEST_RECIPE_RESULT
                + " planks_item=" + planks.planks().planksItemId()
                + " planks_expected_count=" + expectedPlanksAfterChest
                + " chest_expected_count=" + expectedChestCount
        );
        if (!planksConsumed || !chestCreated) {
            return new ClientScenarioReport("failed", id, observations);
        }
        boolean craftingClosed = client.closeCurrentScreen(INVENTORY_TIMEOUT);
        observations.add(
            "two-client shared chest crafting screen close: "
                + (craftingClosed ? "passed" : "failed")
        );
        if (!craftingClosed) {
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioHeldItem chest = client.selectHotbarItem("minecraft:chest", 1, HOTBAR_TIMEOUT);
        if (!chest.matches("minecraft:chest", 1)) {
            observations.add("blocked: earned shared chest exists but is not selectable from hotbar");
            return new ClientScenarioReport("blocked", id, observations);
        }
        ScenarioBlockPair pair = client.findDryPlaceablePair(ScenarioReach.WITHIN_SURVIVAL_REACH);
        if (pair == null) {
            observations.add("blocked: no loaded dry target found for shared chest placement");
            return new ClientScenarioReport("blocked", id, observations);
        }
        ScenarioUseResult placeUse = client.useItemOn(pair.clicked(), chest);
        boolean placed = client.waitForBlock(pair.target(), "minecraft:chest", BLOCK_TIMEOUT);
        ScenarioBlockTarget chestTarget = new ScenarioBlockTarget(
            pair.target().x(),
            pair.target().y(),
            pair.target().z(),
            "up",
            "playable-two-client-chest-marker",
            "minecraft:chest"
        );

        ScenarioHeldItem storedItem = client.selectHotbarItem(
            planks.planks().planksItemId(),
            1,
            HOTBAR_TIMEOUT
        );
        if (!storedItem.matches(planks.planks().planksItemId(), 1)) {
            observations.add("blocked: earned shared chest storage plank is not selectable from hotbar");
            return new ClientScenarioReport("blocked", id, observations);
        }

        ScenarioUseResult openUse = client.useItemOn(chestTarget, storedItem);
        boolean opened = client.waitForScreenClassName(CONTAINER_SCREEN, INVENTORY_TIMEOUT);
        observations.add(
            "two-client shared chest placement/open: " + (placed && opened ? "passed" : "failed")
                + " place_use_result=" + placeUse.result()
                + " placed=" + placed
                + " open_use_result=" + openUse.result()
                + " screen_matched=" + opened
                + " target=" + coordinates(chestTarget)
        );
        if (!placed || !opened) {
            return new ClientScenarioReport("failed", id, observations);
        }

        boolean deposited = client.moveSelectedItemToContainerSlot(
            0,
            planks.planks().planksItemId(),
            1,
            INVENTORY_TIMEOUT
        );
        boolean slotMatched = deposited
            && client.waitForContainerSlot(0, planks.planks().planksItemId(), 1, INVENTORY_TIMEOUT);
        boolean closed = client.closeCurrentScreen(INVENTORY_TIMEOUT);
        boolean passed = deposited && slotMatched && closed;
        observations.add(
            "two-client shared chest deposit: " + (passed ? "passed" : "failed")
                + " slot=0 item=" + planks.planks().planksItemId()
                + " moved=" + deposited
                + " slot_matched=" + slotMatched
                + " closed=" + closed
        );
        if (!passed) {
            return new ClientScenarioReport("failed", id, observations);
        }

        ChestStorageResult stored = new ChestStorageResult(
            new ClientScenarioReport("passed", id, observations),
            chestTarget,
            planks.planks().planksItemId(),
            1
        );
        PlayableStorageStage.writeChestStorageMarker(sharedChestMarkerPath(screenshotsDir), stored);
        observations.add(
            "two-client shared chest marker: passed target=" + coordinates(stored.chestTarget())
                + " item=" + stored.itemId()
                + " count=" + stored.count()
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport("passed", id, observations);
    }

    private ClientScenarioReport runTwoClientEarnedSharedChestWithdraw(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = sharedChestMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing shared chest marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }

        ChestStorageMarker marker = PlayableStorageStage.readSharedChestMarker(markerPath);
        boolean approached = client.approachBlock(marker.chestTarget(), APPROACH_TIMEOUT);
        observations.add(
            "two-client shared chest approach: " + (approached ? "passed" : "failed")
                + " target=" + coordinates(marker.chestTarget())
        );
        boolean visible = approached
            && client.waitForBlock(marker.chestTarget(), "minecraft:chest", BLOCK_TIMEOUT);
        ScenarioUseResult openUse = new ScenarioUseResult("skipped");
        boolean opened = false;
        boolean slotMatched = false;
        boolean moved = false;
        boolean empty = false;
        boolean closed = false;
        if (visible) {
            openUse = client.useItemOn(marker.chestTarget(), client.selectedItem());
            opened = client.waitForScreenClassName(CONTAINER_SCREEN, INVENTORY_TIMEOUT);
            slotMatched = opened
                && client.waitForContainerSlot(0, marker.itemId(), marker.count(), INVENTORY_TIMEOUT);
            moved = slotMatched
                && client.moveContainerSlotToInventory(0, marker.itemId(), marker.count(), INVENTORY_TIMEOUT);
            empty = moved && client.waitForContainerSlotEmpty(0, INVENTORY_TIMEOUT);
            closed = opened && client.closeCurrentScreen(INVENTORY_TIMEOUT);
        }
        boolean passed = visible && opened && slotMatched && moved && empty && closed;
        observations.add(
            "two-client shared chest withdraw: " + (passed ? "passed" : "failed")
                + " target=" + coordinates(marker.chestTarget())
                + " item=" + marker.itemId()
                + " count=" + marker.count()
                + " approached=" + approached
                + " visible=" + visible
                + " open_use_result=" + openUse.result()
                + " screen_matched=" + opened
                + " slot_matched=" + slotMatched
                + " moved=" + moved
                + " empty=" + empty
                + " closed=" + closed
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport(passed ? "passed" : "failed", id, observations);
    }

    private ClientScenarioReport runTwoClientEarnedSharedChestObserveEmpty(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = sharedChestMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing shared chest marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }

        ChestStorageMarker marker = PlayableStorageStage.readSharedChestMarker(markerPath);
        boolean approached = client.approachBlock(marker.chestTarget(), APPROACH_TIMEOUT);
        observations.add(
            "two-client shared chest empty approach: " + (approached ? "passed" : "failed")
                + " target=" + coordinates(marker.chestTarget())
        );
        boolean visible = approached
            && client.waitForBlock(marker.chestTarget(), "minecraft:chest", BLOCK_TIMEOUT);
        ScenarioUseResult openUse = new ScenarioUseResult("skipped");
        boolean opened = false;
        boolean empty = false;
        boolean closed = false;
        if (visible) {
            openUse = client.useItemOn(marker.chestTarget(), client.selectedItem());
            opened = client.waitForScreenClassName(CONTAINER_SCREEN, INVENTORY_TIMEOUT);
            empty = opened && client.waitForContainerSlotEmpty(0, INVENTORY_TIMEOUT);
            closed = opened && client.closeCurrentScreen(INVENTORY_TIMEOUT);
        }
        boolean passed = visible && opened && empty && closed;
        observations.add(
            "two-client shared chest empty observe: " + (passed ? "passed" : "failed")
                + " target=" + coordinates(marker.chestTarget())
                + " item=" + marker.itemId()
                + " approached=" + approached
                + " visible=" + visible
                + " open_use_result=" + openUse.result()
                + " screen_matched=" + opened
                + " empty=" + empty
                + " closed=" + closed
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport(passed ? "passed" : "failed", id, observations);
    }

    private ClientScenarioReport runTwoClientEarnedTorchPlace(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        TorchPlacementResult placed = PlayableHeatStage.prepareTorchCraftPlace(id, observations, client);
        if (!"passed".equals(placed.report().result())) {
            return placed.report();
        }
        SharedBlockEditMarker marker = new SharedBlockEditMarker(
            placed.torchTarget(),
            new ScenarioBlockTarget(
                placed.approachTarget().x(),
                placed.approachTarget().y(),
                placed.approachTarget().z(),
                placed.approachTarget().face(),
                "playable-two-client-block-approach",
                placed.approachTarget().blockId()
            ),
            "minecraft:torch",
            "minecraft:torch"
        );
        writeSharedBlockEditMarker(sharedBlockEditMarkerPath(screenshotsDir), marker);
        observations.add(
            "two-client shared torch placement: passed target=" + coordinates(marker.target())
                + " block=" + marker.blockId()
                + " item=" + marker.itemId()
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport("passed", id, observations);
    }

    private ClientScenarioReport runTwoClientEarnedTorchObserve(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = sharedBlockEditMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing shared block edit marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }

        SharedBlockEditMarker marker = readSharedBlockEditMarker(markerPath);
        boolean approached = client.approachBlock(marker.approachTarget(), APPROACH_TIMEOUT);
        boolean visible = approached && client.waitForBlock(marker.target(), marker.blockId(), BLOCK_TIMEOUT);
        observations.add(
            "two-client shared torch visibility: " + (visible ? "passed" : "failed")
                + " target=" + coordinates(marker.target())
                + " block=" + marker.blockId()
                + " approached=" + approached
                + " visible=" + visible
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport(visible ? "passed" : "failed", id, observations);
    }

    private ClientScenarioReport runTwoClientEarnedTorchBreak(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = sharedBlockEditMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing shared block edit marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }

        SharedBlockEditMarker marker = readSharedBlockEditMarker(markerPath);
        boolean approached = client.approachBlock(marker.approachTarget(), APPROACH_TIMEOUT);
        boolean visible = approached && client.waitForBlock(marker.target(), marker.blockId(), BLOCK_TIMEOUT);
        ScenarioBreakResult broke = new ScenarioBreakResult(
            false,
            false,
            false,
            false,
            new ScenarioHeldItem("minecraft:air", 0)
        );
        boolean becameAir = false;
        ScenarioBreakResult pickup = new ScenarioBreakResult(
            false,
            false,
            false,
            false,
            new ScenarioHeldItem("minecraft:air", 0)
        );
        if (visible) {
            broke = client.breakBlockUntilDropVisible(marker.target(), marker.itemId(), BREAK_TIMEOUT);
            becameAir = broke.becameAir() && client.waitForBlock(marker.target(), "minecraft:air", BLOCK_TIMEOUT);
            if (broke.sawDrop()) {
                pickup = client.collectVisibleItemDrop(marker.target(), marker.itemId(), 1, PICKUP_TIMEOUT);
            }
        }
        boolean collectedByBreaker = pickup.pickupRestored();
        boolean passed = visible && broke.started() && broke.becameAir() && broke.sawDrop() && becameAir;
        observations.add(
            "two-client shared torch break: " + (passed ? "passed" : "failed")
                + " target=" + coordinates(marker.target())
                + " block=" + marker.blockId()
                + " item=" + marker.itemId()
                + " approached=" + approached
                + " visible=" + visible
                + " break_started=" + broke.started()
                + " became_air=" + becameAir
                + " saw_drop=" + broke.sawDrop()
                + " collected_by_breaker=" + collectedByBreaker
                + " held=" + pickup.selectedItem().itemId() + " x" + pickup.selectedItem().count()
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport(passed ? "passed" : "failed", id, observations);
    }

    private ClientScenarioReport runTwoClientEarnedTorchGoneObserve(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = sharedBlockEditMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing shared block edit marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }

        SharedBlockEditMarker marker = readSharedBlockEditMarker(markerPath);
        boolean approached = client.approachBlock(marker.approachTarget(), APPROACH_TIMEOUT);
        boolean removed = approached && client.waitForBlock(marker.target(), "minecraft:air", BLOCK_TIMEOUT);
        observations.add(
            "two-client shared torch removal visibility: " + (removed ? "passed" : "failed")
                + " target=" + coordinates(marker.target())
                + " block=" + marker.blockId()
                + " approached=" + approached
                + " removed=" + removed
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport(removed ? "passed" : "failed", id, observations);
    }

    private ClientScenarioReport runTwoClientPlayerObserve(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        ScenarioPlayerObservation primary = client.waitForVisiblePlayer(
            PRIMARY_CLIENT_USERNAME,
            ENTITY_SCAN_TIMEOUT
        );
        if (primary == null) {
            observations.add("failed: primary player not visible to secondary client: " + PRIMARY_CLIENT_USERNAME);
            return new ClientScenarioReport("failed", id, observations);
        }
        writePlayerVisibilityMarker(playerVisibilityMarkerPath(screenshotsDir), new PlayerVisibilityMarker(primary));
        observations.add(
            "two-client player visibility: passed player=" + primary.playerName()
                + " entity_id=" + primary.entityId()
                + " position=" + coordinates(primary)
                + " distance_squared=" + primary.distanceSquared()
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport("passed", id, observations);
    }

    private ClientScenarioReport runTwoClientPlayerMovedObserve(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = playerVisibilityMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing player visibility marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }

        PlayerVisibilityMarker marker = readPlayerVisibilityMarker(markerPath);
        ScenarioPlayerObservation moved = client.waitForMovedPlayer(
            marker.observation().playerName(),
            marker.observation(),
            PLAYER_MOVEMENT_MIN_HORIZONTAL_DELTA,
            ENTITY_SCAN_TIMEOUT
        );
        boolean passed = moved != null;
        double horizontalDelta = passed ? horizontalDistance(marker.observation(), moved) : 0.0;
        observations.add(
            "two-client player movement visibility: " + (passed ? "passed" : "failed")
                + " player=" + marker.observation().playerName()
                + " before=" + coordinates(marker.observation())
                + " after=" + (moved == null ? "missing" : coordinates(moved))
                + " min_horizontal_delta=" + PLAYER_MOVEMENT_MIN_HORIZONTAL_DELTA
                + " horizontal_delta=" + horizontalDelta
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport(passed ? "passed" : "failed", id, observations);
    }

    private ClientScenarioReport runTwoClientChatSend(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        client.sendChatMessage(TWO_CLIENT_CHAT_MESSAGE_TEXT);
        observations.add("two-client chat send: passed message=" + TWO_CLIENT_CHAT_MESSAGE_TEXT);
        return new ClientScenarioReport("passed", id, observations);
    }

    private ClientScenarioReport runTwoClientChatObserve(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        String expectedText = "<" + PRIMARY_CLIENT_USERNAME + "> " + TWO_CLIENT_CHAT_MESSAGE_TEXT;
        boolean seen = client.waitForChatMessage(expectedText, CHAT_TIMEOUT);
        observations.add(
            "two-client chat observe: " + (seen ? "passed" : "failed")
                + " expected=" + expectedText
        );
        return new ClientScenarioReport(seen ? "passed" : "failed", id, observations);
    }

    private ClientScenarioReport runTwoClientPlayerDisconnectVisible(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        ScenarioPlayerObservation primary = client.waitForVisiblePlayer(
            PRIMARY_CLIENT_USERNAME,
            ENTITY_SCAN_TIMEOUT
        );
        if (primary == null) {
            observations.add("failed: primary player not visible before disconnect: " + PRIMARY_CLIENT_USERNAME);
            return new ClientScenarioReport("failed", id, observations);
        }
        observations.add(
            "two-client player pre-disconnect visibility: passed player=" + primary.playerName()
                + " entity_id=" + primary.entityId()
                + " position=" + coordinates(primary)
                + " distance_squared=" + primary.distanceSquared()
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport("passed", id, observations);
    }

    private ClientScenarioReport runTwoClientPlayerGoneObserve(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        boolean removed = client.waitForNoVisiblePlayer(PRIMARY_CLIENT_USERNAME, ENTITY_SCAN_TIMEOUT);
        observations.add(
            "two-client player disconnect removal: " + (removed ? "passed" : "failed")
                + " player=" + PRIMARY_CLIENT_USERNAME
        );
        return new ClientScenarioReport(removed ? "passed" : "failed", id, observations);
    }

    private ClientScenarioReport runTwoClientPlayerReconnectVisible(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        ScenarioPlayerObservation primary = client.waitForVisiblePlayer(
            PRIMARY_CLIENT_USERNAME,
            ENTITY_SCAN_TIMEOUT
        );
        if (primary == null) {
            observations.add("failed: primary player not visible before reconnect: " + PRIMARY_CLIENT_USERNAME);
            return new ClientScenarioReport("failed", id, observations);
        }
        writePlayerVisibilityMarker(playerVisibilityMarkerPath(screenshotsDir), new PlayerVisibilityMarker(primary));
        observations.add(
            "two-client player pre-reconnect visibility: passed player=" + primary.playerName()
                + " entity_id=" + primary.entityId()
                + " position=" + coordinates(primary)
                + " distance_squared=" + primary.distanceSquared()
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport("passed", id, observations);
    }

    private ClientScenarioReport runTwoClientPlayerReconnectGoneObserve(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        boolean removed = client.waitForNoVisiblePlayer(PRIMARY_CLIENT_USERNAME, ENTITY_SCAN_TIMEOUT);
        observations.add(
            "two-client player reconnect removal: " + (removed ? "passed" : "failed")
                + " player=" + PRIMARY_CLIENT_USERNAME
        );
        return new ClientScenarioReport(removed ? "passed" : "failed", id, observations);
    }

    private ClientScenarioReport runTwoClientPlayerReconnectedObserve(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = playerVisibilityMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing player visibility marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }

        PlayerVisibilityMarker marker = readPlayerVisibilityMarker(markerPath);
        ScenarioPlayerObservation reconnected = client.waitForVisiblePlayer(
            marker.observation().playerName(),
            ENTITY_SCAN_TIMEOUT
        );
        boolean visible = reconnected != null;
        boolean replaced = visible && reconnected.entityId() != marker.observation().entityId();
        observations.add(
            "two-client player reconnect visibility: " + (replaced ? "passed" : "failed")
                + " player=" + marker.observation().playerName()
                + " old_entity_id=" + marker.observation().entityId()
                + " new_entity_id=" + (reconnected == null ? "missing" : reconnected.entityId())
                + " old_position=" + coordinates(marker.observation())
                + " new_position=" + (reconnected == null ? "missing" : coordinates(reconnected))
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport(replaced ? "passed" : "failed", id, observations);
    }

    private ClientScenarioReport runTwoClientPlayerDeathBaseline(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        ScenarioPlayerObservation primary = client.waitForVisiblePlayer(
            PRIMARY_CLIENT_USERNAME,
            ENTITY_SCAN_TIMEOUT
        );
        if (primary == null) {
            observations.add("failed: primary player not visible before death: " + PRIMARY_CLIENT_USERNAME);
            return new ClientScenarioReport("failed", id, observations);
        }
        writePlayerVisibilityMarker(playerVisibilityMarkerPath(screenshotsDir), new PlayerVisibilityMarker(primary));
        observations.add(
            "two-client player pre-death visibility: passed player=" + primary.playerName()
                + " entity_id=" + primary.entityId()
                + " position=" + coordinates(primary)
                + " distance_squared=" + primary.distanceSquared()
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport("passed", id, observations);
    }

    private ClientScenarioReport runTwoClientCampfireDeathRespawn(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        CampfireDeathRespawnResult death = PlayableCampfireStage.performEarnedCampfireDeathRespawn(id, observations, client, false);
        if (!"passed".equals(death.report().result())) {
            return death.report();
        }
        observations.add("two-client campfire death/respawn: passed natural_hazard=campfire respawn=true");
        return new ClientScenarioReport("passed", id, observations);
    }

    private ClientScenarioReport runTwoClientPlayerPostRespawnMovedObserve(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = playerVisibilityMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing player visibility marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }

        PlayerVisibilityMarker marker = readPlayerVisibilityMarker(markerPath);
        ScenarioPlayerObservation moved = client.waitForMovedPlayer(
            marker.observation().playerName(),
            marker.observation(),
            PLAYER_MOVEMENT_MIN_HORIZONTAL_DELTA,
            ENTITY_SCAN_TIMEOUT
        );
        boolean passed = moved != null;
        double horizontalDelta = passed ? horizontalDistance(marker.observation(), moved) : 0.0;
        observations.add(
            "two-client player post-respawn movement visibility: " + (passed ? "passed" : "failed")
                + " player=" + marker.observation().playerName()
                + " before_death=" + coordinates(marker.observation())
                + " after_respawn_move=" + (moved == null ? "missing" : coordinates(moved))
                + " min_horizontal_delta=" + PLAYER_MOVEMENT_MIN_HORIZONTAL_DELTA
                + " horizontal_delta=" + horizontalDelta
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport(passed ? "passed" : "failed", id, observations);
    }

    private ClientScenarioReport runTwoClientInventoryDropPrimary(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        ScenarioBlockTarget log = findReachableNaturalLog(id, observations, client, "inventory drop log");
        if (log == null) {
            return new ClientScenarioReport("blocked", id, observations);
        }

        ScenarioBreakResult broke = client.breakBlockUntilDropVisible(log, log.blockId(), BREAK_TIMEOUT);
        ScenarioBreakResult pickup = client.collectVisibleItemDrop(log, log.blockId(), 1, PICKUP_TIMEOUT);
        boolean collected = broke.started()
            && broke.becameAir()
            && broke.sawDrop()
            && pickup.pickupRestored();
        observations.add(
            "inventory drop source log pickup: " + (collected ? "passed" : "failed")
                + " target=" + coordinates(log)
                + " item=" + log.blockId()
                + " break_started=" + broke.started()
                + " became_air=" + broke.becameAir()
                + " saw_drop=" + broke.sawDrop()
                + " pickup_restored=" + pickup.pickupRestored()
        );
        if (!collected) {
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioHeldItem held = client.selectHotbarItem(log.blockId(), 1, HOTBAR_TIMEOUT);
        if (!held.matches(log.blockId(), 1)) {
            observations.add("blocked: earned inventory drop item is not selectable item=" + log.blockId());
            return new ClientScenarioReport("blocked", id, observations);
        }
        ScenarioBlockTarget dropTarget = client.dropSelectedItem(log.blockId(), 1, PICKUP_TIMEOUT);
        boolean visible = client.waitForVisibleItemDrop(log.blockId(), dropTarget, PICKUP_TIMEOUT);
        observations.add(
            "two-client inventory drop: " + (visible ? "passed" : "failed")
                + " target=" + coordinates(dropTarget)
                + " item=" + log.blockId()
                + " selected=" + held.itemId() + " x" + held.count()
                + " visible=" + visible
        );
        if (!visible) {
            return new ClientScenarioReport("failed", id, observations);
        }
        writeSharedLogDropMarker(inventoryDropMarkerPath(screenshotsDir), dropTarget, log.blockId());
        observations.add("inventory drop marker: passed target=" + coordinates(dropTarget) + " item=" + log.blockId());
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport("passed", id, observations);
    }

    private ClientScenarioReport runTwoClientInventoryDropObserve(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = inventoryDropMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing inventory drop marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }
        SharedLogDropMarker marker = readSharedLogDropMarker(markerPath, "playable-two-client-inventory-drop-marker");
        boolean visible = client.waitForVisibleItemDrop(marker.itemId(), marker.target(), PICKUP_TIMEOUT);
        observations.add(
            "two-client inventory drop visibility: " + (visible ? "passed" : "failed")
                + " target=" + coordinates(marker.target())
                + " item=" + marker.itemId()
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport(visible ? "passed" : "failed", id, observations);
    }

    private ClientScenarioReport runTwoClientInventoryDropSecondaryPickup(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = inventoryDropMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing inventory drop marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }
        SharedLogDropMarker marker = readSharedLogDropMarker(markerPath, "playable-two-client-inventory-drop-marker");
        ScenarioBreakResult pickup = client.collectVisibleItemDrop(
            marker.target(),
            marker.itemId(),
            1,
            PICKUP_TIMEOUT
        );
        boolean collected = pickup.becameAir() && pickup.pickupRestored();
        observations.add(
            "two-client inventory drop secondary pickup: " + (collected ? "passed" : "failed")
                + " target=" + coordinates(marker.target())
                + " item=" + marker.itemId()
                + " saw_drop=" + pickup.sawDrop()
                + " drop_gone=" + pickup.becameAir()
                + " pickup_restored=" + pickup.pickupRestored()
                + " held=" + pickup.selectedItem().itemId() + " x" + pickup.selectedItem().count()
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport(collected ? "passed" : "failed", id, observations);
    }

    private ClientScenarioReport runTwoClientInventoryDropGoneObserve(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = inventoryDropMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing inventory drop marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }
        SharedLogDropMarker marker = readSharedLogDropMarker(markerPath, "playable-two-client-inventory-drop-marker");
        boolean removed = client.waitForNoVisibleItemDrop(marker.itemId(), marker.target(), PICKUP_TIMEOUT);
        observations.add(
            "two-client inventory drop removal: " + (removed ? "passed" : "failed")
                + " target=" + coordinates(marker.target())
                + " item=" + marker.itemId()
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport(removed ? "passed" : "failed", id, observations);
    }


    private static Path sharedLogDropMarkerPath(Path screenshotsDir) {
        Path runDir = screenshotsDir.getParent();
        return (runDir == null ? Path.of(".") : runDir).resolve(SHARED_LOG_DROP_MARKER_FILE);
    }

    private static Path inventoryDropMarkerPath(Path screenshotsDir) {
        Path runDir = screenshotsDir.getParent();
        return (runDir == null ? Path.of(".") : runDir).resolve(INVENTORY_DROP_MARKER_FILE);
    }

    private static Path sharedChestMarkerPath(Path screenshotsDir) {
        Path runDir = screenshotsDir.getParent();
        return (runDir == null ? Path.of(".") : runDir).resolve(SHARED_CHEST_MARKER_FILE);
    }

    private static Path sharedBlockEditMarkerPath(Path screenshotsDir) {
        Path runDir = screenshotsDir.getParent();
        return (runDir == null ? Path.of(".") : runDir).resolve(SHARED_BLOCK_EDIT_MARKER_FILE);
    }

    private static Path playerVisibilityMarkerPath(Path screenshotsDir) {
        Path runDir = screenshotsDir.getParent();
        return (runDir == null ? Path.of(".") : runDir).resolve(PLAYER_VISIBILITY_MARKER_FILE);
    }

    private static void writeSharedLogDropMarker(
        Path path,
        ScenarioBlockTarget target,
        String itemId
    ) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(
            path,
            "x=" + target.x() + "\n"
                + "y=" + target.y() + "\n"
                + "z=" + target.z() + "\n"
                + "face=" + target.face() + "\n"
                + "item=" + itemId + "\n"
        );
    }

    private static void writeSharedBlockEditMarker(Path path, SharedBlockEditMarker marker) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(
            path,
            "x=" + marker.target().x() + "\n"
                + "y=" + marker.target().y() + "\n"
                + "z=" + marker.target().z() + "\n"
                + "face=" + marker.target().face() + "\n"
                + "approach_x=" + marker.approachTarget().x() + "\n"
                + "approach_y=" + marker.approachTarget().y() + "\n"
                + "approach_z=" + marker.approachTarget().z() + "\n"
                + "approach_face=" + marker.approachTarget().face() + "\n"
                + "approach_block=" + marker.approachTarget().blockId() + "\n"
                + "block=" + marker.blockId() + "\n"
                + "item=" + marker.itemId() + "\n"
        );
    }

    private static void writePlayerVisibilityMarker(
        Path path,
        PlayerVisibilityMarker marker
    ) throws IOException {
        Files.createDirectories(path.getParent());
        ScenarioPlayerObservation observation = marker.observation();
        Files.writeString(
            path,
            "player=" + observation.playerName() + "\n"
                + "entity_id=" + observation.entityId() + "\n"
                + "x=" + observation.x() + "\n"
                + "y=" + observation.y() + "\n"
                + "z=" + observation.z() + "\n"
                + "distance_squared=" + observation.distanceSquared() + "\n"
        );
    }

    private static SharedLogDropMarker readSharedLogDropMarker(Path path) throws IOException {
        return readSharedLogDropMarker(path, "playable-two-client-drop-marker");
    }

    private static SharedLogDropMarker readSharedLogDropMarker(Path path, String label) throws IOException {
        Integer x = null;
        Integer y = null;
        Integer z = null;
        String face = null;
        String item = null;
        for (String line : Files.readAllLines(path)) {
            String[] parts = line.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            switch (parts[0]) {
                case "x" -> x = Integer.parseInt(parts[1]);
                case "y" -> y = Integer.parseInt(parts[1]);
                case "z" -> z = Integer.parseInt(parts[1]);
                case "face" -> face = parts[1];
                case "item" -> item = parts[1];
                default -> {
                }
            }
        }
        if (x == null || y == null || z == null || face == null || item == null) {
            throw new IOException("invalid shared log drop marker file: " + path);
        }
        return new SharedLogDropMarker(
            new ScenarioBlockTarget(x, y, z, face, label, item),
            item
        );
    }

    private static SharedBlockEditMarker readSharedBlockEditMarker(Path path) throws IOException {
        Integer x = null;
        Integer y = null;
        Integer z = null;
        String face = null;
        Integer approachX = null;
        Integer approachY = null;
        Integer approachZ = null;
        String approachFace = null;
        String approachBlock = null;
        String block = null;
        String item = null;
        for (String line : Files.readAllLines(path)) {
            String[] parts = line.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            switch (parts[0]) {
                case "x" -> x = Integer.parseInt(parts[1]);
                case "y" -> y = Integer.parseInt(parts[1]);
                case "z" -> z = Integer.parseInt(parts[1]);
                case "face" -> face = parts[1];
                case "approach_x" -> approachX = Integer.parseInt(parts[1]);
                case "approach_y" -> approachY = Integer.parseInt(parts[1]);
                case "approach_z" -> approachZ = Integer.parseInt(parts[1]);
                case "approach_face" -> approachFace = parts[1];
                case "approach_block" -> approachBlock = parts[1];
                case "block" -> block = parts[1];
                case "item" -> item = parts[1];
                default -> {
                }
            }
        }
        if (
            x == null
                || y == null
                || z == null
                || face == null
                || approachX == null
                || approachY == null
                || approachZ == null
                || approachFace == null
                || approachBlock == null
                || block == null
                || item == null
                || face.isBlank()
                || approachFace.isBlank()
                || approachBlock.isBlank()
                || block.isBlank()
                || item.isBlank()
        ) {
            throw new IOException("invalid shared block edit marker file: " + path);
        }
        ScenarioBlockTarget target = new ScenarioBlockTarget(
            x,
            y,
            z,
            face,
            "playable-two-client-block-marker",
            block
        );
        ScenarioBlockTarget approachTarget = new ScenarioBlockTarget(
            approachX,
            approachY,
            approachZ,
            approachFace,
            "playable-two-client-block-approach",
            approachBlock
        );
        return new SharedBlockEditMarker(target, approachTarget, block, item);
    }

    private static PlayerVisibilityMarker readPlayerVisibilityMarker(Path path) throws IOException {
        String player = null;
        Integer entityId = null;
        Double x = null;
        Double y = null;
        Double z = null;
        Double distanceSquared = null;
        for (String line : Files.readAllLines(path)) {
            String[] parts = line.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            switch (parts[0]) {
                case "player" -> player = parts[1];
                case "entity_id" -> entityId = Integer.parseInt(parts[1]);
                case "x" -> x = Double.parseDouble(parts[1]);
                case "y" -> y = Double.parseDouble(parts[1]);
                case "z" -> z = Double.parseDouble(parts[1]);
                case "distance_squared" -> distanceSquared = Double.parseDouble(parts[1]);
                default -> {
                }
            }
        }
        if (
            player == null
                || player.isBlank()
                || entityId == null
                || x == null
                || y == null
                || z == null
                || distanceSquared == null
        ) {
            throw new IOException("invalid player visibility marker file: " + path);
        }
        return new PlayerVisibilityMarker(new ScenarioPlayerObservation(player, entityId, x, y, z, distanceSquared));
    }
}
