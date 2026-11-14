package dev.krasnokamen.agent.javaagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class VanillaTickTransformerTest {
    private static byte[] minecraft(boolean tick) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, "net/minecraft/client/Minecraft", null,
            "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, tick ? "tick" : "other", "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        method = writer.visitMethod(Opcodes.ACC_PUBLIC, "renderFrame", "(Z)V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/renderer/GameRenderer",
            "extract", "(Lnet/minecraft/client/DeltaTracker;Z)V", false);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/renderer/GameRenderer",
            "render", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(3, 2);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Test
    void instrumentsOnlyMinecraftTickAndRejectsMissingHook() {
        VanillaTickTransformer transformer = new VanillaTickTransformer();
        byte[] original = minecraft(true);
        assertNull(transformer.transform(null, null, "other/Class", null, null, original));
        assertThrows(IllegalStateException.class, () -> transformer.transform(null, null,
            "net/minecraft/client/Minecraft", null, null, minecraft(false)));
        byte[] transformed = transformer.transform(null, null, "net/minecraft/client/Minecraft", null, null, original);
        int[] calls = {0, 0, 0};
        new ClassReader(transformed).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean isInterface) {
                        if (name.equals("tick") && opcode == Opcodes.INVOKESTATIC
                                && owner.equals("dev/krasnokamen/agent/javaagent/VanillaTickEvents")
                                && method.equals("afterTick")) {
                            calls[0]++;
                        }
                        if (name.equals("renderFrame") && opcode == Opcodes.INVOKESTATIC
                                && owner.equals("dev/krasnokamen/agent/javaagent/VanillaFrameCapture")) {
                            if (method.equals("afterExtract")) calls[1]++;
                            if (method.equals("afterRender")) calls[2]++;
                        }
                    }
                };
            }
        }, 0);
        assertEquals(1, calls[0]);
        assertEquals(1, calls[1]);
        assertEquals(1, calls[2]);
    }

    @Test
    void timedOutCaptureReleasesItsPendingSlot() {
        var path = java.nio.file.Path.of("screenshots", "capture.png");
        var first = VanillaFrameCapture.request(path.getParent(), path);
        assertThrows(IllegalStateException.class, () -> VanillaFrameCapture.request(path.getParent(), path));
        first.cancel(false);
        var next = VanillaFrameCapture.request(path.getParent(), path);
        next.cancel(false);
    }
}
