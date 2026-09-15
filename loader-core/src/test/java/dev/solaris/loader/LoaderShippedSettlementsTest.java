package dev.solaris.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Activation of the archive one real package ships.
 *
 * The screen a player sees is the intersection of the package's verified
 * artifact index and the server's view messages: this test pins the index half
 * against the shipped bytes, so a schema mistake in the package fails here
 * instead of failing silently in a client that never opens the view.
 *
 * The package lives in the independent plugin repository; a Loader workspace
 * without that checkout skips instead of inventing a fixture.
 */
final class LoaderShippedSettlementsTest {
    private static final String OWNER = "solaris-settlements";
    private static final String SCREEN = "solaris-settlements:overview";
    private static final String CONTENT = "[\"views\",\"view_actions\"]";
    private static final String PERMISSIONS = "[\"present_views\",\"send_view_actions\"]";

    @TempDir
    Path cacheDirectory;

    @Test
    void shipped_settlements_archive_activates_its_overview_screen() throws Exception {
        Path archivePath = shippedArchive();
        if (archivePath == null) {
            return;
        }
        byte[] archive = Files.readAllBytes(archivePath);
        String hash = LoaderTestArchive.sha256(archive);

        writeCache(hash, archive);
        LoaderActivatedContent content = new LoaderClientTransport()
                .acceptManifest(
                        manifest(hash, archive.length),
                        environment(),
                        cacheDirectory)
                .activatedContent();

        LoaderScreenDefinition screen = content.screens().get(SCREEN);
        assertNotNull(screen, "the shipped archive must declare " + SCREEN);
        assertEquals(LoaderScreenKind.SETTLEMENT, screen.kind());
        assertEquals("Settlement overview", screen.title());

        // The server sends rows into the declared table and have/need into the
        // declared panel; a model action is only shown when the index declares
        // the same action id, so both halves are pinned here.
        List<LoaderWidget> widgets = screen.widgets();
        LoaderWidget.PagedTable table = widgets.stream()
                .filter(LoaderWidget.PagedTable.class::isInstance)
                .map(LoaderWidget.PagedTable.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the overview needs a paged table"));
        assertEquals(3, table.columns().size());
        assertTrue(widgets.stream().anyMatch(LoaderWidget.ResourcePanel.class::isInstance));
        List<String> actions = widgets.stream()
                .filter(LoaderWidget.ActionButton.class::isInstance)
                .map(LoaderWidget.ActionButton.class::cast)
                .map(LoaderWidget.ActionButton::actionId)
                .toList();
        assertTrue(
                actions.containsAll(List.of(
                        "solaris-settlements:refresh",
                        "solaris-settlements:page_next",
                        "solaris-settlements:page_prev")),
                "declared actions were " + actions);
    }

    /** The shipped archive, or `null` when the plugin checkout is absent. */
    private static Path shippedArchive() {
        String repositoryRoot = System.getProperty("solaris.repoRoot");
        if (repositoryRoot == null || repositoryRoot.isBlank()) {
            return null;
        }
        Path archive = Path.of(repositoryRoot)
                .resolve("../solaris-default-plugins/" + OWNER + "/client/settlements-ui.zip")
                .normalize()
                .toAbsolutePath();
        return Files.isRegularFile(archive) ? archive : null;
    }

    private void writeCache(String hash, byte[] archive) throws Exception {
        Path directory = cacheDirectory.resolve(OWNER + "/settlements-ui/1.0.0");
        Files.createDirectories(directory);
        Files.write(directory.resolve(hash + ".bundle"), archive);
    }

    private static byte[] manifest(String hash, int size) {
        return """
                {"protocol":3,"bundles":[{
                  "owner":"%1$s","id":"settlements-ui","version":"1.0.0",
                  "artifact":"client/settlements-ui.zip","sha256":"%2$s","size_bytes":%3$d,
                  "loaders":["fabric","neoforge","forge"],"content":%4$s,
                  "permissions":%5$s,"cache_key":"%1$s:settlements-ui/1.0.0/%2$s"
                }]}
                """
                .formatted(OWNER, hash, size, CONTENT, PERMISSIONS)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static LoaderEnvironment environment() {
        return new LoaderEnvironment() {
            @Override
            public LoaderPlatform platform() {
                return LoaderPlatform.FABRIC;
            }

            @Override
            public String loaderVersion() {
                return "0.1.0";
            }

            @Override
            public Set<LoaderPermission> grantedPermissions() {
                return Set.of(
                        LoaderPermission.PRESENT_VIEWS, LoaderPermission.SEND_VIEW_ACTIONS);
            }

            @Override
            public List<Integer> carrierBlockStateIds() {
                return List.of();
            }
        };
    }
}
