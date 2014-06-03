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

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.collect.Iterables.transform;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import org.eclipse.jgit.util.IO;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarScanner {
  private static final int SKIP_ALL = ClassReader.SKIP_CODE
      | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;
  private static final Function<ClassData, ExtensionMetaData> CLASS_DATA_TO_EXTENSION_META_DATA =
      new Function<ClassData, ExtensionMetaData>() {
        @Override
        public ExtensionMetaData apply(ClassData classData) {
          return new ExtensionMetaData(classData.className,
              classData.annotationValue, classData.interfaces);
        }
      };
  private static final Function<String, String> TO_JAVA_QUALIFIED_CLASS_NAME =
      new Function<String, String>() {
        @Override
        public String apply(String in) {
          return in.replace("/", ".");
        }
      };

  public static class ExtensionMetaData {
    private final String className;
    private final String annotationValue;
    private final Iterable<String> interfaces;

    private ExtensionMetaData(String className, String annotationValue, Iterable<String> interfaces) {
      this.className = className;
      this.annotationValue = annotationValue;
      this.interfaces = interfaces;
    }

    public String getAnnotationValue() {
      return annotationValue;
    }

    public String getClassName() {
      return className;
    }

    public Iterable<String> getInterfaces() {
      return interfaces;
    }
  }

  public static Iterable<ExtensionMetaData> scan(File file, String pluginName,
      Class<? extends Annotation> annotation) throws InvalidPluginException,
      IOException {
    Map<Class<? extends Annotation>, Iterable<ExtensionMetaData>> result =
        scan(new JarFile(file), pluginName,
            Arrays.<Class<? extends Annotation>> asList(annotation));
    return result.get(annotation);
  }

  public static Map<Class<? extends Annotation>, Iterable<ExtensionMetaData>> scan(
      JarFile jarFile, String pluginName,
      Iterable<Class<? extends Annotation>> annotations)
      throws InvalidPluginException {
    Set<String> descriptors = Sets.newHashSet();
    Multimap<String, JarScanner.ClassData> rawMap = ArrayListMultimap.create();
    Map<Class<? extends Annotation>, String> classObjToClassDescr =
        Maps.newHashMap();

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
        PluginLoader.log.warn(String.format(
            "Plugin %s has invaild class file %s inside of %s", pluginName,
            entry.getName(), jarFile.getName()), err);
        continue;
      }

      if (def.isConcrete()) {
        if (!Strings.isNullOrEmpty(def.annotationName)) {
          rawMap.put(def.annotationName, def);
        }
      } else {
        PluginLoader.log.warn(String.format(
            "Plugin %s tries to @%s(\"%s\") abstract class %s", pluginName,
            def.annotationName, def.annotationValue, def.className));
      }
    }

    ImmutableMap.Builder<Class<? extends Annotation>, Iterable<ExtensionMetaData>> result =
        ImmutableMap.builder();

    for (Class<? extends Annotation> annotoation : annotations) {
      String descr = classObjToClassDescr.get(annotoation);
      Collection<ClassData> discoverdData = rawMap.get(descr);
      Collection<ClassData> values =
          firstNonNull(discoverdData, Collections.<ClassData> emptySet());

      result.put(annotoation,
          transform(values, CLASS_DATA_TO_EXTENSION_META_DATA));
    }

    return result.build();
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

  private static byte[] read(JarFile jarFile, JarEntry entry)
      throws IOException {
    byte[] data = new byte[(int) entry.getSize()];
    InputStream in = jarFile.getInputStream(entry);
    try {
      IO.readFully(in, data, 0, data.length);
    } finally {
      in.close();
    }
    return data;
  }

  public static class ClassData extends ClassVisitor {
    int access;
    String className;
    String annotationName;
    String annotationValue;
    Iterable<String> interfaces;
    Iterable<String> exports;

    private ClassData(Iterable<String> exports) {
      super(Opcodes.ASM4);
      this.exports = exports;
    }

    boolean isConcrete() {
      return (access & Opcodes.ACC_ABSTRACT) == 0
          && (access & Opcodes.ACC_INTERFACE) == 0;
    }

    @Override
    public void visit(int version, int access, String name, String signature,
        String superName, String[] interfaces) {
      this.interfaces =
          Iterables.transform(Sets.newHashSet(interfaces),
              TO_JAVA_QUALIFIED_CLASS_NAME);
      this.className = Type.getObjectType(name).getClassName();
      this.access = access;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      Optional<String> found =
          Iterables.tryFind(exports, Predicates.equalTo(desc));
      if (visible && found.isPresent()) {
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
    public void visitSource(String arg0, String arg1) {
    }

    @Override
    public void visitOuterClass(String arg0, String arg1, String arg2) {
    }

    @Override
    public MethodVisitor visitMethod(int arg0, String arg1, String arg2,
        String arg3, String[] arg4) {
      return null;
    }

    @Override
    public void visitInnerClass(String arg0, String arg1, String arg2, int arg3) {
    }

    @Override
    public FieldVisitor visitField(int arg0, String arg1, String arg2,
        String arg3, Object arg4) {
      return null;
    }

    @Override
    public void visitEnd() {
    }

    @Override
    public void visitAttribute(Attribute arg0) {
    }
  }

  private static abstract class AbstractAnnotationVisitor extends
      AnnotationVisitor {
    AbstractAnnotationVisitor() {
      super(Opcodes.ASM4);
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
    public void visitEnum(String arg0, String arg1, String arg2) {
    }

    @Override
    public void visitEnd() {
    }
  }
}
