package dev.solaris.loader;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class LoaderContentArchive {
    private static final String INDEX_PATH = "solaris-client.json";
    private static final int INDEX_SCHEMA = 1;
    private static final int MAX_INDEX_BYTES = 64 * 1024;
    private static final int MAX_UI_DEFINITIONS = 64;
    private static final int MAX_BLOCKS_PER_BUNDLE = 1;
    private static final int MAX_ACTIVATED_BLOCKS = 8;
    private static final int MAX_ITEMS = 128;
    private static final int MAX_ASSETS = 128;
    private static final int MAX_INTERACTIONS = 64;
    private static final int MAX_SOUNDS = 64;
    private static final int MAX_INTERACTIONS_PER_UI = 8;
    private static final int MAX_IDENTIFIER_BYTES = 128;
    private static final int MAX_TITLE_BYTES = 128;
    private static final int MAX_BODY_BYTES = 8 * 1024;
    private static final int MAX_INTERACTION_LABEL_BYTES = 64;
    private static final int MAX_INTERACTION_PAYLOAD_BYTES = 4 * 1024;
    private static final int MAX_ARCHIVE_PATH_BYTES = 256;
    private static final long MAX_ACTIVATED_ASSET_BYTES = 64L * 1024L * 1024L;
    private static final Gson GSON = new Gson();
    private static final Set<String> INDEX_FIELDS =
            Set.of("schema", "ui", "blocks", "items", "assets", "interactions", "sounds");
    private static final Set<String> UI_FIELDS =
            Set.of("id", "title", "body", "item_id", "block_id");
    private static final Set<String> BLOCK_FIELDS = Set.of("id", "model", "name");
    private static final Set<String> ITEM_FIELDS = Set.of("id", "base_item", "name");
    private static final Set<String> ASSET_FIELDS =
            Set.of("id", "path", "sha256", "size_bytes");
    private static final Set<String> INTERACTION_FIELDS =
            Set.of("id", "ui_id", "label", "payload", "key");
    private static final Set<String> SOUND_FIELDS = Set.of("id");

    private LoaderContentArchive() {
    }

    static LoaderActivatedContent activate(
            LoaderManifest manifest,
            Path cacheDirectory) {
        LinkedHashMap<String, LoaderUiDefinition> ui = new LinkedHashMap<>();
        LinkedHashMap<String, LoaderBlockDefinition> blocks = new LinkedHashMap<>();
        LinkedHashMap<String, LoaderItemDefinition> items = new LinkedHashMap<>();
        LinkedHashMap<String, LoaderAssetDefinition> assets = new LinkedHashMap<>();
        LinkedHashMap<String, LoaderSoundDefinition> sounds = new LinkedHashMap<>();
        LinkedHashMap<String, LoaderInteractionDefinition> interactions =
                new LinkedHashMap<>();
        List<String> cacheKeys = new ArrayList<>();
        long activatedAssetBytes = 0;
        for (LoaderBundle bundle : manifest.bundles()) {
            byte[] archive = readVerifiedArchive(
                    LoaderTransferSession.cachePath(cacheDirectory, bundle),
                    bundle);
            activatedAssetBytes = Math.addExact(
                    activatedAssetBytes,
                    activateBundle(
                            bundle,
                            archive,
                            ui,
                            blocks,
                            items,
                            assets,
                            interactions,
                            sounds,
                            activatedAssetBytes));
            cacheKeys.add(bundle.cacheKey());
        }
        return new LoaderActivatedContent(
                cacheKeys, ui, blocks, items, assets, interactions, sounds);
    }

    private static long activateBundle(
            LoaderBundle bundle,
            byte[] archive,
            Map<String, LoaderUiDefinition> activatedUi,
            Map<String, LoaderBlockDefinition> activatedBlocks,
            Map<String, LoaderItemDefinition> activatedItems,
            Map<String, LoaderAssetDefinition> activatedAssets,
            Map<String, LoaderInteractionDefinition> activatedInteractions,
            Map<String, LoaderSoundDefinition> activatedSounds,
            long activatedAssetBytes) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry first = zip.getNextEntry();
            if (first == null
                    || first.isDirectory()
                    || !INDEX_PATH.equals(first.getName())) {
                throw new IllegalArgumentException(
                        "Loader bundle must begin with " + INDEX_PATH);
            }
            ArchiveIndex index = parseIndex(readEntry(zip, MAX_INDEX_BYTES));
            long bundleAssetBytes = validateIndex(bundle, index);
            if (activatedSounds.size() + index.sounds().size() > MAX_SOUNDS) {
                throw new IllegalArgumentException("Loader activated sounds exceed registry limit");
            }
            ensureRegistryBounds(
                    activatedUi.size() + index.ui().size(),
                    activatedBlocks.size() + index.blocks().size(),
                    activatedItems.size() + index.items().size(),
                    activatedAssets.size() + index.assets().size(),
                    activatedInteractions.size() + index.interactions().size(),
                    Math.addExact(activatedAssetBytes, bundleAssetBytes));
            Map<String, AssetIndex> assetsByPath = new HashMap<>();
            for (AssetIndex asset : index.assets()) {
                if (assetsByPath.put(asset.path(), asset) != null) {
                    throw new IllegalArgumentException(
                            "Loader archive contains duplicate asset path " + asset.path());
                }
            }

            Map<String, LoaderAssetDefinition> bundleAssets = new LinkedHashMap<>();
            Set<String> entryNames = new HashSet<>();
            entryNames.add(INDEX_PATH);
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    throw new IllegalArgumentException(
                            "Loader archive cannot contain directory entries");
                }
                String path = entry.getName();
                requireArchivePath(path);
                if (!entryNames.add(path)) {
                    throw new IllegalArgumentException(
                            "Loader archive contains duplicate entry " + path);
                }
                AssetIndex asset = assetsByPath.remove(path);
                if (asset == null) {
                    throw new IllegalArgumentException(
                            "Loader archive contains undeclared entry " + path);
                }
                byte[] bytes = readEntry(zip, asset.sizeBytes());
                if (bytes.length != asset.sizeBytes()) {
                    throw new IllegalArgumentException(
                            "Loader asset size does not match its index: " + asset.id());
                }
                if (!sha256(bytes).equals(asset.sha256())) {
                    throw new IllegalArgumentException(
                            "Loader asset SHA-256 does not match its index: " + asset.id());
                }
                bundleAssets.put(
                        asset.id(),
                        new LoaderAssetDefinition(asset.id(), asset.path(), bytes));
            }
            if (!assetsByPath.isEmpty()) {
                throw new IllegalArgumentException(
                        "Loader archive is missing indexed asset " + assetsByPath.keySet().iterator().next());
            }

            for (UiIndex screen : index.ui()) {
                LoaderUiDefinition previous = activatedUi.putIfAbsent(
                        screen.id(),
                        new LoaderUiDefinition(
                                screen.id(),
                                screen.title(),
                                screen.body(),
                                java.util.Optional.ofNullable(screen.itemId()),
                                java.util.Optional.ofNullable(screen.blockId())));
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "duplicate activated Loader screen " + screen.id());
                }
            }
            for (BlockIndex block : index.blocks()) {
                LoaderBlockDefinition previous = activatedBlocks.putIfAbsent(
                        block.id(),
                        new LoaderBlockDefinition(
                                block.id(), block.model(), block.name()));
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "duplicate activated Loader block " + block.id());
                }
            }
            for (ItemIndex item : index.items()) {
                LoaderItemDefinition previous = activatedItems.putIfAbsent(
                        item.id(),
                        new LoaderItemDefinition(item.id(), item.baseItem(), item.name()));
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "duplicate activated Loader item " + item.id());
                }
            }
            for (LoaderAssetDefinition asset : bundleAssets.values()) {
                if (activatedAssets.putIfAbsent(asset.id(), asset) != null) {
                    throw new IllegalArgumentException(
                            "duplicate activated Loader asset " + asset.id());
                }
            }
            for (SoundIndex sound : index.sounds()) {
                if (activatedSounds.putIfAbsent(sound.id(), new LoaderSoundDefinition(sound.id())) != null) {
                    throw new IllegalArgumentException("duplicate activated Loader sound " + sound.id());
                }
            }
            for (InteractionIndex interaction : index.interactions()) {
                LoaderInteractionDefinition previous = activatedInteractions.putIfAbsent(
                        interaction.id(),
                        new LoaderInteractionDefinition(
                                interaction.id(),
                                interaction.uiId(),
                                interaction.label(),
                                interaction.payload(),
                                interaction.key()));
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "duplicate activated Loader interaction " + interaction.id());
                }
            }
            return bundleAssetBytes;
        } catch (IOException error) {
            throw new IllegalArgumentException("reading Loader content archive", error);
        }
    }

    private static ArchiveIndex parseIndex(byte[] bytes) {
        try {
            JsonElement document =
                    JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            validateClosedIndex(document);
            ArchiveIndex index = GSON.fromJson(document, ArchiveIndex.class);
            if (index == null
                    || index.schema() != INDEX_SCHEMA
                    || index.ui() == null
                    || index.blocks() == null
                    || index.items() == null
                    || index.assets() == null
                    || index.interactions() == null) {
                throw new IllegalArgumentException(
                        "Loader archive index must declare schema, ui, blocks, items, assets, and interactions");
            }
            return index;
        } catch (JsonParseException error) {
            throw new IllegalArgumentException("Loader archive index is malformed", error);
        }
    }

    private static void validateClosedIndex(JsonElement document) {
        if (!document.isJsonObject()) {
            throw new IllegalArgumentException("Loader archive index must be a JSON object");
        }
        JsonObject index = document.getAsJsonObject();
        rejectUnknown(index, INDEX_FIELDS, "Loader archive index");
        requireNumber(index, "schema", "Loader archive index schema");
        validateArrayItems(index.get("ui"), UI_FIELDS, "Loader screen");
        validateArrayItems(index.get("blocks"), BLOCK_FIELDS, "Loader block");
        validateArrayItems(index.get("items"), ITEM_FIELDS, "Loader item");
        validateArrayItems(index.get("assets"), ASSET_FIELDS, "Loader asset");
        validateArrayItems(index.get("sounds"), SOUND_FIELDS, "Loader sound");
        validateArrayItems(
                index.get("interactions"),
                INTERACTION_FIELDS,
                "Loader interaction");
    }

    private static void validateArrayItems(
            JsonElement value,
            Set<String> fields,
            String name) {
        if (value == null || !value.isJsonArray()) {
            return;
        }
        for (JsonElement item : value.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                throw new IllegalArgumentException(name + " must be a JSON object");
            }
            JsonObject object = item.getAsJsonObject();
            rejectUnknown(object, fields, name);
            requireString(object, "id", name + " id");
            if (fields.contains("title")) {
                requireString(object, "title", "Loader screen title");
                requireString(object, "body", "Loader screen body");
                if (object.has("item_id")) {
                    requireString(object, "item_id", "Loader screen item id");
                }
                if (object.has("block_id")) {
                    requireString(object, "block_id", "Loader screen block id");
                }
            } else if (fields.contains("model")) {
                requireString(object, "model", "Loader block model");
                requireString(object, "name", "Loader block name");
            } else if (fields.contains("base_item")) {
                requireString(object, "base_item", "Loader item base item");
                requireString(object, "name", "Loader item name");
            } else if (fields.contains("path")) {
                requireString(object, "path", "Loader asset path");
                requireString(object, "sha256", "Loader asset SHA-256");
                requireNumber(object, "size_bytes", "Loader asset size");
            } else if (fields.contains("label")) {
                if (object.has("ui_id")) {
                    requireString(object, "ui_id", "Loader interaction UI id");
                }
                if (object.has("key")) {
                    requireString(object, "key", "Loader interaction key");
                }
                requireString(object, "label", "Loader interaction label");
                requireString(object, "payload", "Loader interaction payload");
            }
        }
    }

    private static void requireString(
            JsonObject object,
            String field,
            String name) {
        JsonElement value = object.get(field);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " must be a JSON string");
        }
    }

    private static void requireNumber(
            JsonObject object,
            String field,
            String name) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException(name + " must be a JSON number");
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isNumber()
                || primitive.getAsBigDecimal().stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(name + " must be a JSON number");
        }
    }

    static void ensureRegistryBounds(
            int uiCount,
            int blockCount,
            int itemCount,
            int assetCount,
            int interactionCount,
            long assetBytes) {
        if (uiCount > MAX_UI_DEFINITIONS
                || blockCount > MAX_ACTIVATED_BLOCKS
                || itemCount > MAX_ITEMS
                || assetCount > MAX_ASSETS
                || interactionCount > MAX_INTERACTIONS) {
            throw new IllegalArgumentException(
                    "Loader activated content exceeds registry limits");
        }
        if (assetBytes > MAX_ACTIVATED_ASSET_BYTES) {
            throw new IllegalArgumentException(
                    "Loader activated assets exceed "
                            + MAX_ACTIVATED_ASSET_BYTES
                            + " bytes");
        }
    }

    private static void rejectUnknown(
            JsonObject object,
            Set<String> allowed,
            String name) {
        for (String field : object.keySet()) {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException(
                        name + " contains unknown field " + field);
            }
        }
    }

    private static long validateIndex(
            LoaderBundle bundle,
            ArchiveIndex index) {
        if (index.ui().size() > MAX_UI_DEFINITIONS
                || index.blocks().size() > MAX_BLOCKS_PER_BUNDLE
                || index.items().size() > MAX_ITEMS
                || index.assets().size() > MAX_ASSETS
                || index.interactions().size() > MAX_INTERACTIONS) {
            throw new IllegalArgumentException("Loader archive index exceeds content limits");
        }
        boolean uiDeclared = bundle.content().contains(LoaderContentKind.UI);
        boolean blocksDeclared = bundle.content().contains(LoaderContentKind.BLOCKS);
        boolean itemsDeclared = bundle.content().contains(LoaderContentKind.ITEMS);
        boolean assetsDeclared = bundle.content().contains(LoaderContentKind.ASSETS);
        boolean interactionsDeclared =
                bundle.content().contains(LoaderContentKind.INTERACTIONS);
        if (uiDeclared != !index.ui().isEmpty()) {
            throw new IllegalArgumentException(
                    "Loader screen index does not match the bundle content declaration");
        }
        if (blocksDeclared != !index.blocks().isEmpty()) {
            throw new IllegalArgumentException(
                    "Loader block index does not match the bundle content declaration");
        }
        if (blocksDeclared
                && !bundle.permissions().contains(LoaderPermission.REGISTER_BLOCKS)) {
            throw new IllegalArgumentException(
                    "Loader block content requires register_blocks permission");
        }
        if (itemsDeclared != !index.items().isEmpty()) {
            throw new IllegalArgumentException(
                    "Loader item index does not match the bundle content declaration");
        }
        if (itemsDeclared
                && !bundle.permissions().contains(LoaderPermission.REGISTER_ITEMS)) {
            throw new IllegalArgumentException(
                    "Loader item content requires register_items permission");
        }
        if (assetsDeclared != !index.assets().isEmpty()) {
            throw new IllegalArgumentException(
                    "Loader asset index does not match the bundle content declaration");
        }
        if (interactionsDeclared != !index.interactions().isEmpty()) {
            throw new IllegalArgumentException(
                    "Loader interaction index does not match the bundle content declaration");
        }
        boolean soundsDeclared = bundle.content().contains(LoaderContentKind.SOUNDS);
        if (soundsDeclared != !index.sounds().isEmpty()
                || soundsDeclared && !bundle.permissions().contains(LoaderPermission.PLAY_SOUNDS)) {
            throw new IllegalArgumentException("Loader sounds require matching content and play_sounds");
        }
        if (index.sounds().size() > MAX_SOUNDS) {
            throw new IllegalArgumentException("Loader sound index exceeds content limit");
        }

        Set<String> ids = new HashSet<>();
        Set<String> uiIds = new HashSet<>();
        Set<String> blockIds = new HashSet<>();
        Set<String> itemIds = new HashSet<>();
        for (UiIndex screen : index.ui()) {
            requireOwnedIdentifier(screen.id(), bundle.owner(), "screen id");
            requireText(screen.title(), MAX_TITLE_BYTES, "screen title");
            requireText(screen.body(), MAX_BODY_BYTES, "screen body");
            if (!ids.add(screen.id())) {
                throw new IllegalArgumentException(
                        "Loader archive contains duplicate content id " + screen.id());
            }
            uiIds.add(screen.id());
        }
        Set<String> assetPaths = new HashSet<>();
        for (AssetIndex asset : index.assets()) {
            assetPaths.add(asset.path());
        }
        for (BlockIndex block : index.blocks()) {
            requireOwnedIdentifier(block.id(), bundle.owner(), "block id");
            requireOwnedIdentifier(block.model(), bundle.owner(), "block model");
            requireText(block.name(), MAX_TITLE_BYTES, "block name");
            String modelPath = modelDefinitionPath(block.model());
            if (!assetPaths.contains(modelPath)) {
                throw new IllegalArgumentException(
                        "Loader block is missing its model asset " + modelPath);
            }
            if (!ids.add(block.id())) {
                throw new IllegalArgumentException(
                        "Loader archive contains duplicate content id " + block.id());
            }
            blockIds.add(block.id());
        }
        for (ItemIndex item : index.items()) {
            requireOwnedIdentifier(item.id(), bundle.owner(), "item id");
            requireOwnedIdentifier(item.baseItem(), "minecraft", "item base item");
            requireText(item.name(), MAX_TITLE_BYTES, "item name");
            String modelPath = itemDefinitionPath(item.id());
            if (!assetPaths.contains(modelPath)) {
                throw new IllegalArgumentException(
                        "Loader item is missing its item definition asset " + modelPath);
            }
            if (!ids.add(item.id())) {
                throw new IllegalArgumentException(
                        "Loader archive contains duplicate content id " + item.id());
            }
            itemIds.add(item.id());
        }
        for (UiIndex screen : index.ui()) {
            if (screen.itemId() != null) {
                requireOwnedIdentifier(screen.itemId(), bundle.owner(), "screen item id");
                if (!itemIds.contains(screen.itemId())) {
                    throw new IllegalArgumentException(
                            "Loader screen references an undeclared item " + screen.itemId());
                }
            }
            if (screen.blockId() != null) {
                requireOwnedIdentifier(screen.blockId(), bundle.owner(), "screen block id");
                if (!blockIds.contains(screen.blockId())) {
                    throw new IllegalArgumentException(
                            "Loader screen references an undeclared block "
                                    + screen.blockId());
                }
            }
        }
        long totalAssetBytes = 0;
        for (AssetIndex asset : index.assets()) {
            requireOwnedIdentifier(asset.id(), bundle.owner(), "asset id");
            requireArchivePath(asset.path());
            if (!asset.path().startsWith("assets/")) {
                throw new IllegalArgumentException(
                        "Loader asset path must start with assets/");
            }
            if (asset.sha256() == null
                    || !asset.sha256().matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Loader asset SHA-256 must be lowercase hexadecimal");
            }
            if (asset.sizeBytes() <= 0
                    || asset.sizeBytes() > MAX_ACTIVATED_ASSET_BYTES) {
                throw new IllegalArgumentException(
                        "Loader asset size is outside the activation limit");
            }
            totalAssetBytes = Math.addExact(totalAssetBytes, asset.sizeBytes());
            if (totalAssetBytes > MAX_ACTIVATED_ASSET_BYTES) {
                throw new IllegalArgumentException(
                        "Loader archive activated assets exceed "
                                + MAX_ACTIVATED_ASSET_BYTES
                                + " bytes");
            }
            if (!ids.add(asset.id())) {
                throw new IllegalArgumentException(
                        "Loader archive contains duplicate content id " + asset.id());
            }
        }
        for (SoundIndex sound : index.sounds()) {
            requireOwnedIdentifier(sound.id(), bundle.owner(), "sound id");
            if (!ids.add(sound.id())) {
                throw new IllegalArgumentException("duplicate Loader sound id " + sound.id());
            }
            if (!assetPaths.contains(new LoaderSoundDefinition(sound.id()).archivePath())) {
                throw new IllegalArgumentException("Loader sound is missing its verified OGG asset");
            }
        }
        Map<String, Integer> interactionsPerUi = new HashMap<>();
        for (InteractionIndex interaction : index.interactions()) {
            requireOwnedIdentifier(interaction.id(), bundle.owner(), "interaction id");
            if (interaction.uiId() == null && interaction.key() == null) {
                throw new IllegalArgumentException("Loader interaction requires ui_id or key");
            }
            if (interaction.key() != null
                    && !LoaderInteractionDefinition.isKeyboardKey(interaction.key())) {
                throw new IllegalArgumentException("Loader interaction key is not a named keyboard key");
            }
            if (interaction.uiId() != null) {
                requireOwnedIdentifier(interaction.uiId(), bundle.owner(), "interaction UI id");
                if (!uiIds.contains(interaction.uiId())) {
                    throw new IllegalArgumentException(
                            "Loader interaction references an undeclared UI " + interaction.uiId());
                }
                int count = interactionsPerUi.merge(interaction.uiId(), 1, Integer::sum);
                if (count > MAX_INTERACTIONS_PER_UI) {
                    throw new IllegalArgumentException("Loader UI exceeds its interaction limit");
                }
            }
            requireText(
                    interaction.label(),
                    MAX_INTERACTION_LABEL_BYTES,
                    "interaction label");
            requireBoundedText(
                    interaction.payload(),
                    MAX_INTERACTION_PAYLOAD_BYTES,
                    "interaction payload");
            if (!ids.add(interaction.id())) {
                throw new IllegalArgumentException(
                        "Loader archive contains duplicate content id " + interaction.id());
            }
        }
        return totalAssetBytes;
    }

    private static String itemDefinitionPath(String itemId) {
        int separator = itemId.indexOf(':');
        return "assets/"
                + itemId.substring(0, separator)
                + "/items/"
                + itemId.substring(separator + 1)
                + ".json";
    }

    private static String modelDefinitionPath(String modelId) {
        int separator = modelId.indexOf(':');
        return "assets/"
                + modelId.substring(0, separator)
                + "/models/"
                + modelId.substring(separator + 1)
                + ".json";
    }

    private static byte[] readVerifiedArchive(Path path, LoaderBundle bundle) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) != bundle.sizeBytes()) {
                throw new IllegalArgumentException(
                        "Loader cache file no longer matches " + bundle.cacheKey());
            }
            byte[] bytes;
            try (InputStream input = Files.newInputStream(path)) {
                bytes = input.readNBytes(Math.toIntExact(bundle.sizeBytes()) + 1);
            }
            if (bytes.length != bundle.sizeBytes()
                    || !sha256(bytes).equals(bundle.sha256())) {
                throw new IllegalArgumentException(
                        "Loader cache file failed activation verification "
                                + bundle.cacheKey());
            }
            return bytes;
        } catch (IOException | ArithmeticException error) {
            throw new IllegalArgumentException(
                    "reading verified Loader cache file " + path,
                    error);
        }
    }

    private static byte[] readEntry(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream output =
                new ByteArrayOutputStream((int) Math.min(limit, 32 * 1024));
        byte[] buffer = new byte[8 * 1024];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total = Math.addExact(total, read);
            if (total > limit) {
                throw new IllegalArgumentException(
                        "Loader archive entry exceeds its declared limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void requireOwnedIdentifier(
            String value,
            String owner,
            String name) {
        requireText(value, MAX_IDENTIFIER_BYTES, name);
        String prefix = owner + ":";
        if (!value.startsWith(prefix) || value.length() == prefix.length()) {
            throw new IllegalArgumentException(name + " must be owned by " + owner);
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean separator = index == owner.length() && character == ':';
            boolean allowed = character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '_'
                    || character == '.'
                    || character == '-'
                    || index > owner.length() && character == '/';
            if (!separator && !allowed) {
                throw new IllegalArgumentException(name + " contains invalid characters");
            }
        }
    }

    private static void requireArchivePath(String path) {
        requireText(path, MAX_ARCHIVE_PATH_BYTES, "archive path");
        if (path.startsWith("/")
                || path.contains("\\")
                || path.endsWith("/")) {
            throw new IllegalArgumentException("Loader archive path is not canonical");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Loader archive path is not canonical");
            }
        }
        for (int index = 0; index < path.length(); index++) {
            char character = path.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '_'
                    && character != '.'
                    && character != '/'
                    && character != '-') {
                throw new IllegalArgumentException(
                        "Loader archive path contains invalid characters");
            }
        }
    }

    private static void requireText(String value, int maxBytes, String name) {
        requireBoundedText(value, maxBytes, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    name + " must contain 1..=" + maxBytes + " bytes");
        }
    }

    private static void requireBoundedText(String value, int maxBytes, String name) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException(
                    name + " must contain 0..=" + maxBytes + " bytes");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JVM does not provide SHA-256", error);
        }
    }

    private record ArchiveIndex(
            int schema,
            List<UiIndex> ui,
            List<BlockIndex> blocks,
            List<ItemIndex> items,
            List<AssetIndex> assets,
            List<InteractionIndex> interactions,
            List<SoundIndex> sounds) {
        private ArchiveIndex {
            sounds = sounds == null ? List.of() : sounds;
        }
    }

    private record SoundIndex(String id) {
    }

    private record UiIndex(
            String id,
            String title,
            String body,
            @SerializedName("item_id") String itemId,
            @SerializedName("block_id") String blockId) {
    }

    private record BlockIndex(
            String id,
            String model,
            String name) {
    }

    private record ItemIndex(
            String id,
            @SerializedName("base_item") String baseItem,
            String name) {
    }

    private record AssetIndex(
            String id,
            String path,
            String sha256,
            @SerializedName("size_bytes") long sizeBytes) {
    }

    private record InteractionIndex(
            String id,
            @SerializedName("ui_id") String uiId,
            String label,
            String payload,
            String key) {
    }
}
