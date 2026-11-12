package dev.krasnokamen.loader.fabric.mixin;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientCommonPacketListenerImpl.class)
public interface ClientCommonPacketListenerAccessor {
    @Accessor("serverData")
    ServerData krasnokamen$serverData();

    @Accessor("connection")
    Connection krasnokamen$connection();
}
