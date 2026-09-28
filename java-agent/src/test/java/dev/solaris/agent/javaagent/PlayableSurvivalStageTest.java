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

        assertEquals("passed", report.result(), () -> String.join("\n", report.observations()));
        assertTrue(
            client.operations.contains("visibleEntity:minecraft:zombie|minecraft:skeleton|minecraft:spider:outside-survival-reach"),
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

        assertEquals("passed", report.result(), () -> String.join("\n", report.observations()));
        assertFalse(
            client.operations.stream().anyMatch(operation -> operation.startsWith("attackEntityUntilRemoved:")),
            "an injured survival player must not charge a hostile outside melee reach"
        );
    }

    @Test
    void fullLoopDefersHostileAlreadyWithinReachWhenBadlyInjured() throws Exception {
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
            client.operations.stream().anyMatch(operation -> operation.startsWith("attackEntityUntilRemoved:")),
            "a badly injured survival player must not start a fresh melee exchange"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("within_reach=true") && entry.contains("engagement=deferred_low_health")
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

}
