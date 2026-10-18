package dev.solaris.agent.javaagent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Shared deterministic client behavior for playable story contract tests.

final class FakeScenarioClient implements ScenarioClient {
        final List<String> operations = new ArrayList<>();
        Duration minimumZombieScanTimeout = Duration.ZERO;
        String logItemId = "minecraft:oak_log";
        String planksItemId = "minecraft:oak_planks";
        int oakLogs = 0;
        int oakPlanks = 0;
        int sticks = 0;
        int craftingTables = 0;
        int woodenPickaxes = 0;
        int woodenSwords = 0;
        int woodenHoes = 0;
        int cobblestones = 0;
        int excavationCobblestoneReserve = -1;
        int cobblestoneSlabs = 0;
        int stonePickaxes = 0;
        int stoneSwords = 0;
        int furnaces = 0;
        int rawIron = 0;
        int ironIngots = 0;
        int totalExperience = 0;
        int furnaceBurnRemaining = 0;
        int furnaceBurnTotal = 300;
        int furnaceCookProgress = 0;
        int furnaceCookTotal = 200;
        int ironSwords = 0;
        int shields = 0;
        boolean shieldBlockedAttackObserved = true;
        int ironChestplates = 0;
        int chests = 0;
        int charcoals = 0;
        int torches = 0;
        int beef = 0;
        int porkchops = 0;
        int chickens = 0;
        int cookedBeef = 0;
        int cookedPorkchops = 0;
        int cookedChickens = 0;
        String sheepWoolItemId = "minecraft:white_wool";
        String firstObservedSheepWoolItemId = "minecraft:white_wool";
        int sheepWool = 0;
        int sheepBeds = 0;
        int rottenFlesh = 0;
        int doors = 0;
        int signs = 0;
        int campfires = 0;
        int wheatSeeds = 0;
        int wheat = 0;
        int breads = 0;
        int diamonds = 0;
        int lapisLazuli = 0;
        int farmPlots = 0;
        int shortGrassScans = 0;
        int foodLevel = 20;
        float healthAfterHostileCombat = 20.0F;
        boolean visiblePassiveDuringSoak = false;
        boolean visibleHostileDuringSoak = false;
        boolean visibleHostileOutsideReachDuringSoak = false;
        String visibleHostileTypeDuringSoak = "minecraft:zombie";
        boolean tickProgressDuringSoak = true;
        long clientTicks = 0L;
        final String[] containerItemIds = new String[27];
        final int[] containerCounts = new int[27];
        boolean nearLogsAvailable = true;
        long logsUnavailableDuringSoakAfterTick = Long.MAX_VALUE;
        boolean nearStoneAvailable = true;
        int naturalStoneOverburdenRemaining = 0;
        boolean failStoneOverburdenBreak = false;
        boolean failExcavationExit = false;
        boolean fourthLogIsReachableDownFace = false;
        boolean failPostFurnaceLogApproach = false;
        boolean failCloseCurrentScreen = false;
        boolean failCraftingTableApproachForFurnace = false;
        boolean failCraftingTableApproachForCampfire = false;
        boolean failFirstCampfireReserveLogApproach = false;
        boolean failFirstNaturalLogFarApproach = false;
        boolean failFirstNaturalStoneFarApproach = false;
        boolean failedNaturalLogFarApproach = false;
        boolean failedCampfireReserveLogApproach = false;
        boolean failedNaturalStoneFarApproach = false;
        boolean equippedIronChestplate = false;
        int failedCobblestonePickupsRemaining = 0;
        int failedRawIronPickupsRemaining = 0;
        int failedLogPickupsRemaining = 0;
        int failedTorchPickupsRemaining = 0;
        int missingReachableStoneScansRemaining = 0;
        int missingReachableIronScansRemaining = 0;
        int rawIronPickupBatchSize = 1;
        int droppedWoodenPickaxes = 0;
        int droppedWoodenSwords = 0;
        boolean dieOnNextHostileAttack = false;
        double outsideReachHostileDistanceSquared = 2500.0;
        int visibleHostileRepeats = 0;
        final List<ScenarioCombatRecord> attackTelemetry = new ArrayList<>();
        private final List<ScenarioCombatRecord> combatTelemetry = new ArrayList<>();
        long dieDuringDropBaselineAfterTick = Long.MAX_VALUE;
        boolean deathMaterializedDuringDropBaseline = false;
        boolean woodenPickaxePickupObserved = true;
        boolean woodenPickaxeEntityDisappeared = true;
        boolean postDeathWoodenPickaxeEntityVisible = true;
        boolean postDeathWoodenSwordEntityVisible = true;
        boolean returnPreexistingWoodenPickaxeEntity = false;
        boolean woodenPickaxeIdentityLostBeforePickup = false;
        boolean unrelatedWoodenPickaxePickedUpAfterIdentityLoss = false;
        int preexistingWoodenPickaxeEntityId = -1;
        int preexistingWoodenSwordEntityId = -1;
        int deathDropWoodenPickaxeEntityId = 701;
        int deathDropWoodenSwordEntityId = 702;
        boolean craftingTablePlacementObserved = true;
        String craftingTablePlaceUseResult = "success";
        UUID preexistingWoodenPickaxeEntityUuid =
            UUID.fromString("00000000-0000-0000-0000-000000000700");
        UUID preexistingWoodenSwordEntityUuid =
            UUID.fromString("00000000-0000-0000-0000-000000000710");
        UUID deathDropWoodenPickaxeEntityUuid =
            UUID.fromString("00000000-0000-0000-0000-000000000701");
        UUID deathDropWoodenSwordEntityUuid =
            UUID.fromString("00000000-0000-0000-0000-000000000702");
        int visiblePlayerEntityId = 777;
        int cropSkyLight = 15;
        boolean generatedRuinChestPresent;
        boolean stonecutterPlaced;
        boolean stonecutterMenuOpen;
        boolean stonecutterOfferSelected;
        int stonecutterSlabOfferId;
        String stonecutterScreenClassName;
        int activeContainerId = 7;
        int nextStonecutterContainerId = 8;
        boolean reuseStonecutterContainerIdOnReopen;

        void configureGeneratedRuinCache() {
            generatedRuinChestPresent = true;
            containerItemIds[0] = "minecraft:diamond";
            containerCounts[0] = 1;
            containerItemIds[1] = "minecraft:lapis_lazuli";
            containerCounts[1] = 4;
            containerItemIds[2] = "minecraft:bread";
            containerCounts[2] = 2;
        }

        boolean usedDebugSetup() {
            return operations.stream().anyMatch(operation -> operation.startsWith("debug:"));
        }

        @Override
        public ScenarioHeldItem selectedItem() {
            operations.add("selected");
            return new ScenarioHeldItem("minecraft:air", 0);
        }

        @Override
        public boolean waitForTicks(long ticks, Duration timeout) {
            operations.add("wait_ticks:" + ticks);
            if (tickProgressDuringSoak) {
                clientTicks += ticks;
            }
            return tickProgressDuringSoak;
        }

        @Override
        public long serverGameTime() {
            operations.add("serverGameTime");
            return clientTicks;
        }

        @Override
        public long waitForServerTimeAfter(long baseline, Duration timeout) {
            operations.add("waitForServerTimeAfter:" + baseline);
            if (tickProgressDuringSoak) {
                clientTicks = Math.max(clientTicks, baseline) + 20L;
            }
            return clientTicks;
        }

        @Override
        public int inventoryCount(String itemId) {
            operations.add("count:" + itemId);
            if (logItemId.equals(itemId)) {
                return oakLogs;
            }
            if (planksItemId.equals(itemId)) {
                return oakPlanks;
            }
            if ("minecraft:stick".equals(itemId)) {
                return sticks;
            }
            if ("minecraft:crafting_table".equals(itemId)) {
                return craftingTables;
            }
            if ("minecraft:wooden_pickaxe".equals(itemId)) {
                return woodenPickaxes;
            }
            if ("minecraft:wooden_sword".equals(itemId)) {
                return woodenSwords;
            }
            if ("minecraft:wooden_hoe".equals(itemId)) {
                return woodenHoes;
            }
            if ("minecraft:cobblestone".equals(itemId)) {
                return cobblestones;
            }
            if ("minecraft:cobblestone_slab".equals(itemId)) {
                return cobblestoneSlabs;
            }
            if ("minecraft:stone_pickaxe".equals(itemId)) {
                return stonePickaxes;
            }
            if ("minecraft:stone_sword".equals(itemId)) {
                return stoneSwords;
            }
            if ("minecraft:furnace".equals(itemId)) {
                return furnaces;
            }
            if ("minecraft:raw_iron".equals(itemId)) {
                return rawIron;
            }
            if ("minecraft:iron_ingot".equals(itemId)) {
                return ironIngots;
            }
            if ("minecraft:iron_sword".equals(itemId)) {
                return ironSwords;
            }
            if ("minecraft:shield".equals(itemId)) {
                return shields;
            }
            if ("minecraft:iron_chestplate".equals(itemId)) {
                return ironChestplates;
            }
            if ("minecraft:chest".equals(itemId)) {
                return chests;
            }
            if ("minecraft:charcoal".equals(itemId)) {
                return charcoals;
            }
            if ("minecraft:torch".equals(itemId)) {
                return torches;
            }
            if ("minecraft:beef".equals(itemId)) {
                return beef;
            }
            if ("minecraft:porkchop".equals(itemId)) {
                return porkchops;
            }
            if ("minecraft:chicken".equals(itemId)) {
                return chickens;
            }
            if ("minecraft:cooked_beef".equals(itemId)) {
                return cookedBeef;
            }
            if ("minecraft:cooked_porkchop".equals(itemId)) {
                return cookedPorkchops;
            }
            if ("minecraft:cooked_chicken".equals(itemId)) {
                return cookedChickens;
            }
            if (sheepWoolItemId.equals(itemId)) {
                return sheepWool;
            }
            if (sheepBedItemId().equals(itemId)) {
                return sheepBeds;
            }
            if ("minecraft:rotten_flesh".equals(itemId)) {
                return rottenFlesh;
            }
            if (doorItemId().equals(itemId)) {
                return doors;
            }
            if (signItemId().equals(itemId)) {
                return signs;
            }
            if ("minecraft:campfire".equals(itemId)) {
                return campfires;
            }
            if ("minecraft:wheat_seeds".equals(itemId)) {
                return wheatSeeds;
            }
            if ("minecraft:wheat".equals(itemId)) {
                return wheat;
            }
            if ("minecraft:bread".equals(itemId)) {
                return breads;
            }
            if ("minecraft:diamond".equals(itemId)) {
                return diamonds;
            }
            if ("minecraft:lapis_lazuli".equals(itemId)) {
                return lapisLazuli;
            }
            return 0;
        }

        @Override
        public ScenarioBlockTarget findBreakableBlock(List<String> blockIds, ScenarioReach reach) {
            operations.add("findBreakable:" + String.join("|", blockIds) + ":" + reach.label());
            if (blockIds.contains("minecraft:short_grass")) {
                int offset = shortGrassScans++;
                return new ScenarioBlockTarget(
                    2 + offset,
                    65,
                    2,
                    "up",
                    "natural-short-grass-" + offset,
                    "minecraft:short_grass"
                );
            }
            if (blockIds.contains("minecraft:stone")) {
                if (reach == ScenarioReach.WITHIN_SURVIVAL_REACH && missingReachableStoneScansRemaining > 0) {
                    missingReachableStoneScansRemaining -= 1;
                    return null;
                }
                if (reach == ScenarioReach.WITHIN_SURVIVAL_REACH && !nearStoneAvailable) {
                    return null;
                }
                if (reach == ScenarioReach.OUTSIDE_SURVIVAL_REACH) {
                    return new ScenarioBlockTarget(9, 62, 6, "up", "far-natural-stone", "minecraft:stone");
                }
                return new ScenarioBlockTarget(5, 62, 6, "up", "natural-stone", "minecraft:stone");
            }
            if (blockIds.contains("minecraft:campfire") && campfires > 0) {
                return new ScenarioBlockTarget(1, 65, 1, "up", "campfire-target", "minecraft:campfire");
            }
            if (blockIds.contains("minecraft:iron_ore")) {
                if (reach == ScenarioReach.WITHIN_SURVIVAL_REACH && missingReachableIronScansRemaining > 0) {
                    missingReachableIronScansRemaining -= 1;
                    return null;
                }
                if (reach == ScenarioReach.OUTSIDE_SURVIVAL_REACH) {
                    return new ScenarioBlockTarget(11, 61, 7, "up", "far-natural-iron-ore", "minecraft:iron_ore");
                }
                return new ScenarioBlockTarget(6, 61, 7, "up", "natural-iron-ore", "minecraft:iron_ore");
            }
            if (!blockIds.contains(logItemId)) {
                return null;
            }
            if (woodenSwords > 0 && clientTicks >= logsUnavailableDuringSoakAfterTick) {
                return null;
            }
            if (
                failFirstCampfireReserveLogApproach
                    && !failedCampfireReserveLogApproach
                    && reach == ScenarioReach.WITHIN_SURVIVAL_REACH
                    && woodenPickaxes > 0
            ) {
                return null;
            }
            if (reach == ScenarioReach.WITHIN_SURVIVAL_REACH && !nearLogsAvailable) {
                return null;
            }
            if (reach == ScenarioReach.OUTSIDE_SURVIVAL_REACH) {
                return new ScenarioBlockTarget(8, 64, 5, "up", "far-natural-log", logItemId);
            }
            if (fourthLogIsReachableDownFace && oakLogs >= 3) {
                return new ScenarioBlockTarget(8, 82, 6, "down", "upper-natural-log", logItemId);
            }
            return new ScenarioBlockTarget(4, 64, 5, "up", "natural-log", logItemId);
        }

        @Override
        public boolean approachBlock(ScenarioBlockTarget target, Duration timeout) {
            operations.add("approach:" + target.blockId() + ":" + target.label());
            nearLogsAvailable = true;
            nearStoneAvailable = naturalStoneOverburdenRemaining == 0;
            if (naturalStoneOverburdenRemaining > 0 && "far-natural-stone".equals(target.label())) {
                return false;
            }
            if (failPostFurnaceLogApproach && furnaces > 0 && logItemId.equals(target.blockId())) {
                return false;
            }
            if (failCraftingTableApproachForFurnace && "table-target".equals(target.label()) && cobblestones >= 8) {
                return false;
            }
            if (
                failCraftingTableApproachForCampfire
                    && "table-target".equals(target.label())
                    && charcoals > 0
                    && oakLogs >= 3
            ) {
                return false;
            }
            if (
                failFirstNaturalLogFarApproach
                    && !failedNaturalLogFarApproach
                    && "far-natural-log".equals(target.label())
            ) {
                failedNaturalLogFarApproach = true;
                return false;
            }
            if (
                failFirstNaturalStoneFarApproach
                    && !failedNaturalStoneFarApproach
                    && "far-natural-stone".equals(target.label())
            ) {
                failedNaturalStoneFarApproach = true;
                return false;
            }
            if (
                failFirstCampfireReserveLogApproach
                    && !failedCampfireReserveLogApproach
                    && "far-natural-log".equals(target.label())
                    && woodenPickaxes > 0
            ) {
                failedCampfireReserveLogApproach = true;
                return false;
            }
            if ("upper-natural-log".equals(target.label())) {
                return false;
            }
            return true;
        }

        @Override
        public boolean approachPosition(int x, int z, Duration timeout) {
            operations.add("approachPosition:" + x + ":" + z);
            return x == 72 && z == 8;
        }

        @Override
        public boolean approachColumn(int x, int z, Duration timeout) {
            operations.add("approachColumn:" + x + ":" + z);
            return naturalStoneOverburdenRemaining > 0 && x == 9 && z == 6;
        }

        @Override
        public boolean excavateStaircaseToSurface(int surfaceY, int minimumCobblestoneCount, Duration timeout) {
            operations.add("excavationExit:" + surfaceY);
            excavationCobblestoneReserve = minimumCobblestoneCount;
            return !failExcavationExit;
        }

        @Override
        public ScenarioBlockTarget findLoadedBlockInColumn(int x, int z, List<String> blockIds) {
            operations.add("findColumn:" + x + ":" + z + ":" + String.join("|", blockIds));
            if (naturalStoneOverburdenRemaining > 0 && x == 9 && z == 6
                && blockIds.contains("minecraft:dirt")) {
                return new ScenarioBlockTarget(
                    x, 62 + naturalStoneOverburdenRemaining, z, "up", "stone-overburden",
                    naturalStoneOverburdenRemaining == 4 ? "minecraft:grass_block" : "minecraft:dirt"
                );
            }
            if (generatedRuinChestPresent && x == 72 && z == 8 && blockIds.contains("minecraft:chest")) {
                return new ScenarioBlockTarget(72, 61, 8, "up", "generated-ruin-cache", "minecraft:chest");
            }
            return null;
        }

        @Override
        public ScenarioBreakResult breakBlockUntilDropVisible(
            ScenarioBlockTarget target,
            String expectedDropItemId,
            Duration timeout
        ) {
            operations.add("breakVisible:" + target.blockId() + ":" + expectedDropItemId);
            if ("stone-overburden".equals(target.label())) {
                if (failStoneOverburdenBreak) {
                    return new ScenarioBreakResult(
                        false, false, false, false, new ScenarioHeldItem("minecraft:air", 0)
                    );
                }
                naturalStoneOverburdenRemaining -= 1;
                nearStoneAvailable = naturalStoneOverburdenRemaining == 0;
            }
            return new ScenarioBreakResult(true, true, true, false, new ScenarioHeldItem("minecraft:air", 0));
        }

        @Override
        public ScenarioBreakResult collectVisibleItemDrop(
            ScenarioBlockTarget near,
            String expectedDropItemId,
            int expectedSelectedCount,
            Duration timeout
        ) {
            operations.add("collect:" + near.blockId() + ":" + expectedDropItemId + ":" + expectedSelectedCount);
            if (logItemId.equals(expectedDropItemId)) {
                if (failedLogPickupsRemaining > 0) {
                    failedLogPickupsRemaining -= 1;
                    return new ScenarioBreakResult(
                        true,
                        true,
                        true,
                        false,
                        new ScenarioHeldItem("minecraft:birch_leaves", 5)
                    );
                }
                oakLogs += expectedSelectedCount;
            } else if ("minecraft:cobblestone".equals(expectedDropItemId)) {
                if (failedCobblestonePickupsRemaining > 0) {
                    failedCobblestonePickupsRemaining -= 1;
                    return new ScenarioBreakResult(
                        true,
                        true,
                        true,
                        false,
                        new ScenarioHeldItem("minecraft:wooden_pickaxe", 1)
                    );
                }
                cobblestones += expectedSelectedCount;
            } else if ("minecraft:raw_iron".equals(expectedDropItemId)) {
                if (failedRawIronPickupsRemaining > 0) {
                    failedRawIronPickupsRemaining -= 1;
                    return new ScenarioBreakResult(
                        true,
                        true,
                        true,
                        false,
                        new ScenarioHeldItem("minecraft:stone_pickaxe", 1),
                        "player=(10.0,79.0,7.0) drop=(12.5,77.0,7.5) distance_squared=10.5"
                    );
                }
                rawIron += Math.max(expectedSelectedCount, rawIronPickupBatchSize);
            } else if ("minecraft:torch".equals(expectedDropItemId) && failedTorchPickupsRemaining > 0) {
                failedTorchPickupsRemaining -= 1;
                return new ScenarioBreakResult(
                    true,
                    true,
                    true,
                    false,
                    new ScenarioHeldItem("minecraft:torch", 3)
                );
            } else if ("minecraft:cooked_beef".equals(expectedDropItemId)) {
                cookedBeef += expectedSelectedCount;
            } else if ("minecraft:cooked_porkchop".equals(expectedDropItemId)) {
                cookedPorkchops += expectedSelectedCount;
            } else if ("minecraft:cooked_chicken".equals(expectedDropItemId)) {
                cookedChickens += expectedSelectedCount;
            } else if ("minecraft:wooden_pickaxe".equals(expectedDropItemId)) {
                if (droppedWoodenPickaxes < expectedSelectedCount) {
                    return new ScenarioBreakResult(false, false, true, false, new ScenarioHeldItem("minecraft:air", 0));
                }
                droppedWoodenPickaxes -= expectedSelectedCount;
                woodenPickaxes += expectedSelectedCount;
                return new ScenarioBreakResult(
                    true,
                    woodenPickaxeEntityDisappeared,
                    woodenPickaxePickupObserved,
                    true,
                    new ScenarioHeldItem(expectedDropItemId, 1)
                );
            } else if ("minecraft:wooden_sword".equals(expectedDropItemId)) {
                if (droppedWoodenSwords < expectedSelectedCount) {
                    return new ScenarioBreakResult(false, false, true, false, new ScenarioHeldItem("minecraft:air", 0));
                }
                droppedWoodenSwords -= expectedSelectedCount;
                woodenSwords += expectedSelectedCount;
                return new ScenarioBreakResult(
                    true,
                    true,
                    true,
                    true,
                    new ScenarioHeldItem(expectedDropItemId, 1)
                );
            } else if ("minecraft:wheat_seeds".equals(expectedDropItemId)) {
                wheatSeeds += expectedSelectedCount;
            } else if ("minecraft:wheat".equals(expectedDropItemId)) {
                wheat += expectedSelectedCount;
            }
            return new ScenarioBreakResult(true, true, true, true, new ScenarioHeldItem(expectedDropItemId, 1));
        }

        @Override
        public List<ScenarioItemDropIdentity> visibleItemDropIdentities(String itemId) {
            operations.add("visibleDropIdentities:" + itemId);
            if (
                "minecraft:wooden_pickaxe".equals(itemId)
                    && !deathMaterializedDuringDropBaseline
                    && healthAfterHostileCombat > 0.0F
                    && clientTicks >= dieDuringDropBaselineAfterTick
            ) {
                healthAfterHostileCombat = 0.0F;
                materializeSurvivalDeathDrops();
                deathMaterializedDuringDropBaseline = true;
            }
            List<ScenarioItemDropIdentity> identities = new ArrayList<>();
            if ("minecraft:wooden_pickaxe".equals(itemId) && preexistingWoodenPickaxeEntityId >= 0) {
                identities.add(new ScenarioItemDropIdentity(
                    preexistingWoodenPickaxeEntityId,
                    preexistingWoodenPickaxeEntityUuid
                ));
            }
            if ("minecraft:wooden_sword".equals(itemId) && preexistingWoodenSwordEntityId >= 0) {
                identities.add(new ScenarioItemDropIdentity(
                    preexistingWoodenSwordEntityId,
                    preexistingWoodenSwordEntityUuid
                ));
            }
            if (
                "minecraft:wooden_pickaxe".equals(itemId)
                    && deathMaterializedDuringDropBaseline
                    && droppedWoodenPickaxes > 0
                    && postDeathWoodenPickaxeEntityVisible
            ) {
                identities.add(new ScenarioItemDropIdentity(
                    deathDropWoodenPickaxeEntityId,
                    deathDropWoodenPickaxeEntityUuid
                ));
            }
            if (
                "minecraft:wooden_sword".equals(itemId)
                    && deathMaterializedDuringDropBaseline
                    && droppedWoodenSwords > 0
                    && postDeathWoodenSwordEntityVisible
            ) {
                identities.add(new ScenarioItemDropIdentity(
                    deathDropWoodenSwordEntityId,
                    deathDropWoodenSwordEntityUuid
                ));
            }
            return List.copyOf(identities);
        }

        @Override
        public ScenarioItemDropIdentity waitForNewVisibleItemDropIdentity(
            String itemId,
            List<ScenarioItemDropIdentity> excludedIdentities,
            Duration timeout
        ) {
            operations.add(
                "waitNewDropIdentity:" + itemId + ":excluded=" + excludedIdentities
            );
            if (!postDeathWoodenPickaxeEntityVisible) {
                return null;
            }
            if ("minecraft:wooden_pickaxe".equals(itemId)) {
                ScenarioItemDropIdentity identity = returnPreexistingWoodenPickaxeEntity
                    ? new ScenarioItemDropIdentity(
                        preexistingWoodenPickaxeEntityId,
                        preexistingWoodenPickaxeEntityUuid
                    )
                    : new ScenarioItemDropIdentity(
                        deathDropWoodenPickaxeEntityId,
                        deathDropWoodenPickaxeEntityUuid
                    );
                if (deathMaterializedDuringDropBaseline && excludedIdentities.contains(identity)) {
                    return null;
                }
                return identity;
            }
            if ("minecraft:wooden_sword".equals(itemId)) {
                ScenarioItemDropIdentity identity = new ScenarioItemDropIdentity(
                    deathDropWoodenSwordEntityId,
                    deathDropWoodenSwordEntityUuid
                );
                if (deathMaterializedDuringDropBaseline && excludedIdentities.contains(identity)) {
                    return null;
                }
                return identity;
            }
            return null;
        }

        @Override
        public ScenarioBreakResult collectVisibleItemDropByIdentity(
            ScenarioBlockTarget near,
            String expectedDropItemId,
            ScenarioItemDropIdentity expectedIdentity,
            int expectedSelectedCount,
            Duration timeout
        ) {
            operations.add(
                "collectIdentity:" + expectedDropItemId + ":" + expectedIdentity.entityId()
                    + ":" + expectedIdentity.uuid()
            );
            ScenarioItemDropIdentity deathDropIdentity = switch (expectedDropItemId) {
                case "minecraft:wooden_pickaxe" -> new ScenarioItemDropIdentity(
                    deathDropWoodenPickaxeEntityId,
                    deathDropWoodenPickaxeEntityUuid
                );
                case "minecraft:wooden_sword" -> new ScenarioItemDropIdentity(
                    deathDropWoodenSwordEntityId,
                    deathDropWoodenSwordEntityUuid
                );
                default -> null;
            };
            if (deathDropIdentity == null || !deathDropIdentity.equals(expectedIdentity)) {
                return new ScenarioBreakResult(true, true, false, false, new ScenarioHeldItem("minecraft:air", 0));
            }
            if ("minecraft:wooden_pickaxe".equals(expectedDropItemId) && woodenPickaxeIdentityLostBeforePickup) {
                if (unrelatedWoodenPickaxePickedUpAfterIdentityLoss) {
                    woodenPickaxes += expectedSelectedCount;
                }
                return new ScenarioBreakResult(
                    true,
                    true,
                    true,
                    false,
                    new ScenarioHeldItem("minecraft:air", 0)
                );
            }
            return collectVisibleItemDrop(near, expectedDropItemId, expectedSelectedCount, timeout);
        }

        @Override
        public int recipeDisplayIdForResult(String itemId) {
            if (itemId.equals(planksItemId)) {
                return planksRecipeDisplayId();
            }
            if (itemId.endsWith("_door")) {
                return doorRecipeDisplayId();
            }
            if (itemId.endsWith("_sign")) {
                return signRecipeDisplayId();
            }
            return switch (itemId) {
                case "minecraft:white_bed" -> sheepBedRecipeDisplayId();
                case "minecraft:chest" -> 5;
                case "minecraft:crafting_table" -> 10;
                case "minecraft:furnace" -> 13;
                case "minecraft:stick" -> 21;
                case "minecraft:stone_pickaxe" -> 24;
                case "minecraft:stone_sword" -> 26;
                case "minecraft:torch" -> 27;
                case "minecraft:wooden_hoe" -> 30;
                case "minecraft:wooden_pickaxe" -> 31;
                case "minecraft:wooden_sword" -> 32;
                case "minecraft:campfire" -> 53;
                case "minecraft:iron_sword" -> 57;
                case "minecraft:shield" -> 58;
                case "minecraft:iron_chestplate" -> 59;
                case "minecraft:bread" -> 60;
                default -> throw new IllegalArgumentException("unsupported test recipe result " + itemId);
            };
        }

        @Override
        public void placeRecipe(int containerId, int recipeDisplayId, boolean useMaxItems) {
            operations.add("recipe:" + containerId + ":" + recipeDisplayId + ":" + useMaxItems);
            if (recipeDisplayId == planksRecipeDisplayId()) {
                int logsToCraft = useMaxItems ? oakLogs : Math.min(oakLogs, 1);
                oakLogs -= logsToCraft;
                oakPlanks += logsToCraft * 4;
            } else if (recipeDisplayId == 10) {
                if (oakPlanks >= 4) {
                    oakPlanks -= 4;
                    craftingTables += 1;
                }
            } else if (recipeDisplayId == 21) {
                if (oakPlanks >= 2) {
                    oakPlanks -= 2;
                    sticks += 4;
                }
            } else if (recipeDisplayId == 31) {
                if (oakPlanks >= 3 && sticks >= 2) {
                    oakPlanks -= 3;
                    sticks -= 2;
                    woodenPickaxes += 1;
                }
            } else if (recipeDisplayId == 32) {
                if (oakPlanks >= 2 && sticks >= 1) {
                    oakPlanks -= 2;
                    sticks -= 1;
                    woodenSwords += 1;
                }
            } else if (recipeDisplayId == 30) {
                if (oakPlanks >= 2 && sticks >= 2) {
                    oakPlanks -= 2;
                    sticks -= 2;
                    woodenHoes += 1;
                }
            } else if (recipeDisplayId == 24) {
                if (cobblestones >= 3 && sticks >= 2) {
                    cobblestones -= 3;
                    sticks -= 2;
                    stonePickaxes += 1;
                }
            } else if (recipeDisplayId == 26) {
                if (cobblestones >= 2 && sticks >= 1) {
                    cobblestones -= 2;
                    sticks -= 1;
                    stoneSwords += 1;
                }
            } else if (recipeDisplayId == 13) {
                if (cobblestones >= 8) {
                    cobblestones -= 8;
                    furnaces += 1;
                }
            } else if (recipeDisplayId == 57) {
                if (ironIngots >= 2 && sticks >= 1) {
                    ironIngots -= 2;
                    sticks -= 1;
                    ironSwords += 1;
                }
            } else if (recipeDisplayId == 58) {
                if (oakPlanks >= 6 && ironIngots >= 1) {
                    oakPlanks -= 6;
                    ironIngots -= 1;
                    shields += 1;
                }
            } else if (recipeDisplayId == 59) {
                if (ironIngots >= 8) {
                    ironIngots -= 8;
                    ironChestplates += 1;
                }
            } else if (recipeDisplayId == 5) {
                if (oakPlanks >= 8) {
                    oakPlanks -= 8;
                    chests += 1;
                }
            } else if (recipeDisplayId == 27) {
                if (charcoals >= 1 && sticks >= 1) {
                    charcoals -= 1;
                    sticks -= 1;
                    torches += 4;
                }
            } else if (recipeDisplayId == sheepBedRecipeDisplayId()) {
                if (oakPlanks >= 3 && sheepWool >= 3) {
                    oakPlanks -= 3;
                    sheepWool -= 3;
                    sheepBeds += 1;
                }
            } else if (recipeDisplayId == doorRecipeDisplayId()) {
                if (oakPlanks >= 6) {
                    oakPlanks -= 6;
                    doors += 3;
                }
            } else if (recipeDisplayId == signRecipeDisplayId()) {
                if (oakPlanks >= 6 && sticks >= 1) {
                    oakPlanks -= 6;
                    sticks -= 1;
                    signs += 3;
                }
            } else if (recipeDisplayId == 53) {
                if (oakLogs >= 3 && sticks >= 3 && charcoals >= 1) {
                    oakLogs -= 3;
                    sticks -= 3;
                    charcoals -= 1;
                    campfires += 1;
                }
            } else if (recipeDisplayId == 60) {
                if (wheat >= 3) {
                    wheat -= 3;
                    breads += 1;
                }
            }
        }

        @Override
        public boolean waitForInventoryCount(String itemId, int count, Duration duration) {
            operations.add("waitCount:" + itemId + ":" + count);
            return inventoryCountWithoutRecording(itemId) == count;
        }

        @Override
        public int totalExperience() {
            operations.add("totalExperience");
            return totalExperience;
        }

        @Override
        public int waitForTotalExperienceAbove(int experience, Duration duration) {
            operations.add("waitExperienceAbove:" + experience);
            return totalExperience;
        }

        private int inventoryCountWithoutRecording(String itemId) {
            if (logItemId.equals(itemId)) {
                return oakLogs;
            }
            if (planksItemId.equals(itemId)) {
                return oakPlanks;
            }
            if ("minecraft:stick".equals(itemId)) {
                return sticks;
            }
            if ("minecraft:crafting_table".equals(itemId)) {
                return craftingTables;
            }
            if ("minecraft:wooden_pickaxe".equals(itemId)) {
                return woodenPickaxes;
            }
            if ("minecraft:wooden_sword".equals(itemId)) {
                return woodenSwords;
            }
            if ("minecraft:wooden_hoe".equals(itemId)) {
                return woodenHoes;
            }
            if ("minecraft:cobblestone".equals(itemId)) {
                return cobblestones;
            }
            if ("minecraft:cobblestone_slab".equals(itemId)) {
                return cobblestoneSlabs;
            }
            if ("minecraft:stone_pickaxe".equals(itemId)) {
                return stonePickaxes;
            }
            if ("minecraft:stone_sword".equals(itemId)) {
                return stoneSwords;
            }
            if ("minecraft:furnace".equals(itemId)) {
                return furnaces;
            }
            if ("minecraft:raw_iron".equals(itemId)) {
                return rawIron;
            }
            if ("minecraft:iron_ingot".equals(itemId)) {
                return ironIngots;
            }
            if ("minecraft:iron_sword".equals(itemId)) {
                return ironSwords;
            }
            if ("minecraft:shield".equals(itemId)) {
                return shields;
            }
            if ("minecraft:iron_chestplate".equals(itemId)) {
                return ironChestplates;
            }
            if ("minecraft:chest".equals(itemId)) {
                return chests;
            }
            if ("minecraft:charcoal".equals(itemId)) {
                return charcoals;
            }
            if ("minecraft:torch".equals(itemId)) {
                return torches;
            }
            if ("minecraft:beef".equals(itemId)) {
                return beef;
            }
            if ("minecraft:porkchop".equals(itemId)) {
                return porkchops;
            }
            if ("minecraft:chicken".equals(itemId)) {
                return chickens;
            }
            if ("minecraft:cooked_beef".equals(itemId)) {
                return cookedBeef;
            }
            if ("minecraft:cooked_porkchop".equals(itemId)) {
                return cookedPorkchops;
            }
            if ("minecraft:cooked_chicken".equals(itemId)) {
                return cookedChickens;
            }
            if (sheepWoolItemId.equals(itemId)) {
                return sheepWool;
            }
            if (sheepBedItemId().equals(itemId)) {
                return sheepBeds;
            }
            if ("minecraft:rotten_flesh".equals(itemId)) {
                return rottenFlesh;
            }
            if (doorItemId().equals(itemId)) {
                return doors;
            }
            if (signItemId().equals(itemId)) {
                return signs;
            }
            if ("minecraft:campfire".equals(itemId)) {
                return campfires;
            }
            if ("minecraft:wheat_seeds".equals(itemId)) {
                return wheatSeeds;
            }
            if ("minecraft:wheat".equals(itemId)) {
                return wheat;
            }
            if ("minecraft:bread".equals(itemId)) {
                return breads;
            }
            if ("minecraft:diamond".equals(itemId)) {
                return diamonds;
            }
            if ("minecraft:lapis_lazuli".equals(itemId)) {
                return lapisLazuli;
            }
            return 0;
        }

        private int planksRecipeDisplayId() {
            return switch (planksItemId) {
                case "minecraft:acacia_planks" -> 0;
                case "minecraft:birch_planks" -> 2;
                case "minecraft:cherry_planks" -> 5;
                case "minecraft:dark_oak_planks" -> 12;
                case "minecraft:jungle_planks" -> 16;
                case "minecraft:mangrove_planks" -> 17;
                case "minecraft:oak_planks" -> 18;
                case "minecraft:pale_oak_planks" -> 19;
                case "minecraft:spruce_planks" -> 20;
                default -> throw new IllegalStateException("unsupported test planks item " + planksItemId);
            };
        }

        private String doorItemId() {
            return switch (planksItemId) {
                case "minecraft:acacia_planks" -> "minecraft:acacia_door";
                case "minecraft:birch_planks" -> "minecraft:birch_door";
                case "minecraft:cherry_planks" -> "minecraft:cherry_door";
                case "minecraft:dark_oak_planks" -> "minecraft:dark_oak_door";
                case "minecraft:jungle_planks" -> "minecraft:jungle_door";
                case "minecraft:mangrove_planks" -> "minecraft:mangrove_door";
                case "minecraft:oak_planks" -> "minecraft:oak_door";
                case "minecraft:pale_oak_planks" -> "minecraft:pale_oak_door";
                case "minecraft:spruce_planks" -> "minecraft:spruce_door";
                default -> throw new IllegalStateException("unsupported test planks item " + planksItemId);
            };
        }

        private int doorRecipeDisplayId() {
            return switch (planksItemId) {
                case "minecraft:acacia_planks" -> 35;
                case "minecraft:birch_planks" -> 36;
                case "minecraft:cherry_planks" -> 37;
                case "minecraft:dark_oak_planks" -> 38;
                case "minecraft:jungle_planks" -> 39;
                case "minecraft:mangrove_planks" -> 40;
                case "minecraft:oak_planks" -> 41;
                case "minecraft:pale_oak_planks" -> 42;
                case "minecraft:spruce_planks" -> 43;
                default -> throw new IllegalStateException("unsupported test planks item " + planksItemId);
            };
        }

        private String signItemId() {
            return switch (planksItemId) {
                case "minecraft:acacia_planks" -> "minecraft:acacia_sign";
                case "minecraft:birch_planks" -> "minecraft:birch_sign";
                case "minecraft:cherry_planks" -> "minecraft:cherry_sign";
                case "minecraft:dark_oak_planks" -> "minecraft:dark_oak_sign";
                case "minecraft:jungle_planks" -> "minecraft:jungle_sign";
                case "minecraft:mangrove_planks" -> "minecraft:mangrove_sign";
                case "minecraft:oak_planks" -> "minecraft:oak_sign";
                case "minecraft:pale_oak_planks" -> "minecraft:pale_oak_sign";
                case "minecraft:spruce_planks" -> "minecraft:spruce_sign";
                default -> throw new IllegalStateException("unsupported test planks item " + planksItemId);
            };
        }

        private int signRecipeDisplayId() {
            return switch (planksItemId) {
                case "minecraft:acacia_planks" -> 44;
                case "minecraft:birch_planks" -> 45;
                case "minecraft:cherry_planks" -> 46;
                case "minecraft:dark_oak_planks" -> 47;
                case "minecraft:jungle_planks" -> 48;
                case "minecraft:mangrove_planks" -> 49;
                case "minecraft:oak_planks" -> 50;
                case "minecraft:pale_oak_planks" -> 51;
                case "minecraft:spruce_planks" -> 52;
                default -> throw new IllegalStateException("unsupported test planks item " + planksItemId);
            };
        }

        private String sheepBedItemId() {
            return sheepWoolItemId.replace("_wool", "_bed");
        }

        private int sheepBedRecipeDisplayId() {
            return "minecraft:white_wool".equals(sheepWoolItemId) ? 34 : 61;
        }

        @Override
        public ScenarioHeldItem selectHotbarItem(String itemId, int count, Duration timeout) {
            operations.add("selectHotbar:" + itemId + ":" + count);
            return new ScenarioHeldItem(itemId, count);
        }

        @Override
        public ScenarioBlockTarget dropSelectedItem(String itemId, int count, Duration timeout) {
            operations.add("dropSelected:" + itemId + ":" + count);
            decrementItem(itemId, count);
            return new ScenarioBlockTarget(
                4,
                64,
                5,
                "up",
                "playable-two-client-inventory-drop-marker",
                itemId
            );
        }

        @Override
        public ScenarioEntityObservation findVisibleEntity(
            List<String> entityTypeIds,
            ScenarioReach reach,
            Duration timeout
        ) {
            operations.add("findEntity:" + String.join("|", entityTypeIds) + ":" + reach.label());
            if (reach == ScenarioReach.OUTSIDE_SURVIVAL_REACH && entityTypeIds.contains("minecraft:sheep")) {
                return new ScenarioEntityObservation(
                    "minecraft:sheep",
                    43 + sheepWool,
                    entityUuid(43 + sheepWool),
                    9.5,
                    64.0,
                    8.5,
                    81.0,
                    firstObservedSheepWoolItemId
                );
            }
            if (reach == ScenarioReach.OUTSIDE_SURVIVAL_REACH && entityTypeIds.contains("minecraft:cow")) {
                return new ScenarioEntityObservation(
                    "minecraft:cow", 42, entityUuid(42), 8.5, 64.0, 8.5, 64.0, null
                );
            }
            if (reach == ScenarioReach.OUTSIDE_SURVIVAL_REACH && entityTypeIds.contains("minecraft:chicken")) {
                return new ScenarioEntityObservation(
                    "minecraft:chicken", 44, entityUuid(44), 10.5, 64.0, 8.5, 100.0, null
                );
            }
            if (reach == ScenarioReach.OUTSIDE_SURVIVAL_REACH && entityTypeIds.contains("minecraft:zombie")) {
                if (timeout.compareTo(minimumZombieScanTimeout) < 0) {
                    return null;
                }
                return new ScenarioEntityObservation(
                    "minecraft:zombie", 99, entityUuid(99), 12.5, 64.0, 8.5, 80.0, null
                );
            }
            return null;
        }

        @Override
        public ScenarioEntityObservation findVisibleSheepWithWool(
            String woolItemId,
            ScenarioReach reach,
            Duration timeout
        ) {
            operations.add("findSheepWool:" + woolItemId + ":" + reach.label());
            if (reach != ScenarioReach.OUTSIDE_SURVIVAL_REACH || !sheepWoolItemId.equals(woolItemId)) {
                return null;
            }
            return new ScenarioEntityObservation(
                "minecraft:sheep",
                100 + sheepWool,
                entityUuid(100 + sheepWool),
                9.5,
                64.0,
                8.5,
                81.0,
                sheepWoolItemId
            );
        }

        @Override
        public ScenarioEntityObservation visibleEntity(List<String> entityTypeIds, ScenarioReach reach) {
            operations.add("visibleEntity:" + String.join("|", entityTypeIds) + ":" + reach.label());
            if (visiblePassiveDuringSoak && entityTypeIds.contains("minecraft:cow")) {
                visiblePassiveDuringSoak = false;
                return new ScenarioEntityObservation(
                    "minecraft:cow",
                    198,
                    entityUuid(198),
                    3.5,
                    64.0,
                    3.5,
                    4.0,
                    null
                );
            }
            boolean shouldReturnHostile = visibleHostileDuringSoak && reach == ScenarioReach.WITHIN_SURVIVAL_REACH;
            shouldReturnHostile |= visibleHostileOutsideReachDuringSoak
                && reach == ScenarioReach.OUTSIDE_SURVIVAL_REACH;
            if (shouldReturnHostile) {
                visibleHostileDuringSoak = visibleHostileRepeats > 0;
                visibleHostileRepeats = Math.max(0, visibleHostileRepeats - 1);
                visibleHostileOutsideReachDuringSoak = false;
                if (!entityTypeIds.contains(visibleHostileTypeDuringSoak)) {
                    return null;
                }
                return new ScenarioEntityObservation(
                    visibleHostileTypeDuringSoak,
                    199,
                    entityUuid(199),
                    4.5,
                    64.0,
                    4.5,
                    reach == ScenarioReach.OUTSIDE_SURVIVAL_REACH ? outsideReachHostileDistanceSquared : 9.0,
                    null
                );
            }
            return null;
        }

        private static UUID entityUuid(int entityId) {
            return new UUID(0L, Integer.toUnsignedLong(entityId));
        }

        @Override
        public ScenarioEntityMotionObservation waitForEntityMotion(
            ScenarioEntityObservation entity,
            double minimumHorizontalDistance,
            double minimumVerticalRise,
            Duration timeout
        ) {
            operations.add(
                "motion:" + entity.entityType() + ":" + minimumHorizontalDistance + ":" + minimumVerticalRise
            );
            double speed = switch (entity.entityType()) {
                case "minecraft:cow" -> 0.10;
                case "minecraft:sheep" -> 0.115;
                case "minecraft:chicken" -> 0.125;
                default -> 0.0;
            };
            double verticalRise = "minecraft:cow".equals(entity.entityType()) ? 1.1 : 0.0;
            return new ScenarioEntityMotionObservation(
                entity.entityType(),
                entity.entityId(),
                entity.x() + 1.25,
                entity.y() + verticalRise,
                entity.z(),
                1.25,
                verticalRise,
                speed,
                1.5
            );
        }

        @Override
        public ScenarioPlayerObservation waitForVisiblePlayer(String playerName, Duration timeout) {
            operations.add("waitPlayer:" + playerName);
            if ("SolarisPrimary".equals(playerName)) {
                return new ScenarioPlayerObservation(
                    playerName,
                    visiblePlayerEntityId,
                    10.0,
                    64.0,
                    8.0,
                    16.0
                );
            }
            return null;
        }

        @Override
        public boolean waitForNoVisiblePlayer(String playerName, Duration timeout) {
            operations.add("waitNoPlayer:" + playerName);
            return "SolarisPrimary".equals(playerName);
        }

        @Override
        public ScenarioPlayerObservation waitForMovedPlayer(
            String playerName,
            ScenarioPlayerObservation baseline,
            double minHorizontalDelta,
            Duration timeout
        ) {
            operations.add("waitPlayerMoved:" + playerName + ":" + minHorizontalDelta);
            if ("SolarisPrimary".equals(playerName)) {
                return new ScenarioPlayerObservation(
                    playerName,
                    baseline.entityId(),
                    baseline.x() + minHorizontalDelta + 0.25,
                    baseline.y(),
                    baseline.z(),
                    baseline.distanceSquared()
                );
            }
            return null;
        }

        @Override
        public boolean approachEntity(ScenarioEntityObservation entity, Duration timeout) {
            String kind = "minecraft:zombie".equals(entity.entityType()) ? "loaded-hostile" : "loaded-passive";
            operations.add("approachEntity:" + entity.entityType() + ":" + kind);
            return true;
        }

        @Override
        public ScenarioBreakResult attackEntityUntilDropCollected(
            ScenarioEntityObservation entity,
            String expectedDropItemId,
            int expectedSelectedCount,
            Duration timeout
        ) {
            operations.add(
                "attackEntityDrop:"
                    + entity.entityType()
                    + ":entity_id="
                    + entity.entityId()
                    + ":"
                    + expectedDropItemId
                    + ":"
                    + expectedSelectedCount
            );
            if ("minecraft:beef".equals(expectedDropItemId)) {
                beef += expectedSelectedCount;
            } else if ("minecraft:porkchop".equals(expectedDropItemId)) {
                porkchops += expectedSelectedCount;
            } else if ("minecraft:chicken".equals(expectedDropItemId)) {
                chickens += expectedSelectedCount;
            } else if (sheepWoolItemId.equals(expectedDropItemId)) {
                sheepWool += expectedSelectedCount;
            } else if ("minecraft:sheep".equals(entity.entityType())) {
                return new ScenarioBreakResult(false, false, false, false, new ScenarioHeldItem("minecraft:air", 0));
            } else if ("minecraft:rotten_flesh".equals(expectedDropItemId)) {
                rottenFlesh += expectedSelectedCount;
            }
            return new ScenarioBreakResult(
                true,
                true,
                true,
                true,
                new ScenarioHeldItem(expectedDropItemId, expectedSelectedCount)
            );
        }

        @Override
        public boolean attackEntityUntilRemoved(ScenarioEntityObservation entity, Duration timeout) {
            operations.add("attackEntityUntilRemoved:" + entity.entityType() + ":" + entity.entityId());
            combatTelemetry.addAll(attackTelemetry);
            if (dieOnNextHostileAttack) {
                dieOnNextHostileAttack = false;
                healthAfterHostileCombat = 0.0F;
                materializeSurvivalDeathDrops();
                return false;
            }
            return true;
        }

        @Override
        public List<ScenarioCombatRecord> drainCombatTelemetry() {
            List<ScenarioCombatRecord> drained = List.copyOf(combatTelemetry);
            combatTelemetry.clear();
            return drained;
        }

        @Override
        public float playerHealth() {
            operations.add("playerHealth");
            return healthAfterHostileCombat;
        }

        @Override
        public float waitForPlayerHealthBelow(double health, Duration duration) {
            operations.add("waitHealthBelow:" + health);
            if (healthAfterHostileCombat >= health) {
                float damage = equippedIronChestplate ? 2.46F : 3.0F;
                healthAfterHostileCombat = Math.max(0.0F, healthAfterHostileCombat - damage);
            }
            return healthAfterHostileCombat;
        }

        @Override
        public ScenarioShieldBlockResult blockAttackWithSelectedShield(String itemId, Duration timeout) {
            operations.add("blockAttackWithSelectedShield:" + itemId);
            boolean started = "minecraft:shield".equals(itemId) && shields > 0;
            int damageAfter = started && shieldBlockedAttackObserved ? 4 : 0;
            return new ScenarioShieldBlockResult(
                started,
                started && shieldBlockedAttackObserved,
                healthAfterHostileCombat,
                healthAfterHostileCombat,
                0,
                damageAfter
            );
        }

        @Override
        public boolean quickEquipSelectedArmor(String itemId, String armorSlot, Duration duration) {
            operations.add("quickEquip:" + itemId + ":" + armorSlot);
            if ("minecraft:iron_chestplate".equals(itemId) && "chest".equals(armorSlot) && ironChestplates > 0) {
                ironChestplates -= 1;
                equippedIronChestplate = true;
                return true;
            }
            return "minecraft:iron_chestplate".equals(itemId)
                && "chest".equals(armorSlot)
                && equippedIronChestplate;
        }

        @Override
        public ScenarioHeldItem equippedArmor(String armorSlot) {
            operations.add("equippedArmor:" + armorSlot);
            if ("chest".equals(armorSlot) && equippedIronChestplate) {
                return new ScenarioHeldItem("minecraft:iron_chestplate", 1);
            }
            return new ScenarioHeldItem("minecraft:air", 0);
        }

        @Override
        public boolean drainHungerBySprinting(Duration timeout) {
            operations.add("drainHungerBySprinting");
            foodLevel = 19;
            return true;
        }

        @Override
        public ScenarioFoodUseResult eatSelectedFood(String itemId, int itemCountBefore, Duration timeout) {
            operations.add("eatSelectedFood:" + itemId + ":" + itemCountBefore);
            int beforeFood = foodLevel;
            int beforeCount = inventoryCountWithoutRecording(itemId);
            if (inventoryCountWithoutRecording(itemId) > 0 && foodLevel < 20) {
                decrementItem(itemId, 1);
                foodLevel = 20;
            }
            return new ScenarioFoodUseResult(
                true,
                beforeFood,
                foodLevel,
                beforeCount,
                inventoryCountWithoutRecording(itemId)
            );
        }

        @Override
        public ScenarioBlockPair findDryPlaceablePair(ScenarioReach reach) {
            operations.add("findDry:" + reach.label());
            if (woodenHoes > 0) {
                int plot = farmPlots++;
                return new ScenarioBlockPair(
                    new ScenarioBlockTarget(
                        2 + plot,
                        64,
                        2,
                        "up",
                        "farm-" + plot + "-clicked",
                        "minecraft:grass_block"
                    ),
                    new ScenarioBlockTarget(
                        2 + plot,
                        65,
                        2,
                        "down",
                        "farm-" + plot + "-target",
                        "minecraft:air"
                    )
                );
            }
            return new ScenarioBlockPair(
                new ScenarioBlockTarget(1, 64, 1, "up", nextPlacementLabel() + "-clicked", "minecraft:dirt"),
                new ScenarioBlockTarget(1, 65, 1, "down", nextPlacementLabel() + "-target", "minecraft:air")
            );
        }

        @Override
        public ScenarioBlockPair findUnobstructedPlaceablePair(ScenarioReach reach) {
            return findDryPlaceablePair(reach);
        }

        @Override
        public ScenarioBlockPair findTillableSoil(ScenarioReach reach) {
            operations.add("findTillable:" + reach.label());
            int plot = farmPlots++;
            return new ScenarioBlockPair(
                new ScenarioBlockTarget(
                    2 + plot,
                    64,
                    2,
                    "up",
                    "farm-" + plot + "-clicked",
                    "minecraft:grass_block"
                ),
                new ScenarioBlockTarget(
                    2 + plot,
                    65,
                    2,
                    "down",
                    "farm-" + plot + "-target",
                    "minecraft:air"
                )
            );
        }

        private String nextPlacementLabel() {
            if (campfires > 0) {
                return "campfire";
            }
            if (signs > 0) {
                return "sign";
            }
            if (sheepBeds > 0) {
                return "bed";
            }
            if (doors > 0) {
                return "door";
            }
            if (furnaces > 0 && torches > 0) {
                return "torch";
            }
            if (chests > 0) {
                return "chest";
            }
            if (furnaces > 0) {
                return "furnace";
            }
            return "table";
        }

        @Override
        public ScenarioUseResult useItemOn(ScenarioBlockTarget clicked, ScenarioHeldItem heldItem) {
            operations.add("use:" + heldItem.itemId() + ":" + clicked.label());
            if ("minecraft:stonecutter".equals(heldItem.itemId()) && !stonecutterPlaced) {
                stonecutterPlaced = true;
            }
            if ("minecraft:stonecutter".equals(clicked.blockId()) && stonecutterPlaced) {
                stonecutterMenuOpen = true;
                stonecutterScreenClassName =
                    "net.minecraft.client.gui.screens.inventory.StonecutterScreen";
                if (reuseStonecutterContainerIdOnReopen && nextStonecutterContainerId > 8) {
                    activeContainerId = 8;
                } else {
                    activeContainerId = nextStonecutterContainerId++;
                }
            }
            if ("minecraft:wheat_seeds".equals(heldItem.itemId()) && wheatSeeds > 0) {
                wheatSeeds -= 1;
            }
            if (
                "minecraft:crafting_table".equals(heldItem.itemId())
                    && "table-clicked".equals(clicked.label())
            ) {
                return new ScenarioUseResult(craftingTablePlaceUseResult);
            }
            return new ScenarioUseResult("success");
        }

        @Override
        public boolean waitForBlock(ScenarioBlockTarget target, String blockId, Duration duration) {
            operations.add("waitBlock:" + target.label() + ":" + blockId);
            if (
                "table-target".equals(target.label())
                    && "minecraft:crafting_table".equals(blockId)
                    && !craftingTablePlacementObserved
            ) {
                return false;
            }
            return true;
        }

        @Override
        public boolean waitForBlockProperty(
            ScenarioBlockTarget target,
            String property,
            String value,
            Duration duration
        ) {
            operations.add("waitProperty:" + target.label() + ":" + property + ":" + value);
            return true;
        }

        @Override
        public ScenarioLightLevel lightLevel(ScenarioBlockTarget target) {
            operations.add("light:" + target.label());
            return new ScenarioLightLevel(cropSkyLight, 0);
        }

        @Override
        public boolean waitForVisibleItemDrop(String itemId, ScenarioBlockTarget near, Duration timeout) {
            operations.add("waitDrop:" + itemId + ":" + near.label());
            return true;
        }

        @Override
        public boolean waitForNoVisibleItemDrop(String itemId, ScenarioBlockTarget near, Duration timeout) {
            operations.add("waitNoDrop:" + itemId + ":" + near.label());
            return true;
        }

        private void materializeSurvivalDeathDrops() {
            droppedWoodenPickaxes += woodenPickaxes;
            droppedWoodenSwords += woodenSwords;
            woodenPickaxes = 0;
            woodenSwords = 0;
        }

        @Override
        public boolean waitForDeathScreen(Duration duration) {
            operations.add("waitDeathScreen");
            if (healthAfterHostileCombat > 0.0F) {
                return false;
            }
            if (droppedWoodenPickaxes == 0 && droppedWoodenSwords == 0) {
                materializeSurvivalDeathDrops();
            }
            return true;
        }

        @Override
        public boolean standOnBlockUntilDeath(ScenarioBlockTarget target, Duration duration) {
            operations.add("standOnBlockUntilDeath:" + target.label() + ":" + target.blockId());
            healthAfterHostileCombat = 0.0F;
            materializeSurvivalDeathDrops();
            return true;
        }

        @Override
        public boolean performRespawn(Duration duration) {
            operations.add("respawn");
            healthAfterHostileCombat = 20.0F;
            return true;
        }

        @Override
        public boolean waitForSignEditor(ScenarioBlockTarget target, Duration duration) {
            operations.add("signEditor:" + target.label());
            return true;
        }

        @Override
        public void updateSignText(ScenarioBlockTarget target, List<String> lines) {
            operations.add("signText:" + target.label() + ":" + String.join("|", lines));
        }

        @Override
        public boolean waitForSignText(ScenarioBlockTarget target, List<String> lines, Duration duration) {
            operations.add("waitSignText:" + target.label() + ":" + String.join("|", lines));
            return true;
        }

        @Override
        public boolean waitForScreenClassName(String className, Duration duration) {
            operations.add("screen:" + className);
            if ("net.minecraft.client.gui.screens.inventory.StonecutterScreen".equals(className)) {
                return className.equals(stonecutterScreenClassName);
            }
            return true;
        }

        @Override
        public boolean closeCurrentScreen(Duration duration) {
            operations.add("closeScreen");
            boolean closed = !failCloseCurrentScreen
                || operations.stream().noneMatch(operation -> operation.startsWith("moveToContainer:0:"));
            if (closed && stonecutterMenuOpen) {
                if ("minecraft:cobblestone".equals(containerItemIds[0])) {
                    cobblestones += containerCounts[0];
                }
                containerItemIds[0] = null;
                containerCounts[0] = 0;
                containerItemIds[1] = null;
                containerCounts[1] = 0;
                stonecutterOfferSelected = false;
                stonecutterMenuOpen = false;
                stonecutterScreenClassName = null;
                activeContainerId = 7;
            }
            return closed;
        }

        @Override
        public int activeContainerId() {
            operations.add("containerId");
            return activeContainerId;
        }

        @Override
        public ScenarioFurnaceState furnaceState() {
            operations.add("furnaceState");
            return new ScenarioFurnaceState(
                furnaceBurnRemaining, furnaceBurnTotal, furnaceCookProgress, furnaceCookTotal,
                containerItemIds[1] == null ? "minecraft:air" : containerItemIds[1], containerCounts[1]
            );
        }

        @Override
        public boolean moveSelectedItemToContainerSlot(
            int containerSlot,
            String itemId,
            int count,
            Duration duration
        ) {
            operations.add("moveToContainer:" + containerSlot + ":" + itemId + ":" + count);
            if (containerSlot < 0 || containerSlot >= containerItemIds.length) {
                return false;
            }
            int movedCount = count;
            if (logItemId.equals(itemId)) {
                if (oakLogs < count) {
                    return false;
                }
                if (containerSlot == 0) {
                    movedCount = oakLogs;
                }
                oakLogs -= movedCount;
            } else if (planksItemId.equals(itemId)) {
                if (oakPlanks < count) {
                    return false;
                }
                if (containerSlot == 1) {
                    movedCount = oakPlanks;
                }
                oakPlanks -= movedCount;
            } else if ("minecraft:wooden_pickaxe".equals(itemId)) {
                if (woodenPickaxes < count) {
                    return false;
                }
                woodenPickaxes -= count;
            } else if ("minecraft:beef".equals(itemId)) {
                if (beef < count) {
                    return false;
                }
                beef -= count;
            } else if ("minecraft:porkchop".equals(itemId)) {
                if (porkchops < count) {
                    return false;
                }
                porkchops -= count;
            } else if ("minecraft:chicken".equals(itemId)) {
                if (chickens < count) {
                    return false;
                }
                chickens -= count;
            } else if ("minecraft:charcoal".equals(itemId)) {
                if (charcoals < count) {
                    return false;
                }
                charcoals -= count;
            } else if ("minecraft:raw_iron".equals(itemId)) {
                if (rawIron < count) {
                    return false;
                }
                if (containerSlot == 0) {
                    movedCount = rawIron;
                }
                rawIron -= movedCount;
            } else if ("minecraft:cobblestone".equals(itemId)) {
                if (cobblestones < count || containerSlot != 0) {
                    return false;
                }
                movedCount = cobblestones;
                cobblestones = 0;
            } else {
                return false;
            }
            containerItemIds[containerSlot] = itemId;
            containerCounts[containerSlot] = movedCount;
            return true;
        }

        @Override
        public boolean waitForContainerSlot(
            int containerSlot,
            String itemId,
            int count,
            Duration duration
        ) {
            operations.add("waitContainer:" + containerSlot + ":" + itemId + ":" + count);
            if (
                containerSlot == 2
                    && "minecraft:charcoal".equals(itemId)
                    && logItemId.equals(containerItemIds[0])
                    && planksItemId.equals(containerItemIds[1])
            ) {
                containerCounts[0] = Math.max(0, containerCounts[0] - 1);
                if (containerCounts[0] == 0) {
                    containerItemIds[0] = null;
                }
                containerCounts[1] = Math.max(0, containerCounts[1] - 1);
                if (containerCounts[1] == 0) {
                    containerItemIds[1] = null;
                }
                containerItemIds[2] = "minecraft:charcoal";
                containerCounts[2] = 1;
            }
            if (
                containerSlot == 2
                    && !"minecraft:raw_iron".equals(containerItemIds[0])
                    && cookedItemFor(containerItemIds[0]).equals(itemId)
            ) {
                containerItemIds[0] = null;
                containerCounts[0] = 0;
                containerCounts[1] = Math.max(0, containerCounts[1] - 1);
                if (containerCounts[1] == 0) {
                    containerItemIds[1] = null;
                }
                containerItemIds[2] = itemId;
                containerCounts[2] = 1;
            }
            if (containerSlot == 2 && "minecraft:iron_ingot".equals(itemId) && "minecraft:raw_iron".equals(containerItemIds[0])) {
                int requiredBurn = furnaceCookTotal * count - furnaceCookProgress;
                int queuedBurn = planksItemId.equals(containerItemIds[1]) ? containerCounts[1] * 300 : 0;
                if (furnaceBurnRemaining + queuedBurn < requiredBurn) {
                    return false;
                }
                int fuelUsed = Math.max(0, (requiredBurn - furnaceBurnRemaining + 299) / 300);
                int smeltedCount = Math.min(containerCounts[0], count);
                containerCounts[0] = Math.max(0, containerCounts[0] - smeltedCount);
                if (containerCounts[0] == 0) {
                    containerItemIds[0] = null;
                }
                containerCounts[1] = Math.max(0, containerCounts[1] - fuelUsed);
                // The next natural-ore trip may take longer than the residual burn.
                furnaceBurnRemaining = 0;
                furnaceCookProgress = 0;
                if (containerCounts[1] == 0) {
                    containerItemIds[1] = null;
                }
                containerItemIds[2] = "minecraft:iron_ingot";
                containerCounts[2] = smeltedCount;
            }
            return itemId.equals(containerItemIds[containerSlot]) && containerCounts[containerSlot] >= count;
        }

        @Override
        public boolean moveContainerSlotToInventory(
            int containerSlot,
            String itemId,
            int count,
            Duration duration
        ) {
            operations.add("moveFromContainer:" + containerSlot + ":" + itemId + ":" + count);
            if (
                containerSlot < 0
                    || containerSlot >= containerItemIds.length
                    || !itemId.equals(containerItemIds[containerSlot])
                    || containerCounts[containerSlot] < count
            ) {
                return false;
            }
            if (
                stonecutterMenuOpen
                    && stonecutterOfferSelected
                    && containerSlot == 1
                    && "minecraft:cobblestone_slab".equals(itemId)
                    && "minecraft:cobblestone".equals(containerItemIds[0])
                    && containerCounts[0] > 0
            ) {
                containerCounts[0] -= 1;
                if (containerCounts[0] == 0) {
                    containerItemIds[0] = null;
                    containerItemIds[1] = null;
                    containerCounts[1] = 0;
                    stonecutterOfferSelected = false;
                }
                cobblestoneSlabs += 2;
                return true;
            }
            int movedCount = containerCounts[containerSlot];
            containerCounts[containerSlot] = 0;
            containerItemIds[containerSlot] = null;
            if ("minecraft:charcoal".equals(itemId)) {
                charcoals += movedCount;
            } else if (logItemId.equals(itemId)) {
                oakLogs += movedCount;
            } else if (planksItemId.equals(itemId)) {
                oakPlanks += movedCount;
            } else if ("minecraft:cooked_beef".equals(itemId)) {
                cookedBeef += movedCount;
            } else if ("minecraft:cooked_porkchop".equals(itemId)) {
                cookedPorkchops += movedCount;
            } else if ("minecraft:cooked_chicken".equals(itemId)) {
                cookedChickens += movedCount;
            } else if ("minecraft:iron_ingot".equals(itemId)) {
                ironIngots += movedCount;
                totalExperience += movedCount * 7 / 10;
            } else if ("minecraft:raw_iron".equals(itemId)) {
                rawIron += movedCount;
            }
            return true;
        }

        @Override
        public boolean waitForContainerSlotEmpty(int containerSlot, Duration duration) {
            operations.add("waitContainerEmpty:" + containerSlot);
            return containerSlot >= 0
                && containerSlot < containerItemIds.length
                && (containerItemIds[containerSlot] == null || containerCounts[containerSlot] == 0);
        }

        @Override
        public int findContainerSlot(String itemId, int count) {
            operations.add("findContainerSlot:" + itemId + ":" + count);
            for (int slot = 0; slot < containerItemIds.length; slot++) {
                if (itemId.equals(containerItemIds[slot]) && containerCounts[slot] == count) {
                    return slot;
                }
            }
            return -1;
        }

        @Override
        public boolean quickMoveContainerSlot(int containerSlot, Duration duration) {
            operations.add("quickMoveContainer:" + containerSlot);
            if (containerSlot < 0 || containerSlot >= containerItemIds.length) {
                return false;
            }
            if (
                stonecutterMenuOpen
                    && stonecutterOfferSelected
                    && containerSlot == 1
                    && "minecraft:cobblestone".equals(containerItemIds[0])
                    && containerCounts[0] > 0
            ) {
                cobblestoneSlabs += containerCounts[0] * 2;
                containerItemIds[0] = null;
                containerCounts[0] = 0;
                containerItemIds[1] = null;
                containerCounts[1] = 0;
                stonecutterOfferSelected = false;
                return true;
            }
            String itemId = containerItemIds[containerSlot];
            int count = containerCounts[containerSlot];
            if (itemId == null || count <= 0) {
                return false;
            }
            switch (itemId) {
                case "minecraft:diamond" -> diamonds += count;
                case "minecraft:lapis_lazuli" -> lapisLazuli += count;
                case "minecraft:bread" -> breads += count;
                default -> {
                    return false;
                }
            }
            containerItemIds[containerSlot] = null;
            containerCounts[containerSlot] = 0;
            return true;
        }

        private static String cookedItemFor(String rawItemId) {
            if (rawItemId == null) {
                return "";
            }
            return switch (rawItemId) {
                case "minecraft:beef" -> "minecraft:cooked_beef";
                case "minecraft:porkchop" -> "minecraft:cooked_porkchop";
                case "minecraft:chicken" -> "minecraft:cooked_chicken";
                case "minecraft:raw_iron" -> "minecraft:iron_ingot";
                default -> "";
            };
        }

        private void decrementItem(String itemId, int count) {
            if (logItemId.equals(itemId)) {
                oakLogs -= count;
                return;
            }
            if (planksItemId.equals(itemId)) {
                oakPlanks -= count;
                return;
            }
            switch (itemId) {
                case "minecraft:beef" -> beef -= count;
                case "minecraft:porkchop" -> porkchops -= count;
                case "minecraft:chicken" -> chickens -= count;
                case "minecraft:cooked_beef" -> cookedBeef -= count;
                case "minecraft:cooked_porkchop" -> cookedPorkchops -= count;
                case "minecraft:cooked_chicken" -> cookedChickens -= count;
                case "minecraft:bread" -> breads -= count;
                default -> throw new IllegalArgumentException("unsupported test item " + itemId);
            }
        }

        @Override
        public boolean waitForDayTimeAtOrAfter(long dayTime, Duration duration) {
            operations.add("waitNight");
            return true;
        }

        @Override
        public boolean waitForDayTimeBelow(long dayTime, Duration duration) {
            operations.add("waitMorning");
            return true;
        }

        @Override
        public ScenarioHeldItem giveAndSelect(String itemId, int count, int hotbarSlot, Duration timeout) {
            operations.add("debug:give:" + itemId + ":" + count + ":" + hotbarSlot);
            if ("minecraft:cobblestone".equals(itemId)) {
                cobblestones += count;
            }
            return new ScenarioHeldItem(itemId, count);
        }

        @Override
        public boolean clickContainerButton(int buttonId, Duration duration) {
            operations.add("containerButton:" + buttonId);
            if (
                !stonecutterMenuOpen
                    || buttonId < 0
                    || buttonId >= 3
                    || !"minecraft:cobblestone".equals(containerItemIds[0])
                    || containerCounts[0] <= 0
            ) {
                return false;
            }
            stonecutterOfferSelected = buttonId == stonecutterSlabOfferId;
            if (stonecutterOfferSelected) {
                containerItemIds[1] = "minecraft:cobblestone_slab";
                containerCounts[1] = 2;
            } else {
                containerItemIds[1] = buttonId == 0
                    ? "minecraft:cobblestone_stairs"
                    : "minecraft:cobblestone_wall";
                containerCounts[1] = 1;
            }
            return true;
        }

        @Override
        public void sendCommand(String command) {
            operations.add("debug:command");
        }

        @Override
        public void sendChatMessage(String message) {
            operations.add("sendChat:" + message);
        }

        @Override
        public boolean waitForChatMessage(String expectedText, Duration timeout) {
            operations.add("waitChat:" + expectedText);
            return true;
        }

        @Override
        public boolean teleportTo(double x, double y, double z, Duration timeout) {
            operations.add("debug:teleport");
            return true;
        }

        @Override
        public ScenarioBlockPair findOccupiedPair(ScenarioReach reach) {
            throw new UnsupportedOperationException("not used by playable scenario");
        }

        @Override
        public ScenarioBlockPair findPlaceablePair(ScenarioReach reach) {
            throw new UnsupportedOperationException("not used by playable scenario");
        }

        @Override
        public boolean waitForStableBlocks(ScenarioBlockPair pair, Duration duration) {
            throw new UnsupportedOperationException("not used by playable scenario");
        }

        @Override
        public boolean waitForNoFluid(ScenarioBlockTarget target, Duration duration) {
            throw new UnsupportedOperationException("not used by playable scenario");
        }

        @Override
        public ScenarioBreakResult breakBlock(
            ScenarioBlockTarget target,
            String expectedDropItemId,
            int expectedSelectedCount,
            Duration timeout
        ) {
            throw new UnsupportedOperationException("not used by playable scenario");
        }
}
