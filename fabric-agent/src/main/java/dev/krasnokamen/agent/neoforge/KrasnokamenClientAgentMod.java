package dev.krasnokamen.agent.neoforge;

import dev.krasnokamen.agent.javaagent.KrasnokamenClientAgent;
import dev.krasnokamen.agent.javaagent.ClientStateEvents;
import dev.krasnokamen.agent.javaagent.MinecraftScenarioClient;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod("krasnokamen_client_agent")
public final class KrasnokamenClientAgentMod {
    private static boolean started;

    public KrasnokamenClientAgentMod() {
        NeoForge.EVENT_BUS.addListener(KrasnokamenClientAgentMod::onClientTickPre);
        NeoForge.EVENT_BUS.addListener(KrasnokamenClientAgentMod::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(KrasnokamenClientAgentMod::onClientLogin);
        NeoForge.EVENT_BUS.addListener(KrasnokamenClientAgentMod::onClientLogout);
        NeoForge.EVENT_BUS.addListener(KrasnokamenClientAgentMod::onClientClone);
    }

    private static void onClientTickPre(ClientTickEvent.Pre event) {
        MinecraftScenarioClient.runPreTickActions();
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        ClientStateEvents.publishTick();
        if (!started && Minecraft.getInstance() != null) {
            started = KrasnokamenClientAgent.startFromRuntime();
        }
    }

    private static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientStateEvents.publishState();
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientStateEvents.publishState();
    }

    private static void onClientClone(ClientPlayerNetworkEvent.Clone event) {
        ClientStateEvents.publishState();
    }
}
