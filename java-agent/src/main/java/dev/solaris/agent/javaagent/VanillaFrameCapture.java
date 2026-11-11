package dev.solaris.agent.javaagent;

import com.google.gson.JsonArray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Client-thread render hooks service a pending screenshot without sampling another frame. */
public final class VanillaFrameCapture {
    private static final AtomicReference<Request> PENDING = new AtomicReference<>();

    private record Request(Path directory, Path path, CompletableFuture<JsonArray> completion) {}
    private static Request extractedRequest;
    private static JsonArray extracted;

    private VanillaFrameCapture() {}

    static CompletableFuture<JsonArray> request(Path directory, Path path) {
        CompletableFuture<JsonArray> completion = new CompletableFuture<>();
        Request request = new Request(directory, path, completion);
        if (!PENDING.compareAndSet(null, request)) {
            throw new IllegalStateException("vanilla screenshot already pending");
        }
        completion.whenComplete((ignored, error) -> PENDING.compareAndSet(request, null));
        return completion;
    }

    public static void afterExtract(Minecraft minecraft) {
        extractedRequest = PENDING.get();
        if (extractedRequest != null) {
            try {
                extracted = MinecraftClientFacade.renderedGait(minecraft);
            } catch (RuntimeException error) {
                extractedRequest.completion().completeExceptionally(error);
                extractedRequest = null;
                extracted = null;
            }
        }
    }

    public static void afterRender(Minecraft minecraft) {
        Request request = extractedRequest;
        extractedRequest = null;
        if (request == null || !PENDING.compareAndSet(request, null)) return;
        JsonArray gait = extracted;
        extracted = null;
        if (gait == null) {
            request.completion().completeExceptionally(new IllegalStateException("missing frame extraction"));
            return;
        }
        try {
            Screenshot.grab(request.directory().toFile(), request.path().getFileName().toString(),
                MinecraftGuiAccess.mainRenderTarget(minecraft), 1, message -> request.completion().complete(gait));
        } catch (RuntimeException error) {
            request.completion().completeExceptionally(error);
        }
    }
}
