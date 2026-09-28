package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static dev.solaris.agent.javaagent.PlayableCoordinates.coordinates;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.BREAK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.CRAFTING_SCREEN;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.FURNACE_COOK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.FURNACE_SCREEN;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.PICKUP_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableResourceStage.STICK_RECIPE_RESULT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.APPROACH_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.INVENTORY_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.HOTBAR_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.BLOCK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.ENTITY_ATTACK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.ENTITY_SCAN_TIMEOUT;

/** Shared passive-food drop maps and single-mob collection. */
final class PlayableFoodStage {
    private PlayableFoodStage() {
    }

    private static final String WOODEN_HOE_RECIPE_RESULT = "minecraft:wooden_hoe";
    private static final String BREAD_RECIPE_RESULT = "minecraft:bread";
    private static final Duration HUNGER_DRAIN_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration FOOD_EAT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration CROP_GROWTH_TIMEOUT = Duration.ofMinutes(20);
    private static final Duration LIVESTOCK_MOTION_TIMEOUT = Duration.ofSeconds(90);
    private static final double LIVESTOCK_MIN_HORIZONTAL_DISTANCE = 1.0;
    private static final double COW_MIN_VERTICAL_RISE = 0.8;
    private static final double LIVESTOCK_MIN_HORIZONTAL_SPEED = 0.02;
    private static final double LIVESTOCK_MAX_HORIZONTAL_SPEED = 0.25;
    private static final double LIVESTOCK_MAX_YAW_DELTA = 15.0;

    static final Map<String, String> PASSIVE_FOOD_DROPS = Map.of(
        "minecraft:cow", "minecraft:beef",
        "minecraft:pig", "minecraft:porkchop",
        "minecraft:chicken", "minecraft:chicken"
    );

    static final Map<String, String> COOKED_PASSIVE_FOOD_RESULTS = Map.of(
        "minecraft:beef", "minecraft:cooked_beef",
        "minecraft:porkchop", "minecraft:cooked_porkchop",
        "minecraft:chicken", "minecraft:cooked_chicken"
    );

    static final List<String> PASSIVE_FOOD_ENTITY_IDS = List.of(
        "minecraft:cow",
        "minecraft:pig",
        "minecraft:chicken"
    );

    record PassiveFoodDropResult(ClientScenarioReport report, String dropItemId, int foodCountAfter) {}

    static PassiveFoodDropResult collectPassiveFoodDrop(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        ScenarioEntityObservation passive = client.findVisibleEntity(
            PASSIVE_FOOD_ENTITY_IDS,
            ScenarioReach.OUTSIDE_SURVIVAL_REACH,
            ENTITY_SCAN_TIMEOUT
        );
        if (passive == null) {
            passive = client.findVisibleEntity(
                PASSIVE_FOOD_ENTITY_IDS,
                ScenarioReach.WITHIN_SURVIVAL_REACH,
                ENTITY_SCAN_TIMEOUT
            );
        }
        if (passive == null) {
            observations.add("blocked: no loaded natural passive food mob visible to the real client");
            return new PassiveFoodDropResult(new ClientScenarioReport("blocked", id, observations), null, 0);
        }

        String dropItemId = PASSIVE_FOOD_DROPS.get(passive.entityType());
        if (dropItemId == null) {
            observations.add("blocked: passive mob has no embedded food drop mapping entity=" + passive.entityType());
            return new PassiveFoodDropResult(new ClientScenarioReport("blocked", id, observations), null, 0);
        }
        observations.add(
            "passive mob scan: passed"
                + " entity=" + passive.entityType()
                + " entity_id=" + passive.entityId()
                + " distance_squared=" + passive.distanceSquared()
                + " expected_drop=" + dropItemId
        );

        boolean approached = client.approachEntity(passive, APPROACH_TIMEOUT);
        observations.add(
            "passive mob approach: " + (approached ? "passed" : "failed")
                + " entity=" + passive.entityType()
                + " entity_id=" + passive.entityId()
        );
        if (!approached) {
            return new PassiveFoodDropResult(new ClientScenarioReport("blocked", id, observations), dropItemId, 0);
        }

        int foodCountBefore = client.inventoryCount(dropItemId);
        ScenarioBreakResult attack = client.attackEntityUntilDropCollected(
            passive,
            dropItemId,
            1,
            ENTITY_ATTACK_TIMEOUT
        );
        int foodCountAfter = client.inventoryCount(dropItemId);
        boolean collected = attack.started()
            && attack.becameAir()
            && attack.pickupRestored()
            && foodCountAfter >= foodCountBefore + 1;
        observations.add(
            "passive food drop: " + (collected ? "passed" : "failed")
                + " entity=" + passive.entityType()
                + " entity_id=" + passive.entityId()
                + " expected_drop=" + dropItemId
                + " attack_started=" + attack.started()
                + " entity_removed=" + attack.becameAir()
                + " saw_drop=" + attack.sawDrop()
                + " pickup_restored=" + attack.pickupRestored()
                + " food_count_before=" + foodCountBefore
                + " food_count_after=" + foodCountAfter
        );
        return new PassiveFoodDropResult(
            new ClientScenarioReport(collected ? "passed" : "failed", id, observations),
            dropItemId,
            foodCountAfter
        );
    }

    static ClientScenarioReport runPassiveFoodDrop(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        return PlayableFoodStage.collectPassiveFoodDrop(id, observations, client).report();
    }

    static ClientScenarioReport runPassiveLivestockMotion(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        boolean passed = true;
        for (String entityTypeId : List.of("minecraft:cow", "minecraft:sheep", "minecraft:chicken")) {
            ScenarioEntityObservation entity = client.findVisibleEntity(
                List.of(entityTypeId),
                ScenarioReach.OUTSIDE_SURVIVAL_REACH,
                ENTITY_SCAN_TIMEOUT
            );
            if (entity == null) {
                entity = client.findVisibleEntity(
                    List.of(entityTypeId),
                    ScenarioReach.WITHIN_SURVIVAL_REACH,
                    ENTITY_SCAN_TIMEOUT
                );
            }
            if (entity == null) {
                observations.add("blocked: no loaded natural livestock visible entity=" + entityTypeId);
                return new ClientScenarioReport("blocked", id, observations);
            }

            double minimumVerticalRise = "minecraft:cow".equals(entityTypeId) ? COW_MIN_VERTICAL_RISE : 0.0;
            ScenarioEntityMotionObservation motion = client.waitForEntityMotion(
                entity,
                LIVESTOCK_MIN_HORIZONTAL_DISTANCE,
                minimumVerticalRise,
                LIVESTOCK_MOTION_TIMEOUT
            );
            if (motion == null) {
                observations.add(
                    "livestock motion: failed entity=" + entityTypeId + " entity_id=" + entity.entityId()
                        + " reason=entity-disappeared"
                );
                passed = false;
                continue;
            }

            boolean moved = motion.horizontalDistance() >= LIVESTOCK_MIN_HORIZONTAL_DISTANCE;
            boolean climbed = motion.verticalRise() >= minimumVerticalRise;
            boolean speedMatched = motion.maxHorizontalSpeed() >= LIVESTOCK_MIN_HORIZONTAL_SPEED
                && motion.maxHorizontalSpeed() <= LIVESTOCK_MAX_HORIZONTAL_SPEED;
            boolean yawMatched = motion.minimumYawDelta() <= LIVESTOCK_MAX_YAW_DELTA;
            boolean entityPassed = moved && climbed && speedMatched && yawMatched;
            passed &= entityPassed;
            observations.add(
                "livestock motion: " + (entityPassed ? "passed" : "failed")
                    + " entity=" + entityTypeId
                    + " entity_id=" + entity.entityId()
                    + " horizontal_distance=" + motion.horizontalDistance()
                    + " vertical_rise=" + motion.verticalRise()
                    + " max_horizontal_speed=" + motion.maxHorizontalSpeed()
                    + " minimum_yaw_delta=" + motion.minimumYawDelta()
                    + " end=" + motion.endX() + "," + motion.endY() + "," + motion.endZ()
            );
        }
        return new ClientScenarioReport(passed ? "passed" : "failed", id, observations);
    }

    static ClientScenarioReport runEatPassiveFood(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        PlayableFoodStage.PassiveFoodDropResult drop = PlayableFoodStage.collectPassiveFoodDrop(id, observations, client);
        if (!"passed".equals(drop.report().result())) {
            return drop.report();
        }

        boolean hungerDrained = client.drainHungerBySprinting(HUNGER_DRAIN_TIMEOUT);
        observations.add("natural hunger drain: " + (hungerDrained ? "passed" : "failed"));
        if (!hungerDrained) {
            return new ClientScenarioReport("failed", id, observations);
        }

        int foodCountBefore = client.inventoryCount(drop.dropItemId());
        if (foodCountBefore < 1) {
            observations.add("passive food eating: failed no earned food item remained after hunger drain");
            return new ClientScenarioReport("failed", id, observations);
        }
        ScenarioHeldItem selectedFood = client.selectHotbarItem(drop.dropItemId(), 1, HOTBAR_TIMEOUT);
        if (!selectedFood.matches(drop.dropItemId(), 1)) {
            observations.add("blocked: earned passive food exists but is not selectable from hotbar");
            return new ClientScenarioReport("blocked", id, observations);
        }

        ScenarioFoodUseResult eaten = client.eatSelectedFood(drop.dropItemId(), foodCountBefore, FOOD_EAT_TIMEOUT);
        boolean passed = eaten.started()
            && eaten.foodBefore() < 20
            && eaten.foodAfter() > eaten.foodBefore()
            && eaten.itemCountAfter() < eaten.itemCountBefore();
        observations.add(
            "passive food eating: " + (passed ? "passed" : "failed")
                + " item=" + drop.dropItemId()
                + " started=" + eaten.started()
                + " food_before=" + eaten.foodBefore()
                + " food_after=" + eaten.foodAfter()
                + " item_count_before=" + eaten.itemCountBefore()
                + " item_count_after=" + eaten.itemCountAfter()
        );
        return new ClientScenarioReport(passed ? "passed" : "failed", id, observations);
    }

    static ClientScenarioReport runRenewableWheatBread(
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

        int containerId = client.activeContainerId();
        int planksBeforeSticks = client.inventoryCount(planks.planks().planksItemId());
        int sticksBefore = client.inventoryCount("minecraft:stick");
        if (planksBeforeSticks < 2) {
            observations.add("renewable food stick recipe: failed fewer than two earned planks available");
            return new ClientScenarioReport("failed", id, observations);
        }
        client.placeRecipe(containerId, client.requireRecipeDisplayId(STICK_RECIPE_RESULT), false);
        boolean stickPlanksConsumed = client.waitForInventoryCount(
            planks.planks().planksItemId(),
            planksBeforeSticks - 2,
            INVENTORY_TIMEOUT
        );
        boolean sticksCreated = client.waitForInventoryCount(
            "minecraft:stick",
            sticksBefore + 4,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "renewable food stick recipe: " + (stickPlanksConsumed && sticksCreated ? "passed" : "failed")
        );
        if (!stickPlanksConsumed || !sticksCreated) {
            return new ClientScenarioReport("failed", id, observations);
        }

        int planksBeforeHoe = client.inventoryCount(planks.planks().planksItemId());
        int sticksBeforeHoe = client.inventoryCount("minecraft:stick");
        int hoesBefore = client.inventoryCount("minecraft:wooden_hoe");
        if (planksBeforeHoe < 2 || sticksBeforeHoe < 2) {
            observations.add("wooden hoe recipe: failed missing earned planks or sticks");
            return new ClientScenarioReport("failed", id, observations);
        }
        client.placeRecipe(containerId, client.requireRecipeDisplayId(WOODEN_HOE_RECIPE_RESULT), false);
        boolean hoePlanksConsumed = client.waitForInventoryCount(
            planks.planks().planksItemId(),
            planksBeforeHoe - 2,
            INVENTORY_TIMEOUT
        );
        boolean hoeSticksConsumed = client.waitForInventoryCount(
            "minecraft:stick",
            sticksBeforeHoe - 2,
            INVENTORY_TIMEOUT
        );
        boolean hoeCreated = client.waitForInventoryCount(
            "minecraft:wooden_hoe",
            hoesBefore + 1,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "wooden hoe recipe: " + (hoePlanksConsumed && hoeSticksConsumed && hoeCreated ? "passed" : "failed")
                + " recipe_result=" + WOODEN_HOE_RECIPE_RESULT
        );
        if (!hoePlanksConsumed || !hoeSticksConsumed || !hoeCreated) {
            return new ClientScenarioReport("failed", id, observations);
        }
        if (!client.closeCurrentScreen(INVENTORY_TIMEOUT)) {
            observations.add("renewable food crafting screen close: failed");
            return new ClientScenarioReport("failed", id, observations);
        }

        List<ScenarioBlockTarget> crops = new ArrayList<>();
        for (int plot = 1; plot <= 3; plot++) {
            ScenarioBlockTarget grass = client.findBreakableBlock(
                List.of("minecraft:short_grass"),
                ScenarioReach.WITHIN_SURVIVAL_REACH
            );
            if (grass == null) {
                ScenarioBlockTarget farGrass = client.findBreakableBlock(
                    List.of("minecraft:short_grass"),
                    ScenarioReach.OUTSIDE_SURVIVAL_REACH
                );
                if (farGrass == null || !client.approachBlock(farGrass, APPROACH_TIMEOUT)) {
                    observations.add("blocked: no reachable generated short grass for plot=" + plot);
                    return new ClientScenarioReport("blocked", id, observations);
                }
                grass = client.findBreakableBlock(
                    List.of("minecraft:short_grass"),
                    ScenarioReach.WITHIN_SURVIVAL_REACH
                );
            }
            if (grass == null || !client.approachBlock(grass, APPROACH_TIMEOUT)) {
                observations.add("blocked: generated short grass remained outside reach for plot=" + plot);
                return new ClientScenarioReport("blocked", id, observations);
            }

            int seedsBefore = client.inventoryCount("minecraft:wheat_seeds");
            ScenarioBreakResult grassBreak = client.breakBlockUntilDropVisible(
                grass,
                "minecraft:wheat_seeds",
                BREAK_TIMEOUT
            );
            ScenarioBreakResult seedPickup = client.collectVisibleItemDrop(
                grass,
                "minecraft:wheat_seeds",
                1,
                PICKUP_TIMEOUT
            );
            int seedsAfter = client.inventoryCount("minecraft:wheat_seeds");
            boolean seedEarned = grassBreak.started()
                && grassBreak.becameAir()
                && grassBreak.sawDrop()
                && seedPickup.pickupRestored()
                && seedsAfter >= seedsBefore + 1;
            observations.add(
                "wheat seed source plot=" + plot + ": " + (seedEarned ? "passed" : "failed")
                    + " target=" + coordinates(grass)
                    + " seeds_before=" + seedsBefore
                    + " seeds_after=" + seedsAfter
            );
            if (!seedEarned) {
                return new ClientScenarioReport("failed", id, observations);
            }

            ScenarioBlockPair soil = client.findTillableSoil(ScenarioReach.WITHIN_SURVIVAL_REACH);
            if (soil == null || !"up".equals(soil.clicked().face())) {
                observations.add("blocked: no reachable natural tillable soil for plot=" + plot);
                return new ClientScenarioReport("blocked", id, observations);
            }
            ScenarioHeldItem hoe = client.selectHotbarItem("minecraft:wooden_hoe", 1, HOTBAR_TIMEOUT);
            if (!hoe.matches("minecraft:wooden_hoe", 1)) {
                observations.add("blocked: earned wooden hoe is not selectable for plot=" + plot);
                return new ClientScenarioReport("blocked", id, observations);
            }
            ScenarioUseResult tillUse = client.useItemOn(soil.clicked(), hoe);
            boolean tilled = client.waitForBlock(soil.clicked(), "minecraft:farmland", BLOCK_TIMEOUT);
            ScenarioBlockTarget farmland = new ScenarioBlockTarget(
                soil.clicked().x(),
                soil.clicked().y(),
                soil.clicked().z(),
                "up",
                "renewable-wheat-farmland-" + plot,
                "minecraft:farmland"
            );

            int seedsBeforePlant = client.inventoryCount("minecraft:wheat_seeds");
            ScenarioHeldItem seeds = client.selectHotbarItem("minecraft:wheat_seeds", 1, HOTBAR_TIMEOUT);
            if (!seeds.matches("minecraft:wheat_seeds", 1)) {
                observations.add("blocked: earned wheat seed is not selectable for plot=" + plot);
                return new ClientScenarioReport("blocked", id, observations);
            }
            ScenarioUseResult plantUse = client.useItemOn(farmland, seeds);
            ScenarioBlockTarget crop = new ScenarioBlockTarget(
                soil.target().x(),
                soil.target().y(),
                soil.target().z(),
                "up",
                "renewable-wheat-crop-" + plot,
                "minecraft:wheat"
            );
            boolean planted = client.waitForBlock(crop, "minecraft:wheat", BLOCK_TIMEOUT);
            boolean seedConsumed = client.waitForInventoryCount(
                "minecraft:wheat_seeds",
                seedsBeforePlant - 1,
                INVENTORY_TIMEOUT
            );
            observations.add(
                "wheat planting plot=" + plot + ": " + (tilled && planted && seedConsumed ? "passed" : "failed")
                    + " till_use=" + tillUse.result()
                    + " plant_use=" + plantUse.result()
            );
            if (!tilled || !planted || !seedConsumed) {
                return new ClientScenarioReport("failed", id, observations);
            }
            crops.add(crop);
        }

        for (int index = 0; index < crops.size(); index++) {
            boolean mature = client.waitForBlockProperty(
                crops.get(index),
                "age",
                "7",
                CROP_GROWTH_TIMEOUT
            );
            observations.add(
                "wheat natural growth plot=" + (index + 1) + ": " + (mature ? "passed" : "failed")
            );
            if (!mature) {
                return new ClientScenarioReport("failed", id, observations);
            }
            ScenarioLightLevel light = client.lightLevel(crops.get(index));
            boolean growthLit = Math.max(light.sky(), light.block()) >= 9;
            observations.add(
                "wheat client light plot=" + (index + 1) + ": "
                    + (growthLit ? "passed" : "failed")
                    + " sky=" + light.sky()
                    + " block=" + light.block()
            );
            if (!growthLit) {
                return new ClientScenarioReport("failed", id, observations);
            }
        }

        for (int index = 0; index < crops.size(); index++) {
            ScenarioBlockTarget crop = crops.get(index);
            ScenarioBlockTarget support = new ScenarioBlockTarget(
                crop.x(),
                crop.y() - 1,
                crop.z(),
                "up",
                "renewable-wheat-support-" + (index + 1),
                "minecraft:farmland"
            );
            if (!client.approachBlock(support, APPROACH_TIMEOUT)) {
                observations.add("blocked: mature wheat support remained outside reach plot=" + (index + 1));
                return new ClientScenarioReport("blocked", id, observations);
            }
            int wheatBefore = client.inventoryCount("minecraft:wheat");
            ScenarioBreakResult cropBreak = client.breakBlockUntilDropVisible(
                crop,
                "minecraft:wheat",
                BREAK_TIMEOUT
            );
            ScenarioBreakResult wheatPickup = client.collectVisibleItemDrop(
                crop,
                "minecraft:wheat",
                1,
                PICKUP_TIMEOUT
            );
            int wheatAfter = client.inventoryCount("minecraft:wheat");
            boolean harvested = cropBreak.started()
                && cropBreak.becameAir()
                && cropBreak.sawDrop()
                && wheatPickup.pickupRestored()
                && wheatAfter >= wheatBefore + 1;
            observations.add(
                "wheat harvest plot=" + (index + 1) + ": " + (harvested ? "passed" : "failed")
                    + " wheat_before=" + wheatBefore
                    + " wheat_after=" + wheatAfter
            );
            if (!harvested) {
                return new ClientScenarioReport("failed", id, observations);
            }
        }

        if (!client.approachBlock(table.tableTarget(), APPROACH_TIMEOUT)) {
            observations.add("blocked: crafting table remained outside reach after harvest");
            return new ClientScenarioReport("blocked", id, observations);
        }
        ScenarioHeldItem hoe = client.selectHotbarItem("minecraft:wooden_hoe", 1, HOTBAR_TIMEOUT);
        ScenarioUseResult openUse = client.useItemOn(PlayableCoordinates.reachableUseTarget(client, table.tableTarget()), hoe);
        boolean opened = client.waitForScreenClassName(CRAFTING_SCREEN, INVENTORY_TIMEOUT);
        observations.add(
            "crafting table reopen for bread: " + (opened ? "passed" : "failed")
                + " open_use_result=" + openUse.result()
        );
        if (!opened) {
            return new ClientScenarioReport("failed", id, observations);
        }

        containerId = client.activeContainerId();
        int wheatBeforeBread = client.inventoryCount("minecraft:wheat");
        int breadBefore = client.inventoryCount("minecraft:bread");
        if (wheatBeforeBread < 3) {
            observations.add("bread recipe: failed fewer than three earned wheat available");
            return new ClientScenarioReport("failed", id, observations);
        }
        client.placeRecipe(containerId, client.requireRecipeDisplayId(BREAD_RECIPE_RESULT), false);
        boolean wheatConsumed = client.waitForInventoryCount(
            "minecraft:wheat",
            wheatBeforeBread - 3,
            INVENTORY_TIMEOUT
        );
        boolean breadCreated = client.waitForInventoryCount(
            "minecraft:bread",
            breadBefore + 1,
            INVENTORY_TIMEOUT
        );
        boolean closed = client.closeCurrentScreen(INVENTORY_TIMEOUT);
        observations.add(
            "bread recipe: " + (wheatConsumed && breadCreated && closed ? "passed" : "failed")
                + " recipe_result=" + BREAD_RECIPE_RESULT
        );
        if (!wheatConsumed || !breadCreated || !closed) {
            return new ClientScenarioReport("failed", id, observations);
        }

        observations.add("renewable bread ready: passed count=" + (breadBefore + 1));
        return new ClientScenarioReport("passed", id, observations);
    }

    static ClientScenarioReport runCookedPassiveFood(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        PlayableResourceStage.WoodenToolTableResult prepared = PlayableResourceStage.prepareWoodenToolAndTable(id, observations, client);
        if (!"passed".equals(prepared.report().result())) {
            return prepared.report();
        }

        PlayableFoodStage.PassiveFoodDropResult drop = PlayableFoodStage.collectPassiveFoodDrop(id, observations, client);
        if (!"passed".equals(drop.report().result())) {
            return drop.report();
        }
        String cookedItemId = PlayableFoodStage.COOKED_PASSIVE_FOOD_RESULTS.get(drop.dropItemId());
        if (cookedItemId == null) {
            observations.add("blocked: passive food drop has no cooked furnace result item=" + drop.dropItemId());
            return new ClientScenarioReport("blocked", id, observations);
        }

        PlayableHeatStage.FurnacePlacementOpenResult placedFurnace = PlayableHeatStage.finishFurnacePlacementOpen(
            id,
            observations,
            client,
            prepared.planks(),
            prepared.tableTarget()
        );
        if (!"passed".equals(placedFurnace.report().result())) {
            return placedFurnace.report();
        }

        PlayableHeatStage.FurnaceCharcoalSmeltResult charcoal = PlayableHeatStage.smeltCharcoalInPlacedFurnace(id, observations, client, placedFurnace);
        if (!"passed".equals(charcoal.report().result())) {
            return charcoal.report();
        }

        boolean furnaceApproached = client.approachBlock(charcoal.furnaceTarget(), APPROACH_TIMEOUT);
        observations.add(
            "furnace approach for cooked food: " + (furnaceApproached ? "passed" : "failed")
                + " target=" + coordinates(charcoal.furnaceTarget())
        );
        if (!furnaceApproached) {
            return new ClientScenarioReport("blocked", id, observations);
        }

        ScenarioBlockTarget furnaceUseTarget = PlayableCoordinates.reachableUseTarget(client, charcoal.furnaceTarget());
        ScenarioUseResult openUse = client.useItemOn(furnaceUseTarget, client.selectedItem());
        boolean opened = client.waitForScreenClassName(FURNACE_SCREEN, INVENTORY_TIMEOUT);
        observations.add(
            "furnace reopen for cooked food: " + (opened ? "passed" : "failed")
                + " open_use_result=" + openUse.result()
                + " screen=" + FURNACE_SCREEN
                + " screen_matched=" + opened
        );
        if (!opened) {
            return new ClientScenarioReport("failed", id, observations);
        }

        boolean inputMoved = client.moveSelectedItemToContainerSlot(
            0,
            drop.dropItemId(),
            1,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "cooked passive food input transfer: " + (inputMoved ? "passed" : "failed")
                + " slot=0 item=" + drop.dropItemId()
        );
        if (!inputMoved) {
            return new ClientScenarioReport("failed", id, observations);
        }

        boolean fuelMoved = client.moveSelectedItemToContainerSlot(
            1,
            "minecraft:charcoal",
            1,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "cooked passive food fuel transfer: " + (fuelMoved ? "passed" : "failed")
                + " slot=1 item=minecraft:charcoal"
        );
        if (!fuelMoved) {
            return new ClientScenarioReport("failed", id, observations);
        }

        boolean outputReady = client.waitForContainerSlot(2, cookedItemId, 1, FURNACE_COOK_TIMEOUT);
        observations.add(
            "cooked passive food output: " + (outputReady ? "passed" : "failed")
                + " slot=2 item=" + cookedItemId
        );
        if (!outputReady) {
            return new ClientScenarioReport("failed", id, observations);
        }

        boolean outputTaken = client.moveContainerSlotToInventory(
            2,
            cookedItemId,
            1,
            INVENTORY_TIMEOUT
        );
        boolean cookedInInventory = client.waitForInventoryCount(cookedItemId, 1, INVENTORY_TIMEOUT);
        boolean closed = client.closeCurrentScreen(INVENTORY_TIMEOUT);
        observations.add(
            "cooked passive food inventory: "
                + (outputTaken && cookedInInventory && closed ? "passed" : "failed")
                + " output_taken=" + outputTaken
                + " cooked_inventory_matched=" + cookedInInventory
                + " closed=" + closed
        );
        if (!outputTaken || !cookedInInventory || !closed) {
            return new ClientScenarioReport("failed", id, observations);
        }

        boolean hungerDrained = client.drainHungerBySprinting(HUNGER_DRAIN_TIMEOUT);
        observations.add("natural hunger drain for cooked food: " + (hungerDrained ? "passed" : "failed"));
        if (!hungerDrained) {
            return new ClientScenarioReport("failed", id, observations);
        }

        int foodCountBefore = client.inventoryCount(cookedItemId);
        ScenarioHeldItem selectedFood = client.selectHotbarItem(cookedItemId, 1, HOTBAR_TIMEOUT);
        if (!selectedFood.matches(cookedItemId, 1)) {
            observations.add("blocked: cooked passive food exists but is not selectable from hotbar");
            return new ClientScenarioReport("blocked", id, observations);
        }
        ScenarioFoodUseResult eaten = client.eatSelectedFood(cookedItemId, foodCountBefore, FOOD_EAT_TIMEOUT);
        boolean passed = eaten.started()
            && eaten.foodBefore() < 20
            && eaten.foodAfter() > eaten.foodBefore()
            && eaten.itemCountAfter() < eaten.itemCountBefore();
        observations.add(
            "cooked passive food eating: " + (passed ? "passed" : "failed")
                + " item=" + cookedItemId
                + " raw_item=" + drop.dropItemId()
                + " started=" + eaten.started()
                + " food_before=" + eaten.foodBefore()
                + " food_after=" + eaten.foodAfter()
                + " item_count_before=" + eaten.itemCountBefore()
                + " item_count_after=" + eaten.itemCountAfter()
        );
        return new ClientScenarioReport(passed ? "passed" : "failed", id, observations);
    }
}
