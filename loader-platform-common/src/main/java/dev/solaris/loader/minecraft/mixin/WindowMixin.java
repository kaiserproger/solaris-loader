package dev.solaris.loader.minecraft.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.solaris.loader.minecraft.LoaderMinecraftInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Release held bindings from the game window's focus callback, before any later
 * key-up. Since 26.3 (SDL3) the callback is an instance method of that window.
 */
@Mixin(Window.class)
abstract class WindowMixin {
    @Inject(method = "onFocus", at = @At("RETURN"))
    private void solaris$windowFocus(boolean focused, CallbackInfo callback) {
        if (!focused) {
            LoaderMinecraftInput.focusLost();
        }
    }
}
