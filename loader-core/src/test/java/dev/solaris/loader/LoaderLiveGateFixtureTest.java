package dev.solaris.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LoaderLiveGateFixtureTest {
    private static final String CONTENT =
            "[\"blocks\",\"items\",\"views\",\"view_actions\",\"assets\"]";
    private static final String PERMISSIONS =
            "[\"register_blocks\",\"register_items\",\"present_views\","
                    + "\"send_view_actions\",\"load_assets\"]";

    @TempDir
    Path cacheDirectory;

    @Test
    void shippedTwoOwnerArchivesActivateOwnedContentTogether() throws Exception {
        byte[] ruby = archive("ruby-live");
        byte[] sapphire = archive("sapphire-live");
        String rubyHash = LoaderTestArchive.sha256(ruby);
        String sapphireHash = LoaderTestArchive.sha256(sapphire);
        writeCache("ruby-live", rubyHash, ruby);
        writeCache("sapphire-live", sapphireHash, sapphire);

        byte[] manifest = """
                {"protocol":3,"bundles":[
                  %s,
                  %s
                ]}
                """.formatted(
                        bundle("ruby-live", rubyHash, ruby.length),
                        bundle("sapphire-live", sapphireHash, sapphire.length))
                .getBytes(StandardCharsets.UTF_8);

        LoaderOutgoing outgoing =
                new LoaderClientTransport().acceptManifest(
                        manifest, environment(LoaderPlatform.FABRIC), cacheDirectory);
        assertEquals(LoaderOutgoing.Kind.ACKNOWLEDGEMENT, outgoing.kind());
        LoaderActivatedContent active = outgoing.activatedContent();

        LoaderScreenDefinition rubyScreen = active.screens().get("ruby-live:showcase");
        assertEquals(LoaderScreenKind.SETTLEMENT, rubyScreen.kind());
        assertEquals("ruby-live:ruby", rubyScreen.itemId().orElseThrow());
        assertEquals("ruby-live:ruby_block", rubyScreen.blockId().orElseThrow());

        LoaderScreenDefinition rubyHud = active.screens().get("ruby-live:hud");
        assertEquals(LoaderScreenKind.HUD, rubyHud.kind());
        LoaderScreenDefinition rubyInput = active.screens().get("ruby-live:input");
        assertEquals(LoaderScreenKind.HUD, rubyInput.kind());
        assertEquals(List.of(), rubyInput.widgets());

        LoaderScreenDefinition sapphireScreen = active.screens().get("sapphire-live:showcase");
        assertEquals("sapphire-live:sapphire", sapphireScreen.itemId().orElseThrow());
        assertEquals("sapphire-live:sapphire_block", sapphireScreen.blockId().orElseThrow());
        assertTrue(active.blocks().containsKey("ruby-live:ruby_block"));
        assertTrue(active.blocks().containsKey("sapphire-live:sapphire_block"));
        assertTrue(active.items().containsKey("ruby-live:ruby"));
        assertTrue(active.items().containsKey("ruby-live:blade"));
        assertTrue(active.items().containsKey("sapphire-live:sapphire"));
        assertTrue(active.assets().containsKey("ruby-live:ruby_item_definition"));
        assertTrue(active.assets().containsKey("ruby-live:blade_item_definition"));
        assertTrue(active.assets().containsKey("sapphire-live:sapphire_block_model"));

        for (LoaderPlatform platform : List.of(LoaderPlatform.NEOFORGE, LoaderPlatform.FORGE)) {
            LoaderOutgoing other = new LoaderClientTransport().acceptManifest(
                    manifest, environment(platform), cacheDirectory);
            assertEquals(LoaderOutgoing.Kind.ACKNOWLEDGEMENT, other.kind());
            assertEquals(active.items().keySet(), other.activatedContent().items().keySet());
            assertEquals(active.blocks().keySet(), other.activatedContent().blocks().keySet());
            assertEquals(active.assets().keySet(), other.activatedContent().assets().keySet());
        }
    }

    private static byte[] archive(String owner) throws Exception {
        Path root = Path.of(System.getProperty("solaris.repoRoot"));
        return Files.readAllBytes(root.resolve(
                "examples/loader-live-gate/plugins/" + owner + "/client/rich-content.zip"));
    }

    private void writeCache(String owner, String hash, byte[] archive) throws Exception {
        Path directory = cacheDirectory.resolve(owner + "/rich-content/1");
        Files.createDirectories(directory);
        Files.write(directory.resolve(hash + ".bundle"), archive);
    }

    private static String bundle(String owner, String hash, int size) {
        return """
                {"owner":"%1$s","id":"rich-content","version":"1",
                 "artifact":"client/rich-content.zip","sha256":"%2$s","size_bytes":%3$d,
                 "loaders":["fabric","neoforge","forge"],"content":%4$s,
                 "permissions":%5$s,"cache_key":"%1$s:rich-content/1/%2$s"}
                """.formatted(owner, hash, size, CONTENT, PERMISSIONS);
    }

    private static LoaderEnvironment environment(LoaderPlatform platform) {
        return new LoaderEnvironment() {
            @Override
            public LoaderPlatform platform() {
                return platform;
            }

            @Override
            public String loaderVersion() {
                return "0.19.3";
            }

            @Override
            public Set<LoaderPermission> grantedPermissions() {
                return Set.of(
                        LoaderPermission.REGISTER_BLOCKS,
                        LoaderPermission.REGISTER_ITEMS,
                        LoaderPermission.PRESENT_VIEWS,
                        LoaderPermission.SEND_VIEW_ACTIONS,
                        LoaderPermission.LOAD_ASSETS);
            }

            @Override
            public List<Integer> carrierBlockStateIds() {
                return List.of(321, 654);
            }
        };
    }
}
