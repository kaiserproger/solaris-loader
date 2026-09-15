package dev.solaris.loader.neoforge;

import dev.solaris.loader.LoaderViewActionRequest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

record LoaderViewActionPayload(byte[] bytes) implements CustomPacketPayload {
    static final Type<LoaderViewActionPayload> TYPE =
            new Type<>(Identifier.parse(LoaderViewActionRequest.CHANNEL));
    static final StreamCodec<FriendlyByteBuf, LoaderViewActionPayload> CODEC =
            CustomPacketPayload.codec(
                    LoaderViewActionPayload::write,
                    LoaderViewActionPayload::new);

    LoaderViewActionPayload {
        bytes = bytes.clone();
    }

    private LoaderViewActionPayload(FriendlyByteBuf buffer) {
        this(read(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBytes(bytes);
    }

    private static byte[] read(FriendlyByteBuf buffer) {
        int length = buffer.readableBytes();
        if (length > LoaderViewActionRequest.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Loader view action payload exceeds limit");
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
