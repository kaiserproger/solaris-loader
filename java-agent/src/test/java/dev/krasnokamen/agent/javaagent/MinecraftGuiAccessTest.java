package dev.krasnokamen.agent.javaagent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class MinecraftGuiAccessTest {
    @Test
    void retainedClientReadsAndWritesMinecraftScreenAndGuiChat() throws Throwable {
        LegacyMinecraft minecraft = new LegacyMinecraft();
        MinecraftGuiAccess.Access access = MinecraftGuiAccess.resolve(LegacyMinecraft.class);
        assertSame(minecraft.screen, (Object) access.screen().invokeExact((Object) minecraft));
        assertSame(minecraft.overlay, (Object) access.overlay().invokeExact((Object) minecraft));
        assertSame(minecraft.gui.chat, (Object) access.chat().invokeExact((Object) minecraft));
        assertSame(minecraft.target, (Object) access.mainRenderTarget().invokeExact((Object) minecraft));
        assertSame(minecraft.gameRenderer.renderState, (Object) access.renderState().invokeExact((Object) minecraft));
        FakeScreen replacement = new FakeScreen();
        access.setScreen().invokeExact((Object) minecraft, (Object) replacement);
        assertSame(replacement, minecraft.screen);
        access.setScreen().invokeExact((Object) minecraft, (Object) null);
        assertSame(null, (Object) access.screen().invokeExact((Object) minecraft));
    }

    @Test
    void canonicalClientReadsAndWritesGuiScreenAndHudChat() throws Throwable {
        ModernMinecraft minecraft = new ModernMinecraft();
        MinecraftGuiAccess.Access access = MinecraftGuiAccess.resolve(ModernMinecraft.class);
        assertSame(minecraft.gui.current, (Object) access.screen().invokeExact((Object) minecraft));
        assertSame(minecraft.gui.overlay, (Object) access.overlay().invokeExact((Object) minecraft));
        assertSame(minecraft.gui.hud.chat, (Object) access.chat().invokeExact((Object) minecraft));
        assertSame(minecraft.gameRenderer.target, (Object) access.mainRenderTarget().invokeExact((Object) minecraft));
        assertSame(minecraft.gameRenderer.renderState, (Object) access.renderState().invokeExact((Object) minecraft));
        FakeScreen replacement = new FakeScreen();
        access.setScreen().invokeExact((Object) minecraft, (Object) replacement);
        assertSame(replacement, minecraft.gui.current);
        access.setScreen().invokeExact((Object) minecraft, (Object) null);
        assertSame(null, (Object) access.screen().invokeExact((Object) minecraft));
    }

    @Test
    void missingNativeLayoutFailsInsteadOfReportingAnEmptyScreen() {
        assertThrows(IllegalStateException.class, () -> MinecraftGuiAccess.resolve(Object.class));
    }

    @Test
    void keyboardTypeRetainsBothNativeNamesWithoutAdmittingMouseOrScancodes() {
        assertTrue(MinecraftGuiAccess.isKeyboardType("KEYSYM"));
        assertTrue(MinecraftGuiAccess.isKeyboardType("KEYBOARD"));
        assertFalse(MinecraftGuiAccess.isKeyboardType("MOUSE"));
        assertFalse(MinecraftGuiAccess.isKeyboardType("SCANCODE"));
    }

    public static final class FakeScreen {
    }

    public static final class LegacyMinecraft {
        public final LegacyGui gui = new LegacyGui();
        public final LegacyGameRenderer gameRenderer = new LegacyGameRenderer();
        public FakeScreen screen = new FakeScreen();
        final Object overlay = new Object();
        final Object target = new Object();

        public void setScreen(FakeScreen screen) {
            this.screen = screen;
        }

        public Object getOverlay() {
            return overlay;
        }

        public Object getMainRenderTarget() {
            return target;
        }
    }

    public static final class LegacyGameRenderer {
        final Object renderState = new Object();

        public Object getGameRenderState() {
            return renderState;
        }
    }

    public static final class LegacyGui {
        final Object chat = new Object();

        public Object getChat() {
            return chat;
        }
    }

    public static final class ModernMinecraft {
        public final ModernGui gui = new ModernGui();
        public final GameRenderer gameRenderer = new GameRenderer();
    }

    public static final class ModernGui {
        public final Hud hud = new Hud();
        FakeScreen current = new FakeScreen();
        final Object overlay = new Object();

        public FakeScreen screen() {
            return current;
        }

        public void setScreen(FakeScreen screen) {
            current = screen;
        }

        public Object overlay() {
            return overlay;
        }
    }

    public static final class GameRenderer {
        final Object target = new Object();
        final Object renderState = new Object();

        public Object mainRenderTarget() {
            return target;
        }

        public Object gameRenderState() {
            return renderState;
        }
    }

    public static final class Hud {
        final Object chat = new Object();

        public Object getChat() {
            return chat;
        }
    }
}
