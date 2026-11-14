package dev.krasnokamen.agent.javaagent;

final class AttackCadence {
    private AttackCadence() {
    }

    static boolean ready(float attackStrength) {
        return attackStrength >= 1.0F;
    }
}
