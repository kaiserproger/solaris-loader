package dev.solaris.agent.javaagent;

import dev.solaris.agent.client.ClientScenarioReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static dev.solaris.agent.javaagent.PlayableCoordinates.coordinates;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.BLOCK_TIMEOUT;
import static dev.solaris.agent.javaagent.PlayableRealClientLoopScenario.TWENTY_MINUTE_ID;

/** Save/restart marker write/read plus the before/after rejoin assertions. */
final class PlayablePersistenceStage {
    private PlayablePersistenceStage() {
    }

    private static final String SAVE_RESTART_MARKER_FILE = "playable-03-save-restart-marker.properties";

    static Path saveRestartMarkerPath(Path screenshotsDir) {
        Path runDir = screenshotsDir.getParent();
        return (runDir == null ? Path.of(".") : runDir).resolve(SAVE_RESTART_MARKER_FILE);
    }

    static void writeMarker(Path path, ScenarioBlockTarget target) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(
            path,
            "x=" + target.x() + "\n"
                + "y=" + target.y() + "\n"
                + "z=" + target.z() + "\n"
                + "face=" + target.face() + "\n"
        );
    }

    static ScenarioBlockTarget readMarker(Path path, String label) throws IOException {
        return readMarker(path, label, "minecraft:crafting_table");
    }

    static ScenarioBlockTarget readMarker(Path path, String label, String blockId) throws IOException {
        Integer x = null;
        Integer y = null;
        Integer z = null;
        String face = null;
        for (String line : Files.readAllLines(path)) {
            String[] parts = line.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            switch (parts[0]) {
                case "x" -> x = Integer.parseInt(parts[1]);
                case "y" -> y = Integer.parseInt(parts[1]);
                case "z" -> z = Integer.parseInt(parts[1]);
                case "face" -> face = parts[1];
                default -> {
                }
            }
        }
        if (x == null || y == null || z == null || face == null) {
            throw new IOException("invalid restart marker file: " + path);
        }
        return new ScenarioBlockTarget(x, y, z, face, label, blockId);
    }

    static ClientScenarioReport runSaveRestartBefore(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        PlayableResourceStage.LogToPlanksResult planks = PlayableResourceStage.runLogToPlanks(id, observations, client, 3, true, false);
        if (!"passed".equals(planks.report().result())) {
            return planks.report();
        }
        PlayableResourceStage.CraftingTableOpenResult table = PlayableResourceStage.craftPlaceAndOpenTable(
            id,
            observations,
            client,
            planks.planks().planksItemId(),
            false
        );
        if (!"passed".equals(table.report().result())) {
            return table.report();
        }
        boolean prepareForLongSurvival = TWENTY_MINUTE_ID.equals(id);
        ClientScenarioReport tool = PlayableResourceStage.craftWoodenPickaxeInOpenTable(
            id,
            observations,
            client,
            planks.planks().planksItemId(),
            !prepareForLongSurvival
        );
        if (!"passed".equals(tool.result())) {
            return tool;
        }
        if (prepareForLongSurvival) {
            ClientScenarioReport sword = PlayableResourceStage.craftWoodenSwordInOpenTable(
                id,
                observations,
                client,
                planks.planks().planksItemId()
            );
            if (!"passed".equals(sword.result())) {
                return sword;
            }
        }

        writeMarker(saveRestartMarkerPath(screenshotsDir), table.tableTarget());
        observations.add("restart marker placement: passed target=" + coordinates(table.tableTarget()));
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport("passed", id, observations);
    }

    static ClientScenarioReport runSaveRestartAfter(
        String id,
        List<String> observations,
        Path screenshotsDir,
        ScenarioClient client
    ) throws Exception {
        Path markerPath = saveRestartMarkerPath(screenshotsDir);
        if (!Files.isRegularFile(markerPath)) {
            observations.add("missing restart marker file: " + markerPath);
            return new ClientScenarioReport("failed", id, observations);
        }

        ScenarioBlockTarget marker = readMarker(markerPath, "restart-marker");
        boolean markerPersisted = client.waitForBlock(marker, "minecraft:crafting_table", BLOCK_TIMEOUT);
        int woodenPickaxeCount = client.inventoryCount("minecraft:wooden_pickaxe");
        boolean inventoryPersisted = woodenPickaxeCount >= 1;
        observations.add(
            "restart marker persistence: " + (markerPersisted ? "passed" : "failed")
                + " target=" + coordinates(marker)
        );
        observations.add(
            "inventory persistence: " + (inventoryPersisted ? "passed" : "failed")
                + " wooden_pickaxe_count=" + woodenPickaxeCount
        );
        observations.add("screenshots directory available to driver: " + screenshotsDir);
        return new ClientScenarioReport(
            markerPersisted && inventoryPersisted ? "passed" : "failed",
            id,
            observations
        );
    }
}
