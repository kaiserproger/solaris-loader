package dev.krasnokamen.agent.javaagent;

record ScenarioShieldBlockResult(
    boolean useStarted,
    boolean blockedAttackObserved,
    float healthBefore,
    float healthAfter,
    int shieldDamageBefore,
    int shieldDamageAfter
) {
}
