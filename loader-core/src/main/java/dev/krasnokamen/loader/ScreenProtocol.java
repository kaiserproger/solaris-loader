package dev.krasnokamen.loader;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Wire codec of the {@code krasnokamen:screen} Play channel, protocol 1 (core ADR 0018
 * "Screen sharing"). Big-endian {@code u8 version, u8 kind, body}; every message
 * but ANSWER starts its body with the 16-byte {@link Id}. Pure: no Minecraft types.
 */
public final class ScreenProtocol {
    public static final String CHANNEL = "krasnokamen:screen";
    public static final int VERSION = 1;
    public static final int MAX_FRAME_BYTES = 131_072;
    public static final int MAX_PART = 30_000;
    public static final int MAX_PARTS = 5;
    public static final int MAX_EDGE = 640;
    public static final int MAX_OPUS_BYTES = 1000;
    /** Largest accepted server-to-client payload: header + id + frame header + H.264 picture. */
    public static final int MAX_PAYLOAD = MAX_FRAME_BYTES + 64;

    private static final int ID_BYTES = 16;

    private ScreenProtocol() {}

    /**
     * One screen rectangle on a monitor face. {@code x,y,z} is the minimum-coordinate
     * block; {@code side} is the face direction (2 N, 3 S, 4 W, 5 E); the rectangle
     * extends {@code width} blocks along +X (sides 2/3) or +Z (4/5) and {@code height}
     * blocks along +Y.
     */
    public record Id(int x, int y, int z, int side, int width, int height, int dimension)
            implements Comparable<Id> {
        public Id {
            if (side < 2 || side > 5 || width < 1 || width > 16 || height < 1 || height > 16
                    || dimension < 0 || dimension > 2) {
                throw new IllegalArgumentException("invalid shared screen identity");
            }
        }

        /** Horizontal axis index of the rectangle (0 = X, 2 = Z). */
        public int axis() {
            return side <= 3 ? 0 : 2;
        }

        public boolean contains(int blockX, int blockY, int blockZ, int faceSide) {
            if (faceSide != side) {
                return false;
            }
            int[] block = {blockX, blockY, blockZ};
            int[] origin = {x, y, z};
            for (int i = 0; i < 3; i++) {
                int length = i == axis() ? width : i == 1 ? height : 1;
                long offset = (long) block[i] - origin[i];
                if (offset < 0 || offset >= length) {
                    return false;
                }
            }
            return true;
        }

        void write(ByteBuffer out) {
            out.putInt(x).putInt(y).putInt(z).put((byte) side).put((byte) width)
                    .put((byte) height).put((byte) dimension);
        }

        static Id read(ByteBuffer in) {
            int x = in.getInt();
            int y = in.getInt();
            int z = in.getInt();
            return new Id(x, y, z, in.get() & 0xFF, in.get() & 0xFF, in.get() & 0xFF,
                    in.get() & 0xFF);
        }

        @Override
        public int compareTo(Id other) {
            return java.util.Comparator
                    .comparingInt(Id::x).thenComparingInt(Id::y).thenComparingInt(Id::z)
                    .thenComparingInt(Id::side).thenComparingInt(Id::width)
                    .thenComparingInt(Id::height).thenComparingInt(Id::dimension)
                    .compare(this, other);
        }
    }

    // ---- client -> server -------------------------------------------------------

    public static byte[] open(Id id) {
        return withId(0, id, 0, out -> {});
    }

    public static byte[] close(Id id) {
        return withId(1, id, 0, out -> {});
    }

    public static byte[] request(Id id) {
        return withId(3, id, 0, out -> {});
    }

    public static byte[] leave(Id id) {
        return withId(6, id, 0, out -> {});
    }

    public static byte[] revoke(Id id, byte[] viewer) {
        if (viewer.length != 16) {
            throw new IllegalArgumentException("viewer uuid must be 16 bytes");
        }
        return withId(5, id, 16, out -> out.put(viewer));
    }

    public static byte[] answer(long request, boolean allow) {
        ByteBuffer out = ByteBuffer.allocate(7);
        out.put((byte) VERSION).put((byte) 4).putInt((int) request).put((byte) (allow ? 1 : 0));
        return out.array();
    }

    public static byte[] keyRequest(Id id) {
        return withId(8, id, 0, out -> {});
    }

    /** FRAME_PART messages for one H.264 picture, each carrying the full header. */
    public static List<byte[]> frameParts(
            Id id, long sequence, int width, int height, boolean key, byte[] data) {
        if (data.length == 0 || data.length > MAX_FRAME_BYTES || width < 1 || height < 1
                || width > MAX_EDGE || height > MAX_EDGE) {
            throw new IllegalArgumentException("shared image size bound");
        }
        int count = (data.length + MAX_PART - 1) / MAX_PART;
        if (count > MAX_PARTS) {
            throw new IllegalArgumentException("shared image part bound");
        }
        List<byte[]> parts = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int from = index * MAX_PART;
            int length = Math.min(MAX_PART, data.length - from);
            ByteBuffer out = ByteBuffer.allocate(2 + ID_BYTES + 4 + 2 + 2 + 4 + length);
            out.put((byte) VERSION).put((byte) 2);
            id.write(out);
            out.putInt((int) sequence).putShort((short) width).putShort((short) height)
                    .put((byte) ScreenVideo.CODEC).put((byte) (key ? 1 : 0))
                    .put((byte) index).put((byte) count);
            out.put(data, from, length);
            parts.add(out.array());
        }
        return parts;
    }

    private interface Body {
        void write(ByteBuffer out);
    }

    private static byte[] withId(int kind, Id id, int extra, Body body) {
        ByteBuffer out = ByteBuffer.allocate(2 + ID_BYTES + extra);
        out.put((byte) VERSION).put((byte) kind);
        id.write(out);
        body.write(out);
        return out.array();
    }

    // ---- server -> client -------------------------------------------------------

    public sealed interface Message {
        Id id();
    }

    /** Owner: start (on) or stop (off) uploading frames of at most {@code maxWidth} px. */
    public record Demand(Id id, boolean on, int maxWidth, int fps) implements Message {}

    public record Frame(Id id, long sequence, int width, int height, boolean key, byte[] data)
            implements Message {}

    /** One 48 kHz mono, 20 ms Opus packet from a permitted screen owner. */
    public record Audio(Id id, long sequence, byte[] opus) implements Message {}

    /** Owner: the next picture must be a key frame. */
    public record KeyRequest(Id id) implements Message {}

    /** Reason 0 looked away, 1 owner closed, 2 revoked, 3 owner left. */
    public record Stop(Id id, int reason) implements Message {}

    /**
     * Reason 0 not allowed, 1 not a monitor, 2 out of reach, 3 too many, 4 taken,
     * 5 bad frame, 6 no such screen, 7 repeat/rate limited.
     */
    public record Refused(Id id, int reason) implements Message {}

    public record Requested(Id id, long request, byte[] viewer, String name) implements Message {}

    public record Permission(Id id, boolean allowed) implements Message {}

    public record Active(Id id, boolean on) implements Message {}

    /** Decodes one server-to-client payload; rejects anything malformed. */
    public static Message decode(byte[] payload) {
        if (payload.length < 2 + ID_BYTES || payload.length > MAX_PAYLOAD) {
            throw new IllegalArgumentException("shared screen payload bound");
        }
        try {
            ByteBuffer in = ByteBuffer.wrap(payload);
            if ((in.get() & 0xFF) != VERSION) {
                throw new IllegalArgumentException("unsupported shared screen version");
            }
            int kind = in.get() & 0xFF;
            Id id = Id.read(in);
            Message message = switch (kind) {
                case 0 -> {
                    boolean on = bool(in);
                    int width = in.getShort() & 0xFFFF;
                    int fps = in.get() & 0xFF;
                    if (width == 0 || width > MAX_EDGE || fps == 0 || fps > 5) {
                        throw new IllegalArgumentException("invalid shared screen demand");
                    }
                    yield new Demand(id, on, width, fps);
                }
                case 1 -> {
                    long sequence = in.getInt() & 0xFFFFFFFFL;
                    int width = in.getShort() & 0xFFFF;
                    int height = in.getShort() & 0xFFFF;
                    if ((in.get() & 0xFF) != ScreenVideo.CODEC) {
                        throw new IllegalArgumentException("unsupported screen image codec");
                    }
                    boolean key = bool(in);
                    byte[] data = new byte[in.remaining()];
                    in.get(data);
                    yield new Frame(id, sequence, width, height, key, data);
                }
                case 2 -> {
                    int reason = in.get() & 0xFF;
                    if (reason > 3) {
                        throw new IllegalArgumentException("invalid screen stop reason");
                    }
                    yield new Stop(id, reason);
                }
                case 3 -> {
                    int reason = in.get() & 0xFF;
                    if (reason > 7) {
                        throw new IllegalArgumentException("invalid screen refusal");
                    }
                    yield new Refused(id, reason);
                }
                case 4 -> {
                    long request = in.getInt() & 0xFFFFFFFFL;
                    byte[] viewer = new byte[16];
                    in.get(viewer);
                    byte[] name = new byte[in.get() & 0xFF];
                    in.get(name);
                    yield new Requested(id, request, viewer, viewerName(name));
                }
                case 5 -> new Permission(id, bool(in));
                case 6 -> new Active(id, bool(in));
                case 7 -> {
                    long sequence = in.getInt() & 0xFFFFFFFFL;
                    if (in.remaining() < 1 || in.remaining() > MAX_OPUS_BYTES) {
                        throw new IllegalArgumentException("screen audio packet bound");
                    }
                    byte[] opus = new byte[in.remaining()];
                    in.get(opus);
                    yield new Audio(id, sequence, opus);
                }
                case 8 -> new KeyRequest(id);
                default -> throw new IllegalArgumentException("unknown screen relay message");
            };
            if (in.hasRemaining()) {
                throw new IllegalArgumentException("trailing bytes in screen message");
            }
            return message;
        } catch (BufferUnderflowException error) {
            throw new IllegalArgumentException("truncated screen message", error);
        }
    }

    private static boolean bool(ByteBuffer in) {
        int value = in.get() & 0xFF;
        if (value > 1) {
            throw new IllegalArgumentException("invalid boolean");
        }
        return value == 1;
    }

    private static String viewerName(byte[] bytes) {
        try {
            String name = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            if (name.isEmpty() || bytes.length > 64 || name.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("invalid viewer name");
            }
            return name;
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException("invalid viewer name", error);
        }
    }
}
