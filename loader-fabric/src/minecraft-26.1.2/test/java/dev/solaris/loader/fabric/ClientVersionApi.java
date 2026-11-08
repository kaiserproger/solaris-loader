package dev.solaris.loader.fabric;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import org.junit.jupiter.api.function.Executable;
import org.lwjgl.opengl.GL;

final class ClientVersionApi {
    private ClientVersionApi() {}

    static void captureWindow(Executable action) throws Throwable {
        glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11);
        assertTrue(glfwInit(), "GLFW must initialize on the isolated display");
        long window = 0;
        try {
            glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);
            window = glfwCreateWindow(64, 64, "Solaris capture smoke (synthetic pixels)", 0, 0);
            assertNotEquals(0, window);
            glfwSetWindowPos(window, 0, 0);
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            action.execute();
        } finally {
            GL.setCapabilities(null);
            if (window != 0) glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
