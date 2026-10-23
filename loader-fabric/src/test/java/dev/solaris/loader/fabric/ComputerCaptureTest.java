package dev.solaris.loader.fabric;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
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
        // Never let inherited WAYLAND_DISPLAY redirect this isolated X11 smoke.
        GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_X11);
        assertTrue(GLFW.glfwInit(), "GLFW must initialize on the isolated display");
        long window = 0;
        try {
            GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_DOUBLEBUFFER, GLFW.GLFW_FALSE);
            window = GLFW.glfwCreateWindow(64, 64, "Solaris capture smoke (synthetic pixels)", 0, 0);
            assertNotEquals(0, window);
            GLFW.glfwSetWindowPos(window, 0, 0);
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            GL11.glClearColor(32f / 255, 128f / 255, 224f / 255, 1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glFinish();
            GLFW.glfwPollEvents();
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
            if (window != 0) GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
    private static void assertPixel(ByteBuffer pixels, int offset) {
        assertEquals(32, Byte.toUnsignedInt(pixels.get(offset)), 1);
        assertEquals(128, Byte.toUnsignedInt(pixels.get(offset + 1)), 1);
        assertEquals(224, Byte.toUnsignedInt(pixels.get(offset + 2)), 1);
        assertEquals(255, Byte.toUnsignedInt(pixels.get(offset + 3)));
    }
}
