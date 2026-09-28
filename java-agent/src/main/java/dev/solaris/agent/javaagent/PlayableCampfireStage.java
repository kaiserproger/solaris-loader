package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;

import java.util.List;

import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.CRAFTING_SCREEN;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.HOTBAR_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableResourceStage.STICK_RECIPE_RESULT;
import static dev.solaris.agent.javaagent.PlayableCoordinates.coordinates;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.APPROACH_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.BLOCK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.CAMPFIRE_COOK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.CAMPFIRE_DEATH_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.CAMPFIRE_RECIPE_RESULT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.INVENTORY_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.PICKUP_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.RESPAWN_TIMEOUT;

/** Campfire cooking plus hazard death/respawn and death-drop recovery. */
final class PlayableCampfireStage {
    private PlayableCampfireStage() {
    }

    record CampfireDeathRespawnResult(
        ClientScenarioReport report,
        ScenarioBlockTarget campfireTarget,
        ScenarioItemDropIdentity woodenPickaxeDropIdentity
    ) {}

    private record CampfireCookingResult(
        ClientScenarioReport report,
        ScenarioBlockTarget campfireTarget
    ) {}

    static ClientScenarioReport runEarnedCampfireCooking(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        return runEarnedCampfireCooking(id, observations, client, true);
    }

    static ClientScenarioReport runEarnedCampfireCooking(
        String id,
        List<String> observations,
        ScenarioClient client,
        boolean requireCookedPickup
    ) throws Exception {
        return prepareEarnedCampfireCooking(id, observations, client, requireCookedPickup).report();
    }

    static CampfireCookingResult prepareEarnedCampfireCooking(
        String id,
        List<String> observations,
        ScenarioClient client,
        boolean requireCookedPickup
    ) throws Exception {
        PlayableResourceStage.WoodenToolTableResult prepared = PlayableResourceStage.prepareWoodenToolAndTable(id, observations, client);
        if (!"passed".equals(prepared.report().result())) {
            return new CampfireCookingResult(prepared.report(), null);
        }

        for (int log = 0; log < 4; log++) {
            ClientScenarioReport collectedLog = PlayableHeatStage.collectNaturalLogItem(
                id,
                observations,
                client,
                prepared.planks(),
                "campfire reserve log"
            );
            if (!"passed".equals(collectedLog.result())) {
                return new CampfireCookingResult(collectedLog, null);
            }
        }

        PlayableFoodStage.PassiveFoodDropResult drop = PlayableFoodStage.collectPassiveFoodDrop(id, observations, client);
        if (!"passed".equals(drop.report().result())) {
            return new CampfireCookingResult(drop.report(), null);
        }
        String cookedItemId = PlayableFoodStage.COOKED_PASSIVE_FOOD_RESULTS.get(drop.dropItemId());
        if (cookedItemId == null) {
            observations.add("blocked: passive food drop has no campfire cooking result item=" + drop.dropItemId());
            return new CampfireCookingResult(new ClientScenarioReport("blocked", id, observations), null);
        }

        PlayableHeatStage.FurnacePlacementOpenResult furnace = PlayableHeatStage.finishFurnacePlacementOpen(
            id,
            observations,
            client,
            prepared.planks(),
            prepared.tableTarget()
        );
        if (!"passed".equals(furnace.report().result())) {
            return new CampfireCookingResult(furnace.report(), null);
        }

        PlayableHeatStage.FurnaceCharcoalSmeltResult charcoal = PlayableHeatStage.smeltCharcoalInPlacedFurnace(id, observations, client, furnace);
        if (!"passed".equals(charcoal.report().result())) {
            return new CampfireCookingResult(charcoal.report(), null);
        }

        ScenarioBlockTarget craftingTableTarget = prepared.tableTarget();
        boolean tableApproached = client.approachBlock(craftingTableTarget, APPROACH_TIMEOUT);
        observations.add(
            "crafting table approach for campfire: " + (tableApproached ? "passed" : "failed")
                + " target=" + coordinates(craftingTableTarget)
        );
        if (!tableApproached) {
            PlayableResourceStage.CraftingTableOpenResult spareTable = PlayableResourceStage.craftPlaceAndOpenTable(
                id,
                observations,
                client,
                prepared.planks().planksItemId(),
                false
            );
            if (!"passed".equals(spareTable.report().result())) {
                return new CampfireCookingResult(spareTable.report(), null);
            }
            craftingTableTarget = spareTable.tableTarget();
        }
        if (tableApproached) {
            ScenarioBlockTarget tableUseTarget = PlayableCoordinates.reachableUseTarget(client, craftingTableTarget);
            ScenarioUseResult tableOpenUse = client.useItemOn(tableUseTarget, client.selectedItem());
            boolean tableOpened = client.waitForScreenClassName(CRAFTING_SCREEN, INVENTORY_TIMEOUT);
            observations.add(
                "crafting table reopen for campfire: " + (tableOpened ? "passed" : "failed")
                    + " open_use_result=" + tableOpenUse.result()
                    + " screen=" + CRAFTING_SCREEN
                    + " screen_matched=" + tableOpened
            );
            if (!tableOpened) {
                return new CampfireCookingResult(new ClientScenarioReport("failed", id, observations), null);
            }
        }

        int containerId = client.activeContainerId();
        int stickCount = client.inventoryCount("minecraft:stick");
        if (stickCount < 3) {
            int planksCount = client.inventoryCount(prepared.planks().planksItemId());
            if (planksCount < 2) {
                observations.add("campfire stick recipe: failed fewer than two planks available");
                return new CampfireCookingResult(new ClientScenarioReport("failed", id, observations), null);
            }
            int expectedPlanksAfterSticks = planksCount - 2;
            int expectedStickCount = stickCount + 4;
            client.placeRecipe(containerId, client.requireRecipeDisplayId(STICK_RECIPE_RESULT), false);
            boolean planksConsumed = client.waitForInventoryCount(
                prepared.planks().planksItemId(),
                expectedPlanksAfterSticks,
                INVENTORY_TIMEOUT
            );
            boolean sticksCreated = client.waitForInventoryCount(
                "minecraft:stick",
                expectedStickCount,
                INVENTORY_TIMEOUT
            );
            observations.add(
                "campfire stick recipe: " + (planksConsumed && sticksCreated ? "passed" : "failed")
                    + " container_id=" + containerId
                    + " recipe_result=" + STICK_RECIPE_RESULT
                    + " planks_item=" + prepared.planks().planksItemId()
                    + " planks_expected_count=" + expectedPlanksAfterSticks
                    + " planks_count_matched=" + planksConsumed
                    + " stick_expected_count=" + expectedStickCount
                    + " stick_count_matched=" + sticksCreated
            );
            if (!planksConsumed || !sticksCreated) {
                return new CampfireCookingResult(new ClientScenarioReport("failed", id, observations), null);
            }
        }

        int logCount = client.inventoryCount(prepared.planks().logItemId());
        stickCount = client.inventoryCount("minecraft:stick");
        int charcoalCount = client.inventoryCount("minecraft:charcoal");
        int campfireCount = client.inventoryCount("minecraft:campfire");
        if (logCount < 3 || stickCount < 3 || charcoalCount < 1) {
            observations.add(
                "campfire recipe: failed missing earned logs, sticks, or charcoal"
                    + " log_count=" + logCount
                    + " stick_count=" + stickCount
                    + " charcoal_count=" + charcoalCount
            );
            return new CampfireCookingResult(new ClientScenarioReport("failed", id, observations), null);
        }
        int expectedLogCount = logCount - 3;
        int expectedStickCount = stickCount - 3;
        int expectedCharcoalCount = charcoalCount - 1;
        int expectedCampfireCount = campfireCount + 1;
        client.placeRecipe(containerId, client.requireRecipeDisplayId(CAMPFIRE_RECIPE_RESULT), false);
        boolean logsConsumed = client.waitForInventoryCount(
            prepared.planks().logItemId(),
            expectedLogCount,
            INVENTORY_TIMEOUT
        );
        boolean sticksConsumed = client.waitForInventoryCount(
            "minecraft:stick",
            expectedStickCount,
            INVENTORY_TIMEOUT
        );
        boolean charcoalConsumed = client.waitForInventoryCount(
            "minecraft:charcoal",
            expectedCharcoalCount,
            INVENTORY_TIMEOUT
        );
        boolean campfireCreated = client.waitForInventoryCount(
            "minecraft:campfire",
            expectedCampfireCount,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "campfire recipe: "
                + (logsConsumed && sticksConsumed && charcoalConsumed && campfireCreated ? "passed" : "failed")
                + " container_id=" + containerId
                + " recipe_result=" + CAMPFIRE_RECIPE_RESULT
                + " log_item=" + prepared.planks().logItemId()
                + " log_expected_count=" + expectedLogCount
                + " log_count_matched=" + logsConsumed
                + " stick_expected_count=" + expectedStickCount
                + " stick_count_matched=" + sticksConsumed
                + " charcoal_expected_count=" + expectedCharcoalCount
                + " charcoal_count_matched=" + charcoalConsumed
                + " campfire_expected_count=" + expectedCampfireCount
                + " campfire_count_matched=" + campfireCreated
        );
        if (!logsConsumed || !sticksConsumed || !charcoalConsumed || !campfireCreated) {
            return new CampfireCookingResult(new ClientScenarioReport("failed", id, observations), null);
        }
        boolean craftingClosed = client.closeCurrentScreen(INVENTORY_TIMEOUT);
        observations.add("crafting table screen close after campfire: " + (craftingClosed ? "passed" : "failed"));
        if (!craftingClosed) {
            return new CampfireCookingResult(new ClientScenarioReport("failed", id, observations), null);
        }

        ScenarioHeldItem heldCampfire = client.selectHotbarItem("minecraft:campfire", 1, HOTBAR_TIMEOUT);
        if (!heldCampfire.matches("minecraft:campfire", 1)) {
            observations.add("blocked: crafted campfire exists but is not selectable from hotbar");
            return new CampfireCookingResult(new ClientScenarioReport("blocked", id, observations), null);
        }
        ScenarioBlockPair pair = client.findOpenDryPlaceablePair(ScenarioReach.WITHIN_SURVIVAL_REACH);
        if (pair == null) {
            observations.add("blocked: no loaded open dry target found for campfire placement");
            return new CampfireCookingResult(new ClientScenarioReport("blocked", id, observations), null);
        }
        ScenarioBlockTarget clicked = new ScenarioBlockTarget(
            pair.clicked().x(),
            pair.clicked().y(),
            pair.clicked().z(),
            pair.clicked().face(),
            "campfire-clicked",
            pair.clicked().blockId()
        );
        ScenarioBlockTarget campfireTarget = new ScenarioBlockTarget(
            pair.target().x(),
            pair.target().y(),
            pair.target().z(),
            "up",
            "campfire-target",
            "minecraft:campfire"
        );
        ScenarioUseResult placeUse = client.useItemOn(clicked, heldCampfire);
        boolean placed = client.waitForBlock(campfireTarget, "minecraft:campfire", BLOCK_TIMEOUT);
        observations.add(
            "campfire placement: " + (placed ? "passed" : "failed")
                + " place_use_result=" + placeUse.result()
                + " target=" + coordinates(campfireTarget)
        );
        if (!placed) {
            return new CampfireCookingResult(new ClientScenarioReport("failed", id, observations), null);
        }

        int cookedCountBefore = client.inventoryCount(cookedItemId);
        ScenarioHeldItem rawFood = client.selectHotbarItem(drop.dropItemId(), 1, HOTBAR_TIMEOUT);
        if (!rawFood.matches(drop.dropItemId(), 1)) {
            observations.add("blocked: earned raw passive food exists but is not selectable from hotbar");
            return new CampfireCookingResult(new ClientScenarioReport("blocked", id, observations), campfireTarget);
        }
        ScenarioUseResult cookUse = client.useItemOn(campfireTarget, rawFood);
        boolean outputVisible = client.waitForVisibleItemDrop(cookedItemId, campfireTarget, CAMPFIRE_COOK_TIMEOUT);
        ScenarioBreakResult pickup = outputVisible
            ? client.collectVisibleItemDrop(campfireTarget, cookedItemId, 1, PICKUP_TIMEOUT)
            : new ScenarioBreakResult(false, false, false, false, rawFood);
        boolean cookedCollected = outputVisible
            && pickup.pickupRestored()
            && client.waitForInventoryCount(cookedItemId, cookedCountBefore + 1, INVENTORY_TIMEOUT);
        boolean cookingSatisfied = requireCookedPickup ? cookedCollected : outputVisible;
        observations.add(
            "campfire cooking output: " + (cookingSatisfied ? "passed" : "failed")
                + " cook_use_result=" + cookUse.result()
                + " raw_item=" + drop.dropItemId()
                + " cooked_item=" + cookedItemId
                + " output_visible=" + outputVisible
                + " pickup_restored=" + pickup.pickupRestored()
                + " pickup_required=" + requireCookedPickup
                + " cooked_expected_count=" + (cookedCountBefore + 1)
        );
        return new CampfireCookingResult(
            new ClientScenarioReport(cookingSatisfied ? "passed" : "failed", id, observations),
            campfireTarget
        );
    }

    static ClientScenarioReport runEarnedCampfireDeathRespawn(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        return performEarnedCampfireDeathRespawn(id, observations, client, false).report();
    }

    static ClientScenarioReport runCampfireDeathDropRecovery(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        CampfireDeathRespawnResult death = performEarnedCampfireDeathRespawn(id, observations, client, true);
        if (!"passed".equals(death.report().result())) {
            return death.report();
        }

        int pickaxeCountBeforeRecovery = client.inventoryCount("minecraft:wooden_pickaxe");
        boolean deathSiteApproached = client.approachBlock(death.campfireTarget(), APPROACH_TIMEOUT);
        observations.add(
            "campfire death-site return: " + (deathSiteApproached ? "passed" : "failed")
                + " target=" + coordinates(death.campfireTarget())
        );
        if (!deathSiteApproached) {
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioBreakResult pickup = client.collectVisibleItemDropByIdentity(
            death.campfireTarget(),
            "minecraft:wooden_pickaxe",
            death.woodenPickaxeDropIdentity(),
            1,
            PICKUP_TIMEOUT
        );
        boolean pickupVisible = pickup.sawDrop();
        boolean pickupDisappeared = pickup.becameAir();
        boolean inventoryExact = pickupVisible
            && pickupDisappeared
            && pickup.pickupRestored()
            && client.waitForInventoryCount(
                "minecraft:wooden_pickaxe",
                pickaxeCountBeforeRecovery + 1,
                INVENTORY_TIMEOUT
            );
        observations.add(
            "campfire death-drop recovery: " + (inventoryExact ? "passed" : "failed")
                + " item=minecraft:wooden_pickaxe"
                + " identity=" + death.woodenPickaxeDropIdentity()
                + " pickup_visible=" + pickupVisible
                + " pickup_disappeared=" + pickupDisappeared
                + " pickup_restored=" + pickup.pickupRestored()
                + " inventory_exact=" + inventoryExact
                + " expected_count=" + (pickaxeCountBeforeRecovery + 1)
        );
        return new ClientScenarioReport(inventoryExact ? "passed" : "failed", id, observations);
    }

    static CampfireDeathRespawnResult performEarnedCampfireDeathRespawn(
        String id,
        List<String> observations,
        ScenarioClient client,
        boolean observeWoodenPickaxeDrop
    ) throws Exception {
        CampfireCookingResult cooked = prepareEarnedCampfireCooking(id, observations, client, false);
        if (!"passed".equals(cooked.report().result())) {
            return new CampfireDeathRespawnResult(cooked.report(), cooked.campfireTarget(), null);
        }

        if (cooked.campfireTarget() == null) {
            observations.add("blocked: placed campfire target was not retained for hazard death probe");
            return new CampfireDeathRespawnResult(new ClientScenarioReport("blocked", id, observations), null, null);
        }
        ScenarioBlockTarget campfireTarget = new ScenarioBlockTarget(
            cooked.campfireTarget().x(),
            cooked.campfireTarget().y(),
            cooked.campfireTarget().z(),
            "up",
            "campfire-target",
            cooked.campfireTarget().blockId()
        );
        List<ScenarioItemDropIdentity> preexistingPickaxeIdentities = observeWoodenPickaxeDrop
            ? client.visibleItemDropIdentities("minecraft:wooden_pickaxe")
            : List.of();

        boolean deathScreen = client.standOnBlockUntilDeath(campfireTarget, CAMPFIRE_DEATH_TIMEOUT);
        observations.add(
            "campfire hazard death: " + (deathScreen ? "passed" : "failed")
                + " target=" + coordinates(campfireTarget)
                + " timeout_seconds=" + CAMPFIRE_DEATH_TIMEOUT.toSeconds()
        );
        if (!deathScreen) {
            return new CampfireDeathRespawnResult(
                new ClientScenarioReport("failed", id, observations),
                campfireTarget,
                null
            );
        }

        ScenarioItemDropIdentity woodenPickaxeDropIdentity = observeWoodenPickaxeDrop
            ? client.waitForNewVisibleItemDropIdentity(
                "minecraft:wooden_pickaxe",
                preexistingPickaxeIdentities,
                PICKUP_TIMEOUT
            )
            : null;
        boolean attributableDrop = !observeWoodenPickaxeDrop
            || (woodenPickaxeDropIdentity != null
                && !preexistingPickaxeIdentities.contains(woodenPickaxeDropIdentity));
        if (observeWoodenPickaxeDrop) {
            observations.add(
                "campfire wooden pickaxe death drop: " + (attributableDrop ? "passed" : "failed")
                    + " identity=" + (woodenPickaxeDropIdentity == null ? "missing" : woodenPickaxeDropIdentity)
                    + " preexisting_identities=" + preexistingPickaxeIdentities
            );
        }

        boolean respawned = client.performRespawn(RESPAWN_TIMEOUT);
        observations.add(
            "campfire respawn: " + (respawned ? "passed" : "failed")
                + " timeout_seconds=" + RESPAWN_TIMEOUT.toSeconds()
        );
        return new CampfireDeathRespawnResult(
            new ClientScenarioReport(respawned && attributableDrop ? "passed" : "failed", id, observations),
            campfireTarget,
            woodenPickaxeDropIdentity
        );
    }
}
