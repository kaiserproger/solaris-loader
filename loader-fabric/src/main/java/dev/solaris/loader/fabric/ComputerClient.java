package dev.solaris.loader.fabric;

import dev.solaris.loader.ComputerProtocol;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;

/** Explicit local JVM opt-in; servers and downloaded content cannot enable it. */
final class ComputerClient {
    private static final AtomicInteger NEXT = new AtomicInteger();
    private ComputerClient() {}
    static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(ComputerPayload.TYPE, ComputerPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ComputerPayload.TYPE, ComputerPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ComputerPayload.TYPE, (payload, context) -> {
            Connection origin = context.packetContext().orElseThrow(PacketContext.CONNECTION);
            ComputerProtocol.Reply reply = ComputerProtocol.reply(payload.bytes());
            context.client().execute(() -> {
                var listener = context.client().getConnection();
                if (listener != null && listener.getConnection() == origin && origin.isConnected()
                        && context.client().screen instanceof ComputerScreen screen) screen.receive(reply);
            });
        });
        if (!Boolean.getBoolean("solaris.computer.enabled")) return;
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            Minecraft client = Minecraft.getInstance();
            if (player != client.player || hand != InteractionHand.MAIN_HAND
                    || player.isShiftKeyDown() || !level.getBlockState(hit.getBlockPos()).is(Blocks.IRON_BLOCK)
                    || client.getConnection() == null) return InteractionResult.PASS;
            Connection origin = client.getConnection().getConnection();
            int session = NEXT.updateAndGet(value -> value == -1 ? 1 : value + 1);
            var screen = new ComputerScreen(session, bytes -> {
                if (client.getConnection() != null && client.getConnection().getConnection() == origin
                        && origin.isConnected()) ClientPlayNetworking.send(new ComputerPayload(bytes));
            });
            client.setScreen(screen);
            var pos = hit.getBlockPos();
            ClientPlayNetworking.send(new ComputerPayload(ComputerProtocol.open(session, pos.getX(), pos.getY(), pos.getZ())));
            return InteractionResult.SUCCESS;
        });
    }
}
