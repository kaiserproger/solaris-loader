package dev.solaris.loader.minecraft.mixin;

import dev.solaris.loader.minecraft.LoaderMinecraftInput;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The producer of the native client key edges every platform shares: vanilla's
 * own key dispatch is the only place that sees the keys vanilla consumes before
 * any mod hook (F2 screenshot, F11 fullscreen, the Escape that closes a GUI).
 * The head captures the focus an edge arrived with, the return point decides
 * after vanilla handled it. Nothing is cancelled, so every vanilla behavior is
 * preserved.
 */
@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void solaris$captureFocus(long window, int action, KeyEvent event, CallbackInfo callback) {
        if (window == Minecraft.getInstance().getWindow().handle()) {
            LoaderMinecraftInput.beforeKeyPress();
        }
    }

    @Inject(method = "keyPress", at = @At("RETURN"))
    private void solaris$keyEdge(long window, int action, KeyEvent event, CallbackInfo callback) {
        if (window == Minecraft.getInstance().getWindow().handle()) {
            LoaderMinecraftInput.afterKeyPress(
                    action, event.key(), event.scancode(), event.modifiers());
        }
    }
}
