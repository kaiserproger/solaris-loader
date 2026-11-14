package dev.krasnokamen.agent.javaagent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.PlayerTeam;

/** Native HUD visibility and scoreboard color APIs across retained clients. */
final class MinecraftHudAccess {
    private static final Access ACCESS = resolve(Minecraft.class);
    private static final MethodHandle TEAM_COLOR = resolveColor(PlayerTeam.class);

    private MinecraftHudAccess() {
    }

    static void setHidden(Minecraft minecraft, boolean hidden) {
        try {
            ACCESS.setHidden(minecraft, hidden);
        } catch (Throwable failure) {
            throw new IllegalStateException("setting native HUD visibility", failure);
        }
    }

    static String teamColor(PlayerTeam team) {
        try {
            return colorName((Object) TEAM_COLOR.invokeExact((Object) team));
        } catch (Throwable failure) {
            throw new IllegalStateException("reading native team color", failure);
        }
    }

    static String colorName(Object nativeColor) {
        Object color = nativeColor instanceof Optional<?> optional ? optional.orElse(null) : nativeColor;
        return color == null ? null : ((Enum<?>) color).name();
    }

    static MethodHandle resolveColor(Class<?> team) {
        try {
            return MethodHandles.publicLookup().unreflect(team.getMethod("getColor"))
                .asType(MethodType.methodType(Object.class, Object.class));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("unsupported native team color API: " + team.getName(), failure);
        }
    }

    static Access resolve(Class<?> minecraft) {
        try {
            var lookup = MethodHandles.publicLookup();
            Field options = minecraft.getField("options");
            Field legacyHidden;
            try {
                legacyHidden = options.getType().getField("hideGui");
            } catch (NoSuchFieldException modern) {
                legacyHidden = null;
            }
            MethodHandle owner;
            MethodHandle hidden;
            MethodHandle setter = null;
            MethodHandle toggle = null;
            if (legacyHidden != null) {
                owner = lookup.unreflectGetter(options);
                hidden = lookup.unreflectGetter(legacyHidden);
                setter = lookup.unreflectSetter(legacyHidden)
                    .asType(MethodType.methodType(void.class, Object.class, boolean.class));
            } else {
                Field gui = minecraft.getField("gui");
                Field hud = gui.getType().getField("hud");
                owner = MethodHandles.filterReturnValue(lookup.unreflectGetter(gui), lookup.unreflectGetter(hud));
                hidden = lookup.unreflect(hud.getType().getMethod("isHidden"));
                toggle = lookup.unreflect(hud.getType().getMethod("toggle"))
                    .asType(MethodType.methodType(void.class, Object.class));
            }
            return new Access(owner.asType(MethodType.methodType(Object.class, Object.class)),
                hidden.asType(MethodType.methodType(boolean.class, Object.class)), setter, toggle);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("unsupported native HUD API: " + minecraft.getName(), failure);
        }
    }

    record Access(MethodHandle owner, MethodHandle hidden, MethodHandle setter, MethodHandle toggle) {
        void setHidden(Object minecraft, boolean requested) throws Throwable {
            Object nativeOwner = (Object) owner.invokeExact(minecraft);
            if ((boolean) hidden.invokeExact(nativeOwner) == requested) return;
            if (setter != null) setter.invokeExact(nativeOwner, requested);
            else toggle.invokeExact(nativeOwner);
        }
    }
}
