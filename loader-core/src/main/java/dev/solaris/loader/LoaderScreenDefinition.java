package dev.solaris.loader;

import java.util.List;
import java.util.Optional;

/**
 * One validated schema-2 screen of the activated Loader registry. Input
 * bindings are declared only by {@link LoaderScreenKind#HUD} screens and stay
 * empty for every modal kind.
 */
public record LoaderScreenDefinition(
        String id,
        LoaderScreenKind kind,
        String title,
        Optional<String> itemId,
        Optional<String> blockId,
        List<LoaderWidget> widgets,
        List<LoaderInputBinding> inputBindings) {
    public static final int MAX_TITLE_BYTES = 128;

    public LoaderScreenDefinition {
        itemId = itemId == null ? Optional.empty() : itemId;
        blockId = blockId == null ? Optional.empty() : blockId;
        widgets = List.copyOf(widgets);
        inputBindings = List.copyOf(inputBindings);
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
