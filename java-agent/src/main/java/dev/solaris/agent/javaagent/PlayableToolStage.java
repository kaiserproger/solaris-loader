package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static dev.solaris.agent.javaagent.PlayableCoordinates.coordinates;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.APPROACH_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.NIGHT_WAIT_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.NIGHT_START_DAY_TIME;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.FURNACE_SCREEN;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.FURNACE_COOK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.ENTITY_SCAN_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableResourceStage.STICK_RECIPE_RESULT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.CRAFTING_SCREEN;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.BREAK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.BLOCK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.HOTBAR_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.INVENTORY_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.PICKUP_TIMEOUT;

/** Stone/iron tool progression plus save/restart assertions. */
final class PlayableToolStage {
    private PlayableToolStage() {
    }

    private static final String STONE_PICKAXE_RECIPE_RESULT = "minecraft:stone_pickaxe";
    private static final String STONE_SWORD_RECIPE_RESULT = "minecraft:stone_sword";
    private static final String IRON_SWORD_RECIPE_RESULT = "minecraft:iron_sword";
    private static final String SHIELD_RECIPE_RESULT = "minecraft:shield";
    private static final String IRON_CHESTPLATE_RECIPE_RESULT = "minecraft:iron_chestplate";
    private static final List<String> IRON_ORE_BLOCK_IDS = List.of(
        "minecraft:iron_ore",
        "minecraft:deepslate_iron_ore"
    );

    record StoneToolProgressionResult(ClientScenarioReport report, ScenarioBlockTarget tableTarget) {}

    record IronProgressionBaseResult(
        ClientScenarioReport report,
        PlayableResourceStage.WoodenToolTableResult prepared,
        PlayableHeatStage.FurnacePlacementOpenResult placedFurnace
    ) {}

    record IronSwordProgressionResult(
        ClientScenarioReport report,
        ScenarioBlockTarget tableTarget
    ) {}

    record IronChestplateProgressionResult(
        ClientScenarioReport report,
        ScenarioBlockTarget tableTarget
    ) {}

    static ClientScenarioReport runWoodToTool(
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

        ClientScenarioReport tool = PlayableResourceStage.craftWoodenPickaxeInOpenTable(
            id,
            observations,
            client,
            planks.planks().planksItemId(),
            true
        );
        if (!"passed".equals(tool.result())) {
            return tool;
        }

        observations.add("remaining: save/restart and 20-minute soak still need real-client automation");
        return new ClientScenarioReport("passed", id, observations);
    }

    static StoneToolProgressionResult runStoneToolProgression(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        PlayableResourceStage.LogToPlanksResult planks = PlayableResourceStage.runLogToPlanks(id, observations, client, 3, true, false);
        if (!"passed".equals(planks.report().result())) {
            return new StoneToolProgressionResult(planks.report(), null);
        }
        PlayableResourceStage.CraftingTableOpenResult table = PlayableResourceStage.craftPlaceAndOpenTable(
            id,
            observations,
            client,
            planks.planks().planksItemId(),
            false
        );
        if (!"passed".equals(table.report().result())) {
            return new StoneToolProgressionResult(table.report(), table.tableTarget());
        }
        ClientScenarioReport woodenTool = PlayableResourceStage.craftWoodenPickaxeInOpenTable(
            id,
            observations,
            client,
            planks.planks().planksItemId(),
            true
        );
        if (!"passed".equals(woodenTool.result())) {
            return new StoneToolProgressionResult(woodenTool, table.tableTarget());
        }

        ClientScenarioReport cobblestone = PlayableHeatStage.mineCobblestoneWithWoodenPickaxe(id, observations, client, 3);
        if (!"passed".equals(cobblestone.result())) {
            return new StoneToolProgressionResult(cobblestone, table.tableTarget());
        }

        boolean tableApproached = client.approachBlock(table.tableTarget(), APPROACH_TIMEOUT);
        observations.add(
            "crafting table approach for stone tool: " + (tableApproached ? "passed" : "failed")
                + " target=" + coordinates(table.tableTarget())
        );
        if (!tableApproached) {
            return new StoneToolProgressionResult(
                new ClientScenarioReport("blocked", id, observations),
                table.tableTarget()
            );
        }

        ScenarioHeldItem woodenPickaxe = client.selectHotbarItem("minecraft:wooden_pickaxe", 1, HOTBAR_TIMEOUT);
        ScenarioBlockTarget tableUseTarget = PlayableCoordinates.reachableUseTarget(client, table.tableTarget());
        ScenarioUseResult openUse = client.useItemOn(tableUseTarget, woodenPickaxe);
        boolean opened = client.waitForScreenClassName(CRAFTING_SCREEN, INVENTORY_TIMEOUT);
        observations.add(
            "crafting table reopen for stone tool: " + (opened ? "passed" : "failed")
                + " open_use_result=" + openUse.result()
                + " screen=" + CRAFTING_SCREEN
                + " screen_matched=" + opened
        );
        if (!opened) {
            return new StoneToolProgressionResult(
                new ClientScenarioReport("failed", id, observations),
                table.tableTarget()
            );
        }

        return new StoneToolProgressionResult(
            craftStonePickaxeInOpenTable(id, observations, client, true),
            table.tableTarget()
        );
    }

    static ClientScenarioReport runStoneToolSaveRestartBefore(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        StoneToolProgressionResult stoneTool = runStoneToolProgression(id, observations, client);
        if (!"passed".equals(stoneTool.report().result())) {
            return stoneTool.report();
        }
        if (stoneTool.tableTarget() == null) {
            observations.add("restart marker placement: failed missing crafted table target");
            return new ClientScenarioReport("failed", id, observations);
        }

        PlayablePersistenceStage.writeMarker(PlayablePersistenceStage.saveRestartMarkerPath(screenshotsDir), stoneTool.tableTarget());
        observations.add("restart marker placement: passed target=" + coordinates(stoneTool.tableTarget()));
        observations.add("runner-managed restart: pending clean server restart and stone-tool rejoin check");
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport("passed", id, observations);
    }

    static ClientScenarioReport runStoneToolSaveRestartAfter(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = PlayablePersistenceStage.saveRestartMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing restart marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioBlockTarget marker = PlayablePersistenceStage.readMarker(markerPath, "restart-marker");
        boolean markerPersisted = client.waitForBlock(marker, "minecraft:crafting_table", BLOCK_TIMEOUT);
        int stonePickaxeCount = client.inventoryCount("minecraft:stone_pickaxe");
        boolean inventoryPersisted = stonePickaxeCount >= 1;
        observations.add(
            "restart marker persistence: " + (markerPersisted ? "passed" : "failed")
                + " target=" + coordinates(marker)
        );
        observations.add(
            "stone inventory persistence: " + (inventoryPersisted ? "passed" : "failed")
                + " stone_pickaxe_count=" + stonePickaxeCount
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport(
            markerPersisted && inventoryPersisted ? "passed" : "failed",
            id,
            observations
        );
    }

    static ClientScenarioReport runIronIngotProgression(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        IronProgressionBaseResult base = prepareIronProgressionBase(id, observations, client);
        if (!"passed".equals(base.report().result())) {
            return base.report();
        }

        for (int ore = 0; ore < 2; ore++) {
            ClientScenarioReport rawIron = mineRawIronWithStonePickaxe(id, observations, client);
            if (!"passed".equals(rawIron.result())) {
                return rawIron;
            }
        }

        return PlayableHeatStage.smeltRawIronInPlacedFurnace(
            id,
            observations,
            client,
            base.placedFurnace(),
            base.prepared().planks(),
            2
        );
    }

    static IronProgressionBaseResult prepareIronProgressionBase(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        return prepareIronProgressionBase(id, observations, client, 4);
    }

    private static IronProgressionBaseResult prepareIronProgressionBase(
        String id,
        List<String> observations,
        ScenarioClient client,
        int initialLogCount
    ) throws Exception {
        PlayableResourceStage.WoodenToolTableResult prepared =
            PlayableResourceStage.prepareWoodenToolAndTable(id, observations, client, initialLogCount);
        if (!"passed".equals(prepared.report().result())) {
            return new IronProgressionBaseResult(prepared.report(), prepared, null);
        }

        ClientScenarioReport cobblestone = PlayableHeatStage.mineCobblestoneWithWoodenPickaxe(id, observations, client, 11);
        if (!"passed".equals(cobblestone.result())) {
            return new IronProgressionBaseResult(cobblestone, prepared, null);
        }

        boolean tableApproached = client.approachBlock(prepared.tableTarget(), APPROACH_TIMEOUT);
        observations.add(
            "crafting table approach for iron progression: " + (tableApproached ? "passed" : "failed")
                + " target=" + coordinates(prepared.tableTarget())
        );
        if (!tableApproached) {
            return new IronProgressionBaseResult(
                new ClientScenarioReport("blocked", id, observations),
                prepared,
                null
            );
        }

        ScenarioHeldItem woodenPickaxe = client.selectHotbarItem("minecraft:wooden_pickaxe", 1, HOTBAR_TIMEOUT);
        ScenarioBlockTarget tableUseTarget = PlayableCoordinates.reachableUseTarget(client, prepared.tableTarget());
        ScenarioUseResult openUse = client.useItemOn(tableUseTarget, woodenPickaxe);
        boolean opened = client.waitForScreenClassName(CRAFTING_SCREEN, INVENTORY_TIMEOUT);
        observations.add(
            "crafting table reopen for iron progression: " + (opened ? "passed" : "failed")
                + " open_use_result=" + openUse.result()
                + " screen=" + CRAFTING_SCREEN
                + " screen_matched=" + opened
        );
        if (!opened) {
            return new IronProgressionBaseResult(
                new ClientScenarioReport("failed", id, observations),
                prepared,
                null
            );
        }

        ClientScenarioReport stonePickaxe = craftStonePickaxeInOpenTable(id, observations, client, false);
        if (!"passed".equals(stonePickaxe.result())) {
            return new IronProgressionBaseResult(stonePickaxe, prepared, null);
        }
        ClientScenarioReport furnace = PlayableHeatStage.craftFurnaceInOpenTable(id, observations, client, true);
        if (!"passed".equals(furnace.result())) {
            return new IronProgressionBaseResult(furnace, prepared, null);
        }

        PlayableHeatStage.FurnacePlacementOpenResult placedFurnace = PlayableHeatStage.placeAndOpenFurnace(id, observations, client);
        if (!"passed".equals(placedFurnace.report().result())) {
            return new IronProgressionBaseResult(placedFurnace.report(), prepared, placedFurnace);
        }

        return new IronProgressionBaseResult(
            new ClientScenarioReport("passed", id, observations),
            prepared,
            placedFurnace
        );
    }

    static ClientScenarioReport craftIronSwordInOpenTable(
        String id,
        List<String> observations,
        ScenarioClient client,
        String planksItemId,
        boolean closeScreen
    ) throws Exception {
        int containerId = client.activeContainerId();
        int stickCount = client.inventoryCount("minecraft:stick");
        if (stickCount < 1) {
            int planksCount = client.inventoryCount(planksItemId);
            if (planksCount < 2) {
                observations.add("iron sword stick recipe: failed fewer than two planks available");
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
            boolean sticksCreated = client.waitForInventoryCount(
                "minecraft:stick",
                expectedStickCount,
                INVENTORY_TIMEOUT
            );
            observations.add(
                "iron sword stick recipe: "
                    + (stickPlanksConsumed && sticksCreated ? "passed" : "failed")
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
        }

        int ironIngotCount = client.inventoryCount("minecraft:iron_ingot");
        stickCount = client.inventoryCount("minecraft:stick");
        int ironSwordCount = client.inventoryCount("minecraft:iron_sword");
        if (ironIngotCount < 2 || stickCount < 1) {
            observations.add("iron sword recipe: failed missing iron ingots or sticks");
            return new ClientScenarioReport("failed", id, observations);
        }
        int expectedIronIngotAfterSword = ironIngotCount - 2;
        int expectedStickAfterSword = stickCount - 1;
        int expectedIronSwordCount = ironSwordCount + 1;
        client.placeRecipe(containerId, client.requireRecipeDisplayId(IRON_SWORD_RECIPE_RESULT), false);
        boolean swordIronConsumed = client.waitForInventoryCount(
            "minecraft:iron_ingot",
            expectedIronIngotAfterSword,
            INVENTORY_TIMEOUT
        );
        boolean swordStickConsumed = client.waitForInventoryCount(
            "minecraft:stick",
            expectedStickAfterSword,
            INVENTORY_TIMEOUT
        );
        boolean swordCreated = client.waitForInventoryCount(
            "minecraft:iron_sword",
            expectedIronSwordCount,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "iron sword recipe: "
                + (swordIronConsumed && swordStickConsumed && swordCreated ? "passed" : "failed")
                + " container_id=" + containerId
                + " recipe_result=" + IRON_SWORD_RECIPE_RESULT
                + " iron_ingot_expected_count=" + expectedIronIngotAfterSword
                + " iron_ingot_count_matched=" + swordIronConsumed
                + " stick_expected_count=" + expectedStickAfterSword
                + " stick_count_matched=" + swordStickConsumed
                + " iron_sword_expected_count=" + expectedIronSwordCount
                + " iron_sword_count_matched=" + swordCreated
        );
        boolean closed = !closeScreen || client.closeCurrentScreen(INVENTORY_TIMEOUT);
        if (closeScreen) {
            observations.add("crafting table screen close after iron sword: " + (closed ? "passed" : "failed"));
        }
        if (!swordIronConsumed || !swordStickConsumed || !swordCreated || !closed) {
            return new ClientScenarioReport("failed", id, observations);
        }

        return new ClientScenarioReport("passed", id, observations);
    }

    static ClientScenarioReport craftEarnedShieldProgression(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        IronProgressionBaseResult base = prepareIronProgressionBase(id, observations, client);
        if (!"passed".equals(base.report().result())) {
            return base.report();
        }

        ClientScenarioReport rawIron = mineRawIronWithStonePickaxe(id, observations, client);
        if (!"passed".equals(rawIron.result())) {
            return rawIron;
        }
        ClientScenarioReport smelt = PlayableHeatStage.smeltRawIronInPlacedFurnace(
            id,
            observations,
            client,
            base.placedFurnace(),
            base.prepared().planks()
        );
        if (!"passed".equals(smelt.result())) {
            return smelt;
        }

        boolean tableApproached = client.approachBlock(base.prepared().tableTarget(), APPROACH_TIMEOUT);
        observations.add(
            "crafting table approach for shield: " + (tableApproached ? "passed" : "failed")
                + " target=" + coordinates(base.prepared().tableTarget())
        );
        if (!tableApproached) {
            return new ClientScenarioReport("blocked", id, observations);
        }

        ScenarioHeldItem stonePickaxe = client.selectHotbarItem("minecraft:stone_pickaxe", 1, HOTBAR_TIMEOUT);
        ScenarioBlockTarget tableUseTarget = PlayableCoordinates.reachableUseTarget(client, base.prepared().tableTarget());
        ScenarioUseResult openUse = client.useItemOn(tableUseTarget, stonePickaxe);
        boolean opened = client.waitForScreenClassName(CRAFTING_SCREEN, INVENTORY_TIMEOUT);
        observations.add(
            "crafting table reopen for shield: " + (opened ? "passed" : "failed")
                + " open_use_result=" + openUse.result()
                + " screen=" + CRAFTING_SCREEN
                + " screen_matched=" + opened
        );
        if (!opened) {
            return new ClientScenarioReport("failed", id, observations);
        }

        return craftShieldInOpenTable(
            id,
            observations,
            client,
            base.prepared().planks().planksItemId(),
            true
        );
    }

    static ClientScenarioReport craftEarnedIronChestplateProgression(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        return craftEarnedIronChestplateProgressionResult(id, observations, client).report();
    }

    static IronChestplateProgressionResult craftEarnedIronChestplateProgressionResult(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        // Five logs yield 20 planks; table (4), sticks (2), and pickaxe (3)
        // leave 11 before furnace placement, enough for eight independent burns.
        IronProgressionBaseResult base = prepareIronProgressionBase(id, observations, client, 5);
        if (!"passed".equals(base.report().result())) {
            return new IronChestplateProgressionResult(base.report(), null);
        }

        PlayableResourceStage.PlanksRecipe fuelPlanks = base.prepared().planks();
        int fuelPlanksCount = client.inventoryCount(fuelPlanks.planksItemId());
        boolean enoughFuel = fuelPlanksCount >= 8;
        observations.add(
            "iron chestplate fuel planks: " + (enoughFuel ? "passed" : "failed")
                + " planks_item=" + fuelPlanks.planksItemId()
                + " planks_count=" + fuelPlanksCount
                + " expected_at_least=8"
        );
        if (!enoughFuel) {
            return new IronChestplateProgressionResult(
                new ClientScenarioReport("failed", id, observations),
                base.prepared().tableTarget()
            );
        }

        for (int ingot = 0; ingot < 8; ingot++) {
            ClientScenarioReport rawIron = mineRawIronWithStonePickaxe(id, observations, client);
            if (!"passed".equals(rawIron.result())) {
                return new IronChestplateProgressionResult(rawIron, base.prepared().tableTarget());
            }
            ClientScenarioReport smelt = PlayableHeatStage.smeltRawIronInPlacedFurnace(
                id,
                observations,
                client,
                base.placedFurnace(),
                fuelPlanks,
                1
            );
            if (!"passed".equals(smelt.result())) {
                return new IronChestplateProgressionResult(smelt, base.prepared().tableTarget());
            }
        }

        boolean tableApproached = client.approachBlock(base.prepared().tableTarget(), APPROACH_TIMEOUT);
        observations.add(
            "crafting table approach for iron chestplate: " + (tableApproached ? "passed" : "failed")
                + " target=" + coordinates(base.prepared().tableTarget())
        );
        if (!tableApproached) {
            return new IronChestplateProgressionResult(
                new ClientScenarioReport("blocked", id, observations),
                base.prepared().tableTarget()
            );
        }

        ScenarioHeldItem stonePickaxe = client.selectHotbarItem("minecraft:stone_pickaxe", 1, HOTBAR_TIMEOUT);
        ScenarioBlockTarget tableUseTarget = PlayableCoordinates.reachableUseTarget(client, base.prepared().tableTarget());
        ScenarioUseResult openUse = client.useItemOn(tableUseTarget, stonePickaxe);
        boolean opened = client.waitForScreenClassName(CRAFTING_SCREEN, INVENTORY_TIMEOUT);
        observations.add(
            "crafting table reopen for iron chestplate: " + (opened ? "passed" : "failed")
                + " open_use_result=" + openUse.result()
                + " screen=" + CRAFTING_SCREEN
                + " screen_matched=" + opened
        );
        if (!opened) {
            return new IronChestplateProgressionResult(
                new ClientScenarioReport("failed", id, observations),
                base.prepared().tableTarget()
            );
        }

        return new IronChestplateProgressionResult(
            craftIronChestplateInOpenTable(id, observations, client, true),
            base.prepared().tableTarget()
        );
    }

    static ClientScenarioReport mineRawIronWithStonePickaxe(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        ScenarioHeldItem stonePickaxe = client.selectHotbarItem("minecraft:stone_pickaxe", 1, HOTBAR_TIMEOUT);
        if (!stonePickaxe.matches("minecraft:stone_pickaxe", 1)) {
            observations.add("blocked: crafted stone pickaxe is not selectable for iron mining");
            return new ClientScenarioReport("blocked", id, observations);
        }

        int rawIronTarget = client.inventoryCount("minecraft:raw_iron") + 1;
        int miningAttempts = 0;
        int maxMiningAttempts = 4;
        while (
            client.inventoryCount("minecraft:raw_iron") < rawIronTarget
                && miningAttempts < maxMiningAttempts
        ) {
            miningAttempts += 1;
            ScenarioBlockTarget ore = client.findBreakableBlock(
                IRON_ORE_BLOCK_IDS,
                ScenarioReach.WITHIN_SURVIVAL_REACH
            );
            if (ore == null) {
                ScenarioBlockTarget farOre = client.findBreakableBlock(
                    IRON_ORE_BLOCK_IDS,
                    ScenarioReach.OUTSIDE_SURVIVAL_REACH
                );
                if (farOre == null) {
                    observations.add("blocked: no loaded natural iron ore found near the real client");
                    return new ClientScenarioReport("blocked", id, observations);
                }
                boolean approached = client.approachBlock(farOre, APPROACH_TIMEOUT);
                observations.add(
                    "natural iron ore approach: " + (approached ? "passed" : "failed")
                        + " target=" + coordinates(farOre)
                );
                if (!approached) {
                    return new ClientScenarioReport("blocked", id, observations);
                }
                // Approach proved this exact observed ore reachable. A ring scan
                // excludes the player's own column, including the exposed shaft floor.
                ore = farOre;
            }

            boolean closeApproached = client.approachBlock(ore, APPROACH_TIMEOUT);
            observations.add(
                "natural iron ore close approach: " + (closeApproached ? "passed" : "failed")
                    + " target=" + coordinates(ore)
            );
            if (!closeApproached) {
                return new ClientScenarioReport("blocked", id, observations);
            }

            int rawIronBefore = client.inventoryCount("minecraft:raw_iron");
            ScenarioBreakResult broke = client.breakBlockUntilDropVisible(ore, "minecraft:raw_iron", BREAK_TIMEOUT);
            ScenarioBreakResult pickup = client.collectVisibleItemDrop(ore, "minecraft:raw_iron", 1, PICKUP_TIMEOUT);
            int rawIronAfter = client.inventoryCount("minecraft:raw_iron");
            boolean inventoryAdvanced = rawIronAfter >= rawIronBefore + 1;
            boolean naturalPickup = broke.started()
                && broke.becameAir()
                && broke.sawDrop()
                && inventoryAdvanced;
            String pickupDetail = pickup.pickupDetail().isBlank()
                ? ""
                : " pickup_detail=" + pickup.pickupDetail();
            observations.add(
                "natural iron ore break/drop/pickup: " + (naturalPickup ? "passed" : "failed")
                    + " target=" + coordinates(ore)
                    + " block=" + ore.blockId()
                    + " break_started=" + broke.started()
                    + " became_air=" + broke.becameAir()
                    + " saw_drop=" + broke.sawDrop()
                    + " pickup_restored=" + pickup.pickupRestored()
                    + " raw_iron_before=" + rawIronBefore
                    + " raw_iron_after=" + rawIronAfter
                    + pickupDetail
            );
            if (!naturalPickup) {
                if (broke.started() && broke.becameAir() && broke.sawDrop() && !inventoryAdvanced) {
                    continue;
                }
                return new ClientScenarioReport("failed", id, observations);
            }
        }

        int rawIronCount = client.inventoryCount("minecraft:raw_iron");
        if (rawIronCount < rawIronTarget) {
            observations.add(
                "natural iron ore inventory: failed raw_iron_count=" + rawIronCount
                    + " expected_at_least=" + rawIronTarget
                    + " mining_attempts=" + miningAttempts
            );
            return new ClientScenarioReport("failed", id, observations);
        }

        return new ClientScenarioReport("passed", id, observations);
    }

    static ClientScenarioReport craftStonePickaxeInOpenTable(
        String id,
        List<String> observations,
        ScenarioClient client,
        boolean closeScreen
    ) throws Exception {
        int containerId = client.activeContainerId();
        int cobblestoneCount = client.inventoryCount("minecraft:cobblestone");
        int stickCount = client.inventoryCount("minecraft:stick");
        int stonePickaxeCount = client.inventoryCount("minecraft:stone_pickaxe");
        if (cobblestoneCount < 3 || stickCount < 2) {
            observations.add("stone pickaxe recipe: failed missing cobblestone or sticks");
            return new ClientScenarioReport("failed", id, observations);
        }
        int expectedCobblestoneAfterPickaxe = cobblestoneCount - 3;
        int expectedStickAfterPickaxe = stickCount - 2;
        int expectedStonePickaxeCount = stonePickaxeCount + 1;
        client.placeRecipe(containerId, client.requireRecipeDisplayId(STONE_PICKAXE_RECIPE_RESULT), false);
        boolean pickaxeCobblestoneConsumed = client.waitForInventoryCount(
            "minecraft:cobblestone",
            expectedCobblestoneAfterPickaxe,
            INVENTORY_TIMEOUT
        );
        boolean pickaxeSticksConsumed = client.waitForInventoryCount(
            "minecraft:stick",
            expectedStickAfterPickaxe,
            INVENTORY_TIMEOUT
        );
        boolean pickaxeCreated = client.waitForInventoryCount(
            "minecraft:stone_pickaxe",
            expectedStonePickaxeCount,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "stone pickaxe recipe: "
                + (pickaxeCobblestoneConsumed && pickaxeSticksConsumed && pickaxeCreated ? "passed" : "failed")
                + " container_id=" + containerId
                + " recipe_result=" + STONE_PICKAXE_RECIPE_RESULT
                + " cobblestone_expected_count=" + expectedCobblestoneAfterPickaxe
                + " cobblestone_count_matched=" + pickaxeCobblestoneConsumed
                + " stick_expected_count=" + expectedStickAfterPickaxe
                + " stick_count_matched=" + pickaxeSticksConsumed
                + " stone_pickaxe_expected_count=" + expectedStonePickaxeCount
                + " stone_pickaxe_count_matched=" + pickaxeCreated
        );
        boolean closed = !closeScreen || client.closeCurrentScreen(INVENTORY_TIMEOUT);
        if (closeScreen) {
            observations.add("crafting table screen close after stone tool: " + (closed ? "passed" : "failed"));
        }
        if (!pickaxeCobblestoneConsumed || !pickaxeSticksConsumed || !pickaxeCreated || !closed) {
            return new ClientScenarioReport("failed", id, observations);
        }

        return new ClientScenarioReport("passed", id, observations);
    }

    static ClientScenarioReport craftStoneSwordInOpenTable(
        String id,
        List<String> observations,
        ScenarioClient client,
        boolean closeScreen
    ) throws Exception {
        int containerId = client.activeContainerId();
        int cobblestoneCount = client.inventoryCount("minecraft:cobblestone");
        int stickCount = client.inventoryCount("minecraft:stick");
        int stoneSwordCount = client.inventoryCount("minecraft:stone_sword");
        if (cobblestoneCount < 2 || stickCount < 1) {
            observations.add("stone sword recipe: failed missing cobblestone or sticks");
            return new ClientScenarioReport("failed", id, observations);
        }
        int expectedCobblestoneAfterSword = cobblestoneCount - 2;
        int expectedStickAfterSword = stickCount - 1;
        int expectedStoneSwordCount = stoneSwordCount + 1;
        client.placeRecipe(containerId, client.requireRecipeDisplayId(STONE_SWORD_RECIPE_RESULT), false);
        boolean swordCobblestoneConsumed = client.waitForInventoryCount(
            "minecraft:cobblestone",
            expectedCobblestoneAfterSword,
            INVENTORY_TIMEOUT
        );
        boolean swordSticksConsumed = client.waitForInventoryCount(
            "minecraft:stick",
            expectedStickAfterSword,
            INVENTORY_TIMEOUT
        );
        boolean swordCreated = client.waitForInventoryCount(
            "minecraft:stone_sword",
            expectedStoneSwordCount,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "stone sword recipe: "
                + (swordCobblestoneConsumed && swordSticksConsumed && swordCreated ? "passed" : "failed")
                + " container_id=" + containerId
                + " recipe_result=" + STONE_SWORD_RECIPE_RESULT
                + " cobblestone_expected_count=" + expectedCobblestoneAfterSword
                + " cobblestone_count_matched=" + swordCobblestoneConsumed
                + " stick_expected_count=" + expectedStickAfterSword
                + " stick_count_matched=" + swordSticksConsumed
                + " stone_sword_expected_count=" + expectedStoneSwordCount
                + " stone_sword_count_matched=" + swordCreated
        );
        boolean closed = !closeScreen || client.closeCurrentScreen(INVENTORY_TIMEOUT);
        if (closeScreen) {
            observations.add("crafting table screen close after stone sword: " + (closed ? "passed" : "failed"));
        }
        if (!swordCobblestoneConsumed || !swordSticksConsumed || !swordCreated || !closed) {
            return new ClientScenarioReport("failed", id, observations);
        }

        return new ClientScenarioReport("passed", id, observations);
    }


    static ClientScenarioReport craftShieldInOpenTable(
        String id,
        List<String> observations,
        ScenarioClient client,
        String planksItemId,
        boolean closeScreen
    ) throws Exception {
        int containerId = client.activeContainerId();
        int planksCount = client.inventoryCount(planksItemId);
        int ironIngotCount = client.inventoryCount("minecraft:iron_ingot");
        int shieldCount = client.inventoryCount("minecraft:shield");
        if (planksCount < 6 || ironIngotCount < 1) {
            observations.add("shield recipe: failed missing planks or iron ingot");
            return new ClientScenarioReport("failed", id, observations);
        }

        int expectedPlanksAfterShield = planksCount - 6;
        int expectedIronIngotAfterShield = ironIngotCount - 1;
        int expectedShieldCount = shieldCount + 1;
        client.placeRecipe(containerId, client.requireRecipeDisplayId(SHIELD_RECIPE_RESULT), false);
        boolean shieldPlanksConsumed = client.waitForInventoryCount(
            planksItemId,
            expectedPlanksAfterShield,
            INVENTORY_TIMEOUT
        );
        boolean shieldIronConsumed = client.waitForInventoryCount(
            "minecraft:iron_ingot",
            expectedIronIngotAfterShield,
            INVENTORY_TIMEOUT
        );
        boolean shieldCreated = client.waitForInventoryCount(
            "minecraft:shield",
            expectedShieldCount,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "shield recipe: "
                + (shieldPlanksConsumed && shieldIronConsumed && shieldCreated ? "passed" : "failed")
                + " container_id=" + containerId
                + " recipe_result=" + SHIELD_RECIPE_RESULT
                + " planks_item=" + planksItemId
                + " planks_expected_count=" + expectedPlanksAfterShield
                + " planks_count_matched=" + shieldPlanksConsumed
                + " iron_ingot_expected_count=" + expectedIronIngotAfterShield
                + " iron_ingot_count_matched=" + shieldIronConsumed
                + " shield_expected_count=" + expectedShieldCount
                + " shield_count_matched=" + shieldCreated
        );
        boolean closed = !closeScreen || client.closeCurrentScreen(INVENTORY_TIMEOUT);
        if (closeScreen) {
            observations.add("crafting table screen close after shield: " + (closed ? "passed" : "failed"));
        }
        if (!shieldPlanksConsumed || !shieldIronConsumed || !shieldCreated || !closed) {
            return new ClientScenarioReport("failed", id, observations);
        }

        return new ClientScenarioReport("passed", id, observations);
    }

    static ClientScenarioReport craftIronChestplateInOpenTable(
        String id,
        List<String> observations,
        ScenarioClient client,
        boolean closeScreen
    ) throws Exception {
        int containerId = client.activeContainerId();
        int ironIngotCount = client.inventoryCount("minecraft:iron_ingot");
        int ironChestplateCount = client.inventoryCount("minecraft:iron_chestplate");
        if (ironIngotCount < 8) {
            observations.add("iron chestplate recipe: failed missing iron ingots");
            return new ClientScenarioReport("failed", id, observations);
        }

        int expectedIronIngotAfterChestplate = ironIngotCount - 8;
        int expectedIronChestplateCount = ironChestplateCount + 1;
        client.placeRecipe(containerId, client.requireRecipeDisplayId(IRON_CHESTPLATE_RECIPE_RESULT), false);
        boolean chestplateIronConsumed = client.waitForInventoryCount(
            "minecraft:iron_ingot",
            expectedIronIngotAfterChestplate,
            INVENTORY_TIMEOUT
        );
        boolean chestplateCreated = client.waitForInventoryCount(
            "minecraft:iron_chestplate",
            expectedIronChestplateCount,
            INVENTORY_TIMEOUT
        );
        observations.add(
            "iron chestplate recipe: "
                + (chestplateIronConsumed && chestplateCreated ? "passed" : "failed")
                + " container_id=" + containerId
                + " recipe_result=" + IRON_CHESTPLATE_RECIPE_RESULT
                + " iron_ingot_expected_count=" + expectedIronIngotAfterChestplate
                + " iron_ingot_count_matched=" + chestplateIronConsumed
                + " iron_chestplate_expected_count=" + expectedIronChestplateCount
                + " iron_chestplate_count_matched=" + chestplateCreated
        );
        boolean closed = !closeScreen || client.closeCurrentScreen(INVENTORY_TIMEOUT);
        if (closeScreen) {
            observations.add("crafting table screen close after iron chestplate: " + (closed ? "passed" : "failed"));
        }
        if (!chestplateIronConsumed || !chestplateCreated || !closed) {
            return new ClientScenarioReport("failed", id, observations);
        }

        return new ClientScenarioReport("passed", id, observations);
    }

    static ClientScenarioReport runIronSwordSaveRestartBefore(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        IronSwordProgressionResult ironSword = craftEarnedIronSwordProgression(id, observations, client);
        if (!"passed".equals(ironSword.report().result())) {
            return ironSword.report();
        }
        if (ironSword.tableTarget() == null) {
            observations.add("restart marker placement: failed missing crafted table target");
            return new ClientScenarioReport("failed", id, observations);
        }

        PlayablePersistenceStage.writeMarker(PlayablePersistenceStage.saveRestartMarkerPath(screenshotsDir), ironSword.tableTarget());
        observations.add("restart marker placement: passed target=" + coordinates(ironSword.tableTarget()));
        observations.add("runner-managed restart: pending clean server restart and iron-sword rejoin check");
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport("passed", id, observations);
    }

    static ClientScenarioReport runIronSwordSaveRestartAfter(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = PlayablePersistenceStage.saveRestartMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing restart marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioBlockTarget marker = PlayablePersistenceStage.readMarker(markerPath, "restart-marker");
        boolean markerPersisted = client.waitForBlock(marker, "minecraft:crafting_table", BLOCK_TIMEOUT);
        int ironSwordCount = client.inventoryCount("minecraft:iron_sword");
        boolean inventoryPersisted = ironSwordCount >= 1;
        observations.add(
            "restart marker persistence: " + (markerPersisted ? "passed" : "failed")
                + " target=" + coordinates(marker)
        );
        observations.add(
            "iron sword inventory persistence: " + (inventoryPersisted ? "passed" : "failed")
                + " iron_sword_count=" + ironSwordCount
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport(
            markerPersisted && inventoryPersisted ? "passed" : "failed",
            id,
            observations
        );
    }



    static IronSwordProgressionResult craftEarnedIronSwordProgression(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        IronProgressionBaseResult base = prepareIronProgressionBase(id, observations, client);
        if (!"passed".equals(base.report().result())) {
            return new IronSwordProgressionResult(base.report(), null);
        }

        for (int ingot = 0; ingot < 2; ingot++) {
            ClientScenarioReport rawIron = mineRawIronWithStonePickaxe(id, observations, client);
            if (!"passed".equals(rawIron.result())) {
                return new IronSwordProgressionResult(rawIron, base.prepared().tableTarget());
            }
            ClientScenarioReport smelt = PlayableHeatStage.smeltRawIronInPlacedFurnace(
                id,
                observations,
                client,
                base.placedFurnace(),
                base.prepared().planks()
            );
            if (!"passed".equals(smelt.result())) {
                return new IronSwordProgressionResult(smelt, base.prepared().tableTarget());
            }
        }

        boolean tableApproached = client.approachBlock(base.prepared().tableTarget(), APPROACH_TIMEOUT);
        observations.add(
            "crafting table approach for iron sword: " + (tableApproached ? "passed" : "failed")
                + " target=" + coordinates(base.prepared().tableTarget())
        );
        if (!tableApproached) {
            return new IronSwordProgressionResult(
                new ClientScenarioReport("blocked", id, observations),
                base.prepared().tableTarget()
            );
        }

        ScenarioHeldItem stonePickaxe = client.selectHotbarItem("minecraft:stone_pickaxe", 1, HOTBAR_TIMEOUT);
        ScenarioBlockTarget tableUseTarget = PlayableCoordinates.reachableUseTarget(client, base.prepared().tableTarget());
        ScenarioUseResult openUse = client.useItemOn(tableUseTarget, stonePickaxe);
        boolean opened = client.waitForScreenClassName(CRAFTING_SCREEN, INVENTORY_TIMEOUT);
        observations.add(
            "crafting table reopen for iron sword: " + (opened ? "passed" : "failed")
                + " open_use_result=" + openUse.result()
                + " screen=" + CRAFTING_SCREEN
                + " screen_matched=" + opened
        );
        if (!opened) {
            return new IronSwordProgressionResult(
                new ClientScenarioReport("failed", id, observations),
                base.prepared().tableTarget()
            );
        }

        ClientScenarioReport ironSword = craftIronSwordInOpenTable(
            id,
            observations,
            client,
            base.prepared().planks().planksItemId(),
            true
        );
        if (!"passed".equals(ironSword.result())) {
            return new IronSwordProgressionResult(ironSword, base.prepared().tableTarget());
        }

        return new IronSwordProgressionResult(
            new ClientScenarioReport("passed", id, observations),
            base.prepared().tableTarget()
        );
    }





}
