package dev.solaris.agent.javaagent;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

final class VanillaEventTransformerTest {
    private Map<String, Integer> hooks(String name) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, name);
            byte[] output = new VanillaEventTransformer().transform(null, name, null, null, input.readAllBytes());
            assertNotNull(output, name);
            Map<String, Integer> calls = new HashMap<>();
            new ClassReader(output).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String method, String descriptor,
                                                 String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String target,
                                                    String descriptor, boolean isInterface) {
                            if (owner.endsWith("/ClientStateEvents") || owner.endsWith("/VanillaPacketHooks")) {
                                calls.merge(method + ":" + target, 1, Integer::sum);
                            }
                        }
                    };
                }
            }, 0);
            return calls;
        }
    }

    @Test
    void instrumentsRealClientTickLifecycleAndPacketCompletion() throws Exception {
        Map<String, Integer> minecraft = hooks("net/minecraft/client/Minecraft");
        Map<String, Integer> gui = hooks("net/minecraft/client/gui/Gui");
        assertTrue(minecraft.getOrDefault("setScreen:publishState", 0) + gui.getOrDefault("setScreen:publishState", 0) > 0);
        assertTrue(minecraft.getOrDefault("setOverlay:publishState", 0) + gui.getOrDefault("setOverlay:publishState", 0) > 0);
        assertTrue(minecraft.getOrDefault("tick:publishTick", 0) > 0);
        assertEquals(0, gui.getOrDefault("tick:publishTick", 0));
        assertTrue(minecraft.getOrDefault("setLevel:publishState", 0) > 0);
        assertTrue(minecraft.getOrDefault("clearClientLevel:publishState", 0) > 0);
        assertTrue(hooks("net/minecraft/network/PacketProcessor$ListenerAndPacket")
            .getOrDefault("handle:publishState", 0) > 0);
        Map<String, Integer> packets = hooks("net/minecraft/client/multiplayer/ClientPacketListener");
        assertTrue(packets.getOrDefault("handleTakeItemEntity:beforePacket", 0) > 0);
        assertTrue(packets.getOrDefault("handleTakeItemEntity:afterPacket", 0) > 0);
        assertTrue(packets.getOrDefault("handleContainerContent:afterPacket", 0) > 0);
        assertTrue(packets.getOrDefault("handleBlockChangedAck:afterPacket", 0) > 0);
    }

    @Test
    void frameAwareTickTransformerOwnsTickPublicationWhenEnabled() throws Exception {
        String previous = System.getProperty("solaris.client.vanillaEvents");
        try {
            System.setProperty("solaris.client.vanillaEvents", "true");
            assertEquals(0, hooks("net/minecraft/client/Minecraft").getOrDefault("tick:publishTick", 0));
            assertTrue(hooks("net/minecraft/client/multiplayer/ClientPacketListener")
                .getOrDefault("handleContainerContent:afterPacket", 0) > 0);
        } finally {
            if (previous == null) System.clearProperty("solaris.client.vanillaEvents");
            else System.setProperty("solaris.client.vanillaEvents", previous);
        }
    }

    @Test
    void ignoresUnrelatedClasses() {
        assertNull(new VanillaEventTransformer().transform(null, "example/Other", null, null, new byte[0]));
    }
}
