package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;

import java.time.Duration;
import java.util.List;

import static dev.solaris.agent.javaagent.PlayableCoordinates.coordinates;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.APPROACH_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.BLOCK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.HOTBAR_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.INVENTORY_TIMEOUT;

/** Workstation conservation: stonecutter placement, offers, and slab output. */
final class PlayableStationStage {
    private PlayableStationStage() {
    }

    private static final String STONECUTTER_SCREEN =
        "net.minecraft.client.gui.screens.inventory.StonecutterScreen";
    private static final String STONECUTTER_INPUT_ITEM_ID = "minecraft:cobblestone";
    private static final String STONECUTTER_OUTPUT_ITEM_ID = "minecraft:cobblestone_slab";
    private static final int STONECUTTER_COBBLESTONE_OFFER_COUNT = 3;
    private static final int STONECUTTER_INPUT_SLOT = 0;
    private static final int STONECUTTER_OUTPUT_SLOT = 1;

    static ClientScenarioReport runStonecutterConservation(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        ScenarioHeldItem station = client.giveAndSelect(
            "minecraft:stonecutter",
            1,
            0,
            HOTBAR_TIMEOUT
        );
        ScenarioBlockPair pair = client.findUnobstructedPlaceablePair(ScenarioReach.WITHIN_SURVIVAL_REACH);
        if (!station.matches("minecraft:stonecutter", 1) || pair == null) {
            observations.add("stonecutter setup: failed station=" + station.itemId() + " x" + station.count());
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioUseResult placeUse = client.useItemOn(pair.clicked(), station);
        boolean placed = client.waitForBlock(pair.target(), "minecraft:stonecutter", BLOCK_TIMEOUT);
        ScenarioBlockTarget stonecutter = new ScenarioBlockTarget(
            pair.target().x(),
            pair.target().y(),
            pair.target().z(),
            "up",
            "stonecutter-target",
            "minecraft:stonecutter"
        );
        ScenarioHeldItem firstInput = client.giveAndSelect(
            STONECUTTER_INPUT_ITEM_ID,
            1,
            1,
            HOTBAR_TIMEOUT
        );
        observations.add(
            "stonecutter setup: " + (placed && firstInput.matches(STONECUTTER_INPUT_ITEM_ID, 1) ? "passed" : "failed")
                + " place_use_result=" + placeUse.result()
                + " input=" + firstInput.itemId() + " x" + firstInput.count()
        );
        if (!placed || !firstInput.matches(STONECUTTER_INPUT_ITEM_ID, 1)) {
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioUseResult openUse = client.useItemOn(stonecutter, firstInput);
        boolean opened = client.waitForScreenClassName(STONECUTTER_SCREEN, INVENTORY_TIMEOUT);
        int firstContainerId = opened ? client.activeContainerId() : -1;
        boolean openedFromClientState = opened && firstContainerId > 0;
        observations.add(
            "stonecutter menu open: " + (openedFromClientState ? "passed" : "failed")
                + " use_result=" + openUse.result()
                + " container_id=" + firstContainerId
        );
        if (!openedFromClientState) {
            return new ClientScenarioReport("failed", id, observations);
        }

        boolean initialInputMoved = client.moveSelectedItemToContainerSlot(
            STONECUTTER_INPUT_SLOT,
            STONECUTTER_INPUT_ITEM_ID,
            1,
            INVENTORY_TIMEOUT
        );
        boolean initialInputVisible = initialInputMoved && client.waitForContainerSlot(
            STONECUTTER_INPUT_SLOT,
            STONECUTTER_INPUT_ITEM_ID,
            1,
            INVENTORY_TIMEOUT
        );
        int initialOfferId = initialInputVisible ? selectStonecutterSlabOffer(client) : -1;
        boolean initialOutputVisible = initialOfferId >= 0;
        boolean normalPickup = initialOutputVisible && client.moveContainerSlotToInventory(
            STONECUTTER_OUTPUT_SLOT,
            STONECUTTER_OUTPUT_ITEM_ID,
            2,
            INVENTORY_TIMEOUT
        );
        boolean normalConserved = normalPickup
            && client.waitForContainerSlotEmpty(STONECUTTER_INPUT_SLOT, INVENTORY_TIMEOUT)
            && client.waitForContainerSlotEmpty(STONECUTTER_OUTPUT_SLOT, INVENTORY_TIMEOUT)
            && client.waitForInventoryCount(STONECUTTER_INPUT_ITEM_ID, 0, INVENTORY_TIMEOUT)
            && client.waitForInventoryCount(STONECUTTER_OUTPUT_ITEM_ID, 2, INVENTORY_TIMEOUT);
        observations.add(
            "stonecutter normal pickup: " + (normalConserved ? "passed" : "failed")
                + " offer_id=" + initialOfferId
                + " input_consumed=1 output_added=2"
        );
        if (!normalConserved) {
            return new ClientScenarioReport("failed", id, observations);
        }

        boolean closedAfterNormalPickup = client.closeCurrentScreen(INVENTORY_TIMEOUT);
        ScenarioUseResult reopenUse = closedAfterNormalPickup
            ? client.useItemOn(stonecutter, client.selectedItem())
            : new ScenarioUseResult("skipped");
        boolean reopened = closedAfterNormalPickup
            && client.waitForScreenClassName(STONECUTTER_SCREEN, INVENTORY_TIMEOUT);
        int reopenedContainerId = reopened ? client.activeContainerId() : -1;
        boolean reopenedConserved = reopened
            && reopenedContainerId > 0
            && reopenedContainerId != firstContainerId
            && client.waitForContainerSlotEmpty(STONECUTTER_INPUT_SLOT, INVENTORY_TIMEOUT)
            && client.waitForContainerSlotEmpty(STONECUTTER_OUTPUT_SLOT, INVENTORY_TIMEOUT)
            && client.waitForInventoryCount(STONECUTTER_INPUT_ITEM_ID, 0, INVENTORY_TIMEOUT)
            && client.waitForInventoryCount(STONECUTTER_OUTPUT_ITEM_ID, 2, INVENTORY_TIMEOUT);
        observations.add(
            "stonecutter close/reopen conservation: " + (reopenedConserved ? "passed" : "failed")
                + " reopen_use_result=" + reopenUse.result()
                + " first_container_id=" + firstContainerId
                + " reopened_container_id=" + reopenedContainerId
        );
        if (!reopenedConserved) {
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioHeldItem quickMoveInput = client.giveAndSelect(
            STONECUTTER_INPUT_ITEM_ID,
            2,
            1,
            HOTBAR_TIMEOUT
        );
        boolean quickInputMoved = quickMoveInput.matches(STONECUTTER_INPUT_ITEM_ID, 2)
            && client.moveSelectedItemToContainerSlot(
                STONECUTTER_INPUT_SLOT,
                STONECUTTER_INPUT_ITEM_ID,
                2,
                INVENTORY_TIMEOUT
            );
        int quickOfferId = quickInputMoved ? selectStonecutterSlabOffer(client) : -1;
        boolean quickOutputVisible = quickOfferId >= 0;
        boolean quickMoved = quickOutputVisible
            && client.quickMoveContainerSlot(STONECUTTER_OUTPUT_SLOT, INVENTORY_TIMEOUT);
        boolean exactConservation = quickMoved
            && client.waitForContainerSlotEmpty(STONECUTTER_INPUT_SLOT, INVENTORY_TIMEOUT)
            && client.waitForContainerSlotEmpty(STONECUTTER_OUTPUT_SLOT, INVENTORY_TIMEOUT)
            && client.waitForInventoryCount(STONECUTTER_INPUT_ITEM_ID, 0, INVENTORY_TIMEOUT)
            && client.waitForInventoryCount(STONECUTTER_OUTPUT_ITEM_ID, 6, INVENTORY_TIMEOUT);
        boolean closed = exactConservation && client.closeCurrentScreen(INVENTORY_TIMEOUT);
        boolean passed = exactConservation && closed;
        observations.add(
            "stonecutter conservation: " + (passed ? "passed" : "failed")
                + " offer_id=" + quickOfferId
                + " input_total=3 input_remaining=0 output_expected=6 quick_move=" + quickMoved
                + " closed=" + closed
        );
        return new ClientScenarioReport(passed ? "passed" : "failed", id, observations);
    }

    private static int selectStonecutterSlabOffer(ScenarioClient client) throws Exception {
        for (int offerId = 0; offerId < STONECUTTER_COBBLESTONE_OFFER_COUNT; offerId++) {
            boolean selected = client.clickContainerButton(offerId, INVENTORY_TIMEOUT);
            if (
                selected
                    && client.waitForContainerSlot(
                        STONECUTTER_OUTPUT_SLOT,
                        STONECUTTER_OUTPUT_ITEM_ID,
                        2,
                        Duration.ZERO
                    )
            ) {
                return offerId;
            }
        }
        return -1;
    }
}
