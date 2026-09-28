package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;

import java.time.Duration;
import java.util.List;

import static dev.solaris.agent.javaagent.PlayableCoordinates.coordinates;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.APPROACH_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.BLOCK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.ENTITY_ATTACK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.ENTITY_SCAN_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.HOTBAR_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.INVENTORY_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.NIGHT_START_DAY_TIME;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.NIGHT_WAIT_TIMEOUT;

/** Earned bed: wool collection, bed recipe, placement, and night sleep. */
final class PlayableRestStage {
    private PlayableRestStage() {
    }

        private static final String EARNED_BED_WOOL_ITEM_ID = "minecraft:white_wool";
    private static final String EARNED_BED_ITEM_ID = "minecraft:white_bed";
    private static final Duration MORNING_WAIT_TIMEOUT = Duration.ofSeconds(8);
    private static final List<String> SHEEP_WOOL_ENTITY_IDS = List.of(
        "minecraft:sheep"
    );

    private record BedRecipe(String woolItemId, String bedItemId) {}

    private record WoolCollectionResult(ClientScenarioReport report, BedRecipe bedRecipe) {}

    static ClientScenarioReport runEarnedBedSleep(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        PlayableResourceStage.LogToPlanksResult planks = PlayableResourceStage.runLogToPlanks(id, observations, client, 2, true, false);
        if (!"passed".equals(planks.report().result())) {
            return planks.report();
        }

        WoolCollectionResult wool = collectSheepWool(id, observations, client, 3);
        if (!"passed".equals(wool.report().result())) {
            return wool.report();
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

        ClientScenarioReport bed = craftBedInOpenTable(
            id,
            observations,
            client,
            planks.planks().planksItemId(),
            wool.bedRecipe()
        );
        if (!"passed".equals(bed.result())) {
            return bed;
        }

        return placeAndSleepInEarnedBed(id, observations, client, wool.bedRecipe().bedItemId());
    }

    static WoolCollectionResult collectSheepWool(
        String id,
        List<String> observations,
        ScenarioClient client,
        int targetWoolCount
    ) throws Exception {
        ScenarioEntityObservation firstSheep = client.findVisibleEntity(
            SHEEP_WOOL_ENTITY_IDS,
            ScenarioReach.OUTSIDE_SURVIVAL_REACH,
            ENTITY_SCAN_TIMEOUT
        );
        if (firstSheep == null) {
            firstSheep = client.findVisibleEntity(
                SHEEP_WOOL_ENTITY_IDS,
                ScenarioReach.WITHIN_SURVIVAL_REACH,
                ENTITY_SCAN_TIMEOUT
            );
        }
        int woolBefore = client.inventoryCount(EARNED_BED_WOOL_ITEM_ID);
        int requiredWoolCount = woolBefore + targetWoolCount;
        ScenarioEntityObservation sheep = firstSheep != null
            && EARNED_BED_WOOL_ITEM_ID.equals(firstSheep.sheepWoolItemId())
            ? firstSheep
            : null;
        if (firstSheep != null && sheep == null) {
            observations.add(
                "sheep wool scan: skipped"
                    + " entity=" + firstSheep.entityType()
                    + " entity_id=" + firstSheep.entityId()
                    + " wool_item=" + firstSheep.sheepWoolItemId()
                    + " required_wool_item=" + EARNED_BED_WOOL_ITEM_ID
            );
        }
        while (client.inventoryCount(EARNED_BED_WOOL_ITEM_ID) < requiredWoolCount) {
            if (sheep == null) {
                sheep = client.findVisibleSheepWithWool(
                    EARNED_BED_WOOL_ITEM_ID,
                    ScenarioReach.OUTSIDE_SURVIVAL_REACH,
                    ENTITY_SCAN_TIMEOUT
                );
            }
            if (sheep == null) {
                sheep = client.findVisibleSheepWithWool(
                    EARNED_BED_WOOL_ITEM_ID,
                    ScenarioReach.WITHIN_SURVIVAL_REACH,
                    ENTITY_SCAN_TIMEOUT
                );
            }
            if (sheep == null) {
                observations.add("blocked: no loaded natural sheep visible with wool=" + EARNED_BED_WOOL_ITEM_ID);
                return new WoolCollectionResult(new ClientScenarioReport("blocked", id, observations), null);
            }
            observations.add(
                "sheep wool scan: passed"
                    + " entity=" + sheep.entityType()
                    + " entity_id=" + sheep.entityId()
                    + " wool_item=" + EARNED_BED_WOOL_ITEM_ID
                    + " distance_squared=" + sheep.distanceSquared()
            );

            boolean approached = client.approachEntity(sheep, APPROACH_TIMEOUT);
            observations.add(
                "sheep wool approach: " + (approached ? "passed" : "failed")
                    + " entity_id=" + sheep.entityId()
            );
            if (!approached) {
                return new WoolCollectionResult(new ClientScenarioReport("blocked", id, observations), null);
            }

            int woolCountBeforeAttack = client.inventoryCount(EARNED_BED_WOOL_ITEM_ID);
            ScenarioBreakResult attack = client.attackEntityUntilDropCollected(
                sheep,
                EARNED_BED_WOOL_ITEM_ID,
                1,
                ENTITY_ATTACK_TIMEOUT
            );
            int woolCountAfterAttack = client.inventoryCount(EARNED_BED_WOOL_ITEM_ID);
            boolean collected = attack.started()
                && attack.becameAir()
                && attack.pickupRestored()
                && woolCountAfterAttack >= woolCountBeforeAttack + 1;
            observations.add(
                "sheep wool drop: " + (collected ? "passed" : "failed")
                    + " entity_id=" + sheep.entityId()
                    + " attack_started=" + attack.started()
                    + " entity_removed=" + attack.becameAir()
                    + " saw_drop=" + attack.sawDrop()
                    + " pickup_restored=" + attack.pickupRestored()
                    + " wool_count_before=" + woolCountBeforeAttack
                    + " wool_count_after=" + woolCountAfterAttack
                    + " wool_required=" + requiredWoolCount
            );
            if (!collected) {
                return new WoolCollectionResult(new ClientScenarioReport("failed", id, observations), null);
            }
            sheep = null;
        }

        observations.add(
            "earned wool total: passed item=" + EARNED_BED_WOOL_ITEM_ID + " count="
                + client.inventoryCount(EARNED_BED_WOOL_ITEM_ID)
                + " required=" + requiredWoolCount
        );
        BedRecipe bedRecipe = new BedRecipe(
            EARNED_BED_WOOL_ITEM_ID,
            EARNED_BED_ITEM_ID
        );
        return new WoolCollectionResult(new ClientScenarioReport("passed", id, observations), bedRecipe);
    }

    static ClientScenarioReport craftBedInOpenTable(
        String id,
        List<String> observations,
        ScenarioClient client,
        String planksItemId,
        BedRecipe bedRecipe
    ) throws Exception {
        int containerId = client.activeContainerId();
        int planksCount = client.inventoryCount(planksItemId);
        int woolCount = client.inventoryCount(bedRecipe.woolItemId());
        int bedCount = client.inventoryCount(bedRecipe.bedItemId());
        if (planksCount < 3 || woolCount < 3) {
            observations.add("bed recipe: failed missing earned planks or wool=" + bedRecipe.woolItemId());
            return new ClientScenarioReport("failed", id, observations);
        }
        int expectedPlanksCount = planksCount - 3;
        int expectedWoolCount = woolCount - 3;
        int expectedBedCount = bedCount + 1;
        client.placeRecipe(containerId, client.requireRecipeDisplayId(bedRecipe.bedItemId()), false);
        boolean planksConsumed = client.waitForInventoryCount(
            planksItemId,
            expectedPlanksCount,
            INVENTORY_TIMEOUT
        );
        boolean woolConsumed = client.waitForInventoryCount(
            bedRecipe.woolItemId(),
            expectedWoolCount,
            INVENTORY_TIMEOUT
        );
        boolean bedCreated = client.waitForInventoryCount(
            bedRecipe.bedItemId(),
            expectedBedCount,
            INVENTORY_TIMEOUT
        );
        boolean closed = client.closeCurrentScreen(INVENTORY_TIMEOUT);
        boolean passed = planksConsumed && woolConsumed && bedCreated && closed;
        observations.add(
            "bed recipe: " + (passed ? "passed" : "failed")
                + " container_id=" + containerId
                + " recipe_result=" + bedRecipe.bedItemId()
                + " planks_item=" + planksItemId
                + " wool_item=" + bedRecipe.woolItemId()
                + " bed_item=" + bedRecipe.bedItemId()
                + " planks_expected_count=" + expectedPlanksCount
                + " planks_count_matched=" + planksConsumed
                + " wool_expected_count=" + expectedWoolCount
                + " wool_count_matched=" + woolConsumed
                + " bed_expected_count=" + expectedBedCount
                + " bed_count_matched=" + bedCreated
                + " closed=" + closed
        );
        return new ClientScenarioReport(passed ? "passed" : "failed", id, observations);
    }

    static ClientScenarioReport placeAndSleepInEarnedBed(
        String id,
        List<String> observations,
        ScenarioClient client,
        String bedItemId
    ) throws Exception {
        ScenarioHeldItem bed = client.selectHotbarItem(bedItemId, 1, HOTBAR_TIMEOUT);
        if (!bed.matches(bedItemId, 1)) {
            observations.add("blocked: earned bed exists but is not selectable from hotbar item=" + bedItemId);
            return new ClientScenarioReport("blocked", id, observations);
        }
        ScenarioBlockPair pair = client.findDryPlaceablePair(ScenarioReach.WITHIN_SURVIVAL_REACH);
        if (pair == null) {
            observations.add("blocked: no loaded dry target found for earned bed placement");
            return new ClientScenarioReport("blocked", id, observations);
        }

        ScenarioUseResult placeUse = client.useItemOn(pair.clicked(), bed);
        boolean placed = client.waitForBlock(pair.target(), bedItemId, BLOCK_TIMEOUT);
        ScenarioBlockTarget bedTarget = new ScenarioBlockTarget(
            pair.target().x(),
            pair.target().y(),
            pair.target().z(),
            "up",
            "bed-target",
            bedItemId
        );
        observations.add(
            "bed placement: " + (placed ? "passed" : "failed")
                + " place_use_result=" + placeUse.result()
                + " target=" + coordinates(bedTarget)
        );
        if (!placed) {
            return new ClientScenarioReport("failed", id, observations);
        }

        boolean nightReached = client.waitForDayTimeAtOrAfter(NIGHT_START_DAY_TIME, NIGHT_WAIT_TIMEOUT);
        observations.add(
            "natural night wait: " + (nightReached ? "passed" : "failed")
                + " night_start_day_time=" + NIGHT_START_DAY_TIME
        );
        if (!nightReached) {
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioUseResult sleepUse = client.useItemOn(bedTarget, client.selectedItem());
        boolean morningReached = client.waitForDayTimeBelow(NIGHT_START_DAY_TIME, MORNING_WAIT_TIMEOUT);
        observations.add(
            "bed sleep skip: " + (morningReached ? "passed" : "failed")
                + " sleep_use_result=" + sleepUse.result()
                + " morning_day_time_below=" + NIGHT_START_DAY_TIME
        );
        return new ClientScenarioReport(morningReached ? "passed" : "failed", id, observations);
    }
}
