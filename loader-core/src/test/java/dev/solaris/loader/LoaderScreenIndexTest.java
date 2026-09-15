package dev.solaris.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The eight schema-2 widget types and their closed activation bounds. */
final class LoaderScreenIndexTest {
    private static final String CONTENT = "[\"views\"]";
    private static final String PERMISSIONS = "[\"present_views\"]";

    @TempDir
    Path cacheDirectory;

    @Test
    void everyImplementedWidgetTypeActivatesWithItsDeclaredValues() throws Exception {
        LoaderScreenDefinition screen = activate(
                """
                [{"type":"paged_table","id":"roster","columns":[
                   {"id":"name","label":"Name","align":"left","width_bucket":1},
                   {"id":"count","label":"Count","align":"right","width_bucket":4}]},
                 {"type":"tabs","id":"pages","entries":[{"id":"one","label":"One"}]},
                 {"type":"input_number","id":"amount","label":"Amount","min":1,"max":64,"step":1},
                 {"type":"input_text","id":"note","label":"Note","max_bytes":32},
                 {"type":"select_enum","id":"mode","label":"Mode",
                  "options":[{"id":"fast","label":"Fast"}]},
                 {"type":"resource_panel","id":"cost","label":"Cost",
                  "entries":[{"id":"ruby","label":"Ruby"}]},
                 {"type":"action_button","action_id":"confirm","label":"Confirm"},
                 {"type":"action_button","action_id":"locked","label":"Locked",
                  "enabled":false,"deny_reason":"Garrison is not yours"},
                 {"type":"world_marker","id":"anchor","label":"Anchor","action_id":"place",
                  "preview_id":"example:camp","formation":"wedge","radius":8}]
                """);

        assertEquals("Showcase", screen.title());
        assertEquals(LoaderScreenKind.SETTLEMENT, screen.kind());
        assertEquals(
                List.of(
                        new LoaderWidget.PagedTable(
                                "roster",
                                List.of(
                                        new LoaderWidget.Column(
                                                "name", "Name", LoaderWidget.Align.LEFT, 1),
                                        new LoaderWidget.Column(
                                                "count", "Count", LoaderWidget.Align.RIGHT, 4))),
                        new LoaderWidget.Tabs(
                                "pages", List.of(new LoaderWidget.Entry("one", "One"))),
                        new LoaderWidget.InputNumber(
                                "amount",
                                "Amount",
                                Optional.of(1.0),
                                Optional.of(64.0),
                                Optional.of(1.0)),
                        new LoaderWidget.InputText("note", "Note", 32),
                        new LoaderWidget.SelectEnum(
                                "mode", "Mode", List.of(new LoaderWidget.Entry("fast", "Fast"))),
                        new LoaderWidget.ResourcePanel(
                                "cost", "Cost", List.of(new LoaderWidget.Entry("ruby", "Ruby"))),
                        new LoaderWidget.ActionButton(
                                "confirm", "Confirm", true, Optional.empty()),
                        new LoaderWidget.ActionButton(
                                "locked", "Locked", false, Optional.of("Garrison is not yours")),
                        new LoaderWidget.WorldMarker(
                                "anchor",
                                "Anchor",
                                "place",
                                Optional.of("example:camp"),
                                Optional.of(LoaderFormation.WEDGE),
                                Optional.of(8.0))),
                screen.widgets());
        assertEquals(
                new LoaderWidget.Tabs("pages", List.of(new LoaderWidget.Entry("one", "One"))),
                screen.widget("pages").orElseThrow());
        assertEquals(Optional.empty(), screen.widget("missing"));
    }

    @Test
    void everyDeclaredScreenKindActivates() throws Exception {
        for (LoaderScreenKind kind : LoaderScreenKind.values()) {
            String widgets = """
                    [{"type":"paged_table","id":"roster","columns":[
                       {"id":"name","label":"Name","align":"center","width_bucket":2}]}]
                    """;
            byte[] archive = LoaderTestArchive.archive(index(kind.wireName(), widgets), Map.of());
            LoaderActivatedContent active = LoaderTestBundles.activate(
                    cacheDirectory,
                    archive,
                    CONTENT,
                    PERMISSIONS,
                    LoaderPermission.PRESENT_VIEWS);
            assertEquals(kind, active.screens().get("example:showcase").kind());
        }
    }

    @Test
    void eachWidgetCollectionBoundIsInclusiveAtItsLimit() throws Exception {
        assertEquals(
                16,
                ((LoaderWidget.PagedTable) activate(columns(16)).widgets().get(0)).columns().size());
        assertEquals(
                16,
                ((LoaderWidget.Tabs) activate(entries(16)).widgets().get(0)).entries().size());
        assertEquals(32, activate(widgets(32)).widgets().size());
        assertEquals(
                "x".repeat(LoaderWidget.MAX_LABEL_BYTES),
                ((LoaderWidget.InputText) activate("""
                        [{"type":"input_text","id":"note","label":"%s","max_bytes":256}]
                        """.formatted("x".repeat(LoaderWidget.MAX_LABEL_BYTES))).widgets().get(0))
                        .label());
        assertEquals(
                128.0,
                ((LoaderWidget.WorldMarker) activate(
                                """
                                [{"type":"world_marker","id":"anchor","label":"Anchor",
                                  "action_id":"place","radius":128}]
                                """)
                        .widgets()
                        .get(0))
                        .radius()
                        .orElseThrow());
    }

    @Test
    void unknownWidgetTypeAndUnknownScreenKindFailClosed() {
        assertScreenRejected(
                "settlement",
                """
                [{"type":"client_script","id":"roster"}]
                """);
        assertScreenRejected("village", """
                [{"type":"tabs","id":"pages","entries":[{"id":"one","label":"One"}]}]
                """);
    }

    @Test
    void outOfBoundWidgetGeometryAndValuesFailClosed() {
        assertScreenRejected("settlement", """
                [{"type":"paged_table","id":"roster","columns":[
                  {"id":"name","label":"Name","align":"middle","width_bucket":2}]}]
                """);
        assertScreenRejected("settlement", columns(17));
        for (int widthBucket : new int[] {0, 5}) {
            assertScreenRejected("settlement", """
                    [{"type":"paged_table","id":"roster","columns":[
                      {"id":"name","label":"Name","align":"left","width_bucket":%d}]}]
                    """.formatted(widthBucket));
        }
        assertScreenRejected("settlement", entries(17));
        assertScreenRejected("settlement", widgets(33));
        assertScreenRejected("settlement", """
                [{"type":"input_text","id":"note","label":"%s","max_bytes":8}]
                """.formatted("x".repeat(LoaderWidget.MAX_LABEL_BYTES + 1)));
        for (int maxBytes : new int[] {0, 257}) {
            assertScreenRejected("settlement", """
                    [{"type":"input_text","id":"note","label":"Note","max_bytes":%d}]
                    """.formatted(maxBytes));
        }
        assertScreenRejected("settlement", """
                [{"type":"input_number","id":"amount","label":"Amount","min":9,"max":1}]
                """);
        assertScreenRejected("settlement", """
                [{"type":"input_number","id":"amount","label":"Amount","step":0}]
                """);
        assertScreenRejected("settlement", """
                [{"type":"select_enum","id":"mode","label":"Mode","options":[]}]
                """);
        assertScreenRejected("settlement", """
                [{"type":"tabs","id":"pages","entries":[]}]
                """);
        assertScreenRejected("settlement", """
                [{"type":"resource_panel","id":"cost","label":"Cost","entries":[]}]
                """);
        assertScreenRejected("settlement", """
                [{"type":"world_marker","id":"anchor","label":"Anchor","action_id":"place",
                  "formation":"spiral"}]
                """);
        assertScreenRejected("settlement", """
                [{"type":"world_marker","id":"anchor","label":"Anchor","action_id":"place",
                  "radius":129}]
                """);
        assertScreenRejected("settlement", """
                [{"type":"action_button","action_id":"confirm","label":"Confirm","future":true}]
                """);
        assertScreenRejected("settlement", """
                [{"type":"tabs","id":"pages","entries":[{"id":"one","label":"One"}]},
                 {"type":"tabs","id":"pages","entries":[{"id":"two","label":"Two"}]}]
                """);
        assertScreenRejected("settlement", """
                [{"type":"tabs","id":"%s","entries":[{"id":"one","label":"One"}]}]
                """.formatted("x".repeat(LoaderJson.MAX_IDENTIFIER_BYTES + 1)));
        assertScreenRejected("settlement", """
                [{"id":"roster"}]
                """);
        assertScreenRejected("settlement", """
                [{"type":"action_button","action_id":"confirm"}]
                """);
    }

    @Test
    void previewGeometryIsBoundedInsideItsDeclaredBox() throws Exception {
        String content = "[\"world_previews\"]";
        String permissions = "[\"present_world_previews\"]";
        LoaderWorldPreviewDefinition preview = activatePreview("""
                {"id":"example:camp","blueprint_id":"example:camp","content_hash":"%s",
                 "rotation":2,"size_x":64,"size_y":64,"size_z":64,
                 "blocks":[{"x":63,"y":63,"z":63,"block_id":"minecraft:stone"}]}
                """.formatted("b".repeat(64)), content, permissions);
        assertEquals(2, preview.rotation());
        assertEquals(64, preview.sizeX());
        assertEquals(
                new LoaderWorldPreviewDefinition.Block(63, 63, 63, "minecraft:stone"),
                preview.blocks().get(0));

        for (String invalid : List.of(
                """
                {"id":"example:camp","blueprint_id":"example:camp","content_hash":"%s",
                 "rotation":4,"size_x":1,"size_y":1,"size_z":1,"blocks":[]}
                """.formatted("b".repeat(64)),
                """
                {"id":"example:camp","blueprint_id":"example:camp","content_hash":"%s",
                 "rotation":0,"size_x":65,"size_y":1,"size_z":1,"blocks":[]}
                """.formatted("b".repeat(64)),
                """
                {"id":"example:camp","blueprint_id":"example:camp","content_hash":"%s",
                 "rotation":0,"size_x":2,"size_y":2,"size_z":2,
                 "blocks":[{"x":2,"y":0,"z":0,"block_id":"minecraft:stone"}]}
                """.formatted("b".repeat(64)),
                """
                {"id":"example:camp","blueprint_id":"example:camp",
                 "content_hash":"%s","rotation":0,"size_x":2,"size_y":2,"size_z":2,"blocks":[]}
                """.formatted("B".repeat(64)),
                """
                {"id":"example:camp","blueprint_id":"camp","content_hash":"%s",
                 "rotation":0,"size_x":2,"size_y":2,"size_z":2,"blocks":[]}
                """.formatted("b".repeat(64)),
                """
                {"id":"other:camp","blueprint_id":"example:camp","content_hash":"%s",
                 "rotation":0,"size_x":2,"size_y":2,"size_z":2,"blocks":[]}
                """.formatted("b".repeat(64)),
                """
                {"id":"example:camp","blueprint_id":"example:camp","content_hash":"%s",
                 "rotation":0,"size_x":2,"size_y":2,"size_z":2,"future":true,"blocks":[]}
                """.formatted("b".repeat(64)))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> activatePreview(invalid, content, permissions));
        }
    }

    private static String index(String kind, String widgets) {
        return """
                {"schema":2,"screens":[{
                  "id":"example:showcase","kind":"%s","title":"Showcase","widgets":%s
                }],"world_previews":[],"blocks":[],"items":[],"assets":[],"sounds":[]}
                """.formatted(kind, widgets);
    }

    private static String columns(int count) {
        StringBuilder columns = new StringBuilder();
        for (int index = 0; index < count; index++) {
            columns.append(index == 0 ? "" : ",")
                    .append("{\"id\":\"column%d\",\"label\":\"Column %d\",\"align\":\"left\",\"width_bucket\":1}"
                            .formatted(index, index));
        }
        return "[{\"type\":\"paged_table\",\"id\":\"roster\",\"columns\":[%s]}]"
                .formatted(columns);
    }

    private static String entries(int count) {
        StringBuilder entries = new StringBuilder();
        for (int index = 0; index < count; index++) {
            entries.append(index == 0 ? "" : ",")
                    .append("{\"id\":\"entry%d\",\"label\":\"Entry %d\"}".formatted(index, index));
        }
        return "[{\"type\":\"tabs\",\"id\":\"pages\",\"entries\":[%s]}]".formatted(entries);
    }

    private static String widgets(int count) {
        StringBuilder widgets = new StringBuilder();
        for (int index = 0; index < count; index++) {
            widgets.append(index == 0 ? "" : ",")
                    .append("{\"type\":\"resource_panel\",\"id\":\"panel%d\",\"label\":\"Panel %d\","
                                    .formatted(index, index))
                    .append("\"entries\":[{\"id\":\"entry\",\"label\":\"Entry\"}]}");
        }
        return "[%s]".formatted(widgets);
    }

    private LoaderScreenDefinition activate(String widgets) throws Exception {
        byte[] archive = LoaderTestArchive.archive(index("settlement", widgets), Map.of());
        return LoaderTestBundles.activate(
                        cacheDirectory,
                        archive,
                        CONTENT,
                        PERMISSIONS,
                        LoaderPermission.PRESENT_VIEWS)
                .screens()
                .get("example:showcase");
    }

    private void assertScreenRejected(String kind, String widgets) {
        byte[] archive = LoaderTestArchive.archive(index(kind, widgets), Map.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    LoaderTestBundles.writeCache(cacheDirectory, archive);
                    new LoaderClientTransport()
                            .acceptManifest(
                                    LoaderTestBundles.manifest(archive, CONTENT, PERMISSIONS),
                                    LoaderTestBundles.environment(LoaderPermission.PRESENT_VIEWS),
                                    cacheDirectory);
                });
    }

    private LoaderWorldPreviewDefinition activatePreview(
            String preview,
            String content,
            String permissions) throws Exception {
        byte[] archive = LoaderTestArchive.archive(
                """
                {"schema":2,"screens":[],"world_previews":[%s],"blocks":[],"items":[],
                 "assets":[],"sounds":[]}
                """.formatted(preview),
                Map.of());
        return LoaderTestBundles.activate(
                        cacheDirectory,
                        archive,
                        content,
                        permissions,
                        LoaderPermission.PRESENT_WORLD_PREVIEWS)
                .worldPreviews()
                .get("example:camp");
    }
}
