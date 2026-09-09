package dev.solaris.loader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class LoaderInteractionAction {
    public static final int MAX_INTERACTION_ID_BYTES = 128;
    public static final int MAX_INTERACTION_PAYLOAD_BYTES = 4 * 1024;
    public static final int MAX_PAYLOAD_BYTES =
            7 + MAX_INTERACTION_ID_BYTES + MAX_INTERACTION_PAYLOAD_BYTES;

    public enum Phase {
        TRIGGER(0),
        PRESS(1),
        RELEASE(2);

        private final int wireValue;

        Phase(int wireValue) {
            this.wireValue = wireValue;
        }
    }

    private LoaderInteractionAction() {
    }

    public static Optional<byte[]> encode(
            LoaderInteractionDefinition definition,
            Phase phase,
            LoaderActivatedContent content,
            boolean connectionActive) {
        if (!connectionActive
                || !definition.equals(content.interactions().get(definition.id()))
                || (phase == Phase.TRIGGER ? definition.uiId() == null : definition.key() == null)) {
            return Optional.empty();
        }
        byte[] id = definition.id().getBytes(StandardCharsets.UTF_8);
        byte[] payload = definition.payload().getBytes(StandardCharsets.UTF_8);
        if (id.length < 1
                || id.length > MAX_INTERACTION_ID_BYTES
                || payload.length > MAX_INTERACTION_PAYLOAD_BYTES) {
            return Optional.empty();
        }
        return Optional.of(ByteBuffer
                .allocate(7 + id.length + payload.length)
                .order(ByteOrder.BIG_ENDIAN)
                .putShort((short) LoaderHandshake.PROTOCOL_VERSION)
                .put((byte) phase.wireValue)
                .putShort((short) id.length)
                .put(id)
                .putShort((short) payload.length)
                .put(payload)
                .array());
    }
}
