package dev.solaris.loader;

import java.util.Optional;

public record LoaderUiDefinition(
        String id,
        String title,
        String body,
        Optional<String> itemId,
        Optional<String> blockId) {
    public LoaderUiDefinition {
        itemId = itemId == null ? Optional.empty() : itemId;
        blockId = blockId == null ? Optional.empty() : blockId;
    }
}
