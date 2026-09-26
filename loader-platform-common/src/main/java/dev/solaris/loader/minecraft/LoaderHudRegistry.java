package dev.solaris.loader.minecraft;

import dev.solaris.loader.LoaderInputBinding;
import dev.solaris.loader.LoaderScreenDefinition;
import dev.solaris.loader.LoaderViewModel;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;

/**
 * The live non-modal HUD instances of one connection, keyed by their exact view
 * instance id: several owners' HUDs are visible at once, an update touches only
 * the instance it names, a close removes only that instance, and disconnect
 * drops every instance with its bindings, held edges and action sink.
 */
final class LoaderHudRegistry {
    private final Map<String, LoaderHud> live = new LinkedHashMap<>();

    /** Install or replace exactly one HUD instance. */
    LoaderHud open(
            String viewInstanceId,
            long revision,
            LoaderScreenDefinition definition,
            LoaderViewModel model,
            List<ItemStack> displayItems,
            Consumer<byte[]> send,
            Map<Integer, LoaderInputBinding> bindings) {
        LoaderHud hud = new LoaderHud(
                viewInstanceId, revision, definition, model, displayItems, send, bindings);
        live.put(viewInstanceId, hud);
        return hud;
    }

    /** The instance an update or an edge belongs to, or null when it is not live. */
    LoaderHud hud(String viewInstanceId) {
        return live.get(viewInstanceId);
    }

    /** Replace one live instance's model; null when no such instance is live. */
    LoaderHud present(String viewInstanceId, long revision, LoaderViewModel model) {
        LoaderHud hud = live.get(viewInstanceId);
        if (hud != null) {
            hud.present(revision, model);
        }
        return hud;
    }

    /** Close exactly one instance. */
    boolean close(String viewInstanceId) {
        return live.remove(viewInstanceId) != null;
    }

    boolean isEmpty() {
        return live.isEmpty();
    }

    /** Live instances in presentation order, without copying the map. */
    Collection<LoaderHud> live() {
        return live.values();
    }

    boolean holds() {
        for (LoaderHud hud : live.values()) {
            if (hud.holds()) {
                return true;
            }
        }
        return false;
    }

    /** One admitted native key edge reaches every live instance that binds it. */
    void keyEdge(int key, boolean pressed) {
        for (LoaderHud hud : live.values()) {
            if (pressed) {
                hud.press(key);
            } else {
                hud.release(key);
            }
        }
    }

    /** One release for each binding still held across every live instance. */
    void releaseHeld() {
        for (LoaderHud hud : live.values()) {
            hud.releaseHeld();
        }
    }

    /** Disconnect/unmount: every instance, binding and held edge is dropped. */
    void clear() {
        for (LoaderHud hud : live.values()) {
            hud.clear();
        }
        live.clear();
    }
}
