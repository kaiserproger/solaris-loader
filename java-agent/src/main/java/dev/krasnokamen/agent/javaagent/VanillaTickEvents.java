package dev.krasnokamen.agent.javaagent;

import net.minecraft.client.Minecraft;

/** Called on the client thread after the plain client's tick has completed. */
public final class VanillaTickEvents {
    private static Object lastScreen;
    private static Object lastLevel;
    private static Object lastPlayer;
    private static int lastPlayerTick;

    private VanillaTickEvents() {}

    public static void afterTick(Minecraft minecraft) {
        Object screen = MinecraftGuiAccess.screen(minecraft);
        Object level = minecraft.level;
        Object player = minecraft.player;
        ClientStateEvents.publishTick();
        int playerTick = minecraft.player == null ? 0 : minecraft.player.tickCount;
        if (playerTick != lastPlayerTick || screen != lastScreen || level != lastLevel || player != lastPlayer) {
            lastScreen = screen;
            lastLevel = level;
            lastPlayer = player;
            lastPlayerTick = playerTick;
            ClientStateEvents.publishState();
        }
    }
}
