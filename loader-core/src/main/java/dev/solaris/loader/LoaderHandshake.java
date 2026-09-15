package dev.solaris.loader;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class LoaderHandshake {
    public static final int PROTOCOL_VERSION = 3;
    public static final int MAX_MANIFEST_BYTES = 32_767;
    public static final int MAX_VIEW_MESSAGE_BYTES = 64 * 1024;
    private static final long MAX_BUNDLE_BYTES = 64L * 1024L * 1024L;
    private static final Gson GSON = new Gson();
    private static final Set<String> MANIFEST_FIELDS = Set.of("protocol", "bundles");
    private static final Set<String> BUNDLE_FIELDS = Set.of(
            "owner",
            "id",
            "version",
            "artifact",
            "sha256",
            "size_bytes",
            "loaders",
            "content",
            "permissions",
            "cache_key");
    private static final Set<String> TEXT_FIELDS =
            Set.of("owner", "id", "version", "artifact", "sha256", "cache_key");
    private static final Set<String> LIST_FIELDS = Set.of("loaders", "content", "permissions");

    private LoaderHandshake() {
    }

    static LoaderManifest validateTransferManifest(
            byte[] payload,
            LoaderEnvironment environment) {
        LoaderManifest manifest =
                validateManifest(payload, environment.platform(), environment.loaderVersion());
        for (LoaderBundle bundle : manifest.bundles()) {
            for (LoaderPermission permission : bundle.permissions()) {
                if (!environment.grantedPermissions().contains(permission)) {
                    throw new IllegalArgumentException("permission was not granted: " + permission);
                }
            }
        }
        return manifest;
    }

    static LoaderManifest inspectManifest(
            byte[] payload,
            LoaderPlatform platform,
            String loaderVersion) {
        return validateManifest(payload, platform, loaderVersion);
    }

    static byte[] acknowledgement(
            LoaderManifest manifest,
            LoaderEnvironment environment,
            LoaderActivatedContent content) {
        List<LoaderPermission> accepted = new ArrayList<>();
        List<String> cached = new ArrayList<>();
        for (LoaderBundle bundle : manifest.bundles()) {
            for (LoaderPermission permission : bundle.permissions()) {
                if (!accepted.contains(permission)) {
                    accepted.add(permission);
                }
            }
            cached.add(bundle.cacheKey());
        }
        Map<String, Integer> carrierBlockStateIds = new LinkedHashMap<>();
        List<String> blockIds = content.blocks().keySet().stream().sorted().toList();
        if (!blockIds.isEmpty()) {
            List<Integer> stateIds = environment.carrierBlockStateIds();
            if (stateIds.size() < blockIds.size()) {
                throw new IllegalArgumentException(
                        "Solaris Loader block carrier capacity is unavailable");
            }
            for (int index = 0; index < blockIds.size(); index++) {
                int stateId = stateIds.get(index);
                if (stateId < 0) {
                    throw new IllegalArgumentException(
                            "Solaris Loader block carrier state must be non-negative");
                }
                carrierBlockStateIds.put(blockIds.get(index), stateId);
            }
        }
        LoaderClientAck ack = new LoaderClientAck(
                PROTOCOL_VERSION,
                environment.platform(),
                environment.loaderVersion(),
                List.copyOf(accepted),
                List.copyOf(cached),
                Collections.unmodifiableMap(carrierBlockStateIds));
        return GSON.toJson(ack).getBytes(StandardCharsets.UTF_8);
    }

    private static LoaderManifest validateManifest(
            byte[] payload,
            LoaderPlatform platform,
            String loaderVersion) {
        LoaderManifest manifest;
        try {
            JsonObject document = LoaderJson.document(
                    payload, MAX_MANIFEST_BYTES, "loader manifest");
            validateClosedSchema(document);
            manifest = GSON.fromJson(document, LoaderManifest.class);
        } catch (JsonParseException error) {
            throw new IllegalArgumentException("loader manifest is malformed", error);
        }
        validateManifest(manifest, platform, loaderVersion);
        return manifest;
    }

    private static void validateClosedSchema(JsonObject manifest) {
        LoaderJson.rejectUnknown(manifest, MANIFEST_FIELDS, "loader manifest");
        LoaderJson.integer(
                manifest,
                "protocol",
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                "loader manifest protocol");
        JsonElement bundles = manifest.get("bundles");
        if (bundles == null || !bundles.isJsonArray()) {
            return;
        }
        for (JsonElement bundle : bundles.getAsJsonArray()) {
            JsonObject object = LoaderJson.object(bundle, "loader bundle");
            LoaderJson.rejectUnknown(object, BUNDLE_FIELDS, "loader bundle");
            for (String field : TEXT_FIELDS) {
                LoaderJson.string(
                        LoaderJson.require(object, field, "loader bundle " + field),
                        "loader bundle " + field);
            }
            LoaderJson.integer(
                    object, "size_bytes", Long.MIN_VALUE, Long.MAX_VALUE, "loader bundle size");
            for (String field : LIST_FIELDS) {
                for (JsonElement entry : LoaderJson.array(object, field, 64, "loader bundle " + field)) {
                    LoaderJson.string(entry, "loader bundle " + field + " entry");
                }
            }
        }
    }

    private static void validateManifest(
            LoaderManifest manifest,
            LoaderPlatform platform,
            String loaderVersion) {
        if (manifest == null || manifest.protocol() != PROTOCOL_VERSION) {
            throw new IllegalArgumentException("unsupported loader protocol");
        }
        if (platform == null) {
            throw new IllegalArgumentException("loader platform is missing");
        }
        requireText(loaderVersion, 64, "loader version");
        if (manifest.bundles() == null || manifest.bundles().isEmpty()) {
            throw new IllegalArgumentException("loader manifest must contain at least one bundle");
        }
        Set<String> cacheKeys = new HashSet<>();
        for (LoaderBundle bundle : manifest.bundles()) {
            validateBundle(bundle, platform);
            if (!cacheKeys.add(bundle.cacheKey())) {
                throw new IllegalArgumentException("duplicate loader cache key " + bundle.cacheKey());
            }
        }
    }

    private static void validateBundle(
            LoaderBundle bundle,
            LoaderPlatform platform) {
        if (bundle == null) {
            throw new IllegalArgumentException("loader bundle is missing");
        }
        requireLiteral(bundle.owner(), 64, "bundle owner");
        requireLiteral(bundle.id(), 48, "bundle id");
        requireLiteral(bundle.version(), 32, "bundle version");
        requireArtifactPath(bundle.artifact());
        if (bundle.sha256() == null || !bundle.sha256().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("bundle sha256 must be 64 lowercase hexadecimal characters");
        }
        if (bundle.sizeBytes() <= 0 || bundle.sizeBytes() > MAX_BUNDLE_BYTES) {
            throw new IllegalArgumentException("bundle size is outside 1..=" + MAX_BUNDLE_BYTES);
        }
        requireList(bundle.loaders(), "bundle loaders");
        requireList(bundle.content(), "bundle content");
        requireList(bundle.permissions(), "bundle permissions");
        if (!bundle.loaders().contains(platform)) {
            throw new IllegalArgumentException("bundle does not support " + platform);
        }
        for (LoaderContentKind content : bundle.content()) {
            LoaderPermission required = requiredPermission(content);
            if (!bundle.permissions().contains(required)) {
                throw new IllegalArgumentException("bundle content is missing permission " + required);
            }
        }
        String expectedCacheKey =
                bundle.owner() + ":" + bundle.id() + "/" + bundle.version() + "/" + bundle.sha256();
        if (!expectedCacheKey.equals(bundle.cacheKey())) {
            throw new IllegalArgumentException("bundle cache key does not match its identity");
        }
    }

    private static LoaderPermission requiredPermission(LoaderContentKind content) {
        if (content == null) {
            throw new IllegalArgumentException("bundle content contains an unknown value");
        }
        return switch (content) {
            case BLOCKS -> LoaderPermission.REGISTER_BLOCKS;
            case ITEMS -> LoaderPermission.REGISTER_ITEMS;
            case VIEWS -> LoaderPermission.PRESENT_VIEWS;
            case VIEW_ACTIONS -> LoaderPermission.SEND_VIEW_ACTIONS;
            case ASSETS -> LoaderPermission.LOAD_ASSETS;
            case WORLD_PREVIEWS -> LoaderPermission.PRESENT_WORLD_PREVIEWS;
            case WORLD_SELECTION -> LoaderPermission.SEND_WORLD_SELECTION;
            case SOUNDS -> LoaderPermission.PLAY_SOUNDS;
        };
    }

    private static void requireText(String value, int maxBytes, String name) {
        if (value == null || value.isEmpty() || value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException(name + " must contain 1..=" + maxBytes + " bytes");
        }
    }

    private static void requireLiteral(String value, int maxBytes, String name) {
        requireText(value, maxBytes, name);
        if (value.equals(".") || value.equals("..")) {
            throw new IllegalArgumentException(name + " contains an invalid path segment");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= 'A' && character <= 'Z')
                    && !(character >= '0' && character <= '9')
                    && character != '_'
                    && character != '.'
                    && character != '-') {
                throw new IllegalArgumentException(name + " contains invalid characters");
            }
        }
    }

    private static void requireArtifactPath(String path) {
        requireText(path, 160, "bundle artifact");
        if (path.startsWith("/") || path.contains("\\") || path.endsWith("/")) {
            throw new IllegalArgumentException("bundle artifact must be a relative canonical path");
        }
        for (int index = 0; index < path.length(); index++) {
            char character = path.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= 'A' && character <= 'Z')
                    && !(character >= '0' && character <= '9')
                    && character != '_'
                    && character != '.'
                    && character != '/'
                    && character != '-') {
                throw new IllegalArgumentException("bundle artifact must be a relative canonical path");
            }
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("bundle artifact must be a relative canonical path");
            }
        }
    }

    private static <T> void requireList(List<T> values, String name) {
        if (values == null || values.isEmpty() || values.contains(null)) {
            throw new IllegalArgumentException(name + " must be non-empty and contain known values");
        }
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(name + " contains duplicate values");
        }
    }
}
