package dev.solaris.agent.javaagent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Check JVM descriptors, including changed return types that source compilation can miss. */
final class NativeMemberCompatibilityTest {
    @Test
    void everyDirectNativeMemberExistsInTheActualRuntimeJar() throws Exception {
        Path classes = Path.of(MinecraftClientFacade.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Map<String, Api> apis = new HashMap<>();
        Set<String> missing = new TreeSet<>();
        try (var files = Files.walk(classes.resolve("dev/solaris/agent/javaagent"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                new ClassReader(Files.readAllBytes(file)).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            private void check(boolean field, String owner, String member, String type) {
                                if (!owner.startsWith("net/minecraft/") && !owner.startsWith("com/mojang/blaze3d/")) return;
                                try {
                                    if (!exists(apis, owner, field, member, type, new HashSet<>())) {
                                        missing.add(owner + "." + member + type + " <- " + file.getFileName());
                                    }
                                } catch (IOException failure) {
                                    throw new IllegalStateException("reading native API " + owner, failure);
                                }
                            }
                            @Override
                            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                                check(true, owner, name, descriptor);
                            }
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                                                        boolean isInterface) {
                                check(false, owner, name, descriptor);
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        assertTrue(missing.isEmpty(), () -> "Native members absent from runtime client:\n" + String.join("\n", missing));
    }

    private static boolean exists(Map<String, Api> apis, String owner, boolean field, String name,
                                  String descriptor, Set<String> visited) throws IOException {
        if (owner == null || !visited.add(owner)) return false;
        Api api = apis.get(owner);
        if (api == null) {
            api = readApi(owner);
            apis.put(owner, api);
        }
        if ((field ? api.fields : api.methods).contains(name + descriptor)) return true;
        if (name.equals("<init>")) return false;
        if (exists(apis, api.parent, field, name, descriptor, visited)) return true;
        for (String parent : api.interfaces) {
            if (exists(apis, parent, field, name, descriptor, visited)) return true;
        }
        return false;
    }

    private static Api readApi(String owner) throws IOException {
        Api snapshot = new Api();
        try (InputStream input = MinecraftClientFacade.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            if (input == null) return snapshot;
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature,
                                  String parent, String[] interfaces) {
                    snapshot.parent = parent;
                    snapshot.interfaces = interfaces;
                }
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    snapshot.fields.add(name + descriptor);
                    return null;
                }
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    snapshot.methods.add(name + descriptor);
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return snapshot;
    }

    private static final class Api {
        String parent;
        String[] interfaces = new String[0];
        final Set<String> fields = new HashSet<>();
        final Set<String> methods = new HashSet<>();
    }
}
