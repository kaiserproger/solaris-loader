package dev.solaris.agent.javaagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CombatLedgerTest {
    @Test
    void attackIsConfirmedByLowerTargetHealthObservedAfterTheConfirmWindow() {
        CombatLedger ledger = new CombatLedger();
        ledger.attackSent(100L, "minecraft:zombie", 7, 2.4, 20.0F, 18.0F);
        ledger.observeTarget(7, 101L, 20.0F);
        ledger.observeTarget(7, 103L, 16.0F);

        List<ScenarioCombatRecord> records = ledger.drain();

        assertEquals(1, records.size());
        assertTrue(records.get(0).attack());
        assertTrue(records.get(0).hitConfirmed());
        assertTrue(records.get(0).line().contains("tick=100 entity=minecraft:zombie entity_id=7 distance=2.40"), records.get(0).line());
        assertTrue(records.get(0).line().contains("target_health_before=20.0 target_health_after=16.0"), records.get(0).line());
        assertTrue(records.get(0).line().contains("player_health=18.0"), records.get(0).line());
    }

    @Test
    void attackWithUnchangedHealthIsReportedAsUnconfirmed() {
        CombatLedger ledger = new CombatLedger();
        ledger.attackSent(10L, "minecraft:skeleton", 3, 2.9, 20.0F, 20.0F);
        ledger.observeTarget(3, 14L, 20.0F);

        ScenarioCombatRecord record = ledger.drain().get(0);

        assertFalse(record.hitConfirmed());
        assertTrue(record.line().contains("hit_confirmed=false"), record.line());
    }

    @Test
    void removalConfirmsEveryPendingAttackOnThatTarget() {
        CombatLedger ledger = new CombatLedger();
        ledger.attackSent(10L, "minecraft:zombie", 5, 2.0, 4.0F, 20.0F);
        ledger.attackSent(22L, "minecraft:zombie", 5, 2.0, 4.0F, 20.0F);
        ledger.attackSent(22L, "minecraft:zombie", 6, 2.0, 20.0F, 20.0F);
        ledger.targetRemoved(5);

        List<ScenarioCombatRecord> records = ledger.drain();

        assertEquals(3, records.size());
        assertEquals(2, records.stream().filter(record -> record.line().contains("target_health_after=removed")).count());
        assertEquals(2, records.stream().filter(ScenarioCombatRecord::hitConfirmed).count());
    }

    @Test
    void drainClearsAndEngagementSummaryIsNotAnAttack() {
        CombatLedger ledger = new CombatLedger();
        ledger.engagement("attack", "minecraft:zombie", 5, 0, "not_ready:3", "timeout");

        List<ScenarioCombatRecord> records = ledger.drain();

        assertEquals(1, records.size());
        assertFalse(records.get(0).attack());
        assertTrue(records.get(0).line().contains("attacks_sent=0 withheld=not_ready:3 outcome=timeout"), records.get(0).line());
        assertTrue(ledger.drain().isEmpty());
    }

    @Test
    void playerDamageIsRecordedWithTheHostilesAroundIt() {
        CombatLedger ledger = new CombatLedger();
        ledger.playerDamaged(77L, 17.5F, 14.5F, 9, 2, "zombie#9@1.2,zombie#11@2.8");

        ScenarioCombatRecord record = ledger.drain().get(0);

        assertFalse(record.attack());
        assertTrue(record.line().contains("tick=77 player_health_before=17.5 player_health_after=14.5 target_id=9 hostiles_within_6=2"), record.line());
    }
}
