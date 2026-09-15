package dev.solaris.loader;

import java.util.List;
import java.util.Map;

/** The immutable, validated registry a live Loader connection activated. */
public record LoaderActivatedContent(
        List<String> cacheKeys,
        Map<String, LoaderScreenDefinition> screens,
        Map<String, LoaderWorldPreviewDefinition> worldPreviews,
        Map<String, LoaderBlockDefinition> blocks,
        Map<String, LoaderItemDefinition> items,
        Map<String, LoaderAssetDefinition> assets,
        Map<String, LoaderSoundDefinition> sounds) {
    private static final LoaderActivatedContent EMPTY =
            new LoaderActivatedContent(
                    List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    public LoaderActivatedContent {
        cacheKeys = List.copyOf(cacheKeys);
        screens = Map.copyOf(screens);
        worldPreviews = Map.copyOf(worldPreviews);
        blocks = Map.copyOf(blocks);
        items = Map.copyOf(items);
        assets = Map.copyOf(assets);
        sounds = Map.copyOf(sounds);
    }

    public static LoaderActivatedContent empty() {
        return EMPTY;
    }
}
