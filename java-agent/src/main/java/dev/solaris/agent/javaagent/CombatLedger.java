package dev.solaris.agent.javaagent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Per-attack ledger. An attack is recorded when the packet is sent with the target's health as the
 * client currently knows it (server-synchronised entity data); the "after" value is the target's
 * health observed at least {@link #CONFIRM_TICKS} client ticks later, or {@code removed} once the
 * server removed the entity.
 */
final class CombatLedger {
    static final long CONFIRM_TICKS = 3L;

    private static final class Pending {
        final long tick;
        final String entityType;
        final int entityId;
        final double distance;
        final float healthBefore;
        final float playerHealth;
        float lastObserved;

        Pending(long tick, String entityType, int entityId, double distance, float healthBefore, float playerHealth) {
            this.tick = tick;
            this.entityType = entityType;
            this.entityId = entityId;
            this.distance = distance;
            this.healthBefore = healthBefore;
            this.playerHealth = playerHealth;
            this.lastObserved = healthBefore;
        }
    }

    private final List<Pending> pending = new ArrayList<>();
    private final List<ScenarioCombatRecord> records = new ArrayList<>();

    synchronized void attackSent(
        long tick, String entityType, int entityId, double distance, float targetHealth, float playerHealth
    ) {
        pending.add(new Pending(tick, entityType, entityId, distance, targetHealth, playerHealth));
    }

    /** Feeds the latest synchronised health of a target seen on {@code tick}. */
    synchronized void observeTarget(int entityId, long tick, float health) {
        for (int i = 0; i < pending.size(); ) {
            Pending attack = pending.get(i);
            if (attack.entityId != entityId) {
                i++;
                continue;
            }
            attack.lastObserved = health;
            if (tick - attack.tick >= CONFIRM_TICKS) {
                finish(pending.remove(i), health, false);
            } else {
                i++;
            }
        }
    }

    synchronized void playerDamaged(
        long tick, float healthBefore, float healthAfter, int targetId, int hostilesWithin6, String hostiles
    ) {
        records.add(new ScenarioCombatRecord(
            "combat damage: tick=" + tick + " player_health_before=" + healthBefore
                + " player_health_after=" + healthAfter + " target_id=" + targetId
                + " hostiles_within_6=" + hostilesWithin6 + " hostiles=" + hostiles,
            false,
            false
        ));
    }

    synchronized void targetRemoved(int entityId) {
        for (int i = 0; i < pending.size(); ) {
            if (pending.get(i).entityId == entityId) {
                finish(pending.remove(i), 0.0F, true);
            } else {
                i++;
            }
        }
    }

    synchronized void engagement(String phase, String entityType, int entityId, int sent, String withheld, String outcome) {
        records.add(new ScenarioCombatRecord(
            "combat engagement: phase=" + phase + " entity=" + entityType + " entity_id=" + entityId
                + " attacks_sent=" + sent + " withheld=" + withheld + " outcome=" + outcome,
            false,
            false
        ));
    }

    /** Resolves attacks still waiting for confirmation with their last observed health, then returns and clears all records. */
    synchronized List<ScenarioCombatRecord> drain() {
        for (Pending attack : pending) {
            finish(attack, attack.lastObserved, false);
        }
        pending.clear();
        List<ScenarioCombatRecord> drained = List.copyOf(records);
        records.clear();
        return drained;
    }

    private void finish(Pending attack, float healthAfter, boolean removed) {
        boolean hit = removed || healthAfter < attack.healthBefore;
        records.add(new ScenarioCombatRecord(
            "combat attack: tick=" + attack.tick + " entity=" + attack.entityType + " entity_id=" + attack.entityId
                + String.format(Locale.ROOT, " distance=%.2f", attack.distance)
                + " sent=true target_health_before=" + attack.healthBefore
                + " target_health_after=" + (removed ? "removed" : Float.toString(healthAfter))
                + " hit_confirmed=" + hit + " player_health=" + attack.playerHealth,
            true,
            hit
        ));
    }
}
