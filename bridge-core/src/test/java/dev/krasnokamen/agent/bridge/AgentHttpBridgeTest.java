package dev.krasnokamen.agent.bridge;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Waits block on the exact event; this is only a hang guard that turns a lost
// event (e.g. a serialized dispatcher) into a failure instead of a stuck build.
@Timeout(value = 2, unit = TimeUnit.MINUTES)
final class AgentHttpBridgeTest {
    @Test
    void acceptsLoopbackRpcWithSharedSecret() throws Exception {
        CommandRegistry registry = new CommandRegistry();
        registry.register("ping", request -> {
            JsonObject payload = new JsonObject();
            payload.addProperty("pong", true);
            return payload;
        });

        try (AgentHttpBridge bridge = AgentHttpBridge.start("run-secret", 0, registry)) {
            HttpResponse<String> response = post(bridge.port(), """
                {"id":1,"secret":"run-secret","command":"ping","payload":{}}
                """);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"ok\":true"));
            assertTrue(response.body().contains("\"pong\":true"));
        }
    }

    @Test
    void bindsIpv4LoopbackForRunnerUrlCompatibility() throws Exception {
        try (AgentHttpBridge bridge = AgentHttpBridge.start("run-secret", 0, new CommandRegistry())) {
            assertEquals("127.0.0.1", bridge.hostAddress());
        }
    }

    @Test
    void rejectsMissingSecretWithoutExecutingCommand() throws Exception {
        CommandRegistry registry = new CommandRegistry();
        registry.register("ping", request -> {
            throw new AssertionError("command must not execute without secret");
        });

        try (AgentHttpBridge bridge = AgentHttpBridge.start("run-secret", 0, registry)) {
            HttpResponse<String> response = post(bridge.port(), """
                {"id":2,"secret":"wrong","command":"ping","payload":{}}
                """);

            assertEquals(403, response.statusCode());
            assertTrue(response.body().contains("\"ok\":false"));
            assertTrue(response.body().contains("\"code\":\"forbidden\""));
        }
    }

    @Test
    void rejectsUnknownCommandAsStructuredError() throws Exception {
        try (AgentHttpBridge bridge = AgentHttpBridge.start("run-secret", 0, new CommandRegistry())) {
            HttpResponse<String> response = post(bridge.port(), """
                {"id":3,"secret":"run-secret","command":"mine","payload":{}}
                """);

            assertEquals(404, response.statusCode());
            assertTrue(response.body().contains("\"ok\":false"));
            assertTrue(response.body().contains("\"code\":\"unknown-command\""));
        }
    }

    @Test
    void convertsCommandErrorsIntoStructuredDiagnosticsInsteadOfDroppingConnection() throws Exception {
        CommandRegistry registry = new CommandRegistry();
        registry.register("explode", request -> {
            throw new AssertionError("long scenario invariant failed");
        });

        try (AgentHttpBridge bridge = AgentHttpBridge.start("run-secret", 0, registry)) {
            HttpResponse<String> response = post(bridge.port(), """
                {"id":31,"secret":"run-secret","command":"explode","payload":{}}
                """);

            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("\"ok\":false"));
            assertTrue(response.body().contains("\"code\":\"command-error\""));
            assertTrue(response.body().contains("java.lang.AssertionError: long scenario invariant failed"));
        }
    }

    @Test
    void servesObservationWhileLongCommandIsRunning() throws Exception {
        CountDownLatch longCommandStarted = new CountDownLatch(1);
        CountDownLatch releaseLongCommand = new CountDownLatch(1);
        CommandRegistry registry = new CommandRegistry();
        registry.register("long-command", request -> {
            longCommandStarted.countDown();
            releaseLongCommand.await();
            return new JsonObject();
        });
        registry.registerConcurrent("state", request -> new JsonObject());

        ExecutorService clients = Executors.newFixedThreadPool(1);
        try (AgentHttpBridge bridge = AgentHttpBridge.start("run-secret", 0, registry)) {
            Future<HttpResponse<String>> longCommand = clients.submit(() -> post(bridge.port(), """
                {"id":4,"secret":"run-secret","command":"long-command","payload":{}}
                """));
            longCommandStarted.await();

            // The long command is released only after this response, so a served
            // observation proves it was not queued behind the long command.
            assertEquals(200, post(bridge.port(), """
                {"id":5,"secret":"run-secret","command":"state","payload":{}}
                """).statusCode());
            assertFalse(longCommand.isDone());

            releaseLongCommand.countDown();
            assertEquals(200, longCommand.get().statusCode());
        } finally {
            releaseLongCommand.countDown();
            clients.shutdownNow();
        }
    }

    @Test
    void dispatchesControlWhileNotificationWaitsOccupyLegacyWorkerCount() throws Exception {
        int legacyWorkerCount = 4;
        CountDownLatch waitsStarted = new CountDownLatch(legacyWorkerCount);
        CountDownLatch releaseWaits = new CountDownLatch(1);
        CommandRegistry registry = new CommandRegistry();
        registry.registerConcurrent("wait_state_change", request -> {
            waitsStarted.countDown();
            releaseWaits.await();
            return new JsonObject();
        });
        registry.register("disconnect", request -> new JsonObject());

        ExecutorService clients = Executors.newFixedThreadPool(legacyWorkerCount);
        List<Future<HttpResponse<String>>> waits = new ArrayList<>();
        try (AgentHttpBridge bridge = AgentHttpBridge.start("run-secret", 0, registry)) {
            for (int index = 0; index < legacyWorkerCount; index += 1) {
                long requestId = 100L + index;
                waits.add(clients.submit(() -> post(bridge.port(), """
                    {"id":%d,"secret":"run-secret","command":"wait_state_change","payload":{}}
                    """.formatted(requestId))));
            }
            waitsStarted.await();

            // The waits are released only after this response: control was dispatched
            // while every legacy worker slot was occupied.
            assertEquals(200, post(bridge.port(), """
                {"id":200,"secret":"run-secret","command":"disconnect","payload":{}}
                """).statusCode());
            releaseWaits.countDown();
            for (Future<HttpResponse<String>> wait : waits) {
                assertEquals(200, wait.get().statusCode());
            }
        } finally {
            releaseWaits.countDown();
            clients.shutdownNow();
        }
    }

    @Test
    void closeInterruptsOutstandingNotificationWaits() throws Exception {
        CountDownLatch waitStarted = new CountDownLatch(1);
        CountDownLatch waitInterrupted = new CountDownLatch(1);
        CountDownLatch releaseWait = new CountDownLatch(1);
        CommandRegistry registry = new CommandRegistry();
        registry.registerConcurrent("wait_state_change", request -> {
            waitStarted.countDown();
            try {
                releaseWait.await();
            } catch (InterruptedException error) {
                waitInterrupted.countDown();
                throw error;
            }
            return new JsonObject();
        });

        ExecutorService clients = Executors.newFixedThreadPool(1);
        AgentHttpBridge bridge = AgentHttpBridge.start("run-secret", 0, registry);
        boolean closed = false;
        try {
            clients.submit(() -> post(bridge.port(), """
                {"id":300,"secret":"run-secret","command":"wait_state_change","payload":{}}
                """));
            waitStarted.await();

            // close() returns only once the executor terminated, which the wait
            // allows only by observing the interrupt.
            bridge.close();
            closed = true;
            assertEquals(0, waitInterrupted.getCount(), "bridge close must interrupt outstanding notification waits");
        } finally {
            releaseWait.countDown();
            if (!closed) {
                bridge.close();
            }
            clients.shutdownNow();
        }
    }

    @Test
    void closeFailsWhenHandlerOutlivesShutdownBoundAndCanTerminateAfterRelease() throws Exception {
        Duration shutdownTimeout = Duration.ofMillis(50);
        CountDownLatch waitStarted = new CountDownLatch(1);
        CountDownLatch interruptObserved = new CountDownLatch(1);
        CountDownLatch releaseWait = new CountDownLatch(1);
        CountDownLatch handlerTerminated = new CountDownLatch(1);
        CommandRegistry registry = new CommandRegistry();
        registry.registerConcurrent("wait_state_change", request -> {
            waitStarted.countDown();
            try {
                releaseWait.await();
            } catch (InterruptedException error) {
                interruptObserved.countDown();
                releaseWait.await();
            } finally {
                handlerTerminated.countDown();
            }
            return new JsonObject();
        });

        ExecutorService clients = Executors.newFixedThreadPool(1);
        AgentHttpBridge bridge = AgentHttpBridge.start(
            "run-secret",
            0,
            registry,
            shutdownTimeout
        );
        try {
            clients.submit(() -> post(bridge.port(), """
                {"id":400,"secret":"run-secret","command":"wait_state_change","payload":{}}
                """));
            waitStarted.await();

            // The handler survives the interrupt until released, so the bound expires
            // however long the host takes to schedule it.
            IllegalStateException failure = assertThrows(IllegalStateException.class, bridge::close);
            assertEquals(
                "HTTP bridge executor did not terminate within " + shutdownTimeout,
                failure.getMessage()
            );
            interruptObserved.await();

            releaseWait.countDown();
            handlerTerminated.await();
        } finally {
            releaseWait.countDown();
            handlerTerminated.await();
            bridge.close();
            clients.shutdownNow();
        }
    }

    private static HttpResponse<String> post(int port, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/rpc"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
