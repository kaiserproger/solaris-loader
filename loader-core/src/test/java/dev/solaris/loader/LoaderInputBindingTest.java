package dev.solaris.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The optional schema-2 screen input_bindings and their activation fences. */
final class LoaderInputBindingTest {
    private static final String CONTENT = "[\"views\",\"view_actions\"]";
    private static final String PERMISSIONS = "[\"present_views\",\"send_view_actions\"]";
    private static final String INPUT_ONLY = "\"widgets\":[]";

    @TempDir
    Path cacheDirectory;

    @Test
    void declaredHudInputContextActivatesWithTypedBindings() throws Exception {
        LoaderScreenDefinition screen = activate(
                "hud",
                INPUT_ONLY
                        + """
                        ,"input_bindings":[{
                           "key":"key.keyboard.g",
                           "press_action":"example:key_press",
                           "release_action":"example:key_release"
                         }, {
                           "key":"key.keyboard.left.shift",
                           "press_action":"example:sprint_press",
                           "release_action":"example:sprint_release"
                         }]
                        """);

        assertEquals(LoaderScreenKind.HUD, screen.kind());
        assertEquals(List.of(), screen.widgets());
        assertEquals(
                List.of(
                        new LoaderInputBinding(
                                "key.keyboard.g", "example:key_press", "example:key_release"),
                        new LoaderInputBinding(
                                "key.keyboard.left.shift",
                                "example:sprint_press",
                                "example:sprint_release")),
                screen.inputBindings());
        assertThrows(UnsupportedOperationException.class, screen.inputBindings()::clear);
    }

    @Test
    void absentOrEmptyBindingsDeclareNothingAtAll() throws Exception {
        assertEquals(List.of(), activate("hud", INPUT_ONLY).inputBindings());
        assertEquals(
                List.of(),
                activate("hud", INPUT_ONLY + ",\"input_bindings\":[]").inputBindings());
        assertEquals(
                List.of(),
                activate(
                                "settlement",
                                INPUT_ONLY,
                                "[\"views\"]",
                                "[\"present_views\"]",
                                LoaderPermission.PRESENT_VIEWS)
                        .inputBindings());
        assertEquals(
                List.of(),
                activate(
                                "settlement",
                                INPUT_ONLY + ",\"input_bindings\":[]",
                                "[\"views\"]",
                                "[\"present_views\"]",
                                LoaderPermission.PRESENT_VIEWS)
                        .inputBindings());
    }

    @Test
    void bindingCountBoundIsInclusiveAtItsLimit() throws Exception {
        assertEquals(
                LoaderInputBinding.MAX_BINDINGS_PER_SCREEN,
                activate("hud", INPUT_ONLY + ",\"input_bindings\":" + bindings(8))
                        .inputBindings()
                        .size());
        assertRejected("hud", INPUT_ONLY + ",\"input_bindings\":" + bindings(9));
    }

    @Test
    void onlyHudScreensMayDeclareBindings() {
        for (LoaderScreenKind kind : LoaderScreenKind.values()) {
            if (kind == LoaderScreenKind.HUD) {
                continue;
            }
            assertRejected(kind.wireName(), INPUT_ONLY + ",\"input_bindings\":" + bindings(1));
        }
    }

    @Test
    void malformedForeignAndDuplicateBindingsFailClosed() {
        for (String bindings : List.of(
                // Unknown binding field.
                """
                [{"key":"key.keyboard.g","press_action":"example:key_press",
                  "release_action":"example:key_release","future":true}]
                """,
                // Not a native keyboard name.
                """
                [{"key":"key.mouse.left","press_action":"example:key_press",
                  "release_action":"example:key_release"}]
                """,
                """
                [{"key":"g","press_action":"example:key_press",
                  "release_action":"example:key_release"}]
                """,
                // Not a canonical native name.
                """
                [{"key":"key.keyboard.Left.Shift","press_action":"example:key_press",
                  "release_action":"example:key_release"}]
                """,
                """
                [{"key":"key.keyboard.","press_action":"example:key_press",
                  "release_action":"example:key_release"}]
                """,
                """
                [{"key":"key.keyboard..g","press_action":"example:key_press",
                  "release_action":"example:key_release"}]
                """,
                """
                [{"key":"key.keyboard.g-","press_action":"example:key_press",
                  "release_action":"example:key_release"}]
                """,
                // Beyond the native name bound.
                """
                [{"key":"key.keyboard.%s","press_action":"example:key_press",
                  "release_action":"example:key_release"}]
                """.formatted("x".repeat(LoaderInputBinding.MAX_KEY_BYTES)),
                // Missing or untyped actions.
                "[{\"key\":\"key.keyboard.g\",\"press_action\":\"example:key_press\"}]",
                """
                [{"key":"key.keyboard.g","press_action":7,"release_action":"example:key_release"}]
                """,
                // Foreign or unqualified owner actions.
                """
                [{"key":"key.keyboard.g","press_action":"other:key_press",
                  "release_action":"example:key_release"}]
                """,
                """
                [{"key":"key.keyboard.g","press_action":"example:key_press",
                  "release_action":"key_release"}]
                """,
                // Repeated key or repeated action id.
                """
                [{"key":"key.keyboard.g","press_action":"example:key_press",
                  "release_action":"example:key_release"},
                 {"key":"key.keyboard.g","press_action":"example:other_press",
                  "release_action":"example:other_release"}]
                """,
                """
                [{"key":"key.keyboard.g","press_action":"example:key_press",
                  "release_action":"example:key_release"},
                 {"key":"key.keyboard.space","press_action":"example:key_press",
                  "release_action":"example:space_release"}]
                """,
                """
                [{"key":"key.keyboard.g","press_action":"example:key_press",
                  "release_action":"example:key_press"}]
                """,
                // Not an array of binding objects.
                "\"key.keyboard.g\"",
                "[\"key.keyboard.g\"]")) {
            assertRejected("hud", INPUT_ONLY + ",\"input_bindings\":" + bindings);
        }
    }

    @Test
    void bindingsRequireViewActionsContentAndPermission() throws Exception {
        String declared = INPUT_ONLY + ",\"input_bindings\":" + bindings(1);

        assertRejected(
                "hud",
                declared,
                "[\"views\"]",
                PERMISSIONS,
                LoaderPermission.PRESENT_VIEWS,
                LoaderPermission.SEND_VIEW_ACTIONS);
        assertRejected(
                "hud",
                declared,
                CONTENT,
                "[\"present_views\"]",
                LoaderPermission.PRESENT_VIEWS);
        assertRejected(
                "hud",
                declared,
                "[\"views\"]",
                "[\"present_views\"]",
                LoaderPermission.PRESENT_VIEWS);
        assertEquals(1, activate("hud", declared).inputBindings().size());
    }

    private static String index(String kind, String fields) {
        return """
                {"schema":2,"screens":[{
                  "id":"example:input","kind":"%s","title":"Input",%s
                }],"world_previews":[],"blocks":[],"items":[],"assets":[],"sounds":[]}
                """.formatted(kind, fields);
    }

    private static String bindings(int count) {
        StringBuilder bindings = new StringBuilder();
        for (int index = 0; index < count; index++) {
            bindings.append(index == 0 ? "" : ",")
                    .append("""
                            {"key":"key.keyboard.%s","press_action":"example:press_%d",
                             "release_action":"example:release_%d"}"""
                            .formatted((char) ('a' + index), index, index));
        }
        return "[%s]".formatted(bindings);
    }

    private LoaderScreenDefinition activate(String kind, String fields) throws Exception {
        return activate(
                kind,
                fields,
                CONTENT,
                PERMISSIONS,
                LoaderPermission.PRESENT_VIEWS,
                LoaderPermission.SEND_VIEW_ACTIONS);
    }

    private LoaderScreenDefinition activate(
            String kind,
            String fields,
            String content,
            String permissions,
            LoaderPermission... granted) throws Exception {
        byte[] archive = LoaderTestArchive.archive(index(kind, fields), Map.of());
        return LoaderTestBundles.activate(
                        cacheDirectory, archive, content, permissions, granted)
                .screens()
                .get("example:input");
    }

    private void assertRejected(String kind, String fields) {
        assertRejected(
                kind,
                fields,
                CONTENT,
                PERMISSIONS,
                LoaderPermission.PRESENT_VIEWS,
                LoaderPermission.SEND_VIEW_ACTIONS);
    }

    private void assertRejected(
            String kind,
            String fields,
            String content,
            String permissions,
            LoaderPermission... granted) {
        byte[] archive = LoaderTestArchive.archive(index(kind, fields), Map.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    LoaderTestBundles.writeCache(cacheDirectory, archive);
                    new LoaderClientTransport()
                            .acceptManifest(
                                    LoaderTestBundles.manifest(archive, content, permissions),
                                    LoaderTestBundles.environment(granted),
                                    cacheDirectory);
                });
    }
}
