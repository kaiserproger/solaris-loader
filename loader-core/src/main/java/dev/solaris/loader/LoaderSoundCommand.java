package dev.solaris.loader;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public record LoaderSoundCommand(
        LoaderSoundDefinition definition, int mode, float volume, float pitch, double x, double y, double z) {
    public static final String CHANNEL = "solaris:loader/sound";
    public static final int MAX_PAYLOAD_BYTES = 165;

    public static Optional<LoaderSoundCommand> resolve(
            byte[] payload, LoaderActivatedContent content, boolean connectionActive) {
        if (!connectionActive || payload.length < 6 || payload.length > MAX_PAYLOAD_BYTES) {
            return Optional.empty();
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        try {
            if (Short.toUnsignedInt(buffer.getShort()) != LoaderHandshake.PROTOCOL_VERSION) {
                return Optional.empty();
            }
            int mode = Byte.toUnsignedInt(buffer.get());
            if (mode > 2) { return Optional.empty(); }
            int length = Short.toUnsignedInt(buffer.getShort());
            if (length == 0 || length > 128 || length > buffer.remaining()) { return Optional.empty(); }
            String id = StandardCharsets.UTF_8.newDecoder().decode(buffer.slice(buffer.position(), length)).toString();
            buffer.position(buffer.position() + length);
            LoaderSoundDefinition definition = content.sounds().get(id);
            if (definition == null) { return Optional.empty(); }
            float volume = mode == 0 ? 0 : buffer.getFloat();
            float pitch = mode == 0 ? 1 : buffer.getFloat();
            if (!Float.isFinite(volume) || volume < 0 || volume > 1
                    || !Float.isFinite(pitch) || pitch < 0.5f || pitch > 2) { return Optional.empty(); }
            double x = mode == 2 ? buffer.getDouble() : 0;
            double y = mode == 2 ? buffer.getDouble() : 0;
            double z = mode == 2 ? buffer.getDouble() : 0;
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x) > 30_000_000 || Math.abs(y) > 20_000_000 || Math.abs(z) > 30_000_000
                    || buffer.hasRemaining()) { return Optional.empty(); }
            return Optional.of(new LoaderSoundCommand(definition, mode, volume, pitch, x, y, z));
        } catch (BufferUnderflowException | CharacterCodingException error) {
            return Optional.empty();
        }
    }
}
