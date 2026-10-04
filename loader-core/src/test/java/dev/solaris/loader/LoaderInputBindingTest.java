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
                true,
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
        assertEquals(List.of(), activate(true, INPUT_ONLY).inputBindings());
        assertEquals(
                List.of(),
                activate(true, INPUT_ONLY + ",\"input_bindings\":[]").inputBindings());
        assertEquals(
                List.of(),
                activate(
                                false,
                                INPUT_ONLY,
                                "[\"views\"]",
                                "[\"present_views\"]",
                                LoaderPermission.PRESENT_VIEWS)
                        .inputBindings());
        assertEquals(
                List.of(),
                activate(
                                false,
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
                activate(true, INPUT_ONLY + ",\"input_bindings\":" + bindings(8))
                        .inputBindings()
                        .size());
        assertRejected(true, INPUT_ONLY + ",\"input_bindings\":" + bindings(9));
    }

    @Test
    void onlyOverlayDeclarationsMayBindGameplayInput() {
        assertRejected(false, INPUT_ONLY + ",\"input_bindings\":" + bindings(1));
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
            assertRejected(true, INPUT_ONLY + ",\"input_bindings\":" + bindings);
        }
    }

    @Test
    void bindingsRequireViewActionsContentAndPermission() throws Exception {
        String declared = INPUT_ONLY + ",\"input_bindings\":" + bindings(1);

        assertRejected(
                true,
                declared,
                "[\"views\"]",
                PERMISSIONS,
                LoaderPermission.PRESENT_VIEWS,
                LoaderPermission.SEND_VIEW_ACTIONS);
        assertRejected(
                true,
                declared,
                CONTENT,
                "[\"present_views\"]",
                LoaderPermission.PRESENT_VIEWS);
        assertRejected(
                true,
                declared,
                "[\"views\"]",
                "[\"present_views\"]",
                LoaderPermission.PRESENT_VIEWS);
        assertEquals(1, activate(true, declared).inputBindings().size());
    }

    private static String index(boolean overlay, String fields) {
        return """
                {"schema":2,"screens":[{
                  "id":"example:input","overlay":%s,"title":"Input",%s
                }],"world_previews":[],"blocks":[],"items":[],"assets":[],"sounds":[]}
                """.formatted(overlay, fields);
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

    private LoaderScreenDefinition activate(boolean overlay, String fields) throws Exception {
        return activate(
                overlay,
                fields,
                CONTENT,
                PERMISSIONS,
                LoaderPermission.PRESENT_VIEWS,
                LoaderPermission.SEND_VIEW_ACTIONS);
    }

    private LoaderScreenDefinition activate(
            boolean overlay,
            String fields,
            String content,
            String permissions,
            LoaderPermission... granted) throws Exception {
        byte[] archive = LoaderTestArchive.archive(index(overlay, fields), Map.of());
        return LoaderTestBundles.activate(
                        cacheDirectory, archive, content, permissions, granted)
                .screens()
                .get("example:input");
    }

    private void assertRejected(boolean overlay, String fields) {
        assertRejected(
                overlay,
                fields,
                CONTENT,
                PERMISSIONS,
                LoaderPermission.PRESENT_VIEWS,
                LoaderPermission.SEND_VIEW_ACTIONS);
    }

    private void assertRejected(
            boolean overlay,
            String fields,
            String content,
            String permissions,
            LoaderPermission... granted) {
        byte[] archive = LoaderTestArchive.archive(index(overlay, fields), Map.of());
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
