package dev.solaris.loader.minecraft;

import dev.solaris.loader.LoaderActivatedContent;
import dev.solaris.loader.LoaderInputBinding;
import dev.solaris.loader.LoaderScreenDefinition;
import dev.solaris.loader.LoaderScreenKind;
import dev.solaris.loader.LoaderViewMessage;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Client-thread presentation of the server-authoritative Loader views shared by
 * all adapters. A modal kind presents one {@link LoaderViewScreen} on the client's
 * screen stack; a {@link LoaderScreenKind#HUD} kind is a non-modal instance that
 * stays visible beside every other owner's HUD and hosts the screen's declared
 * input bindings. Every validation stays in the core codec.
 */
public final class LoaderMinecraftView {
    /** The native HUD layer every adapter registers this presentation under. */
    public static final String HUD_LAYER = "solaris_loader:loader_hud";

    private static final LoaderHudRegistry HUDS = new LoaderHudRegistry();
    private static LoaderViewScreen active;

    private LoaderMinecraftView() {
    }

    /**
     * Present one decoded wire-3 view message. Messages for a view instance that
     * is not the active one never touch the active view, and a HUD never takes,
     * replaces or closes the modal view.
     */
    public static void present(
            LoaderViewMessage message,
            LoaderActivatedContent content,
            Consumer<byte[]> send) {
        Minecraft client = Minecraft.getInstance();
        switch (message) {
            case LoaderViewMessage.Open open -> {
                LoaderScreenDefinition definition = content.screens().get(open.viewId());
                if (definition == null) {
                    return;
                }
                if (definition.kind() == LoaderScreenKind.HUD) {
                    openHud(open, definition, content, send);
                    return;
                }
                close(client);
                LoaderViewScreen screen = new LoaderViewScreen(
                        open.viewInstanceId(),
                        open.revision(),
                        Component.literal(definition.title()),
                        definition,
                        open.model(),
                        LoaderMinecraftDisplay.forScreen(definition, content),
                        content.worldPreviews(),
                        send,
                        false);
                active = screen;
                client.setScreen(screen);
            }
            case LoaderViewMessage.Present present -> {
                if (HUDS.present(present.viewInstanceId(), present.revision(), present.model())
                        == null) {
                    LoaderViewScreen screen = active;
                    if (screen != null && screen.viewInstanceId().equals(present.viewInstanceId())) {
                        screen.present(present.revision(), present.model());
                    }
                }
            }
            case LoaderViewMessage.Close close -> {
                if (!HUDS.close(close.viewInstanceId())) {
                    LoaderViewScreen screen = active;
                    if (screen != null && screen.viewInstanceId().equals(close.viewInstanceId())) {
                        close(client);
                    }
                }
            }
        }
    }

    /**
     * Present one non-modal HUD instance, updating only the exact instance id.
     * The declared bindings are resolved through the client's own key table
     * first: a binding this client cannot name natively refuses the view.
     */
    private static void openHud(
            LoaderViewMessage.Open open,
            LoaderScreenDefinition definition,
            LoaderActivatedContent content,
            Consumer<byte[]> send) {
        Optional<Map<Integer, LoaderInputBinding>> bindings =
                LoaderMinecraftInput.resolve(definition.inputBindings());
        if (bindings.isEmpty()) {
            HUDS.close(open.viewInstanceId());
            return;
        }
        HUDS.open(
                open.viewInstanceId(),
                open.revision(),
                definition,
                open.model(),
                LoaderMinecraftDisplay.forScreen(definition, content),
                send,
                bindings.orElseThrow());
    }

    /** Extract every live HUD into the native HUD layer, in presentation order. */
    public static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker ignoredDelta) {
        if (HUDS.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.options.hideGui) {
            return;
        }
        int y = 8;
        int limit = graphics.guiHeight() - 8;
        for (LoaderHud hud : HUDS.live()) {
            y = hud.render(graphics, y, limit);
        }
    }

    /** One admitted native key edge, delivered to every live HUD that binds it. */
    static void keyEdge(int key, boolean pressed) {
        HUDS.keyEdge(key, pressed);
    }

    /** Focus loss: one release for each held binding, before the physical key-up. */
    static void releaseHeldKeys() {
        if (HUDS.isEmpty() || !HUDS.holds()) {
            return;
        }
        HUDS.releaseHeld();
    }

    /**
     * Clear every presented view and its input state. Adapters call this on
     * resource unmount and disconnect; Minecraft owns modal teardown for a
     * closed screen, and a reconnect starts from no HUD, no binding and no held
     * edge, so old edges cannot replay into the new session.
     */
    public static void clear() {
        close(Minecraft.getInstance());
        HUDS.clear();
        LoaderMinecraftInput.clear();
    }

    static void dismiss(LoaderViewScreen screen) {
        if (active == screen) {
            active = null;
        }
    }

    private static void close(Minecraft client) {
        LoaderViewScreen screen = active;
        active = null;
        if (screen == null) {
            return;
        }
        if (client.screen == screen) {
            client.setScreen(null);
        }
    }
}
