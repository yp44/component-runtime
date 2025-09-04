/**
 * Copyright (C) 2006-2025 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.talend.sdk.component.runtime.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.jar.JarOutputStream;

import javax.json.Json;
import javax.json.JsonValue;

import org.apache.xbean.asm9.AnnotationVisitor;
import org.apache.xbean.asm9.ClassWriter;
import org.apache.xbean.asm9.FieldVisitor;
import org.apache.xbean.asm9.MethodVisitor;
import org.apache.xbean.asm9.Opcodes;
import org.apache.xbean.asm9.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.type.DataSet;
import org.talend.sdk.component.api.configuration.type.DataStore;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.input.PartitionMapper;
import org.talend.sdk.component.api.processor.ElementListener;
import org.talend.sdk.component.api.processor.Processor;
import org.talend.sdk.component.runtime.input.CheckpointState;
import org.talend.sdk.component.runtime.manager.asm.PluginGenerator;

class ComponentManagerExtTest {

    private final PluginGenerator pluginGenerator = new PluginGenerator();

    @Test
    void mergeCheckpointConfiguration(@TempDir final File temporaryFolder) throws IOException {
        final File pluginFolder = new File(temporaryFolder, "test-plugins_" + UUID.randomUUID().toString());
        pluginFolder.mkdirs();
        final File plugin = new File(pluginFolder, "checkpoint-plugin.jar");
        final String packageName = "org.talend.test.checkpoint";
        final String packagePath = packageName.replace('.', '/');

        try (final JarOutputStream jar = new JarOutputStream(new FileOutputStream(plugin))) {
            pluginGenerator.createPluginAt(plugin, out -> {
                try {
                    // Create a datastore
                    final ClassWriter dsWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    dsWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, packagePath + "/MyDataStore", null,
                            "java/lang/Object", null);
                    dsWriter.visitAnnotation(Type.getDescriptor(DataStore.class), true).visit("value", "test-ds");
                    dsWriter.visitEnd();
                    MethodVisitor mv = dsWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();
                    out.putNextEntry(new java.util.jar.JarEntry(packagePath + "/MyDataStore.class"));
                    out.write(dsWriter.toByteArray());
                    out.closeEntry();

                    // Create a dataset
                    final ClassWriter dataSetWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    dataSetWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, packagePath + "/MyDataSet", null,
                            "java/lang/Object", null);
                    dataSetWriter.visitAnnotation(Type.getDescriptor(DataSet.class), true).visit("value", "test-ds");
                    dataSetWriter.visitEnd();
                    mv = dataSetWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();
                    out.putNextEntry(new java.util.jar.JarEntry(packagePath + "/MyDataSet.class"));
                    out.write(dataSetWriter.toByteArray());
                    out.closeEntry();

                    // Create a configuration class with a checkpoint
                    final ClassWriter configWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    configWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, packagePath + "/MyConfig", null,
                            "java/lang/Object", null);
                    {
                        final FieldVisitor fv = configWriter.visitField(Opcodes.ACC_PRIVATE, "checkpoint",
                                "Ljava/lang/String;", null, null);
                        AnnotationVisitor av = fv.visitAnnotation(Type.getDescriptor(Option.class), true);
                        av.visit("value", "checkpoint");
                        av.visitEnd();
                        av = fv.visitAnnotation("Lorg/talend/sdk/component/api/service/checkpoint/CheckPoint;", true);
                        av.visitEnd();
                        fv.visitEnd();
                    }
                    mv = configWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();
                    out.putNextEntry(new java.util.jar.JarEntry(packagePath + "/MyConfig.class"));
                    out.write(configWriter.toByteArray());
                    out.closeEntry();

                    // Create a mapper
                    final ClassWriter mapperWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    final AnnotationVisitor mapperAnnotation = mapperWriter
                            .visitAnnotation(Type.getDescriptor(PartitionMapper.class), true);
                    mapperAnnotation.visit("name", "mymapper");
                    mapperAnnotation.visit("family", "test");
                    mapperAnnotation.visitEnd();
                    mapperWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, packagePath + "/MyMapper", null,
                            "java/lang/Object", null);
                    mv = mapperWriter
                            .visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                                    "(L" + packagePath + "/MyDataSet;L" + packagePath + "/MyConfig;)V", null, null);
                    mv.visitParameterAnnotation(1, Type.getDescriptor(Option.class), true)
                            .visit("value", "configuration");
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(1, 3);
                    mv.visitEnd();

                    // @Assessor
                    mv = mapperWriter.visitMethod(Opcodes.ACC_PUBLIC, "size", "()J", null, null);
                    mv.visitAnnotation("Lorg/talend/sdk/component/api/input/Assessor;", true).visitEnd();
                    mv.visitCode();
                    mv.visitInsn(Opcodes.LCONST_0);
                    mv.visitInsn(Opcodes.LRETURN);
                    mv.visitMaxs(2, 1);
                    mv.visitEnd();

                    // @Split
                    mv = mapperWriter.visitMethod(Opcodes.ACC_PUBLIC, "split", "()Ljava/util/List;",
                            "()Ljava/util/List<L" + packagePath + "/MyMapper;>;", null);
                    mv.visitAnnotation("Lorg/talend/sdk/component/api/input/Split;", true).visitEnd();
                    mv.visitCode();
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections", "emptyList", "()Ljava/util/List;",
                            true);
                    mv.visitInsn(Opcodes.ARETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();

                    // @Emitter
                    mv = mapperWriter.visitMethod(Opcodes.ACC_PUBLIC, "create", "()L" + packagePath + "/MyEmitter;",
                            null, null);
                    mv.visitAnnotation("Lorg/talend/sdk/component/api/input/Emitter;", true).visitEnd();
                    mv.visitCode();
                    mv.visitTypeInsn(Opcodes.NEW, packagePath + "/MyEmitter");
                    mv.visitInsn(Opcodes.DUP);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, packagePath + "/MyEmitter", "<init>", "()V", false);
                    mv.visitInsn(Opcodes.ARETURN);
                    mv.visitMaxs(2, 1);
                    mv.visitEnd();

                    mapperWriter.visitEnd();
                    out.putNextEntry(new java.util.jar.JarEntry(packagePath + "/MyMapper.class"));
                    out.write(mapperWriter.toByteArray());
                    out.closeEntry();

                    // Create a dummy emitter class
                    final ClassWriter emitterWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    emitterWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, packagePath + "/MyEmitter", null,
                            "java/lang/Object", null);
                    MethodVisitor mvEmitter =
                            emitterWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
                    mvEmitter.visitCode();
                    mvEmitter.visitVarInsn(Opcodes.ALOAD, 0);
                    mvEmitter.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                    mvEmitter.visitInsn(Opcodes.RETURN);
                    mvEmitter.visitMaxs(1, 1);
                    mvEmitter.visitEnd();
                    // @Producer method
                    mvEmitter = emitterWriter.visitMethod(Opcodes.ACC_PUBLIC, "next", "()Ljava/lang/Object;", null,
                            null);
                    mvEmitter.visitAnnotation("Lorg/talend/sdk/component/api/input/Producer;", true).visitEnd();
                    mvEmitter.visitCode();
                    mvEmitter.visitInsn(Opcodes.ACONST_NULL);
                    mvEmitter.visitInsn(Opcodes.ARETURN);
                    mvEmitter.visitMaxs(1, 1);
                    mvEmitter.visitEnd();
                    emitterWriter.visitEnd();
                    out.putNextEntry(new java.util.jar.JarEntry(packagePath + "/MyEmitter.class"));
                    out.write(emitterWriter.toByteArray());
                    out.closeEntry();

                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }

        try (final ComponentManager manager = new ComponentManager(new File("target/test-dependencies"))) {
            System.setProperty("talend.checkpoint.enabled", "true");
            final String pluginId = manager.addPlugin(plugin.getAbsolutePath());
            final Map<String, String> config = new HashMap<>();
            config.put(CheckpointState.CHECKPOINT_KEY, "my-checkpoint-value");

            final Map<String, String> merged = manager.mergeCheckpointConfiguration(pluginId, "mymapper",
                    ComponentManager.ComponentType.MAPPER, config);

            assertEquals(1, merged.size());
            assertEquals("my-checkpoint-value", merged.get("configuration.checkpoint"));
        } finally {
            System.clearProperty("talend.checkpoint.enabled");
        }
    }

    @Test
    void findComponentWithInvalidConfiguration(@TempDir final File temporaryFolder) throws IOException {
        final File pluginFolder = new File(temporaryFolder, "test-plugins_" + UUID.randomUUID().toString());
        pluginFolder.mkdirs();
        final File plugin = new File(pluginFolder, "invalid-config-plugin.jar");
        final String packageName = "org.talend.test.invalidconfig";
        final String packagePath = packageName.replace('.', '/');

        try (final JarOutputStream jar = new JarOutputStream(new FileOutputStream(plugin))) {
            pluginGenerator.createPluginAt(plugin, out -> {
                try {
                    // Create a configuration class with an integer field
                    final ClassWriter configWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    configWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, packagePath + "/MyConfig", null,
                            "java/lang/Object", null);
                    configWriter.visitAnnotation(Type.getDescriptor(GridLayout.class), true).visitEnd();
                    final FieldVisitor fv = configWriter.visitField(Opcodes.ACC_PRIVATE, "age", "I", null, null);
                    fv.visitAnnotation(Type.getDescriptor(Option.class), true).visitEnd();
                    fv.visitEnd();
                    // getter and setter for age
                    MethodVisitor mv =
                            configWriter.visitMethod(Opcodes.ACC_PUBLIC, "getAge", "()I", null, null);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitFieldInsn(Opcodes.GETFIELD, packagePath + "/MyConfig", "age", "I");
                    mv.visitInsn(Opcodes.IRETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();
                    mv = configWriter.visitMethod(Opcodes.ACC_PUBLIC, "setAge", "(I)V", null, null);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitVarInsn(Opcodes.ILOAD, 1);
                    mv.visitFieldInsn(Opcodes.PUTFIELD, packagePath + "/MyConfig", "age", "I");
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(2, 2);
                    mv.visitEnd();
                    // constructor
                    mv = configWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(1, 1);
                    mv.visitEnd();
                    configWriter.visitEnd();
                    out.putNextEntry(new java.util.jar.JarEntry(packagePath + "/MyConfig.class"));
                    out.write(configWriter.toByteArray());
                    out.closeEntry();

                    // Create a processor that uses this configuration
                    final ClassWriter procWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                    final AnnotationVisitor procAnnotation =
                            procWriter.visitAnnotation(Type.getDescriptor(Processor.class), true);
                    procAnnotation.visit("family", "test");
                    procAnnotation.visit("name", "default");
                    procAnnotation.visitEnd();
                    procWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, packagePath + "/MyProcessor", null,
                            "java/lang/Object", null);
                    mv = procWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                            "(L" + packagePath + "/MyConfig;)V", null, null);
                    mv.visitParameterAnnotation(0, Type.getDescriptor(Option.class), true)
                            .visit("value", "configuration");
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(1, 2);
                    mv.visitEnd();
                    mv = procWriter.visitMethod(Opcodes.ACC_PUBLIC, "onNext",
                            "(Ljavax/json/JsonObject;)V", null, null);
                    mv.visitAnnotation(Type.getDescriptor(ElementListener.class), true).visitEnd();
                    mv.visitCode();
                    mv.visitInsn(Opcodes.RETURN);
                    mv.visitMaxs(0, 2);
                    mv.visitEnd();
                    procWriter.visitEnd();
                    out.putNextEntry(new java.util.jar.JarEntry(packagePath + "/MyProcessor.class"));
                    out.write(procWriter.toByteArray());
                    out.closeEntry();

                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }

        try (final ComponentManager manager = new ComponentManager(new File("target/test-dependencies"))) {
            final String pluginId = manager.addPlugin(plugin.getAbsolutePath());
            final Map<String, String> config = new HashMap<>();
            config.put("configuration.age", "not-an-int");
            assertThrows(IllegalArgumentException.class,
                    () -> manager.findProcessor(pluginId, "default", 1, config));
        }
    }

    @Test
    void jsonToMap() {
        final JsonValue json = Json.createReader(new java.io.StringReader(
                "{\"user\":{\"name\":\"test\",\"pwd\":\"p\"},\"configuration\":[1,2,3],\"db\":\"mysql\"}"))
                .readValue();
        final Map<String, String> map = ComponentManager.jsonToMap(json);
        assertEquals(6, map.size());
        assertEquals("test", map.get("user.name"));
        assertEquals("p", map.get("user.pwd"));
        assertEquals("1", map.get("configuration[0]"));
        assertEquals("2", map.get("configuration[1]"));
        assertEquals("3", map.get("configuration[2]"));
        assertEquals("mysql", map.get("db"));
    }

    @Test
    void replaceKeys() {
        final Map<String, String> configuration = new HashMap<>();
        configuration.put("test.user", "tester");
        configuration.put("test.pwd", "pwd");
        configuration.put("db.host", "localhost");

        final Map<String, String> replaced = ComponentManager.replaceKeys(configuration, "test", "new");
        assertEquals(3, replaced.size());
        assertEquals("tester", replaced.get("new.user"));
        assertEquals("pwd", replaced.get("new.pwd"));
        assertEquals("localhost", replaced.get("db.host"));
    }

    @Test
    void addNotExistingPlugin() {
        try (final ComponentManager manager = new ComponentManager(new File("target/test-dependencies"))) {
            assertThrows(IllegalArgumentException.class, () -> manager.addPlugin("not/existing/path.jar"));
        }
    }

    @Test
    void addCorruptedPlugin(@TempDir final File temporaryFolder) throws IOException {
        final File pluginFolder = new File(temporaryFolder, "test-plugins_" + UUID.randomUUID().toString());
        pluginFolder.mkdirs();
        final File plugin = new File(pluginFolder, "invalid-plugin.jar");
        assertTrue(plugin.createNewFile());

        try (final ComponentManager manager = new ComponentManager(new File("target/test-dependencies"))) {
            assertThrows(IllegalArgumentException.class, () -> manager.addPlugin(plugin.getAbsolutePath()));
        }
    }

    @Test
    void findComponentFromNotExistingPlugin() {
        try (final ComponentManager manager = new ComponentManager(new File("target/test-dependencies"))) {
            assertTrue(manager.findMapper("non-existing-plugin", "test-component", 1, Collections.emptyMap())
                    .isEmpty());
        }
    }

    @Test
    void findNotExistingComponent(@TempDir final File temporaryFolder) {
        final File pluginFolder = new File(temporaryFolder, "test-plugins_" + UUID.randomUUID().toString());
        pluginFolder.mkdirs();
        final File plugin = pluginGenerator.createChainPlugin(pluginFolder, "plugin.jar");

        try (final ComponentManager manager = new ComponentManager(new File("target/test-dependencies"))) {
            manager.addPlugin(plugin.getAbsolutePath());
            assertTrue(manager.findMapper(plugin.getName(), "non-existing-component", 1, Collections.emptyMap())
                    .isEmpty());
        }
    }
}
