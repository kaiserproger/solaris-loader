package dev.solaris.loader.fabric;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import static org.lwjgl.sdl.SDLHints.SDL_HINT_VIDEO_DRIVER;
import static org.lwjgl.sdl.SDLHints.SDL_SetHint;
import static org.lwjgl.sdl.SDLInit.SDL_INIT_VIDEO;
import static org.lwjgl.sdl.SDLInit.SDL_Init;
import static org.lwjgl.sdl.SDLInit.SDL_Quit;
import static org.lwjgl.sdl.SDLVideo.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

class ComputerCaptureTest {
    @Test void rejectsUnboundedAndMalformedFrames() {
        assertThrows(IllegalArgumentException.class, () -> X11ComputerCapture.byteCount(0, 1));
        assertThrows(IllegalArgumentException.class, () -> X11ComputerCapture.byteCount(4097, 1));
        assertThrows(IllegalArgumentException.class, () -> new X11ComputerCapture.Frame(2, 2, ByteBuffer.allocateDirect(15)));
        assertThrows(IllegalArgumentException.class, () -> new X11ComputerCapture.Frame(2, 2, ByteBuffer.allocate(16)));
        assertEquals(16, X11ComputerCapture.byteCount(2, 2));
    }
    @Test void linuxX11CaptureUploadsActualPixelsToTexture() throws Exception {
        assumeTrue("1".equals(System.getenv("SOLARIS_CAPTURE_SMOKE_X11")), "Explicit isolated Xvfb smoke only");
        // Minecraft 26.3 uses SDL3 rather than GLFW. Pin this opt-in smoke to X11.
        assertTrue(SDL_SetHint(SDL_HINT_VIDEO_DRIVER, "x11"));
        assertTrue(SDL_Init(SDL_INIT_VIDEO), "SDL must initialize on the isolated display");
        long window = 0;
        long context = 0;
        try {
            window = SDL_CreateWindow(
                    "Solaris capture smoke (synthetic pixels)", 64, 64, SDL_WINDOW_BORDERLESS | SDL_WINDOW_OPENGL);
            assertNotEquals(0, window);
            assertTrue(SDL_SetWindowPosition(window, 0, 0));
            context = SDL_GL_CreateContext(window);
            assertNotEquals(0, context);
            assertTrue(SDL_GL_MakeCurrent(window, context));
            GL.createCapabilities();
            GL11.glClearColor(32f / 255, 128f / 255, 224f / 255, 1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glFinish();
            var frame = X11ComputerCapture.capture("/usr/bin/ffmpeg", System.getenv("DISPLAY"), 64, 64);
            int center = (32 * 64 + 32) * 4;
            assertPixel(frame.rgba(), center);
            try (var texture = new ComputerCaptureTexture()) {
                texture.upload(frame);
                texture.upload(frame); // same dimensions exercise sub-image reuse
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.id());
                ByteBuffer readback = ByteBuffer.allocateDirect(64 * 64 * 4);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, readback);
                assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
                assertPixel(readback, center);
            }
        } finally {
            if (context != 0) SDL_GL_DestroyContext(context);
            if (window != 0) SDL_DestroyWindow(window);
            SDL_Quit();
        }
    }
    private static void assertPixel(ByteBuffer pixels, int offset) {
        assertEquals(32, Byte.toUnsignedInt(pixels.get(offset)), 1);
        assertEquals(128, Byte.toUnsignedInt(pixels.get(offset + 1)), 1);
        assertEquals(224, Byte.toUnsignedInt(pixels.get(offset + 2)), 1);
        assertEquals(255, Byte.toUnsignedInt(pixels.get(offset + 3)));
    }
}
