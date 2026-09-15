package dev.solaris.loader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * The client-to-server Loader wire-3 side of the view contract: one
 * `view_action`, one `cancel_selection` and the key-driven `view_request`.
 * Every encoded message is closed, bounded and fails closed at protocol 3.
 */
public final class LoaderViewActionRequest {
    public static final String CHANNEL = "solaris:loader/view_action";
    public static final int MAX_PAYLOAD_BYTES = LoaderHandshake.MAX_VIEW_MESSAGE_BYTES;
    public static final int MAX_FIELDS = LoaderViewModel.MAX_FIELDS;

    private static final Gson GSON = new Gson();

    private LoaderViewActionRequest() {
    }

    /** The two declared kinds of key-driven view request. */
    public enum RequestKind {
        SETTLEMENT("settlement"),
        ARMY("army");

        private final String wireName;

        RequestKind(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }
    }

    public static Optional<byte[]> action(
            String viewInstanceId,
            long viewRevision,
            String actionId,
            long actionSequence,
            List<LoaderViewModel.Field> fields,
            Optional<String> selectionToken) {
        if (fields == null || fields.size() > MAX_FIELDS || selectionToken == null) {
            return Optional.empty();
        }
        if (viewRevision < 0 || actionSequence < 0) {
            return Optional.empty();
        }
        if (!identifier(viewInstanceId) || !identifier(actionId)) {
            return Optional.empty();
        }
        JsonArray encoded = new JsonArray();
        for (LoaderViewModel.Field field : fields) {
            Optional<JsonObject> value = encodeField(field);
            if (value.isEmpty()) {
                return Optional.empty();
            }
            encoded.add(value.orElseThrow());
        }
        JsonObject message = envelope("view_action");
        message.addProperty("view_instance_id", viewInstanceId);
        message.addProperty("view_revision", viewRevision);
        message.addProperty("action_id", actionId);
        message.addProperty("action_sequence", actionSequence);
        message.add("fields", encoded);
        if (selectionToken.isPresent()) {
            String token = selectionToken.orElseThrow();
            if (!identifier(token)) {
                return Optional.empty();
            }
            message.addProperty("selection_token", token);
        }
        return payload(message);
    }

    public static Optional<byte[]> cancelSelection(String selectionContextId) {
        if (!identifier(selectionContextId)) {
            return Optional.empty();
        }
        JsonObject message = envelope("cancel_selection");
        message.addProperty("selection_context_id", selectionContextId);
        return payload(message);
    }

    public static Optional<byte[]> request(RequestKind kind) {
        if (kind == null) {
            return Optional.empty();
        }
        JsonObject message = envelope("view_request");
        message.addProperty("request_kind", kind.wireName());
        return payload(message);
    }

    private static JsonObject envelope(String message) {
        JsonObject document = new JsonObject();
        document.addProperty("protocol", LoaderHandshake.PROTOCOL_VERSION);
        document.addProperty("message", message);
        return document;
    }

    private static Optional<byte[]> payload(JsonObject document) {
        byte[] payload = GSON.toJson(document).getBytes(StandardCharsets.UTF_8);
        return payload.length > MAX_PAYLOAD_BYTES ? Optional.empty() : Optional.of(payload);
    }

    private static Optional<JsonObject> encodeField(LoaderViewModel.Field field) {
        if (field == null || !identifier(field.id()) || field.typedValues() != 1) {
            return Optional.empty();
        }
        JsonObject encoded = new JsonObject();
        encoded.addProperty("id", field.id());
        if (field.number().isPresent()) {
            double number = field.number().orElseThrow();
            if (!Double.isFinite(number)) {
                return Optional.empty();
            }
            encoded.addProperty("number", number);
        } else if (field.text().isPresent()) {
            String text = field.text().orElseThrow();
            if (text.getBytes(StandardCharsets.UTF_8).length > LoaderViewModel.MAX_TEXT_BYTES) {
                return Optional.empty();
            }
            encoded.addProperty("text", text);
        } else {
            String selected = field.selected().orElseThrow();
            if (!identifier(selected)) {
                return Optional.empty();
            }
            encoded.addProperty("selected", selected);
        }
        return Optional.of(encoded);
    }

    private static boolean identifier(String value) {
        if (value == null) {
            return false;
        }
        int length = value.getBytes(StandardCharsets.UTF_8).length;
        return length >= 1 && length <= LoaderJson.MAX_IDENTIFIER_BYTES;
    }
}
