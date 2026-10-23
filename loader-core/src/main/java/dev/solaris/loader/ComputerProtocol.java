package dev.solaris.loader;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Independent computer protocol 1; does not change Loader protocol 3. */
public final class ComputerProtocol {
    public static final String CHANNEL = "solaris:computer";
    public static final int MAX_INPUT = 4096;
    public static final int MAX_OUTPUT = 16384;
    private ComputerProtocol() {}

    public record Reply(int session, int kind, String text) {}

    private static ByteBuffer header(int session, int kind, int size) {
        if (session == 0) throw new IllegalArgumentException("Zero computer session");
        return ByteBuffer.allocate(6 + size).put((byte) 1).put((byte) kind).putInt(session);
    }
    public static byte[] open(int session, int x, int y, int z) {
        return header(session, 0, 12).putInt(x).putInt(y).putInt(z).array();
    }
    public static byte[] line(int session, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_INPUT || text.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("One plain-text line of at most 4096 UTF-8 bytes required");
        }
        return header(session, 1, bytes.length).put(bytes).array();
    }
    public static byte[] close(int session) { return header(session, 2, 0).array(); }
    public static Reply reply(byte[] bytes) {
        if (bytes.length < 6 || bytes.length > MAX_OUTPUT + 6) {
            throw new IllegalArgumentException("Invalid computer reply length");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        if (buffer.get() != 1) throw new IllegalArgumentException("Unsupported computer protocol");
        int kind = Byte.toUnsignedInt(buffer.get());
        int session = buffer.getInt();
        if (kind > 2 || session == 0) throw new IllegalArgumentException("Invalid computer reply header");
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).decode(buffer).toString();
            return new Reply(session, kind, text);
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException("Computer reply is not UTF-8", error);
        }
    }
}
