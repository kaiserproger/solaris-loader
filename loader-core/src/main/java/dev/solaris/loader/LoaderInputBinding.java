package dev.solaris.loader;

/**
 * One validated native key binding of a schema-2 HUD screen: the press and
 * release actions an owner declares for one keyboard key. The key is a
 * canonical Minecraft {@code key.keyboard.*} name that the platform resolves
 * through the client's own native input table before installing the view;
 * both actions are owner-qualified view action ids carried by the existing
 * wire-3 {@code solaris:loader/view_action} request.
 */
public record LoaderInputBinding(
        String key,
        String pressAction,
        String releaseAction) {
    /** The schema-2 bound of one screen's {@code input_bindings} array. */
    public static final int MAX_BINDINGS_PER_SCREEN = 8;
    /** The prefix shared by every native keyboard key name. */
    public static final String KEY_PREFIX = "key.keyboard.";
    /**
     * The shipped client's native key table tops out at 28 bytes
     * ({@code key.keyboard.keypad.multiply}); 64 bounds a canonical name
     * without enumerating the native table here.
     */
    public static final int MAX_KEY_BYTES = 64;
}
