package dev.solaris.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

final class LoaderKeyActionsTest {
    @Test
    void keyEdgesIgnoreRepeatAndReleaseExactlyOnceOnFocusLoss() {
        var input = new LoaderKeyActions(List.of(action("ruby:key", "key.keyboard.g")));
        var phases = new ArrayList<LoaderInteractionAction.Phase>();
        BiConsumer<LoaderInteractionDefinition, LoaderInteractionAction.Phase> send =
                (definition, phase) -> phases.add(phase);
        input.key("key.keyboard.g", false, send);
        input.key("key.keyboard.g", true, send);
        input.key("key.keyboard.g", true, send);
        input.releaseAll(send);
        input.releaseAll(send);
        input.key("key.keyboard.g", false, send);
        input.key("key.keyboard.g", true, send);
        input.key("key.keyboard.g", false, send);
        assertEquals(List.of(
                LoaderInteractionAction.Phase.PRESS, LoaderInteractionAction.Phase.RELEASE,
                LoaderInteractionAction.Phase.PRESS, LoaderInteractionAction.Phase.RELEASE), phases);
    }

    @Test
    void sharedKeyDispatchesBothOwnersWithoutTriggeringUnboundOrUiActions() {
        var input = new LoaderKeyActions(List.of(
                action("ruby:key", "key.keyboard.g"),
                action("sapphire:key", "key.keyboard.g"),
                action("ruby:ui", null)));
        var events = new ArrayList<String>();
        BiConsumer<LoaderInteractionDefinition, LoaderInteractionAction.Phase> send =
                (definition, phase) -> events.add(definition.id() + "/" + phase);
        input.key("key.keyboard.h", true, send);
        input.key("key.keyboard.g", true, send);
        input.key("key.keyboard.g", false, send);
        assertEquals(List.of("ruby:key/PRESS", "sapphire:key/PRESS",
                "ruby:key/RELEASE", "sapphire:key/RELEASE"), events);
    }

    private static LoaderInteractionDefinition action(String id, String key) {
        return new LoaderInteractionDefinition(id, key == null ? "ruby:ui" : null, id, "", key);
    }
}
