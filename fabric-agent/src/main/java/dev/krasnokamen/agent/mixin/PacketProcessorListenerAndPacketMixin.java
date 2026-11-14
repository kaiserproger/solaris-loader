package dev.krasnokamen.agent.mixin;

import dev.krasnokamen.agent.javaagent.ClientStateEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
abstract class PacketProcessorListenerAndPacketMixin {
    @Inject(method = "handle", at = @At("RETURN"))
    private void krasnokamen$publishAppliedPacket(CallbackInfo callbackInfo) {
        ClientStateEvents.publishState();
    }
}
