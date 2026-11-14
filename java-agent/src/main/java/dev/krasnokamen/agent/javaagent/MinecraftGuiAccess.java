package dev.krasnokamen.agent.javaagent;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;

/** The GUI moved from Minecraft/Gui to Gui/Hud after the retained 26.1.2 client. */
final class MinecraftGuiAccess {
    private static final Access ACCESS = resolve(Minecraft.class);

    private MinecraftGuiAccess() {
    }

    static Screen screen(Minecraft minecraft) {
        try {
            return (Screen) (Object) ACCESS.screen().invokeExact((Object) minecraft);
        } catch (Throwable failure) {
            throw new IllegalStateException("reading Minecraft screen", failure);
        }
    }

    static void setScreen(Minecraft minecraft, Screen screen) {
        try {
            ACCESS.setScreen().invokeExact((Object) minecraft, (Object) screen);
        } catch (Throwable failure) {
            throw new IllegalStateException("setting Minecraft screen", failure);
        }
    }

    static Overlay overlay(Minecraft minecraft) {
        try {
            return (Overlay) (Object) ACCESS.overlay().invokeExact((Object) minecraft);
        } catch (Throwable failure) {
            throw new IllegalStateException("reading Minecraft overlay", failure);
        }
    }

    static ChatComponent chat(Minecraft minecraft) {
        try {
            return (ChatComponent) (Object) ACCESS.chat().invokeExact((Object) minecraft);
        } catch (Throwable failure) {
            throw new IllegalStateException("reading Minecraft chat", failure);
        }
    }

    static RenderTarget mainRenderTarget(Minecraft minecraft) {
        try {
            return (RenderTarget) (Object) ACCESS.mainRenderTarget().invokeExact((Object) minecraft);
        } catch (Throwable failure) {
            throw new IllegalStateException("reading Minecraft screenshot render target", failure);
        }
    }

    static boolean isKeyboardType(String name) {
        return name.equals("KEYBOARD") || name.equals("KEYSYM");
    }

    // Resolve the complete layout once. Missing members are an unsupported API,
    // never an empty screen/chat observation or a per-frame reflective search.
    static Access resolve(Class<?> minecraft) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Field gui = minecraft.getField("gui");
            MethodHandle guiGetter = lookup.unreflectGetter(gui);
            Field legacyScreen;
            try {
                legacyScreen = minecraft.getField("screen");
            } catch (NoSuchFieldException movedToGui) {
                legacyScreen = null;
            }
            MethodHandle screen;
            MethodHandle setScreen;
            MethodHandle overlay;
            MethodHandle chat;
            MethodHandle mainRenderTarget;
            if (legacyScreen != null) {
                screen = lookup.unreflectGetter(legacyScreen);
                setScreen = lookup.unreflect(minecraft.getMethod("setScreen", legacyScreen.getType()));
                overlay = lookup.unreflect(minecraft.getMethod("getOverlay"));
                chat = MethodHandles.filterReturnValue(guiGetter,
                    lookup.unreflect(gui.getType().getMethod("getChat")));
                mainRenderTarget = lookup.unreflect(minecraft.getMethod("getMainRenderTarget"));
            } else {
                var screenMethod = gui.getType().getMethod("screen");
                screen = MethodHandles.filterReturnValue(guiGetter, lookup.unreflect(screenMethod));
                setScreen = MethodHandles.filterArguments(
                    lookup.unreflect(gui.getType().getMethod("setScreen", screenMethod.getReturnType())),
                    0, guiGetter);
                overlay = MethodHandles.filterReturnValue(guiGetter,
                    lookup.unreflect(gui.getType().getMethod("overlay")));
                Field hud = gui.getType().getField("hud");
                MethodHandle hudGetter = MethodHandles.filterReturnValue(guiGetter, lookup.unreflectGetter(hud));
                chat = MethodHandles.filterReturnValue(hudGetter,
                    lookup.unreflect(hud.getType().getMethod("getChat")));
                Field renderer = minecraft.getField("gameRenderer");
                mainRenderTarget = MethodHandles.filterReturnValue(lookup.unreflectGetter(renderer),
                    lookup.unreflect(renderer.getType().getMethod("mainRenderTarget")));
            }
            MethodType getter = MethodType.methodType(Object.class, Object.class);
            return new Access(screen.asType(getter),
                setScreen.asType(MethodType.methodType(void.class, Object.class, Object.class)),
                overlay.asType(getter), chat.asType(getter), mainRenderTarget.asType(getter));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("unsupported Minecraft GUI API: " + minecraft.getName(), failure);
        }
    }

    record Access(MethodHandle screen, MethodHandle setScreen, MethodHandle overlay, MethodHandle chat,
                  MethodHandle mainRenderTarget) {
    }
}
