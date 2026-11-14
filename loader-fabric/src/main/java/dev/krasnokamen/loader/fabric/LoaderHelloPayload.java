<<<<<<<< HEAD:loader-fabric/src/main/java/dev/krasnokamen/loader/fabric/LoaderHelloPayload.java
package dev.krasnokamen.loader.fabric;
========
package dev.krasnokamen.loader.neoforge;
>>>>>>>> stage0/rebrand/LoaderAgentJar:loader-neoforge/src/main/java/dev/krasnokamen/loader/neoforge/LoaderHelloPayload.java

import dev.krasnokamen.loader.LoaderHandshake;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record LoaderHelloPayload() implements CustomPacketPayload {
    public static final Type<LoaderHelloPayload> TYPE =
            new Type<>(Identifier.parse(LoaderHandshake.HELLO_CHANNEL));
    public static final StreamCodec<FriendlyByteBuf, LoaderHelloPayload> CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                    },
                    buffer -> readHello(buffer));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static LoaderHelloPayload readHello(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() != 0) {
            throw new IllegalArgumentException(
                    "invalid Krasnokamen Loader hello length " + buffer.readableBytes());
        }
        return new LoaderHelloPayload();
    }
}
