package dev.solaris.agent.javaagent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.solaris.agent.client.ClientScenarioReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Observes the installed, unmodified DH client; never injects LODs or requests. */
final class DistantHorizonsScenario {
    static final String ID = "dh-protocol16";
    static final int LANDMARK_X = 1028;
    static final int LANDMARK_Z = 4;
    private static final String CORE = "com.seibel.distanthorizons.core.";

    static boolean supports(String id) {
        return List.of(ID, ID + "-configure", ID + "-debug", ID + "-control",
            ID + "-landmark", ID + "-edit", ID + "-render", ID + "-render-update",
            ID + "-state", ID + "-disconnect").contains(id);
    }

    ClientScenarioReport run(String id, Path artifacts) {
        if (!supports(id)) {
            return new ClientScenarioReport("blocked", id, List.of("unsupported scenario id: " + id));
        }
        try {
            ClassLoader loader = Minecraft.class.getClassLoader();
            Class<?> shared;
            try {
                shared = Class.forName(CORE + "api.internal.SharedApi", false, loader);
            } catch (ClassNotFoundException absent) {
                return new ClientScenarioReport("blocked", id, List.of("Distant Horizons is not loaded"));
            }
            if (id.endsWith("-configure")) {
                new MinecraftClientExecutor().callOnClientThread(() -> {
                    Object radius = Class.forName(CORE + "config.Config$Client$Advanced$Graphics$Quality", true, loader)
                        .getField("lodChunkRenderDistanceRadius").get(null);
                    call(radius, "set", 128);
                    Minecraft.getInstance().options.renderDistance().set(4);
                    return null;
                });
                writeRuntimeManifest(artifacts, shared);
                return new ClientScenarioReport("passed", id,
                    List.of("{\"configured_dh_radius_chunks\":128,\"vanilla_render_distance\":4}"));
            }
            if (id.endsWith("-disconnect")) {
                // DH drains its request/mesh queues during a normal disconnect;
                // this is not a ten-second interactive input operation.
                CompletableFuture<Void> closed = new CompletableFuture<>();
                Minecraft.getInstance().execute(() -> {
                    try {
                        new MinecraftClientFacade().disconnect();
                        // The low-level disconnect deliberately leaves a
                        // ProgressScreen; this scenario returns to the menu.
                        Minecraft.getInstance().gui.setScreen(new TitleScreen());
                        closed.complete(null);
                    } catch (Throwable failure) {
                        closed.completeExceptionally(failure);
                    }
                });
                closed.get(120, TimeUnit.SECONDS);
                if (Minecraft.getInstance().level != null
                    || !(Minecraft.getInstance().gui.screen() instanceof TitleScreen)) {
                    throw new IllegalStateException("DH disconnect did not return to the title screen");
                }
                return new ClientScenarioReport("passed", id,
                    List.of("{\"disconnected\":true,\"title_screen\":true}"));
            }
            if (id.endsWith("-debug") || id.endsWith("-control") || id.endsWith("-render")
                || id.endsWith("-render-update")) {
                new MinecraftClientExecutor().callOnClientThread(() -> {
                    // Command chat/toasts otherwise cover the distant test column.
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.hud.isHidden() != !id.endsWith("-debug")) minecraft.gui.hud.toggle();
                    if (id.endsWith("-debug")) Minecraft.getInstance().debugEntries.toggleDebugOverlay();
                    return null;
                });
            }
            Object world = shared.getMethod("getAbstractDhWorld").invoke(null);
            if (world == null) {
                throw new IllegalStateException("DH has no client world after Minecraft entered Play");
            }
            Object network = field(world, "networkState");
            if (id.equals(ID)) {
                CompletableFuture<Void> ready = new CompletableFuture<>();
                Runnable changed = () -> ready.complete(null);
                try (AutoCloseable listener = (AutoCloseable) call(network, "addSessionConfigListener", changed)) {
                    if ((boolean) call(network, "isReady")) ready.complete(null);
                    ready.get(120, TimeUnit.SECONDS);
                }
            }
            JsonObject state;
            if (id.endsWith("-landmark") || id.endsWith("-edit")) {
                String expected = id.endsWith("-edit") ? "minecraft:emerald_block" : "minecraft:gold_block";
                state = awaitLandmark(loader, world, network, expected);
            } else if (id.endsWith("-render") || id.endsWith("-render-update")) {
                JsonArray previous = id.endsWith("-render-update")
                    ? JsonParser.parseString(Files.readString(artifacts.resolve("previous-render.json"))).getAsJsonArray()
                    : null;
                state = awaitRender(loader, world, previous);
            } else {
                state = inspect(loader, world, network);
            }
            int protocol = Class.forName("com.seibel.distanthorizons.coreapi.ModInfo", true, loader)
                .getField("PROTOCOL_VERSION").getInt(null);
            state.addProperty("protocol", protocol);
            Files.createDirectories(artifacts);
            Files.writeString(artifacts.resolve(id + ".json"), state.toString() + "\n");
            boolean passed = protocol == 16;
            if (id.equals(ID)) {
                passed &= state.get("ready").getAsBoolean();
                // GenerationMaxChunkRadius bounds the server's fixed generation
                // region (0 means unlimited), not distance from this player.
                passed &= state.get("generation_distance_chunks").getAsInt() == 128;
            }
            if (id.endsWith("-control")) {
                passed &= !state.get("ready").getAsBoolean();
                passed &= !state.get("landmark_gold").getAsBoolean();
                passed &= !state.get("landmark_vanilla_loaded").getAsBoolean();
            }
            if (id.endsWith("-landmark") || id.endsWith("-edit")) {
                passed &= state.get("ready").getAsBoolean();
                passed &= !state.get("landmark_vanilla_loaded").getAsBoolean();
            }
            return new ClientScenarioReport(passed ? "passed" : "failed", id, List.of(state.toString()));
        } catch (Exception failure) {
            return new ClientScenarioReport("failed", id, List.of(failure.toString()));
        }
    }

    private static void writeRuntimeManifest(Path artifacts, Class<?> shared) throws Exception {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("java_version", System.getProperty("java.version"));
        manifest.addProperty("java_vendor", System.getProperty("java.vendor"));
        JsonArray entries = new JsonArray();
        var paths = new java.util.LinkedHashSet<Path>();
        for (String property : List.of("java.class.path", "jdk.module.path")) {
            String value = System.getProperty(property, "");
            manifest.addProperty(property, value);
            for (String entry : value.split(java.io.File.pathSeparator)) {
                if (!entry.isEmpty()) paths.add(Path.of(entry).toAbsolutePath().normalize());
            }
        }
        for (Class<?> source : List.of(shared, Minecraft.class, DistantHorizonsScenario.class)) {
            var location = source.getProtectionDomain().getCodeSource().getLocation();
            paths.add(Path.of(location.toURI()).toAbsolutePath().normalize());
        }
        byte[] buffer = new byte[32 * 1024];
        for (Path path : paths) {
            try (var files = Files.walk(path)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    var digest = java.security.MessageDigest.getInstance("SHA-256");
                    try (var stream = Files.newInputStream(file)) {
                        int count;
                        while ((count = stream.read(buffer)) != -1) digest.update(buffer, 0, count);
                    }
                    JsonObject entry = new JsonObject();
                    entry.addProperty("path", file.toString());
                    entry.addProperty("size", Files.size(file));
                    entry.addProperty("sha256", java.util.HexFormat.of().formatHex(digest.digest()));
                    entries.add(entry);
                }
            }
        }
        manifest.add("resolved_runtime_files", entries);
        Files.createDirectories(artifacts);
        Files.writeString(artifacts.resolve("client-runtime-manifest.json"), manifest + "\n");
    }

    private static JsonObject awaitRender(ClassLoader loader, Object world, JsonArray previous) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(20);
        MinecraftClientExecutor executor = new MinecraftClientExecutor();
        for (;;) {
            long tick = ClientStateEvents.tickVersion();
            JsonObject state = executor.callOnClientThread(() -> renderSnapshot(loader, world));
            if (hasFreshGeometry(state, previous) && !state.get("overlay_open").getAsBoolean()) {
                if (state.get("landmark_vanilla_loaded").getAsBoolean()) {
                    throw new IllegalStateException("landmark arrived through vanilla chunks: " + state);
                }
                return state;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0 || !ClientStateEvents.awaitTickChange(
                tick, java.time.Duration.ofNanos(remaining))) {
                throw new IllegalStateException("DH did not upload visible landmark geometry: " + state);
            }
        }
    }

    static boolean hasFreshGeometry(JsonObject state, JsonArray previous) {
        if (!state.get("landmark_uploaded").getAsBoolean()) return false;
        if (previous == null) return true;
        JsonArray current = state.getAsJsonArray("visible_uploaded_landmark_buffers");
        for (var buffer : current) {
            for (var old : previous) {
                var entry = buffer.getAsJsonObject();
                var baseline = old.getAsJsonObject();
                if (entry.get("section").equals(baseline.get("section"))
                    && entry.get("buffer_identity").equals(baseline.get("buffer_identity"))) return false;
            }
        }
        return !current.isEmpty();
    }

    private static JsonObject renderSnapshot(ClassLoader loader, Object world) throws Exception {
        var minecraft = Minecraft.getInstance();
        JsonObject state = new JsonObject();
        state.addProperty("overlay_open", minecraft.gui.overlay() != null);
        state.addProperty("landmark_vanilla_loaded",
            minecraft.level.getChunkSource().hasChunk(LANDMARK_X >> 4, LANDMARK_Z >> 4));
        Class<?> section = Class.forName(CORE + "pos.DhSectionPos", true, loader);
        long target = (long) section.getMethod("encode", byte.class, int.class, int.class)
            .invoke(null, (byte) 6, LANDMARK_X >> 6, LANDMARK_Z >> 6);
        JsonArray visible = new JsonArray();
        for (Object level : (Iterable<?>) call(world, "getAllLoadedLevels")) {
            Object wrapper = field(level, "levelWrapper");
            if (!minecraft.level.dimension().identifier().toString().equals(
                String.valueOf(call(wrapper, "getDimensionName")))) continue;
            Object module = field(level, "clientside");
            Object render = ((java.util.concurrent.atomic.AtomicReference<?>)
                field(module, "ClientRenderStateRef")).get();
            if (render == null) continue;
            Object buffers = call(field(render, "renderBufferHandler"), "getColumnRenderBuffers");
            int count = ((Number) call(buffers, "size")).intValue();
            for (int i = 0; i < count; i++) {
                Object buffer = call(buffers, "get", i);
                long position = ((Number) field(buffer, "pos")).longValue();
                if (!(boolean) section.getMethod("contains", long.class, long.class)
                    .invoke(null, position, target)) continue;
                if (!hasUploadedGeometry(buffer)) continue;
                JsonObject entry = new JsonObject();
                entry.addProperty("section", String.valueOf(section.getMethod("toString", long.class)
                    .invoke(null, position)));
                entry.addProperty("buffer_identity", System.identityHashCode(buffer));
                entry.addProperty("vbo_count", (Number) call(buffer, "vboBufferCount"));
                visible.add(entry);
            }
        }
        state.add("visible_uploaded_landmark_buffers", visible);
        state.addProperty("landmark_uploaded", !visible.isEmpty());
        return state;
    }

    private static JsonObject awaitLandmark(ClassLoader loader, Object world, Object network, String expected)
        throws Exception {
        Path game = Minecraft.getInstance().gameDirectory.toPath();
        // SQLite commits wake this read-only observer. Never sleep or manufacture a DTO.
        try (WatchService changes = game.getFileSystem().newWatchService(); var directories = Files.walk(game)) {
            for (Path directory : directories.filter(Files::isDirectory).toList()) {
                directory.register(changes, StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);
            }
            // The real client requests nearer leaves first. A clean 128-chunk
            // view must traverse those within the advertised two-request budget.
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(30);
            for (;;) {
                JsonObject state = inspect(loader, world, network);
                String property = expected.equals("minecraft:gold_block") ? "landmark_gold" : "landmark_emerald";
                if (state.get(property).getAsBoolean()) return state;
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new IllegalStateException("DH never received landmark " + expected + ": " + state);
                var key = changes.poll(remaining, TimeUnit.NANOSECONDS);
                if (key == null) throw new IllegalStateException("No DH cache commit before landmark deadline: " + state);
                for (var event : key.pollEvents()) {
                    Path created = ((Path) key.watchable()).resolve((Path) event.context());
                    if (Files.isDirectory(created)) {
                        try (var added = Files.walk(created)) {
                            for (Path directory : added.filter(Files::isDirectory).toList()) {
                                directory.register(changes, StandardWatchEventKinds.ENTRY_MODIFY,
                                    StandardWatchEventKinds.ENTRY_CREATE);
                            }
                        }
                    }
                }
                key.reset();
            }
        }
    }

    private static JsonObject inspect(ClassLoader loader, Object world, Object network) throws Exception {
        JsonObject result = new JsonObject();
        boolean ready = (boolean) call(network, "isReady");
        result.addProperty("ready", ready);
        Object session = call(network, "getSession");
        result.addProperty("session_closed", (boolean) call(session, "isClosed"));
        result.addProperty("session_close_reason", String.valueOf(call(session, "getCloseReason")));
        Object config = field(network, "sessionConfig");
        result.addProperty("generation_radius_chunks", (Number) call(config, "getGenerationMaxChunkRadius"));
        result.addProperty("generation_distance_chunks", (Number) call(config, "getMaxGenerationRequestDistance"));
        result.addProperty("updates_enabled", (boolean) call(config, "isRealTimeUpdatesEnabled"));
        result.addProperty("sync_enabled", (boolean) call(config, "getSynchronizeOnLoad"));
        result.addProperty("generator_plan", String.valueOf(call(config, "getGeneratorPlan")));
        result.addProperty("generation_request_rate", (Number) call(config, "getGenerationRequestRateLimit"));
        java.util.ArrayList<String> debug = new java.util.ArrayList<>();
        call(world, "addDebugMenuStringsToList", debug);
        JsonArray debugLines = new JsonArray();
        debug.forEach(debugLines::add);
        result.add("dh_debug", debugLines);
        String activeDimension = Minecraft.getInstance().level.dimension().identifier().toString();
        result.addProperty("dimension", activeDimension);
        JsonArray levels = new JsonArray();
        JsonArray columns = new JsonArray();
        boolean gold = false, emerald = false;
        Object repo = Class.forName(CORE + "api.external.methods.data.DhApiTerrainDataRepo", true, loader)
            .getField("INSTANCE").get(null);
        for (Object level : (Iterable<?>) call(world, "getAllLoadedLevels")) {
            Object wrapper = field(level, "levelWrapper");
            if (!activeDimension.equals(String.valueOf(call(wrapper, "getDimensionName")))) continue;
            levels.add(String.valueOf(call(wrapper, "getDhIdentifier")));
            java.util.ArrayList<String> levelDebug = new java.util.ArrayList<>();
            call(level, "addDebugMenuStringsToList", levelDebug);
            levelDebug.forEach(debugLines::add);
            result.addProperty("level_rendering", (boolean) call(level, "isRendering"));
            result.addProperty("level_should_generate", (boolean) call(level, "shouldDoWorldGen"));
            result.addProperty("worldgen_running",
                (boolean) call(field(level, "lodRequestModule"), "isWorldGenRunning"));
            Object answer = readLandmarkColumn(repo, wrapper);
            result.addProperty("landmark_lookup_success", (boolean) field(answer, "success"));
            result.addProperty("landmark_lookup_message", String.valueOf(field(answer, "message")));
            if (!(boolean) field(answer, "success")) continue;
            Object points = field(answer, "payload");
            if (points == null) continue;
            for (int i = 0; i < Array.getLength(points); i++) {
                Object point = Array.get(points, i);
                String block = String.valueOf(call(field(point, "blockStateWrapper"), "getSerialString"));
                JsonObject column = new JsonObject();
                column.addProperty("block", block);
                column.addProperty("bottom_y", ((Number) field(point, "bottomYBlockPos")).intValue());
                column.addProperty("top_y", ((Number) field(point, "topYBlockPos")).intValue());
                column.addProperty("sky_light", ((Number) field(point, "skyLightLevel")).intValue());
                column.addProperty("block_light", ((Number) field(point, "blockLightLevel")).intValue());
                columns.add(column);
                gold |= block.contains("minecraft:gold_block");
                emerald |= block.contains("minecraft:emerald_block");
            }
        }
        result.add("levels", levels);
        result.add("landmark_columns", columns);
        result.addProperty("landmark_gold", gold);
        result.addProperty("landmark_emerald", emerald);
        var minecraft = Minecraft.getInstance();
        result.addProperty("landmark_vanilla_loaded", minecraft.level != null
            && minecraft.level.getChunkSource().hasChunk(LANDMARK_X >> 4, LANDMARK_Z >> 4));
        result.addProperty("vanilla_render_distance", minecraft.options.renderDistance().get());
        return result;
    }

    static boolean hasUploadedGeometry(Object buffer) throws Exception {
        return (boolean) field(buffer, "buffersUploaded") && (boolean) call(buffer, "hasNonNullVbos")
            && ((Number) call(buffer, "vboBufferCount")).intValue() > 0;
    }

    static Object readLandmarkColumn(Object repo, Object wrapper) throws Exception {
        try (AutoCloseable cache = (AutoCloseable) call(repo, "createSoftCache")) {
            return call(repo, "getColumnDataAtBlockPos", wrapper, LANDMARK_X, LANDMARK_Z, cache);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        return target.getClass().getField(name).get(target);
    }

    private static Object call(Object target, String name, Object... arguments) throws Exception {
        Method found = null;
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            if (found != null) throw new NoSuchMethodException("Ambiguous DH API method: " + name);
            found = method;
        }
        if (found == null) throw new NoSuchMethodException(target.getClass().getName() + "." + name);
        return found.invoke(target, arguments);
    }
}
