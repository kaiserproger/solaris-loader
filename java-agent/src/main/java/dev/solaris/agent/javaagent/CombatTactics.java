package dev.solaris.agent.javaagent;

final class CombatTactics {
    private CombatTactics() {
    }

    static boolean hostileTooClose(boolean creeper, double playerDistanceSquared, double targetDistanceSquared) {
        double threshold = creeper ? 49.0 : 36.0;
        return playerDistanceSquared <= threshold || targetDistanceSquared <= threshold;
    }

    static boolean safeSupport(double feetY, int supportY) {
        double supportTop = (double) supportY + 1.0;
        return Double.isFinite(feetY) && feetY - supportTop <= 3.0 && supportTop - feetY <= 1.0;
    }

    static int movement(double distanceSquared, boolean attackReady) {
        if (attackReady && distanceSquared > 7.29) {
            return 1;
        }
        if (!attackReady && distanceSquared < 12.25) {
            return -1;
        }
        return 0;
    }

    static boolean dropChaseAllowed(double playerY, double dropY, boolean hostileNearby) {
        return Double.isFinite(playerY) && Double.isFinite(dropY)
            && playerY - dropY <= 3.0 && !hostileNearby;
    }

    static final class Recovery {
        private boolean recovering;
        private boolean ateOwnedFood;

        boolean recovering() {
            return recovering;
        }

        boolean ateOwnedFood() {
            return ateOwnedFood;
        }

        void update(float health, boolean edibleOwnedFoodAvailable) {
            if (!Float.isFinite(health) || health <= 10.0f) {
                if (!recovering) {
                    recovering = true;
                    ateOwnedFood = false;
                }
            } else if (recovering && health >= 14.0f && (!edibleOwnedFoodAvailable || ateOwnedFood)) {
                recovering = false;
            }
        }

        void foodConsumed() {
            if (recovering) {
                ateOwnedFood = true;
            }
        }
    }
}
