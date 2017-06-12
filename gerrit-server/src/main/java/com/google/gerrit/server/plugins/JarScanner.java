// Copyright (C) 2014 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.plugins;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.collect.Iterables.transform;

import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.eclipse.jgit.util.IO;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class JarScanner implements PluginContentScanner {
  private static final int SKIP_ALL =
      ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;
  private final JarFile jarFile;

  public JarScanner(Path src) throws IOException {
    this.jarFile = new JarFile(src.toFile());
  }

  @Override
  public Map<Class<? extends Annotation>, Iterable<ExtensionMetaData>> scan(
      String pluginName, Iterable<Class<? extends Annotation>> annotations)
      throws InvalidPluginException {
    Set<String> descriptors = new HashSet<>();
    Multimap<String, JarScanner.ClassData> rawMap = ArrayListMultimap.create();
    Map<Class<? extends Annotation>, String> classObjToClassDescr = new HashMap<>();

    for (Class<? extends Annotation> annotation : annotations) {
      String descriptor = Type.getType(annotation).getDescriptor();
      descriptors.add(descriptor);
      classObjToClassDescr.put(annotation, descriptor);
    }

    Enumeration<JarEntry> e = jarFile.entries();
    while (e.hasMoreElements()) {
      JarEntry entry = e.nextElement();
      if (skip(entry)) {
        continue;
      }

      ClassData def = new ClassData(descriptors);
      try {
        new ClassReader(read(jarFile, entry)).accept(def, SKIP_ALL);
      } catch (IOException err) {
        throw new InvalidPluginException("Cannot auto-register", err);
      } catch (RuntimeException err) {
        PluginLoader.log.warn(
            String.format(
                "Plugin %s has invalid class file %s inside of %s",
                pluginName, entry.getName(), jarFile.getName()),
            err);
        continue;
      }

      if (!Strings.isNullOrEmpty(def.annotationName)) {
        if (def.isConcrete()) {
          rawMap.put(def.annotationName, def);
        } else {
          PluginLoader.log.warn(
              String.format(
                  "Plugin %s tries to @%s(\"%s\") abstract class %s",
                  pluginName, def.annotationName, def.annotationValue, def.className));
        }
      }
    }

    ImmutableMap.Builder<Class<? extends Annotation>, Iterable<ExtensionMetaData>> result =
        ImmutableMap.builder();

    for (Class<? extends Annotation> annotoation : annotations) {
      String descr = classObjToClassDescr.get(annotoation);
      Collection<ClassData> discoverdData = rawMap.get(descr);
      Collection<ClassData> values = firstNonNull(discoverdData, Collections.<ClassData>emptySet());

      result.put(
          annotoation,
          transform(values, cd -> new ExtensionMetaData(cd.className, cd.annotationValue)));
    }

    return result.build();
  }

  public List<String> findSubClassesOf(Class<?> superClass) throws IOException {
    return findSubClassesOf(superClass.getName());
  }

  private List<String> findSubClassesOf(String superClass) throws IOException {
    String name = superClass.replace('.', '/');

    List<String> classes = new ArrayList<>();
    Enumeration<JarEntry> e = jarFile.entries();
    while (e.hasMoreElements()) {
      JarEntry entry = e.nextElement();
      if (skip(entry)) {
        continue;
      }

      ClassData def = new ClassData(Collections.<String>emptySet());
      try {
        new ClassReader(read(jarFile, entry)).accept(def, SKIP_ALL);
      } catch (RuntimeException err) {
        PluginLoader.log.warn(
            String.format("Jar %s has invalid class file %s", jarFile.getName(), entry.getName()),
            err);
        continue;
      }

      if (name.equals(def.superName)) {
        classes.addAll(findSubClassesOf(def.className));
        if (def.isConcrete()) {
          classes.add(def.className);
        }
      }
    }

    return classes;
  }

  private static boolean skip(JarEntry entry) {
    if (!entry.getName().endsWith(".class")) {
      return true; // Avoid non-class resources.
    }
    if (entry.getSize() <= 0) {
      return true; // Directories have 0 size.
    }
    if (entry.getSize() >= 1024 * 1024) {
      return true; // Do not scan huge class files.
    }
    return false;
  }

  private static byte[] read(JarFile jarFile, JarEntry entry) throws IOException {
    byte[] data = new byte[(int) entry.getSize()];
    try (InputStream in = jarFile.getInputStream(entry)) {
      IO.readFully(in, data, 0, data.length);
    }
    return data;
  }

  public static class ClassData extends ClassVisitor {
    int access;
    String className;
    String superName;
    String annotationName;
    String annotationValue;
    String[] interfaces;
    Collection<String> exports;

    private ClassData(Collection<String> exports) {
      super(Opcodes.ASM5);
      this.exports = exports;
    }

    boolean isConcrete() {
      return (access & Opcodes.ACC_ABSTRACT) == 0 && (access & Opcodes.ACC_INTERFACE) == 0;
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      this.className = Type.getObjectType(name).getClassName();
      this.access = access;
      this.superName = superName;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      if (!visible) {
        return null;
      }
      Optional<String> found = exports.stream().filter(x -> x.equals(desc)).findAny();
      if (found.isPresent()) {
        annotationName = desc;
        return new AbstractAnnotationVisitor() {
          @Override
          public void visit(String name, Object value) {
            annotationValue = (String) value;
          }
        };
      }
      return null;
    }

    @Override
    public void visitSource(String arg0, String arg1) {}

    @Override
    public void visitOuterClass(String arg0, String arg1, String arg2) {}

    @Override
    public MethodVisitor visitMethod(
        int arg0, String arg1, String arg2, String arg3, String[] arg4) {
      return null;
    }

    @Override
    public void visitInnerClass(String arg0, String arg1, String arg2, int arg3) {}

    @Override
    public FieldVisitor visitField(int arg0, String arg1, String arg2, String arg3, Object arg4) {
      return null;
    }

    @Override
    public void visitEnd() {}

    @Override
    public void visitAttribute(Attribute arg0) {}
  }

  private abstract static class AbstractAnnotationVisitor extends AnnotationVisitor {
    AbstractAnnotationVisitor() {
      super(Opcodes.ASM5);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String arg0, String arg1) {
      return null;
    }

    @Override
    public AnnotationVisitor visitArray(String arg0) {
      return null;
    }

    @Override
    public void visitEnum(String arg0, String arg1, String arg2) {}

    @Override
    public void visitEnd() {}
  }

  @Override
  public Optional<PluginEntry> getEntry(String resourcePath) throws IOException {
    JarEntry jarEntry = jarFile.getJarEntry(resourcePath);
    if (jarEntry == null || jarEntry.getSize() == 0) {
      return Optional.empty();
    }

    return Optional.of(resourceOf(jarEntry));
  }

  @Override
  public Enumeration<PluginEntry> entries() {
    return Collections.enumeration(
        Lists.transform(
            Collections.list(jarFile.entries()),
            jarEntry -> {
              try {
                return resourceOf(jarEntry);
              } catch (IOException e) {
                throw new IllegalArgumentException(
                    "Cannot convert jar entry " + jarEntry + " to a resource", e);
              }
            }));
  }

  @Override
  public InputStream getInputStream(PluginEntry entry) throws IOException {
    return jarFile.getInputStream(jarFile.getEntry(entry.getName()));
  }

  @Override
  public Manifest getManifest() throws IOException {
    return jarFile.getManifest();
  }

  private PluginEntry resourceOf(JarEntry jarEntry) throws IOException {
    return new PluginEntry(
        jarEntry.getName(),
        jarEntry.getTime(),
        Optional.of(jarEntry.getSize()),
        attributesOf(jarEntry));
  }

  private Map<Object, String> attributesOf(JarEntry jarEntry) throws IOException {
    Attributes attributes = jarEntry.getAttributes();
    if (attributes == null) {
      return Collections.emptyMap();
    }
    return Maps.transformEntries(
        attributes,
        new Maps.EntryTransformer<Object, Object, String>() {
          @Override
          public String transformEntry(Object key, Object value) {
            return (String) value;
          }
        });
  }
}
