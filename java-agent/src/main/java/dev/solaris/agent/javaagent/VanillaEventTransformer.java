package dev.solaris.agent.javaagent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Native lifecycle and packet events for premain clients without a mod loader. */
final class VanillaEventTransformer implements ClassFileTransformer {
    private static final String EVENTS = "dev/solaris/agent/javaagent/ClientStateEvents";
    private static final String HOOKS = "dev/solaris/agent/javaagent/VanillaPacketHooks";
    private static final Set<String> PACKET_HANDLERS = Set.of(
        "handleSetEntityData", "handleSetEquipment", "handleBlockChangedAck",
        "handleTakeItemEntity", "handleSetTime", "handleSetHealth", "handleSoundEntityEvent",
        "handleOpenScreen", "handleContainerSetSlot", "handleContainerContent",
        "handleContainerSetData", "handleContainerClose", "handleSystemChat"
    );

    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                            ProtectionDomain domain, byte[] bytes) {
        if (!"net/minecraft/network/PacketProcessor$ListenerAndPacket".equals(name)
            && !"net/minecraft/client/Minecraft".equals(name)
            && !"net/minecraft/client/gui/Gui".equals(name)
            && !"net/minecraft/client/multiplayer/ClientPacketListener".equals(name)) {
            return null;
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String method, String desc,
                                             String signature, String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, method, desc, signature, exceptions);
                boolean packet = name.endsWith("/ClientPacketListener") && PACKET_HANDLERS.contains(method)
                    && desc.startsWith("(Lnet/minecraft/network/protocol/game/") && desc.endsWith(")V");
                String event = null;
                if (name.endsWith("/PacketProcessor$ListenerAndPacket") && method.equals("handle") && desc.equals("()V")) {
                    event = "publishState";
                } else if (name.equals("net/minecraft/client/Minecraft") || name.equals("net/minecraft/client/gui/Gui")) {
                    if (name.equals("net/minecraft/client/Minecraft") && method.equals("tick") && desc.equals("()V")) {
                        event = "publishTick";
                    }
                    if ((method.equals("setLevel") || method.equals("clearClientLevel")
                        || method.equals("setScreen") || method.equals("setOverlay"))
                        && desc.endsWith(")V")) event = "publishState";
                }
                if (!packet && event == null) return visitor;
                final String eventName = event;
                return new MethodVisitor(Opcodes.ASM9, visitor) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        if (packet && method.equals("handleTakeItemEntity")) {
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "beforePacket", "(Ljava/lang/Object;)V", false);
                        }
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            if (packet) {
                                super.visitVarInsn(Opcodes.ALOAD, 1);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, "afterPacket", "(Ljava/lang/Object;)V", false);
                            }
                            if (eventName != null) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, EVENTS, eventName, "()V", false);
                            }
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }
}
