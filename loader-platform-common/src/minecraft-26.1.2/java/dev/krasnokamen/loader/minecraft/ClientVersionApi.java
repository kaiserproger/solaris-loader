package dev.krasnokamen.loader.minecraft;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import java.util.function.Function;

/** Version-specific Minecraft signatures; common Loader code owns the behavior. */
public final class ClientVersionApi {
    private ClientVersionApi() {}
    public static final String GUI_FOCUS_OWNER = "net.minecraft.client.Minecraft";
    public static Screen screen(Minecraft client) { return client.screen; }
    public static void setScreen(Minecraft client, Screen screen) { client.setScreen(screen); }
    public static Overlay overlay(Minecraft client) { return client.getOverlay(); }
    public static boolean hudHidden(Minecraft client) { return client.options.hideGui; }
    public static int keycode(KeyEvent event) { return event.scancode(); }
    public static InputConstants.Type keyboardType() { return InputConstants.Type.KEYSYM; }
    interface PackMetadata {}
    static java.util.stream.Stream<PackResources> openPack(Pack pack) {
        return java.util.stream.Stream.of(pack.open());
    }
    static <T extends PackResources & PackMetadata> Pack.ResourcesSupplier packSupplier(
            Function<PackLocationInfo, T> resources) {
        return new Pack.ResourcesSupplier() {
            public PackResources openPrimary(PackLocationInfo location) { return resources.apply(location); }
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return resources.apply(location);
            }
        };
    }
}
