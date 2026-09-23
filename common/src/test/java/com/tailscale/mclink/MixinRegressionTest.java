/*
 * Copyright (c) 2026, Jasper (Axodouble) V. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package com.tailscale.mclink;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the classes of mixin defects that broke the 26.3
 * clients:
 *
 * <p>1. A non-mixin helper class placed in the declared mixin package
 *    (issue #15). Fabric/NeoForge loader classloaders guard their mixin
 *    packages, so any class there that is not itself a {@code @Mixin} fails to
 *    load when a mixin references it.
 *
 * <p>2. An injection targeting a method that does not exist on the target
 *    (issue #16, {@code Minecraft.destroy} removed in 26.2). Mixin resolves the
 *    target at apply time and throws if the method is absent.
 *
 * <p>3. An injection into a zero-argument method whose callback declares an
 *    extra parameter it cannot match (issue #17, {@code Minecraft.close}
 *    injected with a {@code Minecraft self} argument). Mixin can only bind the
 *    instance to a callback when the target also has real parameters, so a lone
 *    {@code self} on a no-arg target is rejected with an
 *    {@code InvalidInjectionException} at apply time.
 *
 * <p>4. An {@code @Inject} callback that does not end with a
 *    {@code CallbackInfo} or {@code CallbackInfoReturnable} parameter
 *    (issue #26, {@code Minecraft.tick} injected with a no-argument
 *    {@code mclink$onTick} handler). Mixin derives the expected descriptor as
 *    the target's parameters plus a trailing callback-info parameter, so a
 *    bare {@code ()V} handler is rejected with an
 *    {@code InvalidInjectionException} at apply time.
 *
 * <p>These tests run in the {@code test} task of every leaf. They read the
 * mixin classes and their named Minecraft targets straight off the test
 * classpath with ASM, so no Minecraft runtime is required.
 */
class MixinRegressionTest {

    private static final String MIXIN_PKG = "com.tailscale.mclink.mixin";
    private static final String MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String SHADOW_ANNOTATION = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String[] CONFIG_NAMES = {
        "mclink.mixins.json", "mclink.neoforge.mixins.json", "mclink.forge.mixins.json"
    };
    private static final String INJECT_ANNOTATION =
        "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final Set<String> INJECTION_ANNOTATIONS = Set.of(
        "Lorg/spongepowered/asm/mixin/injection/Inject;",
        "Lorg/spongepowered/asm/mixin/injection/Redirect;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
        "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;"
    );
    private static final String CALLBACK_INFO =
        "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    private static final String CALLBACK_INFO_RETURNABLE =
        "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;";

    @Test
    void mixinPackageContainsOnlyMixinClasses() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String cls : topLevelClassesIn(MIXIN_PKG)) {
            if (!hasMixinAnnotation(readClass(cls))) {
                offenders.add(cls);
            }
        }
        assertTrue(offenders.isEmpty(),
            "Non-mixin classes found in the mixin package " + MIXIN_PKG
                + "; loader classloaders guard this package, so a mixin that "
                + "references one of them fails to load at runtime: " + offenders);
    }

    @Test
    void injectionTargetsExistOnTargetClasses() throws IOException {
        List<String> problems = new ArrayList<>();
        for (JsonObject config : mixinConfigs()) {
            String pkg = config.get("package").getAsString();
            for (String mixin : declaredMixins(config)) {
                MixinInfo info = parseMixin(readClass(pkg + "." + mixin));
                for (String target : info.targets) {
                    Set<String> names = methodNamesOfHierarchy(target);
                    for (String method : info.injectionMethods) {
                        if (!names.contains(method)) {
                            problems.add(pkg + "." + mixin + " -> " + target
                                + " has no method '" + method + "'");
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(),
            "Mixin injections target methods that do not exist on their "
                + "target class:\n  " + String.join("\n  ", problems));
    }

    @Test
    void shadowTargetsExistOnTargetClasses() throws IOException {
        List<String> problems = new ArrayList<>();
        for (JsonObject config : mixinConfigs()) {
            String pkg = config.get("package").getAsString();
            for (String mixin : declaredMixins(config)) {
                byte[] bytes = readClass(pkg + "." + mixin);
                MixinInfo info = parseMixin(bytes);
                ShadowInfo shadows = parseShadows(bytes);
                if (shadows.fields.isEmpty() && shadows.methods.isEmpty()) {
                    continue;
                }
                for (String target : info.targets) {
                    Set<String> targetMethods = methodNamesOfHierarchy(target);
                    Set<String> targetFields = fieldNamesOfHierarchy(target);
                    for (String field : shadows.fields) {
                        if (!targetFields.contains(field)) {
                            problems.add(pkg + "." + mixin + " -> " + target
                                + " has no field '" + field + "' (declared as @Shadow)");
                        }
                    }
                    for (String method : shadows.methods) {
                        if (!targetMethods.contains(method)) {
                            problems.add(pkg + "." + mixin + " -> " + target
                                + " has no method '" + method + "' (declared as @Shadow)");
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(),
            "Mixin @Shadow fields/methods do not exist on their target class:\n  "
                + String.join("\n  ", problems));
    }

    @Test
    void zeroArgInjectionCallbacksDeclareNoUnmatchableParameters() throws IOException {
        List<String> problems = new ArrayList<>();
        for (JsonObject config : mixinConfigs()) {
            String pkg = config.get("package").getAsString();
            for (String mixin : declaredMixins(config)) {
                MixinInfo info = parseMixin(readClass(pkg + "." + mixin));
                for (InjectionInfo inj : info.injections) {
                    if (inj.atHasTarget) {
                        continue;
                    }
                    for (String target : info.targets) {
                        if (targetIsZeroArg(target, inj.targetSpec)
                                && callbackExtraParams(inj.callbackDesc) != 0) {
                            problems.add(pkg + "." + mixin + " -> " + inj.callbackName
                                + " (" + inj.callbackDesc + ") injects into the "
                                + "zero-argument " + target + "." + methodName(inj.targetSpec)
                                + " but declares an unmatchable parameter; Mixin "
                                + "rejects the injection at apply time");
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(),
            "Mixin injections into zero-argument methods declare callback "
                + "parameters that Mixin cannot match (the instance cannot be "
                + "bound to a no-argument target):\n  " + String.join("\n  ", problems));
    }

    @Test
    void injectionCallbacksMatchTargetArity() throws IOException {
        List<String> problems = new ArrayList<>();
        for (JsonObject config : mixinConfigs()) {
            String pkg = config.get("package").getAsString();
            for (String mixin : declaredMixins(config)) {
                MixinInfo info = parseMixin(readClass(pkg + "." + mixin));
                for (InjectionInfo inj : info.injections) {
                    if (inj.atHasTarget) {
                        continue;
                    }
                    for (String target : info.targets) {
                        Integer targetArity = targetMethodParamCount(target, inj.targetSpec);
                        if (targetArity == null) {
                            continue;
                        }
                        int callbackArity = callbackExtraParams(inj.callbackDesc);
                        if (callbackArity != targetArity) {
                            problems.add(pkg + "." + mixin + " -> " + inj.callbackName
                                + " (" + inj.callbackDesc + ") injects into " + target + "."
                                + methodName(inj.targetSpec) + " (arity " + targetArity
                                + ") but its callback declares " + callbackArity
                                + " parameter(s) before CallbackInfo; Mixin rejects the "
                                + "mismatch at apply time");
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(),
            "Mixin @Inject callbacks do not match their target method's parameter "
                + "count (an extra leading parameter, such as the instance, is "
                + "rejected at apply time):\n  " + String.join("\n  ", problems));
    }

    @Test
    void injectCallbacksEndWithCallbackInfo() throws IOException {
        List<String> problems = new ArrayList<>();
        for (JsonObject config : mixinConfigs()) {
            String pkg = config.get("package").getAsString();
            for (String mixin : declaredMixins(config)) {
                MixinInfo info = parseMixin(readClass(pkg + "." + mixin));
                for (InjectionInfo inj : info.injections) {
                    if (!INJECT_ANNOTATION.equals(inj.annotation)) {
                        continue;
                    }
                    if (!endsWithCallbackInfo(inj.callbackDesc)) {
                        problems.add(pkg + "." + mixin + " -> " + inj.callbackName
                            + " (" + inj.callbackDesc + ") is an @Inject callback that "
                            + "does not end with CallbackInfo or CallbackInfoReturnable; "
                            + "Mixin rejects the injection at apply time");
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(),
            "Mixin @Inject callbacks do not end with a CallbackInfo or "
                + "CallbackInfoReturnable parameter (Mixin requires one and rejects "
                + "the injection at apply time):\n  " + String.join("\n  ", problems));
    }

    private static final class MixinInfo {
        final Set<String> targets = new LinkedHashSet<>();
        final Set<String> injectionMethods = new LinkedHashSet<>();
        final List<InjectionInfo> injections = new ArrayList<>();
    }

    private static final class ShadowInfo {
        final Set<String> fields = new LinkedHashSet<>();
        final Set<String> methods = new LinkedHashSet<>();
    }

    private static final class InjectionInfo {
        String annotation;
        String callbackName;
        String callbackDesc;
        boolean callbackStatic;
        String targetSpec;
        boolean atHasTarget;
    }

    private static MixinInfo parseMixin(byte[] bytes) {
        MixinInfo info = new MixinInfo();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                if (!MIXIN_ANNOTATION.equals(desc)) {
                    return null;
                }
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        if ("value".equals(name)) {
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String n, Object value) {
                                    info.targets.add(((Type) value).getClassName());
                                }
                            };
                        }
                        if ("targets".equals(name)) {
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String n, Object value) {
                                    info.targets.add(((String) value).replace('/', '.'));
                                }
                            };
                        }
                        return null;
                    }
                };
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        if (!INJECTION_ANNOTATIONS.contains(desc)) {
                            return null;
                        }
                        InjectionInfo inj = new InjectionInfo();
                        inj.annotation = desc;
                        inj.callbackName = name;
                        inj.callbackDesc = descriptor;
                        inj.callbackStatic = isStatic;
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public AnnotationVisitor visitArray(String name) {
                                if ("method".equals(name)) {
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override
                                        public void visit(String n, Object value) {
                                            String spec = (String) value;
                                            info.injectionMethods.add(methodName(spec));
                                            inj.targetSpec = spec;
                                        }
                                    };
                                }
                                return null;
                            }

                            @Override
                            public AnnotationVisitor visitAnnotation(String name, String atDesc) {
                                if ("at".equals(name)) {
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override
                                        public void visit(String n, Object value) {
                                            if ("target".equals(n)) {
                                                inj.atHasTarget = true;
                                            }
                                        }
                                    };
                                }
                                return null;
                            }

                            @Override
                            public void visitEnd() {
                                info.injections.add(inj);
                            }
                        };
                    }
                };
            }
        }, 0);
        return info;
    }

    private static ShadowInfo parseShadows(byte[] bytes) {
        ShadowInfo info = new ShadowInfo();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                                           String signature, Object value) {
                return new FieldVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        if (SHADOW_ANNOTATION.equals(desc)) {
                            info.fields.add(name);
                        }
                        return null;
                    }
                };
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        if (SHADOW_ANNOTATION.equals(desc)) {
                            info.methods.add(name);
                        }
                        return null;
                    }
                };
            }
        }, ClassReader.SKIP_CODE);
        return info;
    }

    private static boolean hasMixinAnnotation(byte[] bytes) {
        boolean[] found = {false};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                if (MIXIN_ANNOTATION.equals(desc)) {
                    found[0] = true;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE);
        return found[0];
    }

    private static Set<String> methodNamesOfHierarchy(String dotName) {
        Set<String> names = new LinkedHashSet<>();
        collectMethodNames(dotName, names, new LinkedHashSet<>());
        return names;
    }

    private static void collectMethodNames(String dotName, Set<String> into, Set<String> visited) {
        if (!visited.add(dotName)) {
            return;
        }
        byte[] bytes = tryReadClass(dotName);
        if (bytes == null) {
            return;
        }
        String[] superHolder = new String[1];
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String className, String signature,
                              String superName, String[] interfaces) {
                superHolder[0] = superName;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                into.add(name);
                return null;
            }
        }, ClassReader.SKIP_CODE);
        if (superHolder[0] != null) {
            collectMethodNames(superHolder[0].replace('/', '.'), into, visited);
        }
    }

    private static Set<String> fieldNamesOfHierarchy(String dotName) {
        Set<String> names = new LinkedHashSet<>();
        collectFieldNames(dotName, names, new LinkedHashSet<>());
        return names;
    }

    private static void collectFieldNames(String dotName, Set<String> into, Set<String> visited) {
        if (!visited.add(dotName)) {
            return;
        }
        byte[] bytes = tryReadClass(dotName);
        if (bytes == null) {
            return;
        }
        String[] superHolder = new String[1];
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String className, String signature,
                              String superName, String[] interfaces) {
                superHolder[0] = superName;
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                                           String signature, Object value) {
                into.add(name);
                return null;
            }
        }, ClassReader.SKIP_CODE);
        if (superHolder[0] != null) {
            collectFieldNames(superHolder[0].replace('/', '.'), into, visited);
        }
    }

    /**
     * The number of a callback method's parameters other than its trailing
     * {@code CallbackInfo}/{@code CallbackInfoReturnable}.
     */
    private static int callbackExtraParams(String descriptor) {
        Type[] args = Type.getArgumentTypes(descriptor);
        int n = args.length;
        if (n == 0) {
            return 0;
        }
        String last = args[n - 1].getDescriptor();
        return endsWithCallbackInfo(descriptor) ? n - 1 : n;
    }

    /**
     * Whether the callback method's final parameter is a
     * {@code CallbackInfo} or {@code CallbackInfoReturnable}. An
     * {@code @Inject} callback without one is rejected by Mixin at apply time.
     */
    private static boolean endsWithCallbackInfo(String descriptor) {
        Type[] args = Type.getArgumentTypes(descriptor);
        if (args.length == 0) {
            return false;
        }
        String last = args[args.length - 1].getDescriptor();
        return CALLBACK_INFO.equals(last) || CALLBACK_INFO_RETURNABLE.equals(last);
    }

    /**
     * Whether the target method named by {@code targetSpec} takes no arguments.
     * A spec carrying a descriptor is zero-argument iff its parameter list is
     * empty; a bare name is resolved against the target's class hierarchy, and
     * is treated as ambiguous (and skipped) if the name has more than one
     * overload.
     */
    private static boolean targetIsZeroArg(String target, String targetSpec) {
        if (targetSpec == null) {
            return false;
        }
        String spec = targetSpec.trim();
        int paren = spec.indexOf('(');
        if (paren >= 0) {
            return paren + 1 < spec.length() && spec.charAt(paren + 1) == ')';
        }
        Set<String> descriptors = methodDescriptorsOfHierarchy(target, methodName(spec));
        return descriptors.size() == 1 && descriptors.contains("()V");
    }

    /**
     * The parameter count of the target method named by {@code targetSpec}, or
     * {@code null} if the spec is a bare name with more than one overload on the
     * target's hierarchy. A spec carrying a descriptor is counted directly.
     */
    private static Integer targetMethodParamCount(String target, String targetSpec) {
        if (targetSpec == null) {
            return null;
        }
        String spec = targetSpec.trim();
        int paren = spec.indexOf('(');
        if (paren >= 0) {
            return Type.getArgumentTypes(spec.substring(paren)).length;
        }
        Set<String> descriptors = methodDescriptorsOfHierarchy(target, methodName(spec));
        if (descriptors.size() != 1) {
            return null;
        }
        return Type.getArgumentTypes(descriptors.iterator().next()).length;
    }

    private static Set<String> methodDescriptorsOfHierarchy(String dotName, String methodName) {
        Set<String> descriptors = new LinkedHashSet<>();
        collectMethodDescriptors(dotName, methodName, descriptors, new LinkedHashSet<>());
        return descriptors;
    }

    private static void collectMethodDescriptors(String dotName, String methodName,
                                                 Set<String> into, Set<String> visited) {
        if (!visited.add(dotName)) {
            return;
        }
        byte[] bytes = tryReadClass(dotName);
        if (bytes == null) {
            return;
        }
        String[] superHolder = new String[1];
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String className, String signature,
                              String superName, String[] interfaces) {
                superHolder[0] = superName;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (name.equals(methodName)) {
                    into.add(descriptor);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE);
        if (superHolder[0] != null) {
            collectMethodDescriptors(superHolder[0].replace('/', '.'), methodName, into, visited);
        }
    }

    private static List<JsonObject> mixinConfigs() throws IOException {
        List<JsonObject> configs = new ArrayList<>();
        ClassLoader cl = MixinRegressionTest.class.getClassLoader();
        for (String name : CONFIG_NAMES) {
            Enumeration<URL> urls = cl.getResources(name);
            while (urls.hasMoreElements()) {
                try (InputStream in = urls.nextElement().openStream()) {
                    configs.add(JsonParser.parseString(readAll(in)).getAsJsonObject());
                }
            }
        }
        return configs;
    }

    private static Set<String> declaredMixins(JsonObject config) {
        Set<String> mixins = new LinkedHashSet<>();
        for (String key : new String[]{"mixins", "client", "server"}) {
            JsonElement element = config.get(key);
            if (element != null && element.isJsonArray()) {
                for (JsonElement el : (JsonArray) element) {
                    mixins.add(el.getAsString());
                }
            }
        }
        return mixins;
    }

    private static Set<String> topLevelClassesIn(String pkg) throws IOException {
        Set<String> classes = new LinkedHashSet<>();
        String pkgPath = pkg.replace('.', '/');
        Enumeration<URL> urls = MixinRegressionTest.class.getClassLoader().getResources(pkgPath);
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            if ("file".equals(url.getProtocol())) {
                File dir;
                try {
                    dir = new File(url.toURI());
                } catch (java.net.URISyntaxException e) {
                    continue;
                }
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String n = f.getName();
                        if (n.endsWith(".class") && !n.contains("$")) {
                            classes.add(pkg + "." + n.substring(0, n.length() - 6));
                        }
                    }
                }
            } else if ("jar".equals(url.getProtocol())) {
                String jarPath = url.getPath();
                int bang = jarPath.indexOf('!');
                if (bang > 0) {
                    jarPath = jarPath.substring(0, bang);
                }
                try (JarFile jar = new JarFile(jarPath)) {
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        String e = entries.nextElement().getName();
                        if (e.startsWith(pkgPath + "/") && e.endsWith(".class") && !e.contains("$")) {
                            String rel = e.substring(pkgPath.length() + 1, e.length() - 6);
                            if (!rel.contains("/")) {
                                classes.add(pkg + "." + rel);
                            }
                        }
                    }
                }
            }
        }
        return classes;
    }

    private static byte[] readClass(String dotName) throws IOException {
        byte[] bytes = tryReadClass(dotName);
        assertNotNull(bytes, "class not on the test classpath: " + dotName);
        return bytes;
    }

    private static byte[] tryReadClass(String dotName) {
        String resource = dotName.replace('.', '/') + ".class";
        try (InputStream in = MixinRegressionTest.class.getClassLoader().getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String methodName(String methodSpec) {
        int paren = methodSpec.indexOf('(');
        return paren >= 0 ? methodSpec.substring(0, paren) : methodSpec;
    }
}
