package dev.krasnokamen.agent.javaagent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;

/** Native actions whose signatures changed after the retained 26.1.2 client. */
final class MinecraftActionAccess {
    private static final Access ACCESS = resolve(LocalPlayer.class, InteractionHand.class,
        SwingAnimation.class, ItemStack.class);

    private MinecraftActionAccess() {
    }

    static void swing(LocalPlayer player, InteractionHand hand) {
        try {
            ACCESS.swing().invokeExact((Object) player, (Object) hand);
        } catch (Throwable failure) {
            throw new IllegalStateException("swinging native player hand", failure);
        }
    }

    static ItemEntity drop(LocalPlayer player, ItemStack stack, boolean randomly) {
        try {
            return (ItemEntity) (Object) ACCESS.drop().invokeExact((Object) player, (Object) stack, randomly);
        } catch (Throwable failure) {
            throw new IllegalStateException("dropping native player stack", failure);
        }
    }

    static Access resolve(Class<?> player, Class<?> hand, Class<?> animation, Class<?> stack) {
        try {
            var lookup = MethodHandles.publicLookup();
            MethodHandle swing;
            try {
                // LocalPlayer's override also sends the native arm-animation packet.
                swing = lookup.unreflect(player.getMethod("swing", hand));
            } catch (NoSuchMethodException modern) {
                swing = MethodHandles.insertArguments(
                    lookup.unreflect(player.getMethod("swing", hand, animation, boolean.class)),
                    2, animation.getField("DEFAULT").get(null), true);
            }
            MethodHandle drop;
            try {
                drop = lookup.unreflect(player.getMethod("drop", stack, boolean.class));
            } catch (NoSuchMethodException modern) {
                Method predictedDrop = null;
                for (Method method : player.getMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (method.getName().equals("drop") && parameters.length == 3
                        && parameters[0] == stack && parameters[1] == boolean.class
                        && parameters[2].isEnum()) {
                        predictedDrop = method;
                        break;
                    }
                }
                if (predictedDrop == null) throw new NoSuchMethodException("predicted player drop");
                Object predicted = predictedDrop.getParameterTypes()[2].getField("PREDICTED").get(null);
                drop = MethodHandles.insertArguments(lookup.unreflect(predictedDrop), 3, predicted);
            }
            return new Access(swing.asType(MethodType.methodType(void.class, Object.class, Object.class)),
                drop.asType(MethodType.methodType(Object.class, Object.class, Object.class, boolean.class)));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("unsupported native player action API: " + player.getName(), failure);
        }
    }

    record Access(MethodHandle swing, MethodHandle drop) {
    }
}
