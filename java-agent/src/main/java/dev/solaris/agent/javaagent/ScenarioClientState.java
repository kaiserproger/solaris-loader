package dev.solaris.agent.javaagent;

import net.minecraft.client.Minecraft;

import java.time.Duration;

/** Valid client state and pushed state/tick notifications shared by scenario operations. */
final class ScenarioClientState {
    private ScenarioClientState() {
    }
    static boolean awaitClientStateChange(long observedVersion, long deadlineNanos)
        throws InterruptedException {
        long remainingNanos = deadlineNanos - System.nanoTime();
        return remainingNanos > 0L
            && ClientStateEvents.awaitChange(observedVersion, Duration.ofNanos(remainingNanos));
    }

    static boolean awaitClientTick(long observedVersion, long deadlineNanos)
        throws InterruptedException {
        long remainingNanos = deadlineNanos - System.nanoTime();
        return remainingNanos > 0L
            && ClientStateEvents.awaitTickChange(
                observedVersion,
                Duration.ofNanos(remainingNanos)
            );
    }
    static Minecraft requireInPlay() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.gameMode == null) {
            throw new IllegalStateException("client is not in play");
        }
        if (minecraft.getConnection() == null) {
            throw new IllegalStateException("client connection is not available");
        }
        return minecraft;
    }
}
