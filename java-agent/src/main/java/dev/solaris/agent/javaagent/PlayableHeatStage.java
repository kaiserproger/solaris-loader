package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;

import java.util.List;

import static dev.solaris.agent.javaagent.PlayableCoordinates.coordinates;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.BREAK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.PICKUP_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.APPROACH_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.BLOCK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.CONTAINER_SCREEN;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.CRAFTING_SCREEN;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.FURNACE_COOK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.FURNACE_RECIPE_RESULT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.FURNACE_SCREEN;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.HOTBAR_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.INVENTORY_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.TORCH_RECIPE_RESULT;

/** Furnace placement/open, charcoal smelting, and torch craft/place spine. */
final class PlayableHeatStage {
    private PlayableHeatStage() {
    }

    record FurnacePlacementOpenResult(
        ClientScenarioReport report,
        PlayableResourceStage.PlanksRecipe planks,
        ScenarioBlockTarget furnaceTarget
    ) {}

    record FurnaceCharcoalSmeltResult(
        ClientScenarioReport report,
        PlayableResourceStage.PlanksRecipe planks,
        ScenarioBlockTarget furnaceTarget
    ) {}

    record TorchPlacementResult(
        ClientScenarioReport report,
        ScenarioBlockTarget torchTarget,
        ScenarioBlockTarget approachTarget
    ) {}

    static ClientScenarioReport runFurnacePlacementOpen(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        return prepareFurnacePlacementOpen(id, observations, client).report();
    }

    static FurnacePlacementOpenResult prepareFurnacePlacementOpen(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        PlayableResourceStage.WoodenToolTableResult prepared = PlayableResourceStage.prepareWoodenToolAndTable(id, observations, client);
        if (!"passed".equals(prepared.report().result())) {
            return new FurnacePlacementOpenResult(prepared.report(), prepared.planks(), null);
        }
        return finishFurnacePlacementOpen(id, observations, client, prepared.planks(), prepared.tableTarget());
    }

    static FurnacePlacementOpenResult finishFurnacePlacementOpen(
        String id,
        List<String> observations,
        ScenarioClient client,
        PlayableResourceStage.PlanksRecipe planks,
        ScenarioBlockTarget tableTarget
    ) throws Exception {
        ClientScenarioReport cobblestone = mineCobblestoneWithWoodenPickaxe(id, observations, client, 8);
        if (!"passed".equals(cobblestone.result())) {
            return new FurnacePlacementOpenResult(cobblestone, planks, null);
        }

        boolean tableApproached = client.approachBlock(tableTarget, APPROACH_TIMEOUT);
        observations.add(
            "crafting table approach for furnace: " + (tableApproached ? "passed" : "failed")
                + " target=" + coordinates(tableTarget)
        );
        if (tableApproached) {
            ScenarioHeldItem woodenPickaxe = client.selectHotbarItem("minecraft:wooden_pickaxe", 1, HOTBAR_TIMEOUT);
            ScenarioBlockTarget tableUseTarget = PlayableCoordinates.reachableUseTarget(client, tableTarget);
            ScenarioUseResult openUse = client.useItemOn(tableUseTarget, woodenPickaxe);
            boolean opened = client.waitForScreenClassName(CRAFTING_SCREEN, INVENTORY_TIMEOUT);
            observations.add(
                "crafting table reopen for furnace: " + (opened ? "passed" : "failed")
                    + " open_use_result=" + openUse.result()
                    + " screen=" + CRAFTING_SCREEN
                    + " screen_matched=" + opened
            );
            if (!opened) {
                return new FurnacePlacementOpenResult(
                    new ClientScenarioReport("failed", id, observations),
                    planks,
                    null
                );
            }
        } else {
            PlayableResourceStage.CraftingTableOpenResult spareTable = PlayableResourceStage.craftPlaceAndOpenTable(
                id,
                observations,
                client,
                planks.planksItemId(),
                false
            );
            if (!"passed".equals(spareTable.report().result())) {
                return new FurnacePlacementOpenResult(spareTable.report(), planks, null);
            }
        }

        ClientScenarioReport furnace = craftFurnaceInOpenTable(id, observations, client, true);
        if (!"passed".equals(furnace.result())) {
            return new FurnacePlacementOpenResult(furnace, planks, null);
        }
        FurnacePlacementOpenResult placed = placeAndOpenFurnace(id, observations, client);
        return new FurnacePlacementOpenResult(placed.report(), planks, placed.furnaceTarget());
    }

    static FurnacePlacementOpenResult placeAndOpenFurnace(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        ScenarioHeldItem furnace = client.selectHotbarItem("minecraft:furnace", 1, HOTBAR_TIMEOUT);
        if (!furnace.matches("minecraft:furnace", 1)) {
            observations.add("blocked: crafted furnace exists but is not selectable from hotbar");
            return new FurnacePlacementOpenResult(new ClientScenarioReport("blocked", id, observations), null, null);
        }
        ScenarioBlockPair pair = client.findDryPlaceablePair(ScenarioReach.WITHIN_SURVIVAL_REACH);
        if (pair == null) {
            observations.add("blocked: no loaded dry target found for furnace placement");
            return new FurnacePlacementOpenResult(new ClientScenarioReport("blocked", id, observations), null, null);
        }
        ScenarioBlockTarget clicked = new ScenarioBlockTarget(
            pair.clicked().x(),
            pair.clicked().y(),
            pair.clicked().z(),
            pair.clicked().face(),
            "furnace-clicked",
            pair.clicked().blockId()
        );
        ScenarioBlockTarget target = new ScenarioBlockTarget(
            pair.target().x(),
            pair.target().y(),
            pair.target().z(),
            "up",
            "furnace-target",
            "minecraft:furnace"
        );
        ScenarioUseResult placeUse = client.useItemOn(clicked, furnace);
        boolean placed = client.waitForBlock(target, "minecraft:furnace", BLOCK_TIMEOUT);
        ScenarioBlockTarget furnaceUseTarget = PlayableCoordinates.reachableUseTarget(client, target);
        ScenarioUseResult openUse = client.useItemOn(furnaceUseTarget, furnace);
        boolean opened = client.waitForScreenClassName(FURNACE_SCREEN, INVENTORY_TIMEOUT);
        boolean closed = client.closeCurrentScreen(INVENTORY_TIMEOUT);
        boolean passed = placed && opened && closed;
        observations.add(
            "furnace open: " + (passed ? "passed" : "failed")
                + " place_use_result=" + placeUse.result()
                + " placed=" + placed
                + " open_use_result=" + openUse.result()
                + " screen=" + FURNACE_SCREEN
                + " screen_matched=" + opened
                + " closed=" + closed
        );
        if (!passed) {
            return new FurnacePlacementOpenResult(new ClientScenarioReport("failed", id, observations), null, target);
        }
        return new FurnacePlacementOpenResult(new ClientScenarioReport("passed", id, observations), null, target);
    }

    static ClientScenarioReport runFurnaceCharcoalSmelt(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        return prepareFurnaceCharcoalSmelt(id, observations, client).report();
    }

    static FurnaceCharcoalSmeltResult prepareFurnaceCharcoalSmelt(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        FurnacePlacementOpenResult furnace = prepareFurnacePlacementOpen(id, observations, client);
        if (!"passed".equals(furnace.report().result())) {
            return new FurnaceCharcoalSmeltResult(
                furnace.report(),
                furnace.planks(),
                furnace.furnaceTarget()
            );
        }

        return smeltCharcoalInPlacedFurnace(id, observations, client, furnace);
    }

    static FurnaceCharcoalSmeltResult smeltCharcoalInPlacedFurnace(
        String id,
        List<String> observations,
        ScenarioClient client,
        FurnacePlacementOpenResult furnace
    ) throws Exception {
        int existingLogCount = client.inventoryCount(furnace.planks().logItemId());
        if (existingLogCount < 1) {
            ClientScenarioReport log = collectNaturalLogItem(id, observations, client, furnace.planks());
            if (!"passed".equals(log.result())) {
                return new FurnaceCharcoalSmeltResult(log, furnace.planks(), furnace.furnaceTarget());
            }
        } else {
            observations.add(
                "furnace input log inventory: passed item=" + furnace.planks().logItemId()
                    + " count=" + existingLogCount
            );
        }

        boolean furnaceApproached = client.approachBlock(furnace.furnaceTarget(), APPROACH_TIMEOUT);
        observations.add(
            "furnace approach for charcoal: " + (furnaceApproached ? "passed" : "failed")
                + " target=" + coordinates(furnace.furnaceTarget())
        );
        if (!furnaceApproached) {
            return new FurnaceCharcoalSmeltResult(
                new ClientScenarioReport("blocked", id, observations),
                furnace.planks(),
                furnace.furnaceTarget()
            );
        }

        ScenarioHeldItem furnaceItem = client.selectHotbarItem("minecraft:furnace", 1, HOTBAR_TIMEOUT);
        ScenarioBlockTarget furnaceUseTarget = PlayableCoordinates.reachableUseTarget(client, furnace.furnaceTarget());
        ScenarioUseResult openUse = client.useItemOn(furnaceUseTarget, furnaceItem);
        boolean opened = client.waitForScreenClassName(FURNACE_SCREEN, INVENTORY_TIMEOUT);
        observations.add(
            "furnace reopen for charcoal: " + (opened ? "passed" : "failed")
                + " open_use_result=" + openUse.result()
                + " screen=" + FURNACE_SCREEN
                + " screen_matched=" + opened
        );
        if (!opened) {
            return new FurnaceCharcoalSmeltResult(
                new ClientScenarioReport("failed", id, observations),
                furnace.planks(),
                furnace.furnaceTarget()
            );
        }

        boolean inputMoved = client.moveSelectedItemToContainerSlot(
            0,
            furnace.planks().logItemId(),
            1,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "furnace input transfer: " + (inputMoved ? "passed" : "failed")
                + " slot=0 item=" + furnace.planks().logItemId()
        );
        if (!inputMoved) {
            return new FurnaceCharcoalSmeltResult(
                new ClientScenarioReport("failed", id, observations),
                furnace.planks(),
                furnace.furnaceTarget()
            );
        }

        boolean fuelMoved = client.moveSelectedItemToContainerSlot(
            1,
            furnace.planks().planksItemId(),
            1,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "furnace fuel transfer: " + (fuelMoved ? "passed" : "failed")
                + " slot=1 item=" + furnace.planks().planksItemId()
        );
        if (!fuelMoved) {
            return new FurnaceCharcoalSmeltResult(
                new ClientScenarioReport("failed", id, observations),
                furnace.planks(),
                furnace.furnaceTarget()
            );
        }

        boolean outputReady = client.waitForContainerSlot(2, "minecraft:charcoal", 1, FURNACE_COOK_TIMEOUT);
        observations.add(
            "furnace charcoal output: " + (outputReady ? "passed" : "failed")
                + " slot=2 item=minecraft:charcoal"
        );
        if (!outputReady) {
            return new FurnaceCharcoalSmeltResult(
                new ClientScenarioReport("failed", id, observations),
                furnace.planks(),
                furnace.furnaceTarget()
            );
        }

        boolean fuelRemainderCleared = client.moveContainerSlotToInventory(
            1,
            furnace.planks().planksItemId(),
            1,
            INVENTORY_TIMEOUT
        ) || client.waitForContainerSlotEmpty(1, INVENTORY_TIMEOUT);
        observations.add(
            "furnace fuel remainder clear: " + (fuelRemainderCleared ? "passed" : "failed")
                + " slot=1 item=" + furnace.planks().planksItemId()
        );
        if (!fuelRemainderCleared) {
            return new FurnaceCharcoalSmeltResult(
                new ClientScenarioReport("failed", id, observations),
                furnace.planks(),
                furnace.furnaceTarget()
            );
        }

        boolean inputRemainderCleared = client.moveContainerSlotToInventory(
            0,
            furnace.planks().logItemId(),
            1,
            INVENTORY_TIMEOUT
        ) || client.waitForContainerSlotEmpty(0, INVENTORY_TIMEOUT);
        observations.add(
            "furnace input remainder clear: " + (inputRemainderCleared ? "passed" : "failed")
                + " slot=0 item=" + furnace.planks().logItemId()
        );
        if (!inputRemainderCleared) {
            return new FurnaceCharcoalSmeltResult(
                new ClientScenarioReport("failed", id, observations),
                furnace.planks(),
                furnace.furnaceTarget()
            );
        }

        boolean outputTaken = client.moveContainerSlotToInventory(
            2,
            "minecraft:charcoal",
            1,
            INVENTORY_TIMEOUT
        );
        boolean charcoalInInventory = client.waitForInventoryCount(
            "minecraft:charcoal",
            1,
            INVENTORY_TIMEOUT
        );
        boolean closed = client.closeCurrentScreen(INVENTORY_TIMEOUT);
        observations.add(
            "furnace charcoal inventory: "
                + (outputTaken && charcoalInInventory && closed ? "passed" : "failed")
                + " output_taken=" + outputTaken
                + " charcoal_inventory_matched=" + charcoalInInventory
                + " closed=" + closed
            );
        if (!outputTaken || !charcoalInInventory || !closed) {
            return new FurnaceCharcoalSmeltResult(
                new ClientScenarioReport("failed", id, observations),
                furnace.planks(),
                furnace.furnaceTarget()
            );
        }
        return new FurnaceCharcoalSmeltResult(
            new ClientScenarioReport("passed", id, observations),
            furnace.planks(),
            furnace.furnaceTarget()
        );
    }

    static ClientScenarioReport runTorchCraftPlace(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        return prepareTorchCraftPlace(id, observations, client).report();
    }

    static TorchPlacementResult prepareTorchCraftPlace(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        FurnaceCharcoalSmeltResult charcoal = prepareFurnaceCharcoalSmelt(id, observations, client);
        if (!"passed".equals(charcoal.report().result())) {
            return new TorchPlacementResult(charcoal.report(), null, null);
        }

        int charcoalCount = client.inventoryCount("minecraft:charcoal");
        int stickCount = client.inventoryCount("minecraft:stick");
        int torchCount = client.inventoryCount("minecraft:torch");
        if (charcoalCount < 1 || stickCount < 1) {
            observations.add(
                "torch recipe: failed missing charcoal or sticks"
                    + " charcoal_count=" + charcoalCount
                    + " stick_count=" + stickCount
            );
            return new TorchPlacementResult(new ClientScenarioReport("failed", id, observations), null, null);
        }

        int expectedCharcoalCount = charcoalCount - 1;
        int expectedStickCount = stickCount - 1;
        int expectedTorchCount = torchCount + 4;
        client.placeRecipe(0, client.requireRecipeDisplayId(TORCH_RECIPE_RESULT), false);
        boolean charcoalConsumed = client.waitForInventoryCount(
            "minecraft:charcoal",
            expectedCharcoalCount,
            INVENTORY_TIMEOUT
        );
        boolean stickConsumed = client.waitForInventoryCount(
            "minecraft:stick",
            expectedStickCount,
            INVENTORY_TIMEOUT
        );
        boolean torchesCreated = client.waitForInventoryCount(
            "minecraft:torch",
            expectedTorchCount,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "torch recipe: "
                + (charcoalConsumed && stickConsumed && torchesCreated ? "passed" : "failed")
                + " recipe_result=" + TORCH_RECIPE_RESULT
                + " charcoal_expected_count=" + expectedCharcoalCount
                + " charcoal_count_matched=" + charcoalConsumed
                + " stick_expected_count=" + expectedStickCount
                + " stick_count_matched=" + stickConsumed
                + " torch_expected_count=" + expectedTorchCount
                + " torch_count_matched=" + torchesCreated
        );
        if (!charcoalConsumed || !stickConsumed || !torchesCreated) {
            return new TorchPlacementResult(new ClientScenarioReport("failed", id, observations), null, null);
        }

        ScenarioHeldItem torch = client.selectHotbarItem("minecraft:torch", expectedTorchCount, HOTBAR_TIMEOUT);
        if (!torch.matches("minecraft:torch", expectedTorchCount)) {
            observations.add("blocked: crafted torches exist but are not selectable from hotbar");
            return new TorchPlacementResult(new ClientScenarioReport("blocked", id, observations), null, null);
        }
        ScenarioBlockPair pair = client.findDryPlaceablePair(ScenarioReach.WITHIN_SURVIVAL_REACH);
        if (pair == null) {
            observations.add("blocked: no loaded dry target found for torch placement");
            return new TorchPlacementResult(new ClientScenarioReport("blocked", id, observations), null, null);
        }
        ScenarioBlockTarget clicked = new ScenarioBlockTarget(
            pair.clicked().x(),
            pair.clicked().y(),
            pair.clicked().z(),
            pair.clicked().face(),
            "torch-clicked",
            pair.clicked().blockId()
        );
        ScenarioBlockTarget target = new ScenarioBlockTarget(
            pair.target().x(),
            pair.target().y(),
            pair.target().z(),
            "up",
            "torch-target",
            "minecraft:torch"
        );
        ScenarioUseResult placeUse = client.useItemOn(clicked, torch);
        boolean placed = client.waitForBlock(target, "minecraft:torch", BLOCK_TIMEOUT);
        observations.add(
            "torch placement: " + (placed ? "passed" : "failed")
                + " place_use_result=" + placeUse.result()
                + " target=" + coordinates(target)
        );
        return new TorchPlacementResult(
            new ClientScenarioReport(placed ? "passed" : "failed", id, observations),
            placed ? target : null,
            placed ? clicked : null
        );
    }

    static ClientScenarioReport collectNaturalLogItem(
        String id,
        List<String> observations,
        ScenarioClient client,
        PlayableResourceStage.PlanksRecipe planks
    ) throws Exception {
        return collectNaturalLogItem(id, observations, client, planks, "furnace input log");
    }

    static ClientScenarioReport collectNaturalLogItem(
        String id,
        List<String> observations,
        ScenarioClient client,
        PlayableResourceStage.PlanksRecipe planks,
        String observationLabel
    ) throws Exception {
        ScenarioBlockTarget log = client.findBreakableBlock(
            List.of(planks.logItemId()),
            ScenarioReach.WITHIN_SURVIVAL_REACH
        );
        if (log == null) {
            ScenarioBlockTarget farLog = client.findBreakableBlock(
                List.of(planks.logItemId()),
                ScenarioReach.OUTSIDE_SURVIVAL_REACH
            );
            if (farLog == null) {
                observations.add("blocked: no loaded natural log found for furnace input");
                return new ClientScenarioReport("blocked", id, observations);
            }
            boolean approached = client.approachBlock(farLog, APPROACH_TIMEOUT);
            observations.add(
                observationLabel + " approach: " + (approached ? "passed" : "failed")
                    + " target=" + coordinates(farLog)
            );
            log = client.findBreakableBlock(List.of(planks.logItemId()), ScenarioReach.WITHIN_SURVIVAL_REACH);
            if (!approached) {
                if (log == null) {
                    return new ClientScenarioReport("blocked", id, observations);
                }
                observations.add(
                    observationLabel + " reachable fallback after failed approach: passed"
                        + " target=" + coordinates(log)
                );
            } else if (log == null) {
                observations.add("blocked: furnace input log remained outside survival reach after approach");
                return new ClientScenarioReport("blocked", id, observations);
            }
        }

        boolean closeApproached = client.approachBlock(log, APPROACH_TIMEOUT);
        observations.add(
            observationLabel + " close approach: " + (closeApproached ? "passed" : "failed")
                + " target=" + coordinates(log)
        );
        ScenarioBreakResult broke = client.breakBlockUntilDropVisible(log, planks.logItemId(), BREAK_TIMEOUT);
        ScenarioBreakResult pickup = client.collectVisibleItemDrop(
            log,
            planks.logItemId(),
            1,
            PICKUP_TIMEOUT
        );
        boolean naturalPickup = broke.started()
            && broke.becameAir()
            && broke.sawDrop()
            && pickup.pickupRestored();
        observations.add(
            observationLabel + " break/drop/pickup: " + (naturalPickup ? "passed" : "failed")
                + " target=" + coordinates(log)
                + " break_started=" + broke.started()
                + " became_air=" + broke.becameAir()
                + " saw_drop=" + broke.sawDrop()
                + " pickup_restored=" + pickup.pickupRestored()
        );
        if (!naturalPickup) {
            return new ClientScenarioReport("failed", id, observations);
        }
        return new ClientScenarioReport("passed", id, observations);
    }

    static ClientScenarioReport mineCobblestoneWithWoodenPickaxe(
        String id,
        List<String> observations,
        ScenarioClient client,
        int targetCobblestoneCount
    ) throws Exception {
        ScenarioHeldItem woodenPickaxe = client.selectHotbarItem("minecraft:wooden_pickaxe", 1, HOTBAR_TIMEOUT);
        if (!woodenPickaxe.matches("minecraft:wooden_pickaxe", 1)) {
            observations.add("blocked: crafted wooden pickaxe is not selectable for stone mining");
            return new ClientScenarioReport("blocked", id, observations);
        }

        int miningAttempts = 0;
        int maxMiningAttempts = targetCobblestoneCount + 6;
        while (
            client.inventoryCount("minecraft:cobblestone") < targetCobblestoneCount
                && miningAttempts < maxMiningAttempts
        ) {
            miningAttempts += 1;
            ScenarioBlockTarget stone = client.findBreakableBlock(
                List.of("minecraft:stone"),
                ScenarioReach.WITHIN_SURVIVAL_REACH
            );
            if (stone == null) {
                ScenarioBlockTarget farStone = client.findBreakableBlock(
                    List.of("minecraft:stone"),
                    ScenarioReach.OUTSIDE_SURVIVAL_REACH
                );
                if (farStone == null) {
                    observations.add("blocked: no loaded natural stone found near the real client");
                    return new ClientScenarioReport("blocked", id, observations);
                }
                boolean approached = client.approachBlock(farStone, APPROACH_TIMEOUT);
                observations.add(
                    "natural stone approach: " + (approached ? "passed" : "failed")
                        + " target=" + coordinates(farStone)
                );
                stone = client.findBreakableBlock(List.of("minecraft:stone"), ScenarioReach.WITHIN_SURVIVAL_REACH);
                if (!approached) {
                    if (stone == null) {
                        return new ClientScenarioReport("blocked", id, observations);
                    }
                    observations.add(
                        "natural stone reachable fallback after failed approach: passed"
                            + " target=" + coordinates(stone)
                    );
                } else if (stone == null) {
                    observations.add("blocked: natural stone remained outside survival reach after approach");
                    return new ClientScenarioReport("blocked", id, observations);
                }
            }

            boolean closeApproached = client.approachBlock(stone, APPROACH_TIMEOUT);
            observations.add(
                "natural stone close approach: " + (closeApproached ? "passed" : "failed")
                    + " target=" + coordinates(stone)
            );
            if (!closeApproached) {
                return new ClientScenarioReport("blocked", id, observations);
            }

            int cobblestoneBefore = client.inventoryCount("minecraft:cobblestone");
            ScenarioBreakResult broke = client.breakBlockUntilDropVisible(
                stone,
                "minecraft:cobblestone",
                BREAK_TIMEOUT
            );
            ScenarioBreakResult pickup = client.collectVisibleItemDrop(
                stone,
                "minecraft:cobblestone",
                1,
                PICKUP_TIMEOUT
            );
            int cobblestoneAfter = client.inventoryCount("minecraft:cobblestone");
            boolean inventoryAdvanced = cobblestoneAfter >= cobblestoneBefore + 1;
            boolean naturalPickup = broke.started()
                && broke.becameAir()
                && broke.sawDrop()
                && inventoryAdvanced;
            observations.add(
                "stone break/drop/pickup: " + (naturalPickup ? "passed" : "failed")
                    + " target=" + coordinates(stone)
                    + " break_started=" + broke.started()
                    + " became_air=" + broke.becameAir()
                    + " saw_drop=" + broke.sawDrop()
                    + " pickup_restored=" + pickup.pickupRestored()
                    + " cobblestone_before=" + cobblestoneBefore
                    + " cobblestone_after=" + cobblestoneAfter
                    + " held=" + pickup.selectedItem().itemId() + " x" + pickup.selectedItem().count()
            );
            if (!naturalPickup) {
                if (broke.started() && broke.becameAir() && broke.sawDrop() && !inventoryAdvanced) {
                    continue;
                }
                return new ClientScenarioReport("failed", id, observations);
            }
        }

        int cobblestoneCount = client.inventoryCount("minecraft:cobblestone");
        if (cobblestoneCount < targetCobblestoneCount) {
            observations.add(
                "stone inventory: failed cobblestone_count=" + cobblestoneCount
                    + " expected_at_least=" + targetCobblestoneCount
                    + " mining_attempts=" + miningAttempts
            );
            return new ClientScenarioReport("failed", id, observations);
        }
        observations.add(
            "stone inventory: passed cobblestone_count=" + cobblestoneCount
                + " expected_at_least=" + targetCobblestoneCount
                + " mining_attempts=" + miningAttempts
        );
        return new ClientScenarioReport("passed", id, observations);
    }

    static ClientScenarioReport craftFurnaceInOpenTable(
        String id,
        List<String> observations,
        ScenarioClient client,
        boolean closeScreen
    ) throws Exception {
        int containerId = client.activeContainerId();
        int cobblestoneCount = client.inventoryCount("minecraft:cobblestone");
        int furnaceCount = client.inventoryCount("minecraft:furnace");
        if (cobblestoneCount < 8) {
            observations.add("furnace recipe: failed fewer than eight cobblestone available");
            return new ClientScenarioReport("failed", id, observations);
        }
        int expectedCobblestoneAfterFurnace = cobblestoneCount - 8;
        int expectedFurnaceCount = furnaceCount + 1;
        client.placeRecipe(containerId, client.requireRecipeDisplayId(FURNACE_RECIPE_RESULT), false);
        boolean furnaceCobblestoneConsumed = client.waitForInventoryCount(
            "minecraft:cobblestone",
            expectedCobblestoneAfterFurnace,
            INVENTORY_TIMEOUT
        );
        boolean furnaceCreated = client.waitForInventoryCount(
            "minecraft:furnace",
            expectedFurnaceCount,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "furnace recipe: "
                + (furnaceCobblestoneConsumed && furnaceCreated ? "passed" : "failed")
                + " container_id=" + containerId
                + " recipe_result=" + FURNACE_RECIPE_RESULT
                + " cobblestone_expected_count=" + expectedCobblestoneAfterFurnace
                + " cobblestone_count_matched=" + furnaceCobblestoneConsumed
                + " furnace_expected_count=" + expectedFurnaceCount
                + " furnace_count_matched=" + furnaceCreated
        );
        boolean closed = !closeScreen || client.closeCurrentScreen(INVENTORY_TIMEOUT);
        if (closeScreen) {
            observations.add("crafting table screen close after furnace: " + (closed ? "passed" : "failed"));
        }
        if (!furnaceCobblestoneConsumed || !furnaceCreated || !closed) {
            return new ClientScenarioReport("failed", id, observations);
        }

        return new ClientScenarioReport("passed", id, observations);
    }

    static ClientScenarioReport smeltRawIronInPlacedFurnace(
        String id,
        List<String> observations,
        ScenarioClient client,
        PlayableHeatStage.FurnacePlacementOpenResult furnace,
        PlayableResourceStage.PlanksRecipe planks
    ) throws Exception {
        return smeltRawIronInPlacedFurnace(id, observations, client, furnace, planks, true, 1);
    }

    static ClientScenarioReport smeltRawIronInPlacedFurnace(
        String id,
        List<String> observations,
        ScenarioClient client,
        PlayableHeatStage.FurnacePlacementOpenResult furnace,
        PlayableResourceStage.PlanksRecipe planks,
        int smeltCount
    ) throws Exception {
        return smeltRawIronInPlacedFurnace(id, observations, client, furnace, planks, true, smeltCount);
    }

    static ClientScenarioReport smeltRawIronInPlacedFurnace(
        String id,
        List<String> observations,
        ScenarioClient client,
        PlayableHeatStage.FurnacePlacementOpenResult furnace,
        PlayableResourceStage.PlanksRecipe planks,
        boolean addFuel,
        int smeltCount
    ) throws Exception {
        boolean furnaceApproached = client.approachBlock(furnace.furnaceTarget(), APPROACH_TIMEOUT);
        observations.add(
            "furnace approach for iron ingot: " + (furnaceApproached ? "passed" : "failed")
                + " target=" + coordinates(furnace.furnaceTarget())
        );
        if (!furnaceApproached) {
            return new ClientScenarioReport("blocked", id, observations);
        }

        ScenarioHeldItem stonePickaxe = client.selectHotbarItem("minecraft:stone_pickaxe", 1, HOTBAR_TIMEOUT);
        ScenarioBlockTarget furnaceUseTarget = PlayableCoordinates.reachableUseTarget(client, furnace.furnaceTarget());
        ScenarioUseResult openUse = client.useItemOn(furnaceUseTarget, stonePickaxe);
        boolean opened = client.waitForScreenClassName(FURNACE_SCREEN, INVENTORY_TIMEOUT);
        observations.add(
            "furnace reopen for iron ingot: " + (opened ? "passed" : "failed")
                + " open_use_result=" + openUse.result()
                + " screen=" + FURNACE_SCREEN
                + " screen_matched=" + opened
        );
        if (!opened) {
            return new ClientScenarioReport("failed", id, observations);
        }

        int ironIngotCount = client.inventoryCount("minecraft:iron_ingot");
        boolean inputMoved = client.moveSelectedItemToContainerSlot(
            0,
            "minecraft:raw_iron",
            smeltCount,
            INVENTORY_TIMEOUT
        );
        observations.add("furnace raw iron input transfer: " + (inputMoved ? "passed" : "failed"));
        if (!inputMoved) {
            return new ClientScenarioReport("failed", id, observations);
        }

        boolean fuelMoved = true;
        if (addFuel) {
            int fuelCount = smeltCount > 1 ? 2 : 1;
            fuelMoved = client.moveSelectedItemToContainerSlot(
                1,
                planks.planksItemId(),
                fuelCount,
                INVENTORY_TIMEOUT
            );
            observations.add(
                "furnace iron fuel transfer: " + (fuelMoved ? "passed" : "failed")
                    + " slot=1 item=" + planks.planksItemId()
            );
            if (!fuelMoved) {
                return new ClientScenarioReport("failed", id, observations);
            }
        } else {
            observations.add(
                "furnace iron fuel transfer: skipped existing_burn=true"
                    + " slot=1 item=" + planks.planksItemId()
            );
        }

        boolean outputReady = client.waitForContainerSlot(
            2,
            "minecraft:iron_ingot",
            smeltCount,
            FURNACE_COOK_TIMEOUT
        );
        observations.add(
            "furnace iron ingot output: " + (outputReady ? "passed" : "failed")
                + " slot=2 item=minecraft:iron_ingot"
        );
        if (!outputReady) {
            return new ClientScenarioReport("failed", id, observations);
        }

        boolean fuelRemainderCleared = !addFuel || client.moveContainerSlotToInventory(
            1,
            planks.planksItemId(),
            1,
            INVENTORY_TIMEOUT
        ) || client.waitForContainerSlotEmpty(1, INVENTORY_TIMEOUT);
        observations.add(
            "furnace iron fuel remainder clear: " + (fuelRemainderCleared ? "passed" : "failed")
                + " slot=1 item=" + planks.planksItemId()
        );
        if (!fuelRemainderCleared) {
            return new ClientScenarioReport("failed", id, observations);
        }

        boolean inputRemainderCleared = client.waitForContainerSlotEmpty(0, INVENTORY_TIMEOUT)
            || client.moveContainerSlotToInventory(0, "minecraft:raw_iron", 1, INVENTORY_TIMEOUT);
        observations.add(
            "furnace raw iron input remainder clear: " + (inputRemainderCleared ? "passed" : "failed")
                + " slot=0 item=minecraft:raw_iron"
        );
        if (!inputRemainderCleared) {
            return new ClientScenarioReport("failed", id, observations);
        }

        int experienceBefore = smeltCount > 1 ? client.totalExperience() : -1;
        int expectedIronIngotCount = ironIngotCount + smeltCount;
        boolean outputTaken = client.moveContainerSlotToInventory(
            2,
            "minecraft:iron_ingot",
            smeltCount,
            INVENTORY_TIMEOUT
        );
        boolean ingotInInventory = client.waitForInventoryCount(
            "minecraft:iron_ingot",
            expectedIronIngotCount,
            INVENTORY_TIMEOUT
        );
        int experienceAfter = smeltCount > 1
            ? client.waitForTotalExperienceAbove(experienceBefore, INVENTORY_TIMEOUT)
            : experienceBefore;
        boolean experienceAdvanced = smeltCount <= 1 || experienceAfter > experienceBefore;
        boolean closed = client.closeCurrentScreen(INVENTORY_TIMEOUT);
        observations.add(
            "furnace iron ingot inventory: "
                + (outputTaken && ingotInInventory && closed ? "passed" : "failed")
                + " output_taken=" + outputTaken
                + " iron_ingot_expected_count=" + expectedIronIngotCount
                + " iron_ingot_inventory_matched=" + ingotInInventory
                + " closed=" + closed
        );
        if (smeltCount > 1) {
            observations.add(
                "furnace iron experience: " + (experienceAdvanced ? "passed" : "failed")
                    + " total_experience_before=" + experienceBefore
                    + " total_experience_after=" + experienceAfter
            );
        }
        if (!outputTaken || !ingotInInventory || !experienceAdvanced || !closed) {
            return new ClientScenarioReport("failed", id, observations);
        }
        return new ClientScenarioReport("passed", id, observations);
    }
}
