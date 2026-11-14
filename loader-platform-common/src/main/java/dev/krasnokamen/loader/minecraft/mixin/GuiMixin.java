package dev.krasnokamen.loader.minecraft.mixin;

import dev.krasnokamen.loader.minecraft.LoaderMinecraftInput;
<<<<<<<< HEAD:loader-platform-common/src/main/java/dev/krasnokamen/loader/minecraft/mixin/GuiMixin.java
<<<<<<<< HEAD:loader-platform-common/src/main/java/dev/krasnokamen/loader/minecraft/mixin/GuiMixin.java
import net.minecraft.client.gui.Gui;
========
========
>>>>>>>> stage0/rebrand/JavaLoaderGateMod:loader-platform-common/src/main/java/dev/krasnokamen/loader/minecraft/mixin/MinecraftMixin.java
import net.minecraft.client.Minecraft;
>>>>>>>> stage0/rebrand/ClientBugsGaps3Loader:loader-platform-common/src/main/java/dev/krasnokamen/loader/minecraft/mixin/MinecraftMixin.java
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gameplay input focus: the head of the client's own screen transition releases
 * every held Loader binding before the new GUI takes focus, so no held edge
 * outlives the key-up that the GUI may never deliver. Since 26.3 the client's
 * screen and overlay live on {@link Gui}.
 */
@Mixin(Gui.class)
abstract class GuiMixin {
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void krasnokamen$screenFocus(Screen screen, CallbackInfo callback) {
        LoaderMinecraftInput.screenChanged(screen);
    }

    @Inject(method = "setOverlay", at = @At("HEAD"))
    private void krasnokamen$overlayFocus(Overlay overlay, CallbackInfo callback) {
        if (overlay != null) {
            LoaderMinecraftInput.focusLost();
        }
    }
}
