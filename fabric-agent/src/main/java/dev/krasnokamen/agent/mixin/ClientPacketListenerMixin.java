package dev.krasnokamen.agent.mixin;

import com.mojang.logging.LogUtils;
import dev.krasnokamen.agent.javaagent.ClientStateEvents;
import dev.krasnokamen.agent.javaagent.ScenarioItemDropIdentity;
import dev.krasnokamen.agent.javaagent.ReceivedEntityQa;
import dev.krasnokamen.agent.javaagent.ReceivedContainerQa;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;

@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
    private static final Logger KRASNOKAMEN_LOGGER = LogUtils.getLogger();
    private static final boolean KRASNOKAMEN_TRACE_BLOCK_BREAK = Boolean.getBoolean(
        "krasnokamen.traceBlockBreak"
    );

    @Inject(method = "handleSetEntityData", at = @At("RETURN"))
    private void krasnokamen$recordQaMetadata(ClientboundSetEntityDataPacket packet, CallbackInfo callbackInfo) {
        ReceivedEntityQa.metadata(packet);
    }

    @Inject(method = "handleSetEquipment", at = @At("RETURN"))
    private void krasnokamen$recordQaEquipment(ClientboundSetEquipmentPacket packet, CallbackInfo callbackInfo) {
        ReceivedEntityQa.equipment(packet);
    }

    @Inject(method = "handleBlockChangedAck", at = @At("RETURN"))
    private void krasnokamen$publishBlockChangeAck(
        ClientboundBlockChangedAckPacket packet,
        CallbackInfo callbackInfo
    ) {
        if (KRASNOKAMEN_TRACE_BLOCK_BREAK) {
            KRASNOKAMEN_LOGGER.info(
                "[krasnokamen-block-break] receive ack sequence={}",
                packet.sequence()
            );
        }
        ClientStateEvents.publishBlockChangeAck();
    }

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"))
    private void krasnokamen$traceBlockUpdate(
        ClientboundBlockUpdatePacket packet,
        CallbackInfo callbackInfo
    ) {
        if (KRASNOKAMEN_TRACE_BLOCK_BREAK) {
            KRASNOKAMEN_LOGGER.info(
                "[krasnokamen-block-break] receive block_update pos={} state={}",
                packet.getPos(),
                packet.getBlockState()
            );
        }
    }

    @Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
    private void krasnokamen$publishTakenItemIdentity(
        ClientboundTakeItemEntityPacket packet,
        CallbackInfo callbackInfo
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (
            minecraft.level == null
                || minecraft.player == null
                || packet.getPlayerId() != minecraft.player.getId()
        ) {
            return;
        }
        Entity entity = minecraft.level.getEntity(packet.getItemId());
        if (entity instanceof ItemEntity && !entity.isRemoved()) {
            ClientStateEvents.publishItemTaken(
                new ScenarioItemDropIdentity(entity.getId(), entity.getUUID()),
                packet.getPlayerId()
            );
        }
    }

    @Inject(method = "handleSetTime", at = @At("RETURN"))
    private void krasnokamen$publishServerTime(ClientboundSetTimePacket packet, CallbackInfo callbackInfo) {
        ClientStateEvents.publishServerTime(packet.gameTime());
    }

    @Inject(method = "handleSetHealth", at = @At("RETURN"))
    private void krasnokamen$publishHealth(ClientboundSetHealthPacket packet, CallbackInfo callbackInfo) {
        ClientStateEvents.publishHealth();
    }

    @Inject(method = "handleSoundEntityEvent", at = @At("RETURN"))
    private void krasnokamen$publishEntitySound(ClientboundSoundEntityPacket packet, CallbackInfo callbackInfo) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            ClientStateEvents.publishEntitySound(
                packet.getId(), minecraft.player.getId(), packet.getSound().value().location()
            );
        }
    }

    @Inject(method = "handleOpenScreen", at = @At("RETURN"))
    private void krasnokamen$publishOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo callbackInfo) {
        ClientStateEvents.publishContainerPacket();
    }

    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void krasnokamen$publishContainerSlot(
        ClientboundContainerSetSlotPacket packet,
        CallbackInfo callbackInfo
    ) {
        ClientStateEvents.publishContainerPacket();
    }

    @Inject(method = "handleContainerContent", at = @At("RETURN"))
    private void krasnokamen$publishContainerContent(
        ClientboundContainerSetContentPacket packet,
        CallbackInfo callbackInfo
    ) {
        ReceivedContainerQa.content(packet);
        ClientStateEvents.publishContainerPacket();
    }

    @Inject(method = "handleContainerSetData", at = @At("RETURN"))
    private void krasnokamen$publishContainerData(
        ClientboundContainerSetDataPacket packet,
        CallbackInfo callbackInfo
    ) {
        ClientStateEvents.publishContainerPacket();
    }

    @Inject(method = "handleContainerClose", at = @At("RETURN"))
    private void krasnokamen$publishContainerClose(
        ClientboundContainerClosePacket packet,
        CallbackInfo callbackInfo
    ) {
        ClientStateEvents.publishContainerPacket();
    }
}
