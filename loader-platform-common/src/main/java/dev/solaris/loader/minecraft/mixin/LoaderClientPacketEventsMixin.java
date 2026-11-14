package dev.solaris.loader.minecraft.mixin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Packet-specific MCP waits need the received packet event, not just client ticks. */
@Mixin(ClientPacketListener.class)
abstract class LoaderClientPacketEventsMixin {
    @Unique
    private static final MethodHandle solaris$publishHealth = solaris$healthPublisher();

    @Unique
    private static MethodHandle solaris$healthPublisher() {
        String token = System.getenv("SOLARIS_CLIENT_MCP_TOKEN");
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Class<?> events = Class.forName("dev.solaris.agent.javaagent.ClientStateEvents", true,
                    ClientPacketListener.class.getClassLoader());
            return MethodHandles.publicLookup().findStatic(events, "publishHealth", MethodType.methodType(void.class));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Loader MCP health event publisher is unavailable", error);
        }
    }

    @Inject(method = "handleSetHealth", at = @At("RETURN"))
    private void solaris$receivedHealth(ClientboundSetHealthPacket packet, CallbackInfo callback) {
        if (solaris$publishHealth != null) {
            try {
                solaris$publishHealth.invokeExact();
            } catch (Throwable error) {
                throw new IllegalStateException("Loader MCP could not publish received health", error);
            }
        }
    }
}
