package dev.solaris.loader;

import java.util.List;
import java.util.Optional;

/** One validated schema-2 screen of the activated Loader registry. */
public record LoaderScreenDefinition(
        String id,
        LoaderScreenKind kind,
        String title,
        Optional<String> itemId,
        Optional<String> blockId,
        List<LoaderWidget> widgets) {
    public static final int MAX_TITLE_BYTES = 128;

    public LoaderScreenDefinition {
        itemId = itemId == null ? Optional.empty() : itemId;
        blockId = blockId == null ? Optional.empty() : blockId;
        widgets = List.copyOf(widgets);
    }

    public Optional<LoaderWidget> widget(String widgetId) {
        for (LoaderWidget widget : widgets) {
            if (widget.id().equals(widgetId)) {
                return Optional.of(widget);
            }
        }
        return Optional.empty();
    }
}
