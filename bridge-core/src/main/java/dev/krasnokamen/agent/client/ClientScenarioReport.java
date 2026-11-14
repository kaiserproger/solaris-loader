package dev.krasnokamen.agent.client;

import java.util.List;

public record ClientScenarioReport(String result, String id, List<String> observations) {
}
