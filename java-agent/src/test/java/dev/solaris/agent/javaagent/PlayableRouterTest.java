package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlayableRouterTest {
    @Test
    void blocksUnknownScenarioIds() {
        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-unknown",
            Path.of("run/screenshots"),
            new FakeScenarioClient()
        );

        assertEquals("blocked", report.result());
        assertTrue(report.observations().get(0).contains("unsupported scenario"));
    }
}
