package dev.solaris.loader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ScreenProtocolTest {
    private static final ScreenProtocol.Id ID = new ScreenProtocol.Id(-2, 4, 9, 2, 3, 2, 0);

    private static byte[] server(int kind, ScreenProtocol.Id id, byte... body) {
        byte[] head = ScreenProtocol.request(id).clone();
        head[1] = (byte) kind;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(head);
        out.writeBytes(body);
        return out.toByteArray();
    }

    @Test
    void identityPlacesHeightBeforeDimensionAndRejectsOutOfRange() {
        byte[] wire = ScreenProtocol.open(ID);
        assertEquals(18, wire.length);
        assertArrayEquals(new byte[] {2, 3, 2, 0}, java.util.Arrays.copyOfRange(wire, 14, 18));
        assertEquals(1, wire[0]);
        assertEquals(0, wire[1]);
        assertThrows(IllegalArgumentException.class, () -> new ScreenProtocol.Id(0, 0, 0, 1, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ScreenProtocol.Id(0, 0, 0, 2, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ScreenProtocol.Id(0, 0, 0, 2, 1, 17, 0));
        assertThrows(IllegalArgumentException.class, () -> new ScreenProtocol.Id(0, 0, 0, 2, 1, 1, 3));
    }

    @Test
    void clientMessagesMatchTheWire() {
        assertEquals(3, ScreenProtocol.request(ID)[1]);
        assertEquals(1, ScreenProtocol.close(ID)[1]);
        assertEquals(6, ScreenProtocol.leave(ID)[1]);
        assertArrayEquals(new byte[] {1, 4, 0, 0, 0, 17, 1}, ScreenProtocol.answer(17, true));
        byte[] revoke = ScreenProtocol.revoke(ID, new byte[16]);
        assertEquals(34, revoke.length);
        assertEquals(5, revoke[1]);
    }

    @Test
    void serverMessagesDecode() {
        assertEquals(new ScreenProtocol.Demand(ID, true, 640, 5),
                ScreenProtocol.decode(server(0, ID, (byte) 1, (byte) 2, (byte) 0x80, (byte) 5)));
        assertEquals(new ScreenProtocol.Stop(ID, 2), ScreenProtocol.decode(server(2, ID, (byte) 2)));
        assertEquals(new ScreenProtocol.Refused(ID, 4), ScreenProtocol.decode(server(3, ID, (byte) 4)));
        assertEquals(new ScreenProtocol.Permission(ID, true), ScreenProtocol.decode(server(5, ID, (byte) 1)));
        assertEquals(new ScreenProtocol.Active(ID, true), ScreenProtocol.decode(server(6, ID, (byte) 1)));
        byte[] name = "Bob".getBytes(StandardCharsets.UTF_8);
        ByteBuffer body = ByteBuffer.allocate(4 + 16 + 1 + name.length);
        body.putInt(17).put(new byte[16]).put((byte) name.length).put(name);
        ScreenProtocol.Requested requested =
                (ScreenProtocol.Requested) ScreenProtocol.decode(server(4, ID, body.array()));
        assertEquals(17, requested.request());
        assertEquals("Bob", requested.name());
        assertThrows(IllegalArgumentException.class, () -> ScreenProtocol.decode(server(6, ID, (byte) 2)));
        assertThrows(IllegalArgumentException.class, () -> ScreenProtocol.decode(server(6, ID, (byte) 1, (byte) 0)));
        assertThrows(IllegalArgumentException.class, () -> ScreenProtocol.decode(server(9, ID, (byte) 0)));
        assertEquals(new ScreenProtocol.KeyRequest(ID), ScreenProtocol.decode(server(8, ID)));
        ScreenProtocol.Audio audio = (ScreenProtocol.Audio) ScreenProtocol.decode(
                server(7, ID, (byte) 0, (byte) 0, (byte) 0, (byte) 1, (byte) 9));
        assertEquals(ID, audio.id());
        assertEquals(1, audio.sequence());
        assertArrayEquals(new byte[] {9}, audio.opus());
        assertThrows(IllegalArgumentException.class, () -> ScreenProtocol.decode(server(7, ID, new byte[4])));
        assertThrows(IllegalArgumentException.class, () -> ScreenProtocol.decode(server(7, ID, new byte[1005])));
        assertEquals(8, ScreenProtocol.keyRequest(ID)[1]);
        assertThrows(IllegalArgumentException.class, () -> ScreenProtocol.decode(server(0, ID, (byte) 1, (byte) 2, (byte) 0x81, (byte) 5)));
    }

    @Test
    void relayTracksActiveStopAndRequests() {
        ScreenRelay relay = new ScreenRelay();
        assertThrows(IllegalArgumentException.class,
                () -> relay.receive(server(1, ID, new byte[10])));
        relay.receive(server(6, ID, (byte) 1));
        assertNotNull(relay.active().get(ID));
        assertNull(relay.active().get(ID).picture());
        relay.watching().add(ID);
        relay.receive(server(5, ID, (byte) 0));
        assertTrue(relay.watching().isEmpty());
        assertEquals("Screen owner denied viewing", relay.takeNotice());
        relay.receive(server(2, ID, (byte) 1));
        assertEquals("Screen viewing permission ended", relay.takeNotice());
        relay.receive(server(6, ID, (byte) 0));
        assertTrue(relay.active().isEmpty());
        byte[] name = "Al".getBytes(StandardCharsets.UTF_8);
        ByteBuffer body = ByteBuffer.allocate(4 + 16 + 1 + name.length);
        body.putInt(5).put(new byte[16]).put((byte) name.length).put(name);
        relay.receive(server(4, ID, body.array()));
        assertEquals(1, relay.pendingRequests());
        assertEquals("Al", relay.pollRequest().name());
    }

    @Test
    void ownerOpenIsAcknowledgedByDemandOffAndRefusalEndsIt() {
        ScreenRelay relay = new ScreenRelay();
        relay.receive(server(0, ID, (byte) 0, (byte) 2, (byte) 0x80, (byte) 5));
        assertTrue(relay.takeAcknowledged().contains(ID));
        assertTrue(relay.demand().isEmpty());
        assertTrue(relay.takeAcknowledged().isEmpty());
        relay.receive(server(3, ID, (byte) 1));
        assertTrue(relay.takeRefused().contains(ID));
        assertEquals("Screen sharing: monitor rectangle changed", relay.takeNotice());
    }

    @Test
    void rectanglesShareOneSurfaceFromEveryMemberAndRejectHoles() {
        for (int side = 2; side <= 5; side++) {
            int axis = side <= 3 ? 0 : 2;
            final int fixed = 7;
            ScreenGeometry.Members member = (x, y, z) -> {
                int[] p = {x, y, z};
                return y >= 4 && y < 6 && p[2 - axis] == fixed && p[axis] >= 0 && p[axis] < 4;
            };
            for (int h = 0; h < 4; h++) {
                for (int y = 4; y < 6; y++) {
                    int[] p = {0, y, 0};
                    p[axis] = h;
                    p[2 - axis] = fixed;
                    Optional<ScreenProtocol.Id> id =
                            ScreenGeometry.rectangle(p[0], p[1], p[2], side, 0, member);
                    assertEquals(4, id.orElseThrow().width());
                    assertEquals(2, id.orElseThrow().height());
                    assertTrue(id.orElseThrow().contains(p[0], p[1], p[2], side));
                }
            }
            ScreenGeometry.Members holed = (x, y, z) -> member.isMonitor(x, y, z)
                    && !((axis == 0 ? x : z) == 1 && y == 5);
            int[] p = {0, 4, 0};
            p[2 - axis] = fixed;
            assertTrue(ScreenGeometry.rectangle(p[0], p[1], p[2], side, 0, holed).isEmpty());
        }
    }

    @Test
    void faceCenterMapsToPictureCenterOnEverySide() {
        for (int side = 2; side <= 5; side++) {
            ScreenProtocol.Id id = new ScreenProtocol.Id(-3, 20, 7, side, 1, 1, 0);
            ScreenGeometry.Basis basis = ScreenGeometry.basis(id);
            double[] center = new double[3];
            for (int i = 0; i < 3; i++) {
                center[i] = basis.origin()[i] + basis.right()[i] * 0.5 + basis.down()[i] * 0.5;
            }
            // Centre lies in front of the block face, never inside the block.
            double[] block = {-3, 20, 7};
            int axisAcross = side <= 3 ? 2 : 0;
            double offset = center[axisAcross] - block[axisAcross];
            assertTrue(offset < 0 || offset > 1, "side " + side);
            assertEquals(0.5, center[1] - 20, 1e-9);
        }
        double[] fit = ScreenGeometry.fit(1920, 1080, 4, 2);
        assertEquals(1.875, fit[1], 1e-9);
        assertEquals(1920 * 1.875 / 1080, fit[0], 1e-9);
    }
}
