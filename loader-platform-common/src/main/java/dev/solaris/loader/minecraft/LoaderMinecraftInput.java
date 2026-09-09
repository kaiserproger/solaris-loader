package dev.solaris.loader.minecraft;

import com.mojang.blaze3d.platform.InputConstants;
import dev.solaris.loader.LoaderActivatedContent;
import dev.solaris.loader.LoaderInteractionAction;
import dev.solaris.loader.LoaderInteractionDefinition;
import dev.solaris.loader.LoaderKeyActions;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;

/** Shared client-thread input lifecycle; adapters only supply native events and transport. */
public final class LoaderMinecraftInput {
    private static Connection origin;
    private static LoaderKeyActions actions;
    private static BiConsumer<LoaderInteractionDefinition, LoaderInteractionAction.Phase> sender;

    private LoaderMinecraftInput() {
    }

    public static void bind(
            Connection connection,
            LoaderActivatedContent content,
            BiConsumer<LoaderInteractionDefinition, LoaderInteractionAction.Phase> send) {
        clear();
        origin = connection;
        actions = new LoaderKeyActions(content.interactions().values());
        sender = send;
    }

    public static void clear() {
        origin = null;
        actions = null;
        sender = null;
    }

    public static void keyEvent(int key, int action) {
        Minecraft client = Minecraft.getInstance();
        tick(client);
        if (origin == null || !acceptsInput(client) || key < 0
                || (action != InputConstants.PRESS && action != InputConstants.RELEASE)) {
            return;
        }
        actions.key(
                InputConstants.Type.KEYSYM.getOrCreate(key).getName(),
                action == InputConstants.PRESS,
                sender);
    }

    public static void tick(Minecraft client) {
        if (origin == null) {
            return;
        }
        var listener = client.getConnection();
        if (!origin.isConnected() || listener == null || listener.getConnection() != origin) {
            clear();
        } else if (!acceptsInput(client)) {
            actions.releaseAll(sender);
        }
    }

    private static boolean acceptsInput(Minecraft client) {
        return client.player != null && client.screen == null
                && client.getOverlay() == null && client.isWindowActive();
    }
}
