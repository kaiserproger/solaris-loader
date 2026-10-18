package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlayableSurvivalStageTest {
    @Test
    void fullLoopCraftsToolSoaksAndWritesRestartMarkerWithoutDebugSetup() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.visiblePassiveDuringSoak = true;
        client.visibleHostileDuringSoak = true;
        Path screenshotsDir = Path.of("build/tmp/playable-04-test/screenshots");
        Files.deleteIfExists(screenshotsDir.getParent().resolve("playable-03-save-restart-marker.properties"));

        ClientScenarioReport report = new PlayableRealClientLoopScenario(Duration.ofMillis(1_050)).run(
            "playable-04-twenty-minute-survival-loop",
            screenshotsDir,
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "playable loop must not fall back to debug setup");
        assertTrue(
            screenshotsDir.getParent().resolve("playable-03-save-restart-marker.properties").toFile().isFile(),
            "full playable loop must write marker coordinates for the runner-managed restart phase"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("20-minute survival soak: passed")),
            "full playable loop must record the survival soak evidence"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("natural spawn acceptance: passed")),
            "the full playable loop must fail closed unless passive and hostile natural spawn evidence was observed"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("survival resource work: passed")),
            "the long playable loop must perform useful work instead of standing AFK"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("wooden sword recipe: passed")),
            "the long survival loop must prepare a basic combat weapon before night"
        );
    }


    @Test
    void fullLoopFailsClosedWhenNaturalSpawnEvidenceNeverAppears() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario(Duration.ofMillis(1)).run(
            "playable-04-twenty-minute-survival-loop",
            Path.of("build/tmp/playable-04-natural-spawn-missing-test/screenshots"),
            client
        );

        assertEquals("failed", report.result(), () -> String.join("\n", report.observations()));
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("natural spawn acceptance: failed")
                    && entry.contains("passive_observed=false")
                    && entry.contains("hostile_observed=false")
            ),
            () -> String.join("\n", report.observations())
        );
    }

    @Test
    void fullLoopDefendsAgainstVisibleHostileDuringSurvivalSoak() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.visiblePassiveDuringSoak = true;
        client.visibleHostileDuringSoak = true;

        ClientScenarioReport report = new PlayableRealClientLoopScenario(Duration.ofMillis(1)).run(
            "playable-04-twenty-minute-survival-loop",
            Path.of("build/tmp/playable-04-hostile-test/screenshots"),
            client
        );

        assertEquals("passed", report.result(), () -> String.join("\n", report.observations()));
        assertTrue(
            client.operations.stream().anyMatch(operation -> operation.startsWith("attackEntityUntilRemoved:")),
            "the long survival gate must react to a visible hostile instead of remaining AFK"
        );
    }

    @Test
    void fullLoopContinuesAfterLocalLogsAreExhaustedOnceUsefulWorkWasProven() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.visiblePassiveDuringSoak = true;
        client.visibleHostileDuringSoak = true;
        client.logsUnavailableDuringSoakAfterTick = 60L;

        ClientScenarioReport report = new PlayableRealClientLoopScenario(Duration.ofSeconds(40)).run(
            "playable-04-twenty-minute-survival-loop",
            Path.of("build/tmp/playable-04-resource-exhaustion-test/screenshots"),
            client
        );

        assertEquals("passed", report.result(), () -> String.join("\n", report.observations()));
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("survival resource work: exhausted")
                    && entry.contains("continuing_soak=true")
            ),
            () -> String.join("\n", report.observations())
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("natural spawn acceptance: passed")),
            "resource exhaustion must not bypass the passive+hostile natural-spawn gate"
        );
        assertFalse(
            client.operations.stream().anyMatch(operation -> operation.contains("far-natural-log")),
            "periodic survival work must remain nearby-only after initial natural progression"
        );
    }

    @Test
    void fullLoopDefendsAgainstVisibleSpiderDuringSurvivalSoak() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.visiblePassiveDuringSoak = true;
        client.visibleHostileDuringSoak = true;
        client.visibleHostileTypeDuringSoak = "minecraft:spider";

        ClientScenarioReport report = new PlayableRealClientLoopScenario(Duration.ofMillis(1)).run(
            "playable-04-twenty-minute-survival-loop",
            Path.of("build/tmp/playable-04-spider-test/screenshots"),
            client
        );

        assertEquals("passed", report.result(), () -> String.join("\n", report.observations()));
        assertTrue(
            client.operations.contains("attackEntityUntilRemoved:minecraft:spider:199"),
            "the long survival gate must defend against the spider that attacks the player"
        );
    }

    @Test
    void fullLoopDefendsAgainstVisibleSlimeDuringSurvivalSoak() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.visiblePassiveDuringSoak = true;
        client.visibleHostileDuringSoak = true;
        client.visibleHostileTypeDuringSoak = "minecraft:slime";

        ClientScenarioReport report = new PlayableRealClientLoopScenario(Duration.ofMillis(1)).run(
            "playable-04-twenty-minute-survival-loop",
            Path.of("build/tmp/playable-04-slime-test/screenshots"),
            client
        );

        assertEquals("passed", report.result(), () -> String.join("\n", report.observations()));
        assertTrue(
            client.operations.contains("attackEntityUntilRemoved:minecraft:slime:199"),
            "slimes camping the respawn point must be fought, not ignored"
        );
    }

    @Test
    void fullLoopObservesDistantHostileAtNightWithoutChargingAcrossTheWorld() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.visiblePassiveDuringSoak = true;
        client.clientTicks = 12_522L;
        client.visibleHostileOutsideReachDuringSoak = true;

        ClientScenarioReport report = new PlayableRealClientLoopScenario(Duration.ofMillis(1)).run(
            "playable-04-twenty-minute-survival-loop",
            Path.of("build/tmp/playable-04-distant-hostile-test/screenshots"),
            client
        );

        assertEquals("failed", report.result(), () -> String.join("\n", report.observations()));
        assertTrue(
            client.operations.contains("visibleEntity:minecraft:zombie|minecraft:skeleton|minecraft:spider|minecraft:slime:outside-survival-reach"),
            "the long survival gate must scan beyond melee reach once night begins"
        );
        assertFalse(
            client.operations.stream().anyMatch(operation -> operation.startsWith("attackEntityUntilRemoved:")),
            "the long survival gate must observe distant natural hostiles without suicidal long-range pursuit"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("outside_reach=true") && entry.contains("engagement=deferred")
            ),
            () -> String.join("\n", report.observations())
        );
    }

    @Test
    void fullLoopHuntsAHealthyNightHostileThatStaysOutsideMeleeReach() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.visiblePassiveDuringSoak = true;
        client.clientTicks = 12_522L;
        client.visibleHostileOutsideReachDuringSoak = true;
        client.outsideReachHostileDistanceSquared = 15.0 * 15.0;

        ClientScenarioReport report = new PlayableRealClientLoopScenario(Duration.ofMillis(1)).run(
            "playable-04-twenty-minute-survival-loop",
            Path.of("build/tmp/playable-04-hunt-test/screenshots"),
            client
        );

        assertEquals("passed", report.result(), () -> String.join("\n", report.observations()));
        assertTrue(
            client.operations.stream().anyMatch(operation -> operation.startsWith("attackEntityUntilRemoved:")),
            "a healthy night player must go and kill a visible hostile instead of waiting for it forever"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("hunt=true engaging")),
            () -> String.join("\n", report.observations())
        );
    }

    @Test
    void fullLoopDoesNotChaseDistantHostileWhileBadlyInjured() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.visiblePassiveDuringSoak = true;
        client.clientTicks = 12_522L;
        client.healthAfterHostileCombat = 6.0F;
        client.visibleHostileOutsideReachDuringSoak = true;

        ClientScenarioReport report = new PlayableRealClientLoopScenario(Duration.ofMillis(1)).run(
            "playable-04-twenty-minute-survival-loop",
            Path.of("build/tmp/playable-04-injured-test/screenshots"),
            client
        );

        assertEquals("failed", report.result(), () -> String.join("\n", report.observations()));
        assertFalse(
            client.operations.stream().anyMatch(operation -> operation.startsWith("attackEntityUntilRemoved:")),
            "an injured survival player must not charge a hostile outside melee reach"
        );
    }

    @Test
    void fullLoopDefendsInPlaceWithoutChargingWhenBadlyInjuredHostileIsWithinReach() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.visiblePassiveDuringSoak = true;
        client.visibleHostileDuringSoak = true;
        client.healthAfterHostileCombat = 6.0F;

        ClientScenarioReport report = new PlayableRealClientLoopScenario(Duration.ofMillis(1)).run(
            "playable-04-twenty-minute-survival-loop",
            Path.of("build/tmp/playable-04-injured-within-reach-test/screenshots"),
            client
        );

        assertEquals("passed", report.result(), () -> String.join("\n", report.observations()));
        assertFalse(
            client.operations.stream().anyMatch(operation -> operation.startsWith("approachEntity:")),
            "a badly injured survival player must not charge a hostile"
        );
        assertTrue(
            client.operations.stream().anyMatch(operation -> operation.startsWith("attackEntityUntilRemoved:")),
            "a hostile already inside reach must be answered instead of idling under attack"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("survival hostile defense:") && entry.contains("injured_engagement=true")
            ),
            () -> String.join("\n", report.observations())
        );
    }

    @Test
    void fullLoopEngagesHostileAtInclusiveFifteenHealthBoundary() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.visiblePassiveDuringSoak = true;
        client.visibleHostileDuringSoak = true;
        client.healthAfterHostileCombat = 15.0F;

        ClientScenarioReport report = new PlayableRealClientLoopScenario(Duration.ofMillis(1)).run(
            "playable-04-twenty-minute-survival-loop",
            Path.of("build/tmp/playable-04-fifteen-health-boundary-test/screenshots"),
            client
        );

        assertEquals("passed", report.result(), () -> String.join("\n", report.observations()));
        assertTrue(
            client.operations.stream().anyMatch(operation -> operation.startsWith("attackEntityUntilRemoved:")),
            "exactly 15 health must remain inside the active-defense boundary"
        );
        assertFalse(
            report.observations().stream().anyMatch(entry -> entry.contains("engagement=deferred_low_health")),
            () -> String.join("\n", report.observations())
        );
    }

    @Test
    void survivalSoakTreatsVanillaNightAsUnsafeForLongResourceWork() {
        assertFalse(PlayableSurvivalStage.isNightTime(12_541L));
        assertTrue(PlayableSurvivalStage.isNightTime(12_542L));
        assertTrue(PlayableSurvivalStage.isNightTime(23_999L));
        assertFalse(PlayableSurvivalStage.isNightTime(24_000L));
        assertTrue(PlayableSurvivalStage.isNightTime(36_542L));
    }

    @Test
    void fullLoopRecoversNaturalCombatDeathAndContinuesServerTimeSoak() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.visiblePassiveDuringSoak = true;
        client.visibleHostileDuringSoak = true;
        client.dieOnNextHostileAttack = true;
        client.visibleHostileRepeats = 1;

        ClientScenarioReport report = new PlayableRealClientLoopScenario(Duration.ofMillis(1_050)).run(
            "playable-04-twenty-minute-survival-loop",
            Path.of("build/tmp/playable-04-death-recovery-test/screenshots"),
            client
        );

        assertEquals("passed", report.result(), () -> String.join("\n", report.observations()));
        assertTrue(client.operations.contains("waitDeathScreen"));
        assertTrue(client.operations.contains("respawn"));
        assertTrue(
            client.operations.stream().anyMatch(operation -> operation.startsWith("collectIdentity:minecraft:wooden_pickaxe:")),
            "natural combat death must recover the exact wooden pickaxe death drop"
        );
        assertTrue(
            client.operations.stream().anyMatch(operation -> operation.startsWith("collectIdentity:minecraft:wooden_sword:")),
            "natural combat death must recover the exact wooden sword death drop"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("survival death-drop recovery: passed")
                    && entry.contains("pickaxe_recovered=true")
                    && entry.contains("sword_recovered=true")
            ),
            () -> String.join("\n", report.observations())
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("recovered_deaths=1")),
            () -> String.join("\n", report.observations())
        );
    }

    @Test
    void fullLoopDoesNotFoldDeathDropsIntoThePreDeathIdentityBaseline() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.visiblePassiveDuringSoak = true;
        client.visibleHostileDuringSoak = true;
        client.dieDuringDropBaselineAfterTick = 20L;

        ClientScenarioReport report = new PlayableRealClientLoopScenario(Duration.ofMillis(1_050)).run(
            "playable-04-twenty-minute-survival-loop",
            Path.of("build/tmp/playable-04-between-iterations-death-test/screenshots"),
            client
        );

        assertEquals("passed", report.result(), () -> String.join("\n", report.observations()));
        assertTrue(client.deathMaterializedDuringDropBaseline);
        assertTrue(
            client.operations.stream().anyMatch(operation ->
                operation.startsWith("waitNewDropIdentity:minecraft:wooden_pickaxe:")
                    && !operation.contains("entityId=701")
            ),
            "the exact death drop must not be captured into the pre-death exclusion baseline"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("survival death-drop recovery: passed")),
            () -> String.join("\n", report.observations())
        );
    }

    @Test
    void fullLoopFailsWhenServerTimePacketsStop() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.tickProgressDuringSoak = false;

        ClientScenarioReport report = new PlayableRealClientLoopScenario(Duration.ofMillis(1)).run(
            "playable-04-twenty-minute-survival-loop",
            Path.of("build/tmp/playable-04-server-time-stall-test/screenshots"),
            client
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("server_time_progress=false")),
            () -> String.join("\n", report.observations())
        );
    }

    @Test
    void combatAcceptanceRequiresAKillAndAtMostThreeDeaths() {
        assertFalse(PlayableSurvivalStage.combatAcceptancePassed(0, 0), "no neutralized hostile must fail");
        assertTrue(PlayableSurvivalStage.combatAcceptancePassed(1, 0));
        assertTrue(PlayableSurvivalStage.combatAcceptancePassed(1, 3), "three deaths are the allowed maximum");
        assertFalse(PlayableSurvivalStage.combatAcceptancePassed(5, 4), "a fourth death must fail");
        assertFalse(PlayableSurvivalStage.combatAcceptancePassed(0, 18), "a death loop without kills must fail");
    }

    @Test
    void fullLoopRecordsPerAttackTelemetryIntoTheObservationsAndSummary() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.visiblePassiveDuringSoak = true;
        client.visibleHostileDuringSoak = true;
        client.attackTelemetry.add(new ScenarioCombatRecord("combat attack: tick=10 entity=minecraft:zombie sent=true hit_confirmed=true", true, true));
        client.attackTelemetry.add(new ScenarioCombatRecord("combat attack: tick=22 entity=minecraft:zombie sent=true hit_confirmed=false", true, false));
        client.attackTelemetry.add(new ScenarioCombatRecord("combat engagement: phase=attack attacks_sent=2", false, false));

        ClientScenarioReport report = new PlayableRealClientLoopScenario(Duration.ofMillis(1)).run(
            "playable-04-twenty-minute-survival-loop",
            Path.of("build/tmp/playable-04-telemetry-test/screenshots"),
            client
        );

        assertEquals("passed", report.result(), () -> String.join("\n", report.observations()));
        assertTrue(report.observations().contains("combat attack: tick=10 entity=minecraft:zombie sent=true hit_confirmed=true"));
        assertTrue(report.observations().contains("combat engagement: phase=attack attacks_sent=2"));
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("survival combat acceptance: passed")
                    && entry.contains("hostiles_neutralized=1")
                    && entry.contains("attacks_sent=2")
                    && entry.contains("hits_confirmed=1")
            ),
            () -> String.join("\n", report.observations())
        );
    }

    @Test
    void fullLoopFailsWithoutAHostileKillEvenWhenSpawnEvidenceExists() throws Exception {
        FakeScenarioClient client = new FakeScenarioClient();
        client.visiblePassiveDuringSoak = true;
        client.visibleHostileOutsideReachDuringSoak = true;
        client.clientTicks = 12_522L;

        ClientScenarioReport report = new PlayableRealClientLoopScenario(Duration.ofMillis(1)).run(
            "playable-04-twenty-minute-survival-loop",
            Path.of("build/tmp/playable-04-no-kill-test/screenshots"),
            client
        );

        assertEquals("failed", report.result(), () -> String.join("\n", report.observations()));
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("survival combat acceptance: failed") && entry.contains("hostiles_neutralized=0")
            ),
            () -> String.join("\n", report.observations())
        );
    }
}
