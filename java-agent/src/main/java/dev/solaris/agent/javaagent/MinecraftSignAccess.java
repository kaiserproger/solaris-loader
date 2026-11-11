package dev.solaris.agent.javaagent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

/** Binds the native front-sign APIs without linking newer-only slot enums. */
final class MinecraftSignAccess {
    private static final Access ACCESS = resolve(ServerboundSignUpdatePacket.class, BlockPos.class,
        SignBlockEntity.class, SignText.class);

    private MinecraftSignAccess() {
    }

    static ServerboundSignUpdatePacket frontUpdate(BlockPos position, List<String> lines) {
        try {
            return (ServerboundSignUpdatePacket) ACCESS.frontUpdate(position, lines);
        } catch (Throwable failure) {
            throw new IllegalStateException("creating native front-sign update", failure);
        }
    }

    static SignText frontText(SignBlockEntity sign) {
        try {
            return (SignText) (Object) ACCESS.frontText().invokeExact((Object) sign);
        } catch (Throwable failure) {
            throw new IllegalStateException("reading native front-sign text", failure);
        }
    }

    static Component message(SignText text, int index) {
        try {
            return (Component) ACCESS.message(text, index);
        } catch (Throwable failure) {
            throw new IllegalStateException("reading native sign message", failure);
        }
    }

    static Access resolve(Class<?> packet, Class<?> position, Class<?> sign, Class<?> text) {
        try {
            var lookup = MethodHandles.publicLookup();
            MethodHandle constructor;
            MethodHandle front;
            boolean legacy;
            try {
                constructor = lookup.unreflectConstructor(packet.getConstructor(position, boolean.class,
                    String.class, String.class, String.class, String.class));
                constructor = MethodHandles.insertArguments(constructor, 1, true);
                front = MethodHandles.insertArguments(lookup.unreflect(sign.getMethod("getText", boolean.class)),
                    1, true);
                legacy = true;
            } catch (NoSuchMethodException modern) {
                Constructor<?> frontConstructor = null;
                for (Constructor<?> candidate : packet.getConstructors()) {
                    Class<?>[] parameters = candidate.getParameterTypes();
                    if (parameters.length == 3 && parameters[0] == position && parameters[1] == List.class
                        && parameters[2].isEnum()) {
                        frontConstructor = candidate;
                        break;
                    }
                }
                if (frontConstructor == null) throw new NoSuchMethodException("front-sign packet constructor");
                Class<?> slot = frontConstructor.getParameterTypes()[2];
                Object frontSlot = slot.getField("FRONT").get(null);
                constructor = MethodHandles.insertArguments(lookup.unreflectConstructor(frontConstructor), 2, frontSlot);
                front = MethodHandles.insertArguments(lookup.unreflect(sign.getMethod("getText", slot)), 1, frontSlot);
                legacy = false;
            }
            Method messages = text.getMethod("getMessages", boolean.class);
            MethodType updateType = legacy
                ? MethodType.methodType(Object.class, Object.class, String.class, String.class, String.class, String.class)
                : MethodType.methodType(Object.class, Object.class, List.class);
            return new Access(constructor.asType(updateType),
                front.asType(MethodType.methodType(Object.class, Object.class)),
                MethodHandles.insertArguments(lookup.unreflect(messages), 1, false)
                    .asType(MethodType.methodType(Object.class, Object.class)), legacy);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("unsupported native sign API: " + packet.getName(), failure);
        }
    }

    record Access(MethodHandle update, MethodHandle frontText, MethodHandle messages, boolean legacy) {
        Object frontUpdate(Object position, List<String> lines) throws Throwable {
            if (lines.size() != 4) throw new IllegalArgumentException("sign update requires four lines");
            if (legacy) {
                return (Object) update.invokeExact(position, lines.get(0), lines.get(1), lines.get(2), lines.get(3));
            }
            return (Object) update.invokeExact(position, lines);
        }

        Object message(Object text, int index) throws Throwable {
            Object nativeMessages = (Object) messages.invokeExact(text);
            return legacy ? ((Object[]) nativeMessages)[index] : ((List<?>) nativeMessages).get(index);
        }
    }
}
