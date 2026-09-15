package dev.solaris.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class LoaderSoundCommandTest {
    private static final LoaderActivatedContent CONTENT = new LoaderActivatedContent(
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of("example:tone", new LoaderSoundDefinition("example:tone")));

    @Test
    void stopAndPlayRequireAnActivatedSoundOnTheCurrentConnection() {
        for (int mode : new int[] {0, 1, 2}) {
            byte[] command = payload("example:tone", mode, 1, 1, 0);
            assertTrue(LoaderSoundCommand.resolve(command, CONTENT, true).isPresent());
            assertTrue(LoaderSoundCommand.resolve(command, CONTENT, false).isEmpty());
            assertTrue(LoaderSoundCommand.resolve(command, LoaderActivatedContent.empty(), true).isEmpty());
            assertTrue(LoaderSoundCommand.resolve(payload("other:tone", mode, 1, 1, 0), CONTENT, true).isEmpty());
        }
    }

    @Test
    void invalidSoundParametersAndMalformedWireCannotProduceCommands() {
        for (float volume : new float[] {-1, 1.01f, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertTrue(LoaderSoundCommand.resolve(payload("example:tone", 1, volume, 1, 0), CONTENT, true).isEmpty());
        }
        for (float pitch : new float[] {0.49f, 2.01f, Float.NaN}) {
            assertTrue(LoaderSoundCommand.resolve(payload("example:tone", 1, 1, pitch, 0), CONTENT, true).isEmpty());
        }
        for (double x : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 30_000_001}) {
            assertTrue(LoaderSoundCommand.resolve(payload("example:tone", 2, 1, 1, x), CONTENT, true).isEmpty());
        }
        byte[] valid = payload("example:tone", 2, 1, 1, 30_000_000);
        assertTrue(LoaderSoundCommand.resolve(valid, CONTENT, true).isPresent());
        assertTrue(LoaderSoundCommand.resolve(Arrays.copyOf(valid, valid.length - 1), CONTENT, true).isEmpty());
        assertTrue(LoaderSoundCommand.resolve(Arrays.copyOf(valid, valid.length + 1), CONTENT, true).isEmpty());
        valid[2] = 3;
        assertTrue(LoaderSoundCommand.resolve(valid, CONTENT, true).isEmpty());
        valid = payload("example:tone", 0, 1, 1, 0);
        valid[1] = (byte) (LoaderHandshake.PROTOCOL_VERSION + 1);
        assertTrue(LoaderSoundCommand.resolve(valid, CONTENT, true).isEmpty());
        valid = payload("example:tone", 0, 1, 1, 0);
        valid[5] = (byte) 0xff;
        assertTrue(LoaderSoundCommand.resolve(valid, CONTENT, true).isEmpty());
    }

    @Test
    void soundChannelUsesWireThreeOnly() {
        assertEquals(3, LoaderHandshake.PROTOCOL_VERSION);
        byte[] play = payload("example:tone", 1, 1, 1, 0);
        assertEquals(
                LoaderHandshake.PROTOCOL_VERSION,
                ((play[0] & 0xff) << 8) | (play[1] & 0xff));
        assertTrue(LoaderSoundCommand.resolve(play, CONTENT, true).isPresent());
        byte[] protocolTwo = play.clone();
        protocolTwo[0] = 0;
        protocolTwo[1] = 2;
        assertTrue(LoaderSoundCommand.resolve(protocolTwo, CONTENT, true).isEmpty());
    }

    private static byte[] payload(String id, int mode, float volume, float pitch, double x) {
        byte[] name = id.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(5 + name.length + (mode == 0 ? 0 : mode == 1 ? 8 : 32));
        buffer.putShort((short) LoaderHandshake.PROTOCOL_VERSION).put((byte) mode).putShort((short) name.length).put(name);
        if (mode != 0) { buffer.putFloat(volume).putFloat(pitch); }
        if (mode == 2) { buffer.putDouble(x).putDouble(64).putDouble(0); }
        return buffer.array();
    }
}
