package dev.solaris.loader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ScreenVideoTest {
    private static final ScreenProtocol.Id ID = new ScreenProtocol.Id(-2, 4, 9, 2, 3, 2, 0);

    /** Flat window, text-like stripes and a moving box: stands in for a desktop. */
    private static int[] desktop(int width, int height, int shift) {
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = 36;
                int g = 52;
                int b = 84;
                if (y % 24 < 8 && x > 16 && x < width - 16 && (x / 6) % 5 != 4) {
                    r = 220;
                    g = 224;
                    b = 230;
                }
                if (x >= 40 + shift && x < 120 + shift && y >= 30 && y < 90) {
                    r = 240;
                    g = 30;
                    b = 30;
                }
                argb[y * width + x] = 0xFF000000 | r << 16 | g << 8 | b;
            }
        }
        return argb;
    }

    /** What the server relays to a viewer for the owner's parts of one picture. */
    private static byte[] relayed(List<byte[]> parts) {
        byte[] first = parts.get(0);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            data.write(part, 30, part.length - 30);
        }
        ByteBuffer out = ByteBuffer.allocate(2 + 16 + 10 + data.size());
        out.put(first, 0, 2).put(first, 2, 16);
        out.put(1, (byte) 1);
        out.put(first, 18, 10);
        out.put(data.toByteArray());
        return out.array();
    }

    private static double meanError(ScreenVideo.Picture picture, int[] expected) {
        long sum = 0;
        for (int i = 0; i < expected.length; i++) {
            int a = picture.argb()[i];
            int b = expected[i];
            sum += Math.abs((a >> 16 & 255) - (b >> 16 & 255)) + Math.abs((a >> 8 & 255) - (b >> 8 & 255))
                    + Math.abs((a & 255) - (b & 255));
        }
        return sum / (3.0 * expected.length);
    }

    @Test
    void keyThenDeltaPicturesRoundTripThroughTheRelay() {
        ScreenVideo.Encoder encoder = new ScreenVideo.Encoder();
        ScreenRelay relay = new ScreenRelay();
        relay.receive(active(ID));
        int[] first = desktop(640, 360, 0);
        ScreenVideo.Encoder.Encoded key = encoder.encode(first, 640, 360, 640, false);
        assertTrue(key.key());
        assertEquals(640, key.width());
        assertEquals(360, key.height());
        List<byte[]> keyParts = ScreenVideo.parts(ID, 1, key);
        assertTrue(keyParts.size() <= ScreenProtocol.MAX_PARTS);
        relay.receive(relayed(keyParts));
        ScreenVideo.Picture decoded = relay.active().get(ID).picture();
        assertNotNull(decoded);
        assertEquals(640 * 360, decoded.argb().length);
        assertTrue(meanError(decoded, first) < 6, "key picture error " + meanError(decoded, first));

        int[] second = desktop(640, 360, 24);
        ScreenVideo.Encoder.Encoded delta = encoder.encode(second, 640, 360, 640, false);
        assertFalse(delta.key());
        assertTrue(delta.data().length < key.data().length, "a delta is smaller than its key frame");
        relay.receive(relayed(ScreenVideo.parts(ID, 2, delta)));
        assertTrue(meanError(relay.active().get(ID).picture(), second) < 6);
        assertTrue(relay.takeOutbox().isEmpty());
    }

    @Test
    void unchangedPicturesSendNothingAndAKeyIsStillOwed() {
        ScreenVideo.Encoder encoder = new ScreenVideo.Encoder();
        int[] picture = desktop(320, 200, 0);
        assertNotNull(encoder.encode(picture, 320, 200, 640, false));
        assertNull(encoder.encode(picture.clone(), 320, 200, 640, false));
        ScreenVideo.Encoder.Encoded again = encoder.encode(picture, 320, 200, 640, true);
        assertNotNull(again);
        assertTrue(again.key());
        // A key frame is self-contained: a fresh decoder starts from it.
        ScreenRelay viewer = new ScreenRelay();
        viewer.receive(active(ID));
        viewer.receive(relayed(ScreenVideo.parts(ID, 9, again)));
        assertNotNull(viewer.active().get(ID).picture());
    }

    @Test
    void aKeyIsSpsPpsIdrAndADeltaIsOneNonIdrSlice() {
        ScreenVideo.Encoder encoder = new ScreenVideo.Encoder();
        ScreenVideo.Encoder.Encoded key = encoder.encode(desktop(320, 200, 0), 320, 200, 640, false);
        ScreenVideo.Encoder.Encoded delta = encoder.encode(desktop(320, 200, 1), 320, 200, 640, false);
        assertEquals(List.of(7, 8, 5), nalTypes(key.data()));
        assertEquals(List.of(1), nalTypes(delta.data()));
    }

    private static List<Integer> nalTypes(byte[] annexB) {
        List<Integer> types = new java.util.ArrayList<>();
        for (int i = 0; i + 3 < annexB.length; i++) {
            if (annexB[i] == 0 && annexB[i + 1] == 0 && annexB[i + 2] == 1) {
                types.add(annexB[i + 3] & 31);
            }
        }
        return types;
    }

    @Test
    void aDeltaBeforeAnyKeyAsksForOneOnceAndCorruptionResyncs() {
        ScreenVideo.Encoder encoder = new ScreenVideo.Encoder();
        ScreenRelay viewer = new ScreenRelay();
        viewer.receive(active(ID));
        encoder.encode(desktop(320, 200, 0), 320, 200, 640, false);
        ScreenVideo.Encoder.Encoded delta = encoder.encode(desktop(320, 200, 10), 320, 200, 640, false);
        viewer.receive(relayed(ScreenVideo.parts(ID, 2, delta)));
        assertNull(viewer.active().get(ID).picture());
        assertEquals(1, viewer.takeOutbox().size());
        ScreenVideo.Encoder.Encoded delta2 = encoder.encode(desktop(320, 200, 20), 320, 200, 640, false);
        viewer.receive(relayed(ScreenVideo.parts(ID, 3, delta2)));
        assertTrue(viewer.takeOutbox().isEmpty(), "one outstanding KEY_REQUEST at a time");

        ScreenVideo.Encoder.Encoded key = encoder.encode(desktop(320, 200, 30), 320, 200, 640, true);
        viewer.receive(relayed(ScreenVideo.parts(ID, 4, key)));
        assertNotNull(viewer.active().get(ID).picture());
        // Garbage in a delta: error, decoder dropped, a KEY_REQUEST goes out.
        byte[] garbage = relayed(ScreenVideo.parts(ID, 5,
                new ScreenVideo.Encoder.Encoded(320, 200, false, new byte[] {0, 0, 0, 1, 0x41, 0x13, 0x37})));
        assertThrows(IllegalArgumentException.class, () -> viewer.receive(garbage));
        assertEquals(1, viewer.takeOutbox().size());
    }

    @Test
    void noisePicturesStayWithinTheFrameBound() {
        ScreenVideo.Encoder encoder = new ScreenVideo.Encoder();
        int[] noise = new int[640 * 640];
        long seed = 1;
        for (int i = 0; i < noise.length; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            noise[i] = 0xFF000000 | (int) (seed >>> 40) & 0xFFFFFF;
        }
        ScreenVideo.Encoder.Encoded picture = encoder.encode(noise, 640, 640, 640, false);
        assertTrue(picture.data().length <= ScreenProtocol.MAX_FRAME_BYTES);
        assertTrue(picture.key());
        assertTrue(ScreenVideo.parts(ID, 1, picture).size() <= ScreenProtocol.MAX_PARTS);
    }

    @Test
    void oddSourceSizesScaleToEvenPictures() {
        ScreenVideo.Encoder encoder = new ScreenVideo.Encoder();
        ScreenVideo.Encoder.Encoded picture = encoder.encode(desktop(1281, 721, 0), 1281, 721, 640, false);
        assertEquals(0, picture.width() % 2);
        assertEquals(0, picture.height() % 2);
        assertTrue(Math.max(picture.width(), picture.height()) <= 640);
        ScreenVideo.Picture decoded = new ScreenVideo.Decoder()
                .decode(picture.data(), picture.width(), picture.height());
        assertEquals(picture.width() * picture.height(), decoded.argb().length);
    }

    @Test
    void demandOnAndKeyRequestsAskTheOwnerForKeyFrames() {
        ScreenRelay owner = new ScreenRelay();
        owner.receive(withId(0, (byte) 1, (byte) 2, (byte) 0x80, (byte) 5));
        assertTrue(owner.takeKeyWanted(ID));
        assertFalse(owner.takeKeyWanted(ID));
        owner.receive(withId(8));
        assertTrue(owner.takeKeyWanted(ID));
        owner.receive(withId(0, (byte) 0, (byte) 2, (byte) 0x80, (byte) 5));
        owner.receive(withId(8));
        assertFalse(owner.takeKeyWanted(ID), "no stream, nothing to restart");
        // A JPEG frame (codec 0) is refused.
        assertThrows(IllegalArgumentException.class, () -> ScreenProtocol.decode(
                withId(1, new byte[] {0, 0, 0, 1, 0, 2, 0, 2, 0, 0, 0, 1})));
        assertArrayEquals(new byte[] {1, 8}, java.util.Arrays.copyOf(ScreenProtocol.keyRequest(ID), 2));
    }

    private static byte[] active(ScreenProtocol.Id id) {
        byte[] head = ScreenProtocol.request(id).clone();
        head[1] = 6;
        byte[] out = java.util.Arrays.copyOf(head, head.length + 1);
        out[head.length] = 1;
        return out;
    }

    private static byte[] withId(int kind, byte... body) {
        byte[] head = ScreenProtocol.request(ID).clone();
        head[1] = (byte) kind;
        byte[] out = java.util.Arrays.copyOf(head, head.length + body.length);
        System.arraycopy(body, 0, out, head.length, body.length);
        return out;
    }
}
