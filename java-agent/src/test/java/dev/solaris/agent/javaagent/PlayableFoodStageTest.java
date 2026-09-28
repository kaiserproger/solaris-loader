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

final class PlayableFoodStageTest {
    @Test
    void supportsPassiveLivestockMotionScenario() {
        assertTrue(
            PlayableRealClientLoopScenario.supports("playable-44-passive-livestock-motion"),
            "playable client must expose the reusable livestock movement regression"
        );
    }
    @Test
    void passiveLivestockMotionChecksAllSpeciesWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-44-passive-livestock-motion",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "livestock movement must use natural spawned entities");
        assertFalse(
            client.operations.stream().anyMatch(operation -> operation.startsWith("approachEntity:")),
            "livestock observation must not move the player across unrelated chunks"
        );
        for (String entityTypeId : List.of("minecraft:cow", "minecraft:sheep", "minecraft:chicken")) {
            assertTrue(
                client.operations.stream().anyMatch(operation -> operation.startsWith("motion:" + entityTypeId)),
                "scenario must observe packet-driven motion for " + entityTypeId
            );
            assertTrue(
                report.observations().stream().anyMatch(
                    observation -> observation.contains("livestock motion: passed entity=" + entityTypeId)
                ),
                "scenario must record movement evidence for " + entityTypeId
            );
        }
        assertTrue(
            report.observations().stream().anyMatch(
                observation -> observation.contains("entity=minecraft:cow")
                    && observation.contains("vertical_rise=1.1")
            ),
            "cow observation must prove a vanilla-height step climb"
        );
    }
    @Test
    void renewableWheatBreadCompletesWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-43-renewable-wheat-bread",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "renewable food progression must not use debug setup");
        assertTrue(client.operations.contains("recipe:7:30:false"), "scenario must craft an earned wooden hoe");
        assertEquals(
            3,
            client.operations.stream().filter(operation -> operation.startsWith("findTillable:")).count()
        );
        assertEquals(
            3,
            client.operations.stream().filter(operation -> operation.endsWith(":age:7")).count(),
            "scenario must observe three mature crops"
        );
        assertEquals(
            3,
            report.observations().stream()
                .filter(entry -> entry.contains("wheat client light plot=") && entry.contains("sky=15"))
                .count(),
            "scenario must prove mature wheat has client-visible growth light"
        );
        assertTrue(client.operations.contains("recipe:7:60:false"), "scenario must craft bread from earned wheat");
        assertFalse(
            client.operations.contains("drainHungerBySprinting"),
            "crop progression must not make the visible client perform synthetic hunger-drain movement"
        );
        assertFalse(
            client.operations.stream().anyMatch(operation -> operation.startsWith("eatSelectedFood:minecraft:bread:")),
            "food consumption remains covered by the dedicated eating scenario"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("renewable bread ready: passed")),
            "scenario must record the earned bread left ready for normal play"
        );
    }
    @Test
    void renewableWheatAcceptsVanillaGrowthLightBelowOpenSkyMaximum() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.cropSkyLight = 14;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-43-renewable-wheat-bread",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result(), () -> String.join("\n", report.observations()));
        assertEquals(
            3,
            report.observations().stream()
                .filter(entry -> entry.contains("wheat client light plot=") && entry.contains("sky=14"))
                .count()
        );
    }
    @Test
    void passiveFoodDropKillsNaturalAnimalAndCollectsFoodWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-10-passive-food-drop",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertFalse(client.usedDebugSetup(), "passive food path must not use debug setup");
        assertTrue(
            client.operations.contains("findEntity:minecraft:cow|minecraft:pig|minecraft:chicken:outside-survival-reach"),
            "passive food path must scan for a naturally loaded passive mob"
        );
        assertTrue(
            client.operations.contains("approachEntity:minecraft:cow:loaded-passive"),
            "passive food path must approach the natural mob through movement"
        );
        assertTrue(
            client.operations.contains("attackEntityDrop:minecraft:cow:entity_id=42:minecraft:beef:1"),
            "passive food path must attack the natural mob until its food drop is collected"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("passive food drop: passed")),
            "passive food path must record natural mob kill/drop/pickup evidence"
        );
    }
    @Test
    void eatPassiveFoodUsesEarnedDropAfterNaturalHungerDrainWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-11-eat-passive-food",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertFalse(client.usedDebugSetup(), "passive food eating path must not use debug setup");
        assertTrue(
            client.operations.contains("drainHungerBySprinting"),
            "passive food eating path must create a natural hunger deficit through movement"
        );
        assertTrue(
            client.operations.contains("selectHotbar:minecraft:beef:1"),
            "passive food eating path must select the earned food drop"
        );
        assertTrue(
            client.operations.contains("eatSelectedFood:minecraft:beef:1"),
            "passive food eating path must hold use until the earned food is consumed"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("passive food eating: passed")),
            "passive food eating path must record consumed earned food evidence"
        );
    }
    @Test
    void cookedPassiveFoodSmeltsRawDropAndEatsCookedMealWithoutDebugSetup() {
        FakeScenarioClient client = new FakeScenarioClient();

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-15-cooked-passive-food",
            Path.of("run/screenshots"),
            client
        );

        assertEquals("passed", report.result());
        assertFalse(client.usedDebugSetup(), "cooked passive food path must not use debug setup");
        assertTrue(
            client.operations.contains("waitCount:minecraft:charcoal:1"),
            "cooked passive food path must prepare earned charcoal fuel"
        );
        assertTrue(
            client.operations.indexOf("attackEntityDrop:minecraft:cow:entity_id=42:minecraft:beef:1")
                < client.operations.indexOf("waitCount:minecraft:charcoal:1"),
            "cooked passive food path must collect raw food before the long furnace/stone route moves away from animals"
        );
        assertTrue(
            client.operations.indexOf("waitCount:minecraft:wooden_pickaxe:1")
                < client.operations.indexOf("attackEntityDrop:minecraft:cow:entity_id=42:minecraft:beef:1"),
            "cooked passive food path must finish the initial wood/tool route before chasing animals"
        );
        assertTrue(
            client.operations.indexOf("attackEntityDrop:minecraft:cow:entity_id=42:minecraft:beef:1")
                < client.operations.indexOf("breakVisible:minecraft:stone:minecraft:cobblestone"),
            "cooked passive food path must collect raw food before stone mining moves away from nearby animals"
        );
        assertTrue(
            client.operations.contains("attackEntityDrop:minecraft:cow:entity_id=42:minecraft:beef:1"),
            "cooked passive food path must collect a raw passive food drop"
        );
        assertTrue(
            client.operations.contains("moveToContainer:0:minecraft:beef:1"),
            "cooked passive food path must move raw food into furnace input slot 0"
        );
        assertTrue(
            client.operations.contains("moveToContainer:1:minecraft:charcoal:1"),
            "cooked passive food path must move earned charcoal into furnace fuel slot 1"
        );
        assertTrue(
            client.operations.contains("waitContainer:2:minecraft:cooked_beef:1"),
            "cooked passive food path must wait for cooked food in furnace output slot 2"
        );
        assertTrue(
            client.operations.contains("moveFromContainer:2:minecraft:cooked_beef:1"),
            "cooked passive food path must move cooked food into inventory"
        );
        assertTrue(
            client.operations.contains("selectHotbar:minecraft:cooked_beef:1"),
            "cooked passive food path must select the cooked food from hotbar"
        );
        assertTrue(
            client.operations.contains("eatSelectedFood:minecraft:cooked_beef:1"),
            "cooked passive food path must eat the cooked food after natural hunger drain"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("cooked passive food eating: passed")),
            "cooked passive food path must record consumed cooked food evidence"
        );
    }
    @Test
    void cookedPassiveFoodCarriesSpareTableWhenOriginalTableIsUnreachableAfterStoneMining() {
        FakeScenarioClient client = new FakeScenarioClient();
        client.failCraftingTableApproachForFurnace = true;

        ClientScenarioReport report = new PlayableRealClientLoopScenario().run(
            "playable-15-cooked-passive-food",
            Path.of("run/screenshots"),
            client
        );

        assertEquals(
            "passed",
            report.result(),
            () -> String.join("\n", report.observations()) + "\noperations=" + client.operations
        );
        assertFalse(client.usedDebugSetup(), "spare crafting table fallback must not use debug setup");
        assertTrue(
            client.operations.stream().filter("recipe:0:10:false"::equals).count() >= 2,
            "fallback must craft a spare crafting table from carried planks"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry ->
                entry.contains("crafting table approach for furnace: failed")
            ),
            "scenario must record the failed old-table approach before using the spare table"
        );
        assertTrue(
            report.observations().stream().anyMatch(entry -> entry.contains("furnace recipe: passed")),
            "spare table fallback must still craft the furnace"
        );
    }
}
