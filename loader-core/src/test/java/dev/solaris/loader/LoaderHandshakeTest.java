package dev.solaris.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class LoaderHandshakeTest {
    private static final String HASH = "a".repeat(64);
    private static final String CACHE_KEY = "example:rich/1/" + HASH;
    private static final byte[] MANIFEST = """
            {
              "protocol": 3,
              "bundles": [{
                "owner": "example",
                "id": "rich",
                "version": "1",
                "artifact": "client/rich.zip",
                "sha256": "%s",
                "size_bytes": 128,
                "loaders": ["fabric", "neoforge", "forge"],
                "content": ["views", "assets"],
                "permissions": ["present_views", "load_assets"],
                "cache_key": "%s"
              }]
            }
            """.formatted(HASH, CACHE_KEY).getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsTheSameManifestForAllThreeLoaderPlatforms() {
        for (LoaderPlatform platform : LoaderPlatform.values()) {
            LoaderEnvironment environment = environment(
                    platform,
                    Set.of(LoaderPermission.PRESENT_VIEWS, LoaderPermission.LOAD_ASSETS));
            LoaderManifest manifest = LoaderHandshake.validateTransferManifest(
                    MANIFEST,
                    environment);
            byte[] payload = LoaderHandshake.acknowledgement(
                    manifest,
                    environment,
                    LoaderActivatedContent.empty());
            LoaderClientAck ack =
                    new Gson().fromJson(new String(payload, StandardCharsets.UTF_8), LoaderClientAck.class);
            assertEquals(platform, ack.platform());
            assertEquals(
                    List.of(LoaderPermission.PRESENT_VIEWS, LoaderPermission.LOAD_ASSETS),
                    ack.acceptedPermissions());
            assertEquals(List.of(CACHE_KEY), ack.cachedBundles());
            assertEquals(Map.of(), ack.carrierBlockStateIds());
        }
    }

    @Test
    void acknowledgementMapsSortedOwnerBlocksToDistinctCarrierStates() {
        LoaderEnvironment environment = new LoaderEnvironment() {
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
                        LoaderPermission.PRESENT_VIEWS,
                        LoaderPermission.LOAD_ASSETS);
            }

            @Override
            public List<Integer> carrierBlockStateIds() {
                return List.of(101, 202);
            }
        };
        LoaderManifest manifest = LoaderHandshake.validateTransferManifest(
                MANIFEST,
                environment);
        LoaderActivatedContent content = new LoaderActivatedContent(
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "other:sapphire_block",
                        new LoaderBlockDefinition(
                                "other:sapphire_block",
                                "other:block/sapphire_block",
                                "Sapphire Block"),
                        "example:ruby_block",
                        new LoaderBlockDefinition(
                                "example:ruby_block",
                                "example:block/ruby_block",
                                "Ruby Block")),
                Map.of(),
                Map.of(),
                Map.of());

        LoaderClientAck ack = new Gson().fromJson(
                new String(
                        LoaderHandshake.acknowledgement(manifest, environment, content),
                        StandardCharsets.UTF_8),
                LoaderClientAck.class);

        assertEquals(
                Map.of(
                        "example:ruby_block", 101,
                        "other:sapphire_block", 202),
                ack.carrierBlockStateIds());
    }

    @Test
    void rejectsMissingPermission() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LoaderHandshake.validateTransferManifest(
                        MANIFEST,
                        environment(LoaderPlatform.FABRIC, Set.of(LoaderPermission.LOAD_ASSETS))));
    }

    @Test
    void rejectsUnknownManifestAndBundleFields() {
        String manifest = new String(MANIFEST, StandardCharsets.UTF_8);
        byte[] unknownManifestField =
                manifest.replace("\"protocol\": 3,", "\"protocol\": 3, \"future\": true,")
                        .getBytes(StandardCharsets.UTF_8);
        byte[] unknownBundleField =
                manifest.replace("\"owner\": \"example\",", "\"owner\": \"example\", \"future\": true,")
                        .getBytes(StandardCharsets.UTF_8);
        LoaderEnvironment environment = environment(
                LoaderPlatform.FABRIC,
                Set.of(LoaderPermission.PRESENT_VIEWS, LoaderPermission.LOAD_ASSETS));

        assertThrows(
                IllegalArgumentException.class,
                () -> LoaderHandshake.validateTransferManifest(unknownManifestField, environment));
        assertThrows(
                IllegalArgumentException.class,
                () -> LoaderHandshake.validateTransferManifest(unknownBundleField, environment));
    }

    @Test
    void rejectsCachePathSegmentsInBundleIdentity() {
        byte[] invalidVersion = new String(MANIFEST, StandardCharsets.UTF_8)
                .replace("\"version\": \"1\"", "\"version\": \"..\"")
                .getBytes(StandardCharsets.UTF_8);

        assertThrows(
                IllegalArgumentException.class,
                () -> LoaderHandshake.validateTransferManifest(
                        invalidVersion,
                        environment(
                                LoaderPlatform.FABRIC,
                                Set.of(
                                        LoaderPermission.PRESENT_VIEWS,
                                        LoaderPermission.LOAD_ASSETS))));
    }

    @Test
    void permissionWireNamesRoundTrip() {
        for (LoaderPermission permission : LoaderPermission.values()) {
            assertEquals(
                    permission,
                    LoaderPermission.fromWireName(permission.wireName()));
        }
    }

    @Test
    void wireThreeIsTheOnlyAcceptedProtocol() {
        assertEquals(3, LoaderHandshake.PROTOCOL_VERSION);
        LoaderEnvironment environment = environment(
                LoaderPlatform.FABRIC,
                Set.of(LoaderPermission.PRESENT_VIEWS, LoaderPermission.LOAD_ASSETS));

        LoaderManifest manifest = LoaderHandshake.validateTransferManifest(MANIFEST, environment);
        assertEquals(3, manifest.protocol());
        byte[] acknowledgement = LoaderHandshake.acknowledgement(
                manifest, environment, LoaderActivatedContent.empty());
        assertEquals(
                3,
                new Gson()
                        .fromJson(
                                new String(acknowledgement, StandardCharsets.UTF_8),
                                LoaderClientAck.class)
                        .protocol());

        for (String rejected : List.of(
                new String(MANIFEST, StandardCharsets.UTF_8)
                        .replace("\"protocol\": 3", "\"protocol\": 2"),
                new String(MANIFEST, StandardCharsets.UTF_8)
                        .replace("\"protocol\": 3", "\"protocol\": 4"),
                new String(MANIFEST, StandardCharsets.UTF_8)
                        .replace("\"protocol\": 3", "\"protocol\": \"3\""))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> LoaderHandshake.validateTransferManifest(
                            rejected.getBytes(StandardCharsets.UTF_8), environment));
        }
    }

    @Test
    void everyContentKindRequiresItsMatchingPermission() {
        Map<LoaderContentKind, LoaderPermission> required = Map.of(
                LoaderContentKind.BLOCKS, LoaderPermission.REGISTER_BLOCKS,
                LoaderContentKind.ITEMS, LoaderPermission.REGISTER_ITEMS,
                LoaderContentKind.VIEWS, LoaderPermission.PRESENT_VIEWS,
                LoaderContentKind.VIEW_ACTIONS, LoaderPermission.SEND_VIEW_ACTIONS,
                LoaderContentKind.ASSETS, LoaderPermission.LOAD_ASSETS,
                LoaderContentKind.WORLD_PREVIEWS, LoaderPermission.PRESENT_WORLD_PREVIEWS,
                LoaderContentKind.WORLD_SELECTION, LoaderPermission.SEND_WORLD_SELECTION,
                LoaderContentKind.SOUNDS, LoaderPermission.PLAY_SOUNDS);
        assertEquals(LoaderContentKind.values().length, required.size());

        for (LoaderContentKind kind : LoaderContentKind.values()) {
            LoaderPermission permission = required.get(kind);
            LoaderEnvironment granted = environment(LoaderPlatform.FABRIC, Set.of(permission));
            LoaderManifest manifest = LoaderHandshake.validateTransferManifest(
                    bundle(content(kind), permission.wireName()),
                    granted);
            assertEquals(List.of(kind), manifest.bundles().get(0).content());

            LoaderPermission wrong = permission == LoaderPermission.LOAD_ASSETS
                    ? LoaderPermission.PLAY_SOUNDS
                    : LoaderPermission.LOAD_ASSETS;
            assertThrows(
                    IllegalArgumentException.class,
                    () -> LoaderHandshake.validateTransferManifest(
                            bundle(content(kind), wrong.wireName()),
                            environment(LoaderPlatform.FABRIC, Set.of(wrong))));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> LoaderHandshake.validateTransferManifest(
                        new String(MANIFEST, StandardCharsets.UTF_8)
                                .replace("\"views\"", "\"ui\"")
                                .getBytes(StandardCharsets.UTF_8),
                        environment(
                                LoaderPlatform.FABRIC,
                                Set.of(LoaderPermission.PRESENT_VIEWS, LoaderPermission.LOAD_ASSETS))));
        assertThrows(
                IllegalArgumentException.class,
                () -> LoaderHandshake.validateTransferManifest(
                        new String(MANIFEST, StandardCharsets.UTF_8)
                                .replace("\"views\"", "\"interactions\"")
                                .getBytes(StandardCharsets.UTF_8),
                        environment(
                                LoaderPlatform.FABRIC,
                                Set.of(LoaderPermission.PRESENT_VIEWS, LoaderPermission.LOAD_ASSETS))));
    }

    private static String content(LoaderContentKind kind) {
        return switch (kind) {
            case BLOCKS -> "blocks";
            case ITEMS -> "items";
            case VIEWS -> "views";
            case VIEW_ACTIONS -> "view_actions";
            case ASSETS -> "assets";
            case WORLD_PREVIEWS -> "world_previews";
            case WORLD_SELECTION -> "world_selection";
            case SOUNDS -> "sounds";
        };
    }

    private static byte[] bundle(String content, String permission) {
        return """
                {"protocol":3,"bundles":[{
                  "owner":"example","id":"rich","version":"1",
                  "artifact":"client/rich.zip","sha256":"%s","size_bytes":128,
                  "loaders":["fabric","neoforge","forge"],"content":["%s"],
                  "permissions":["%s"],"cache_key":"%s"
                }]}
                """.formatted(HASH, content, permission, CACHE_KEY)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static LoaderEnvironment environment(
            LoaderPlatform platform,
            Set<LoaderPermission> permissions) {
        return new LoaderEnvironment() {
            @Override
            public LoaderPlatform platform() {
                return platform;
            }

            @Override
            public String loaderVersion() {
                return "0.1.0";
            }

            @Override
            public Set<LoaderPermission> grantedPermissions() {
                return permissions;
            }
        };
    }
}
