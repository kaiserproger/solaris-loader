package dev.solaris.loader;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ComputerProtocolTest {
    @Test void matchesServerBigEndianVectors() {
        assertArrayEquals(new byte[] {1,0,0,0,0,7, -1,-1,-1,-17, 0,0,0,64, 0,0,0,25},
                ComputerProtocol.open(7, -17, 64, 25));
        assertArrayEquals(new byte[] {1,2,0,0,0,7}, ComputerProtocol.close(7));
        var bytes = ComputerProtocol.line(7, "hello λ");
        assertEquals("hello λ", new String(bytes, 6, bytes.length - 6, StandardCharsets.UTF_8));
        assertEquals(7, ComputerProtocol.reply(bytes).session());
    }
    @Test void rejectsMalformedAndOverLimitMessages() {
        assertThrows(IllegalArgumentException.class, () -> ComputerProtocol.open(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ComputerProtocol.line(1, "a\nb"));
        assertThrows(IllegalArgumentException.class, () -> ComputerProtocol.line(1, "λ".repeat(2049)));
        assertDoesNotThrow(() -> ComputerProtocol.line(1, "a".repeat(4096)));
        assertThrows(IllegalArgumentException.class, () -> ComputerProtocol.reply(new byte[5]));
        assertThrows(IllegalArgumentException.class, () -> ComputerProtocol.reply(new byte[] {1,1,0,0,0,1,(byte)255}));
        assertThrows(IllegalArgumentException.class, () -> ComputerProtocol.reply(new byte[] {2,1,0,0,0,1}));
        assertThrows(IllegalArgumentException.class, () -> ComputerProtocol.reply(new byte[16391]));
    }
    @Test void staleRepliesCannotOpenNewOrClosedScreen() {
        var terminal = new ComputerTerminal(7);
        assertFalse(terminal.accept(new ComputerProtocol.Reply(8, 0, "stale")));
        assertFalse(terminal.connected());
        assertTrue(terminal.accept(new ComputerProtocol.Reply(7, 0, "opened")));
        assertTrue(terminal.connected());
        terminal.accept(new ComputerProtocol.Reply(7, 1, "agent reply\n"));
        assertTrue(terminal.text().contains("agent reply"));
        terminal.accept(new ComputerProtocol.Reply(7, 2, "closed"));
        assertFalse(terminal.connected());
        assertFalse(terminal.accept(new ComputerProtocol.Reply(7, 0, "late open")));
    }
    @Test void scrollbackIsBoundedAndControlsAreNotInterpreted() {
        var terminal = new ComputerTerminal(1);
        terminal.append("a".repeat(40000));
        assertEquals(32768, terminal.text().length());
        terminal.append("\u001b\u0000\nplain");
        assertFalse(terminal.text().contains("\u001b"));
        assertTrue(terminal.text().endsWith("\nplain"));
    }
}
