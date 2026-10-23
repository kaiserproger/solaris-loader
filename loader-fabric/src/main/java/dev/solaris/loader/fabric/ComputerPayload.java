package dev.solaris.loader.fabric;

import dev.solaris.loader.ComputerProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

record ComputerPayload(byte[] bytes) implements CustomPacketPayload {
    static final Type<ComputerPayload> TYPE = new Type<>(Identifier.parse(ComputerProtocol.CHANNEL));
    static final StreamCodec<FriendlyByteBuf, ComputerPayload> CODEC =
            CustomPacketPayload.codec(ComputerPayload::write, ComputerPayload::read);
    ComputerPayload { bytes = bytes.clone(); }
    private void write(FriendlyByteBuf buffer) { buffer.writeBytes(bytes); }
    private static ComputerPayload read(FriendlyByteBuf buffer) {
        int length = buffer.readableBytes();
        if (length < 6 || length > ComputerProtocol.MAX_OUTPUT + 6) {
            throw new IllegalArgumentException("Invalid computer payload length");
        }
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new ComputerPayload(bytes);
    }
    @Override public byte[] bytes() { return bytes.clone(); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
