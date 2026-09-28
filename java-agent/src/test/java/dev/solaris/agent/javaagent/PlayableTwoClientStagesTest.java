package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlayableTwoClientStagesTest {
    @Test
    void twoClientSharedLogDropBreakWritesNaturalDropMarkerWithoutDebugSetup() throws Exception {
        Path runDir = Files.createTempDirectory("playable-30-drop");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-30-two-client-shared-log-drop-break",
            screenshotsDir,
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "two-client shared drop break must not use debug setup");
        assertTrue(
            Files.isRegularFile(runDir.resolve("playable-30-shared-log-drop-marker.properties")),
            "primary drop phase must write the shared natural log drop marker"
        );
        assertTrue(
            client.operations.contains("breakVisible:minecraft:oak_log:minecraft:oak_log"),
            "primary drop phase must break a natural generated log through the normal break path"
        );
        assertFalse(
            client.operations.stream().anyMatch(operation -> operation.startsWith("collect:")),
            "primary drop phase must leave the natural log drop visible for the secondary client"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client shared log drop break: passed")),
            "primary drop phase must record shared natural log drop evidence"
        );
    }

    @Test
    void twoClientSharedLogDropObserveSeesPrimaryDropWithoutDebugSetup() throws Exception {
        Path runDir = Files.createTempDirectory("playable-30-observe");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient primary = new FakeScenarioClient();
        ClientScenarioReport before = new PlayableRealClientLoopScenario().run(
            "playable-30-two-client-shared-log-drop-break",
            screenshotsDir,
            primary
        );
        assertEquals("passed", before.result());

        FakeScenarioClient secondary = new FakeScenarioClient();
        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-30-two-client-shared-log-drop-observe",
            screenshotsDir,
            secondary
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + secondary.operations
        );
        assertFalse(secondary.usedDebugSetup(), "secondary drop observe must not use debug setup");
        assertTrue(
            secondary.operations.contains("waitDrop:minecraft:oak_log:playable-two-client-drop-marker"),
            "secondary must wait for the shared natural log item entity at the primary marker"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client shared log drop visibility: passed")),
            "secondary drop observe must record shared drop visibility"
        );
    }

    @Test
    void twoClientSharedLogPickupCollectAndGoneObserveUseSharedMarker() throws Exception {
        Path runDir = Files.createTempDirectory("playable-30-pickup");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient primary = new FakeScenarioClient();
        ClientScenarioReport before = new PlayableRealClientLoopScenario().run(
            "playable-30-two-client-shared-log-drop-break",
            screenshotsDir,
            primary
        );
        assertEquals("passed", before.result());
        primary.operations.clear();

        ClientScenarioReport pickup = new PlayableRealClientLoopScenario().run(
            "playable-30-two-client-shared-log-pickup-collect",
            screenshotsDir,
            primary
        );

        assertEquals(
            "passed",
            pickup.result(),
            () -> String.join("\n", pickup.observations()) + "\noperations=" + primary.operations
        );
        assertTrue(
            primary.operations.contains("collect:minecraft:oak_log:minecraft:oak_log:1"),
            "primary must collect the shared natural log drop through normal pickup"
        );
        assertTrue(
            pickup.observations().stream().anyMatch(entry -> entry.contains("two-client shared log pickup: passed")),
            "primary pickup phase must record pickup convergence"
        );

        FakeScenarioClient secondary = new FakeScenarioClient();
        ClientScenarioReport gone = new PlayableRealClientLoopScenario().run(
            "playable-30-two-client-shared-log-pickup-gone-observe",
            screenshotsDir,
            secondary
        );

        assertEquals(
            "passed",
            gone.result(),
            () -> String.join("\n", gone.observations()) + "\noperations=" + secondary.operations
        );
        assertTrue(
            secondary.operations.contains("waitNoDrop:minecraft:oak_log:playable-two-client-drop-marker"),
            "secondary must observe the shared natural log item entity disappear after primary pickup"
        );
        assertFalse(secondary.usedDebugSetup(), "secondary pickup removal observe must not use debug setup");
        assertTrue(
            gone.observations().stream().anyMatch(entry -> entry.contains("two-client shared log pickup removal: passed")),
            "secondary removal phase must record shared pickup removal"
        );
    }

    @Test
    void twoClientSharedLogDropObserveFailsClosedWithoutMarker() {
        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-30-two-client-shared-log-drop-observe",
            Path.of("build/tmp/playable-30-missing/screenshots"),
            new FakeScenarioClient()
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("missing shared log drop marker")),
            "secondary observe phase must fail closed when marker file is absent"
        );
    }

    @Test
    void twoClientEarnedSharedChestDepositWritesEarnedPlankMarkerWithoutDebugSetup() throws Exception {
        Path runDir = Files.createTempDirectory("playable-31-deposit");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-31-two-client-earned-shared-chest-deposit",
            screenshotsDir,
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "earned shared chest deposit must not use debug setup");
        assertTrue(
            Files.isRegularFile(runDir.resolve("playable-31-shared-chest-marker.properties")),
            "primary deposit phase must write the shared earned chest marker"
        );
        assertTrue(
            client.operations.contains("recipe:7:5:false"),
            "primary deposit phase must craft the shared chest from earned planks"
        );
        assertTrue(
            client.operations.contains("moveToContainer:0:minecraft:oak_planks:1"),
            "primary deposit phase must deposit leftover earned planks from the chest-crafting route"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client shared chest deposit: passed")),
            "primary deposit phase must record shared chest deposit evidence"
        );
    }

    @Test
    void twoClientEarnedSharedChestWithdrawTakesPrimaryItemWithoutDebugSetup() throws Exception {
        Path runDir = Files.createTempDirectory("playable-31-withdraw");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient primary = new FakeScenarioClient();
        ClientScenarioReport deposit = new PlayableRealClientLoopScenario().run(
            "playable-31-two-client-earned-shared-chest-deposit",
            screenshotsDir,
            primary
        );
        assertEquals("passed", deposit.result());

        FakeScenarioClient secondary = new FakeScenarioClient();
        secondary.containerItemIds[0] = "minecraft:oak_planks";
        secondary.containerCounts[0] = 1;
        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-31-two-client-earned-shared-chest-withdraw",
            screenshotsDir,
            secondary
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + secondary.operations
        );
        assertFalse(secondary.usedDebugSetup(), "secondary shared chest withdraw must not use debug setup");
        assertTrue(
            secondary.operations.contains("waitBlock:playable-two-client-chest-marker:minecraft:chest"),
            "secondary must wait for the primary placed chest block"
        );
        assertTrue(
            secondary.operations.contains("approach:minecraft:chest:playable-two-client-chest-marker"),
            "secondary must walk to the primary placed chest before opening it"
        );
        assertTrue(
            secondary.operations.contains("waitContainer:0:minecraft:oak_planks:1"),
            "secondary must observe the primary deposited item"
        );
        assertTrue(
            secondary.operations.contains("moveFromContainer:0:minecraft:oak_planks:1"),
            "secondary must withdraw the shared item through the container quick-move path"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client shared chest withdraw: passed")),
            "secondary withdraw phase must record shared chest transfer evidence"
        );
    }

    @Test
    void twoClientEarnedSharedChestObserveEmptyFailsClosedWithoutMarker() {
        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-31-two-client-earned-shared-chest-observe-empty",
            Path.of("build/tmp/playable-31-missing/screenshots"),
            new FakeScenarioClient()
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("missing shared chest marker")),
            "primary observe-empty phase must fail closed when marker file is absent"
        );
    }

    @Test
    void twoClientEarnedSharedChestObserveEmptyUsesMarkerAfterSecondaryWithdraw() throws Exception {
        Path runDir = Files.createTempDirectory("playable-31-empty");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient primary = new FakeScenarioClient();
        ClientScenarioReport deposit = new PlayableRealClientLoopScenario().run(
            "playable-31-two-client-earned-shared-chest-deposit",
            screenshotsDir,
            primary
        );
        assertEquals("passed", deposit.result());

        primary.containerItemIds[0] = null;
        primary.containerCounts[0] = 0;
        primary.operations.clear();
        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-31-two-client-earned-shared-chest-observe-empty",
            screenshotsDir,
            primary
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + primary.operations
        );
        assertTrue(
            primary.operations.contains("waitContainerEmpty:0"),
            "primary must observe the shared chest slot empty after secondary withdraw"
        );
        assertTrue(
            primary.operations.contains("approach:minecraft:chest:playable-two-client-chest-marker"),
            "primary empty-observe phase must walk to the shared chest marker before opening it"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client shared chest empty observe: passed")),
            "primary empty-observe phase must record shared chest removal evidence"
        );
    }

    @Test
    void twoClientEarnedTorchPlaceWritesBlockMarkerWithoutDebugSetup() throws Exception {
        Path runDir = Files.createTempDirectory("playable-32-place");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-32-two-client-earned-torch-place",
            screenshotsDir,
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "earned shared torch placement must not use debug setup");
        assertTrue(
            Files.isRegularFile(runDir.resolve("playable-32-shared-block-edit-marker.properties")),
            "primary place phase must write the shared block edit marker"
        );
        assertTrue(
            client.operations.contains("recipe:0:27:false"),
            "primary place phase must craft torches through the earned charcoal route"
        );
        assertTrue(
            client.operations.contains("selectHotbar:minecraft:torch:4"),
            "primary place phase must select the earned torches from hotbar"
        );
        assertTrue(
            client.operations.contains("use:minecraft:torch:torch-clicked"),
            "primary place phase must place the earned torch item"
        );
        assertTrue(
            client.operations.contains("waitBlock:torch-target:minecraft:torch"),
            "primary place phase must wait for the placed torch block"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client shared torch placement: passed")),
            "primary place phase must record shared torch placement evidence"
        );
    }

    @Test
    void twoClientEarnedTorchObserveSeesPrimaryPlacedBlockWithoutDebugSetup() throws Exception {
        Path runDir = Files.createTempDirectory("playable-32-observe");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient primary = new FakeScenarioClient();
        ClientScenarioReport place = new PlayableRealClientLoopScenario().run(
            "playable-32-two-client-earned-torch-place",
            screenshotsDir,
            primary
        );
        assertEquals("passed", place.result());

        FakeScenarioClient secondary = new FakeScenarioClient();
        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-32-two-client-earned-torch-observe",
            screenshotsDir,
            secondary
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + secondary.operations
        );
        assertFalse(secondary.usedDebugSetup(), "secondary shared torch observe must not use debug setup");
        assertTrue(
            secondary.operations.contains("approach:minecraft:dirt:playable-two-client-block-approach"),
            "secondary must walk to the solid support block for the primary placed torch before observing it"
        );
        assertTrue(
            secondary.operations.contains("waitBlock:playable-two-client-block-marker:minecraft:torch"),
            "secondary must wait for the primary placed torch block"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client shared torch visibility: passed")),
            "secondary observe phase must record shared torch block visibility"
        );
    }

    @Test
    void twoClientEarnedTorchBreakAndGoneObserveUseSharedMarker() throws Exception {
        Path runDir = Files.createTempDirectory("playable-32-break");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient primary = new FakeScenarioClient();
        ClientScenarioReport place = new PlayableRealClientLoopScenario().run(
            "playable-32-two-client-earned-torch-place",
            screenshotsDir,
            primary
        );
        assertEquals("passed", place.result());
        primary.operations.clear();
        primary.failedTorchPickupsRemaining = 1;

        ClientScenarioReport broke = new PlayableRealClientLoopScenario().run(
            "playable-32-two-client-earned-torch-break",
            screenshotsDir,
            primary
        );

        assertEquals(
            "passed",
            broke.result(),
            () -> String.join("\n", broke.observations()) + "\noperations=" + primary.operations
        );
        assertTrue(
            primary.operations.contains("approach:minecraft:dirt:playable-two-client-block-approach"),
            "primary break phase must walk back to the solid support block for the shared torch marker"
        );
        assertTrue(
            primary.operations.contains("breakVisible:minecraft:torch:minecraft:torch"),
            "primary break phase must break the placed torch through the normal block edit path"
        );
        assertTrue(
            primary.operations.contains("collect:minecraft:torch:minecraft:torch:1"),
            "primary break phase should attempt to collect the torch drop"
        );
        assertTrue(
            primary.operations.contains("waitBlock:playable-two-client-block-marker:minecraft:air"),
            "primary break phase must confirm the torch block became air"
        );
        assertTrue(
            broke.observations().stream().anyMatch(entry -> entry.contains("two-client shared torch break: passed")),
            "primary break phase must record shared torch break evidence"
        );
        assertTrue(
            broke.observations().stream().anyMatch(entry -> entry.contains("collected_by_breaker=false")),
            "a peer pickup must not invalidate otherwise complete shared block-edit evidence"
        );

        FakeScenarioClient secondary = new FakeScenarioClient();
        ClientScenarioReport gone = new PlayableRealClientLoopScenario().run(
            "playable-32-two-client-earned-torch-gone-observe",
            screenshotsDir,
            secondary
        );

        assertEquals(
            "passed",
            gone.result(),
            () -> String.join("\n", gone.observations()) + "\noperations=" + secondary.operations
        );
        assertTrue(
            secondary.operations.contains("approach:minecraft:dirt:playable-two-client-block-approach"),
            "secondary gone-observe phase must walk to the solid support block for the shared torch marker"
        );
        assertTrue(
            secondary.operations.contains("waitBlock:playable-two-client-block-marker:minecraft:air"),
            "secondary must observe the placed torch block disappear"
        );
        assertFalse(secondary.usedDebugSetup(), "secondary block removal observe must not use debug setup");
        assertTrue(
            gone.observations().stream().anyMatch(entry -> entry.contains("two-client shared torch removal visibility: passed")),
            "secondary gone-observe phase must record shared torch removal visibility"
        );
    }

    @Test
    void twoClientEarnedTorchObserveFailsClosedWithoutMarker() {
        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-32-two-client-earned-torch-observe",
            Path.of("build/tmp/playable-32-missing/screenshots"),
            new FakeScenarioClient()
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("missing shared block edit marker")),
            "secondary observe phase must fail closed when marker file is absent"
        );
    }

    @Test
    void twoClientPlayerObserveWritesVisibilityMarker() throws Exception {
        Path runDir = Files.createTempDirectory("playable-33-observe");
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-33-two-client-player-observe",
            runDir.resolve("screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            Files.isRegularFile(runDir.resolve("playable-33-player-visibility-marker.properties")),
            "secondary observe phase must write the shared player visibility marker"
        );
        assertTrue(
            client.operations.contains("waitPlayer:SolarisPrimary"),
            "secondary observe phase must wait for the primary player through client-visible entities"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client player visibility: passed")),
            "secondary observe phase must record player visibility evidence"
        );
    }

    @Test
    void twoClientPlayerMovedObserveUsesVisibilityMarker() throws Exception {
        Path runDir = Files.createTempDirectory("playable-33-moved");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient client = new FakeScenarioClient();
        ClientScenarioReport observe = new PlayableRealClientLoopScenario().run(
            "playable-33-two-client-player-observe",
            screenshotsDir,
            client
        );
        assertEquals("passed", observe.result());
        client.operations.clear();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-33-two-client-player-moved-observe",
            screenshotsDir,
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            client.operations.contains("waitPlayerMoved:SolarisPrimary:0.05"),
            "secondary moved-observe phase must wait for a visible movement delta from the primary player"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client player movement visibility: passed")),
            "secondary moved-observe phase must record player movement visibility evidence"
        );
    }

    @Test
    void twoClientPlayerMovedObserveFailsClosedWithoutMarker() {
        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-33-two-client-player-moved-observe",
            Path.of("build/tmp/playable-33-missing/screenshots"),
            new FakeScenarioClient()
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("missing player visibility marker")),
            "moved-observe phase must fail closed when marker file is absent"
        );
    }

    @Test
    void twoClientChatSendUsesNormalChatMessageWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-34-two-client-chat-send",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            client.operations.contains("sendChat:p34 hello from primary"),
            "primary send phase must use normal chat, not slash commands"
        );
        assertFalse(client.usedDebugSetup(), "chat send phase must not use debug command setup");
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client chat send: passed")),
            "primary send phase must record chat send evidence"
        );
    }

    @Test
    void twoClientChatObserveSeesPrimaryMessageWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-34-two-client-chat-observe",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            client.operations.contains("waitChat:<SolarisPrimary> p34 hello from primary"),
            "secondary observe phase must wait for the rendered primary chat line"
        );
        assertFalse(client.usedDebugSetup(), "chat observe phase must not use debug command setup");
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client chat observe: passed")),
            "secondary observe phase must record visible chat evidence"
        );
    }

    @Test
    void twoClientPlayerDisconnectVisiblePhaseRecordsPrimaryBeforeDisconnect() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-35-two-client-player-disconnect-visible",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            client.operations.contains("waitPlayer:SolarisPrimary"),
            "secondary visible phase must prove the primary player was visible before disconnect"
        );
        assertFalse(client.usedDebugSetup(), "player disconnect visible phase must not use debug setup");
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client player pre-disconnect visibility: passed")),
            "visible phase must record pre-disconnect player visibility evidence"
        );
    }

    @Test
    void twoClientPlayerDisconnectGoneObserveWaitsForPrimaryRemoval() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-35-two-client-player-gone-observe",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            client.operations.contains("waitNoPlayer:SolarisPrimary"),
            "secondary gone-observe phase must wait until the primary player entity disappears"
        );
        assertFalse(client.usedDebugSetup(), "player disconnect gone-observe phase must not use debug setup");
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client player disconnect removal: passed")),
            "gone-observe phase must record player removal evidence"
        );
    }

    @Test
    void twoClientPlayerReconnectVisiblePhaseWritesBaselineMarker() throws Exception {
        Path runDir = Files.createTempDirectory("playable-36-visible");
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-36-two-client-player-reconnect-visible",
            runDir.resolve("screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            Files.isRegularFile(runDir.resolve("playable-33-player-visibility-marker.properties")),
            "secondary reconnect-visible phase must write the baseline player visibility marker"
        );
        assertTrue(
            client.operations.contains("waitPlayer:SolarisPrimary"),
            "secondary reconnect-visible phase must prove the primary player was visible before reconnect"
        );
        assertFalse(client.usedDebugSetup(), "player reconnect visible phase must not use debug setup");
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client player pre-reconnect visibility: passed")),
            "visible phase must record pre-reconnect player visibility evidence"
        );
    }

    @Test
    void twoClientPlayerReconnectedObserveRequiresNewVisibleEntity() throws Exception {
        Path runDir = Files.createTempDirectory("playable-36-reconnected");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient client = new FakeScenarioClient();
        client.visiblePlayerEntityId = 777;
        ClientScenarioReport visible = new PlayableRealClientLoopScenario().run(
            "playable-36-two-client-player-reconnect-visible",
            screenshotsDir,
            client
        );
        assertEquals("passed", visible.result());
        client.operations.clear();
        client.visiblePlayerEntityId = 778;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-36-two-client-player-reconnected-observe",
            screenshotsDir,
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            client.operations.contains("waitPlayer:SolarisPrimary"),
            "secondary reconnected-observe phase must wait for the reconnected primary player"
        );
        assertFalse(client.usedDebugSetup(), "player reconnect observe phase must not use debug setup");
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("two-client player reconnect visibility: passed")
                    && entry.contains("old_entity_id=777")
                    && entry.contains("new_entity_id=778")
            ),
            "reconnected-observe phase must record old and new primary player entity ids"
        );
    }

    @Test
    void twoClientPlayerReconnectGoneObserveWaitsForPrimaryRemoval() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-36-two-client-player-reconnect-gone-observe",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            client.operations.contains("waitNoPlayer:SolarisPrimary"),
            "secondary reconnect gone-observe phase must wait until the old primary player entity disappears"
        );
        assertFalse(client.usedDebugSetup(), "player reconnect gone-observe phase must not use debug setup");
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client player reconnect removal: passed")),
            "gone-observe phase must record reconnect removal evidence"
        );
    }

    @Test
    void twoClientPlayerReconnectedObserveFailsClosedWithoutMarker() {
        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-36-two-client-player-reconnected-observe",
            Path.of("build/tmp/playable-36-missing/screenshots"),
            new FakeScenarioClient()
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("missing player visibility marker")),
            "reconnected-observe phase must fail closed when the baseline marker is absent"
        );
    }

    @Test
    void twoClientPlayerDeathBaselineWritesVisibilityMarker() throws Exception {
        Path runDir = Files.createTempDirectory("playable-37-baseline");
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-37-two-client-player-death-baseline",
            runDir.resolve("screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            Files.isRegularFile(runDir.resolve("playable-33-player-visibility-marker.properties")),
            "secondary death baseline phase must write the player visibility marker"
        );
        assertTrue(
            client.operations.contains("waitPlayer:SolarisPrimary"),
            "secondary death baseline phase must prove the primary player was visible before death"
        );
        assertFalse(client.usedDebugSetup(), "player death baseline phase must not use debug setup");
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client player pre-death visibility: passed")),
            "death baseline phase must record pre-death player visibility evidence"
        );
    }

    @Test
    void twoClientCampfireDeathRespawnUsesNaturalDeathPathWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.logItemId = "minecraft:birch_log";
        client.planksItemId = "minecraft:birch_planks";

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-37-two-client-campfire-death-respawn",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            client.operations.contains("standOnBlockUntilDeath:campfire-target:minecraft:campfire"),
            "primary death phase must use natural campfire contact damage"
        );
        assertTrue(
            client.operations.contains("respawn"),
            "primary death phase must perform the vanilla respawn packet"
        );
        assertFalse(
            client.operations.stream().anyMatch(operation ->
                operation.startsWith("findBreakable:minecraft:campfire|minecraft:soul_campfire")
            ),
            "primary death phase must reuse the campfire placement target instead of rediscovering it as a solid block"
        );
        assertFalse(client.usedDebugSetup(), "primary death/respawn phase must not use debug setup");
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client campfire death/respawn: passed")),
            "primary death phase must record death/respawn evidence for the two-client scenario"
        );
    }

    @Test
    void twoClientPlayerPostRespawnMovedObserveUsesBaselineMarker() throws Exception {
        Path runDir = Files.createTempDirectory("playable-37-post-respawn");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient client = new FakeScenarioClient();
        ClientScenarioReport baseline = new PlayableRealClientLoopScenario().run(
            "playable-37-two-client-player-death-baseline",
            screenshotsDir,
            client
        );
        assertEquals("passed", baseline.result());
        client.operations.clear();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-37-two-client-player-post-respawn-moved-observe",
            screenshotsDir,
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            client.operations.contains("waitPlayerMoved:SolarisPrimary:0.05"),
            "secondary post-respawn phase must wait for visible movement from the primary player"
        );
        assertFalse(client.usedDebugSetup(), "post-respawn moved-observe phase must not use debug setup");
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client player post-respawn movement visibility: passed")),
            "post-respawn moved-observe phase must record visible movement evidence"
        );
    }

    @Test
    void twoClientPlayerPostRespawnMovedObserveFailsClosedWithoutMarker() {
        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-37-two-client-player-post-respawn-moved-observe",
            Path.of("build/tmp/playable-37-missing/screenshots"),
            new FakeScenarioClient()
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("missing player visibility marker")),
            "post-respawn moved-observe phase must fail closed when the baseline marker is absent"
        );
    }

    @Test
    void twoClientInventoryDropPrimaryDropsEarnedNaturalLogAndWritesMarker() throws Exception {
        Path runDir = Files.createTempDirectory("playable-38-drop-primary");
        FakeScenarioClient client = new FakeScenarioClient();
        client.logItemId = "minecraft:birch_log";
        client.planksItemId = "minecraft:birch_planks";

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-38-two-client-inventory-drop-primary",
            runDir.resolve("screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            Files.isRegularFile(runDir.resolve("playable-38-inventory-drop-marker.properties")),
            "primary inventory-drop phase must persist the dropped item marker for the observer client"
        );
        assertTrue(
            client.operations.contains("dropSelected:minecraft:birch_log:1"),
            "primary inventory-drop phase must drop the earned log through the selected-item drop primitive"
        );
        assertFalse(client.usedDebugSetup(), "inventory drop handoff primary phase must not use debug setup");
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("two-client inventory drop: passed")),
            "primary inventory-drop phase must record selected-item drop evidence"
        );
    }

    @Test
    void twoClientInventoryDropObserveUsesMarker() throws Exception {
        Path runDir = Files.createTempDirectory("playable-38-drop-observe");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient client = new FakeScenarioClient();
        client.logItemId = "minecraft:birch_log";
        client.planksItemId = "minecraft:birch_planks";
        ClientScenarioReport primary = new PlayableRealClientLoopScenario().run(
            "playable-38-two-client-inventory-drop-primary",
            screenshotsDir,
            client
        );
        assertEquals("passed", primary.result());
        client.operations.clear();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-38-two-client-inventory-drop-observe",
            screenshotsDir,
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            client.operations.contains("waitDrop:minecraft:birch_log:playable-two-client-inventory-drop-marker"),
            "secondary observe phase must wait for the primary client's dropped item entity"
        );
        assertFalse(client.usedDebugSetup(), "inventory drop observe phase must not use debug setup");
    }

    @Test
    void twoClientInventoryDropSecondaryPickupUsesMarker() throws Exception {
        Path runDir = Files.createTempDirectory("playable-38-drop-pickup");
        Path screenshotsDir = runDir.resolve("screenshots");
        FakeScenarioClient client = new FakeScenarioClient();
        client.logItemId = "minecraft:birch_log";
        client.planksItemId = "minecraft:birch_planks";
        ClientScenarioReport primary = new PlayableRealClientLoopScenario().run(
            "playable-38-two-client-inventory-drop-primary",
            screenshotsDir,
            client
        );
        assertEquals("passed", primary.result());
        client.operations.clear();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-38-two-client-inventory-drop-secondary-pickup",
            screenshotsDir,
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertTrue(
            client.operations.contains("collect:minecraft:birch_log:minecraft:birch_log:1"),
            "secondary pickup phase must collect the primary client's dropped item"
        );
        assertFalse(client.usedDebugSetup(), "inventory drop pickup phase must not use debug setup");
    }

    @Test
    void twoClientInventoryDropGoneObserveFailsClosedWithoutMarker() {
        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-38-two-client-inventory-drop-gone-observe",
            Path.of("build/tmp/playable-38-missing/screenshots"),
            new FakeScenarioClient()
        );

        assertEquals("failed", report.result());
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("missing inventory drop marker")),
            "inventory drop gone-observe phase must fail closed when the marker is absent"
        );
    }

}
