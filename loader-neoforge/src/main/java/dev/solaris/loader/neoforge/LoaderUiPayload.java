package dev.solaris.loader.neoforge;

import dev.solaris.loader.LoaderUiPresentation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

record LoaderUiPayload(byte[] bytes) implements CustomPacketPayload {
    static final Type<LoaderUiPayload> TYPE =
            new Type<>(Identifier.parse(LoaderUiPresentation.CHANNEL));
    static final StreamCodec<FriendlyByteBuf, LoaderUiPayload> CODEC =
            CustomPacketPayload.codec(
                    LoaderUiPayload::write,
                    LoaderUiPayload::new);

    LoaderUiPayload {
        bytes = bytes.clone();
    }

    private LoaderUiPayload(FriendlyByteBuf buffer) {
        this(read(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBytes(bytes);
    }

    private static byte[] read(FriendlyByteBuf buffer) {
        int length = buffer.readableBytes();
        if (length > LoaderUiPresentation.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Loader open-screen payload exceeds limit");
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
