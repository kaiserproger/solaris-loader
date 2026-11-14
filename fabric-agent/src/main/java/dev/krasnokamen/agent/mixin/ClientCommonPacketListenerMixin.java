package dev.krasnokamen.agent.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;

@Mixin(ClientCommonPacketListenerImpl.class)
abstract class ClientCommonPacketListenerMixin {
    private static final Logger KRASNOKAMEN_LOGGER = LogUtils.getLogger();
    private static final boolean KRASNOKAMEN_TRACE_BLOCK_BREAK = Boolean.getBoolean(
        "krasnokamen.traceBlockBreak"
    );

    @Inject(method = "send", at = @At("HEAD"))
    private void krasnokamen$traceBlockBreakAction(Packet<?> packet, CallbackInfo callbackInfo) {
        if (!(packet instanceof ServerboundPlayerActionPacket action)) {
            return;
        }
        if (
            action.getAction() != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                && action.getAction() != ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK
                && action.getAction() != ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK
        ) {
            return;
        }
        if (KRASNOKAMEN_TRACE_BLOCK_BREAK) {
            KRASNOKAMEN_LOGGER.info(
                "[krasnokamen-block-break] send action={} pos={} direction={} sequence={}",
                action.getAction(),
                action.getPos(),
                action.getDirection(),
                action.getSequence()
            );
        }
    }
}
