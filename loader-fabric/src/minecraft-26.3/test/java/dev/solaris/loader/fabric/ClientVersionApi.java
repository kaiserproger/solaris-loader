package dev.solaris.loader.fabric;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.sdl.SDLHints.SDL_HINT_VIDEO_DRIVER;
import static org.lwjgl.sdl.SDLHints.SDL_SetHint;
import static org.lwjgl.sdl.SDLInit.SDL_INIT_VIDEO;
import static org.lwjgl.sdl.SDLInit.SDL_Init;
import static org.lwjgl.sdl.SDLInit.SDL_Quit;
import static org.lwjgl.sdl.SDLVideo.*;
import org.junit.jupiter.api.function.Executable;
import org.lwjgl.opengl.GL;

final class ClientVersionApi {
    private ClientVersionApi() {}

    static void captureWindow(Executable action) throws Throwable {
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
            action.execute();
        } finally {
            GL.setCapabilities(null);
            if (context != 0) SDL_GL_DestroyContext(context);
            if (window != 0) SDL_DestroyWindow(window);
            SDL_Quit();
        }
    }
}
