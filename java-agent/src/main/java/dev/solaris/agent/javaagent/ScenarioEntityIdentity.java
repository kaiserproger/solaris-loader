package dev.solaris.agent.javaagent;

import java.util.Objects;
import java.util.UUID;

record ScenarioEntityIdentity(int entityId, UUID entityUuid, String entityType) {
    ScenarioEntityIdentity {
        Objects.requireNonNull(entityUuid, "entity uuid");
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("entity type must not be blank");
        }
    }

    boolean matches(int observedEntityId, UUID observedUuid, String observedEntityTypeId) {
        return entityId == observedEntityId
            && entityUuid.equals(observedUuid)
            && entityType.equals(observedEntityTypeId);
    }
}
