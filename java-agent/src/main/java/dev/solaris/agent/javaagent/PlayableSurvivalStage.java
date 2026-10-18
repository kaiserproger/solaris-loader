package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;
import dev.solaris.agent.javaagent.PlayableResourceStage.LogToPlanksResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.APPROACH_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.ENTITY_ATTACK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.HOTBAR_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.INVENTORY_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.NIGHT_START_DAY_TIME;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.PICKUP_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.RESPAWN_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayablePersistenceStage.readMarker;
import static dev.solaris.agent.javaagent.PlayablePersistenceStage.saveRestartMarkerPath;

/** Continuous survival soak, natural encounters, resource work, and death recovery. */
final class PlayableSurvivalStage {
    private PlayableSurvivalStage() {
    }
    private static final float SURVIVAL_HOSTILE_DEFENSE_MIN_HEALTH = 15.0F;
    /** Combat acceptance over the whole soak: at least this many hostiles must be killed ... */
    private static final int MIN_HOSTILES_NEUTRALIZED = 1;
    /** ... and the player may not die more often than this. */
    private static final int MAX_RECOVERED_DEATHS = 3;
    /** Damage taken within this many ticks means a hostile is attacking, even from outside melee reach. */
    private static final long UNDER_FIRE_TICKS = 100L;
    private static final double UNDER_FIRE_MAX_DISTANCE_SQUARED = 20.0 * 20.0;
    /** A healthy night player walks up to a visible hostile this close instead of waiting for it forever. */
    private static final double HUNT_MAX_DISTANCE_SQUARED = 48.0 * 48.0;
    private static final long DAY_LENGTH_TICKS = 24_000L;

    private static final List<String> PASSIVE_NATURAL_ENTITY_IDS = List.of(
        "minecraft:cow",
        "minecraft:pig",
        "minecraft:chicken",
        "minecraft:sheep"
    );

    private static final List<String> HOSTILE_ENTITY_IDS = List.of(
        "minecraft:zombie",
        "minecraft:skeleton",
        "minecraft:spider",
        // Run 20261016T095132: three slimes camped the respawn point and killed the player 15 times
        // while the stage ignored them; any hostile that damages the player is a target.
        "minecraft:slime"
    );
    private static final Duration SURVIVAL_SOAK_STEP_TIMEOUT = Duration.ofSeconds(30);
    private static final long SURVIVAL_RESOURCE_INTERVAL_TICKS = 500L;
    private static final long SURVIVAL_RESOURCE_RETRY_TICKS = 100L;
    private static final int MAX_CONSECUTIVE_RESOURCE_BLOCKS = 3;

    static ClientScenarioReport run(
        Duration survivalSoakDuration,
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        ClientScenarioReport beforeRestart = PlayablePersistenceStage.runSaveRestartBefore(id, observations, screenshotsDir, client);
        if (!"passed".equals(beforeRestart.result())) {
            return beforeRestart;
        }

        long soakMillis = survivalSoakDuration.toMillis();
        long soakTicks = Math.max(1L, (soakMillis + 49L) / 50L);
        observations.add("20-minute survival soak: started duration_millis=" + soakMillis);
        ScenarioHeldItem weapon = client.selectHotbarItem("minecraft:wooden_sword", 1, HOTBAR_TIMEOUT);
        if (!weapon.matches("minecraft:wooden_sword", 1)) {
            observations.add("20-minute survival soak: failed wooden_sword_selectable=false");
            return new ClientScenarioReport("failed", id, observations);
        }

        long startServerTime = client.serverGameTime();
        if (startServerTime == Long.MIN_VALUE) {
            startServerTime = client.waitForServerTimeAfter(Long.MIN_VALUE, SURVIVAL_SOAK_STEP_TIMEOUT);
        }
        if (startServerTime == Long.MIN_VALUE) {
            observations.add("20-minute survival soak: failed initial_server_time_packet=false");
            return new ClientScenarioReport("failed", id, observations);
        }
        long currentServerTime = startServerTime;
        long completedTicks = 0L;
        long nextResourceTick = 0L;
        int consecutiveResourceBlocks = 0;
        int resourceRuns = 0;
        boolean resourceWorkExhausted = false;
        int hostilesNeutralized = 0;
        int recoveredDeaths = 0;
        int engagements = 0;
        int unresolvedEngagements = 0;
        int attacksSent = 0;
        int hitsConfirmed = 0;
        float lastHealth = client.playerHealth();
        long lastDamageTick = Long.MIN_VALUE / 2;
        client.drainCombatTelemetry();
        boolean sawNaturalPassive = false;
        boolean sawNaturalHostile = false;
        List<ScenarioItemDropIdentity> pickaxeDropsBefore = client.visibleItemDropIdentities("minecraft:wooden_pickaxe");
        List<ScenarioItemDropIdentity> swordDropsBefore = client.visibleItemDropIdentities("minecraft:wooden_sword");
        while (completedTicks < soakTicks) {
            long nextServerTime = client.waitForServerTimeAfter(
                currentServerTime,
                SURVIVAL_SOAK_STEP_TIMEOUT
            );
            if (nextServerTime <= currentServerTime) {
                observations.add(
                    "20-minute survival soak: failed server_time_progress=false completed_ticks="
                        + completedTicks
                );
                return new ClientScenarioReport("failed", id, observations);
            }
            currentServerTime = nextServerTime;
            completedTicks = currentServerTime - startServerTime;
            float health = client.playerHealth();
            if (health <= 0.0F) {
                boolean recovered = recoverSurvivalDeath(
                    observations,
                    screenshotsDir,
                    client,
                    pickaxeDropsBefore,
                    swordDropsBefore,
                    completedTicks
                );
                if (!recovered) {
                    return new ClientScenarioReport("failed", id, observations);
                }
                recoveredDeaths++;
                weapon = client.selectHotbarItem("minecraft:wooden_sword", 1, HOTBAR_TIMEOUT);
                if (!weapon.matches("minecraft:wooden_sword", 1)) {
                    observations.add(
                        "20-minute survival soak: combat_weapon_unavailable_after_respawn=true continuing_soak=true"
                    );
                }
                continue;
            }

            List<ScenarioItemDropIdentity> nextPickaxeDropsBefore = client.visibleItemDropIdentities("minecraft:wooden_pickaxe");
            List<ScenarioItemDropIdentity> nextSwordDropsBefore = client.visibleItemDropIdentities("minecraft:wooden_sword");
            health = client.playerHealth();
            if (health <= 0.0F) {
                boolean recovered = recoverSurvivalDeath(
                    observations,
                    screenshotsDir,
                    client,
                    pickaxeDropsBefore,
                    swordDropsBefore,
                    completedTicks
                );
                if (!recovered) {
                    return new ClientScenarioReport("failed", id, observations);
                }
                recoveredDeaths++;
                weapon = client.selectHotbarItem("minecraft:wooden_sword", 1, HOTBAR_TIMEOUT);
                if (!weapon.matches("minecraft:wooden_sword", 1)) {
                    observations.add(
                        "20-minute survival soak: combat_weapon_unavailable_after_respawn=true continuing_soak=true"
                    );
                }
                pickaxeDropsBefore = client.visibleItemDropIdentities("minecraft:wooden_pickaxe");
                swordDropsBefore = client.visibleItemDropIdentities("minecraft:wooden_sword");
                continue;
            }
            if (health < lastHealth) {
                lastDamageTick = completedTicks;
                ScenarioEntityObservation attacker = client.visibleEntity(
                    HOSTILE_ENTITY_IDS,
                    ScenarioReach.WITHIN_SURVIVAL_REACH
                );
                if (attacker == null) {
                    attacker = client.visibleEntity(HOSTILE_ENTITY_IDS, ScenarioReach.OUTSIDE_SURVIVAL_REACH);
                }
                observations.add(
                    "survival damage taken: completed_ticks=" + completedTicks
                        + " health_before=" + lastHealth + " health_after=" + health
                        + " nearest_hostile=" + (attacker == null ? "none"
                            : attacker.entityType() + "#" + attacker.entityId()
                                + "@" + String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(attacker.distanceSquared())))
                );
            }
            lastHealth = health;
            pickaxeDropsBefore = nextPickaxeDropsBefore;
            swordDropsBefore = nextSwordDropsBefore;

            if (!sawNaturalPassive) {
                ScenarioEntityObservation passive = client.visibleEntity(
                    PASSIVE_NATURAL_ENTITY_IDS,
                    ScenarioReach.WITHIN_SURVIVAL_REACH
                );
                if (passive == null) {
                    passive = client.visibleEntity(
                        PASSIVE_NATURAL_ENTITY_IDS,
                        ScenarioReach.OUTSIDE_SURVIVAL_REACH
                    );
                }
                if (passive != null) {
                    sawNaturalPassive = true;
                    observations.add(
                        "natural passive spawn observation: passed entity=" + passive.entityType()
                            + " entity_id=" + passive.entityId()
                            + " distance_squared=" + passive.distanceSquared()
                    );
                }
            }

            boolean night = isNightTime(currentServerTime);
            boolean underFire = completedTicks - lastDamageTick <= UNDER_FIRE_TICKS;
            ScenarioEntityObservation hostile = client.visibleEntity(
                HOSTILE_ENTITY_IDS,
                ScenarioReach.WITHIN_SURVIVAL_REACH
            );
            boolean hostileWithinReach = hostile != null;
            if (hostile == null && (night || underFire)) {
                hostile = client.visibleEntity(
                    HOSTILE_ENTITY_IDS,
                    ScenarioReach.OUTSIDE_SURVIVAL_REACH
                );
            }
            if (hostile != null) {
                if (!sawNaturalHostile) {
                    sawNaturalHostile = true;
                    observations.add(
                        "natural hostile spawn observation: passed entity=" + hostile.entityType()
                            + " entity_id=" + hostile.entityId()
                            + " distance_squared=" + hostile.distanceSquared()
                    );
                }
                if (!hostileWithinReach) {
                    boolean hunt = health >= SURVIVAL_HOSTILE_DEFENSE_MIN_HEALTH
                        && hostile.distanceSquared() <= HUNT_MAX_DISTANCE_SQUARED;
                    if (!hunt && !(underFire && hostile.distanceSquared() <= UNDER_FIRE_MAX_DISTANCE_SQUARED)) {
                        observations.add(
                            "survival hostile defense: observed outside_reach=true engagement=deferred entity="
                                + hostile.entityType()
                                + " entity_id=" + hostile.entityId()
                                + " distance=" + String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(hostile.distanceSquared()))
                                + " health=" + health
                        );
                        continue;
                    }
                    observations.add(
                        "survival hostile defense: " + (underFire ? "under_fire=true" : "hunt=true")
                            + " engaging entity=" + hostile.entityType()
                            + " entity_id=" + hostile.entityId() + " health=" + health
                    );
                }
                boolean injured = health < SURVIVAL_HOSTILE_DEFENSE_MIN_HEALTH;
                if (!weapon.matches("minecraft:wooden_sword", 1)) {
                    observations.add(
                        "survival hostile defense: engagement=deferred no_combat_weapon=true entity="
                            + hostile.entityType()
                            + " entity_id=" + hostile.entityId()
                            + " health=" + health
                    );
                    continue;
                }
                // An injured player never charges a hostile that is not hurting it, but a hostile
                // inside reach (or one that is shooting it) must be answered: the driver retreats/eats
                // while healing and otherwise fights in place instead of idling under attack.
                boolean approached = (hostileWithinReach && injured) || client.approachEntity(hostile, APPROACH_TIMEOUT);
                boolean gone = approached && client.attackEntityUntilRemoved(hostile, ENTITY_ATTACK_TIMEOUT);
                float healthAfterCombat = client.playerHealth();
                engagements++;
                for (ScenarioCombatRecord record : client.drainCombatTelemetry()) {
                    observations.add(record.line());
                    attacksSent += record.attack() ? 1 : 0;
                    hitsConfirmed += record.hitConfirmed() ? 1 : 0;
                }
                observations.add(
                    "survival hostile defense: " + (gone && healthAfterCombat > 0.0F ? "passed" : "failed")
                        + " entity=" + hostile.entityType()
                        + " entity_id=" + hostile.entityId()
                        + " approached=" + approached
                        + " entity_gone=" + gone
                        + " injured_engagement=" + injured
                        + " health_before=" + health
                        + " health_after=" + healthAfterCombat
                );
                if (healthAfterCombat <= 0.0F) {
                    boolean recovered = recoverSurvivalDeath(
                        observations,
                        screenshotsDir,
                        client,
                        pickaxeDropsBefore,
                        swordDropsBefore,
                        completedTicks
                    );
                    if (!recovered) {
                        return new ClientScenarioReport("failed", id, observations);
                    }
                    recoveredDeaths++;
                    weapon = client.selectHotbarItem("minecraft:wooden_sword", 1, HOTBAR_TIMEOUT);
                    if (!weapon.matches("minecraft:wooden_sword", 1)) {
                        observations.add(
                            "20-minute survival soak: combat_weapon_unavailable_after_respawn=true continuing_soak=true"
                        );
                    }
                    continue;
                }
                if (!gone) {
                    // The aggregate acceptance (neutralized hostiles, bounded deaths) judges the run;
                    // the per-attack telemetry above attributes why this engagement did not finish.
                    unresolvedEngagements++;
                    continue;
                }
                hostilesNeutralized++;
                continue;
            }

            if (completedTicks < nextResourceTick) {
                continue;
            }

            if (night) {
                continue;
            }

            if (resourceWorkExhausted) {
                continue;
            }

            String planksItemId = PlayableResourceStage.availablePlanksItem(client);
            String workItemId = weapon.itemId();
            if (planksItemId != null) {
                ScenarioHeldItem workItem = client.selectHotbarItem(planksItemId, 1, HOTBAR_TIMEOUT);
                if (!workItem.matches(planksItemId, 1)) {
                    observations.add("survival resource work: failed planks_selectable=false item=" + planksItemId);
                    return new ClientScenarioReport("failed", id, observations);
                }
                workItemId = planksItemId;
            }

            List<String> resourceObservations = new ArrayList<>();
            LogToPlanksResult resourceWork = PlayableResourceStage.runLogToPlanks(
                id,
                resourceObservations,
                client,
                1,
                false,
                false,
                false
            );
            if (client.playerHealth() <= 0.0F) {
                boolean recovered = recoverSurvivalDeath(
                    observations,
                    screenshotsDir,
                    client,
                    pickaxeDropsBefore,
                    swordDropsBefore,
                    completedTicks
                );
                if (!recovered) {
                    return new ClientScenarioReport("failed", id, observations);
                }
                recoveredDeaths++;
                weapon = client.selectHotbarItem("minecraft:wooden_sword", 1, HOTBAR_TIMEOUT);
                if (!weapon.matches("minecraft:wooden_sword", 1)) {
                    observations.add(
                        "20-minute survival soak: combat_weapon_unavailable_after_respawn=true continuing_soak=true"
                    );
                }
                continue;
            }
            if ("passed".equals(resourceWork.report().result())) {
                resourceRuns++;
                consecutiveResourceBlocks = 0;
                nextResourceTick = completedTicks + SURVIVAL_RESOURCE_INTERVAL_TICKS;
                observations.add(
                    "survival resource work: passed run=" + resourceRuns
                        + " completed_ticks=" + completedTicks
                        + " work_item=" + workItemId
                );
            } else if ("blocked".equals(resourceWork.report().result())) {
                consecutiveResourceBlocks++;
                nextResourceTick = completedTicks + SURVIVAL_RESOURCE_RETRY_TICKS;
                observations.add(
                    "survival resource work: blocked completed_ticks=" + completedTicks
                        + " consecutive=" + consecutiveResourceBlocks
                );
                if (consecutiveResourceBlocks >= MAX_CONSECUTIVE_RESOURCE_BLOCKS) {
                    resourceWorkExhausted = true;
                    observations.add(
                        "survival resource work: exhausted no_loaded_reachable_log=true continuing_soak=true"
                    );
                }
            } else {
                observations.addAll(resourceObservations);
                observations.add("survival resource work: failed completed_ticks=" + completedTicks);
                return new ClientScenarioReport("failed", id, observations);
            }
            weapon = client.selectHotbarItem("minecraft:wooden_sword", 1, HOTBAR_TIMEOUT);
            if (!weapon.matches("minecraft:wooden_sword", 1)) {
                observations.add(
                    "20-minute survival soak: combat_weapon_unavailable_after_work=true continuing_soak=true"
                );
            }
        }

        ScenarioHeldItem selected = client.selectedItem();
        int woodenPickaxeCount = client.inventoryCount("minecraft:wooden_pickaxe");
        boolean inventoryStillPresent = woodenPickaxeCount >= 1;
        boolean naturalSpawnEvidence = sawNaturalPassive && sawNaturalHostile;
        observations.add(
            "natural spawn acceptance: " + (naturalSpawnEvidence ? "passed" : "failed")
                + " passive_observed=" + sawNaturalPassive
                + " hostile_observed=" + sawNaturalHostile
        );
        boolean combatAcceptance = combatAcceptancePassed(hostilesNeutralized, recoveredDeaths);
        observations.add(
            "survival combat acceptance: " + (combatAcceptance ? "passed" : "failed")
                + " hostiles_neutralized=" + hostilesNeutralized + " required_min=" + MIN_HOSTILES_NEUTRALIZED
                + " recovered_deaths=" + recoveredDeaths + " allowed_max=" + MAX_RECOVERED_DEATHS
                + " engagements=" + engagements + " unresolved_engagements=" + unresolvedEngagements
                + " attacks_sent=" + attacksSent + " hits_confirmed=" + hitsConfirmed
        );
        boolean soakPassed = inventoryStillPresent && naturalSpawnEvidence && combatAcceptance;
        observations.add(
            "20-minute survival soak: " + (soakPassed ? "passed" : "failed")
                + " duration_millis=" + soakMillis
                + " ticks=" + soakTicks
                + " resource_runs=" + resourceRuns
                + " hostiles_neutralized=" + hostilesNeutralized
                + " recovered_deaths=" + recoveredDeaths
                + " selected=" + selected.itemId() + " x" + selected.count()
                + " wooden_pickaxe_count=" + woodenPickaxeCount
        );
        observations.add("runner-managed restart: pending clean server restart and post-restart rejoin check");
        return new ClientScenarioReport(soakPassed ? "passed" : "failed", id, observations);
    }

    private static boolean recoverSurvivalDeath(
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client,
        List<ScenarioItemDropIdentity> pickaxeDropsBefore,
        List<ScenarioItemDropIdentity> swordDropsBefore,
        long completedTicks
    ) throws Exception {
        boolean deathScreen = client.waitForDeathScreen(RESPAWN_TIMEOUT);
        observations.add(
            "survival death recovery: death_screen=" + deathScreen
                + " completed_ticks=" + completedTicks
        );
        if (!deathScreen) {
            return false;
        }

        ScenarioItemDropIdentity pickaxeDrop = client.waitForNewVisibleItemDropIdentity(
            "minecraft:wooden_pickaxe",
            pickaxeDropsBefore,
            PICKUP_TIMEOUT
        );
        ScenarioItemDropIdentity swordDrop = client.waitForNewVisibleItemDropIdentity(
            "minecraft:wooden_sword",
            swordDropsBefore,
            PICKUP_TIMEOUT
        );
        boolean pickaxeDropAttributable = pickaxeDrop != null
            && !pickaxeDropsBefore.contains(pickaxeDrop);
        boolean swordDropAttributable = swordDrop == null
            || !swordDropsBefore.contains(swordDrop);
        boolean attributableDrops = pickaxeDropAttributable && swordDropAttributable;
        observations.add(
            "survival death drops: " + (attributableDrops ? "passed" : "failed")
                + " pickaxe_identity=" + (pickaxeDrop == null ? "missing" : pickaxeDrop)
                + " sword_identity=" + (swordDrop == null ? "missing_or_consumed" : swordDrop)
                + " sword_recovery_required=" + (swordDrop != null)
        );
        if (!attributableDrops) {
            return false;
        }

        boolean respawned = client.performRespawn(RESPAWN_TIMEOUT);
        observations.add("survival death respawn: " + (respawned ? "passed" : "failed"));
        if (!respawned) {
            return false;
        }

        Path markerPath = saveRestartMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("survival death recovery: failed missing_restart_marker=" + markerPath);
            return false;
        }
        ScenarioBlockTarget anchor = readMarker(markerPath, "survival-death-recovery");
        int pickaxeCountAfterRespawn = client.inventoryCount("minecraft:wooden_pickaxe");
        int swordCountAfterRespawn = client.inventoryCount("minecraft:wooden_sword");
        // Death empties the inventory, so a pickaxe held right after respawn is the drop,
        // collected on arrival when the respawn point lies next to the death drops.
        boolean pickaxeAutoRecovered = pickaxeCountAfterRespawn > 0;
        boolean pickaxeRecovered = pickaxeAutoRecovered;
        if (!pickaxeAutoRecovered) {
            ScenarioBreakResult pickaxePickup = client.collectVisibleItemDropByIdentity(
                anchor,
                "minecraft:wooden_pickaxe",
                pickaxeDrop,
                1,
                APPROACH_TIMEOUT
            );
            pickaxeRecovered = pickaxePickup.pickupRestored()
                && client.waitForInventoryCount(
                    "minecraft:wooden_pickaxe",
                    pickaxeCountAfterRespawn + 1,
                    INVENTORY_TIMEOUT
                );
        }

        int swordCountBeforePickup = client.inventoryCount("minecraft:wooden_sword");
        boolean swordRecoveryRequired = swordDrop != null;
        boolean swordAutoRecovered = swordRecoveryRequired && swordCountBeforePickup > 0;
        boolean swordRecovered = !swordRecoveryRequired;
        if (swordRecoveryRequired && !swordAutoRecovered) {
            ScenarioBreakResult swordPickup = client.collectVisibleItemDropByIdentity(
                anchor,
                "minecraft:wooden_sword",
                swordDrop,
                1,
                APPROACH_TIMEOUT
            );
            swordRecovered = swordPickup.pickupRestored()
                && client.waitForInventoryCount(
                    "minecraft:wooden_sword",
                    swordCountAfterRespawn + 1,
                    INVENTORY_TIMEOUT
                );
        } else if (swordAutoRecovered) {
            swordRecovered = true;
        }
        boolean recovered = pickaxeRecovered && swordRecovered;
        observations.add(
            "survival death-drop recovery: " + (recovered ? "passed" : "failed")
                + " pickaxe_recovered=" + pickaxeRecovered
                + " sword_recovered=" + swordRecovered
                + " pickaxe_auto_recovered=" + pickaxeAutoRecovered
                + " sword_auto_recovered=" + swordAutoRecovered
                + " sword_recovery_required=" + swordRecoveryRequired
        );
        return recovered;
    }

    static boolean combatAcceptancePassed(int hostilesNeutralized, int recoveredDeaths) {
        return hostilesNeutralized >= MIN_HOSTILES_NEUTRALIZED && recoveredDeaths <= MAX_RECOVERED_DEATHS;
    }

    static boolean isNightTime(long gameTime) {
        return Math.floorMod(gameTime, DAY_LENGTH_TICKS) >= NIGHT_START_DAY_TIME;
    }
}
