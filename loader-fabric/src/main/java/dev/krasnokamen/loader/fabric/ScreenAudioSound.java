package dev.krasnokamen.loader.fabric;

import dev.krasnokamen.loader.ScreenAudio;
import dev.krasnokamen.loader.ScreenGeometry;
import dev.krasnokamen.loader.ScreenProtocol;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.lwjgl.system.MemoryUtil;
import javax.sound.sampled.AudioFormat;

/** Uses Minecraft's own sound engine, device, master/category volume and positional listener. */
public final class ScreenAudioSound extends AbstractTickableSoundInstance implements FabricSoundInstance {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("krasnokamen_loader", "screen_audio");
    private static final Sound SOUND = new Sound(ID, ConstantFloat.of(1), ConstantFloat.of(1),
            1, Sound.Type.FILE, true, false, 32);
    private static final WeighedSoundEvents EVENT = new WeighedSoundEvents(ID, null);
    private static final AudioFormat FORMAT = new AudioFormat(ScreenAudio.RATE, 16, 1, true, false);
    private final ScreenAudio audio;
    private final AudioStream stream;

    ScreenAudioSound(ScreenProtocol.Id id, ScreenAudio audio) {
        super(SoundEvent.createVariableRangeEvent(ID), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.audio = audio;
        sound = SOUND;
        volume = 1;
        pitch = 1;
        attenuation = SoundInstance.Attenuation.LINEAR;
        ScreenGeometry.Basis basis = ScreenGeometry.basis(id);
        x = basis.origin()[0] + basis.right()[0] * id.width() / 2.0 + basis.down()[0] * id.height() / 2.0;
        y = basis.origin()[1] + basis.right()[1] * id.width() / 2.0 + basis.down()[1] * id.height() / 2.0;
        z = basis.origin()[2] + basis.right()[2] * id.width() / 2.0 + basis.down()[2] * id.height() / 2.0;
        stream = new AudioStream() {
            @Override
            public AudioFormat getFormat() {
                return FORMAT;
            }

            @Override
            public ByteBuffer read(int bytes) {
                // Channel owns/frees the native buffer after upload. Short reads keep queue latency bounded.
                ByteBuffer pcm = MemoryUtil.memAlloc(Math.min(bytes, ScreenAudio.SAMPLES * Short.BYTES))
                        .order(ByteOrder.LITTLE_ENDIAN);
                audio.read(pcm);
                return pcm.flip();
            }

            @Override
            public void close() {
                audio.close();
            }
        };
    }

    @Override
    public WeighedSoundEvents getOrResolve(SoundManager manager) {
        return EVENT;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (audio.closed()) {
            stop();
        }
    }

    boolean owns(ScreenAudio candidate) {
        return candidate == audio;
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(
            SoundBufferLibrary buffers, Identifier path, boolean looping) {
        return CompletableFuture.completedFuture(stream);
    }
}
