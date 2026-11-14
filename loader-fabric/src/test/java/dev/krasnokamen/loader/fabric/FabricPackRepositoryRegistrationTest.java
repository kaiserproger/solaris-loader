package dev.krasnokamen.loader.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.minecraft.server.packs.repository.RepositorySource;
import org.junit.jupiter.api.Test;

final class FabricPackRepositoryRegistrationTest {
    @Test
    void immutableVanillaSourcesAreCopiedBeforeRuntimeRegistration() {
        RepositorySource vanilla = output -> {};
        RepositorySource runtime = output -> {};

        Set<RepositorySource> sources =
                KrasnokamenFabricLoader.mutablePackSources(Set.of(vanilla), runtime);

        assertEquals(Set.of(vanilla, runtime), sources);
        assertTrue(sources.add(output -> {}));
    }
}
