package dev.solaris.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The wire-3 view message decoder and view action encoder. */
final class LoaderViewTest {
    private static final LoaderScreenDefinition SHOWCASE = new LoaderScreenDefinition(
            "example:showcase",
            LoaderScreenKind.SETTLEMENT,
            "Showcase",
            Optional.empty(),
            Optional.empty(),
            List.of());
    private static final LoaderActivatedContent CONTENT = new LoaderActivatedContent(
            List.of("example:content/1/hash"),
            Map.of("example:showcase", SHOWCASE),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of());

    @Test
    void openPresentAndCloseDecodeAgainstTheActivatedRegistry() {
        LoaderViewMessage.Open open = (LoaderViewMessage.Open) decode(open(modelJson())).orElseThrow();
        assertEquals("solaris:view-1", open.viewInstanceId());
        assertEquals(2, open.revision());
        assertEquals("example:showcase", open.viewId());
        assertEquals("Showcase", open.title());
        LoaderViewModel model = open.model();
        assertEquals(0, model.page());
        assertEquals(2, model.pageCount());
        assertEquals(
                List.of(
                        new LoaderViewModel.Row(List.of("Hamlet", "12")),
                        new LoaderViewModel.Row(List.of("Quarry", "3"))),
                model.rows());
        assertEquals(
                List.of(
                        LoaderViewModel.Field.ofNumber("amount", 4.0),
                        LoaderViewModel.Field.ofText("note", "hi"),
                        LoaderViewModel.Field.ofSelected("mode", "fast")),
                model.fields());
        assertEquals(
                List.of(
                        new LoaderViewModel.Action(
                                "example:confirm", true, Optional.of("Confirm"), Optional.empty()),
                        new LoaderViewModel.Action(
                                "example:place", false, Optional.empty(), Optional.of("No claim"))),
                model.actions());
        assertEquals(List.of(new LoaderViewModel.Tab("one", "One")), model.tabs());
        assertEquals(
                List.of(new LoaderViewModel.ResourceEntry("example:ruby", 3.0, 8.0)),
                model.resourceEntries());
        assertEquals(
                List.of(new LoaderViewModel.Marker(
                        "anchor",
                        Optional.of("example:ctx-1"),
                        Optional.of("example:place"),
                        Optional.of(LoaderFormation.WEDGE),
                        Optional.of(8.0))),
                model.markers());
        assertEquals("Waiting for orders", model.reason().orElseThrow());

        LoaderViewMessage.Present present = (LoaderViewMessage.Present) decode("""
                {"protocol":3,"message":"present_view","view_instance_id":"solaris:view-1",
                 "revision":3,"model":%s}
                """.formatted(modelJson()))
                .orElseThrow();
        assertEquals("solaris:view-1", present.viewInstanceId());
        assertEquals(3, present.revision());
        assertEquals(model, present.model());

        assertEquals(
                new LoaderViewMessage.Close("solaris:view-1"),
                decode("""
                        {"protocol":3,"message":"close_view","view_instance_id":"solaris:view-1"}
                        """).orElseThrow());
    }

    @Test
    void minimalModelAndBoundaryValuesAreAccepted() {
        LoaderViewModel minimal = model(
                "page", "1",
                "page_count", "2",
                "rows", "[]",
                "fields", "[]",
                "actions", "[]",
                "tabs", "[]",
                "resource_entries", "[]",
                "markers", "[{\"marker_id\":\"anchor\"}]",
                "reason", "\"\"");
        assertEquals(1, minimal.page());
        assertEquals("", minimal.reason().orElseThrow());
        assertEquals(
                new LoaderViewModel.Marker(
                        "anchor",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()),
                minimal.markers().get(0));
        assertEquals(
                128.0,
                model("markers", """
                        [{"marker_id":"anchor","radius":128}]
                        """).markers().get(0).radius().orElseThrow());
        assertEquals(
                LoaderViewModel.MAX_ROWS,
                model("rows", "[" + rows(LoaderViewModel.MAX_ROWS) + "]").rows().size());
        assertEquals(
                LoaderViewModel.MAX_FIELDS,
                model("fields", "[" + fields(LoaderViewModel.MAX_FIELDS) + "]").fields().size());
        assertEquals(
                LoaderViewModel.MAX_MARKERS,
                model("markers", "[" + markers(LoaderViewModel.MAX_MARKERS) + "]").markers().size());
        assertEquals(
                "x".repeat(LoaderViewModel.MAX_CELL_BYTES),
                model("rows", "[{\"cells\":[\"%s\"]}]"
                                .formatted("x".repeat(LoaderViewModel.MAX_CELL_BYTES)))
                        .rows()
                        .get(0)
                        .cells()
                        .get(0));
    }

    @Test
    void openRequiresADeclaredScreenOnALiveConnection() {
        assertTrue(decode(open(modelJson()).replace("example:showcase", "example:missing")).isEmpty());
        assertTrue(LoaderViewMessage.decode(
                        bytes(open(modelJson())), LoaderActivatedContent.empty(), true)
                .isEmpty());
        assertFalse(LoaderViewMessage.decode(bytes(open(modelJson())), CONTENT, true).isEmpty());
        assertTrue(LoaderViewMessage.decode(bytes(open(modelJson())), CONTENT, false).isEmpty());
    }

    @Test
    void protocolUnknownFieldsAndMessagesFailClosed() {
        for (String payload : List.of(
                open(modelJson()),
                """
                {"protocol":3,"message":"present_view","view_instance_id":"solaris:view-1",
                 "revision":1,"model":%s}
                """.formatted(modelJson()),
                """
                {"protocol":3,"message":"close_view","view_instance_id":"solaris:view-1"}
                """)) {
            assertFalse(decode(payload).isEmpty());
            assertTrue(
                    decode(payload.replace("\"protocol\":3", "\"protocol\":2")).isEmpty(),
                    "protocol 2 must fail closed");
        }
        assertTrue(decode("""
                {"protocol":3,"message":"open_view","view_instance_id":"solaris:view-1",
                 "revision":1,"view_id":"example:showcase","title":"Showcase","model":%s,
                 "future":true}
                """.formatted(modelJson())).isEmpty());
        assertTrue(decode("""
                {"protocol":3,"message":"present_view","view_instance_id":"solaris:view-1",
                 "revision":1,"model":%s,"title":"Showcase"}
                """.formatted(modelJson())).isEmpty());
        assertTrue(decode("""
                {"protocol":3,"message":"close_view","view_instance_id":"solaris:view-1",
                 "revision":1}
                """).isEmpty());
        assertTrue(decode("""
                {"protocol":3,"message":"open_ui","view_id":"example:showcase"}
                """).isEmpty());
        assertTrue(decode("""
                {"protocol":3,"view_instance_id":"solaris:view-1"}
                """).isEmpty());
        assertTrue(decode("""
                {"protocol":3.5,"message":"close_view","view_instance_id":"solaris:view-1"}
                """).isEmpty());
        assertTrue(decode("""
                {"protocol":"3","message":"close_view","view_instance_id":"solaris:view-1"}
                """).isEmpty());
        assertTrue(decode("[]").isEmpty());
        assertTrue(decode("not json").isEmpty());
        assertTrue(decode("").isEmpty());
        assertTrue(decode("""
                {"protocol":3,"message":"close_view","view_instance_id":""}
                """).isEmpty());
        assertTrue(decode("""
                {"protocol":3,"message":"close_view","view_instance_id":"%s"}
                """.formatted("x".repeat(LoaderJson.MAX_IDENTIFIER_BYTES + 1))).isEmpty());
        assertTrue(LoaderViewMessage.decode(
                        new byte[LoaderViewMessage.MAX_PAYLOAD_BYTES + 1], CONTENT, true)
                .isEmpty());
        byte[] invalidUtf8 = bytes(open(modelJson()));
        invalidUtf8[invalidUtf8.length - 2] = (byte) 0xff;
        assertTrue(LoaderViewMessage.decode(invalidUtf8, CONTENT, true).isEmpty());
    }

    @Test
    void modelBoundsAndTypingFailClosed() {
        for (String rejected : List.of(
                modelJson("page", "1.5"),
                modelJson("page_count", "0"),
                modelJson("page", "2"),
                modelJson("page", "3"),
                modelJson("page", "-1"),
                modelJson("future", "true"),
                modelJson("rows", "[{\"cells\":[\"Hamlet\",12]}]"),
                modelJson("rows", "[{\"cells\":[\"" + "x".repeat(LoaderViewModel.MAX_CELL_BYTES + 1) + "\"]}]"),
                modelJson("rows", "[{\"cells\":[%s]}]".formatted(cells(LoaderViewModel.MAX_CELLS_PER_ROW + 1))),
                modelJson("rows", "[{\"cells\":[],\"future\":true}]"),
                modelJson("rows", "[" + rows(LoaderViewModel.MAX_ROWS + 1) + "]"),
                modelJson("fields", "[" + fields(LoaderViewModel.MAX_FIELDS + 1) + "]"),
                modelJson("fields", "[{\"id\":\"amount\",\"number\":NaN}]"),
                modelJson("fields", "[{\"id\":\"amount\",\"number\":1e400}]"),
                modelJson("fields", "[{\"id\":\"amount\",\"number\":4,\"text\":\"hi\"}]"),
                modelJson("fields", "[{\"id\":\"amount\"}]"),
                modelJson("fields", "[{\"id\":\"note\",\"text\":\"%s\"}]"
                        .formatted("x".repeat(LoaderViewModel.MAX_TEXT_BYTES + 1))),
                modelJson("fields", "[{\"id\":\"amount\",\"number\":4,\"future\":true}]"),
                modelJson("fields", "[{\"id\":\"amount\",\"number\":4},{\"id\":\"amount\",\"text\":\"hi\"}]"),
                modelJson("fields", "[{\"id\":\"\",\"number\":4}]"),
                modelJson("actions", "[" + actions(LoaderViewModel.MAX_ACTIONS + 1) + "]"),
                modelJson("actions", "[{\"action_id\":\"example:confirm\",\"enabled\":\"true\"}]"),
                modelJson("actions", "[{\"action_id\":\"example:confirm\",\"label\":\"Confirm\"}]"),
                modelJson("actions", "[{\"action_id\":\"example:confirm\",\"enabled\":true,"
                        + "\"label\":\"%s\"}]".formatted("x".repeat(LoaderViewModel.MAX_LABEL_BYTES + 1))),
                modelJson("actions", "[{\"action_id\":\"example:confirm\",\"enabled\":true,"
                        + "\"deny_reason\":\"%s\"}]".formatted(
                                "x".repeat(LoaderViewModel.MAX_DENY_REASON_BYTES + 1))),
                modelJson("actions", "[{\"action_id\":\"example:confirm\",\"enabled\":true},"
                        + "{\"action_id\":\"example:confirm\",\"enabled\":false}]"),
                modelJson("tabs", "[" + tabs(LoaderViewModel.MAX_TABS + 1) + "]"),
                modelJson("tabs", "[{\"id\":\"one\",\"label\":\"\"}]"),
                modelJson("tabs", "[{\"id\":\"one\",\"label\":\"One\",\"future\":true}]"),
                modelJson("tabs", "[{\"id\":\"one\",\"label\":\"One\"},{\"id\":\"one\",\"label\":\"Two\"}]"),
                modelJson("resource_entries", "[" + resources(LoaderViewModel.MAX_RESOURCES + 1) + "]"),
                modelJson("resource_entries", "[{\"id\":\"example:ruby\",\"have\":-1,\"need\":8}]"),
                modelJson("resource_entries", "[{\"id\":\"example:ruby\",\"have\":3,\"need\":1e400}]"),
                modelJson("markers", "[" + markers(LoaderViewModel.MAX_MARKERS + 1) + "]"),
                modelJson("markers", "[{\"marker_id\":\"anchor\",\"radius\":129}]"),
                modelJson("markers", "[{\"marker_id\":\"anchor\",\"radius\":-1}]"),
                modelJson("markers", "[{\"marker_id\":\"anchor\",\"formation\":\"spiral\"}]"),
                modelJson("markers", "[{\"marker_id\":\"anchor\",\"selection_token\":\"example:ctx-1\"}]"),
                modelJson("markers", "[{\"marker_id\":\"anchor\",\"selection_token\":\"example:ctx-1\","
                        + "\"action_id\":\"example:place\"},{\"marker_id\":\"second\","
                        + "\"selection_token\":\"example:ctx-1\",\"action_id\":\"example:place\"}]"),
                modelJson("markers", "[{\"marker_id\":\"anchor\"},{\"marker_id\":\"anchor\"}]"),
                modelJson("reason", "\"%s\"".formatted("x".repeat(LoaderViewModel.MAX_REASON_BYTES + 1))))) {
            assertTrue(decode(open(rejected)).isEmpty(), rejected);
        }
    }

    @Test
    void viewActionCancelAndRequestEncodeClosedWireThree() {
        byte[] action = LoaderViewActionRequest.action(
                        "solaris:view-1",
                        7,
                        "example:place",
                        4,
                        List.of(
                                LoaderViewModel.Field.ofNumber("amount", 4.0),
                                LoaderViewModel.Field.ofSelected("mode", "fast")),
                        Optional.of("example:ctx-1"))
                .orElseThrow();
        JsonObject document = json(action);
        assertEquals(3, document.get("protocol").getAsInt());
        assertEquals("view_action", document.get("message").getAsString());
        assertEquals("solaris:view-1", document.get("view_instance_id").getAsString());
        assertEquals(7, document.get("view_revision").getAsLong());
        assertEquals("example:place", document.get("action_id").getAsString());
        assertEquals(4, document.get("action_sequence").getAsLong());
        assertEquals("example:ctx-1", document.get("selection_token").getAsString());
        JsonArray fields = document.getAsJsonArray("fields");
        assertEquals(2, fields.size());
        assertEquals("amount", fields.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals(4.0, fields.get(0).getAsJsonObject().get("number").getAsDouble());
        assertEquals("fast", fields.get(1).getAsJsonObject().get("selected").getAsString());

        byte[] bare = LoaderViewActionRequest.action(
                        "solaris:view-1", 7, "example:confirm", 1, List.of(), Optional.empty())
                .orElseThrow();
        assertTrue(new String(bare, StandardCharsets.UTF_8).contains("\"fields\":[]"));
        assertFalse(new String(bare, StandardCharsets.UTF_8).contains("selection_token"));

        JsonObject cancel = json(
                LoaderViewActionRequest.cancelSelection("example:ctx-1").orElseThrow());
        assertEquals(3, cancel.get("protocol").getAsInt());
        assertEquals("cancel_selection", cancel.get("message").getAsString());
        assertEquals("example:ctx-1", cancel.get("selection_context_id").getAsString());

        for (LoaderViewActionRequest.RequestKind kind : LoaderViewActionRequest.RequestKind.values()) {
            JsonObject request = json(LoaderViewActionRequest.request(kind).orElseThrow());
            assertEquals(3, request.get("protocol").getAsInt());
            assertEquals("view_request", request.get("message").getAsString());
            assertEquals(kind.wireName(), request.get("request_kind").getAsString());
        }
    }

    @Test
    void viewActionBoundsFailClosed() {
        List<LoaderViewModel.Field> tooMany = new ArrayList<>();
        for (int index = 0; index <= LoaderViewActionRequest.MAX_FIELDS; index++) {
            tooMany.add(LoaderViewModel.Field.ofNumber("field" + index, index));
        }
        assertTrue(LoaderViewActionRequest.action(
                        "solaris:view-1", 1, "example:confirm", 1, tooMany, Optional.empty())
                .isEmpty());
        for (LoaderViewModel.Field field : List.of(
                LoaderViewModel.Field.ofNumber("amount", Double.NaN),
                LoaderViewModel.Field.ofNumber("amount", Double.POSITIVE_INFINITY),
                LoaderViewModel.Field.ofText("note", "x".repeat(LoaderViewModel.MAX_TEXT_BYTES + 1)),
                LoaderViewModel.Field.ofSelected("mode", ""),
                LoaderViewModel.Field.ofText("", "value"),
                new LoaderViewModel.Field(
                        "amount", Optional.empty(), Optional.empty(), Optional.empty()),
                new LoaderViewModel.Field(
                        "amount", Optional.of(1.0), Optional.of("x"), Optional.empty()))) {
            assertTrue(LoaderViewActionRequest.action(
                            "solaris:view-1", 1, "example:confirm", 1, List.of(field), Optional.empty())
                    .isEmpty());
        }
        assertTrue(LoaderViewActionRequest.action(
                        "", 1, "example:confirm", 1, List.of(), Optional.empty())
                .isEmpty());
        assertTrue(LoaderViewActionRequest.action(
                        "solaris:view-1", 1, "", 1, List.of(), Optional.empty())
                .isEmpty());
        assertTrue(LoaderViewActionRequest.action(
                        "solaris:view-1", -1, "example:confirm", 1, List.of(), Optional.empty())
                .isEmpty());
        assertTrue(LoaderViewActionRequest.action(
                        "solaris:view-1", 1, "example:confirm", -1, List.of(), Optional.empty())
                .isEmpty());
        assertTrue(LoaderViewActionRequest.action(
                        "solaris:view-1",
                        1,
                        "example:confirm",
                        1,
                        List.of(LoaderViewModel.Field.ofText(
                                "note", "x".repeat(LoaderViewModel.MAX_TEXT_BYTES))),
                        Optional.empty())
                .isPresent());
        assertTrue(LoaderViewActionRequest.cancelSelection("").isEmpty());
        assertTrue(LoaderViewActionRequest.cancelSelection(
                        "x".repeat(LoaderJson.MAX_IDENTIFIER_BYTES + 1))
                .isEmpty());
        assertTrue(LoaderViewActionRequest.request(null).isEmpty());
    }

    /** The declared model members of a valid `open_view` payload. */
    private static Map<String, String> members() {
        Map<String, String> members = new LinkedHashMap<>();
        members.put("page", "0");
        members.put("page_count", "2");
        members.put("rows", "[{\"cells\":[\"Hamlet\",\"12\"]},{\"cells\":[\"Quarry\",\"3\"]}]");
        members.put(
                "fields",
                "[{\"id\":\"amount\",\"number\":4},{\"id\":\"note\",\"text\":\"hi\"},"
                        + "{\"id\":\"mode\",\"selected\":\"fast\"}]");
        members.put(
                "actions",
                "[{\"action_id\":\"example:confirm\",\"enabled\":true,\"label\":\"Confirm\"},"
                        + "{\"action_id\":\"example:place\",\"enabled\":false,"
                        + "\"deny_reason\":\"No claim\"}]");
        members.put("tabs", "[{\"id\":\"one\",\"label\":\"One\"}]");
        members.put("resource_entries", "[{\"id\":\"example:ruby\",\"have\":3,\"need\":8}]");
        members.put(
                "markers",
                "[{\"marker_id\":\"anchor\",\"selection_token\":\"example:ctx-1\","
                        + "\"action_id\":\"example:place\",\"formation\":\"wedge\",\"radius\":8}]");
        members.put("reason", "\"Waiting for orders\"");
        return members;
    }

    private static String modelJson(String... overrides) {
        Map<String, String> members = members();
        for (int index = 0; index < overrides.length; index += 2) {
            members.put(overrides[index], overrides[index + 1]);
        }
        StringBuilder document = new StringBuilder("{");
        members.forEach((field, value) -> document
                .append(document.length() == 1 ? "" : ",")
                .append('"')
                .append(field)
                .append("\":")
                .append(value));
        return document.append('}').toString();
    }

    private static LoaderViewModel model(String... overrides) {
        return decoded(modelJson(overrides));
    }

    private static Optional<LoaderViewMessage> decode(String payload) {
        return LoaderViewMessage.decode(bytes(payload), CONTENT, true);
    }

    private static String open(String modelJson) {
        return """
                {"protocol":3,"message":"open_view","view_instance_id":"solaris:view-1","revision":2,
                 "view_id":"example:showcase","title":"Showcase","model":%s}
                """.formatted(modelJson);
    }

    private static LoaderViewModel decoded(String modelJson) {
        return ((LoaderViewMessage.Open) decode(open(modelJson)).orElseThrow()).model();
    }

    private static JsonObject json(byte[] payload) {
        return new Gson().fromJson(new String(payload, StandardCharsets.UTF_8), JsonObject.class);
    }

    private static byte[] bytes(String payload) {
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    private static String cells(int count) {
        return joined(count, index -> "\"cell%d\"".formatted(index));
    }

    private static String rows(int count) {
        return joined(count, index -> "{\"cells\":[\"row%d\"]}".formatted(index));
    }

    private static String fields(int count) {
        return joined(count, index -> "{\"id\":\"field%d\",\"number\":%d}".formatted(index, index));
    }

    private static String actions(int count) {
        return joined(
                count, index -> "{\"action_id\":\"example:action%d\",\"enabled\":true}".formatted(index));
    }

    private static String tabs(int count) {
        return joined(count, index -> "{\"id\":\"tab%d\",\"label\":\"Tab %d\"}".formatted(index, index));
    }

    private static String resources(int count) {
        return joined(
                count, index -> "{\"id\":\"example:resource%d\",\"have\":1,\"need\":2}".formatted(index));
    }

    private static String markers(int count) {
        return joined(count, index -> "{\"marker_id\":\"marker%d\"}".formatted(index));
    }

    private static String joined(int count, java.util.function.IntFunction<String> entry) {
        StringBuilder values = new StringBuilder();
        for (int index = 0; index < count; index++) {
            values.append(index == 0 ? "" : ",").append(entry.apply(index));
        }
        return values.toString();
    }
}
