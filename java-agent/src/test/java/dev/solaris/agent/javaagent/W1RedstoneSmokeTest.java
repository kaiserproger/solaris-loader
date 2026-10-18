package dev.solaris.agent.javaagent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class W1RedstoneSmokeTest {
    @Test
    void circuitRequiresEveryReceivedSignalAndCorrectFixture() {
        assertTrue(W1RedstoneSmoke.circuitMatches(true, circuit(true, true, 15, true, 15, true)));
        assertFalse(W1RedstoneSmoke.circuitMatches(true, circuit(false, true, 15, true, 15, true)));
        assertFalse(W1RedstoneSmoke.circuitMatches(true, circuit(true, false, 15, true, 15, true)));
        assertFalse(W1RedstoneSmoke.circuitMatches(true, circuit(true, true, 0, true, 15, true)));
        assertFalse(W1RedstoneSmoke.circuitMatches(true, circuit(true, true, 15, false, 15, true)));
        assertFalse(W1RedstoneSmoke.circuitMatches(true, circuit(true, true, 15, true, 0, true)));
        assertFalse(W1RedstoneSmoke.circuitMatches(true, circuit(true, true, 15, true, 15, false)));
        assertFalse(W1RedstoneSmoke.circuitMatches(true, circuit(true, true, 14, true, 15, true)));
        assertFalse(W1RedstoneSmoke.circuitMatches(true, null));
    }

    @Test
    void offCircuitRejectsMissingWireAndStaleLampOrRepeater() {
        assertTrue(W1RedstoneSmoke.circuitMatches(false, circuit(true, false, 0, false, 0, false)));
        assertFalse(W1RedstoneSmoke.circuitMatches(false, circuit(false, false, 0, false, 0, false)));
        assertFalse(W1RedstoneSmoke.circuitMatches(false, circuit(true, false, -1, false, 0, false)));
        assertFalse(W1RedstoneSmoke.circuitMatches(false, circuit(true, false, 0, false, -1, false)));
        assertFalse(W1RedstoneSmoke.circuitMatches(false, circuit(true, false, 0, true, 0, false)));
        assertFalse(W1RedstoneSmoke.circuitMatches(false, circuit(true, false, 0, false, 0, true)));
    }

    @Test
    void pistonMustActuallyMovePayloadAndHaveCorrectHead() {
        assertTrue(W1RedstoneSmoke.pistonMatches(true, piston(true, true, true, true, false, true)));
        assertFalse(W1RedstoneSmoke.pistonMatches(true, piston(false, true, true, true, false, true)));
        assertFalse(W1RedstoneSmoke.pistonMatches(true, piston(true, false, true, true, false, true)));
        assertFalse(W1RedstoneSmoke.pistonMatches(true, piston(true, true, false, true, false, true)));
        assertFalse(W1RedstoneSmoke.pistonMatches(true, piston(true, true, true, false, false, true)));
        assertFalse(W1RedstoneSmoke.pistonMatches(true, piston(true, true, true, true, true, true)));
        assertFalse(W1RedstoneSmoke.pistonMatches(true, piston(true, true, true, true, false, false)));
        assertTrue(W1RedstoneSmoke.pistonMatches(false, piston(true, false, false, false, true, true)));
        assertFalse(W1RedstoneSmoke.pistonMatches(false, piston(true, false, false, true, false, true)));
        assertFalse(W1RedstoneSmoke.pistonMatches(false, piston(true, true, false, false, true, true)));
        assertFalse(W1RedstoneSmoke.pistonMatches(false, piston(true, false, false, false, false, true)));
    }

    @Test
    void comparatorRequiresPoweredStateAndExactAnalogWireOutput() {
        assertTrue(W1RedstoneSmoke.comparatorMatches(1, true, 1));
        assertTrue(W1RedstoneSmoke.comparatorMatches(0, false, 0));
        assertFalse(W1RedstoneSmoke.comparatorMatches(1, false, 1));
        assertFalse(W1RedstoneSmoke.comparatorMatches(1, true, 15));
        assertFalse(W1RedstoneSmoke.comparatorMatches(1, true, 0));
        assertFalse(W1RedstoneSmoke.comparatorMatches(0, true, 0));
        assertFalse(W1RedstoneSmoke.comparatorMatches(0, false, -1));
        assertFalse(W1RedstoneSmoke.comparatorMatches(16, true, 16));
        assertFalse(W1RedstoneSmoke.comparatorMatches(-1, false, -1));
    }

    @Test
    void exactTransferRejectsLossDuplicationResidualItemsAndPlayerCopies() {
        var empty = inventory(5, 0, 0, 0, true);
        var output = inventory(27, 5, 0, 0, true);
        assertTrue(W1RedstoneSmoke.transferMatches(empty, empty, output));
        assertFalse(W1RedstoneSmoke.transferMatches(empty, empty, inventory(27, 4, 0, 0, true)));
        assertFalse(W1RedstoneSmoke.transferMatches(empty, empty, inventory(27, 6, 0, 0, true)));
        assertFalse(W1RedstoneSmoke.transferMatches(inventory(5, 1, 0, 0, true), empty, output));
        assertFalse(W1RedstoneSmoke.transferMatches(empty, inventory(5, 1, 0, 0, true), output));
        assertFalse(W1RedstoneSmoke.transferMatches(empty, inventory(5, 0, 1, 0, true), output));
        assertFalse(W1RedstoneSmoke.transferMatches(empty, empty, inventory(27, 5, 1, 0, true)));
        assertFalse(W1RedstoneSmoke.transferMatches(empty, empty, inventory(27, 5, 0, 5, true)));
        assertFalse(W1RedstoneSmoke.transferMatches(empty, empty, inventory(27, 5, 0, 0, false)));
        assertFalse(W1RedstoneSmoke.transferMatches(empty, empty, inventory(54, 5, 0, 0, true)));
        assertFalse(W1RedstoneSmoke.transferMatches(inventory(27, 0, 0, 0, true), empty, output));
        assertFalse(W1RedstoneSmoke.transferMatches(null, empty, output));
        assertFalse(W1RedstoneSmoke.transferMatches(empty, null, output));
        assertFalse(W1RedstoneSmoke.transferMatches(empty, empty, null));
    }

    @Test
    void nativeInputMustConserveExactlyFiveWithoutCursorOrPlayerRemainder() {
        assertTrue(W1RedstoneSmoke.inventoryMatches(inventory(5, 5, 0, 0, true), 5, 5));
        assertFalse(W1RedstoneSmoke.inventoryMatches(inventory(5, 5, 0, 1, true), 5, 5));
        assertFalse(W1RedstoneSmoke.inventoryMatches(inventory(5, 5, 0, 0, false), 5, 5));
        assertFalse(W1RedstoneSmoke.inventoryMatches(inventory(5, 4, 0, 0, true), 5, 5));
        assertFalse(W1RedstoneSmoke.inventoryMatches(inventory(5, 5, 1, 0, true), 5, 5));
        assertFalse(W1RedstoneSmoke.inventoryMatches(inventory(27, 5, 0, 0, true), 5, 5));
        assertFalse(W1RedstoneSmoke.inventoryMatches(inventory(4, 5, 0, 0, true), 4, 5));
        assertFalse(W1RedstoneSmoke.inventoryMatches(inventory(5, -1, 0, 0, true), 5, -1));
    }

    private static W1RedstoneSmoke.CircuitSnapshot circuit(boolean valid, boolean lever, int input,
                                                           boolean repeater, int output, boolean lamp) {
        return new W1RedstoneSmoke.CircuitSnapshot(valid, lever, input, repeater, output, lamp);
    }

    private static W1RedstoneSmoke.PistonSnapshot piston(boolean valid, boolean button, boolean extended,
                                                         boolean head, boolean air, boolean payload) {
        return new W1RedstoneSmoke.PistonSnapshot(valid, button, extended, head, air, payload);
    }

    private static W1RedstoneSmoke.InventorySnapshot inventory(int slots, int diamonds, int other,
                                                               int player, boolean cursorEmpty) {
        return new W1RedstoneSmoke.InventorySnapshot(slots, diamonds, other, player, cursorEmpty);
    }
}
