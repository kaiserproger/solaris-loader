package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;

import java.time.Duration;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Files;

import static dev.solaris.agent.javaagent.PlayableCoordinates.coordinates;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.APPROACH_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.NIGHT_WAIT_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.NIGHT_START_DAY_TIME;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.CRAFTING_SCREEN;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.BLOCK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.ENTITY_ATTACK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.ENTITY_SCAN_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.HOTBAR_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.INVENTORY_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.PICKUP_TIMEOUT;

/** Earned combat: tool/sword/iron combat, shield block, chestplate equip/mitigation. */
final class PlayableCombatStage {
    private PlayableCombatStage() {
    }

    private static final Duration SHIELD_BLOCK_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration HOSTILE_HIT_TIMEOUT = Duration.ofSeconds(10);
    // Sleep/nightfall starts before surface hostile spawning. Keep the
    // outside-reach scan active through both former 20-second scan windows.
    private static final Duration NATURAL_ZOMBIE_SCAN_TIMEOUT = ENTITY_SCAN_TIMEOUT.multipliedBy(2);
    private static final float IRON_CHESTPLATE_MAX_ZOMBIE_HIT_DAMAGE = 2.75F;

    private static final List<String> ZOMBIE_ENTITY_IDS = List.of(
        "minecraft:zombie"
    );

    static ClientScenarioReport runEarnedToolZombieCombat(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        PlayableResourceStage.WoodenToolTableResult prepared = PlayableResourceStage.prepareWoodenToolAndTable(id, observations, client);
        if (!"passed".equals(prepared.report().result())) {
            return prepared.report();
        }
        boolean closed = client.closeCurrentScreen(INVENTORY_TIMEOUT);
        observations.add("crafting table screen close before zombie combat: " + (closed ? "passed" : "failed"));
        if (!closed) {
            return new ClientScenarioReport("failed", id, observations);
        }

        String weaponId = "minecraft:wooden_pickaxe";
        for (String candidate : List.of(
            "minecraft:iron_sword", "minecraft:stone_sword", "minecraft:wooden_sword",
            "minecraft:iron_pickaxe", "minecraft:stone_pickaxe"
        )) {
            if (client.inventoryCount(candidate) > 0) {
                weaponId = candidate;
                break;
            }
        }
        ScenarioHeldItem weapon = client.selectHotbarItem(weaponId, 1, HOTBAR_TIMEOUT);
        if (!weapon.matches(weaponId, 1)) {
            observations.add("blocked: earned combat weapon exists but is not selectable: " + weaponId);
            return new ClientScenarioReport("blocked", id, observations);
        }

        boolean nightReached = client.waitForDayTimeAtOrAfter(NIGHT_START_DAY_TIME, NIGHT_WAIT_TIMEOUT);
        observations.add(
            "natural night wait for zombie combat: " + (nightReached ? "passed" : "failed")
                + " night_start_day_time=" + NIGHT_START_DAY_TIME
        );
        if (!nightReached) {
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioEntityObservation zombie = client.findVisibleEntity(
            ZOMBIE_ENTITY_IDS,
            ScenarioReach.OUTSIDE_SURVIVAL_REACH,
            NATURAL_ZOMBIE_SCAN_TIMEOUT
        );
        if (zombie == null) {
            zombie = client.findVisibleEntity(
                ZOMBIE_ENTITY_IDS,
                ScenarioReach.WITHIN_SURVIVAL_REACH,
                ENTITY_SCAN_TIMEOUT
            );
        }
        if (zombie == null) {
            observations.add("blocked: no loaded natural zombie visible after nightfall");
            return new ClientScenarioReport("blocked", id, observations);
        }
        observations.add(
            "zombie scan: passed"
                + " entity=" + zombie.entityType()
                + " entity_id=" + zombie.entityId()
                + " distance_squared=" + zombie.distanceSquared()
        );

        boolean approached = client.approachEntity(zombie, APPROACH_TIMEOUT);
        observations.add(
            "zombie approach: " + (approached ? "passed" : "failed")
                + " entity_id=" + zombie.entityId()
        );
        if (!approached) {
            return new ClientScenarioReport("blocked", id, observations);
        }

        int rottenFleshBefore = client.inventoryCount("minecraft:rotten_flesh");
        ScenarioBreakResult attack = client.attackEntityUntilDropCollected(
            zombie,
            "minecraft:rotten_flesh",
            1,
            ENTITY_ATTACK_TIMEOUT
        );
        int rottenFleshAfter = client.inventoryCount("minecraft:rotten_flesh");
        boolean collected = attack.started()
            && attack.becameAir()
            && attack.pickupRestored()
            && rottenFleshAfter >= rottenFleshBefore + 1;
        observations.add(
            "zombie combat drop: " + (collected ? "passed" : "failed")
                + " entity_id=" + zombie.entityId()
                + " weapon=" + weapon.itemId()
                + " expected_drop=minecraft:rotten_flesh"
                + " attack_started=" + attack.started()
                + " entity_removed=" + attack.becameAir()
                + " saw_drop=" + attack.sawDrop()
                + " pickup_restored=" + attack.pickupRestored()
                + " rotten_flesh_before=" + rottenFleshBefore
                + " rotten_flesh_after=" + rottenFleshAfter
        );
        float healthAfterCombat = client.playerHealth();
        boolean survived = healthAfterCombat > 0.0F;
        observations.add(
            "zombie combat survival: " + (survived ? "passed" : "failed")
                + " health_after=" + healthAfterCombat
        );
        return new ClientScenarioReport(collected && survived ? "passed" : "failed", id, observations);
    }

    static ClientScenarioReport runStoneSwordZombieCombat(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        PlayableResourceStage.WoodenToolTableResult prepared = PlayableResourceStage.prepareWoodenToolAndTable(id, observations, client);
        if (!"passed".equals(prepared.report().result())) {
            return prepared.report();
        }

        ClientScenarioReport cobblestone = PlayableHeatStage.mineCobblestoneWithWoodenPickaxe(id, observations, client, 2);
        if (!"passed".equals(cobblestone.result())) {
            return cobblestone;
        }

        boolean tableApproached = client.approachBlock(prepared.tableTarget(), APPROACH_TIMEOUT);
        observations.add(
            "crafting table approach for stone sword: " + (tableApproached ? "passed" : "failed")
                + " target=" + coordinates(prepared.tableTarget())
        );
        if (!tableApproached) {
            return new ClientScenarioReport("blocked", id, observations);
        }

        ScenarioHeldItem woodenPickaxe = client.selectHotbarItem("minecraft:wooden_pickaxe", 1, HOTBAR_TIMEOUT);
        ScenarioBlockTarget tableUseTarget = PlayableCoordinates.reachableUseTarget(client, prepared.tableTarget());
        ScenarioUseResult openUse = client.useItemOn(tableUseTarget, woodenPickaxe);
        boolean opened = client.waitForScreenClassName(CRAFTING_SCREEN, INVENTORY_TIMEOUT);
        observations.add(
            "crafting table reopen for stone sword: " + (opened ? "passed" : "failed")
                + " open_use_result=" + openUse.result()
                + " screen=" + CRAFTING_SCREEN
                + " screen_matched=" + opened
        );
        if (!opened) {
            return new ClientScenarioReport("failed", id, observations);
        }

        ClientScenarioReport stoneSword = PlayableToolStage.craftStoneSwordInOpenTable(id, observations, client, true);
        if (!"passed".equals(stoneSword.result())) {
            return stoneSword;
        }

        ScenarioHeldItem weapon = client.selectHotbarItem("minecraft:stone_sword", 1, HOTBAR_TIMEOUT);
        if (!weapon.matches("minecraft:stone_sword", 1)) {
            observations.add("blocked: earned stone sword exists but is not selectable for zombie combat");
            return new ClientScenarioReport("blocked", id, observations);
        }

        boolean nightReached = client.waitForDayTimeAtOrAfter(NIGHT_START_DAY_TIME, NIGHT_WAIT_TIMEOUT);
        observations.add(
            "natural night wait for stone sword zombie combat: " + (nightReached ? "passed" : "failed")
                + " night_start_day_time=" + NIGHT_START_DAY_TIME
        );
        if (!nightReached) {
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioEntityObservation zombie = client.findVisibleEntity(
            ZOMBIE_ENTITY_IDS,
            ScenarioReach.OUTSIDE_SURVIVAL_REACH,
            NATURAL_ZOMBIE_SCAN_TIMEOUT
        );
        if (zombie == null) {
            zombie = client.findVisibleEntity(
                ZOMBIE_ENTITY_IDS,
                ScenarioReach.WITHIN_SURVIVAL_REACH,
                ENTITY_SCAN_TIMEOUT
            );
        }
        if (zombie == null) {
            observations.add("blocked: no loaded natural zombie visible after nightfall");
            return new ClientScenarioReport("blocked", id, observations);
        }
        observations.add(
            "stone sword zombie scan: passed"
                + " entity=" + zombie.entityType()
                + " entity_id=" + zombie.entityId()
                + " distance_squared=" + zombie.distanceSquared()
        );

        boolean approached = client.approachEntity(zombie, APPROACH_TIMEOUT);
        observations.add(
            "stone sword zombie approach: " + (approached ? "passed" : "failed")
                + " entity_id=" + zombie.entityId()
        );
        if (!approached) {
            return new ClientScenarioReport("blocked", id, observations);
        }

        int rottenFleshBefore = client.inventoryCount("minecraft:rotten_flesh");
        ScenarioBreakResult attack = client.attackEntityUntilDropCollected(
            zombie,
            "minecraft:rotten_flesh",
            1,
            ENTITY_ATTACK_TIMEOUT
        );
        int rottenFleshAfter = client.inventoryCount("minecraft:rotten_flesh");
        boolean collected = attack.started()
            && attack.becameAir()
            && attack.pickupRestored()
            && rottenFleshAfter >= rottenFleshBefore + 1;
        observations.add(
            "stone sword zombie combat: " + (collected ? "passed" : "failed")
                + " entity_id=" + zombie.entityId()
                + " weapon=" + weapon.itemId()
                + " expected_drop=minecraft:rotten_flesh"
                + " attack_started=" + attack.started()
                + " entity_removed=" + attack.becameAir()
                + " saw_drop=" + attack.sawDrop()
                + " pickup_restored=" + attack.pickupRestored()
                + " rotten_flesh_before=" + rottenFleshBefore
                + " rotten_flesh_after=" + rottenFleshAfter
        );
        float healthAfterCombat = client.playerHealth();
        boolean survived = healthAfterCombat > 0.0F;
        observations.add(
            "stone sword zombie combat survival: " + (survived ? "passed" : "failed")
                + " health_after=" + healthAfterCombat
        );
        return new ClientScenarioReport(collected && survived ? "passed" : "failed", id, observations);
    }

    static ClientScenarioReport runIronSwordZombieCombat(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        PlayableToolStage.IronSwordProgressionResult ironSword = PlayableToolStage.craftEarnedIronSwordProgression(id, observations, client);
        if (!"passed".equals(ironSword.report().result())) {
            return ironSword.report();
        }

        ScenarioHeldItem weapon = client.selectHotbarItem("minecraft:iron_sword", 1, HOTBAR_TIMEOUT);
        if (!weapon.matches("minecraft:iron_sword", 1)) {
            observations.add("blocked: earned iron sword exists but is not selectable for zombie combat");
            return new ClientScenarioReport("blocked", id, observations);
        }

        boolean nightReached = client.waitForDayTimeAtOrAfter(NIGHT_START_DAY_TIME, NIGHT_WAIT_TIMEOUT);
        observations.add(
            "natural night wait for iron sword zombie combat: " + (nightReached ? "passed" : "failed")
                + " night_start_day_time=" + NIGHT_START_DAY_TIME
        );
        if (!nightReached) {
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioEntityObservation zombie = client.findVisibleEntity(
            ZOMBIE_ENTITY_IDS,
            ScenarioReach.OUTSIDE_SURVIVAL_REACH,
            NATURAL_ZOMBIE_SCAN_TIMEOUT
        );
        if (zombie == null) {
            zombie = client.findVisibleEntity(
                ZOMBIE_ENTITY_IDS,
                ScenarioReach.WITHIN_SURVIVAL_REACH,
                ENTITY_SCAN_TIMEOUT
            );
        }
        if (zombie == null) {
            observations.add("blocked: no loaded natural zombie visible after nightfall");
            return new ClientScenarioReport("blocked", id, observations);
        }
        observations.add(
            "iron sword zombie scan: passed"
                + " entity=" + zombie.entityType()
                + " entity_id=" + zombie.entityId()
                + " distance_squared=" + zombie.distanceSquared()
        );

        boolean approached = client.approachEntity(zombie, APPROACH_TIMEOUT);
        observations.add(
            "iron sword zombie approach: " + (approached ? "passed" : "failed")
                + " entity_id=" + zombie.entityId()
        );
        if (!approached) {
            return new ClientScenarioReport("blocked", id, observations);
        }

        int rottenFleshBefore = client.inventoryCount("minecraft:rotten_flesh");
        ScenarioBreakResult attack = client.attackEntityUntilDropCollected(
            zombie,
            "minecraft:rotten_flesh",
            1,
            ENTITY_ATTACK_TIMEOUT
        );
        int rottenFleshAfter = client.inventoryCount("minecraft:rotten_flesh");
        boolean collected = attack.started()
            && attack.becameAir()
            && attack.pickupRestored()
            && rottenFleshAfter >= rottenFleshBefore + 1;
        observations.add(
            "iron sword zombie combat: " + (collected ? "passed" : "failed")
                + " entity_id=" + zombie.entityId()
                + " weapon=" + weapon.itemId()
                + " expected_drop=minecraft:rotten_flesh"
                + " attack_started=" + attack.started()
                + " entity_removed=" + attack.becameAir()
                + " saw_drop=" + attack.sawDrop()
                + " pickup_restored=" + attack.pickupRestored()
                + " rotten_flesh_before=" + rottenFleshBefore
                + " rotten_flesh_after=" + rottenFleshAfter
        );
        float healthAfterCombat = client.playerHealth();
        boolean survived = healthAfterCombat > 0.0F;
        observations.add(
            "iron sword zombie combat survival: " + (survived ? "passed" : "failed")
                + " health_after=" + healthAfterCombat
        );
        return new ClientScenarioReport(collected && survived ? "passed" : "failed", id, observations);
    }

    static ClientScenarioReport runEarnedShieldZombieBlock(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        ClientScenarioReport shield = PlayableToolStage.craftEarnedShieldProgression(id, observations, client);
        if (!"passed".equals(shield.result())) {
            return shield;
        }

        ScenarioHeldItem heldShield = client.selectHotbarItem("minecraft:shield", 1, HOTBAR_TIMEOUT);
        if (!heldShield.matches("minecraft:shield", 1)) {
            observations.add("blocked: earned shield exists but is not selectable for zombie block");
            return new ClientScenarioReport("blocked", id, observations);
        }

        boolean nightReached = client.waitForDayTimeAtOrAfter(NIGHT_START_DAY_TIME, NIGHT_WAIT_TIMEOUT);
        observations.add(
            "natural night wait for shield zombie block: " + (nightReached ? "passed" : "failed")
                + " night_start_day_time=" + NIGHT_START_DAY_TIME
        );
        if (!nightReached) {
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioEntityObservation zombie = client.findVisibleEntity(
            ZOMBIE_ENTITY_IDS,
            ScenarioReach.OUTSIDE_SURVIVAL_REACH,
            NATURAL_ZOMBIE_SCAN_TIMEOUT
        );
        if (zombie == null) {
            zombie = client.findVisibleEntity(
                ZOMBIE_ENTITY_IDS,
                ScenarioReach.WITHIN_SURVIVAL_REACH,
                ENTITY_SCAN_TIMEOUT
            );
        }
        if (zombie == null) {
            observations.add("blocked: no loaded natural zombie visible after nightfall");
            return new ClientScenarioReport("blocked", id, observations);
        }
        observations.add(
            "shield zombie scan: passed"
                + " entity=" + zombie.entityType()
                + " entity_id=" + zombie.entityId()
                + " distance_squared=" + zombie.distanceSquared()
        );

        boolean approached = client.approachEntity(zombie, APPROACH_TIMEOUT);
        observations.add(
            "shield zombie approach: " + (approached ? "passed" : "failed")
                + " entity_id=" + zombie.entityId()
        );
        if (!approached) {
            return new ClientScenarioReport("blocked", id, observations);
        }

        ScenarioShieldBlockResult shieldBlock = client.blockAttackWithSelectedShield(
            "minecraft:shield",
            SHIELD_BLOCK_TIMEOUT
        );
        boolean survived = shieldBlock.healthAfter() > 0.0F;
        boolean blocked = shieldBlock.useStarted()
            && shieldBlock.blockedAttackObserved()
            && survived
            && shieldBlock.healthAfter() >= shieldBlock.healthBefore();
        observations.add(
            "shield zombie block: " + (blocked ? "passed" : "failed")
                + " entity_id=" + zombie.entityId()
                + " shield_use_started=" + shieldBlock.useStarted()
                + " blocked_attack_observed=" + shieldBlock.blockedAttackObserved()
                + " shield_damage_before=" + shieldBlock.shieldDamageBefore()
                + " shield_damage_after=" + shieldBlock.shieldDamageAfter()
                + " health_before=" + shieldBlock.healthBefore()
                + " health_after=" + shieldBlock.healthAfter()
                + " survived=" + survived
        );
        return new ClientScenarioReport(blocked ? "passed" : "failed", id, observations);
    }

    static ClientScenarioReport runEarnedIronChestplateEquip(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        PlayableToolStage.IronChestplateProgressionResult chestplate = PlayableToolStage.craftEarnedIronChestplateProgressionResult(
            id,
            observations,
            client
        );
        if (!"passed".equals(chestplate.report().result())) {
            return chestplate.report();
        }

        return equipEarnedIronChestplate(id, observations, client);
    }

    static ClientScenarioReport runEarnedIronChestplateZombieMitigation(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        ClientScenarioReport chestplate = runEarnedIronChestplateEquip(id, observations, client);
        if (!"passed".equals(chestplate.result())) {
            return chestplate;
        }

        return measureIronChestplateZombieMitigation(
            id,
            observations,
            client,
            "iron chestplate zombie mitigation"
        );
    }

    static ClientScenarioReport equipEarnedIronChestplate(
        String id,
        List<String> observations,
        ScenarioClient client
    ) throws Exception {
        ScenarioHeldItem heldChestplate = client.selectHotbarItem(
            "minecraft:iron_chestplate",
            1,
            HOTBAR_TIMEOUT
        );
        if (!heldChestplate.matches("minecraft:iron_chestplate", 1)) {
            observations.add("blocked: earned iron chestplate exists but is not selectable for armor equip");
            return new ClientScenarioReport("blocked", id, observations);
        }

        boolean equipped = client.quickEquipSelectedArmor(
            "minecraft:iron_chestplate",
            "chest",
            INVENTORY_TIMEOUT
        );
        observations.add(
            "iron chestplate equip: " + (equipped ? "passed" : "failed")
                + " armor_slot=chest"
                + " item=minecraft:iron_chestplate"
                + " quick_move_equipped=" + equipped
        );
        return new ClientScenarioReport(equipped ? "passed" : "failed", id, observations);
    }

    static ClientScenarioReport measureIronChestplateZombieMitigation(
        String id,
        List<String> observations,
        ScenarioClient client,
        String observationPrefix
    ) throws Exception {
        String entityObservationPrefix = "iron chestplate zombie mitigation".equals(observationPrefix)
            ? "iron chestplate zombie"
            : observationPrefix;
        boolean nightReached = client.waitForDayTimeAtOrAfter(NIGHT_START_DAY_TIME, NIGHT_WAIT_TIMEOUT);
        observations.add(
            "natural night wait for " + observationPrefix + ": " + (nightReached ? "passed" : "failed")
                + " night_start_day_time=" + NIGHT_START_DAY_TIME
        );
        if (!nightReached) {
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioEntityObservation zombie = client.findVisibleEntity(
            ZOMBIE_ENTITY_IDS,
            ScenarioReach.OUTSIDE_SURVIVAL_REACH,
            NATURAL_ZOMBIE_SCAN_TIMEOUT
        );
        if (zombie == null) {
            zombie = client.findVisibleEntity(
                ZOMBIE_ENTITY_IDS,
                ScenarioReach.WITHIN_SURVIVAL_REACH,
                ENTITY_SCAN_TIMEOUT
            );
        }
        if (zombie == null) {
            observations.add("blocked: no loaded natural zombie visible after nightfall");
            return new ClientScenarioReport("blocked", id, observations);
        }
        observations.add(
            entityObservationPrefix + " scan: passed"
                + " entity=" + zombie.entityType()
                + " entity_id=" + zombie.entityId()
                + " distance_squared=" + zombie.distanceSquared()
        );

        boolean approached = client.approachEntity(zombie, APPROACH_TIMEOUT);
        observations.add(
            entityObservationPrefix + " approach: " + (approached ? "passed" : "failed")
                + " entity_id=" + zombie.entityId()
        );
        if (!approached) {
            return new ClientScenarioReport("blocked", id, observations);
        }

        float healthBefore = client.playerHealth();
        float healthAfter = client.waitForPlayerHealthBelow(healthBefore, HOSTILE_HIT_TIMEOUT);
        float damageTaken = healthBefore - healthAfter;
        boolean observedHit = damageTaken > 0.0F;
        boolean survived = healthAfter > 0.0F;
        boolean mitigated = damageTaken <= IRON_CHESTPLATE_MAX_ZOMBIE_HIT_DAMAGE;
        boolean passed = observedHit && survived && mitigated;
        observations.add(
            observationPrefix + ": " + (passed ? "passed" : "failed")
                + " entity_id=" + zombie.entityId()
                + " health_before=" + healthBefore
                + " health_after=" + healthAfter
                + " damage_taken=" + damageTaken
                + " max_expected_damage=" + IRON_CHESTPLATE_MAX_ZOMBIE_HIT_DAMAGE
                + " observed_hit=" + observedHit
                + " survived=" + survived
                + " mitigated=" + mitigated
        );
        return new ClientScenarioReport(passed ? "passed" : "failed", id, observations);
    }

    static ClientScenarioReport runIronChestplateSaveRestartMitigationBefore(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        PlayableToolStage.IronChestplateProgressionResult chestplate = PlayableToolStage.craftEarnedIronChestplateProgressionResult(
            id,
            observations,
            client
        );
        if (!"passed".equals(chestplate.report().result())) {
            return chestplate.report();
        }
        if (chestplate.tableTarget() == null) {
            observations.add("restart marker placement: failed missing crafted table target");
            return new ClientScenarioReport("failed", id, observations);
        }

        ClientScenarioReport equipped = PlayableCombatStage.equipEarnedIronChestplate(id, observations, client);
        if (!"passed".equals(equipped.result())) {
            return equipped;
        }

        PlayablePersistenceStage.writeMarker(PlayablePersistenceStage.saveRestartMarkerPath(screenshotsDir), chestplate.tableTarget());
        observations.add("restart marker placement: passed target=" + coordinates(chestplate.tableTarget()));
        observations.add("runner-managed restart: pending clean server restart and iron-chestplate rejoin check");
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport("passed", id, observations);
    }

    static ClientScenarioReport runIronChestplateSaveRestartMitigationAfter(
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
        ScenarioHeldItem chestArmor = client.equippedArmor("chest");
        boolean armorPersisted = chestArmor.matches("minecraft:iron_chestplate", 1);
        observations.add(
            "restart marker persistence: " + (markerPersisted ? "passed" : "failed")
                + " target=" + coordinates(marker)
        );
        observations.add(
            "iron chestplate armor persistence: " + (armorPersisted ? "passed" : "failed")
                + " armor_slot=chest"
                + " item=" + chestArmor.itemId()
                + " count=" + chestArmor.count()
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        if (!markerPersisted || !armorPersisted) {
            return new ClientScenarioReport("failed", id, observations);
        }

        return PlayableCombatStage.measureIronChestplateZombieMitigation(
            id,
            observations,
            client,
            "iron chestplate restarted zombie mitigation"
        );
    }

}
