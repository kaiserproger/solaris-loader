package dev.krasnokamen.agent.bridge;

import com.google.gson.JsonObject;

@FunctionalInterface
public interface BridgeCommand {
    JsonObject execute(BridgeRequest request) throws Exception;
}
