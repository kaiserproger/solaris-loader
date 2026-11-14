package dev.krasnokamen.agent.javaagent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Adds the event bridge to the plain Java client's actual tick, without a mod loader. */
final class VanillaTickTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(Module module, ClassLoader loader, String name, Class<?> redefined,
                            ProtectionDomain domain, byte[] bytes) {
        if (!"net/minecraft/client/Minecraft".equals(name)) {
            return null;
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        boolean[] found = {false, false, false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String method, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, method, descriptor, signature, exceptions);
                if (method.equals("renderFrame") && descriptor.equals("(Z)V")) {
                    return new MethodVisitor(Opcodes.ASM9, delegate) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String called,
                                                    String desc, boolean isInterface) {
                            super.visitMethodInsn(opcode, owner, called, desc, isInterface);
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                    && owner.equals("net/minecraft/client/renderer/GameRenderer")) {
                                String hook = null;
                                if (called.equals("extract")
                                        && desc.equals("(Lnet/minecraft/client/DeltaTracker;Z)V")) {
                                    found[1] = true;
                                    hook = "afterExtract";
                                } else if (called.equals("render") && desc.equals("()V")) {
                                    found[2] = true;
                                    hook = "afterRender";
                                }
                                if (hook != null) {
                                    super.visitVarInsn(Opcodes.ALOAD, 0);
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "dev/krasnokamen/agent/javaagent/VanillaFrameCapture", hook,
                                        "(Lnet/minecraft/client/Minecraft;)V", false);
                                }
                            }
                        }
                    };
                }
                if (!method.equals("tick") || !descriptor.equals("()V") || (access & Opcodes.ACC_STATIC) != 0) {
                    return delegate;
                }
                found[0] = true;
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.RETURN) {
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                "dev/krasnokamen/agent/javaagent/VanillaTickEvents", "afterTick",
                                "(Lnet/minecraft/client/Minecraft;)V", false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        }, 0);
        if (!found[0] || !found[1] || !found[2]) {
            throw new IllegalStateException("plain client is missing a tick or frame capture hook");
        }
        return writer.toByteArray();
    }
}
