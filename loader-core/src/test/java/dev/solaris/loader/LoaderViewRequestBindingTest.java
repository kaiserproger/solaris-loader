package dev.solaris.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The schema-2 overlay {@code view_request_bindings}: native keys that send the
 * wire-3 {@code view_request} for one exact declared screen, their activation
 * fences and the request the key produces.
 */
final class LoaderViewRequestBindingTest {
    private static final String CONTENT = "[\"views\",\"view_actions\"]";
    private static final String PERMISSIONS = "[\"present_views\",\"send_view_actions\"]";

    @TempDir
    Path cacheDirectory;

    @Test
    void declaredOverlayKeysActivateAsTypedRequestBindings() throws Exception {
        LoaderActivatedContent content = activate(index("example", """
                ,"view_request_bindings":[
                  {"key":"key.keyboard.c","view":"example:overview"},
                  {"key":"key.keyboard.left.shift","view":"example:keys"}
                ]"""));

        LoaderScreenDefinition keys = content.screens().get("example:keys");
        assertEquals(
                List.of(
                        new LoaderViewRequestBinding(
                                "key.keyboard.c", new LoaderViewKey("example:overview")),
                        new LoaderViewRequestBinding(
                                "key.keyboard.left.shift", new LoaderViewKey("example:keys"))),
                keys.requestBindings());
        assertThrows(UnsupportedOperationException.class, keys.requestBindings()::clear);
        assertEquals(List.of(), content.screens().get("example:overview").requestBindings());
    }

    @Test
    void pressingAnArmedKeyRequestsTheExactDeclaredScreen() throws Exception {
        LoaderActivatedContent content = activate(index("example", """
                ,"view_request_bindings":[{"key":"key.keyboard.c","view":"example:overview"}]"""));
        LoaderViewRequestBinding binding =
                content.screens().get("example:keys").requestBindings().get(0);

        JsonObject request = decode(
                LoaderViewActionRequest.request(binding.view(), content, true).orElseThrow());
        assertEquals(3, request.get("protocol").getAsInt());
        assertEquals("view_request", request.get("message").getAsString());
        assertEquals("example:overview", request.get("request_kind").getAsString());
        assertEquals(3, request.size());

        assertEquals(
                Optional.empty(), LoaderViewActionRequest.request(binding.view(), content, false));
        assertEquals(
                Optional.empty(),
                LoaderViewActionRequest.request(
                        new LoaderViewKey("example:undeclared"), content, true));
    }

    @Test
    void emptyOrAbsentRequestBindingsDeclareNothing() throws Exception {
        assertEquals(
                List.of(),
                activate(index("example", "")).screens().get("example:keys").requestBindings());
        assertEquals(
                List.of(),
                activate(index("example", ",\"view_request_bindings\":[]"))
                        .screens()
                        .get("example:keys")
                        .requestBindings());
    }

    @Test
    void onlyAnOverlayMayHostRequestBindings() {
        String declared = ",\"view_request_bindings\":[{\"key\":\"key.keyboard.c\","
                + "\"view\":\"example:overview\"}]";
        byte[] archive = LoaderTestArchive.archive(
                """
                {"schema":2,"screens":[
                  {"id":"example:overview","title":"Overview","widgets":[]%s}
                ],"world_previews":[],"blocks":[],"items":[],"assets":[],"sounds":[]}
                """.formatted(declared),
                Map.of());
        assertRejected(archive, CONTENT, PERMISSIONS);
    }

    @Test
    void malformedForeignAndConflictingRequestBindingsFailClosed() {
        for (String bindings : List.of(
                // The target must be a screen this same bundle declares.
                "[{\"key\":\"key.keyboard.c\",\"view\":\"example:missing\"}]",
                "[{\"key\":\"key.keyboard.c\",\"view\":\"other:overview\"}]",
                "[{\"key\":\"key.keyboard.c\",\"view\":\"overview\"}]",
                "[{\"key\":\"key.keyboard.c\"}]",
                "[{\"key\":\"key.keyboard.c\",\"view\":7}]",
                // Unknown field, wrong key spaces and malformed names.
                "[{\"key\":\"key.keyboard.c\",\"view\":\"example:overview\",\"future\":1}]",
                "[{\"key\":\"key.mouse.left\",\"view\":\"example:overview\"}]",
                "[{\"key\":\"c\",\"view\":\"example:overview\"}]",
                "[{\"key\":\"key.keyboard.C\",\"view\":\"example:overview\"}]",
                "[{\"key\":\"key.keyboard.\",\"view\":\"example:overview\"}]",
                // Repeated key, including against an input binding of the same screen.
                "[{\"key\":\"key.keyboard.c\",\"view\":\"example:overview\"},"
                        + "{\"key\":\"key.keyboard.c\",\"view\":\"example:keys\"}]",
                "[{\"key\":\"key.keyboard.g\",\"view\":\"example:overview\"}]",
                // Not an array of binding objects.
                "\"key.keyboard.c\"",
                "[\"key.keyboard.c\"]")) {
            byte[] archive = LoaderTestArchive.archive(
                    index("example", ",\"view_request_bindings\":" + bindings), Map.of());
            assertRejected(archive, CONTENT, PERMISSIONS);
        }
    }

    @Test
    void requestAndInputBindingsShareTheEightKeyBound() throws Exception {
        // Seven input bindings (the screen already declares one `g`) plus one request key = 8.
        StringBuilder seven = new StringBuilder();
        for (char key = 'a'; key < 'a' + 7; key++) {
            seven.append(seven.isEmpty() ? "" : ",")
                    .append("{\"key\":\"key.keyboard.%1$c\",\"press_action\":\"example:p_%1$c\","
                            .formatted(key))
                    .append("\"release_action\":\"example:r_%1$c\"}".formatted(key));
        }
        String inputs = ",\"input_bindings\":[" + seven + "]";
        String eighth = ",\"view_request_bindings\":[{\"key\":\"key.keyboard.z\","
                + "\"view\":\"example:overview\"}]";
        assertEquals(
                1,
                activate(overlayIndex(inputs + eighth))
                        .screens()
                        .get("example:keys")
                        .requestBindings()
                        .size());

        String ninth = ",\"view_request_bindings\":[{\"key\":\"key.keyboard.y\","
                + "\"view\":\"example:overview\"},{\"key\":\"key.keyboard.z\","
                + "\"view\":\"example:overview\"}]";
        assertRejected(
                LoaderTestArchive.archive(overlayIndex(inputs + ninth), Map.of()),
                CONTENT,
                PERMISSIONS);
    }

    @Test
    void requestBindingsRequireViewActionsContentAndPermission() throws Exception {
        String declared = index("example", """
                ,"view_request_bindings":[{"key":"key.keyboard.c","view":"example:overview"}]""");
        byte[] archive = LoaderTestArchive.archive(declared, Map.of());

        assertRejected(archive, "[\"views\"]", PERMISSIONS);
        assertRejected(archive, CONTENT, "[\"present_views\"]");
        assertEquals(
                1,
                activate(declared).screens().get("example:keys").requestBindings().size());
    }

    @Test
    void oneKeyCannotRequestScreensOfTwoActivatedBundles() throws Exception {
        String alpha = bundleIndex("alpha", "key.keyboard.c");
        String beta = bundleIndex("beta", "key.keyboard.c");
        assertThrows(
                IllegalArgumentException.class,
                () -> activateBundles(alpha, beta));

        LoaderActivatedContent distinct =
                activateBundles(alpha, bundleIndex("beta", "key.keyboard.v"));
        assertEquals(
                new LoaderViewKey("alpha:overview"),
                distinct.screens().get("alpha:keys").requestBindings().get(0).view());
        assertEquals(
                new LoaderViewKey("beta:overview"),
                distinct.screens().get("beta:keys").requestBindings().get(0).view());
    }

    private static String index(String owner, String keysFields) {
        return """
                {"schema":2,"screens":[
                  {"id":"%1$s:overview","title":"Overview","widgets":[]},
                  {"id":"%1$s:keys","overlay":true,"title":"Keys","widgets":[],
                   "input_bindings":[{"key":"key.keyboard.g",
                     "press_action":"%1$s:g_press","release_action":"%1$s:g_release"}]%2$s}
                ],"world_previews":[],"blocks":[],"items":[],"assets":[],"sounds":[]}
                """.formatted(owner, keysFields);
    }

    private static String overlayIndex(String keysFields) {
        return """
                {"schema":2,"screens":[
                  {"id":"example:overview","title":"Overview","widgets":[]},
                  {"id":"example:keys","overlay":true,"title":"Keys","widgets":[]%s}
                ],"world_previews":[],"blocks":[],"items":[],"assets":[],"sounds":[]}
                """.formatted(keysFields);
    }

    private static String bundleIndex(String owner, String key) {
        return """
                {"schema":2,"screens":[
                  {"id":"%1$s:overview","title":"Overview","widgets":[]},
                  {"id":"%1$s:keys","overlay":true,"title":"Keys","widgets":[],
                   "view_request_bindings":[{"key":"%2$s","view":"%1$s:overview"}]}
                ],"world_previews":[],"blocks":[],"items":[],"assets":[],"sounds":[]}
                """.formatted(owner, key);
    }

    private LoaderActivatedContent activate(String index) throws Exception {
        return LoaderTestBundles.activate(
                cacheDirectory,
                LoaderTestArchive.archive(index, Map.of()),
                CONTENT,
                PERMISSIONS,
                LoaderPermission.PRESENT_VIEWS,
                LoaderPermission.SEND_VIEW_ACTIONS);
    }

    private void assertRejected(byte[] archive, String content, String permissions) {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    LoaderTestBundles.writeCache(cacheDirectory, archive);
                    new LoaderClientTransport()
                            .acceptManifest(
                                    LoaderTestBundles.manifest(archive, content, permissions),
                                    LoaderTestBundles.environment(
                                            LoaderPermission.PRESENT_VIEWS,
                                            LoaderPermission.SEND_VIEW_ACTIONS),
                                    cacheDirectory);
                });
    }

    private LoaderActivatedContent activateBundles(String alphaIndex, String betaIndex)
            throws Exception {
        byte[] alpha = LoaderTestArchive.archive(alphaIndex, Map.of());
        byte[] beta = LoaderTestArchive.archive(betaIndex, Map.of());
        for (Object[] bundle : new Object[][] {{"alpha", alpha}, {"beta", beta}}) {
            byte[] archive = (byte[]) bundle[1];
            Path directory = cacheDirectory.resolve(bundle[0] + "/content/1");
            Files.createDirectories(directory);
            Files.write(
                    directory.resolve(LoaderTestArchive.sha256(archive) + ".bundle"), archive);
        }
        String manifest = """
                {"protocol":3,"bundles":[%s,%s]}
                """.formatted(bundleManifest("alpha", alpha), bundleManifest("beta", beta));
        return new LoaderClientTransport()
                .acceptManifest(
                        manifest.getBytes(StandardCharsets.UTF_8),
                        LoaderTestBundles.environment(
                                LoaderPermission.PRESENT_VIEWS,
                                LoaderPermission.SEND_VIEW_ACTIONS),
                        cacheDirectory)
                .activatedContent();
    }

    private static String bundleManifest(String owner, byte[] archive) {
        return """
                {"owner":"%1$s","id":"content","version":"1",
                 "artifact":"client/content.zip","sha256":"%2$s","size_bytes":%3$d,
                 "loaders":["fabric","neoforge","forge"],"content":%4$s,"permissions":%5$s,
                 "cache_key":"%1$s:content/1/%2$s"}
                """.formatted(
                        owner, LoaderTestArchive.sha256(archive), archive.length, CONTENT, PERMISSIONS);
    }

    private static JsonObject decode(byte[] payload) {
        assertTrue(payload.length > 0);
        return JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
