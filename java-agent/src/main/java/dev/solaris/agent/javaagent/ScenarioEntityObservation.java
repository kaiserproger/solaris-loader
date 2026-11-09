package dev.solaris.agent.javaagent;

import java.util.Objects;
import java.util.UUID;

record ScenarioEntityObservation(
    String entityType,
    int entityId,
    UUID entityUuid,
    double x,
    double y,
    double z,
    double distanceSquared,
    String sheepWoolItemId
) {
    ScenarioEntityObservation {
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("entity type must not be blank");
        }
        Objects.requireNonNull(entityUuid, "entity uuid");
    }

    ScenarioEntityIdentity identity() {
        return new ScenarioEntityIdentity(entityId, entityUuid, entityType);
    }
}
