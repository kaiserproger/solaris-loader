package dev.solaris.loader.forge;

import dev.solaris.loader.LoaderSoundCommand;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

record LoaderSoundPayload(byte[] bytes) implements CustomPacketPayload {
    static final Type<LoaderSoundPayload> TYPE =
            new Type<>(Identifier.parse(LoaderSoundCommand.CHANNEL));
    static final StreamCodec<RegistryFriendlyByteBuf, LoaderSoundPayload> CODEC =
            CustomPacketPayload.codec(
                    LoaderSoundPayload::write,
                    LoaderSoundPayload::new);

    LoaderSoundPayload {
        bytes = bytes.clone();
    }

    private LoaderSoundPayload(RegistryFriendlyByteBuf buffer) {
        this(read(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBytes(bytes);
    }

    private static byte[] read(RegistryFriendlyByteBuf buffer) {
        int length = buffer.readableBytes();
        if (length > LoaderSoundCommand.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Loader sound payload exceeds limit");
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
