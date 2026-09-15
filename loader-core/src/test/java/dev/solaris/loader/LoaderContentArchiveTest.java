package dev.solaris.loader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LoaderContentArchiveTest {
    @TempDir
    Path cacheDirectory;

    @Test
    void verifiedArchiveActivatesClosedScreensAndAssetsBeforeAck() throws Exception {
        byte[] asset = "logo".getBytes(StandardCharsets.UTF_8);
        byte[] archive = LoaderTestArchive.screenAndAsset(asset);
        LoaderTestBundles.writeCache(cacheDirectory, archive);

        LoaderOutgoing outgoing = new LoaderClientTransport().acceptManifest(
                LoaderTestBundles.manifest(archive, "[\"views\",\"assets\"]", "[\"present_views\",\"load_assets\"]"),
                LoaderTestBundles.environment(
                        LoaderPermission.PRESENT_VIEWS,
                        LoaderPermission.LOAD_ASSETS),
                cacheDirectory);

        assertEquals(LoaderOutgoing.Kind.ACKNOWLEDGEMENT, outgoing.kind());
        LoaderActivatedContent active = outgoing.activatedContent();
        LoaderScreenDefinition screen = active.screens().get("example:welcome");
        assertEquals("Welcome", screen.title());
        assertEquals(LoaderScreenKind.SETTLEMENT, screen.kind());
        assertEquals(
                List.of(new LoaderWidget.Tabs(
                        "pages", List.of(new LoaderWidget.Entry("one", "One")))),
                screen.widgets());
        assertArrayEquals(asset, active.assets().get("example:logo").bytes());
        assertEquals(1, active.cacheKeys().size());
        byte[] returnedBytes = active.assets().get("example:logo").bytes();
        returnedBytes[0] = 'X';
        assertArrayEquals(asset, active.assets().get("example:logo").bytes());
        assertThrows(UnsupportedOperationException.class, active.assets()::clear);
        assertThrows(UnsupportedOperationException.class, active.screens()::clear);
        assertTrue(new String(outgoing.bytes(), StandardCharsets.UTF_8)
                .contains("\"protocol\":3"));
    }

    @Test
    void itemScreenActivatesOnlyWithItsOwnedDefinitionAsset() throws Exception {
        LoaderActivatedContent active = LoaderTestBundles.activate(
                cacheDirectory,
                LoaderTestArchive.screenAndItem(),
                "[\"views\",\"items\",\"assets\"]",
                "[\"present_views\",\"register_items\",\"load_assets\"]",
                LoaderPermission.PRESENT_VIEWS,
                LoaderPermission.REGISTER_ITEMS,
                LoaderPermission.LOAD_ASSETS);

        assertEquals("minecraft:paper", active.items().get("example:ruby").baseItem());
        assertEquals("Ruby", active.items().get("example:ruby").name());
        assertEquals(
                "example:ruby",
                active.screens().get("example:catalog").itemId().orElseThrow());
        assertTrue(active.assets().containsKey("example:ruby_definition"));
        assertThrows(UnsupportedOperationException.class, active.items()::clear);

        byte[] missingDefinition = LoaderTestArchive.archive(
                """
                {"schema":2,"screens":[{
                  "id":"example:catalog","kind":"economy","title":"Catalog",
                  "item_id":"example:ruby","widgets":[]}],
                "world_previews":[],"blocks":[],"items":[{
                  "id":"example:ruby","base_item":"minecraft:paper","name":"Ruby"
                }],"assets":[],"sounds":[]}
                """,
                Map.of());
        LoaderTestBundles.writeCache(cacheDirectory, missingDefinition);
        assertThrows(
                IllegalArgumentException.class,
                () -> new LoaderClientTransport().acceptManifest(
                        LoaderTestBundles.manifest(missingDefinition, "[\"items\"]", "[\"register_items\"]"),
                        LoaderTestBundles.environment(LoaderPermission.REGISTER_ITEMS),
                        cacheDirectory));
    }

    @Test
    void blockScreenBindsItsOwnedModelAndCarrierState() throws Exception {
        byte[] archive = LoaderTestArchive.screenAndBlock();
        String content = "[\"views\",\"blocks\",\"assets\"]";
        String permissions = "[\"present_views\",\"register_blocks\",\"load_assets\"]";
        LoaderActivatedContent active = LoaderTestBundles.activate(
                cacheDirectory,
                archive,
                content,
                permissions,
                LoaderPermission.PRESENT_VIEWS,
                LoaderPermission.REGISTER_BLOCKS,
                LoaderPermission.LOAD_ASSETS);

        assertEquals("example:block/ruby_block", active.blocks().get("example:ruby_block").model());
        assertEquals(
                "example:ruby_block",
                active.screens().get("example:catalog").blockId().orElseThrow());
        assertTrue(new String(
                        LoaderHandshake.acknowledgement(
                                LoaderHandshake.inspectManifest(
                                        LoaderTestBundles.manifest(archive, content, permissions),
                                        LoaderPlatform.FABRIC,
                                        "0.1.0"),
                                LoaderTestBundles.environment(
                                        LoaderPermission.PRESENT_VIEWS,
                                        LoaderPermission.REGISTER_BLOCKS,
                                        LoaderPermission.LOAD_ASSETS),
                                active),
                        StandardCharsets.UTF_8)
                .contains("\"carrier_block_state_ids\":{\"example:ruby_block\":321}"));

        byte[] missingModel = LoaderTestArchive.archive(
                """
                {"schema":2,"screens":[],"world_previews":[],"blocks":[{
                  "id":"example:ruby_block","model":"example:block/ruby_block",
                  "name":"Ruby Block"
                }],"items":[],"assets":[],"sounds":[]}
                """,
                Map.of());
        LoaderTestBundles.writeCache(cacheDirectory, missingModel);
        assertThrows(
                IllegalArgumentException.class,
                () -> new LoaderClientTransport().acceptManifest(
                        LoaderTestBundles.manifest(missingModel, "[\"blocks\"]", "[\"register_blocks\"]"),
                        LoaderTestBundles.environment(LoaderPermission.REGISTER_BLOCKS),
                        cacheDirectory));
    }

    @Test
    void screensMayOnlyReferenceTheirOwnDeclaredItemAndBlock() throws Exception {
        byte[] undeclaredBlock = LoaderTestArchive.archive(
                """
                {"schema":2,"screens":[{
                  "id":"example:catalog","kind":"construction","title":"Catalog",
                  "block_id":"example:missing","widgets":[]}],
                "world_previews":[],"blocks":[{
                  "id":"example:ruby_block","model":"example:block/ruby_block",
                  "name":"Ruby Block"
                }],"items":[],"assets":[{
                  "id":"example:ruby_block_model",
                  "path":"assets/example/models/block/ruby_block.json",
                  "sha256":"%s","size_bytes":2
                }],"sounds":[]}
                """.formatted(LoaderTestArchive.sha256("{}".getBytes(StandardCharsets.UTF_8))),
                Map.of(
                        "assets/example/models/block/ruby_block.json",
                        "{}".getBytes(StandardCharsets.UTF_8)));
        LoaderTestBundles.writeCache(cacheDirectory, undeclaredBlock);
        assertThrows(
                IllegalArgumentException.class,
                () -> new LoaderClientTransport().acceptManifest(
                        LoaderTestBundles.manifest(
                                undeclaredBlock,
                                "[\"views\",\"blocks\",\"assets\"]",
                                "[\"present_views\",\"register_blocks\",\"load_assets\"]"),
                        LoaderTestBundles.environment(
                                LoaderPermission.PRESENT_VIEWS,
                                LoaderPermission.REGISTER_BLOCKS,
                                LoaderPermission.LOAD_ASSETS),
                        cacheDirectory));

        byte[] foreignBlock = LoaderTestArchive.archive(
                """
                {"schema":2,"screens":[],"world_previews":[],"blocks":[{
                  "id":"other:ruby_block","model":"example:block/ruby_block",
                  "name":"Ruby Block"
                }],"items":[],"assets":[],"sounds":[]}
                """,
                Map.of());
        LoaderTestBundles.writeCache(cacheDirectory, foreignBlock);
        assertThrows(
                IllegalArgumentException.class,
                () -> new LoaderClientTransport().acceptManifest(
                        LoaderTestBundles.manifest(foreignBlock, "[\"blocks\"]", "[\"register_blocks\"]"),
                        LoaderTestBundles.environment(LoaderPermission.REGISTER_BLOCKS),
                        cacheDirectory));
    }

    @Test
    void worldPreviewsRequireMatchingContentAndPermission() throws Exception {
        String content = "[\"world_previews\"]";
        String permissions = "[\"present_world_previews\"]";
        byte[] preview = LoaderTestArchive.archive(
                """
                {"schema":2,"screens":[],"world_previews":[{
                  "id":"example:camp","blueprint_id":"example:camp",
                  "content_hash":"%s","rotation":3,"size_x":4,"size_y":2,"size_z":1,
                  "blocks":[{"x":0,"y":0,"z":0,"block_id":"minecraft:oak_planks"},
                            {"x":3,"y":1,"z":0,"block_id":"example:ruby_block"}]
                }],"blocks":[],"items":[],"assets":[],"sounds":[]}
                """.formatted("a".repeat(64)),
                Map.of());
        LoaderActivatedContent active = LoaderTestBundles.activate(
                cacheDirectory,
                preview,
                content,
                permissions,
                LoaderPermission.PRESENT_WORLD_PREVIEWS);
        LoaderWorldPreviewDefinition definition = active.worldPreviews().get("example:camp");
        assertEquals("example:camp", definition.blueprintId());
        assertEquals("a".repeat(64), definition.contentHash());
        assertEquals(3, definition.rotation());
        assertEquals(
                new LoaderWorldPreviewDefinition.Block(0, 0, 0, "minecraft:oak_planks"),
                definition.blocks().get(0));
        assertEquals(2, definition.blocks().size());

        byte[] undeclared = LoaderTestArchive.archive(
                """
                {"schema":2,"screens":[],"world_previews":[],"blocks":[],"items":[],
                 "assets":[],"sounds":[]}
                """,
                Map.of());
        LoaderTestBundles.writeCache(cacheDirectory, undeclared);
        assertThrows(
                IllegalArgumentException.class,
                () -> new LoaderClientTransport().acceptManifest(
                        LoaderTestBundles.manifest(undeclared, content, permissions),
                        LoaderTestBundles.environment(LoaderPermission.PRESENT_WORLD_PREVIEWS),
                        cacheDirectory));

        LoaderTestBundles.writeCache(cacheDirectory, preview);
        assertThrows(
                IllegalArgumentException.class,
                () -> new LoaderClientTransport().acceptManifest(
                        LoaderTestBundles.manifest(preview, content, "[\"load_assets\"]"),
                        LoaderTestBundles.environment(LoaderPermission.LOAD_ASSETS),
                        cacheDirectory));
    }

    @Test
    void screenIndexRequiresItsContentDeclaration() throws Exception {
        byte[] undeclaredScreens = LoaderTestArchive.archive(
                """
                {"schema":2,"screens":[{
                  "id":"example:welcome","kind":"settlement","title":"Welcome","widgets":[]
                }],"world_previews":[],"blocks":[],"items":[],"assets":[],"sounds":[]}
                """,
                Map.of());
        LoaderTestBundles.writeCache(cacheDirectory, undeclaredScreens);
        assertThrows(
                IllegalArgumentException.class,
                () -> new LoaderClientTransport().acceptManifest(
                        LoaderTestBundles.manifest(undeclaredScreens, "[\"items\"]", "[\"register_items\"]"),
                        LoaderTestBundles.environment(LoaderPermission.REGISTER_ITEMS),
                        cacheDirectory));

        byte[] withoutScreens = LoaderTestArchive.archive(
                """
                {"schema":2,"screens":[],"blocks":[],"items":[],"assets":[]}
                """,
                Map.of());
        LoaderTestBundles.writeCache(cacheDirectory, withoutScreens);
        assertThrows(
                IllegalArgumentException.class,
                () -> new LoaderClientTransport().acceptManifest(
                        LoaderTestBundles.manifest(
                                withoutScreens, "[\"views\"]", "[\"present_views\"]"),
                        LoaderTestBundles.environment(LoaderPermission.PRESENT_VIEWS),
                        cacheDirectory));
    }

    @Test
    void unknownContentIsRejectedBeforeRequestOrStaging() {
        byte[] archive = LoaderTestArchive.screenOnly();
        String hash = LoaderTestArchive.sha256(archive);

        assertThrows(
                IllegalArgumentException.class,
                () -> new LoaderClientTransport().acceptManifest(
                        LoaderTestBundles.manifest(archive, hash, "[\"future\"]", "[\"register_blocks\"]"),
                        LoaderTestBundles.environment(LoaderPermission.REGISTER_BLOCKS),
                        cacheDirectory));
        assertFalse(Files.exists(cacheDirectory.resolve("example")));
    }

    @Test
    void legacySchemaOneAndUiInteractionIndexesFailClosed() throws Exception {
        for (String index : List.of(
                """
                {"schema":1,"ui":[{
                  "id":"example:welcome","title":"Welcome","body":"Body"
                }],"blocks":[],"items":[],"assets":[],"interactions":[]}
                """,
                """
                {"schema":2,"ui":[{
                  "id":"example:welcome","title":"Welcome","body":"Body"
                }],"screens":[],"world_previews":[],"blocks":[],"items":[],"assets":[],"sounds":[]}
                """,
                """
                {"schema":2,"screens":[{
                  "id":"example:welcome","kind":"settlement","title":"Welcome","widgets":[]
                }],"world_previews":[],"blocks":[],"items":[],"assets":[],"sounds":[],
                "interactions":[]}
                """)) {
            byte[] archive = LoaderTestArchive.archive(index, Map.of());
            LoaderTestBundles.writeCache(cacheDirectory, archive);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new LoaderClientTransport().acceptManifest(
                            LoaderTestBundles.manifest(archive, "[\"views\"]", "[\"present_views\"]"),
                            LoaderTestBundles.environment(LoaderPermission.PRESENT_VIEWS),
                            cacheDirectory));
        }
    }

    @Test
    void closedIndexRejectsUnknownFieldsBeforeAcknowledgement() throws Exception {
        byte[] archive = LoaderTestArchive.archive(
                """
                {"schema":2,"screens":[{
                  "id":"example:welcome","kind":"settlement","title":"Welcome","widgets":[]
                }],"world_previews":[],"blocks":[],"items":[],"assets":[],"sounds":[],
                "future":true}
                """,
                Map.of());
        LoaderTestBundles.writeCache(cacheDirectory, archive);

        assertThrows(
                IllegalArgumentException.class,
                () -> new LoaderClientTransport().acceptManifest(
                        LoaderTestBundles.manifest(archive, "[\"views\"]", "[\"present_views\"]"),
                        LoaderTestBundles.environment(LoaderPermission.PRESENT_VIEWS),
                        cacheDirectory));
    }

    @Test
    void exactAssetBytesAreVerifiedBeforeAcknowledgement() throws Exception {
        byte[] expectedAsset = new byte[] {'x'};
        byte[] archive = LoaderTestArchive.archive(
                """
                {"schema":2,"screens":[],"world_previews":[],"blocks":[],"items":[],"assets":[{
                  "id":"example:logo","path":"assets/example/logo.bin",
                  "sha256":"%s","size_bytes":1
                }],"sounds":[]}
                """.formatted(LoaderTestArchive.sha256(expectedAsset)),
                Map.of("assets/example/logo.bin", new byte[] {'y'}));
        LoaderTestBundles.writeCache(cacheDirectory, archive);

        assertThrows(
                IllegalArgumentException.class,
                () -> new LoaderClientTransport().acceptManifest(
                        LoaderTestBundles.manifest(archive, "[\"assets\"]", "[\"load_assets\"]"),
                        LoaderTestBundles.environment(LoaderPermission.LOAD_ASSETS),
                        cacheDirectory));
    }

    @Test
    void indexRequiresExactJsonTypes() throws Exception {
        for (String index : List.of(
                """
                {"schema":"2","screens":[],"world_previews":[],"blocks":[],"items":[],
                 "assets":[],"sounds":[]}
                """,
                """
                {"schema":2,"screens":[{
                  "id":"example:welcome","kind":"settlement","title":7,"widgets":[]
                }],"world_previews":[],"blocks":[],"items":[],"assets":[],"sounds":[]}
                """,
                """
                {"schema":2,"screens":[{
                  "id":"example:welcome","kind":"settlement","title":"Welcome",
                  "widgets":[{"type":"input_text","id":"note","label":"Note","max_bytes":"8"}]}
                }],"world_previews":[],"blocks":[],"items":[],"assets":[],"sounds":[]}
                """)) {
            byte[] archive = LoaderTestArchive.archive(index, Map.of());
            LoaderTestBundles.writeCache(cacheDirectory, archive);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new LoaderClientTransport().acceptManifest(
                            LoaderTestBundles.manifest(archive, "[\"views\"]", "[\"present_views\"]"),
                            LoaderTestBundles.environment(LoaderPermission.PRESENT_VIEWS),
                            cacheDirectory));
        }
    }

    @Test
    void archiveMustStartWithItsIndexAndUseCanonicalDeclaredPaths() throws Exception {
        byte[] leadingEntry = LoaderTestArchive.archiveWithLeadingEntry(
                """
                {"schema":2,"screens":[{
                  "id":"example:welcome","kind":"settlement","title":"Welcome","widgets":[]
                }],"world_previews":[],"blocks":[],"items":[],"assets":[],"sounds":[]}
                """);
        LoaderTestBundles.writeCache(cacheDirectory, leadingEntry);
        assertThrows(
                IllegalArgumentException.class,
                () -> new LoaderClientTransport().acceptManifest(
                        LoaderTestBundles.manifest(leadingEntry, "[\"views\"]", "[\"present_views\"]"),
                        LoaderTestBundles.environment(LoaderPermission.PRESENT_VIEWS),
                        cacheDirectory));

        byte[] escapingPath = LoaderTestArchive.archive(
                """
                {"schema":2,"screens":[],"world_previews":[],"blocks":[],"items":[],"assets":[{
                  "id":"example:logo","path":"assets/../logo.bin",
                  "sha256":"%s","size_bytes":1
                }],"sounds":[]}
                """.formatted(LoaderTestArchive.sha256(new byte[] {'x'})),
                Map.of("assets/../logo.bin", new byte[] {'x'}));
        LoaderTestBundles.writeCache(cacheDirectory, escapingPath);
        assertThrows(
                IllegalArgumentException.class,
                () -> new LoaderClientTransport().acceptManifest(
                        LoaderTestBundles.manifest(escapingPath, "[\"assets\"]", "[\"load_assets\"]"),
                        LoaderTestBundles.environment(LoaderPermission.LOAD_ASSETS),
                        cacheDirectory));

        byte[] undeclaredEntry = LoaderTestArchive.archive(
                """
                {"schema":2,"screens":[],"world_previews":[],"blocks":[],"items":[],
                 "assets":[],"sounds":[]}
                """,
                Map.of("assets/example/extra.bin", new byte[] {'x'}));
        LoaderTestBundles.writeCache(cacheDirectory, undeclaredEntry);
        assertThrows(
                IllegalArgumentException.class,
                () -> new LoaderClientTransport().acceptManifest(
                        LoaderTestBundles.manifest(undeclaredEntry, "[\"assets\"]", "[\"load_assets\"]"),
                        LoaderTestBundles.environment(LoaderPermission.LOAD_ASSETS),
                        cacheDirectory));
    }

    @Test
    void soundsActivateOnlyWithTheirVerifiedOwnedOggAsset() throws Exception {
        byte[] ogg = new byte[] {'O', 'g', 'g', 'S'};
        byte[] archive = LoaderTestArchive.archive(
                """
                {"schema":2,"screens":[],"world_previews":[],"blocks":[],"items":[],"assets":[{
                  "id":"example:tone_asset","path":"assets/example/sounds/tone.ogg",
                  "sha256":"%s","size_bytes":%d
                }],"sounds":[{"id":"example:tone"}]}
                """.formatted(LoaderTestArchive.sha256(ogg), ogg.length),
                Map.of("assets/example/sounds/tone.ogg", ogg));
        LoaderActivatedContent active = LoaderTestBundles.activate(
                cacheDirectory,
                archive,
                "[\"assets\",\"sounds\"]",
                "[\"load_assets\",\"play_sounds\"]",
                LoaderPermission.LOAD_ASSETS,
                LoaderPermission.PLAY_SOUNDS);
        assertTrue(active.sounds().containsKey("example:tone"));
        assertEquals(
                "assets/example/sounds/tone.ogg",
                active.sounds().get("example:tone").archivePath());
        assertEquals(Map.of(), active.screens());
    }

    @Test
    void combinedRegistryLimitsAreClosed() {
        assertDoesNotThrow(() -> LoaderContentArchive.ensureRegistryBounds(
                64, 64, 8, 128, 128, 64L * 1024L * 1024L));
        assertThrows(
                IllegalArgumentException.class,
                () -> LoaderContentArchive.ensureRegistryBounds(65, 64, 8, 128, 128, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> LoaderContentArchive.ensureRegistryBounds(64, 65, 8, 128, 128, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> LoaderContentArchive.ensureRegistryBounds(64, 64, 9, 128, 128, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> LoaderContentArchive.ensureRegistryBounds(64, 64, 8, 129, 128, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> LoaderContentArchive.ensureRegistryBounds(64, 64, 8, 128, 129, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> LoaderContentArchive.ensureRegistryBounds(
                        64, 64, 8, 128, 128, 64L * 1024L * 1024L + 1));
    }

}
