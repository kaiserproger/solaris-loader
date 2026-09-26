package dev.solaris.loader.minecraft;

import dev.solaris.loader.LoaderInputBinding;
import dev.solaris.loader.LoaderScreenDefinition;
import dev.solaris.loader.LoaderViewActionRequest;
import dev.solaris.loader.LoaderViewModel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * One live non-modal Loader HUD instance: the declared {@code kind=hud} screen,
 * its presented model and the native key edges it owns. Nothing here touches
 * the client's screen stack, so several owners' HUDs stay visible at once and a
 * HUD with no declared widgets renders nothing while still hosting its declared
 * input bindings.
 *
 * <p>A binding is held from the press edge it was admitted for until exactly one
 * release: a native key-up, or the focus loss that took gameplay input away
 * (a GUI, a hidden player or an inactive window) before the physical key-up.
 * Every admitted edge carries the live instance id and revision. Its sequence
 * increases within that revision and restarts when a replacement is presented;
 * only actions enabled by the current model are sent.
 */
final class LoaderHud {
    private final String viewInstanceId;
    private final LoaderScreenDefinition definition;
    private final List<ItemStack> displayItems;
    private final Consumer<byte[]> send;
    private final Map<Integer, LoaderInputBinding> bindings;
    private final Set<Integer> held = new LinkedHashSet<>();
    private long revision;
    private LoaderViewModel model;
    private long sequence;
    private LoaderViewScreen content;
    private int laidOutWidth = -1;
    private int laidOutHeight = -1;
    private int contentLeft;
    private int contentRight;
    private int contentHeight;

    LoaderHud(
            String viewInstanceId,
            long revision,
            LoaderScreenDefinition definition,
            LoaderViewModel model,
            List<ItemStack> displayItems,
            Consumer<byte[]> send,
            Map<Integer, LoaderInputBinding> bindings) {
        this.viewInstanceId = viewInstanceId;
        this.revision = revision;
        this.definition = definition;
        this.model = model;
        this.displayItems = List.copyOf(displayItems);
        this.send = send;
        this.bindings = Map.copyOf(bindings);
    }

    String viewInstanceId() {
        return viewInstanceId;
    }

    /** Replace the presented model: the sequence restarts, held bindings stay held. */
    void present(long revision, LoaderViewModel model) {
        this.revision = revision;
        this.model = model;
        this.sequence = 0;
        if (content != null) {
            content.present(revision, model);
            measure();
        }
    }

    /** Admit one press edge for a held-free binding the presented model enables. */
    boolean press(int key) {
        LoaderInputBinding binding = bindings.get(key);
        if (binding == null || held.contains(key) || !send(binding.pressAction())) {
            return false;
        }
        held.add(key);
        return true;
    }

    /** Admit the release of a binding this instance actually holds. */
    boolean release(int key) {
        if (!held.remove(key)) {
            return false;
        }
        LoaderInputBinding binding = bindings.get(key);
        if (binding != null) {
            send(binding.releaseAction());
        }
        return true;
    }

    /** Focus loss: one release for each held binding, before the physical key-up. */
    int releaseHeld() {
        if (held.isEmpty()) {
            return 0;
        }
        List<Integer> keys = new ArrayList<>(held);
        int released = 0;
        for (int key : keys) {
            if (release(key)) {
                released++;
            }
        }
        return released;
    }

    boolean holds() {
        return !held.isEmpty();
    }

    /** Disconnect/unmount: drop the held edges and the render container, send nothing. */
    void clear() {
        held.clear();
        content = null;
        laidOutWidth = -1;
        laidOutHeight = -1;
    }

    /**
     * Extract this HUD over {@code y} with the shared schema-2 widget renderer
     * and return the next free band. A HUD without declared widgets renders
     * nothing and consumes no band, and a panel that does not fit above
     * {@code limit} ends the layer instead of clipping into the next one.
     */
    int render(GuiGraphicsExtractor graphics, int y, int limit) {
        if (definition.widgets().isEmpty()) {
            return y;
        }
        if (content == null) {
            content = new LoaderViewScreen(
                    viewInstanceId,
                    revision,
                    Component.literal(definition.title()),
                    definition,
                    model,
                    displayItems,
                    Map.of(),
                    send,
                    true);
        }
        if (graphics.guiWidth() != laidOutWidth || graphics.guiHeight() != laidOutHeight) {
            layout(graphics.guiWidth(), graphics.guiHeight());
        }
        if (contentHeight <= 0 || y + contentHeight + 4 > limit) {
            return limit;
        }
        graphics.fill(
                contentLeft - 4,
                y - 4,
                contentRight + 4,
                y + contentHeight + 4,
                0xB0000000);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(0.0F, y - LoaderViewScreen.TOP_MARGIN);
        content.extractRenderState(graphics, -1, -1, 0.0F);
        pose.popMatrix();
        return y + contentHeight + 12;
    }

    private void layout(int width, int height) {
        laidOutWidth = width;
        laidOutHeight = height;
        content.relayout(width, height);
        measure();
    }

    /** The panel bounds of the widgets the shared renderer laid out. */
    private void measure() {
        int left = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = LoaderViewScreen.TOP_MARGIN;
        for (GuiEventListener child : content.children()) {
            if (child instanceof AbstractWidget widget) {
                left = Math.min(left, widget.getX());
                right = Math.max(right, widget.getX() + widget.getWidth());
                bottom = Math.max(bottom, widget.getY() + widget.getHeight());
            }
        }
        contentLeft = left == Integer.MAX_VALUE ? 0 : left;
        contentRight = right == Integer.MIN_VALUE ? 0 : right;
        contentHeight = right == Integer.MIN_VALUE ? 0 : bottom - LoaderViewScreen.TOP_MARGIN;
    }

    private boolean send(String actionId) {
        if (!enabled(actionId)) {
            return false;
        }
        sequence++;
        LoaderViewActionRequest
                .action(viewInstanceId, revision, actionId, sequence, List.of(), Optional.empty())
                .ifPresent(send);
        return true;
    }

    /** The presented model stays authoritative: an undeclared or disabled action sends nothing. */
    private boolean enabled(String actionId) {
        for (LoaderViewModel.Action action : model.actions()) {
            if (action.actionId().equals(actionId)) {
                return action.enabled();
            }
        }
        return false;
    }
}
