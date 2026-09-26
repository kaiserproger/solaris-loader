package dev.solaris.loader.minecraft;

import com.mojang.blaze3d.platform.InputConstants;
import dev.solaris.loader.LoaderInputBinding;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
            InputConstants.Key key;
            try {
                key = InputConstants.getKey(binding.key());
            } catch (IllegalArgumentException invalidKey) {
                return Optional.empty();
            }
            if (key.getType() != InputConstants.Type.KEYSYM
                    || key.getValue() < 0
                    || !key.getName().equals(binding.key())
                    || resolved.putIfAbsent(key.getValue(), binding) != null) {
                return Optional.empty();
            }
        }
        return Optional.of(resolved);
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
    }

    /** The client's own gameplay-input focus: in play, no GUI, no overlay, active window. */
    private static boolean acceptsInput(Minecraft client) {
        return client.player != null
                && client.screen == null
                && client.getOverlay() == null
                && client.isWindowActive();
    }
}
