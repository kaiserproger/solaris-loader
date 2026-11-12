package dev.krasnokamen.loader;

import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusException;
import java.nio.ByteBuffer;

/** One received screen's Opus decoder and bounded PCM queue; never opens a capture device. */
public final class ScreenAudio implements AutoCloseable {
    public static final int RATE = 48_000;
    public static final int SAMPLES = 960;
    private static final int QUEUED_FRAMES = 5;
    private final OpusDecoder decoder;
    private final short[][] frames = new short[QUEUED_FRAMES][SAMPLES];
    private int head;
    private int size;
    private int offset;
    private long sequence;
    private boolean received;
    private boolean closed;

    public ScreenAudio() {
        try {
            decoder = new OpusDecoder(RATE, 1);
        } catch (OpusException error) {
            throw new IllegalStateException("Cannot initialize screen Opus decoder", error);
        }
    }

    /** Reliable transport preserves order; a dropped packet resets codec prediction, not playback permissions. */
    public synchronized void receive(long next, byte[] opus) {
        if (closed) {
            return;
        }
        if (opus.length == 0 || opus.length > ScreenProtocol.MAX_OPUS_BYTES) {
            throw new IllegalArgumentException("screen audio packet bound");
        }
        long distance = (next - sequence) & 0xFFFFFFFFL;
        if (received && (distance == 0 || distance > Integer.MAX_VALUE)) {
            return;
        }
        if (received && distance != 1) {
            decoder.resetState();
        }
        // Keep latency bounded at 100 ms when rendering/audio consumption falls behind.
        if (size == QUEUED_FRAMES) {
            head = (head + 1) % QUEUED_FRAMES;
            size--;
            offset = 0;
        }
        try {
            int samples = decoder.decode(opus, 0, opus.length,
                    frames[(head + size) % QUEUED_FRAMES], 0, SAMPLES, false);
            if (samples != SAMPLES) {
                throw new IllegalArgumentException("screen audio must contain 20 ms at 48 kHz");
            }
            size++;
            sequence = next;
            received = true;
        } catch (OpusException | IllegalArgumentException error) {
            close();
            throw new IllegalArgumentException("Cannot decode screen Opus packet: " + error.getMessage(), error);
        }
    }

    /** Writes at most one 20 ms buffer, with silence on underflow. Never waits on network data. */
    public synchronized void read(ByteBuffer pcm) {
        int count = Math.min(SAMPLES, pcm.remaining() / Short.BYTES);
        for (int i = 0; i < count; i++) {
            short sample = 0;
            if (!closed && size > 0) {
                sample = frames[head][offset++];
                if (offset == SAMPLES) {
                    head = (head + 1) % QUEUED_FRAMES;
                    size--;
                    offset = 0;
                }
            }
            pcm.putShort(sample);
        }
    }

    public synchronized boolean closed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        closed = true;
        size = 0;
        offset = 0;
    }
}
