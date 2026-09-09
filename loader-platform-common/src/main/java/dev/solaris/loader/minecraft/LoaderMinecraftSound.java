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

    private LoaderMinecraftSound() { }

    static void addResources(LoaderActivatedContent content, Map<Identifier, byte[]> resources) {
        Map<String, JsonObject> indexes = new TreeMap<>();
        for (String soundId : new java.util.TreeSet<>(content.sounds().keySet())) {
            Identifier id = Identifier.parse(soundId);
            Identifier clip = Identifier.fromNamespaceAndPath(id.getNamespace(), "sounds/" + id.getPath() + ".ogg");
            byte[] bytes = resources.get(clip);
            if (bytes == null) { throw new IllegalArgumentException("Loader sound has no verified OGG: " + id); }
            try (JOrbisAudioStream stream = new JOrbisAudioStream(new ByteArrayInputStream(bytes))) {
                if (stream.getFormat().getChannels() != 1 || !stream.read(4096).hasRemaining()) {
                    throw new IllegalArgumentException("Loader sounds must contain mono OGG Vorbis audio: " + id);
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
            return;
        }
        if (client.getSoundManager().getSoundEvent(id) == null) {
            throw new IllegalStateException("Loader sound was not activated: " + id);
        }
        boolean relative = command.mode() == 1;
        PLAYED.add(id);
        client.getSoundManager().play(new SimpleSoundInstance(
                id, SoundSource.MASTER, command.volume(), command.pitch(), SoundInstance.createUnseededRandom(),
                false, 0, relative ? SoundInstance.Attenuation.NONE : SoundInstance.Attenuation.LINEAR,
                command.x(), command.y(), command.z(), relative));
    }

    public static void clear() {
        if (PLAYED.isEmpty()) { return; }
        var manager = Minecraft.getInstance().getSoundManager();
        for (Identifier id : PLAYED) { manager.stop(id, SoundSource.MASTER); }
        PLAYED.clear();
    }
}
