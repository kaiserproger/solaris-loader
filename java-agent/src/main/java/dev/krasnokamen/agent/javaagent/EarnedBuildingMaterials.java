package dev.krasnokamen.agent.javaagent;

import dev.krasnokamen.agent.client.ClientScenarioReport;

record EarnedBuildingMaterials(
    ClientScenarioReport report,
    String planksItemId,
    ScenarioBlockTarget tableTarget
) {
}
