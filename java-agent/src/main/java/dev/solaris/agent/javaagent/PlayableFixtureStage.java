package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;

import java.util.List;
import java.util.Map;

import static dev.solaris.agent.javaagent.PlayableCoordinates.coordinates;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.APPROACH_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.BLOCK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.HOTBAR_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.INVENTORY_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableResourceStage.STICK_RECIPE_RESULT;

/** Earned door/sign fixtures: planks recipes, placement, toggle, and text edit. */
final class PlayableFixtureStage {
    private PlayableFixtureStage() {
    }

    private static final Map<String, DoorRecipe> DOOR_BY_PLANKS = Map.ofEntries(
        Map.entry("minecraft:acacia_planks", new DoorRecipe("minecraft:acacia_door")),
        Map.entry("minecraft:birch_planks", new DoorRecipe("minecraft:birch_door")),
        Map.entry("minecraft:cherry_planks", new DoorRecipe("minecraft:cherry_door")),
        Map.entry("minecraft:dark_oak_planks", new DoorRecipe("minecraft:dark_oak_door")),
        Map.entry("minecraft:jungle_planks", new DoorRecipe("minecraft:jungle_door")),
        Map.entry("minecraft:mangrove_planks", new DoorRecipe("minecraft:mangrove_door")),
        Map.entry("minecraft:oak_planks", new DoorRecipe("minecraft:oak_door")),
        Map.entry("minecraft:pale_oak_planks", new DoorRecipe("minecraft:pale_oak_door")),
        Map.entry("minecraft:spruce_planks", new DoorRecipe("minecraft:spruce_door"))
    );
    private static final Map<String, SignRecipe> SIGN_BY_PLANKS = Map.ofEntries(
        Map.entry("minecraft:acacia_planks", new SignRecipe("minecraft:acacia_sign")),
        Map.entry("minecraft:birch_planks", new SignRecipe("minecraft:birch_sign")),
        Map.entry("minecraft:cherry_planks", new SignRecipe("minecraft:cherry_sign")),
        Map.entry("minecraft:dark_oak_planks", new SignRecipe("minecraft:dark_oak_sign")),
        Map.entry("minecraft:jungle_planks", new SignRecipe("minecraft:jungle_sign")),
        Map.entry("minecraft:mangrove_planks", new SignRecipe("minecraft:mangrove_sign")),
        Map.entry("minecraft:oak_planks", new SignRecipe("minecraft:oak_sign")),
        Map.entry("minecraft:pale_oak_planks", new SignRecipe("minecraft:pale_oak_sign")),
        Map.entry("minecraft:spruce_planks", new SignRecipe("minecraft:spruce_sign"))
    );
    private static final List<String> P17_SIGN_LINES = List.of("Solaris", "P17", "NoDebug", "OK");

    private record DoorRecipe(String doorItemId) {}

    private record SignRecipe(String signItemId) {}

    static ClientScenarioReport runEarnedDoorPlaceToggle(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        PlayableResourceStage.LogToPlanksResult planks = PlayableResourceStage.runLogToPlanks(id, observations, client, 3, true, false);
        if (!"passed".equals(planks.report().result())) {
            return planks.report();
        }
        PlayableResourceStage.CraftingTableOpenResult table = PlayableResourceStage.craftPlaceAndOpenTable(
            id,
            observations,
            client,
            planks.planks().planksItemId(),
            false
        );
        if (!"passed".equals(table.report().result())) {
            return table.report();
        }

        DoorRecipe door = DOOR_BY_PLANKS.get(planks.planks().planksItemId());
        if (door == null) {
            observations.add("blocked: no embedded door recipe mapping for planks=" + planks.planks().planksItemId());
            return new ClientScenarioReport("blocked", id, observations);
        }
        int planksCount = client.inventoryCount(planks.planks().planksItemId());
        int doorCount = client.inventoryCount(door.doorItemId());
        if (planksCount < 6) {
            observations.add("door recipe: failed fewer than six matching planks available");
            return new ClientScenarioReport("failed", id, observations);
        }
        int expectedPlanksAfterDoor = planksCount - 6;
        int expectedDoorCount = doorCount + 3;
        int containerId = client.activeContainerId();
        client.placeRecipe(containerId, client.requireRecipeDisplayId(door.doorItemId()), false);
        boolean planksConsumed = client.waitForInventoryCount(
            planks.planks().planksItemId(),
            expectedPlanksAfterDoor,
            INVENTORY_TIMEOUT
        );
        boolean doorCreated = client.waitForInventoryCount(
            door.doorItemId(),
            expectedDoorCount,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "door recipe: " + (planksConsumed && doorCreated ? "passed" : "failed")
                + " container_id=" + containerId
                + " recipe_result=" + door.doorItemId()
                + " planks_item=" + planks.planks().planksItemId()
                + " planks_expected_count=" + expectedPlanksAfterDoor
                + " planks_count_matched=" + planksConsumed
                + " door_item=" + door.doorItemId()
                + " door_expected_count=" + expectedDoorCount
                + " door_count_matched=" + doorCreated
        );
        if (!planksConsumed || !doorCreated) {
            return new ClientScenarioReport("failed", id, observations);
        }
        boolean craftingClosed = client.closeCurrentScreen(INVENTORY_TIMEOUT);
        observations.add("crafting table screen close after door: " + (craftingClosed ? "passed" : "failed"));
        if (!craftingClosed) {
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioHeldItem heldDoor = client.selectHotbarItem(door.doorItemId(), 1, HOTBAR_TIMEOUT);
        if (!heldDoor.matches(door.doorItemId(), 1)) {
            observations.add("blocked: crafted door exists but is not selectable from hotbar");
            return new ClientScenarioReport("blocked", id, observations);
        }
        ScenarioBlockPair pair = client.findDryPlaceablePair(ScenarioReach.WITHIN_SURVIVAL_REACH);
        if (pair == null) {
            observations.add("blocked: no loaded dry target found for door placement");
            return new ClientScenarioReport("blocked", id, observations);
        }
        ScenarioBlockTarget clicked = new ScenarioBlockTarget(
            pair.clicked().x(),
            pair.clicked().y(),
            pair.clicked().z(),
            pair.clicked().face(),
            "door-clicked",
            pair.clicked().blockId()
        );
        ScenarioBlockTarget doorTarget = new ScenarioBlockTarget(
            pair.target().x(),
            pair.target().y(),
            pair.target().z(),
            "up",
            "door-target",
            door.doorItemId()
        );
        ScenarioUseResult placeUse = client.useItemOn(clicked, heldDoor);
        boolean placed = client.waitForBlock(doorTarget, door.doorItemId(), BLOCK_TIMEOUT);
        observations.add(
            "door placement: " + (placed ? "passed" : "failed")
                + " place_use_result=" + placeUse.result()
                + " target=" + coordinates(doorTarget)
                + " door_item=" + door.doorItemId()
        );
        if (!placed) {
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioUseResult openUse = client.useItemOn(doorTarget, heldDoor);
        boolean openObserved = client.waitForBlock(doorTarget, door.doorItemId(), BLOCK_TIMEOUT);
        observations.add(
            "door toggle open: " + (openObserved ? "passed" : "failed")
                + " open_use_result=" + openUse.result()
                + " door_item=" + door.doorItemId()
        );
        if (!openObserved) {
            return new ClientScenarioReport("failed", id, observations);
        }
        ScenarioUseResult closeUse = client.useItemOn(doorTarget, heldDoor);
        boolean closeObserved = client.waitForBlock(doorTarget, door.doorItemId(), BLOCK_TIMEOUT);
        observations.add(
            "door toggle close: " + (closeObserved ? "passed" : "failed")
                + " close_use_result=" + closeUse.result()
                + " door_item=" + door.doorItemId()
        );
        return new ClientScenarioReport(closeObserved ? "passed" : "failed", id, observations);
    }

    static ClientScenarioReport runEarnedSignPlaceEdit(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        PlayableResourceStage.LogToPlanksResult planks = PlayableResourceStage.runLogToPlanks(id, observations, client, 3, true, false);
        if (!"passed".equals(planks.report().result())) {
            return planks.report();
        }
        PlayableResourceStage.CraftingTableOpenResult table = PlayableResourceStage.craftPlaceAndOpenTable(
            id,
            observations,
            client,
            planks.planks().planksItemId(),
            false
        );
        if (!"passed".equals(table.report().result())) {
            return table.report();
        }

        SignRecipe sign = SIGN_BY_PLANKS.get(planks.planks().planksItemId());
        if (sign == null) {
            observations.add("blocked: no embedded sign recipe mapping for planks=" + planks.planks().planksItemId());
            return new ClientScenarioReport("blocked", id, observations);
        }
        int containerId = client.activeContainerId();
        int planksCountBeforeSticks = client.inventoryCount(planks.planks().planksItemId());
        int stickCountBefore = client.inventoryCount("minecraft:stick");
        if (planksCountBeforeSticks < 8) {
            observations.add("sign recipe: failed fewer than eight matching planks available before stick crafting");
            return new ClientScenarioReport("failed", id, observations);
        }
        int expectedPlanksAfterSticks = planksCountBeforeSticks - 2;
        int expectedStickCountAfterSticks = stickCountBefore + 4;
        client.placeRecipe(containerId, client.requireRecipeDisplayId(STICK_RECIPE_RESULT), false);
        boolean stickPlanksConsumed = client.waitForInventoryCount(
            planks.planks().planksItemId(),
            expectedPlanksAfterSticks,
            INVENTORY_TIMEOUT
        );
        boolean sticksCreated = client.waitForInventoryCount(
            "minecraft:stick",
            expectedStickCountAfterSticks,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "sign stick recipe: " + (stickPlanksConsumed && sticksCreated ? "passed" : "failed")
                + " container_id=" + containerId
                + " recipe_result=" + STICK_RECIPE_RESULT
                + " planks_item=" + planks.planks().planksItemId()
                + " planks_expected_count=" + expectedPlanksAfterSticks
                + " planks_count_matched=" + stickPlanksConsumed
                + " stick_expected_count=" + expectedStickCountAfterSticks
                + " stick_count_matched=" + sticksCreated
        );
        if (!stickPlanksConsumed || !sticksCreated) {
            return new ClientScenarioReport("failed", id, observations);
        }

        int planksCount = client.inventoryCount(planks.planks().planksItemId());
        int stickCount = client.inventoryCount("minecraft:stick");
        int signCount = client.inventoryCount(sign.signItemId());
        if (planksCount < 6 || stickCount < 1) {
            observations.add("sign recipe: failed missing earned planks or stick");
            return new ClientScenarioReport("failed", id, observations);
        }
        int expectedPlanksAfterSign = planksCount - 6;
        int expectedStickCountAfterSign = stickCount - 1;
        int expectedSignCount = signCount + 3;
        client.placeRecipe(containerId, client.requireRecipeDisplayId(sign.signItemId()), false);
        boolean planksConsumed = client.waitForInventoryCount(
            planks.planks().planksItemId(),
            expectedPlanksAfterSign,
            INVENTORY_TIMEOUT
        );
        boolean stickConsumed = client.waitForInventoryCount(
            "minecraft:stick",
            expectedStickCountAfterSign,
            INVENTORY_TIMEOUT
        );
        boolean signCreated = client.waitForInventoryCount(
            sign.signItemId(),
            expectedSignCount,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "sign recipe: " + (planksConsumed && stickConsumed && signCreated ? "passed" : "failed")
                + " container_id=" + containerId
                + " recipe_result=" + sign.signItemId()
                + " planks_item=" + planks.planks().planksItemId()
                + " planks_expected_count=" + expectedPlanksAfterSign
                + " planks_count_matched=" + planksConsumed
                + " stick_expected_count=" + expectedStickCountAfterSign
                + " stick_count_matched=" + stickConsumed
                + " sign_item=" + sign.signItemId()
                + " sign_expected_count=" + expectedSignCount
                + " sign_count_matched=" + signCreated
        );
        if (!planksConsumed || !stickConsumed || !signCreated) {
            return new ClientScenarioReport("failed", id, observations);
        }
        boolean craftingClosed = client.closeCurrentScreen(INVENTORY_TIMEOUT);
        observations.add("crafting table screen close after sign: " + (craftingClosed ? "passed" : "failed"));
        if (!craftingClosed) {
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioHeldItem heldSign = client.selectHotbarItem(sign.signItemId(), 1, HOTBAR_TIMEOUT);
        if (!heldSign.matches(sign.signItemId(), 1)) {
            observations.add("blocked: crafted sign exists but is not selectable from hotbar");
            return new ClientScenarioReport("blocked", id, observations);
        }
        ScenarioBlockPair pair = client.findDryPlaceablePair(ScenarioReach.WITHIN_SURVIVAL_REACH);
        if (pair == null) {
            observations.add("blocked: no loaded dry target found for sign placement");
            return new ClientScenarioReport("blocked", id, observations);
        }
        ScenarioBlockTarget clicked = new ScenarioBlockTarget(
            pair.clicked().x(),
            pair.clicked().y(),
            pair.clicked().z(),
            pair.clicked().face(),
            "sign-clicked",
            pair.clicked().blockId()
        );
        ScenarioBlockTarget signTarget = new ScenarioBlockTarget(
            pair.target().x(),
            pair.target().y(),
            pair.target().z(),
            "up",
            "sign-target",
            sign.signItemId()
        );
        ScenarioUseResult placeUse = client.useItemOn(clicked, heldSign);
        boolean placed = client.waitForBlock(signTarget, sign.signItemId(), BLOCK_TIMEOUT);
        observations.add(
            "sign placement: " + (placed ? "passed" : "failed")
                + " place_use_result=" + placeUse.result()
                + " target=" + coordinates(signTarget)
                + " sign_item=" + sign.signItemId()
        );
        if (!placed) {
            return new ClientScenarioReport("failed", id, observations);
        }

        boolean editorOpen = client.waitForSignEditor(signTarget, INVENTORY_TIMEOUT);
        observations.add("sign editor: " + (editorOpen ? "passed" : "failed"));
        if (!editorOpen) {
            return new ClientScenarioReport("failed", id, observations);
        }
        client.updateSignText(signTarget, P17_SIGN_LINES);
        boolean textVisible = client.waitForSignText(signTarget, P17_SIGN_LINES, INVENTORY_TIMEOUT);
        boolean editorClosed = textVisible && client.closeCurrentScreen(INVENTORY_TIMEOUT);
        observations.add(
            "sign text update: " + (textVisible && editorClosed ? "passed" : "failed")
                + " text_visible=" + textVisible
                + " editor_closed=" + editorClosed
                + " lines=" + String.join("|", P17_SIGN_LINES)
        );
        return new ClientScenarioReport(textVisible && editorClosed ? "passed" : "failed", id, observations);
    }
}
