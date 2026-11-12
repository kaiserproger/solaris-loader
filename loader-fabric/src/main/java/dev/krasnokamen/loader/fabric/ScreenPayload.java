package dev.krasnokamen.loader.fabric;

import dev.krasnokamen.loader.ScreenProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One raw {@code krasnokamen:screen} message in either direction; {@link ScreenProtocol} owns the format. */
record ScreenPayload(byte[] bytes) implements CustomPacketPayload {
    static final Type<ScreenPayload> TYPE = new Type<>(Identifier.parse(ScreenProtocol.CHANNEL));
    static final StreamCodec<FriendlyByteBuf, ScreenPayload> CODEC =
            CustomPacketPayload.codec(ScreenPayload::write, ScreenPayload::read);

    ScreenPayload {
        bytes = bytes.clone();
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBytes(bytes);
    }

    private static ScreenPayload read(FriendlyByteBuf buffer) {
        int length = buffer.readableBytes();
        if (length < 18 || length > ScreenProtocol.MAX_PAYLOAD) {
            throw new IllegalArgumentException("Invalid screen payload length");
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new ScreenPayload(bytes);
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
