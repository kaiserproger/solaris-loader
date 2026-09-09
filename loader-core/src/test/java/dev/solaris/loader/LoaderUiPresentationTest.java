package dev.solaris.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class LoaderUiPresentationTest {
    private static final LoaderActivatedContent ACTIVE = new LoaderActivatedContent(List.of("example:ui/1/hash"), Map.of("example:welcome", new LoaderUiDefinition(
            "example:welcome", "Welcome", "Hello", Optional.empty(), Optional.empty())), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    @Test
    void screenDefaultsAndHudTextOverridesRemainDistinct() {
        LoaderUiPresentation screen = resolve(payload(0, null, null));
        assertEquals(LoaderUiPresentation.Mode.SCREEN, screen.mode());
        assertEquals("Welcome", screen.definition().title());
        assertEquals("Hello", screen.definition().body());

        LoaderUiPresentation hud = resolve(payload(1, "Status", "Health: 20"));
        assertEquals(LoaderUiPresentation.Mode.HUD, hud.mode());
        assertEquals("Status", hud.definition().title());
        assertEquals("Health: 20", hud.definition().body());

        LoaderUiPresentation clearedText = resolve(payload(1, "", ""));
        assertEquals("", clearedText.definition().title());
        assertEquals("", clearedText.definition().body());
    }

    @Test
    void hidingStillRequiresDeclaredUiOnALiveConnection() {
        byte[] payload = payload(2, null, null);
        assertEquals(LoaderUiPresentation.Mode.HIDDEN, resolve(payload).mode());
        assertTrue(LoaderUiPresentation.resolve(payload, ACTIVE, false).isEmpty());
        assertTrue(LoaderUiPresentation.resolve(
                payload, LoaderActivatedContent.empty(), true).isEmpty());
    }

    @Test
    void textLimitsCountUtf8BytesRatherThanCharacters() {
        String title = "é".repeat(64);
        String body = "x".repeat(LoaderUiPresentation.MAX_BODY_BYTES);
        assertEquals(title, resolve(payload(1, title, body)).definition().title());
        assertEquals(body, resolve(payload(1, title, body)).definition().body());
        assertTrue(LoaderUiPresentation.resolve(
                payload(1, title + "é", null), ACTIVE, true).isEmpty());
        assertTrue(LoaderUiPresentation.resolve(
                payload(1, null, body + "x"), ACTIVE, true).isEmpty());
    }

    @Test
    void malformedTruncatedAndTrailingPayloadsNeverResolve() {
        byte[] valid = payload(1, null, "x");
        assertTrue(LoaderUiPresentation.resolve(
                Arrays.copyOf(valid, valid.length - 1), ACTIVE, true).isEmpty());
        assertTrue(LoaderUiPresentation.resolve(
                Arrays.copyOf(valid, valid.length + 1), ACTIVE, true).isEmpty());
        byte[] invalidUtf8 = valid.clone();
        invalidUtf8[invalidUtf8.length - 1] = (byte) 0xff;
        assertTrue(LoaderUiPresentation.resolve(invalidUtf8, ACTIVE, true).isEmpty());
        assertTrue(LoaderUiPresentation.resolve(payload(3, null, null), ACTIVE, true).isEmpty());
        assertTrue(LoaderUiPresentation.resolve(
                new byte[] {0, 1, 0, 0, 2, 'x'}, ACTIVE, true).isEmpty());
        assertTrue(LoaderUiPresentation.resolve(
                new byte[LoaderUiPresentation.MAX_PAYLOAD_BYTES + 1], ACTIVE, true).isEmpty());
    }

    private static LoaderUiPresentation resolve(byte[] payload) {
        return LoaderUiPresentation.resolve(payload, ACTIVE, true).orElseThrow();
    }

    private static byte[] payload(int mode, String title, String body) {
        byte[] id = "example:welcome".getBytes(StandardCharsets.UTF_8);
        byte[] titleBytes = title == null ? null : title.getBytes(StandardCharsets.UTF_8);
        byte[] bodyBytes = body == null ? null : body.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(
                        9 + id.length
                                + (titleBytes == null ? 0 : titleBytes.length)
                                + (bodyBytes == null ? 0 : bodyBytes.length))
                .order(ByteOrder.BIG_ENDIAN)
                .putShort((short) LoaderHandshake.PROTOCOL_VERSION)
                .put((byte) mode)
                .putShort((short) id.length)
                .put(id);
        for (byte[] text : new byte[][] {titleBytes, bodyBytes}) {
            buffer.putShort(text == null ? (short) 0xffff : (short) text.length);
            if (text != null) {
                buffer.put(text);
            }
        }
        return buffer.array();
    }
}
