package dev.solaris.agent.javaagent;

import java.time.Duration;

/** Entity movement/removal/attack waits fenced by the current client level and pushed state changes. */
final class ScenarioEntityWaits {
    private ScenarioEntityWaits() {
    }
    static ScenarioEntityMotionObservation waitForEntityMotion(
        EntityWaitSource source,
        int entityId,
        double initialX,
        double initialY,
        double initialZ,
        boolean requireKinematics,
        double minimumHorizontalDistance,
        double minimumVerticalRise,
        Duration timeout
    ) throws Exception {
        Object expectedLevel = source.captureLevel();
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        double startX = initialX;
        double startZ = initialZ;
        double lowestY = initialY;
        double horizontalDistance = 0.0;
        double verticalRise = 0.0;
        double maxHorizontalSpeed = 0.0;
        double minimumYawDelta = 180.0;
        ScenarioEntityMotionObservation latest = null;

        while (true) {
            long observedVersion = source.stateVersion();
            EntityStateSnapshot snapshot = source.snapshot();
            requireSameClientLevel(expectedLevel, snapshot.level());
            EntityMotionSample sample = snapshot.motion();
            if (sample == null) {
                return null;
            }
            if (!Double.isFinite(startX)) {
                startX = sample.x();
                startZ = sample.z();
                lowestY = sample.y();
            }

            horizontalDistance = Math.max(
                horizontalDistance,
                Math.hypot(sample.x() - startX, sample.z() - startZ)
            );
            lowestY = Math.min(lowestY, sample.y());
            verticalRise = Math.max(verticalRise, sample.y() - lowestY);
            maxHorizontalSpeed = Math.max(maxHorizontalSpeed, sample.horizontalSpeed());
            if (Double.isFinite(sample.yawDelta())) {
                minimumYawDelta = Math.min(minimumYawDelta, sample.yawDelta());
            }
            latest = new ScenarioEntityMotionObservation(
                sample.entityTypeId(),
                entityId,
                sample.x(),
                sample.y(),
                sample.z(),
                horizontalDistance,
                verticalRise,
                maxHorizontalSpeed,
                minimumYawDelta
            );
            if (
                horizontalDistance >= minimumHorizontalDistance
                    && verticalRise >= minimumVerticalRise
                    && (!requireKinematics
                        || (maxHorizontalSpeed > 0.0 && minimumYawDelta < 180.0))
            ) {
                return latest;
            }
            if (!source.awaitStateChange(observedVersion, deadlineNanos)) {
                return latest;
            }
        }
    }


    static EntityAttackConfirmation waitForEntityAttackConfirmation(
        EntityAttackWaitSource source,
        Object expectedLevel,
        float healthBefore,
        Duration timeout
    ) throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (true) {
            long observedVersion = source.stateVersion();
            EntityAttackStateSnapshot snapshot = source.snapshot();
            requireSameClientLevel(expectedLevel, snapshot.level());
            if (!snapshot.present()) {
                return new EntityAttackConfirmation(true, true, snapshot.health());
            }
            if (
                Float.isFinite(healthBefore)
                    && Float.isFinite(snapshot.health())
                    && snapshot.health() < healthBefore
            ) {
                return new EntityAttackConfirmation(true, false, snapshot.health());
            }
            if (!source.awaitStateChange(observedVersion, deadlineNanos)) {
                return new EntityAttackConfirmation(false, false, snapshot.health());
            }
        }
    }

    static boolean waitForEntityRemoved(EntityWaitSource source, Duration timeout) throws Exception {
        Object expectedLevel = source.captureLevel();
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (true) {
            long observedVersion = source.stateVersion();
            EntityStateSnapshot snapshot = source.snapshot();
            requireSameClientLevel(expectedLevel, snapshot.level());
            if (!snapshot.present()) {
                return true;
            }
            if (!source.awaitStateChange(observedVersion, deadlineNanos)) {
                return false;
            }
        }
    }


    private static void requireSameClientLevel(Object expected, Object observed) {
        if (observed != expected) {
            throw new IllegalStateException("client level changed while waiting for entity state");
        }
    }


    interface EntityWaitSource {
        Object captureLevel() throws Exception;

        EntityStateSnapshot snapshot() throws Exception;

        long stateVersion();

        boolean awaitStateChange(long observedVersion, long deadlineNanos)
            throws InterruptedException;
    }

    interface EntityAttackWaitSource {
        EntityAttackStateSnapshot snapshot() throws Exception;

        long stateVersion();

        boolean awaitStateChange(long observedVersion, long deadlineNanos)
            throws InterruptedException;
    }


    record EntityStateSnapshot(Object level, EntityMotionSample motion, boolean present) {
    }

    record EntityAttackStateSnapshot(Object level, boolean present, float health) {
    }

    record EntityAttackConfirmation(boolean confirmed, boolean removed, float healthAfter) {
    }

    record EntityMotionSample(
        String entityTypeId,
        double x,
        double y,
        double z,
        double horizontalSpeed,
        double yawDelta
    ) {
    }
}
