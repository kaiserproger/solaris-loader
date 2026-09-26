package dev.solaris.loader.minecraft.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.solaris.loader.minecraft.LoaderMinecraftInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Release held bindings from the native focus callback, before any later key-up. */
@Mixin(Window.class)
abstract class WindowMixin {
    @Inject(method = "onFocus", at = @At("RETURN"))
    private void solaris$windowFocus(long window, boolean focused, CallbackInfo callback) {
        if (focused) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Window gameWindow = client == null ? null : client.getWindow();
        if (gameWindow != null && gameWindow.handle() == window) {
            LoaderMinecraftInput.focusLost();
        }
    }
}
