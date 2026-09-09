package dev.solaris.loader;

import java.util.regex.Pattern;

public record LoaderInteractionDefinition(
        String id,
        String uiId,
        String label,
        String payload,
        String key) {
    private static final Pattern KEY = Pattern.compile(
            "key\\.keyboard\\.(?:[a-z0-9]|f(?:[1-9]|1[0-9]|2[0-5])"
                    + "|keypad\\.(?:[0-9]|add|decimal|enter|equal|multiply|divide|subtract)"
                    + "|(?:left|right)\\.(?:alt|control|shift|win|bracket)"
                    + "|(?:num|caps|scroll)\\.lock|page\\.(?:down|up)|world\\.[12]"
                    + "|grave\\.accent|print\\.screen|down|left|right|up|apostrophe"
                    + "|backslash|comma|equal|minus|period|semicolon|slash|space|tab"
                    + "|enter|escape|backspace|delete|end|home|insert|pause|menu)");

    public static boolean isKeyboardKey(String key) {
        return key != null && KEY.matcher(key).matches();
    }
}
