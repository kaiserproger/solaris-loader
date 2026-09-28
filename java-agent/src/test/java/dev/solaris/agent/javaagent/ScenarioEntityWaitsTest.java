package dev.solaris.agent.javaagent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScenarioEntityWaitsTest {
    @Test
    void entityIdentityFenceRejectsEntityIdReuse() {
        UUID expectedUuid = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        ScenarioEntityIdentity identity = new ScenarioEntityIdentity(
            42,
            expectedUuid,
            "minecraft:cow"
        );

        assertTrue(identity.matches(
            42,
            expectedUuid,
            "minecraft:cow"
        ));
        assertFalse(identity.matches(
            42,
            UUID.fromString("fedcba98-7654-3210-fedc-ba9876543210"),
            "minecraft:cow"
        ));
        assertFalse(identity.matches(
            42,
            expectedUuid,
            "minecraft:sheep"
        ));
        assertFalse(identity.matches(
            43,
            expectedUuid,
            "minecraft:cow"
        ));
    }

    @Test
    void entityMotionWaitWakesOnlyAfterAnAppliedStateNotification() throws Exception {
        EntityWaitProbe probe = new EntityWaitProbe(motion(0.0), true);
        CompletableFuture<ScenarioEntityMotionObservation> result = CompletableFuture.supplyAsync(() -> {
            try {
                return ScenarioEntityWaits.waitForEntityMotion(
                    probe,
                    42,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    false,
                    0.5,
                    0.0,
                    Duration.ofSeconds(1)
                );
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        });

        assertTrue(probe.awaitEntered.await(1, TimeUnit.SECONDS));
        assertEquals(1, probe.snapshotCalls.get());
        probe.motion = motion(0.5);
        probe.publishChange();

        ScenarioEntityMotionObservation observation = result.get(1, TimeUnit.SECONDS);
        assertEquals(0.5, observation.horizontalDistance());
        assertEquals(2, probe.snapshotCalls.get());
        assertEquals(1, probe.awaitCalls.get());
    }

    @Test
    void entityMotionWaitFailsClosedWhenTheClientLevelIsReplaced() throws Exception {
        EntityWaitProbe probe = new EntityWaitProbe(motion(0.0), true);
        CompletableFuture<ScenarioEntityMotionObservation> result = CompletableFuture.supplyAsync(() -> {
            try {
                return ScenarioEntityWaits.waitForEntityMotion(
                    probe,
                    42,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    false,
                    0.5,
                    0.0,
                    Duration.ofSeconds(1)
                );
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        });

        assertTrue(probe.awaitEntered.await(1, TimeUnit.SECONDS));
        probe.level = new Object();
        probe.publishChange();

        Exception failure = org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class,
            () -> result.get(1, TimeUnit.SECONDS)
        );
        assertTrue(rootCause(failure) instanceof IllegalStateException);
        assertTrue(rootCause(failure).getMessage().contains("client level changed"));
    }

    @Test
    void entityRemovalWaitDoesNotTreatAWorldTransitionAsRemoval() throws Exception {
        EntityWaitProbe probe = new EntityWaitProbe(motion(0.0), true);
        CompletableFuture<Boolean> result = CompletableFuture.supplyAsync(() -> {
            try {
                return ScenarioEntityWaits.waitForEntityRemoved(
                    probe,
                    Duration.ofSeconds(1)
                );
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        });

        assertTrue(probe.awaitEntered.await(1, TimeUnit.SECONDS));
        probe.level = new Object();
        probe.present = false;
        probe.publishChange();

        Exception failure = org.junit.jupiter.api.Assertions.assertThrows(
            Exception.class,
            () -> result.get(1, TimeUnit.SECONDS)
        );
        assertTrue(rootCause(failure) instanceof IllegalStateException);
        assertTrue(rootCause(failure).getMessage().contains("client level changed"));
    }

    @Test
    void entityRemovalWaitWakesOnTheExactAppliedStateNotification() throws Exception {
        EntityWaitProbe probe = new EntityWaitProbe(motion(0.0), true);
        CompletableFuture<Boolean> result = CompletableFuture.supplyAsync(() -> {
            try {
                return ScenarioEntityWaits.waitForEntityRemoved(
                    probe,
                    Duration.ofSeconds(1)
                );
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        });

        assertTrue(probe.awaitEntered.await(1, TimeUnit.SECONDS));
        assertEquals(1, probe.snapshotCalls.get());
        probe.motion = null;
        probe.present = false;
        probe.publishChange();

        assertTrue(result.get(1, TimeUnit.SECONDS));
        assertEquals(2, probe.snapshotCalls.get());
        assertEquals(1, probe.awaitCalls.get());
    }

    @Test
    void entityWaitTimeoutIsFailureAndNeverAReadinessSignal() throws Exception {
        EntityWaitProbe motionProbe = new EntityWaitProbe(motion(0.0), true);
        motionProbe.timeout = true;
        ScenarioEntityMotionObservation motion = ScenarioEntityWaits.waitForEntityMotion(
            motionProbe,
            42,
            Double.NaN,
            Double.NaN,
            Double.NaN,
            false,
            0.5,
            0.0,
            Duration.ofSeconds(1)
        );
        assertTrue(motion.horizontalDistance() < 0.5);

        EntityWaitProbe removalProbe = new EntityWaitProbe(motion(0.0), true);
        removalProbe.timeout = true;
        assertFalse(ScenarioEntityWaits.waitForEntityRemoved(
            removalProbe,
            Duration.ofSeconds(1)
        ));
        assertEquals(1, removalProbe.snapshotCalls.get());
    }

    @Test
    void entityAttackWaitConfirmsOnlyAppliedDamageOrRemoval() throws Exception {
        Object level = new Object();
        EntityAttackWaitProbe damage = new EntityAttackWaitProbe(
            new ScenarioEntityWaits.EntityAttackStateSnapshot(level, true, 20.0F),
            new ScenarioEntityWaits.EntityAttackStateSnapshot(level, true, 19.0F)
        );

        var damaged = ScenarioEntityWaits.waitForEntityAttackConfirmation(
            damage,
            level,
            20.0F,
            Duration.ofSeconds(1)
        );
        assertTrue(damaged.confirmed());
        assertFalse(damaged.removed());
        assertEquals(19.0F, damaged.healthAfter());

        EntityAttackWaitProbe timeout = new EntityAttackWaitProbe(
            new ScenarioEntityWaits.EntityAttackStateSnapshot(level, true, 20.0F)
        );
        var unconfirmed = ScenarioEntityWaits.waitForEntityAttackConfirmation(
            timeout,
            level,
            20.0F,
            Duration.ZERO
        );
        assertFalse(unconfirmed.confirmed());

        EntityAttackWaitProbe removed = new EntityAttackWaitProbe(
            new ScenarioEntityWaits.EntityAttackStateSnapshot(level, false, Float.NaN)
        );
        var removal = ScenarioEntityWaits.waitForEntityAttackConfirmation(
            removed,
            level,
            20.0F,
            Duration.ofSeconds(1)
        );
        assertTrue(removal.confirmed());
        assertTrue(removal.removed());
    }

    @Test
    void entityAttackWaitRejectsWorldTransitionAsRemoval() {
        Object originalLevel = new Object();
        EntityAttackWaitProbe transitioned = new EntityAttackWaitProbe(
            new ScenarioEntityWaits.EntityAttackStateSnapshot(
                new Object(),
                false,
                Float.NaN
            )
        );

        IllegalStateException failure = assertThrows(
            IllegalStateException.class,
            () -> ScenarioEntityWaits.waitForEntityAttackConfirmation(
                transitioned,
                originalLevel,
                20.0F,
                Duration.ofSeconds(1)
            )
        );
        assertTrue(failure.getMessage().contains("client level changed"));
    }


    private static ScenarioEntityWaits.EntityMotionSample motion(double x) {
        return new ScenarioEntityWaits.EntityMotionSample(
            "minecraft:cow",
            x,
            64.0,
            0.0,
            x,
            0.0
        );
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }


    private static final class EntityWaitProbe implements ScenarioEntityWaits.EntityWaitSource {
        private final Object initialLevel = new Object();
        private final CountDownLatch awaitEntered = new CountDownLatch(1);
        private final CountDownLatch changed = new CountDownLatch(1);
        private final AtomicInteger snapshotCalls = new AtomicInteger();
        private final AtomicInteger awaitCalls = new AtomicInteger();
        private volatile Object level = initialLevel;
        private volatile ScenarioEntityWaits.EntityMotionSample motion;
        private volatile boolean present;
        private volatile boolean timeout;
        private volatile long version;

        private EntityWaitProbe(ScenarioEntityWaits.EntityMotionSample motion, boolean present) {
            this.motion = motion;
            this.present = present;
        }

        @Override
        public Object captureLevel() {
            return level;
        }

        @Override
        public ScenarioEntityWaits.EntityStateSnapshot snapshot() {
            snapshotCalls.incrementAndGet();
            return new ScenarioEntityWaits.EntityStateSnapshot(level, motion, present);
        }

        @Override
        public long stateVersion() {
            return version;
        }

        @Override
        public boolean awaitStateChange(long observedVersion, long deadlineNanos)
            throws InterruptedException {
            awaitCalls.incrementAndGet();
            awaitEntered.countDown();
            if (timeout) {
                return false;
            }
            if (!changed.await(1, TimeUnit.SECONDS)) {
                throw new AssertionError("test state event was not published");
            }
            return version != observedVersion;
        }

        private void publishChange() {
            version += 1;
            changed.countDown();
        }
    }

    private static final class EntityAttackWaitProbe
        implements ScenarioEntityWaits.EntityAttackWaitSource {
        private final ScenarioEntityWaits.EntityAttackStateSnapshot[] samples;
        private int index;
        private long version;

        private EntityAttackWaitProbe(
            ScenarioEntityWaits.EntityAttackStateSnapshot... samples
        ) {
            this.samples = samples;
        }

        @Override
        public ScenarioEntityWaits.EntityAttackStateSnapshot snapshot() {
            return samples[index];
        }

        @Override
        public long stateVersion() {
            return version;
        }

        @Override
        public boolean awaitStateChange(long observedVersion, long deadlineNanos) {
            if (index + 1 >= samples.length || deadlineNanos <= System.nanoTime()) {
                return false;
            }
            index += 1;
            version += 1;
            return true;
        }
    }
}
