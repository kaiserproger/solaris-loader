package dev.solaris.loader.minecraft;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.solaris.loader.LoaderActivatedContent;
import dev.solaris.loader.LoaderSoundCommand;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/** Verified resource preparation and client-thread playback shared by all adapters. */
public final class LoaderMinecraftSound {
    private static final Set<Identifier> PLAYED = new HashSet<>();
    private static final Set<Identifier> STARTED_THIS_TICK = new HashSet<>();
    private static final int MAX_STARTS_PER_TICK = 8;
    private static final int MAX_ENCODED_BYTES = 256 * 1024;
    private static final int MAX_DECODED_BYTES = 192_000;
    private static long startedTick = Long.MIN_VALUE;

    private LoaderMinecraftSound() { }

    static void addResources(LoaderActivatedContent content, Map<Identifier, byte[]> resources) {
        Map<String, JsonObject> indexes = new TreeMap<>();
        for (String soundId : new java.util.TreeSet<>(content.sounds().keySet())) {
            Identifier id = Identifier.parse(soundId);
            Identifier clip = Identifier.fromNamespaceAndPath(id.getNamespace(), "sounds/" + id.getPath() + ".ogg");
            byte[] bytes = resources.get(clip);
            if (bytes == null || bytes.length > MAX_ENCODED_BYTES) {
                throw new IllegalArgumentException("Loader sound is missing or too large: " + id);
            }
            try (JOrbisAudioStream stream = new JOrbisAudioStream(new ByteArrayInputStream(bytes))) {
                var format = stream.getFormat();
                if (format.getChannels() != 1 || format.getFrameSize() != 2
                        || format.getSampleRate() < 8000 || format.getSampleRate() > 48000) {
                    throw new IllegalArgumentException("Loader sound must be mono PCM at 8..48 kHz: " + id);
                }
                int decoded = 0;
                int maxDurationBytes = (int) (format.getSampleRate() * format.getFrameSize() * 2);
                int read;
                while ((read = stream.read(4096).remaining()) > 0) {
                    decoded += read;
                    if (decoded > Math.min(MAX_DECODED_BYTES, maxDurationBytes)) {
                        throw new IllegalArgumentException("Loader sound exceeds two decoded seconds: " + id);
                    }
                }
                if (decoded == 0) {
                    throw new IllegalArgumentException("Loader sound is empty: " + id);
                }
            } catch (IOException error) {
                throw new IllegalArgumentException("Invalid Loader OGG sound: " + id, error);
            }
            JsonObject file = new JsonObject();
            file.addProperty("name", soundId);
            file.addProperty("stream", true);
            JsonArray files = new JsonArray();
            files.add(file);
            JsonObject event = new JsonObject();
            event.add("sounds", files);
            indexes.computeIfAbsent(id.getNamespace(), ignored -> new JsonObject()).add(id.getPath(), event);
        }
        Gson gson = new Gson();
        for (var entry : indexes.entrySet()) {
            Identifier id = Identifier.fromNamespaceAndPath(entry.getKey(), "sounds.json");
            if (resources.putIfAbsent(id, gson.toJson(entry.getValue()).getBytes(StandardCharsets.UTF_8)) != null) {
                throw new IllegalArgumentException("Loader asset collides with generated sound index: " + id);
            }
        }
    }

    public static void present(LoaderSoundCommand command) {
        Minecraft client = Minecraft.getInstance();
        Identifier id = Identifier.parse(command.definition().id());
        if (command.mode() == 0) {
            client.getSoundManager().stop(id, SoundSource.MASTER);
            PLAYED.remove(id);
            return;
        }
        if (client.level == null) {
            return;
        }
        long tick = client.level.getGameTime();
        if (tick != startedTick) {
            startedTick = tick;
            STARTED_THIS_TICK.clear();
        }
        if (STARTED_THIS_TICK.size() >= MAX_STARTS_PER_TICK || !STARTED_THIS_TICK.add(id)) {
            return;
        }
        if (client.getSoundManager().getSoundEvent(id) == null) {
            throw new IllegalStateException("Loader sound was not activated: " + id);
        }
        boolean relative = command.mode() == 1;
        PLAYED.add(id);
        client.getSoundManager().stop(id, SoundSource.MASTER);
        client.getSoundManager().play(new SimpleSoundInstance(
                id, SoundSource.MASTER, command.volume(), command.pitch(), SoundInstance.createUnseededRandom(),
                false, 0, relative ? SoundInstance.Attenuation.NONE : SoundInstance.Attenuation.LINEAR,
                command.x(), command.y(), command.z(), relative));
    }

    public static void clear() {
        var manager = Minecraft.getInstance().getSoundManager();
        for (Identifier id : PLAYED) { manager.stop(id, SoundSource.MASTER); }
        PLAYED.clear();
        STARTED_THIS_TICK.clear();
        startedTick = Long.MIN_VALUE;
    }
}
