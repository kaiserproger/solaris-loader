package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.solaris.agent.javaagent.PlayableCoordinates.coordinates;

final class PlayableRealClientLoopScenario {
    static final String JOIN_ID = "playable-01-join-generated-spawn";
    static final String WOOD_TO_TOOL_ID = "playable-02-natural-wood-to-tool";
    static final String LOG_TO_PLANKS_ID = "playable-02a-natural-log-to-planks";
    static final String CRAFTING_TABLE_OPEN_ID = "playable-02b-natural-crafting-table-open";
    static final String SAVE_RESTART_ID = "playable-03-save-restart-rejoin";
    static final String SAVE_RESTART_BEFORE_ID = "playable-03-save-restart-before";
    static final String SAVE_RESTART_AFTER_ID = "playable-03-save-restart-after";
    static final String TWENTY_MINUTE_ID = "playable-04-twenty-minute-survival-loop";
    static final String STONE_TOOL_ID = "playable-05-stone-tool-progression";
    static final String STONE_TOOL_SAVE_RESTART_ID = "playable-06-stone-tool-save-restart";
    static final String STONE_TOOL_SAVE_RESTART_BEFORE_ID = "playable-06-stone-tool-save-restart-before";
    static final String STONE_TOOL_SAVE_RESTART_AFTER_ID = "playable-06-stone-tool-save-restart-after";
    static final String FURNACE_PLACEMENT_OPEN_ID = "playable-07-furnace-placement-open";
    static final String FURNACE_CHARCOAL_SMELT_ID = "playable-08-furnace-charcoal-smelt";
    static final String TORCH_CRAFT_PLACE_ID = "playable-09-torch-craft-place";
    static final String PASSIVE_FOOD_DROP_ID = "playable-10-passive-food-drop";
    static final String EAT_PASSIVE_FOOD_ID = "playable-11-eat-passive-food";
    static final String EARNED_CHEST_STORAGE_ID = "playable-12-earned-chest-storage";
    static final String CHEST_STORAGE_SAVE_RESTART_ID = "playable-13-chest-storage-save-restart";
    static final String CHEST_STORAGE_SAVE_RESTART_BEFORE_ID = "playable-13-chest-storage-save-restart-before";
    static final String CHEST_STORAGE_SAVE_RESTART_AFTER_ID = "playable-13-chest-storage-save-restart-after";
    static final String EARNED_BED_SLEEP_ID = "playable-14-earned-bed-sleep";
    static final String COOKED_PASSIVE_FOOD_ID = "playable-15-cooked-passive-food";
    static final String EARNED_DOOR_PLACE_TOGGLE_ID = "playable-16-earned-door-place-toggle";
    static final String EARNED_SIGN_PLACE_EDIT_ID = "playable-17-earned-sign-place-edit";
    static final String EARNED_CAMPFIRE_COOKING_ID = "playable-18-earned-campfire-cooking";
    static final String EARNED_CAMPFIRE_DEATH_RESPAWN_ID = "playable-19-earned-campfire-death-respawn";
    static final String CAMPFIRE_DEATH_DROP_RECOVERY_ID = "playable-20-campfire-death-drop-recovery";
    static final String EARNED_TOOL_ZOMBIE_COMBAT_ID = "playable-21-earned-tool-zombie-combat";
    static final String STONE_SWORD_ZOMBIE_COMBAT_ID = "playable-22-stone-sword-zombie-combat";
    static final String IRON_INGOT_PROGRESSION_ID = "playable-23-iron-ingot-progression";
    static final String IRON_SWORD_ZOMBIE_COMBAT_ID = "playable-24-iron-sword-zombie-combat";
    static final String IRON_SWORD_SAVE_RESTART_ID = "playable-25-iron-sword-save-restart";
    static final String IRON_SWORD_SAVE_RESTART_BEFORE_ID = "playable-25-iron-sword-save-restart-before";
    static final String IRON_SWORD_SAVE_RESTART_AFTER_ID = "playable-25-iron-sword-save-restart-after";
    static final String EARNED_SHIELD_ZOMBIE_BLOCK_ID = "playable-26-earned-shield-zombie-block";
    static final String EARNED_IRON_CHESTPLATE_EQUIP_ID = "playable-27-earned-iron-chestplate-equip";
    static final String EARNED_IRON_CHESTPLATE_ZOMBIE_MITIGATION_ID =
        "playable-28-earned-iron-chestplate-zombie-mitigation";
    static final String IRON_CHESTPLATE_SAVE_RESTART_MITIGATION_ID =
        "playable-29-iron-chestplate-save-restart-mitigation";
    static final String IRON_CHESTPLATE_SAVE_RESTART_MITIGATION_BEFORE_ID =
        "playable-29-iron-chestplate-save-restart-mitigation-before";
    static final String IRON_CHESTPLATE_SAVE_RESTART_MITIGATION_AFTER_ID =
        "playable-29-iron-chestplate-save-restart-mitigation-after";
    static final String TWO_CLIENT_SHARED_LOG_DROP_PICKUP_ID =
        "playable-30-two-client-shared-log-drop-pickup";
    static final String TWO_CLIENT_SHARED_LOG_DROP_BREAK_ID =
        "playable-30-two-client-shared-log-drop-break";
    static final String TWO_CLIENT_SHARED_LOG_DROP_OBSERVE_ID =
        "playable-30-two-client-shared-log-drop-observe";
    static final String TWO_CLIENT_SHARED_LOG_PICKUP_COLLECT_ID =
        "playable-30-two-client-shared-log-pickup-collect";
    static final String TWO_CLIENT_SHARED_LOG_PICKUP_GONE_OBSERVE_ID =
        "playable-30-two-client-shared-log-pickup-gone-observe";
    static final String TWO_CLIENT_EARNED_SHARED_CHEST_ID =
        "playable-31-two-client-earned-shared-chest";
    static final String TWO_CLIENT_EARNED_SHARED_CHEST_DEPOSIT_ID =
        "playable-31-two-client-earned-shared-chest-deposit";
    static final String TWO_CLIENT_EARNED_SHARED_CHEST_WITHDRAW_ID =
        "playable-31-two-client-earned-shared-chest-withdraw";
    static final String TWO_CLIENT_EARNED_SHARED_CHEST_OBSERVE_EMPTY_ID =
        "playable-31-two-client-earned-shared-chest-observe-empty";
    static final String TWO_CLIENT_EARNED_TORCH_BLOCK_EDIT_ID =
        "playable-32-two-client-earned-torch-block-edit";
    static final String TWO_CLIENT_EARNED_TORCH_PLACE_ID =
        "playable-32-two-client-earned-torch-place";
    static final String TWO_CLIENT_EARNED_TORCH_OBSERVE_ID =
        "playable-32-two-client-earned-torch-observe";
    static final String TWO_CLIENT_EARNED_TORCH_BREAK_ID =
        "playable-32-two-client-earned-torch-break";
    static final String TWO_CLIENT_EARNED_TORCH_GONE_OBSERVE_ID =
        "playable-32-two-client-earned-torch-gone-observe";
    static final String TWO_CLIENT_PLAYER_VISIBILITY_MOVEMENT_ID =
        "playable-33-two-client-player-visibility-movement";
    static final String TWO_CLIENT_PLAYER_OBSERVE_ID =
        "playable-33-two-client-player-observe";
    static final String TWO_CLIENT_PLAYER_MOVED_OBSERVE_ID =
        "playable-33-two-client-player-moved-observe";
    static final String TWO_CLIENT_CHAT_MESSAGE_ID =
        "playable-34-two-client-chat-message";
    static final String TWO_CLIENT_CHAT_SEND_ID =
        "playable-34-two-client-chat-send";
    static final String TWO_CLIENT_CHAT_OBSERVE_ID =
        "playable-34-two-client-chat-observe";
    static final String TWO_CLIENT_PLAYER_DISCONNECT_REMOVAL_ID =
        "playable-35-two-client-player-disconnect-removal";
    static final String TWO_CLIENT_PLAYER_DISCONNECT_VISIBLE_ID =
        "playable-35-two-client-player-disconnect-visible";
    static final String TWO_CLIENT_PLAYER_GONE_OBSERVE_ID =
        "playable-35-two-client-player-gone-observe";
    static final String TWO_CLIENT_PLAYER_RECONNECT_CLEANUP_ID =
        "playable-36-two-client-player-reconnect-cleanup";
    static final String TWO_CLIENT_PLAYER_RECONNECT_VISIBLE_ID =
        "playable-36-two-client-player-reconnect-visible";
    static final String TWO_CLIENT_PLAYER_RECONNECT_GONE_OBSERVE_ID =
        "playable-36-two-client-player-reconnect-gone-observe";
    static final String TWO_CLIENT_PLAYER_RECONNECTED_OBSERVE_ID =
        "playable-36-two-client-player-reconnected-observe";
    static final String TWO_CLIENT_PLAYER_DEATH_RESPAWN_VISIBILITY_ID =
        "playable-37-two-client-player-death-respawn-visibility";
    static final String TWO_CLIENT_PLAYER_DEATH_BASELINE_ID =
        "playable-37-two-client-player-death-baseline";
    static final String TWO_CLIENT_CAMPFIRE_DEATH_RESPAWN_ID =
        "playable-37-two-client-campfire-death-respawn";
    static final String TWO_CLIENT_PLAYER_POST_RESPAWN_MOVED_OBSERVE_ID =
        "playable-37-two-client-player-post-respawn-moved-observe";
    static final String TWO_CLIENT_INVENTORY_DROP_HANDOFF_ID =
        "playable-38-two-client-inventory-drop-handoff";
    static final String TWO_CLIENT_INVENTORY_DROP_PRIMARY_ID =
        "playable-38-two-client-inventory-drop-primary";
    static final String TWO_CLIENT_INVENTORY_DROP_OBSERVE_ID =
        "playable-38-two-client-inventory-drop-observe";
    static final String TWO_CLIENT_INVENTORY_DROP_SECONDARY_PICKUP_ID =
        "playable-38-two-client-inventory-drop-secondary-pickup";
    static final String TWO_CLIENT_INVENTORY_DROP_GONE_OBSERVE_ID =
        "playable-38-two-client-inventory-drop-gone-observe";
    static final String RENEWABLE_WHEAT_BREAD_ID = "playable-43-renewable-wheat-bread";
    static final String PASSIVE_LIVESTOCK_MOTION_ID = "playable-44-passive-livestock-motion";
    static final String GENERATED_RUIN_CACHE_ID = "playable-46-generated-ruin-cache";
    static final String GENERATED_RUIN_CACHE_BEFORE_ID = "playable-46-generated-ruin-cache-before";
    static final String GENERATED_RUIN_CACHE_AFTER_ID = "playable-46-generated-ruin-cache-after";
    static final String STONECUTTER_CONSERVATION_ID = "playable-47-stonecutter-conservation";
    static final String CHEST_RECIPE_RESULT = "minecraft:chest";
    static final String FURNACE_RECIPE_RESULT = "minecraft:furnace";
    static final String TORCH_RECIPE_RESULT = "minecraft:torch";
    static final String CAMPFIRE_RECIPE_RESULT = "minecraft:campfire";
    static final String CONTAINER_SCREEN = "net.minecraft.client.gui.screens.inventory.ContainerScreen";
    static final String CRAFTING_SCREEN = "net.minecraft.client.gui.screens.inventory.CraftingScreen";
    static final String FURNACE_SCREEN = "net.minecraft.client.gui.screens.inventory.FurnaceScreen";
    static final Duration BREAK_TIMEOUT = Duration.ofSeconds(10);
    static final Duration PICKUP_TIMEOUT = Duration.ofSeconds(8);
    static final Duration INVENTORY_TIMEOUT = Duration.ofSeconds(5);
    static final Duration FURNACE_COOK_TIMEOUT = Duration.ofSeconds(20);
    static final Duration CAMPFIRE_COOK_TIMEOUT = Duration.ofSeconds(45);
    static final Duration CAMPFIRE_DEATH_TIMEOUT = Duration.ofSeconds(45);
    // The production raw-TCP respawn gate allows 30 seconds for Respawn + chunk replay +
    // health/position publication. Keep the graphical client gate on the same bound so a
    // loaded long-survival world does not fail merely because LevelLoadingScreen outlives 10s.
    static final Duration RESPAWN_TIMEOUT = Duration.ofSeconds(30);
    static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(2);
    static final Duration HOTBAR_TIMEOUT = Duration.ofSeconds(3);
    static final Duration APPROACH_TIMEOUT = Duration.ofSeconds(30);
    static final Duration ENTITY_SCAN_TIMEOUT = Duration.ofSeconds(20);
    static final Duration CHAT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration ENTITY_ATTACK_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration TWENTY_MINUTE_SOAK = Duration.ofMinutes(20);
    static final Duration NIGHT_WAIT_TIMEOUT = Duration.ofMinutes(12);
    static final long NIGHT_START_DAY_TIME = 12_542L;
    private final Duration survivalSoakDuration;





















    PlayableRealClientLoopScenario() {
        this(TWENTY_MINUTE_SOAK);
    }

    PlayableRealClientLoopScenario(Duration survivalSoakDuration) {
        if (survivalSoakDuration.isZero() || survivalSoakDuration.isNegative()) {
            throw new IllegalArgumentException("survival soak duration must be positive");
        }
        this.survivalSoakDuration = survivalSoakDuration;
    }

    static boolean supports(String id) {
        return JOIN_ID.equals(id)
            || WOOD_TO_TOOL_ID.equals(id)
            || LOG_TO_PLANKS_ID.equals(id)
            || CRAFTING_TABLE_OPEN_ID.equals(id)
            || SAVE_RESTART_ID.equals(id)
            || SAVE_RESTART_BEFORE_ID.equals(id)
            || SAVE_RESTART_AFTER_ID.equals(id)
            || TWENTY_MINUTE_ID.equals(id)
            || STONE_TOOL_ID.equals(id)
            || STONE_TOOL_SAVE_RESTART_ID.equals(id)
            || STONE_TOOL_SAVE_RESTART_BEFORE_ID.equals(id)
            || STONE_TOOL_SAVE_RESTART_AFTER_ID.equals(id)
            || FURNACE_PLACEMENT_OPEN_ID.equals(id)
            || FURNACE_CHARCOAL_SMELT_ID.equals(id)
            || TORCH_CRAFT_PLACE_ID.equals(id)
            || PASSIVE_FOOD_DROP_ID.equals(id)
            || EAT_PASSIVE_FOOD_ID.equals(id)
            || EARNED_CHEST_STORAGE_ID.equals(id)
            || CHEST_STORAGE_SAVE_RESTART_ID.equals(id)
            || CHEST_STORAGE_SAVE_RESTART_BEFORE_ID.equals(id)
            || CHEST_STORAGE_SAVE_RESTART_AFTER_ID.equals(id)
            || EARNED_BED_SLEEP_ID.equals(id)
            || COOKED_PASSIVE_FOOD_ID.equals(id)
            || EARNED_DOOR_PLACE_TOGGLE_ID.equals(id)
            || EARNED_SIGN_PLACE_EDIT_ID.equals(id)
            || EARNED_CAMPFIRE_COOKING_ID.equals(id)
            || EARNED_CAMPFIRE_DEATH_RESPAWN_ID.equals(id)
            || CAMPFIRE_DEATH_DROP_RECOVERY_ID.equals(id)
            || EARNED_TOOL_ZOMBIE_COMBAT_ID.equals(id)
            || STONE_SWORD_ZOMBIE_COMBAT_ID.equals(id)
            || IRON_INGOT_PROGRESSION_ID.equals(id)
            || IRON_SWORD_ZOMBIE_COMBAT_ID.equals(id)
            || IRON_SWORD_SAVE_RESTART_ID.equals(id)
            || IRON_SWORD_SAVE_RESTART_BEFORE_ID.equals(id)
            || IRON_SWORD_SAVE_RESTART_AFTER_ID.equals(id)
            || EARNED_SHIELD_ZOMBIE_BLOCK_ID.equals(id)
            || EARNED_IRON_CHESTPLATE_EQUIP_ID.equals(id)
            || EARNED_IRON_CHESTPLATE_ZOMBIE_MITIGATION_ID.equals(id)
            || IRON_CHESTPLATE_SAVE_RESTART_MITIGATION_ID.equals(id)
            || IRON_CHESTPLATE_SAVE_RESTART_MITIGATION_BEFORE_ID.equals(id)
            || IRON_CHESTPLATE_SAVE_RESTART_MITIGATION_AFTER_ID.equals(id)
            || PlayableTwoClientStages.supports(id)
            || RENEWABLE_WHEAT_BREAD_ID.equals(id)
            || PASSIVE_LIVESTOCK_MOTION_ID.equals(id)
            || GENERATED_RUIN_CACHE_BEFORE_ID.equals(id)
            || GENERATED_RUIN_CACHE_AFTER_ID.equals(id)
            || STONECUTTER_CONSERVATION_ID.equals(id);
    }

    ClientScenarioReport run(String id, Path screenshotsDir, ScenarioClient client) {
        if (!supports(id)) {
            return new ClientScenarioReport("blocked", id, List.of("unsupported scenario id: " + id));
        }

        List<String> observations = new ArrayList<>();
        try {
            ScenarioHeldItem selected = client.selectedItem();
            observations.add("join/play-state: passed selected=" + selected.itemId() + " x" + selected.count());
            observations.add(
                "inventory baseline: oak_log="
                    + client.inventoryCount("minecraft:oak_log")
                    + " oak_planks="
                    + client.inventoryCount("minecraft:oak_planks")
                    + " crafting_table="
                    + client.inventoryCount("minecraft:crafting_table")
                    + " wooden_pickaxe="
                    + client.inventoryCount("minecraft:wooden_pickaxe")
            );
            observations.add("artifact directory available to driver: " + screenshotsDir);

            if (JOIN_ID.equals(id)) {
                return new ClientScenarioReport("passed", id, observations);
            }
            if (LOG_TO_PLANKS_ID.equals(id)) {
                return PlayableResourceStage.runWoodToToolStart(id, observations, client, true);
            }
            if (CRAFTING_TABLE_OPEN_ID.equals(id)) {
                return PlayableResourceStage.runCraftingTableOpen(id, observations, client);
            }
            if (WOOD_TO_TOOL_ID.equals(id)) {
                return PlayableToolStage.runWoodToTool(id, observations, client);
            }
            if (SAVE_RESTART_BEFORE_ID.equals(id)) {
                return PlayablePersistenceStage.runSaveRestartBefore(id, observations, screenshotsDir, client);
            }
            if (SAVE_RESTART_AFTER_ID.equals(id)) {
                return PlayablePersistenceStage.runSaveRestartAfter(id, observations, screenshotsDir, client);
            }
            if (TWENTY_MINUTE_ID.equals(id)) {
                return PlayableSurvivalStage.run(survivalSoakDuration, id, observations, screenshotsDir, client);
            }
            if (STONE_TOOL_ID.equals(id)) {
                return PlayableToolStage.runStoneToolProgression(id, observations, client).report();
            }
            if (STONE_TOOL_SAVE_RESTART_BEFORE_ID.equals(id)) {
                return PlayableToolStage.runStoneToolSaveRestartBefore(id, observations, screenshotsDir, client);
            }
            if (STONE_TOOL_SAVE_RESTART_AFTER_ID.equals(id)) {
                return PlayableToolStage.runStoneToolSaveRestartAfter(id, observations, screenshotsDir, client);
            }
            if (FURNACE_PLACEMENT_OPEN_ID.equals(id)) {
                return PlayableHeatStage.runFurnacePlacementOpen(id, observations, client);
            }
            if (FURNACE_CHARCOAL_SMELT_ID.equals(id)) {
                return PlayableHeatStage.runFurnaceCharcoalSmelt(id, observations, client);
            }
            if (TORCH_CRAFT_PLACE_ID.equals(id)) {
                return PlayableHeatStage.runTorchCraftPlace(id, observations, client);
            }
            if (PASSIVE_FOOD_DROP_ID.equals(id)) {
                return PlayableFoodStage.runPassiveFoodDrop(id, observations, client);
            }
            if (EAT_PASSIVE_FOOD_ID.equals(id)) {
                return PlayableFoodStage.runEatPassiveFood(id, observations, client);
            }
            if (RENEWABLE_WHEAT_BREAD_ID.equals(id)) {
                return PlayableFoodStage.runRenewableWheatBread(id, observations, client);
            }
            if (PASSIVE_LIVESTOCK_MOTION_ID.equals(id)) {
                return PlayableFoodStage.runPassiveLivestockMotion(id, observations, client);
            }
            if (EARNED_CHEST_STORAGE_ID.equals(id)) {
                return PlayableStorageStage.runEarnedChestStorage(id, observations, client);
            }
            if (CHEST_STORAGE_SAVE_RESTART_BEFORE_ID.equals(id)) {
                return PlayableStorageStage.runChestStorageSaveRestartBefore(id, observations, screenshotsDir, client);
            }
            if (CHEST_STORAGE_SAVE_RESTART_AFTER_ID.equals(id)) {
                return PlayableStorageStage.runChestStorageSaveRestartAfter(id, observations, screenshotsDir, client);
            }
            if (GENERATED_RUIN_CACHE_BEFORE_ID.equals(id)) {
                return PlayableGeneratedRuinStage.runBefore(id, observations, screenshotsDir, client);
            }
            if (GENERATED_RUIN_CACHE_AFTER_ID.equals(id)) {
                return PlayableGeneratedRuinStage.runAfter(id, observations, screenshotsDir, client);
            }
            if (STONECUTTER_CONSERVATION_ID.equals(id)) {
                return PlayableStationStage.runStonecutterConservation(id, observations, client);
            }
            if (EARNED_BED_SLEEP_ID.equals(id)) {
                return PlayableRestStage.runEarnedBedSleep(id, observations, client);
            }
            if (COOKED_PASSIVE_FOOD_ID.equals(id)) {
                return PlayableFoodStage.runCookedPassiveFood(id, observations, client);
            }
            if (EARNED_DOOR_PLACE_TOGGLE_ID.equals(id)) {
                return PlayableFixtureStage.runEarnedDoorPlaceToggle(id, observations, client);
            }
            if (EARNED_SIGN_PLACE_EDIT_ID.equals(id)) {
                return PlayableFixtureStage.runEarnedSignPlaceEdit(id, observations, client);
            }
            if (EARNED_CAMPFIRE_COOKING_ID.equals(id)) {
                return PlayableCampfireStage.runEarnedCampfireCooking(id, observations, client);
            }
            if (EARNED_CAMPFIRE_DEATH_RESPAWN_ID.equals(id)) {
                return PlayableCampfireStage.runEarnedCampfireDeathRespawn(id, observations, client);
            }
            if (CAMPFIRE_DEATH_DROP_RECOVERY_ID.equals(id)) {
                return PlayableCampfireStage.runCampfireDeathDropRecovery(id, observations, client);
            }
            if (EARNED_TOOL_ZOMBIE_COMBAT_ID.equals(id)) {
                return PlayableCombatStage.runEarnedToolZombieCombat(id, observations, client);
            }
            if (STONE_SWORD_ZOMBIE_COMBAT_ID.equals(id)) {
                return PlayableCombatStage.runStoneSwordZombieCombat(id, observations, client);
            }
            if (IRON_INGOT_PROGRESSION_ID.equals(id)) {
                return PlayableToolStage.runIronIngotProgression(id, observations, client);
            }
            if (IRON_SWORD_ZOMBIE_COMBAT_ID.equals(id)) {
                return PlayableCombatStage.runIronSwordZombieCombat(id, observations, client);
            }
            if (IRON_SWORD_SAVE_RESTART_BEFORE_ID.equals(id)) {
                return PlayableToolStage.runIronSwordSaveRestartBefore(id, observations, screenshotsDir, client);
            }
            if (IRON_SWORD_SAVE_RESTART_AFTER_ID.equals(id)) {
                return PlayableToolStage.runIronSwordSaveRestartAfter(id, observations, screenshotsDir, client);
            }
            if (EARNED_SHIELD_ZOMBIE_BLOCK_ID.equals(id)) {
                return PlayableCombatStage.runEarnedShieldZombieBlock(id, observations, client);
            }
            if (EARNED_IRON_CHESTPLATE_EQUIP_ID.equals(id)) {
                return PlayableCombatStage.runEarnedIronChestplateEquip(id, observations, client);
            }
            if (EARNED_IRON_CHESTPLATE_ZOMBIE_MITIGATION_ID.equals(id)) {
                return PlayableCombatStage.runEarnedIronChestplateZombieMitigation(id, observations, client);
            }
            if (IRON_CHESTPLATE_SAVE_RESTART_MITIGATION_BEFORE_ID.equals(id)) {
                return PlayableCombatStage.runIronChestplateSaveRestartMitigationBefore(id, observations, screenshotsDir, client);
            }
            if (IRON_CHESTPLATE_SAVE_RESTART_MITIGATION_AFTER_ID.equals(id)) {
                return PlayableCombatStage.runIronChestplateSaveRestartMitigationAfter(id, observations, screenshotsDir, client);
            }
            if (PlayableTwoClientStages.supports(id)) {
                return new PlayableTwoClientStages().run(id, observations, screenshotsDir, client);
            }
            observations.add(
                "blocked: playable natural survival automation is intentionally no-debug; "
                    + "runner-managed save/restart and 20-minute loop "
                    + "need real-client natural action primitives"
            );
            observations.add(
                "blocked: debug give, teleport setup, protocol harnesses, and mock clients are forbidden for "
                    + id
            );
            return new ClientScenarioReport("blocked", id, observations);
        } catch (Exception error) {
            observations.add("scenario failed with exception: " + error.getMessage());
            return new ClientScenarioReport("failed", id, observations);
        }
    }

}
