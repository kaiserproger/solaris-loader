package dev.solaris.loader;

import java.util.List;
import java.util.Map;

public record LoaderActivatedContent(
        List<String> cacheKeys,
        Map<String, LoaderUiDefinition> ui,
        Map<String, LoaderBlockDefinition> blocks,
        Map<String, LoaderItemDefinition> items,
        Map<String, LoaderAssetDefinition> assets,
        Map<String, LoaderInteractionDefinition> interactions,
        Map<String, LoaderSoundDefinition> sounds) {
    private static final LoaderActivatedContent EMPTY =
            new LoaderActivatedContent(
                    List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    public LoaderActivatedContent {
        cacheKeys = List.copyOf(cacheKeys);
        ui = Map.copyOf(ui);
        blocks = Map.copyOf(blocks);
        items = Map.copyOf(items);
        assets = Map.copyOf(assets);
        interactions = Map.copyOf(interactions);
        sounds = Map.copyOf(sounds);
    }

    public static LoaderActivatedContent empty() {
        return EMPTY;
    }
}
