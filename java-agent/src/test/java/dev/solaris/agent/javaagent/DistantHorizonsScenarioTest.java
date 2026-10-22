package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DistantHorizonsScenarioTest {
    @TempDir Path directory;

    @Test
    void routesDhScenarioAndFailsClosedWithoutTheActualMod() {
        ClientScenarioReport report = new MinecraftClientFacade().runScenario(
            "dh-protocol16", directory
        );
        assertEquals("blocked", report.result());
        assertTrue(report.observations().stream().anyMatch(
            observation -> observation.contains("Distant Horizons is not loaded")
        ), () -> "DH must have its own scenario, not the unknown-ID fallback: " + report);
    }

    @Test
    void allDriverPhasesUseTheSameFailClosedJavaEntrypoint() {
        for (String phase : new String[] {"configure", "debug", "control", "landmark", "edit", "render", "render-update", "state", "disconnect"}) {
            String id = "dh-protocol16-" + phase;
            ClientScenarioReport report = new MinecraftClientFacade().runScenario(id, directory);
            assertEquals("blocked", report.result(), id);
            assertTrue(report.observations().contains("Distant Horizons is not loaded"), id);
        }
    }
    @Test
    void readsWithTheRequiredFreshCacheAndReleasesIt() throws Exception {
        Object wrapper = new Object();
        TerrainRepo repo = new TerrainRepo(wrapper);
        assertSame(repo.answer, DistantHorizonsScenario.readLandmarkColumn(repo, wrapper));
        assertTrue(repo.cache.closed);
    }

    public static final class TerrainRepo {
        final Object wrapper;
        final Object answer = new Object();
        final TerrainCache cache = new TerrainCache();

        TerrainRepo(Object wrapper) {
            this.wrapper = wrapper;
        }

        public TerrainCache createSoftCache() {
            return cache;
        }

        public Object getColumnDataAtBlockPos(Object level, int x, int z, TerrainCache supplied) {
            assertSame(wrapper, level);
            assertEquals(DistantHorizonsScenario.LANDMARK_X, x);
            assertEquals(DistantHorizonsScenario.LANDMARK_Z, z);
            assertSame(cache, supplied, "DH rejects a null terrain-data cache");
            return answer;
        }
    }

    public static final class TerrainCache implements AutoCloseable {
        boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }
    @Test
    void emptyUploadedContainersAreNotRenderedLandmarkGeometry() throws Exception {
        assertFalse(DistantHorizonsScenario.hasUploadedGeometry(new RenderBuffer(true, 0)));
        assertFalse(DistantHorizonsScenario.hasUploadedGeometry(new RenderBuffer(false, 1)));
        assertTrue(DistantHorizonsScenario.hasUploadedGeometry(new RenderBuffer(true, 1)));
    }

    public static final class RenderBuffer {
        public final boolean buffersUploaded;
        private final int count;

        RenderBuffer(boolean uploaded, int count) {
            this.buffersUploaded = uploaded;
            this.count = count;
        }

        public boolean hasNonNullVbos() { return true; }
        public int vboBufferCount() { return count; }
    }

    @Test
    void editedRenderMustReplaceTheWarmReconnectBuffer() {
        var state = com.google.gson.JsonParser.parseString("""
            {"landmark_uploaded":true,"visible_uploaded_landmark_buffers":[
                {"section":"8*4,0","buffer_identity":17,"vbo_count":2}]}
            """).getAsJsonObject();
        var previous = state.getAsJsonArray("visible_uploaded_landmark_buffers").deepCopy();
        assertFalse(DistantHorizonsScenario.hasFreshGeometry(state, previous));
        state.getAsJsonArray("visible_uploaded_landmark_buffers")
            .get(0).getAsJsonObject().addProperty("buffer_identity", 18);
        assertTrue(DistantHorizonsScenario.hasFreshGeometry(state, previous));
        state.addProperty("landmark_uploaded", false);
        assertFalse(DistantHorizonsScenario.hasFreshGeometry(state, previous));
    }
}
