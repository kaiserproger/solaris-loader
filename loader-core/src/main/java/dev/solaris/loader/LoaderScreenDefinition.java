package dev.solaris.loader;

import java.util.List;
import java.util.Optional;

/**
 * One validated schema-2 plugin view. Overlay presentation is declarative and
 * independent of its opaque view key; only overlays host native input bindings
 * and the key bindings that request another declared screen.
 */
public record LoaderScreenDefinition(
        LoaderViewKey id,
        boolean overlay,
        String title,
        Optional<String> itemId,
        Optional<String> blockId,
        List<LoaderWidget> widgets,
        List<LoaderInputBinding> inputBindings,
        List<LoaderViewRequestBinding> requestBindings) {
    public static final int MAX_TITLE_BYTES = 128;

    public LoaderScreenDefinition {
        itemId = itemId == null ? Optional.empty() : itemId;
        blockId = blockId == null ? Optional.empty() : blockId;
        widgets = List.copyOf(widgets);
        inputBindings = List.copyOf(inputBindings);
        requestBindings = List.copyOf(requestBindings);
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
