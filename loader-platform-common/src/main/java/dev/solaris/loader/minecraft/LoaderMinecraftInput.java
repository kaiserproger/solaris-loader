package dev.solaris.loader.minecraft;

import com.mojang.blaze3d.platform.InputConstants;
import dev.solaris.loader.LoaderActivatedContent;
import dev.solaris.loader.LoaderInputBinding;
import dev.solaris.loader.LoaderScreenDefinition;
import dev.solaris.loader.LoaderViewKey;
import dev.solaris.loader.LoaderViewRequestBinding;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;

/**
 * The native client-thread lifecycle of the Loader HUD key bindings: the
 * adapters only feed vanilla's own key and focus entry points, and every
 * decision stays here.
 *
 * <p>Vanilla consumes several keys before a mod can see them: F2 writes a
 * screenshot and F11 toggles fullscreen, both returning early, and Escape
 * either closes the GUI that already had focus or opens the pause screen.
 * The head of {@code KeyboardHandler#keyPress} therefore captures the focus a
 * key edge arrived with, and the return point decides after vanilla handled it:
 * an edge is admitted only when no GUI had focus and none took focus, so a key
 * that closed or opened a menu never reaches a gameplay binding.
 */
public final class LoaderMinecraftInput {
    private static boolean inputFocusedBefore;
    private static Map<Integer, LoaderViewKey> requestKeys = Map.of();
    private static Consumer<LoaderViewKey> requester = ignored -> { };

    private LoaderMinecraftInput() {
    }

    /**
     * Resolve every declared binding through the client's own key table before
     * a HUD is installed: an unknown, unnamed or duplicated key refuses the
     * whole view instead of guessing a native key.
     */
    static Optional<Map<Integer, LoaderInputBinding>> resolve(
            List<LoaderInputBinding> declared) {
        Map<Integer, LoaderInputBinding> resolved = new LinkedHashMap<>();
        for (LoaderInputBinding binding : declared) {
            OptionalInt key = nativeKey(binding.key());
            if (key.isEmpty() || resolved.putIfAbsent(key.getAsInt(), binding) != null) {
                return Optional.empty();
            }
        }
        return Optional.of(resolved);
    }

    /** The client's own keyboard key code for one canonical name, or empty when it has none. */
    private static OptionalInt nativeKey(String name) {
        InputConstants.Key key;
        try {
            key = InputConstants.getKey(name);
        } catch (IllegalArgumentException invalidKey) {
            return OptionalInt.empty();
        }
        if (key.getType() != InputConstants.Type.KEYBOARD
                || key.getValue() < 0
                || !key.getName().equals(name)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(key.getValue());
    }

    /**
     * Refuse activated content whose declared view-request keys this client
     * cannot name natively, before any acknowledgement arms them: a key that
     * would silently never fire is a refused declaration, not a dead binding.
     */
    static void validate(LoaderActivatedContent content) {
        resolveRequestKeys(content);
    }

    /**
     * Arm every overlay-declared view-request key of the activated content.
     * The key edge sends the exact declared screen id through {@code requester}
     * (the adapter's active-connection {@code view_request} sender); no live
     * view instance is needed, and {@link #clear()} disarms them on unmount.
     */
    public static void armRequestKeys(
            LoaderActivatedContent content,
            Consumer<LoaderViewKey> requester) {
        requestKeys = resolveRequestKeys(content);
        LoaderMinecraftInput.requester = requester;
    }

    private static Map<Integer, LoaderViewKey> resolveRequestKeys(LoaderActivatedContent content) {
        Map<Integer, LoaderViewKey> resolved = new LinkedHashMap<>();
        for (LoaderScreenDefinition screen : content.screens().values()) {
            for (LoaderViewRequestBinding binding : screen.requestBindings()) {
                OptionalInt key = nativeKey(binding.key());
                if (key.isEmpty() || resolved.putIfAbsent(key.getAsInt(), binding.view()) != null) {
                    throw new IllegalArgumentException(
                            "Loader view request key " + binding.key()
                                    + " is not a unique native keyboard key of this client");
                }
            }
        }
        return Map.copyOf(resolved);
    }

    /**
     * One admitted press of a native key: when it is an armed view-request key
     * the declared screen is requested. Returns whether a request was issued.
     */
    static boolean requestKeyEdge(int key) {
        LoaderViewKey view = requestKeys.get(key);
        if (view == null) {
            return false;
        }
        requester.accept(view);
        return true;
    }

    /** {@code KeyboardHandler#keyPress} head: the focus this native key edge arrived with. */
    public static void beforeKeyPress() {
        Minecraft client = Minecraft.getInstance();
        inputFocusedBefore = client != null && acceptsInput(client);
    }

    /** {@code KeyboardHandler#keyPress} return: one native key edge after vanilla handled it. */
    public static void afterKeyPress(int action, int key, int scancode, int modifiers) {
        boolean keyboardFocused = inputFocusedBefore;
        inputFocusedBefore = false;
        Minecraft client = Minecraft.getInstance();
        if (client == null
                || (action != InputConstants.PRESS && action != InputConstants.RELEASE)) {
            // A held native key that repeats is not a new edge.
            return;
        }
        if (!keyboardFocused || !acceptsInput(client)) {
            LoaderMinecraftView.releaseHeldKeys();
            return;
        }
        if (action == InputConstants.PRESS) {
            KeyEvent edge = new KeyEvent(key, scancode, modifiers);
            if (client.options.keyAttack.matches(edge) || client.options.keyUse.matches(edge)) {
                // Vanilla already handled this remapped attack/use key. Do not
                // send a second gameplay intent through the Loader HUD.
                return;
            }
            requestKeyEdge(key);
        }
        LoaderMinecraftView.keyEdge(key, action == InputConstants.PRESS);
    }

    /**
     * {@code Minecraft#setScreen} head: a GUI takes gameplay focus before the
     * native screen transition completes, so held bindings release first.
     */
    public static void screenChanged(Screen next) {
        if (next != null) {
            LoaderMinecraftView.releaseHeldKeys();
        }
    }

    /** A native window/overlay focus transition releases held bindings immediately. */
    public static void focusLost() {
        LoaderMinecraftView.releaseHeldKeys();
    }

    static void clear() {
        inputFocusedBefore = false;
        requestKeys = Map.of();
        requester = ignored -> { };
    }

    /** The client's own gameplay-input focus: in play, no GUI, no overlay, active window. */
    private static boolean acceptsInput(Minecraft client) {
        return client.player != null
                && client.gui.screen() == null
                && client.gui.overlay() == null
                && client.isWindowActive();
    }
}
