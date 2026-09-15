package dev.solaris.loader;

import com.google.gson.JsonObject;
import java.util.Optional;
import java.util.Set;

/**
 * One decoded server-to-client Loader wire-3 view message. Unknown fields,
 * unknown messages, a protocol other than 3 and every out-of-bound value fail
 * closed; there is no lenient or legacy decoder.
 */
public sealed interface LoaderViewMessage {
    String CHANNEL = "solaris:loader/view";
    int MAX_PAYLOAD_BYTES = LoaderHandshake.MAX_VIEW_MESSAGE_BYTES;
    int MAX_TITLE_BYTES = LoaderScreenDefinition.MAX_TITLE_BYTES;

    Set<String> OPEN_FIELDS = Set.of(
            "protocol", "message", "view_instance_id", "revision", "view_id", "title", "model");
    Set<String> PRESENT_FIELDS =
            Set.of("protocol", "message", "view_instance_id", "revision", "model");
    Set<String> CLOSE_FIELDS = Set.of("protocol", "message", "view_instance_id");

    /** Open a declared screen as a new server-authoritative view instance. */
    record Open(
            String viewInstanceId,
            long revision,
            String viewId,
            String title,
            LoaderViewModel model) implements LoaderViewMessage {
    }

    /** Replace the model of an existing view instance and invalidate prior actions. */
    record Present(
            String viewInstanceId,
            long revision,
            LoaderViewModel model) implements LoaderViewMessage {
    }

    /** Close a view instance from the server side. */
    record Close(String viewInstanceId) implements LoaderViewMessage {
    }

    /**
     * Decode one closed-schema wire-3 message against the activated registry of
     * a live connection.
     */
    static Optional<LoaderViewMessage> decode(
            byte[] payload,
            LoaderActivatedContent content,
            boolean connectionActive) {
        if (!connectionActive || content == null) {
            return Optional.empty();
        }
        try {
            JsonObject document = LoaderJson.document(
                    payload, MAX_PAYLOAD_BYTES, "loader view message");
            LoaderJson.integer(
                    document,
                    "protocol",
                    LoaderHandshake.PROTOCOL_VERSION,
                    LoaderHandshake.PROTOCOL_VERSION,
                    "loader view protocol");
            String message = LoaderJson.nonEmpty(
                    document,
                    "message",
                    LoaderJson.MAX_IDENTIFIER_BYTES,
                    "loader view message name");
            return switch (message) {
                case "open_view" -> open(document, content);
                case "present_view" -> present(document);
                case "close_view" -> close(document);
                default -> Optional.empty();
            };
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
    }

    private static Optional<LoaderViewMessage> open(
            JsonObject document,
            LoaderActivatedContent content) {
        LoaderJson.rejectUnknown(document, OPEN_FIELDS, "loader open_view message");
        String viewInstanceId = LoaderJson.identifier(
                document, "view_instance_id", "loader view instance id");
        long revision = LoaderJson.integer(
                document, "revision", 0, Long.MAX_VALUE, "loader view revision");
        String viewId = LoaderJson.identifier(document, "view_id", "loader view id");
        if (!content.screens().containsKey(viewId)) {
            return Optional.empty();
        }
        String title = LoaderJson.nonEmpty(document, "title", MAX_TITLE_BYTES, "loader view title");
        return Optional.of(new Open(
                viewInstanceId,
                revision,
                viewId,
                title,
                model(document)));
    }

    private static Optional<LoaderViewMessage> present(JsonObject document) {
        LoaderJson.rejectUnknown(document, PRESENT_FIELDS, "loader present_view message");
        String viewInstanceId = LoaderJson.identifier(
                document, "view_instance_id", "loader view instance id");
        long revision = LoaderJson.integer(
                document, "revision", 0, Long.MAX_VALUE, "loader view revision");
        return Optional.of(new Present(viewInstanceId, revision, model(document)));
    }

    private static Optional<LoaderViewMessage> close(JsonObject document) {
        LoaderJson.rejectUnknown(document, CLOSE_FIELDS, "loader close_view message");
        return Optional.of(new Close(LoaderJson.identifier(
                document, "view_instance_id", "loader view instance id")));
    }

    private static LoaderViewModel model(JsonObject document) {
        return LoaderViewModel.parse(
                LoaderJson.object(document.get("model"), "loader view model"));
    }
}
