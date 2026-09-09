package dev.solaris.loader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** Client-thread key edges for one activated session; never consumes vanilla input. */
public final class LoaderKeyActions {
    private final Map<String, List<LoaderInteractionDefinition>> bindings = new HashMap<>();
    private final Set<String> held = new HashSet<>();

    public LoaderKeyActions(Collection<LoaderInteractionDefinition> definitions) {
        for (LoaderInteractionDefinition definition : definitions) {
            if (definition.key() != null) {
                bindings.computeIfAbsent(definition.key(), ignored -> new ArrayList<>())
                        .add(definition);
            }
        }
    }

    public void key(
            String key,
            boolean pressed,
            BiConsumer<LoaderInteractionDefinition, LoaderInteractionAction.Phase> send) {
        List<LoaderInteractionDefinition> actions = bindings.get(key);
        if (actions == null || !(pressed ? held.add(key) : held.remove(key))) {
            return;
        }
        LoaderInteractionAction.Phase phase = pressed
                ? LoaderInteractionAction.Phase.PRESS : LoaderInteractionAction.Phase.RELEASE;
        for (LoaderInteractionDefinition action : actions) {
            send.accept(action, phase);
        }
    }

    public void releaseAll(
            BiConsumer<LoaderInteractionDefinition, LoaderInteractionAction.Phase> send) {
        if (held.isEmpty()) {
            return;
        }
        var keys = held.iterator();
        while (keys.hasNext()) {
            List<LoaderInteractionDefinition> actions = bindings.get(keys.next());
            keys.remove();
            for (LoaderInteractionDefinition action : actions) {
                send.accept(action, LoaderInteractionAction.Phase.RELEASE);
            }
        }
    }
}
