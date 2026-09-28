package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;

import java.util.List;
import java.util.Map;

import static dev.solaris.agent.javaagent.PlayableCoordinates.coordinates;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.APPROACH_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.BLOCK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.BREAK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.CRAFTING_SCREEN;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.CHEST_STORAGE_SAVE_RESTART_BEFORE_ID;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.EARNED_CHEST_STORAGE_ID;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.HOTBAR_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.INVENTORY_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.PICKUP_TIMEOUT;

/** Natural log collection, log-to-planks recipe, and crafting-table place/open spine. */
final class PlayableResourceStage {
    private PlayableResourceStage() {
    }

    private static final Map<String, PlanksRecipe> PLANKS_BY_LOG = Map.ofEntries(
        Map.entry("minecraft:acacia_log", new PlanksRecipe("minecraft:acacia_log", "minecraft:acacia_planks")),
        Map.entry("minecraft:birch_log", new PlanksRecipe("minecraft:birch_log", "minecraft:birch_planks")),
        Map.entry("minecraft:cherry_log", new PlanksRecipe("minecraft:cherry_log", "minecraft:cherry_planks")),
        Map.entry(
            "minecraft:dark_oak_log",
            new PlanksRecipe("minecraft:dark_oak_log", "minecraft:dark_oak_planks")
        ),
        Map.entry("minecraft:jungle_log", new PlanksRecipe("minecraft:jungle_log", "minecraft:jungle_planks")),
        Map.entry(
            "minecraft:mangrove_log",
            new PlanksRecipe("minecraft:mangrove_log", "minecraft:mangrove_planks")
        ),
        Map.entry("minecraft:oak_log", new PlanksRecipe("minecraft:oak_log", "minecraft:oak_planks")),
        Map.entry(
            "minecraft:pale_oak_log",
            new PlanksRecipe("minecraft:pale_oak_log", "minecraft:pale_oak_planks")
        ),
        Map.entry("minecraft:spruce_log", new PlanksRecipe("minecraft:spruce_log", "minecraft:spruce_planks"))
    );

    static final List<String> SUPPORTED_LOG_BLOCK_IDS = List.of(
        "minecraft:oak_log",
        "minecraft:spruce_log",
        "minecraft:birch_log",
        "minecraft:jungle_log",
        "minecraft:acacia_log",
        "minecraft:dark_oak_log",
        "minecraft:mangrove_log",
        "minecraft:cherry_log",
        "minecraft:pale_oak_log"
    );

    private static final String CRAFTING_TABLE_RECIPE_RESULT = "minecraft:crafting_table";

    record PlanksRecipe(String logItemId, String planksItemId) {}

    record LogToPlanksResult(ClientScenarioReport report, PlanksRecipe planks) {}

    record CraftingTableOpenResult(ClientScenarioReport report, ScenarioBlockTarget tableTarget) {
    }

    static LogToPlanksResult runLogToPlanks(
        String id,
        List<String> observations,
        ScenarioClient client,
        int targetLogCount,
        boolean useMaxItems,
        boolean terminal
    ) throws Exception {
        return runLogToPlanks(id, observations, client, targetLogCount, useMaxItems, terminal, true);
    }

    static LogToPlanksResult runLogToPlanks(
        String id,
        List<String> observations,
        ScenarioClient client,
        int targetLogCount,
        boolean useMaxItems,
        boolean terminal,
        boolean allowFarApproach
    ) throws Exception {
        PlanksRecipe planks = null;
        int collected = 0;
        int attempts = 0;
        int maxAttempts = Math.max(3, targetLogCount * 4);
        while (collected < targetLogCount && attempts < maxAttempts) {
            attempts++;
            List<String> candidateLogs = planks == null ? SUPPORTED_LOG_BLOCK_IDS : List.of(planks.logItemId());
            ScenarioBlockTarget log = client.findBreakableBlock(
                candidateLogs,
                ScenarioReach.WITHIN_SURVIVAL_REACH
            );
            if (log == null) {
                if (!allowFarApproach) {
                    observations.add("blocked: no nearby supported natural log found during survival soak");
                    return new LogToPlanksResult(new ClientScenarioReport("blocked", id, observations), planks);
                }
                ScenarioBlockTarget farLog = client.findBreakableBlock(
                    candidateLogs,
                    ScenarioReach.OUTSIDE_SURVIVAL_REACH
                );
                if (farLog == null) {
                    observations.add("blocked: no loaded supported natural log found near the real client");
                    return new LogToPlanksResult(new ClientScenarioReport("blocked", id, observations), planks);
                }
                boolean approached = client.approachBlock(farLog, APPROACH_TIMEOUT);
                observations.add(
                    "natural log approach: " + (approached ? "passed" : "failed")
                        + " target=" + coordinates(farLog)
                );
                log = client.findBreakableBlock(candidateLogs, ScenarioReach.WITHIN_SURVIVAL_REACH);
                if (!approached) {
                    if (log == null) {
                        return new LogToPlanksResult(new ClientScenarioReport("blocked", id, observations), planks);
                    }
                    observations.add(
                        "natural log reachable fallback after failed approach: passed"
                            + " target=" + coordinates(log)
                    );
                } else if (log == null) {
                    observations.add("blocked: supported natural log remained outside survival reach after approach");
                    return new LogToPlanksResult(new ClientScenarioReport("blocked", id, observations), planks);
                }
            }
            boolean closeApproached = true;
            if (usesReachOnlyLogClose(id) || "down".equals(log.face())) {
                observations.add(
                    "natural log close approach: skipped target=" + coordinates(log)
                        + " reason=already_within_survival_reach"
                );
            } else {
                closeApproached = client.approachBlock(log, APPROACH_TIMEOUT);
                observations.add(
                    "natural log close approach: " + (closeApproached ? "passed" : "failed")
                        + " target=" + coordinates(log)
                );
                if (!closeApproached) {
                    return new LogToPlanksResult(new ClientScenarioReport("blocked", id, observations), planks);
                }
            }
            PlanksRecipe detected = PLANKS_BY_LOG.get(log.blockId());
            if (detected == null) {
                observations.add("blocked: loaded log has no embedded planks recipe mapping block_id=" + log.blockId());
                return new LogToPlanksResult(new ClientScenarioReport("blocked", id, observations), planks);
            }
            if (planks == null) {
                planks = detected;
            }

            ScenarioBreakResult broke = client.breakBlockUntilDropVisible(log, log.blockId(), BREAK_TIMEOUT);
            ScenarioBreakResult pickup = client.collectVisibleItemDrop(log, log.blockId(), 1, PICKUP_TIMEOUT);
            boolean naturalPickup = broke.started()
                && broke.becameAir()
                && broke.sawDrop()
                && pickup.pickupRestored();
            observations.add(
                "natural log break/drop/pickup: " + (naturalPickup ? "passed" : "failed")
                    + " target=" + coordinates(log)
                    + " break_started=" + broke.started()
                    + " became_air=" + broke.becameAir()
                    + " saw_drop=" + broke.sawDrop()
                    + " pickup_restored=" + pickup.pickupRestored()
                    + " held=" + pickup.selectedItem().itemId() + " x" + pickup.selectedItem().count()
            );
            if (!naturalPickup) {
                continue;
            }
            collected++;
        }

        if (planks == null) {
            observations.add("inventory recipe: failed no collected log family was detected");
            return new LogToPlanksResult(new ClientScenarioReport("failed", id, observations), null);
        }
        if (collected < targetLogCount) {
            observations.add(
                "natural log collection: failed collected=" + collected
                    + " target=" + targetLogCount
                    + " attempts=" + attempts
            );
            return new LogToPlanksResult(new ClientScenarioReport("failed", id, observations), planks);
        }
        int logCount = client.inventoryCount(planks.logItemId());
        int planksCount = client.inventoryCount(planks.planksItemId());
        if (logCount < targetLogCount) {
            observations.add("inventory recipe: failed fewer log items available than expected after pickup");
            return new LogToPlanksResult(new ClientScenarioReport("failed", id, observations), planks);
        }
        int craftedLogs = useMaxItems ? logCount : 1;
        int expectedLogCount = logCount - craftedLogs;
        int expectedPlanksCount = planksCount + craftedLogs * 4;
        int recipeDisplayId = client.recipeDisplayIdForResult(planks.planksItemId());
        if (recipeDisplayId < 0) {
            observations.add("inventory recipe: failed no recipe book entry for " + planks.planksItemId());
            return new LogToPlanksResult(new ClientScenarioReport("failed", id, observations), planks);
        }
        client.placeRecipe(0, recipeDisplayId, useMaxItems);
        boolean logConsumed = client.waitForInventoryCount(
            planks.logItemId(),
            expectedLogCount,
            INVENTORY_TIMEOUT
        );
        boolean planksCreated = client.waitForInventoryCount(
            planks.planksItemId(),
            expectedPlanksCount,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "inventory recipe: " + (logConsumed && planksCreated ? "passed" : "failed")
                + " recipe_display_id=" + recipeDisplayId
                + " use_max_items=" + useMaxItems
                + " log_item=" + planks.logItemId()
                + " log_expected_count=" + expectedLogCount
                + " log_count_matched=" + logConsumed
                + " planks_item=" + planks.planksItemId()
                + " planks_expected_count=" + expectedPlanksCount
                + " planks_count_matched=" + planksCreated
        );
        if (!logConsumed || !planksCreated) {
            return new LogToPlanksResult(new ClientScenarioReport("failed", id, observations), planks);
        }

        if (terminal) {
            observations.add(
                "remaining: crafting table placement/opening, sticks, wooden pickaxe, save/restart, and "
                    + "20-minute soak still need natural real-client automation"
            );
        }
        return new LogToPlanksResult(new ClientScenarioReport("passed", id, observations), planks);
    }

    static CraftingTableOpenResult craftPlaceAndOpenTable(
        String id,
        List<String> observations,
        ScenarioClient client,
        String planksItemId,
        boolean closeAfterOpen
    ) throws Exception {
        int planksCount = client.inventoryCount(planksItemId);
        int craftingTableCount = client.inventoryCount("minecraft:crafting_table");
        if (planksCount < 4) {
            observations.add("crafting table recipe: failed fewer than four planks available");
            return new CraftingTableOpenResult(new ClientScenarioReport("failed", id, observations), null);
        }
        int expectedPlanksCount = planksCount - 4;
        int expectedCraftingTableCount = craftingTableCount + 1;
        client.placeRecipe(0, client.requireRecipeDisplayId(CRAFTING_TABLE_RECIPE_RESULT), false);
        boolean planksConsumed = client.waitForInventoryCount(
            planksItemId,
            expectedPlanksCount,
            INVENTORY_TIMEOUT
        );
        boolean tableCreated = client.waitForInventoryCount(
            "minecraft:crafting_table",
            expectedCraftingTableCount,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "crafting table recipe: " + (planksConsumed && tableCreated ? "passed" : "failed")
                + " recipe_result=" + CRAFTING_TABLE_RECIPE_RESULT
                + " planks_item=" + planksItemId
                + " planks_expected_count=" + expectedPlanksCount
                + " planks_count_matched=" + planksConsumed
                + " crafting_table_expected_count=" + expectedCraftingTableCount
                + " crafting_table_count_matched=" + tableCreated
        );
        if (!planksConsumed || !tableCreated) {
            return new CraftingTableOpenResult(new ClientScenarioReport("failed", id, observations), null);
        }

        ScenarioHeldItem table = client.selectHotbarItem("minecraft:crafting_table", 1, HOTBAR_TIMEOUT);
        if (!table.matches("minecraft:crafting_table", 1)) {
            observations.add(
                "blocked: crafted table exists but is not selectable from hotbar without inventory mutation"
            );
            return new CraftingTableOpenResult(new ClientScenarioReport("blocked", id, observations), null);
        }
        ScenarioBlockPair pair = client.findUnobstructedPlaceablePair(ScenarioReach.WITHIN_SURVIVAL_REACH);
        if (pair == null) {
            observations.add("blocked: no loaded unobstructed target found for crafting table placement");
            return new CraftingTableOpenResult(new ClientScenarioReport("blocked", id, observations), null);
        }
        observations.add(
            "crafting table placement target: clicked="
                + pair.clicked().x() + "," + pair.clicked().y() + "," + pair.clicked().z()
                + "/" + pair.clicked().face()
                + " clicked_block=" + pair.clicked().blockId()
                + " target=" + pair.target().x() + "," + pair.target().y() + "," + pair.target().z()
                + " target_block=" + pair.target().blockId()
        );
        ScenarioUseResult placeUse = client.useItemOn(pair.clicked(), table);
        boolean placed = client.waitForBlock(pair.target(), "minecraft:crafting_table", BLOCK_TIMEOUT);
        ScenarioBlockTarget tableTarget = new ScenarioBlockTarget(
            pair.target().x(),
            pair.target().y(),
            pair.target().z(),
            "up",
            pair.target().label(),
            "minecraft:crafting_table"
        );
        if (!placed) {
            observations.add(
                "crafting table open: failed"
                    + " place_use_result=" + placeUse.result()
                    + " placed=false"
            );
            return new CraftingTableOpenResult(new ClientScenarioReport("failed", id, observations), tableTarget);
        }
        ScenarioUseResult openUse = client.useItemOn(tableTarget, table);
        boolean opened = client.waitForScreenClassName(CRAFTING_SCREEN, INVENTORY_TIMEOUT);
        boolean closed = !closeAfterOpen || client.closeCurrentScreen(INVENTORY_TIMEOUT);
        boolean passed = placed && opened && closed;
        observations.add(
            "crafting table open: " + (passed ? "passed" : "failed")
                + " place_use_result=" + placeUse.result()
                + " placed=" + placed
                + " open_use_result=" + openUse.result()
                + " screen=" + CRAFTING_SCREEN
                + " screen_matched=" + opened
                + " closed=" + closed
        );
        if (!passed) {
            return new CraftingTableOpenResult(new ClientScenarioReport("failed", id, observations), tableTarget);
        }

        if (closeAfterOpen) {
            observations.add(
                "remaining: sticks, wooden pickaxe, save/restart, and 20-minute soak still need natural real-client automation"
            );
        }
        return new CraftingTableOpenResult(new ClientScenarioReport("passed", id, observations), tableTarget);
    }
    static ClientScenarioReport runWoodToToolStart(
        String id,
        List<String> observations,
        ScenarioClient client,
        boolean terminal
    ) throws Exception {
        return runLogToPlanks(id, observations, client, 1, false, terminal).report();
    }

    static ClientScenarioReport runCraftingTableOpen(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        LogToPlanksResult logToPlanks = runLogToPlanks(id, observations, client, 1, false, false);
        if (!"passed".equals(logToPlanks.report().result())) {
            return logToPlanks.report();
        }

        return craftPlaceAndOpenTable(id, observations, client, logToPlanks.planks().planksItemId(), true)
            .report();
    }
    static String availablePlanksItem(ScenarioClient client) throws Exception {
        for (PlanksRecipe recipe : PLANKS_BY_LOG.values()) {
            if (client.inventoryCount(recipe.planksItemId()) > 0) {
                return recipe.planksItemId();
            }
        }
        return null;
    }

    private static boolean usesReachOnlyLogClose(String id) {
        return EARNED_CHEST_STORAGE_ID.equals(id) || CHEST_STORAGE_SAVE_RESTART_BEFORE_ID.equals(id);
    }

    static final String STICK_RECIPE_RESULT = "minecraft:stick";
    private static final String WOODEN_PICKAXE_RECIPE_RESULT = "minecraft:wooden_pickaxe";

    static ClientScenarioReport craftWoodenPickaxeInOpenTable(
        String id,
        List<String> observations,
        ScenarioClient client,
        String planksItemId,
        boolean closeScreen
    ) throws Exception {
        int containerId = client.activeContainerId();
        int planksCount = client.inventoryCount(planksItemId);
        int stickCount = client.inventoryCount("minecraft:stick");
        if (planksCount < 2) {
            observations.add("stick recipe: failed fewer than two planks available");
            return new ClientScenarioReport("failed", id, observations);
        }
        int expectedPlanksAfterSticks = planksCount - 2;
        int expectedStickCount = stickCount + 4;
        client.placeRecipe(containerId, client.requireRecipeDisplayId(STICK_RECIPE_RESULT), false);
        boolean stickPlanksConsumed = client.waitForInventoryCount(
            planksItemId,
            expectedPlanksAfterSticks,
            INVENTORY_TIMEOUT
        );
        boolean sticksCreated = client.waitForInventoryCount("minecraft:stick", expectedStickCount, INVENTORY_TIMEOUT);
        observations.add(
            "stick recipe: " + (stickPlanksConsumed && sticksCreated ? "passed" : "failed")
                + " container_id=" + containerId
                + " recipe_result=" + STICK_RECIPE_RESULT
                + " planks_item=" + planksItemId
                + " planks_expected_count=" + expectedPlanksAfterSticks
                + " planks_count_matched=" + stickPlanksConsumed
                + " stick_expected_count=" + expectedStickCount
                + " stick_count_matched=" + sticksCreated
        );
        if (!stickPlanksConsumed || !sticksCreated) {
            return new ClientScenarioReport("failed", id, observations);
        }

        planksCount = client.inventoryCount(planksItemId);
        stickCount = client.inventoryCount("minecraft:stick");
        int woodenPickaxeCount = client.inventoryCount("minecraft:wooden_pickaxe");
        if (planksCount < 3 || stickCount < 2) {
            observations.add("wooden pickaxe recipe: failed missing planks or sticks");
            return new ClientScenarioReport("failed", id, observations);
        }
        int expectedPlanksAfterPickaxe = planksCount - 3;
        int expectedStickAfterPickaxe = stickCount - 2;
        int expectedWoodenPickaxeCount = woodenPickaxeCount + 1;
        client.placeRecipe(containerId, client.requireRecipeDisplayId(WOODEN_PICKAXE_RECIPE_RESULT), false);
        boolean pickaxePlanksConsumed = client.waitForInventoryCount(
            planksItemId,
            expectedPlanksAfterPickaxe,
            INVENTORY_TIMEOUT
        );
        boolean pickaxeSticksConsumed = client.waitForInventoryCount(
            "minecraft:stick",
            expectedStickAfterPickaxe,
            INVENTORY_TIMEOUT
        );
        boolean pickaxeCreated = client.waitForInventoryCount(
            "minecraft:wooden_pickaxe",
            expectedWoodenPickaxeCount,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "wooden pickaxe recipe: "
                + (pickaxePlanksConsumed && pickaxeSticksConsumed && pickaxeCreated ? "passed" : "failed")
                + " container_id=" + containerId
                + " recipe_result=" + WOODEN_PICKAXE_RECIPE_RESULT
                + " planks_item=" + planksItemId
                + " planks_expected_count=" + expectedPlanksAfterPickaxe
                + " planks_count_matched=" + pickaxePlanksConsumed
                + " stick_expected_count=" + expectedStickAfterPickaxe
                + " stick_count_matched=" + pickaxeSticksConsumed
                + " wooden_pickaxe_expected_count=" + expectedWoodenPickaxeCount
                + " wooden_pickaxe_count_matched=" + pickaxeCreated
        );
        boolean closed = !closeScreen || client.closeCurrentScreen(INVENTORY_TIMEOUT);
        if (closeScreen) {
            observations.add("crafting table screen close after tool: " + (closed ? "passed" : "failed"));
        }
        if (!pickaxePlanksConsumed || !pickaxeSticksConsumed || !pickaxeCreated || !closed) {
            return new ClientScenarioReport("failed", id, observations);
        }

        return new ClientScenarioReport("passed", id, observations);
    }

    static ClientScenarioReport craftWoodenSwordInOpenTable(
        String id,
        List<String> observations,
        ScenarioClient client,
        String planksItemId
    ) throws Exception {
        int containerId = client.activeContainerId();
        int planksCount = client.inventoryCount(planksItemId);
        int stickCount = client.inventoryCount("minecraft:stick");
        int swordCount = client.inventoryCount("minecraft:wooden_sword");
        if (planksCount < 2 || stickCount < 1) {
            observations.add("wooden sword recipe: failed missing planks or stick");
            return new ClientScenarioReport("failed", id, observations);
        }

        int recipeDisplayId = client.recipeDisplayIdForResult("minecraft:wooden_sword");
        client.placeRecipe(containerId, recipeDisplayId, false);
        boolean planksConsumed = client.waitForInventoryCount(
            planksItemId,
            planksCount - 2,
            INVENTORY_TIMEOUT
        );
        boolean stickConsumed = client.waitForInventoryCount(
            "minecraft:stick",
            stickCount - 1,
            INVENTORY_TIMEOUT
        );
        boolean swordCreated = client.waitForInventoryCount(
            "minecraft:wooden_sword",
            swordCount + 1,
            INVENTORY_TIMEOUT
        );
        boolean closed = client.closeCurrentScreen(INVENTORY_TIMEOUT);
        boolean passed = planksConsumed && stickConsumed && swordCreated && closed;
        observations.add(
            "wooden sword recipe: " + (passed ? "passed" : "failed")
                + " container_id=" + containerId
                + " recipe_display_id=" + recipeDisplayId
                + " planks_count_matched=" + planksConsumed
                + " stick_count_matched=" + stickConsumed
                + " wooden_sword_count_matched=" + swordCreated
                + " screen_closed=" + closed
        );
        return new ClientScenarioReport(passed ? "passed" : "failed", id, observations);
    }
    record WoodenToolTableResult(
        dev.solaris.agent.client.ClientScenarioReport report,
        PlanksRecipe planks,
        ScenarioBlockTarget tableTarget
    ) {}

    static WoodenToolTableResult prepareWoodenToolAndTable(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        LogToPlanksResult planks = PlayableResourceStage.runLogToPlanks(id, observations, client, 4, true, false);
        if (!"passed".equals(planks.report().result())) {
            return new WoodenToolTableResult(planks.report(), planks.planks(), null);
        }
        CraftingTableOpenResult table = PlayableResourceStage.craftPlaceAndOpenTable(
            id,
            observations,
            client,
            planks.planks().planksItemId(),
            false
        );
        if (!"passed".equals(table.report().result())) {
            return new WoodenToolTableResult(table.report(), planks.planks(), table.tableTarget());
        }
        ClientScenarioReport woodenTool = craftWoodenPickaxeInOpenTable(
            id,
            observations,
            client,
            planks.planks().planksItemId(),
            true
        );
        if (!"passed".equals(woodenTool.result())) {
            return new WoodenToolTableResult(woodenTool, planks.planks(), table.tableTarget());
        }

        return new WoodenToolTableResult(
            new ClientScenarioReport("passed", id, observations),
            planks.planks(),
            table.tableTarget()
        );
    }
}
