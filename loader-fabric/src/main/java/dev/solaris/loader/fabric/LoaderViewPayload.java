package dev.solaris.loader.fabric;

import dev.solaris.loader.LoaderViewMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

record LoaderViewPayload(byte[] bytes) implements CustomPacketPayload {
    static final Type<LoaderViewPayload> TYPE =
            new Type<>(Identifier.parse(LoaderViewMessage.CHANNEL));
    static final StreamCodec<FriendlyByteBuf, LoaderViewPayload> CODEC =
            CustomPacketPayload.codec(
                    LoaderViewPayload::write,
                    LoaderViewPayload::new);

    LoaderViewPayload {
        bytes = bytes.clone();
    }

    private LoaderViewPayload(FriendlyByteBuf buffer) {
        this(read(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBytes(bytes);
    }

    private static byte[] read(FriendlyByteBuf buffer) {
        int length = buffer.readableBytes();
        if (length > LoaderViewMessage.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Loader view payload exceeds limit");
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return bytes;
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
