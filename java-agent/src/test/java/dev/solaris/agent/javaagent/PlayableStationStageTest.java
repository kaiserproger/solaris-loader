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

final class PlayableStationStageTest {
    @Test
    void stonecutterConservesNormalPickupAndQuickMoveAcrossReopen() {
        assertStonecutterFakeScreenAndContainerFollowUseCloseAndReopenActions();
        FakeScenarioClient client = new FakeScenarioClient();
        client.stonecutterSlabOfferId = 2;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-47-stonecutter-conservation",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertEquals(0, client.cobblestones, "all three stonecutter inputs must be consumed exactly once");
        assertEquals(6, client.cobblestoneSlabs, "one normal pickup plus max quick-move must produce six slabs");
        assertEquals(
            2,
            client.operations.stream()
                .filter("screen:net.minecraft.client.gui.screens.inventory.StonecutterScreen"::equals)
                .count(),
            "the menu must be observed through client state before and after close"
        );
        int normalPickup = client.operations.indexOf("moveFromContainer:1:minecraft:cobblestone_slab:2");
        int close = client.operations.indexOf("closeScreen");
        int reopen = client.operations.lastIndexOf(
            "screen:net.minecraft.client.gui.screens.inventory.StonecutterScreen"
        );
        int quickMove = client.operations.indexOf("quickMoveContainer:1");
        assertTrue(
            normalPickup >= 0 && normalPickup < close && close < reopen && reopen < quickMove,
            "normal pickup must precede the close/reopen boundary and quick-move must follow it"
        );
        for (int offerId = 0; offerId <= client.stonecutterSlabOfferId; offerId++) {
            String operation = "containerButton:" + offerId;
            assertEquals(
                2,
                client.operations.stream().filter(operation::equals).count(),
                "the matching offer must be discovered from observed output after each menu open"
            );
        }
        assertFalse(
            client.operations.stream().anyMatch(operation -> operation.startsWith("wait_ticks:")),
            "stonecutter synchronization must remain event-driven"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("stonecutter normal pickup: passed"))
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("stonecutter conservation: passed"))
        );

        FakeScenarioClient staleReopen = new FakeScenarioClient();
        staleReopen.stonecutterSlabOfferId = 2;
        staleReopen.reuseStonecutterContainerIdOnReopen = true;
        ClientScenarioReport staleReport = new PlayableRealClientLoopScenario().run(
            "playable-47-stonecutter-conservation",
            Path.of("run/screenshots"),
            staleReopen
        );
        assertEquals("failed", staleReport.result(), "same-ID reopen must not prove a new menu lifecycle");
        assertTrue(
            staleReport.observations().stream().anyMatch(entry ->
                entry.contains("stonecutter close/reopen conservation: failed")
            )
        );
    }

    private static void assertStonecutterFakeScreenAndContainerFollowUseCloseAndReopenActions() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.stonecutterSlabOfferId = 2;
        String screen = "net.minecraft.client.gui.screens.inventory.StonecutterScreen";
        ScenarioBlockPair pair = client.findUnobstructedPlaceablePair(ScenarioReach.WITHIN_SURVIVAL_REACH);

        assertFalse(client.waitForScreenClassName(screen, Duration.ZERO));
        client.useItemOn(pair.clicked(), new ScenarioHeldItem("minecraft:stonecutter", 1));
        assertFalse(client.waitForScreenClassName(screen, Duration.ZERO), "placing the block must not open its menu");

        ScenarioBlockTarget stonecutter = new ScenarioBlockTarget(
            pair.target().x(),
            pair.target().y(),
            pair.target().z(),
            "up",
            "stonecutter-target",
            "minecraft:stonecutter"
        );
        client.useItemOn(stonecutter, new ScenarioHeldItem("minecraft:cobblestone", 1));
        assertTrue(client.waitForScreenClassName(screen, Duration.ZERO));
        int firstContainerId = client.activeContainerId();
        client.giveAndSelect("minecraft:cobblestone", 1, 1, Duration.ZERO);
        assertTrue(client.moveSelectedItemToContainerSlot(0, "minecraft:cobblestone", 1, Duration.ZERO));
        assertFalse(
            client.waitForContainerSlot(1, "minecraft:cobblestone_slab", 2, Duration.ZERO),
            "waiting must not materialize stonecutter output before an offer action"
        );
        assertTrue(client.clickContainerButton(0, Duration.ZERO));
        assertFalse(client.waitForContainerSlot(1, "minecraft:cobblestone_slab", 2, Duration.ZERO));
        assertTrue(client.clickContainerButton(2, Duration.ZERO));
        assertTrue(client.waitForContainerSlot(1, "minecraft:cobblestone_slab", 2, Duration.ZERO));

        assertTrue(client.closeCurrentScreen(Duration.ZERO));
        assertFalse(client.waitForScreenClassName(screen, Duration.ZERO));

        client.useItemOn(stonecutter, new ScenarioHeldItem("minecraft:air", 0));
        assertTrue(client.waitForScreenClassName(screen, Duration.ZERO));
        assertTrue(client.activeContainerId() > firstContainerId, "reopen must create a new container state");
    }
}
