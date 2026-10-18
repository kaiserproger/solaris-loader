package dev.solaris.agent.javaagent;

/** Furnace menu data and fuel slot received by the client, sampled together. */
record ScenarioFurnaceState(
    int burnRemaining,
    int burnTotal,
    int cookProgress,
    int cookTotal,
    String fuelItemId,
    int fuelCount
) {
    boolean hasEnoughBurn(int itemCount) {
        return burnRemaining > 0 && cookTotal > 0
            && burnRemaining >= (long) cookTotal * itemCount - cookProgress;
    }
}
