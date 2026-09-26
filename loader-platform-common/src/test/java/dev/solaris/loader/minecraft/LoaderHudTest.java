package dev.solaris.loader.minecraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solaris.loader.LoaderInputBinding;
import dev.solaris.loader.LoaderScreenDefinition;
import dev.solaris.loader.LoaderScreenKind;
import dev.solaris.loader.LoaderViewModel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The live HUD input state a real client drives: exact instance/revision/action
 * edges with an increasing per-instance sequence, no repeat and no unheld
 * release, one release per held binding on focus loss, exact-instance
 * update/close and a clean disconnect that cannot replay an old edge.
 */
final class LoaderHudTest {
    private static final int G = 71;
    private static final int SPACE = 32;

    @Test
    void pressAndReleaseCarryTheExactInstanceRevisionAndIncreasingSequence() {
        List<byte[]> sent = new ArrayList<>();
        LoaderHud hud = hud(
                "solaris:view-1",
                7L,
                model(action("ruby-live:key_press", true), action("ruby-live:key_release", true)),
                Map.of(G, binding("key.keyboard.g", "ruby-live:key_press", "ruby-live:key_release")),
                sent);

        assertTrue(hud.press(G));
        assertTrue(hud.release(G));

        assertEquals(2, sent.size());
        JsonObject press = decode(sent.get(0));
        assertEquals(3, press.get("protocol").getAsInt());
        assertEquals("view_action", press.get("message").getAsString());
        assertEquals("solaris:view-1", press.get("view_instance_id").getAsString());
        assertEquals(7L, press.get("view_revision").getAsLong());
        assertEquals("ruby-live:key_press", press.get("action_id").getAsString());
        assertEquals(1L, press.get("action_sequence").getAsLong());
        JsonObject release = decode(sent.get(1));
        assertEquals("ruby-live:key_release", release.get("action_id").getAsString());
        assertEquals(7L, release.get("view_revision").getAsLong());
        assertEquals(2L, release.get("action_sequence").getAsLong());
    }

    @Test
    void aRepeatedPressAndAnUnheldReleaseSendNothing() {
        List<byte[]> sent = new ArrayList<>();
        LoaderHud hud = hud(
                "solaris:view-1",
                1L,
                model(action("ruby-live:key_press", true), action("ruby-live:key_release", true)),
                Map.of(G, binding("key.keyboard.g", "ruby-live:key_press", "ruby-live:key_release")),
                sent);

        assertTrue(hud.press(G));
        assertFalse(hud.press(G));
        assertTrue(hud.release(G));
        assertFalse(hud.release(G));
        assertEquals(2, sent.size());
    }

    @Test
    void focusLossReleasesEveryHeldBindingExactlyOnceAndSwallowsThePhysicalKeyUp() {
        List<byte[]> sent = new ArrayList<>();
        Map<Integer, LoaderInputBinding> bindings = new LinkedHashMap<>();
        bindings.put(G, binding("key.keyboard.g", "ruby-live:key_press", "ruby-live:key_release"));
        bindings.put(
                SPACE,
                binding("key.keyboard.space", "ruby-live:jump_press", "ruby-live:jump_release"));
        LoaderHud hud = hud(
                "solaris:view-1",
                3L,
                model(
                        action("ruby-live:key_press", true),
                        action("ruby-live:key_release", true),
                        action("ruby-live:jump_press", true),
                        action("ruby-live:jump_release", true)),
                bindings,
                sent);

        assertTrue(hud.press(G));
        assertTrue(hud.press(SPACE));

        assertEquals(2, hud.releaseHeld());
        assertEquals(0, hud.releaseHeld());

        assertFalse(hud.release(G));
        assertFalse(hud.release(SPACE));
        assertEquals(4, sent.size());
        assertEquals("ruby-live:key_release", decode(sent.get(2)).get("action_id").getAsString());
        assertEquals("ruby-live:jump_release", decode(sent.get(3)).get("action_id").getAsString());
    }

    @Test
    void anUndeclaredOrDisabledModelActionSendsNothing() {
        List<byte[]> sent = new ArrayList<>();
        LoaderHud hud = hud(
                "solaris:view-1",
                1L,
                model(action("ruby-live:key_press", false)),
                Map.of(G, binding("key.keyboard.g", "ruby-live:key_press", "ruby-live:key_release")),
                sent);

        assertFalse(hud.press(G));
        assertFalse(hud.release(G));
        assertTrue(sent.isEmpty());
    }

    @Test
    void aPresentedModelRestartsTheSequenceOnItsNewRevision() {
        List<byte[]> sent = new ArrayList<>();
        LoaderHud hud = hud(
                "solaris:view-1",
                1L,
                model(action("ruby-live:key_press", true), action("ruby-live:key_release", true)),
                Map.of(G, binding("key.keyboard.g", "ruby-live:key_press", "ruby-live:key_release")),
                sent);

        assertTrue(hud.press(G));
        hud.present(9L, model(
                action("ruby-live:key_press", true), action("ruby-live:key_release", true)));
        assertTrue(hud.release(G));
        assertTrue(hud.press(G));

        JsonObject released = decode(sent.get(1));
        assertEquals("ruby-live:key_release", released.get("action_id").getAsString());
        assertEquals(9L, released.get("view_revision").getAsLong());
        assertEquals(1L, released.get("action_sequence").getAsLong());
        JsonObject pressed = decode(sent.get(2));
        assertEquals(9L, pressed.get("view_revision").getAsLong());
        assertEquals(2L, pressed.get("action_sequence").getAsLong());
    }

    @Test
    void simultaneousHudsShareOneKeyAndUpdateOrCloseOnlyTheirOwnInstance() {
        List<byte[]> ruby = new ArrayList<>();
        List<byte[]> sapphire = new ArrayList<>();
        LoaderHudRegistry registry = new LoaderHudRegistry();
        registry.open(
                "solaris:ruby",
                1L,
                definition(List.of(new LoaderInputBinding(
                        "key.keyboard.g", "ruby-live:key_press", "ruby-live:key_release"))),
                model(action("ruby-live:key_press", true), action("ruby-live:key_release", true)),
                List.of(),
                ruby::add,
                Map.of(G, binding("key.keyboard.g", "ruby-live:key_press", "ruby-live:key_release")));
        registry.open(
                "solaris:sapphire",
                1L,
                definition(List.of(new LoaderInputBinding(
                        "key.keyboard.g", "sapphire-live:key_press", "sapphire-live:key_release"))),
                model(
                        action("sapphire-live:key_press", true),
                        action("sapphire-live:key_release", true)),
                List.of(),
                sapphire::add,
                Map.of(G, binding(
                        "key.keyboard.g", "sapphire-live:key_press", "sapphire-live:key_release")));

        registry.keyEdge(G, true);
        registry.keyEdge(G, false);

        assertEquals(2, ruby.size());
        assertEquals(2, sapphire.size());
        assertEquals("ruby-live:key_press", decode(ruby.get(0)).get("action_id").getAsString());
        assertEquals(
                "sapphire-live:key_press", decode(sapphire.get(0)).get("action_id").getAsString());

        registry.present(
                "solaris:ruby",
                5L,
                model(action("ruby-live:key_press", true), action("ruby-live:key_release", true)));
        assertNull(registry.present("solaris:other", 5L, model()));
        assertTrue(registry.close("solaris:sapphire"));
        assertFalse(registry.close("solaris:sapphire"));

        registry.keyEdge(G, true);
        assertEquals(3, ruby.size());
        assertEquals(5L, decode(ruby.get(2)).get("view_revision").getAsLong());
        assertEquals(2, sapphire.size());
    }

    @Test
    void disconnectDropsInstancesHeldEdgesAndSinksWithoutReplayingThem() {
        List<byte[]> sent = new ArrayList<>();
        LoaderHudRegistry registry = new LoaderHudRegistry();
        registry.open(
                "solaris:ruby",
                4L,
                definition(List.of(new LoaderInputBinding(
                        "key.keyboard.g", "ruby-live:key_press", "ruby-live:key_release"))),
                model(action("ruby-live:key_press", true), action("ruby-live:key_release", true)),
                List.of(),
                sent::add,
                Map.of(G, binding("key.keyboard.g", "ruby-live:key_press", "ruby-live:key_release")));

        registry.keyEdge(G, true);
        assertTrue(registry.holds());

        registry.clear();

        assertTrue(registry.isEmpty());
        assertFalse(registry.holds());
        registry.keyEdge(G, false);
        registry.releaseHeld();
        assertEquals(1, sent.size());
    }

    @Test
    void anUnknownNativeKeyRefusesTheWholeBindingSet() {
        assertTrue(LoaderMinecraftInput.resolve(List.of(
                binding("key.keyboard.g", "ruby-live:key_press", "ruby-live:key_release"),
                binding("key.keyboard.not_a_real_key", "ruby-live:bad_press", "ruby-live:bad_release")))
                .isEmpty());
    }

    private static LoaderHud hud(
            String viewInstanceId,
            long revision,
            LoaderViewModel model,
            Map<Integer, LoaderInputBinding> bindings,
            List<byte[]> sent) {
        return new LoaderHud(
                viewInstanceId,
                revision,
                definition(List.copyOf(bindings.values())),
                model,
                List.of(),
                sent::add,
                bindings);
    }

    private static LoaderScreenDefinition definition(List<LoaderInputBinding> inputBindings) {
        return new LoaderScreenDefinition(
                "ruby-live:input",
                LoaderScreenKind.HUD,
                "Ruby input",
                Optional.empty(),
                Optional.empty(),
                List.of(),
                inputBindings);
    }

    private static LoaderInputBinding binding(String key, String press, String release) {
        return new LoaderInputBinding(key, press, release);
    }

    private static LoaderViewModel model(LoaderViewModel.Action... actions) {
        return new LoaderViewModel(
                0,
                1,
                List.of(),
                List.of(),
                List.of(actions),
                List.of(),
                List.of(),
                List.of(),
                Optional.empty());
    }

    private static LoaderViewModel.Action action(String actionId, boolean enabled) {
        return new LoaderViewModel.Action(actionId, enabled, Optional.empty(), Optional.empty());
    }

    private static JsonObject decode(byte[] payload) {
        return JsonParser.parseString(new String(payload, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
