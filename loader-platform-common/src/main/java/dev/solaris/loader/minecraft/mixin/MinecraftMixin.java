package dev.solaris.loader.minecraft.mixin;

import dev.solaris.loader.minecraft.LoaderMinecraftInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gameplay input focus: the head of the client's own screen transition releases
 * every held Loader binding before the new GUI takes focus, so no held edge
 * outlives the key-up that the GUI may never deliver.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void solaris$screenFocus(Screen screen, CallbackInfo callback) {
        LoaderMinecraftInput.screenChanged(screen);
    }

    @Inject(method = "setOverlay", at = @At("HEAD"))
    private void solaris$overlayFocus(Overlay overlay, CallbackInfo callback) {
        if (overlay != null) {
            LoaderMinecraftInput.focusLost();
        }
    }
}
