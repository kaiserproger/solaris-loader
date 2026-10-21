package dev.solaris.loader.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.solaris.loader.LoaderActivatedContent;
import dev.solaris.loader.LoaderScreenDefinition;
import dev.solaris.loader.LoaderViewKey;
import dev.solaris.loader.LoaderViewRequestBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Declared view-request keys resolve through the client's own native key table,
 * send the exact declared screen only for an armed key, and disarm on clear.
 */
final class LoaderMinecraftInputTest {
    private static final int C = 67;
    private static final int V = 86;

    @AfterEach
    void disarm() {
        LoaderMinecraftInput.clear();
    }

    @Test
    void anArmedKeyRequestsItsExactDeclaredScreenAndNothingElse() {
        List<LoaderViewKey> requested = new ArrayList<>();
        LoaderMinecraftInput.armRequestKeys(
                content(
                        binding("key.keyboard.c", "example:overview"),
                        binding("key.keyboard.v", "example:squads")),
                requested::add);

        assertTrue(LoaderMinecraftInput.requestKeyEdge(C));
        assertTrue(LoaderMinecraftInput.requestKeyEdge(V));
        assertFalse(LoaderMinecraftInput.requestKeyEdge(71));

        assertEquals(
                List.of(new LoaderViewKey("example:overview"), new LoaderViewKey("example:squads")),
                requested);
    }

    @Test
    void clearDisarmsEveryRequestKey() {
        List<LoaderViewKey> requested = new ArrayList<>();
        LoaderMinecraftInput.armRequestKeys(
                content(binding("key.keyboard.c", "example:overview")), requested::add);

        LoaderMinecraftInput.clear();

        assertFalse(LoaderMinecraftInput.requestKeyEdge(C));
        assertEquals(List.of(), requested);
    }

    @Test
    void contentWithoutRequestBindingsArmsNothing() {
        List<LoaderViewKey> requested = new ArrayList<>();
        LoaderMinecraftInput.armRequestKeys(content(), requested::add);

        assertFalse(LoaderMinecraftInput.requestKeyEdge(C));
        assertEquals(List.of(), requested);
    }

    @Test
    void aKeyThisClientCannotNameNativelyRefusesTheContent() {
        for (String key : List.of("key.keyboard.not_a_key", "key.keyboard.", "key.mouse.left")) {
            LoaderActivatedContent declared = content(binding(key, "example:overview"));
            assertThrows(
                    IllegalArgumentException.class, () -> LoaderMinecraftInput.validate(declared));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> LoaderMinecraftInput.armRequestKeys(declared, ignored -> { }));
        }
        LoaderMinecraftInput.validate(content(binding("key.keyboard.c", "example:overview")));
    }

    private static LoaderViewRequestBinding binding(String key, String view) {
        return new LoaderViewRequestBinding(key, new LoaderViewKey(view));
    }

    private static LoaderActivatedContent content(LoaderViewRequestBinding... bindings) {
        LoaderScreenDefinition overlay = new LoaderScreenDefinition(
                new LoaderViewKey("example:keys"),
                true,
                "Keys",
                Optional.empty(),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(bindings));
        return new LoaderActivatedContent(
                List.of(),
                Map.of(overlay.id().value(), overlay),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of());
    }
}
