package dev.solaris.loader.minecraft.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.solaris.loader.minecraft.LoaderMinecraftInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Release held bindings from the game window's focus callback, before any later
 * key-up. The hook reads the owning window's resulting focus state instead of
 * depending on the native callback's version-specific parameter list.
 */
@Mixin(Window.class)
abstract class WindowMixin {
    @Inject(method = "onFocus", at = @At("RETURN"))
    private void solaris$windowFocus(CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getWindow() == (Object) this && !client.isWindowActive()) {
            LoaderMinecraftInput.focusLost();
        }
    }
}
