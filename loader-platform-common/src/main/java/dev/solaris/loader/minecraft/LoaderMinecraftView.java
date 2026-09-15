package dev.solaris.loader.minecraft;

import dev.solaris.loader.LoaderActivatedContent;
import dev.solaris.loader.LoaderScreenDefinition;
import dev.solaris.loader.LoaderViewMessage;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/**
 * Client-thread presentation of one server-authoritative Loader view instance
 * shared by all adapters. The adapter supplies the native receiver and the
 * connection-bound action sink; every validation stays in the core codec.
 */
public final class LoaderMinecraftView {
    private static LoaderViewScreen active;

    private LoaderMinecraftView() {
    }

    /**
     * Present one decoded wire-3 view message. Messages for a view instance that
     * is not the active one never touch the active view.
     */
    public static void present(
            LoaderViewMessage message,
            LoaderActivatedContent content,
            Consumer<byte[]> send) {
        Minecraft client = Minecraft.getInstance();
        switch (message) {
            case LoaderViewMessage.Open open -> {
                LoaderScreenDefinition definition = content.screens().get(open.viewId());
                if (definition == null) {
                    return;
                }
                close(client);
                LoaderViewScreen screen = new LoaderViewScreen(
                        open, definition, LoaderMinecraftDisplay.forScreen(definition, content), send);
                active = screen;
                client.setScreen(screen);
            }
            case LoaderViewMessage.Present present -> {
                LoaderViewScreen screen = active;
                if (screen != null && screen.viewInstanceId().equals(present.viewInstanceId())) {
                    screen.present(present.revision(), present.model());
                }
            }
            case LoaderViewMessage.Close close -> {
                LoaderViewScreen screen = active;
                if (screen != null && screen.viewInstanceId().equals(close.viewInstanceId())) {
                    close(client);
                }
            }
        }
    }

    /**
     * Clear the active view. Adapters call this on resource unmount and
     * disconnect; Minecraft owns modal teardown for a closed screen.
     */
    public static void clear() {
        close(Minecraft.getInstance());
    }

    static void dismiss(LoaderViewScreen screen) {
        if (active == screen) {
            active = null;
        }
    }

    private static void close(Minecraft client) {
        LoaderViewScreen screen = active;
        active = null;
        if (screen == null) {
            return;
        }
        if (client.screen == screen) {
            client.setScreen(null);
        }
    }
}
