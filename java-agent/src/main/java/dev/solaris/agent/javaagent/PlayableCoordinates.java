package dev.solaris.agent.javaagent;

/** Scenario evidence coordinates, not a second position authority. */
final class PlayableCoordinates {
    private PlayableCoordinates() {
    }

    static String coordinates(ScenarioBlockTarget target) {
        return target.x() + "," + target.y() + "," + target.z() + "/" + target.face();
    }

    static String coordinates(ScenarioPlayerObservation player) {
        return player.x() + "," + player.y() + "," + player.z();
    }

    static double horizontalDistance(ScenarioPlayerObservation a, ScenarioPlayerObservation b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    static ScenarioBlockTarget reachableUseTarget(
        ScenarioClient client,
        ScenarioBlockTarget original
    ) throws Exception {
        ScenarioBlockTarget reachable = client.findBreakableBlock(
            java.util.List.of(original.blockId()),
            ScenarioReach.WITHIN_SURVIVAL_REACH
        );
        if (reachable == null
            || reachable.x() != original.x()
            || reachable.y() != original.y()
            || reachable.z() != original.z()) {
            return original;
        }
        return new ScenarioBlockTarget(
            original.x(),
            original.y(),
            original.z(),
            reachable.face(),
            original.label(),
            original.blockId()
        );
    }

}
