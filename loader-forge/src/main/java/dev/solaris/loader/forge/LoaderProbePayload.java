package dev.solaris.loader.forge;

import dev.solaris.loader.LoaderHandshake;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record LoaderProbePayload() implements CustomPacketPayload {
    public static final Type<LoaderProbePayload> TYPE =
            new Type<>(Identifier.parse(LoaderHandshake.PROBE_CHANNEL));
    public static final StreamCodec<FriendlyByteBuf, LoaderProbePayload> CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                    },
                    buffer -> readProbe(buffer));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static LoaderProbePayload readProbe(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() != 0) {
            throw new IllegalArgumentException(
                    "invalid Solaris Loader probe length " + buffer.readableBytes());
        }
        return new LoaderProbePayload();
    }
}
