package io.github.opencubicchunks.cubicchunks.mixin.transform;

import static org.objectweb.asm.Type.getObjectType;
import static org.objectweb.asm.commons.Method.getMethod;

import com.google.common.collect.Sets;
import net.minecraftforge.coremod.api.ASMAPI;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MainTransformer {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final boolean IS_DEV = !FMLEnvironment.production;

    public static void transformChunkHolder(ClassNode targetClass) {

        Map<Method, String> vanillaToCubic = new HashMap<>();
        vanillaToCubic.put(getMethod("void <init>(net.minecraft.util.math.ChunkPos, "
                + "int, net.minecraft.world.lighting.WorldLightManager, "
                + "net.minecraft.world.server.ChunkHolder$IListener, "
                + "net.minecraft.world.server.ChunkHolder$IPlayerProvider)"),
                "<init>");
        vanillaToCubic.put(getMethod("void func_219291_a(net.minecraft.world.server.ChunkManager)"),
                "updateCubeFutures");

        Map<ClassMethod, String> methods = new HashMap<>();
        methods.put(new ClassMethod(
                getObjectType("net/minecraft/world/server/ChunkHolder"),
                getMethod("net.minecraft.world.chunk.ChunkStatus getStatus(int)")
        ), "getCubeStatus");
        methods.put(new ClassMethod(
                getObjectType("net/minecraft/world/server/ChunkManager"),
                getMethod("java.util.concurrent.CompletableFuture func_222961_b(net.minecraft.world.server.ChunkHolder)")
        ), "unpackCubeTicks");
        methods.put(new ClassMethod(
                getObjectType("net/minecraft/world/server/ChunkManager"),
                getMethod("java.util.concurrent.CompletableFuture func_219179_a(net.minecraft.world.server.ChunkHolder)")
        ), "postProcessCube");
        methods.put(new ClassMethod(
                getObjectType("net/minecraft/world/server/ChunkManager"),
                getMethod("java.util.concurrent.CompletableFuture func_219188_b(net.minecraft.util.math.ChunkPos)")
        ), "getCubeEntityTickingRangeFuture");
        methods.put(new ClassMethod(
                getObjectType("net/minecraft/world/server/ChunkManager"),
                getMethod("java.util.concurrent.CompletableFuture func_222973_a(net.minecraft.world.chunk.Chunk)")
        ), "packCubeTicks");
        methods.put(new ClassMethod(
                getObjectType("net/minecraft/world/server/ChunkHolder$IListener"),
                getMethod("void func_219066_a(net.minecraft.util.math.ChunkPos, java.util.function.IntSupplier, int, java.util.function.IntConsumer)")
        ), "onCubeLevelChange");

        Map<ClassField, String> fields = new HashMap<>();
        fields.put(new ClassField("net/minecraft/world/server/ChunkManager", "field_219249_a"), "MAX_CUBE_DISTANCE"); //MAX_CHUNK_DISTANCE
        fields.put(new ClassField("net/minecraft/world/server/ChunkHolder", "field_219319_n"), "cubePos"); // pos

        Map<Type, Type> types = new HashMap<>();
        types.put(getObjectType("net/minecraft/util/math/ChunkPos"),
                getObjectType("io/github/opencubicchunks/cubicchunks/chunk/util/CubePos"));
        types.put(getObjectType("net/minecraft/world/chunk/Chunk"),
                getObjectType("io/github/opencubicchunks/cubicchunks/chunk/cube/BigCube"));
        types.put(getObjectType("net/minecraft/world/server/ChunkHolder$1"),
                getObjectType("io/github/opencubicchunks/cubicchunks/chunk/ICubeHolder$CubeLoadingError"));

        vanillaToCubic.forEach((old, newName) -> cloneAndApplyRedirects(targetClass, old, newName, methods, fields, types));
    }

    public static void transformChunkManager(ClassNode targetClass) {
        final Method targetMethod = getMethod(
                "net.minecraft.world.server.ChunkHolder func_219213_a(long, int, net.minecraft.world.server.ChunkHolder, int)");
        final String newTargetName = "updateCubeScheduling";

        Map<ClassMethod, String> methodRedirects = new HashMap<>();

        Map<ClassField, String> fieldRedirects = new HashMap<>();
        fieldRedirects.put(new ClassField("net/minecraft/world/server/ChunkManager", "field_219261_o"), // unloadableChunks
                "cubesToDrop");
        fieldRedirects.put(new ClassField("net/minecraft/world/server/ChunkManager", "field_219253_g"), // chunksToUnload
                "pendingCubeUnloads");
        fieldRedirects.put(new ClassField("net/minecraft/world/server/ChunkManager", "field_219263_q"), // chunkTaskPriorityQueueSorter
                "cubeQueueSorter");
        fieldRedirects.put(new ClassField("net/minecraft/world/server/ChunkManager", "field_219251_e"), // loadedChunks
                "updatingCubeMap");
        fieldRedirects.put(new ClassField("net/minecraft/world/server/ChunkManager", "field_219249_a"),
                "MAX_CUBE_DISTANCE"); //MAX_LOADED_LEVEL


        Map<Type, Type> typeRedirects = new HashMap<>();
        // TODO: create target constructor in ChunkHolder with CubePos
        typeRedirects.put(getObjectType("net/minecraft/util/math/ChunkPos"),
                getObjectType("io/github/opencubicchunks/cubicchunks/chunk/util/CubePos"));
        // TODO: generate that class at runtime? transform and duplicate?
        typeRedirects.put(getObjectType("net/minecraft/world/chunk/ChunkTaskPriorityQueueSorter"),
                getObjectType("io/github/opencubicchunks/cubicchunks/chunk/ticket/CubeTaskPriorityQueueSorter"));

        MethodNode newMethod = cloneAndApplyRedirects(targetClass, targetMethod, newTargetName, methodRedirects, fieldRedirects, typeRedirects);
        makeStaticSyntheticAccessor(targetClass, newMethod);
    }

    public static void transformProxyTicketManager(ClassNode targetClass) {
        final Method setChunkLevel = getMethod(
                "net.minecraft.world.server.ChunkHolder func_219372_a(long, int, net.minecraft.world.server.ChunkHolder, int)");
        final String updateCubeScheduling = "updateCubeScheduling";

        Map<ClassMethod, String> methodRedirects = new HashMap<>();
        methodRedirects.put(// synthetic accessor for func_219213_a (updateChunkScheduling)
                new ClassMethod(
                        getObjectType("net/minecraft/world/server/ChunkManager"),
                        getMethod("net.minecraft.world.server.ChunkHolder access$700("
                                + "net.minecraft.world.server.ChunkManager, long, int, net.minecraft.world.server.ChunkHolder, int)")
                ), updateCubeScheduling);

        Map<ClassField, String> fieldRedirects = new HashMap<>();
        Map<Type, Type> typeRedirects = new HashMap<>();

        cloneAndApplyRedirects(targetClass, setChunkLevel, updateCubeScheduling, methodRedirects, fieldRedirects, typeRedirects);
    }

    private static void makeStaticSyntheticAccessor(ClassNode node, MethodNode newMethod) {
        Type[] params = Type.getArgumentTypes(newMethod.desc);
        Type[] newParams = new Type[params.length + 1];
        System.arraycopy(params, 0, newParams, 1, params.length);
        newParams[0] = getObjectType(node.name);

        Type returnType = Type.getReturnType(newMethod.desc);
        MethodNode newNode = new MethodNode(newMethod.access | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, newMethod.name,
                Type.getMethodDescriptor(returnType, newParams), null, null);

        int j = 0;
        for (Type param : newParams) {
            newNode.instructions.add(new VarInsnNode(param.getOpcode(Opcodes.ILOAD), j));
            j += param.getSize();
        }
        newNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, node.name, newMethod.name, newMethod.desc, false));
        newNode.instructions.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));
        node.methods.add(newNode);
    }

    private static MethodNode cloneAndApplyRedirects(ClassNode node, Method existingMethodIn, String newName,
            Map<ClassMethod, String> methodRedirectsIn, Map<ClassField, String> fieldRedirectsIn, Map<Type, Type> typeRedirectsIn) {
        LOGGER.debug("Transforming " + node.name + ": Cloning method " + existingMethodIn.getName() + " " + existingMethodIn.getDescriptor() + " "
                + "into " + newName + " and applying remapping");
        Method existingMethod = new Method(ASMAPI.mapMethod(existingMethodIn.getName()), existingMethodIn.getDescriptor());

        MethodNode m = node.methods.stream()
                        .filter(x -> existingMethod.getName().equals(x.name) && existingMethod.getDescriptor().equals(x.desc))
                        .findAny().orElseThrow(() -> new IllegalStateException("Target method " + existingMethod + " not found"));

        Map<Handle, String> redirectedLambdas = cloneAndApplyLambdaRedirects(node, m, methodRedirectsIn, fieldRedirectsIn, typeRedirectsIn);

        Set<String> defaultKnownClasses = Sets.newHashSet(
                Type.getType(Object.class).getInternalName(),
                Type.getType(String.class).getInternalName(),
                node.name
        );

        Map<String, String> methodRedirects = new HashMap<>();
        for (ClassMethod classMethod : methodRedirectsIn.keySet()) {
            methodRedirects.put(
                    classMethod.owner.getInternalName() + "." + ASMAPI.mapMethod(classMethod.method.getName()) + classMethod.method.getDescriptor(),
                    methodRedirectsIn.get(classMethod)
            );
        }
        for (Handle handle : redirectedLambdas.keySet()) {
            methodRedirects.put(
                    handle.getOwner() + "." + handle.getName() + handle.getDesc(),
                    redirectedLambdas.get(handle)
            );
        }

        Map<String, String> fieldRedirects = new HashMap<>();
        for (ClassField classField : fieldRedirectsIn.keySet()) {
            fieldRedirects.put(
                    classField.owner.getInternalName() + "." + ASMAPI.mapField(classField.name),
                    fieldRedirectsIn.get(classField)
            );
        }

        Map<String, String> typeRedirects = new HashMap<>();
        for (Type type : typeRedirectsIn.keySet()) {
            typeRedirects.put(type.getInternalName(), typeRedirectsIn.get(type).getInternalName());
        }

        methodRedirects.forEach((old, n) -> LOGGER.debug("Method mapping: " + old + " -> " + n));
        fieldRedirects.forEach((old, n) -> LOGGER.debug("Field mapping: " + old + " -> " + n));
        typeRedirects.forEach((old, n) -> LOGGER.debug("Type mapping: " + old + " -> " + n));

        Remapper remapper = new Remapper() {
            @Override
            public String mapMethodName(final String owner, final String name, final String descriptor) {
                if (name.equals("<init>")) {
                    return name;
                }
                String key = owner + '.' + name + descriptor;
                String mappedName = methodRedirects.get(key);
                if (mappedName == null) {
                    if (IS_DEV) {
                        LOGGER.warn("NOTE: handling METHOD redirect to self: " + key);
                    }
                    methodRedirects.put(key, name);
                    return name;
                }
                return mappedName;
            }

            @Override
            public String mapInvokeDynamicMethodName(final String name, final String descriptor) {
                if (IS_DEV) {
                    LOGGER.warn("NOTE: remapping invokedynamic to self: " + name + "." + descriptor);
                }
                return name;
            }

            @Override
            public String mapFieldName(final String owner, final String name, final String descriptor) {
                String key = owner + '.' + name;
                String mapped = fieldRedirects.get(key);
                if (mapped == null) {
                    if (IS_DEV) {
                        LOGGER.warn("NOTE: handling FIELD redirect to self: " + key);
                    }
                    fieldRedirects.put(key, name);
                    return name;
                }
                return mapped;
            }

            @Override
            public String map(final String key) {
                String mapped = typeRedirects.get(key);
                if (mapped == null && defaultKnownClasses.contains(key)) {
                    mapped = key;
                }
                if (mapped == null) {
                    if (IS_DEV) {
                        LOGGER.warn("NOTE: handling CLASS redirect to self: " + key);
                    }
                    typeRedirects.put(key, key);
                    return key;
                }
                return mapped;
            }
        };
        String desc = m.desc;
        Type[] params = Type.getArgumentTypes(desc);
        Type ret = Type.getReturnType(desc);
        for (int i = 0; i < params.length; i++) {
            if (params[i].getSort() == Type.OBJECT) {
                params[i] = getObjectType(remapper.map(params[i].getInternalName()));
            }
        }
        if (ret.getSort() == Type.OBJECT) {
            ret = getObjectType(remapper.map(ret.getInternalName()));
        }
        String mappedDesc = Type.getMethodDescriptor(ret, params);

        MethodNode output = new MethodNode(m.access, newName, mappedDesc, null, m.exceptions.toArray(new String[0]));
        MethodVisitor mv = new MethodVisitor(Opcodes.ASM7, output) {
            @Override public void visitLineNumber(int line, Label start) {
                super.visitLineNumber(line + 10000, start);
            }
        };
        MethodRemapper methodRemapper = new MethodRemapper(mv, remapper);

        m.accept(methodRemapper);
        output.name = newName;
        // remove protected and private, add public
        output.access &= ~(Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
        output.access |= Opcodes.ACC_PUBLIC;
        node.methods.add(output);

        return output;
    }

    private static Map<Handle, String> cloneAndApplyLambdaRedirects(ClassNode node, MethodNode method, Map<ClassMethod, String> methodRedirectsIn,
            Map<ClassField, String> fieldRedirectsIn, Map<Type, Type> typeRedirectsIn) {

        Map<Handle, String> lambdaRedirects = new HashMap<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == Opcodes.INVOKEDYNAMIC) {
                InvokeDynamicInsnNode invoke = (InvokeDynamicInsnNode) instruction;
                String bootstrapMethodName = invoke.bsm.getName();
                String bootstrapMethodOwner = invoke.bsm.getOwner();
                if (bootstrapMethodName.equals("metafactory") && bootstrapMethodOwner.equals("java/lang/invoke/LambdaMetafactory")) {
                    for (Object bsmArg : invoke.bsmArgs) {
                        if (bsmArg instanceof Handle) {
                            Handle handle = (Handle) bsmArg;
                            String owner = handle.getOwner();
                            if (owner.equals(node.name)) {
                                String newName = "cc$redirect$" + handle.getName();
                                lambdaRedirects.put(handle, newName);
                                cloneAndApplyRedirects(node, new Method(handle.getName(), handle.getDesc()), newName,
                                        methodRedirectsIn, fieldRedirectsIn, typeRedirectsIn);
                            }
                        }
                    }
                }
            }
        }
        return lambdaRedirects;
    }

    private static final class ClassMethod {
        final Type owner;
        final Method method;

        ClassMethod(Type owner, Method method) {
            this.owner = owner;
            this.method = method;
        }

        @Override public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ClassMethod that = (ClassMethod) o;
            return owner.equals(that.owner) &&
                    method.equals(that.method);
        }

        @Override public int hashCode() {
            return Objects.hash(owner, method);
        }

        @Override public String toString() {
            return "ClassMethod{" +
                    "owner=" + owner +
                    ", method=" + method +
                    '}';
        }
    }

    private static final class ClassField {
        final Type owner;
        final String name;

        ClassField(String owner, String name) {
            this.owner = getObjectType(owner);
            this.name = name;
        }

        @Override public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ClassField that = (ClassField) o;
            return owner.equals(that.owner) &&
                    name.equals(that.name);
        }

        @Override public int hashCode() {
            return Objects.hash(owner, name);
        }

        @Override public String toString() {
            return "ClassField{" +
                    "owner=" + owner +
                    ", name='" + name + '\'' +
                    '}';
        }
    }
}