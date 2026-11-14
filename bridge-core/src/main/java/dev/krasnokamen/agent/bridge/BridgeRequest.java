package dev.krasnokamen.agent.bridge;

import com.google.gson.JsonObject;

public record BridgeRequest(long id, String secret, String command, JsonObject payload) {
}
