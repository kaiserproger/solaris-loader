package dev.solaris.loader.minecraft;

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
    public static Screen screen(Minecraft client) { return client.gui.screen(); }
    public static void setScreen(Minecraft client, Screen screen) { client.gui.setScreen(screen); }
    public static Overlay overlay(Minecraft client) { return client.gui.overlay(); }
    public static boolean hudHidden(Minecraft client) { return client.gui.hud.isHidden(); }
    public static int keycode(KeyEvent event) { return event.keycode(); }
    public static InputConstants.Type keyboardType() { return InputConstants.Type.KEYBOARD; }
    interface PackMetadata extends net.minecraft.server.packs.PackMetadataResources {}
    static java.util.stream.Stream<PackResources> openPack(Pack pack) {
        return pack.open();
    }
    static <T extends PackResources & PackMetadata> Pack.ResourcesSupplier packSupplier(
            Function<PackLocationInfo, T> resources) {
        return new Pack.ResourcesSupplier() {
            public net.minecraft.server.packs.PackMetadataResources openMetadata(PackLocationInfo location) {
                return resources.apply(location);
            }
            public java.util.stream.Stream<PackResources> openResources(PackLocationInfo location, Pack.Metadata metadata) {
                return java.util.stream.Stream.of(resources.apply(location));
            }
        };
    }
}
