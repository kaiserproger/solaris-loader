package dev.solaris.agent.javaagent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VanillaUiSmokeScenarioTest {
    @Test
    void registersTheTargetedUiAndWaveOneSmokeIds() {
        assertTrue(VanillaUiSmokeScenario.supports("playable-ui-commands"));
        assertTrue(VanillaUiSmokeScenario.supports("playable-ui-teams-scoreboard"));
        assertTrue(VanillaUiSmokeScenario.supports("playable-ui-effects"));
        assertTrue(VanillaUiSmokeScenario.supports("playable-ui-enchant-anvil-grindstone"));
        assertTrue(VanillaUiSmokeScenario.supports("playable-ui-adversarial"));
        assertTrue(VanillaUiSmokeScenario.supports("playable-w1-villager-trade"));
        assertTrue(VanillaUiSmokeScenario.supports("playable-w1-redstone"));
        assertTrue(VanillaUiSmokeScenario.supports("playable-w1-brewing"));
        assertTrue(VanillaUiSmokeScenario.supports("playable-w1-ranged"));
        assertTrue(VanillaUiSmokeScenario.supports("playable-w1-fishing"));
        assertTrue(VanillaUiSmokeScenario.supports("playable-w1-tnt-fire-light"));
        assertTrue(VanillaUiSmokeScenario.supports("playable-w1-stations"));
        assertFalse(VanillaUiSmokeScenario.supports("playable-w1"));
        assertFalse(VanillaUiSmokeScenario.supports("playable-w1-unknown"));
        assertFalse(VanillaUiSmokeScenario.supports("playable-ui"));
        assertFalse(VanillaUiSmokeScenario.supports("playable-01-join-generated-spawn"));
        assertFalse(VanillaUiSmokeScenario.supports(null));
    }

    @Test
    void requiresExactlyThreeCompleteEnchantingOffers() {
        assertTrue(VanillaStationSmoke.threeOffers(new int[]{6, 15, 30}, new int[]{3, 7, 9}, new int[]{1, 2, 3}));
        assertFalse(VanillaStationSmoke.threeOffers(new int[]{6, 0, 30}, new int[]{3, 7, 9}, new int[]{1, 2, 3}));
        assertFalse(VanillaStationSmoke.threeOffers(new int[]{6, 15, 30}, new int[]{3, -1, 9}, new int[]{1, 2, 3}));
        assertFalse(VanillaStationSmoke.threeOffers(new int[]{6, 15, 30}, new int[]{3, 7, 9}, new int[]{1, 0, 3}));
        assertFalse(VanillaStationSmoke.threeOffers(new int[]{6, 15}, new int[]{3, 7, 9}, new int[]{1, 2, 3}));
    }

    @Test
    void speedAttributeAssertionRejectsMissingAndStackedModifiers() {
        assertTrue(VanillaUiSmokeScenario.movementSpeedMatches(0.1, 0.14, 1));
        assertTrue(VanillaUiSmokeScenario.movementSpeedMatches(0.1, 0.12, 0));
        assertFalse(VanillaUiSmokeScenario.movementSpeedMatches(0.1, 0.1, 1));
        assertFalse(VanillaUiSmokeScenario.movementSpeedMatches(0.1, 0.12, 1));
        assertFalse(VanillaUiSmokeScenario.movementSpeedMatches(0.1, 0.168, 1));
        assertFalse(VanillaUiSmokeScenario.movementSpeedMatches(0.0, 0.0, 1));
        assertFalse(VanillaUiSmokeScenario.movementSpeedMatches(0.1, Double.NaN, 1));
    }
}
