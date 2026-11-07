package dev.solaris.agent.javaagent;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

/** Called on the client thread by the vanilla-only transformer. */
public final class VanillaPacketHooks {
    private VanillaPacketHooks() {}

    public static void beforePacket(Object packet) {
        if (packet instanceof ClientboundTakeItemEntityPacket taken) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null
                || taken.getPlayerId() != minecraft.player.getId()) return;
            Entity entity = minecraft.level.getEntity(taken.getItemId());
            if (entity instanceof ItemEntity && !entity.isRemoved()) {
                ClientStateEvents.publishItemTaken(
                    new ScenarioItemDropIdentity(entity.getId(), entity.getUUID()), taken.getPlayerId());
            }
        }
    }

    public static void afterPacket(Object packet) {
        if (packet instanceof ClientboundSetEntityDataPacket metadata) {
            ReceivedEntityQa.metadata(metadata);
        } else if (packet instanceof ClientboundSetEquipmentPacket equipment) {
            ReceivedEntityQa.equipment(equipment);
        } else if (packet instanceof ClientboundBlockChangedAckPacket) {
            ClientStateEvents.publishBlockChangeAck();
        } else if (packet instanceof ClientboundSetTimePacket time) {
            ClientStateEvents.publishServerTime(time.gameTime());
        } else if (packet instanceof ClientboundSetHealthPacket) {
            ClientStateEvents.publishHealth();
        } else if (packet instanceof ClientboundSoundEntityPacket sound) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                ClientStateEvents.publishEntitySound(sound.getId(), minecraft.player.getId(),
                    sound.getSound().value().location());
            }
        } else if (packet instanceof ClientboundContainerSetContentPacket content) {
            ReceivedContainerQa.content(content);
            ClientStateEvents.publishContainerPacket();
        } else if (packet instanceof ClientboundOpenScreenPacket
                   || packet instanceof ClientboundContainerSetSlotPacket
                   || packet instanceof ClientboundContainerSetDataPacket
                   || packet instanceof ClientboundContainerClosePacket) {
            ClientStateEvents.publishContainerPacket();
        }
    }
}
