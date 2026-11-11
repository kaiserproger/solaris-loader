package dev.solaris.loader.fabric;

import static org.junit.jupiter.api.Assertions.*;

import dev.solaris.loader.ScreenAudio;
import dev.solaris.loader.ScreenProtocol;
import java.nio.ByteBuffer;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

class ScreenAudioSoundTest {
    @Test
    void fabricExtensionSuppliesStreamWithoutResourceDecoding() throws Exception {
        ScreenAudio audio = new ScreenAudio();
        ScreenAudioSound sound = new ScreenAudioSound(new ScreenProtocol.Id(3, 10, 7, 2, 2, 1, 0), audio);
        FabricSoundInstance extension = sound;
        // Fabric's own SoundEngine mixin calls this hook; no competing redirect is needed.
        AudioStream stream = extension.getAudioStream(null,
                Identifier.fromNamespaceAndPath("solaris_loader", "screen_audio"), false).join();
        assertEquals(ScreenAudio.RATE, stream.getFormat().getSampleRate());
        ByteBuffer silence = stream.read(4096);
        try {
            assertEquals(ScreenAudio.SAMPLES * Short.BYTES, silence.remaining());
            while (silence.hasRemaining()) {
                assertEquals(0, silence.get());
            }
        } finally {
            MemoryUtil.memFree(silence);
            stream.close();
        }
        assertTrue(audio.closed());
        sound.tick();
        assertTrue(sound.isStopped());
    }
}
