package dev.krasnokamen.loader;

import static org.junit.jupiter.api.Assertions.*;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class ScreenAudioTest {
    private static final ScreenProtocol.Id ID = new ScreenProtocol.Id(3, 10, 7, 2, 2, 1, 0);

    private static byte[] tone(int samples) throws Exception {
        OpusEncoder encoder = new OpusEncoder(ScreenAudio.RATE, 1, OpusApplication.OPUS_APPLICATION_AUDIO);
        encoder.setBitrate(32_000);
        short[] pcm = new short[samples];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (short) (12_000 * Math.sin(2 * Math.PI * 440 * i / ScreenAudio.RATE));
        }
        byte[] packet = new byte[ScreenProtocol.MAX_OPUS_BYTES];
        int bytes = encoder.encode(pcm, 0, pcm.length, packet, 0, packet.length);
        return Arrays.copyOf(packet, bytes);
    }

    private static ByteBuffer read(ScreenAudio audio) {
        ByteBuffer pcm = ByteBuffer.allocate(ScreenAudio.SAMPLES * 2).order(ByteOrder.LITTLE_ENDIAN);
        audio.read(pcm);
        return pcm.flip();
    }

    private static long energy(ByteBuffer pcm) {
        long sum = 0;
        while (pcm.hasRemaining()) {
            long sample = pcm.getShort();
            sum += sample * sample;
        }
        return sum;
    }

    private static byte[] message(int kind, byte... body) {
        byte[] wire = Arrays.copyOf(ScreenProtocol.open(ID), 18 + body.length);
        wire[1] = (byte) kind;
        System.arraycopy(body, 0, wire, 18, body.length);
        return wire;
    }

    private static byte[] audio(long sequence, byte[] opus) {
        return message(7, ByteBuffer.allocate(4 + opus.length).putInt((int) sequence).put(opus).array());
    }

    @Test
    void decodesRealOpusAndUnderflowIsSilence() throws Exception {
        ScreenAudio receiver = new ScreenAudio();
        receiver.receive(1, tone(960));
        assertTrue(energy(read(receiver)) > 1_000_000);
        assertEquals(0, energy(read(receiver)));
        receiver.close();
        assertTrue(receiver.closed());
        assertEquals(0, energy(read(receiver)));
    }

    @Test
    void rejectsWrongDurationAndMalformedPackets() throws Exception {
        for (int samples : new int[] {480, 1920}) {
            ScreenAudio receiver = new ScreenAudio();
            byte[] packet = tone(samples);
            assertThrows(IllegalArgumentException.class, () -> receiver.receive(1, packet));
            assertTrue(receiver.closed());
        }
        ScreenAudio receiver = new ScreenAudio();
        assertThrows(IllegalArgumentException.class, () -> receiver.receive(1, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> receiver.receive(1, new byte[1001]));
    }

    @Test
    void sequenceWrapDuplicatesAndQueueBound() throws Exception {
        byte[] packet = tone(960);
        ScreenAudio receiver = new ScreenAudio();
        receiver.receive(0xFFFFFFFFL, packet);
        receiver.receive(0, packet);
        receiver.receive(0, packet);
        receiver.receive(0xFFFFFFFFL, packet);
        assertTrue(energy(read(receiver)) > 0);
        assertTrue(energy(read(receiver)) > 0);
        assertEquals(0, energy(read(receiver)));
        for (int i = 1; i <= 20; i++) {
            receiver.receive(i, packet);
        }
        for (int i = 0; i < 5; i++) {
            assertTrue(energy(read(receiver)) > 0);
        }
        assertEquals(0, energy(read(receiver)), "queue must not retain more than 100 ms");
    }

    @Test
    void shortReadsPreserveRemainingPcm() throws Exception {
        byte[] packet = tone(960);
        ScreenAudio whole = new ScreenAudio();
        ScreenAudio split = new ScreenAudio();
        whole.receive(1, packet);
        split.receive(1, packet);
        ByteBuffer joined = ByteBuffer.allocate(1920).order(ByteOrder.LITTLE_ENDIAN);
        joined.limit(600);
        split.read(joined);
        joined.limit(1920);
        split.read(joined);
        assertArrayEquals(read(whole).array(), joined.array());
    }

    @Test
    void stopRefusalPermissionRemovalAndDisconnectCloseQueuedAudio() throws Exception {
        byte[] packet = audio(1, tone(960));
        byte[][] endings = {
                message(2, (byte) 0), message(2, (byte) 1), message(2, (byte) 2), message(2, (byte) 3),
                message(3, (byte) 0), message(5, (byte) 0), message(6, (byte) 0)
        };
        for (byte[] ending : endings) {
            ScreenRelay relay = new ScreenRelay();
            relay.receive(message(6, (byte) 1));
            relay.receive(packet);
            assertNull(relay.active().get(ID).audio(), "unsolicited audio must not play");
            relay.watching().add(ID);
            relay.receive(packet);
            ScreenAudio receiver = relay.active().get(ID).audio();
            assertNotNull(receiver);
            relay.receive(ending);
            assertTrue(receiver.closed());
            assertEquals(0, energy(read(receiver)));
        }
        ScreenRelay relay = new ScreenRelay();
        relay.receive(message(6, (byte) 1));
        relay.watching().add(ID);
        relay.receive(packet);
        ScreenAudio first = relay.active().get(ID).audio();
        relay.clearPictures();
        assertTrue(first.closed(), "F8 must clear audio as well as pictures");
        relay.receive(packet);
        ScreenAudio second = relay.active().get(ID).audio();
        assertNotSame(first, second);
        relay.clear();
        assertTrue(second.closed(), "disconnect must close the decoder and PCM queue");
    }
}
