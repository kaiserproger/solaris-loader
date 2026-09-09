package dev.solaris.loader;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public record LoaderUiPresentation(Mode mode, LoaderUiDefinition definition) {
    public static final String CHANNEL = "solaris:loader/ui";
    public static final int MAX_UI_ID_BYTES = 128;
    public static final int MAX_TITLE_BYTES = 128;
    public static final int MAX_BODY_BYTES = 8 * 1024;
    public static final int MAX_PAYLOAD_BYTES =
            9 + MAX_UI_ID_BYTES + MAX_TITLE_BYTES + MAX_BODY_BYTES;

    public enum Mode {
        SCREEN,
        HUD,
        HIDDEN
    }

    public static Optional<LoaderUiPresentation> resolve(
            byte[] payload,
            LoaderActivatedContent content,
            boolean connectionActive) {
        if (!connectionActive || payload.length < 10 || payload.length > MAX_PAYLOAD_BYTES) {
            return Optional.empty();
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        if (Short.toUnsignedInt(buffer.getShort()) != LoaderHandshake.PROTOCOL_VERSION) {
            return Optional.empty();
        }
        Mode mode = switch (buffer.get()) {
            case 0 -> Mode.SCREEN;
            case 1 -> Mode.HUD;
            case 2 -> Mode.HIDDEN;
            default -> null;
        };
        if (mode == null) {
            return Optional.empty();
        }
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            String id = text(buffer, decoder, MAX_UI_ID_BYTES, false);
            LoaderUiDefinition definition = content.ui().get(id);
            if (definition == null) {
                return Optional.empty();
            }
            String title = text(buffer, decoder, MAX_TITLE_BYTES, true);
            String body = text(buffer, decoder, MAX_BODY_BYTES, true);
            if (buffer.hasRemaining()) {
                return Optional.empty();
            }
            if (title != null || body != null) {
                definition = new LoaderUiDefinition(
                        definition.id(),
                        title == null ? definition.title() : title,
                        body == null ? definition.body() : body,
                        definition.itemId(),
                        definition.blockId());
            }
            return Optional.of(new LoaderUiPresentation(mode, definition));
        } catch (BufferUnderflowException | IllegalArgumentException | CharacterCodingException error) {
            return Optional.empty();
        }
    }

    private static String text(
            ByteBuffer buffer,
            CharsetDecoder decoder,
            int maxBytes,
            boolean optional) throws CharacterCodingException {
        int length = Short.toUnsignedInt(buffer.getShort());
        if (optional && length == 0xffff) {
            return null;
        }
        if ((!optional && length == 0) || length > maxBytes || length > buffer.remaining()) {
            throw new IllegalArgumentException("invalid Loader UI text length");
        }
        int position = buffer.position();
        String text = decoder.decode(buffer.slice(position, length)).toString();
        buffer.position(position + length);
        return text;
    }
}
