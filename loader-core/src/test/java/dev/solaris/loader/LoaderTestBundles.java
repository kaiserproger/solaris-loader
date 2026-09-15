package dev.solaris.loader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** One-bundle manifest, cache and activation fixtures for the Loader tests. */
final class LoaderTestBundles {
    static final String OWNER = "example";
    static final String BUNDLE_ID = "content";
    static final String VERSION = "1";

    private LoaderTestBundles() {
    }

    static LoaderActivatedContent activate(
            Path cacheDirectory,
            byte[] archive,
            String content,
            String permissions,
            LoaderPermission... granted) throws Exception {
        writeCache(cacheDirectory, archive);
        return new LoaderClientTransport()
                .acceptManifest(
                        manifest(archive, content, permissions),
                        environment(granted),
                        cacheDirectory)
                .activatedContent();
    }

    static void writeCache(Path cacheDirectory, byte[] archive) throws Exception {
        String hash = LoaderTestArchive.sha256(archive);
        Path directory = cacheDirectory.resolve(OWNER + "/" + BUNDLE_ID + "/" + VERSION);
        Files.createDirectories(directory);
        Files.write(directory.resolve(hash + ".bundle"), archive);
    }

    static byte[] manifest(byte[] archive, String content, String permissions) {
        return manifest(archive, LoaderTestArchive.sha256(archive), content, permissions);
    }

    static byte[] manifest(
            byte[] archive,
            String hash,
            String content,
            String permissions) {
        return """
                {"protocol":3,"bundles":[{
                  "owner":"%1$s","id":"%2$s","version":"%3$s",
                  "artifact":"client/content.zip","sha256":"%4$s","size_bytes":%5$d,
                  "loaders":["fabric","neoforge","forge"],"content":%6$s,"permissions":%7$s,
                  "cache_key":"%1$s:%2$s/%3$s/%4$s"
                }]}
                """.formatted(OWNER, BUNDLE_ID, VERSION, hash, archive.length, content, permissions)
                .getBytes(StandardCharsets.UTF_8);
    }

    static LoaderEnvironment environment(LoaderPermission... permissions) {
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
                return Set.of(permissions);
            }

            @Override
            public List<Integer> carrierBlockStateIds() {
                return List.of(321, 654);
            }
        };
    }
}
