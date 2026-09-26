package dev.solaris.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Activation of one verified schema-2 Loader client artifact: a closed index, a
 * bounded registry and cross-checked content, permission and asset references.
 */
final class LoaderContentArchive {
    private static final String INDEX_PATH = "solaris-client.json";
    private static final int INDEX_SCHEMA = 2;
    private static final int MAX_INDEX_BYTES = 64 * 1024;
    private static final int MAX_SCREENS = 64;
    private static final int MAX_WORLD_PREVIEWS = 64;
    private static final int MAX_BLOCKS_PER_BUNDLE = 1;
    private static final int MAX_ACTIVATED_BLOCKS = 8;
    private static final int MAX_ITEMS = 128;
    private static final int MAX_ASSETS = 128;
    private static final int MAX_SOUNDS = 64;
    private static final int MAX_ARCHIVE_PATH_BYTES = 256;
    private static final long MAX_ACTIVATED_ASSET_BYTES = 64L * 1024L * 1024L;
    private static final Set<String> INDEX_FIELDS = Set.of(
            "schema", "screens", "world_previews", "blocks", "items", "assets", "sounds");
    private static final Set<String> SCREEN_FIELDS =
            Set.of("id", "kind", "title", "item_id", "block_id", "widgets", "input_bindings");
    private static final Set<String> INPUT_BINDING_FIELDS =
            Set.of("key", "press_action", "release_action");
    private static final Set<String> PREVIEW_FIELDS = Set.of(
            "id",
            "blueprint_id",
            "content_hash",
            "rotation",
            "size_x",
            "size_y",
            "size_z",
            "blocks");
    private static final Set<String> LOCAL_BLOCK_FIELDS = Set.of("x", "y", "z", "block_id");
    private static final Set<String> BLOCK_FIELDS = Set.of("id", "model", "name");
    private static final Set<String> ITEM_FIELDS = Set.of("id", "base_item", "name");
    private static final Set<String> ASSET_FIELDS = Set.of("id", "path", "sha256", "size_bytes");
    private static final Set<String> SOUND_FIELDS = Set.of("id");
    private static final Set<String> PAGED_TABLE_FIELDS = Set.of("type", "id", "columns");
    private static final Set<String> COLUMN_FIELDS =
            Set.of("id", "label", "align", "width_bucket");
    private static final Set<String> TABS_FIELDS = Set.of("type", "id", "entries");
    private static final Set<String> ENTRY_FIELDS = Set.of("id", "label");
    private static final Set<String> INPUT_NUMBER_FIELDS =
            Set.of("type", "id", "label", "min", "max", "step");
    private static final Set<String> INPUT_TEXT_FIELDS = Set.of("type", "id", "label", "max_bytes");
    private static final Set<String> SELECT_ENUM_FIELDS = Set.of("type", "id", "label", "options");
    private static final Set<String> RESOURCE_PANEL_FIELDS = Set.of("type", "id", "label", "entries");
    private static final Set<String> ACTION_BUTTON_FIELDS =
            Set.of("type", "action_id", "label", "enabled", "deny_reason");
    private static final Set<String> WORLD_MARKER_FIELDS =
            Set.of("type", "id", "label", "action_id", "preview_id", "formation", "radius");

    private LoaderContentArchive() {
    }

    static LoaderActivatedContent activate(
            LoaderManifest manifest,
            Path cacheDirectory) {
        LinkedHashMap<String, LoaderScreenDefinition> screens = new LinkedHashMap<>();
        LinkedHashMap<String, LoaderWorldPreviewDefinition> worldPreviews = new LinkedHashMap<>();
        LinkedHashMap<String, LoaderBlockDefinition> blocks = new LinkedHashMap<>();
        LinkedHashMap<String, LoaderItemDefinition> items = new LinkedHashMap<>();
        LinkedHashMap<String, LoaderAssetDefinition> assets = new LinkedHashMap<>();
        LinkedHashMap<String, LoaderSoundDefinition> sounds = new LinkedHashMap<>();
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
                            screens,
                            worldPreviews,
                            blocks,
                            items,
                            assets,
                            sounds,
                            activatedAssetBytes));
            cacheKeys.add(bundle.cacheKey());
        }
        return new LoaderActivatedContent(
                cacheKeys, screens, worldPreviews, blocks, items, assets, sounds);
    }

    private static long activateBundle(
            LoaderBundle bundle,
            byte[] archive,
            Map<String, LoaderScreenDefinition> activatedScreens,
            Map<String, LoaderWorldPreviewDefinition> activatedWorldPreviews,
            Map<String, LoaderBlockDefinition> activatedBlocks,
            Map<String, LoaderItemDefinition> activatedItems,
            Map<String, LoaderAssetDefinition> activatedAssets,
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
                    activatedScreens.size() + index.screens().size(),
                    activatedWorldPreviews.size() + index.worldPreviews().size(),
                    activatedBlocks.size() + index.blocks().size(),
                    activatedItems.size() + index.items().size(),
                    activatedAssets.size() + index.assets().size(),
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

            for (LoaderScreenDefinition screen : index.screens()) {
                if (activatedScreens.putIfAbsent(screen.id(), screen) != null) {
                    throw new IllegalArgumentException(
                            "duplicate activated Loader screen " + screen.id());
                }
            }
            for (LoaderWorldPreviewDefinition preview : index.worldPreviews()) {
                if (activatedWorldPreviews.putIfAbsent(preview.id(), preview) != null) {
                    throw new IllegalArgumentException(
                            "duplicate activated Loader world preview " + preview.id());
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
            return bundleAssetBytes;
        } catch (IOException error) {
            throw new IllegalArgumentException("reading Loader content archive", error);
        }
    }

    private static ArchiveIndex parseIndex(byte[] bytes) {
        JsonObject index = LoaderJson.document(bytes, MAX_INDEX_BYTES, "Loader archive index");
        LoaderJson.rejectUnknown(index, INDEX_FIELDS, "Loader archive index");
        LoaderJson.integer(
                index, "schema", INDEX_SCHEMA, INDEX_SCHEMA, "Loader archive index schema");
        return new ArchiveIndex(
                screens(index),
                worldPreviews(index),
                blocks(index),
                items(index),
                assets(index),
                sounds(index));
    }

    private static List<LoaderScreenDefinition> screens(JsonObject index) {
        List<JsonElement> entries = LoaderJson.optionalArray(
                index, "screens", MAX_SCREENS, "Loader screen index");
        List<LoaderScreenDefinition> screens = new ArrayList<>(entries.size());
        Set<String> screenIds = new HashSet<>();
        for (JsonElement entry : entries) {
            JsonObject screen = LoaderJson.object(entry, "Loader screen");
            LoaderJson.rejectUnknown(screen, SCREEN_FIELDS, "Loader screen");
            String id = LoaderJson.identifier(screen, "id", "Loader screen id");
            LoaderScreenKind kind = LoaderScreenKind.fromWireName(LoaderJson.nonEmpty(
                    screen, "kind", LoaderJson.MAX_IDENTIFIER_BYTES, "Loader screen kind"));
            String title = LoaderJson.nonEmpty(
                    screen, "title", LoaderScreenDefinition.MAX_TITLE_BYTES, "Loader screen title");
            Optional<String> itemId = LoaderJson.optionalIdentifier(
                    screen, "item_id", "Loader screen item id");
            Optional<String> blockId = LoaderJson.optionalIdentifier(
                    screen, "block_id", "Loader screen block id");
            if (!screenIds.add(id)) {
                throw new IllegalArgumentException(
                        "Loader archive contains duplicate screen id " + id);
            }
            screens.add(new LoaderScreenDefinition(
                    id, kind, title, itemId, blockId, widgets(screen), inputBindings(screen, kind)));
        }
        return List.copyOf(screens);
    }

    /**
     * The optional {@code input_bindings} of one screen: a bounded, key-unique
     * and action-distinct declaration of native keyboard edges. Only a HUD
     * screen may declare bindings; every name is validated here and resolved
     * against the client's native input table before a view is installed.
     */
    private static List<LoaderInputBinding> inputBindings(
            JsonObject screen,
            LoaderScreenKind kind) {
        List<JsonElement> entries = LoaderJson.optionalArray(
                screen,
                "input_bindings",
                LoaderInputBinding.MAX_BINDINGS_PER_SCREEN,
                "Loader screen input bindings");
        if (entries.isEmpty()) {
            return List.of();
        }
        if (kind != LoaderScreenKind.HUD) {
            throw new IllegalArgumentException(
                    "Loader input bindings require the hud screen kind");
        }
        List<LoaderInputBinding> bindings = new ArrayList<>(entries.size());
        Set<String> keys = new HashSet<>();
        Set<String> actions = new HashSet<>();
        for (JsonElement entry : entries) {
            JsonObject binding = LoaderJson.object(entry, "Loader input binding");
            LoaderJson.rejectUnknown(binding, INPUT_BINDING_FIELDS, "Loader input binding");
            String key = bindingKey(binding);
            if (!keys.add(key)) {
                throw new IllegalArgumentException(
                        "Loader screen repeats input binding key " + key);
            }
            String pressAction = LoaderJson.identifier(
                    binding, "press_action", "Loader input binding press action");
            String releaseAction = LoaderJson.identifier(
                    binding, "release_action", "Loader input binding release action");
            if (!actions.add(pressAction) || !actions.add(releaseAction)) {
                throw new IllegalArgumentException(
                        "Loader screen repeats input binding action id");
            }
            bindings.add(new LoaderInputBinding(key, pressAction, releaseAction));
        }
        return List.copyOf(bindings);
    }

    private static String bindingKey(JsonObject binding) {
        String key = LoaderJson.nonEmpty(
                binding, "key", LoaderInputBinding.MAX_KEY_BYTES, "Loader input binding key");
        if (!key.startsWith(LoaderInputBinding.KEY_PREFIX)) {
            throw new IllegalArgumentException(
                    "Loader input binding key must be a native "
                            + LoaderInputBinding.KEY_PREFIX
                            + "* name");
        }
        if (!key.substring(LoaderInputBinding.KEY_PREFIX.length())
                .matches("[a-z0-9]+(\\.[a-z0-9]+)*")) {
            throw new IllegalArgumentException(
                    "Loader input binding key is not a canonical native keyboard name");
        }
        return key;
    }

    private static List<LoaderWidget> widgets(JsonObject screen) {
        List<JsonElement> entries = LoaderJson.array(
                screen,
                "widgets",
                LoaderWidget.MAX_WIDGETS_PER_SCREEN,
                "Loader screen widgets");
        List<LoaderWidget> widgets = new ArrayList<>(entries.size());
        Set<String> widgetIds = new HashSet<>();
        for (JsonElement entry : entries) {
            JsonObject widget = LoaderJson.object(entry, "Loader widget");
            widgets.add(widget(widget, widgetIds));
        }
        return List.copyOf(widgets);
    }

    private static LoaderWidget widget(JsonObject widget, Set<String> widgetIds) {
        String type = LoaderJson.nonEmpty(
                widget, "type", LoaderJson.MAX_IDENTIFIER_BYTES, "Loader widget type");
        return switch (type) {
            case "paged_table" -> {
                LoaderJson.rejectUnknown(widget, PAGED_TABLE_FIELDS, "Loader paged_table widget");
                yield new LoaderWidget.PagedTable(widgetId(widget, widgetIds), columns(widget));
            }
            case "tabs" -> {
                LoaderJson.rejectUnknown(widget, TABS_FIELDS, "Loader tabs widget");
                yield new LoaderWidget.Tabs(
                        widgetId(widget, widgetIds), entries(widget, "entries", "Loader tab entry"));
            }
            case "input_number" -> {
                LoaderJson.rejectUnknown(widget, INPUT_NUMBER_FIELDS, "Loader input_number widget");
                yield inputNumber(widget, widgetIds);
            }
            case "input_text" -> {
                LoaderJson.rejectUnknown(widget, INPUT_TEXT_FIELDS, "Loader input_text widget");
                yield new LoaderWidget.InputText(
                        widgetId(widget, widgetIds),
                        label(widget),
                        (int) LoaderJson.integer(
                                widget,
                                "max_bytes",
                                LoaderWidget.MIN_INPUT_TEXT_BYTES,
                                LoaderWidget.MAX_INPUT_TEXT_BYTES,
                                "Loader input_text max bytes"));
            }
            case "select_enum" -> {
                LoaderJson.rejectUnknown(widget, SELECT_ENUM_FIELDS, "Loader select_enum widget");
                yield new LoaderWidget.SelectEnum(
                        widgetId(widget, widgetIds),
                        label(widget),
                        entries(widget, "options", "Loader select option"));
            }
            case "resource_panel" -> {
                LoaderJson.rejectUnknown(
                        widget, RESOURCE_PANEL_FIELDS, "Loader resource_panel widget");
                yield new LoaderWidget.ResourcePanel(
                        widgetId(widget, widgetIds),
                        label(widget),
                        entries(widget, "entries", "Loader resource entry"));
            }
            case "action_button" -> {
                LoaderJson.rejectUnknown(
                        widget, ACTION_BUTTON_FIELDS, "Loader action_button widget");
                yield new LoaderWidget.ActionButton(
                        LoaderJson.identifier(widget, "action_id", "Loader action button action id"),
                        label(widget),
                        LoaderJson.optionalFlag(widget, "enabled", "Loader action button enabled")
                                .orElse(true),
                        LoaderJson.optionalText(
                                widget,
                                "deny_reason",
                                LoaderWidget.MAX_DENY_REASON_BYTES,
                                "Loader action button deny reason"));
            }
            case "world_marker" -> {
                LoaderJson.rejectUnknown(
                        widget, WORLD_MARKER_FIELDS, "Loader world_marker widget");
                yield new LoaderWidget.WorldMarker(
                        widgetId(widget, widgetIds),
                        label(widget),
                        LoaderJson.identifier(widget, "action_id", "Loader world marker action id"),
                        LoaderJson.optionalIdentifier(
                                widget, "preview_id", "Loader world marker preview id"),
                        LoaderJson.optionalFormation(
                                widget, "formation", "Loader world marker formation"),
                        LoaderJson.optionalNumber(
                                widget,
                                "radius",
                                0.0,
                                LoaderWidget.MAX_MARKER_RADIUS,
                                "Loader world marker radius"));
            }
            default -> throw new IllegalArgumentException(
                    "Loader widget contains unknown type " + type);
        };
    }

    private static String widgetId(JsonObject widget, Set<String> widgetIds) {
        String id = LoaderJson.identifier(widget, "id", "Loader widget id");
        if (!widgetIds.add(id)) {
            throw new IllegalArgumentException("Loader screen repeats widget id " + id);
        }
        return id;
    }

    private static String label(JsonObject widget) {
        return LoaderJson.nonEmpty(
                widget, "label", LoaderWidget.MAX_LABEL_BYTES, "Loader widget label");
    }

    private static LoaderWidget.InputNumber inputNumber(
            JsonObject widget,
            Set<String> widgetIds) {
        String id = widgetId(widget, widgetIds);
        Optional<Double> min = LoaderJson.optionalNumber(widget, "min", "Loader input_number min");
        Optional<Double> max = LoaderJson.optionalNumber(widget, "max", "Loader input_number max");
        if (min.isPresent() && max.isPresent() && min.orElseThrow() > max.orElseThrow()) {
            throw new IllegalArgumentException("Loader input_number min exceeds its max");
        }
        Optional<Double> step = LoaderJson.optionalNumber(widget, "step", "Loader input_number step");
        if (step.isPresent() && step.orElseThrow() <= 0.0) {
            throw new IllegalArgumentException("Loader input_number step must be positive");
        }
        return new LoaderWidget.InputNumber(id, label(widget), min, max, step);
    }

    private static List<LoaderWidget.Column> columns(JsonObject table) {
        List<JsonElement> entries = LoaderJson.array(
                table, "columns", LoaderWidget.MAX_COLUMNS, "Loader paged_table columns");
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Loader paged_table requires at least one column");
        }
        List<LoaderWidget.Column> columns = new ArrayList<>(entries.size());
        Set<String> columnIds = new HashSet<>();
        for (JsonElement entry : entries) {
            JsonObject column = LoaderJson.object(entry, "Loader column");
            LoaderJson.rejectUnknown(column, COLUMN_FIELDS, "Loader column");
            String id = LoaderJson.identifier(column, "id", "Loader column id");
            if (!columnIds.add(id)) {
                throw new IllegalArgumentException("Loader paged_table repeats column id " + id);
            }
            columns.add(new LoaderWidget.Column(
                    id,
                    LoaderJson.nonEmpty(
                            column, "label", LoaderWidget.MAX_LABEL_BYTES, "Loader column label"),
                    LoaderWidget.Align.fromWireName(LoaderJson.nonEmpty(
                            column, "align", LoaderJson.MAX_IDENTIFIER_BYTES, "Loader column align")),
                    (int) LoaderJson.integer(
                            column,
                            "width_bucket",
                            LoaderWidget.MIN_WIDTH_BUCKET,
                            LoaderWidget.MAX_WIDTH_BUCKET,
                            "Loader column width bucket")));
        }
        return List.copyOf(columns);
    }

    private static List<LoaderWidget.Entry> entries(
            JsonObject widget,
            String field,
            String name) {
        List<JsonElement> values = LoaderJson.array(
                widget, field, LoaderWidget.MAX_ENTRIES, "Loader widget " + field);
        if (values.isEmpty()) {
            throw new IllegalArgumentException(name + " requires at least one entry");
        }
        List<LoaderWidget.Entry> entries = new ArrayList<>(values.size());
        Set<String> ids = new HashSet<>();
        for (JsonElement value : values) {
            JsonObject entry = LoaderJson.object(value, name);
            LoaderJson.rejectUnknown(entry, ENTRY_FIELDS, name);
            String id = LoaderJson.identifier(entry, "id", name + " id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException(name + " repeats id " + id);
            }
            entries.add(new LoaderWidget.Entry(
                    id,
                    LoaderJson.nonEmpty(entry, "label", LoaderWidget.MAX_LABEL_BYTES, name + " label")));
        }
        return List.copyOf(entries);
    }

    private static List<LoaderWorldPreviewDefinition> worldPreviews(JsonObject index) {
        List<JsonElement> entries = LoaderJson.optionalArray(
                index, "world_previews", MAX_WORLD_PREVIEWS, "Loader world preview index");
        List<LoaderWorldPreviewDefinition> previews = new ArrayList<>(entries.size());
        Set<String> previewIds = new HashSet<>();
        for (JsonElement entry : entries) {
            JsonObject preview = LoaderJson.object(entry, "Loader world preview");
            LoaderJson.rejectUnknown(preview, PREVIEW_FIELDS, "Loader world preview");
            String id = LoaderJson.identifier(preview, "id", "Loader world preview id");
            String blueprintId = LoaderJson.identifier(
                    preview, "blueprint_id", "Loader world preview blueprint id");
            requireNamespaced(blueprintId, "Loader world preview blueprint id");
            String contentHash = LoaderJson.nonEmpty(
                    preview, "content_hash", 64, "Loader world preview content hash");
            if (!contentHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Loader world preview content hash must be 64 lowercase hexadecimal characters");
            }
            int rotation = (int) LoaderJson.integer(
                    preview,
                    "rotation",
                    0,
                    LoaderWorldPreviewDefinition.MAX_ROTATION,
                    "Loader world preview rotation");
            int sizeX = axis(preview, "size_x");
            int sizeY = axis(preview, "size_y");
            int sizeZ = axis(preview, "size_z");
            if (!previewIds.add(id)) {
                throw new IllegalArgumentException(
                        "Loader archive contains duplicate world preview id " + id);
            }
            previews.add(new LoaderWorldPreviewDefinition(
                    id,
                    blueprintId,
                    contentHash,
                    rotation,
                    sizeX,
                    sizeY,
                    sizeZ,
                    localBlocks(preview, sizeX, sizeY, sizeZ)));
        }
        return List.copyOf(previews);
    }

    private static int axis(JsonObject preview, String field) {
        return (int) LoaderJson.integer(
                preview, field, 0, LoaderWorldPreviewDefinition.MAX_AXIS, "Loader world preview " + field);
    }

    private static List<LoaderWorldPreviewDefinition.Block> localBlocks(
            JsonObject preview,
            int sizeX,
            int sizeY,
            int sizeZ) {
        List<JsonElement> entries = LoaderJson.array(
                preview,
                "blocks",
                LoaderWorldPreviewDefinition.MAX_BLOCKS,
                "Loader world preview blocks");
        List<LoaderWorldPreviewDefinition.Block> blocks = new ArrayList<>(entries.size());
        for (JsonElement entry : entries) {
            JsonObject block = LoaderJson.object(entry, "Loader world preview block");
            LoaderJson.rejectUnknown(block, LOCAL_BLOCK_FIELDS, "Loader world preview block");
            int x = (int) LoaderJson.integer(block, "x", 0, sizeX - 1, "Loader world preview block x");
            int y = (int) LoaderJson.integer(block, "y", 0, sizeY - 1, "Loader world preview block y");
            int z = (int) LoaderJson.integer(block, "z", 0, sizeZ - 1, "Loader world preview block z");
            String blockId = LoaderJson.identifier(
                    block, "block_id", "Loader world preview block id");
            requireNamespaced(blockId, "Loader world preview block id");
            blocks.add(new LoaderWorldPreviewDefinition.Block(x, y, z, blockId));
        }
        return List.copyOf(blocks);
    }

    private static List<BlockIndex> blocks(JsonObject index) {
        List<JsonElement> entries = LoaderJson.optionalArray(
                index, "blocks", MAX_BLOCKS_PER_BUNDLE, "Loader block index");
        List<BlockIndex> blocks = new ArrayList<>(entries.size());
        for (JsonElement entry : entries) {
            JsonObject block = LoaderJson.object(entry, "Loader block");
            LoaderJson.rejectUnknown(block, BLOCK_FIELDS, "Loader block");
            blocks.add(new BlockIndex(
                    LoaderJson.identifier(block, "id", "Loader block id"),
                    LoaderJson.identifier(block, "model", "Loader block model"),
                    LoaderJson.nonEmpty(
                            block,
                            "name",
                            LoaderScreenDefinition.MAX_TITLE_BYTES,
                            "Loader block name")));
        }
        return List.copyOf(blocks);
    }

    private static List<ItemIndex> items(JsonObject index) {
        List<JsonElement> entries = LoaderJson.optionalArray(index, "items", MAX_ITEMS, "Loader item index");
        List<ItemIndex> items = new ArrayList<>(entries.size());
        for (JsonElement entry : entries) {
            JsonObject item = LoaderJson.object(entry, "Loader item");
            LoaderJson.rejectUnknown(item, ITEM_FIELDS, "Loader item");
            items.add(new ItemIndex(
                    LoaderJson.identifier(item, "id", "Loader item id"),
                    LoaderJson.identifier(item, "base_item", "Loader item base item"),
                    LoaderJson.nonEmpty(
                            item,
                            "name",
                            LoaderScreenDefinition.MAX_TITLE_BYTES,
                            "Loader item name")));
        }
        return List.copyOf(items);
    }

    private static List<AssetIndex> assets(JsonObject index) {
        List<JsonElement> entries = LoaderJson.optionalArray(index, "assets", MAX_ASSETS, "Loader asset index");
        List<AssetIndex> assets = new ArrayList<>(entries.size());
        for (JsonElement entry : entries) {
            JsonObject asset = LoaderJson.object(entry, "Loader asset");
            LoaderJson.rejectUnknown(asset, ASSET_FIELDS, "Loader asset");
            assets.add(new AssetIndex(
                    LoaderJson.identifier(asset, "id", "Loader asset id"),
                    LoaderJson.nonEmpty(
                            asset, "path", MAX_ARCHIVE_PATH_BYTES, "Loader asset path"),
                    LoaderJson.nonEmpty(asset, "sha256", 64, "Loader asset SHA-256"),
                    LoaderJson.integer(
                            asset,
                            "size_bytes",
                            1,
                            MAX_ACTIVATED_ASSET_BYTES,
                            "Loader asset size")));
        }
        return List.copyOf(assets);
    }

    private static List<SoundIndex> sounds(JsonObject index) {
        List<JsonElement> entries = LoaderJson.optionalArray(index, "sounds", MAX_SOUNDS, "Loader sound index");
        List<SoundIndex> sounds = new ArrayList<>(entries.size());
        for (JsonElement entry : entries) {
            JsonObject sound = LoaderJson.object(entry, "Loader sound");
            LoaderJson.rejectUnknown(sound, SOUND_FIELDS, "Loader sound");
            sounds.add(new SoundIndex(LoaderJson.identifier(sound, "id", "Loader sound id")));
        }
        return List.copyOf(sounds);
    }

    static void ensureRegistryBounds(
            int screenCount,
            int worldPreviewCount,
            int blockCount,
            int itemCount,
            int assetCount,
            long assetBytes) {
        if (screenCount > MAX_SCREENS
                || worldPreviewCount > MAX_WORLD_PREVIEWS
                || blockCount > MAX_ACTIVATED_BLOCKS
                || itemCount > MAX_ITEMS
                || assetCount > MAX_ASSETS) {
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

    private static long validateIndex(
            LoaderBundle bundle,
            ArchiveIndex index) {
        boolean viewsDeclared = declared(bundle, LoaderContentKind.VIEWS);
        boolean previewsDeclared = declared(bundle, LoaderContentKind.WORLD_PREVIEWS);
        boolean blocksDeclared = declared(bundle, LoaderContentKind.BLOCKS);
        boolean itemsDeclared = declared(bundle, LoaderContentKind.ITEMS);
        boolean assetsDeclared = declared(bundle, LoaderContentKind.ASSETS);
        boolean soundsDeclared = declared(bundle, LoaderContentKind.SOUNDS);
        if (viewsDeclared != !index.screens().isEmpty()) {
            throw new IllegalArgumentException(
                    "Loader screen index does not match the bundle content declaration");
        }
        if (viewsDeclared
                && !bundle.permissions().contains(LoaderPermission.PRESENT_VIEWS)) {
            throw new IllegalArgumentException(
                    "Loader screen content requires present_views permission");
        }
        if (previewsDeclared != !index.worldPreviews().isEmpty()
                || previewsDeclared
                        && !bundle.permissions().contains(LoaderPermission.PRESENT_WORLD_PREVIEWS)) {
            throw new IllegalArgumentException(
                    "Loader world previews require matching content and present_world_previews");
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
        if (soundsDeclared != !index.sounds().isEmpty()
                || soundsDeclared && !bundle.permissions().contains(LoaderPermission.PLAY_SOUNDS)) {
            throw new IllegalArgumentException("Loader sounds require matching content and play_sounds");
        }

        Set<String> ids = new HashSet<>();
        Set<String> blockIds = new HashSet<>();
        Set<String> itemIds = new HashSet<>();
        for (LoaderScreenDefinition screen : index.screens()) {
            requireOwnedIdentifier(screen.id(), bundle.owner(), "screen id");
            if (!ids.add(screen.id())) {
                throw new IllegalArgumentException(
                        "Loader archive contains duplicate content id " + screen.id());
            }
        }
        Set<String> assetPaths = new HashSet<>();
        for (AssetIndex asset : index.assets()) {
            assetPaths.add(asset.path());
        }
        for (BlockIndex block : index.blocks()) {
            requireOwnedIdentifier(block.id(), bundle.owner(), "block id");
            requireOwnedIdentifier(block.model(), bundle.owner(), "block model");
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
        Set<String> previewIds = new HashSet<>();
        for (LoaderWorldPreviewDefinition preview : index.worldPreviews()) {
            previewIds.add(preview.id());
        }
        for (LoaderScreenDefinition screen : index.screens()) {
            if (screen.itemId().isPresent()) {
                String itemId = screen.itemId().orElseThrow();
                requireOwnedIdentifier(itemId, bundle.owner(), "screen item id");
                if (!itemIds.contains(itemId)) {
                    throw new IllegalArgumentException(
                            "Loader screen references an undeclared item " + itemId);
                }
            }
            if (screen.blockId().isPresent()) {
                String blockId = screen.blockId().orElseThrow();
                requireOwnedIdentifier(blockId, bundle.owner(), "screen block id");
                if (!blockIds.contains(blockId)) {
                    throw new IllegalArgumentException(
                            "Loader screen references an undeclared block " + blockId);
                }
            }
            for (LoaderWidget widget : screen.widgets()) {
                if (widget instanceof LoaderWidget.WorldMarker marker
                        && marker.previewId().isPresent()
                        && !previewIds.contains(marker.previewId().orElseThrow())) {
                    throw new IllegalArgumentException(
                            "Loader screen references an undeclared world preview "
                                    + marker.previewId().orElseThrow());
                }
            }
            if (screen.inputBindings().isEmpty()) {
                continue;
            }
            if (!declared(bundle, LoaderContentKind.VIEW_ACTIONS)
                    || !bundle.permissions().contains(LoaderPermission.SEND_VIEW_ACTIONS)) {
                throw new IllegalArgumentException(
                        "Loader input bindings require view_actions content and "
                                + "send_view_actions permission");
            }
            for (LoaderInputBinding binding : screen.inputBindings()) {
                requireOwnedIdentifier(
                        binding.pressAction(), bundle.owner(), "input binding press action");
                requireOwnedIdentifier(
                        binding.releaseAction(), bundle.owner(), "input binding release action");
            }
        }
        for (LoaderWorldPreviewDefinition preview : index.worldPreviews()) {
            requireOwnedIdentifier(preview.id(), bundle.owner(), "world preview id");
            if (!ids.add(preview.id())) {
                throw new IllegalArgumentException(
                        "Loader archive contains duplicate content id " + preview.id());
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
            if (!asset.sha256().matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Loader asset SHA-256 must be lowercase hexadecimal");
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
        return totalAssetBytes;
    }

    private static boolean declared(LoaderBundle bundle, LoaderContentKind kind) {
        return bundle.content().contains(kind);
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
        requireText(value, LoaderJson.MAX_IDENTIFIER_BYTES, name);
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

    private static void requireNamespaced(String value, String name) {
        int separator = value.indexOf(':');
        if (separator <= 0
                || separator == value.length() - 1
                || value.indexOf(':', separator + 1) >= 0) {
            throw new IllegalArgumentException(name + " must be a namespaced identifier");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean allowed = character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '_'
                    || character == '.'
                    || character == '-'
                    || character == '/';
            if (!allowed && !(index == separator && character == ':')) {
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
        if (value == null
                || value.isEmpty()
                || value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException(
                    name + " must contain 1..=" + maxBytes + " bytes");
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
            List<LoaderScreenDefinition> screens,
            List<LoaderWorldPreviewDefinition> worldPreviews,
            List<BlockIndex> blocks,
            List<ItemIndex> items,
            List<AssetIndex> assets,
            List<SoundIndex> sounds) {
    }

    private record SoundIndex(String id) {
    }

    private record BlockIndex(
            String id,
            String model,
            String name) {
    }

    private record ItemIndex(
            String id,
            String baseItem,
            String name) {
    }

    private record AssetIndex(
            String id,
            String path,
            String sha256,
            long sizeBytes) {
    }
}
