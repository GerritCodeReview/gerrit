// Copyright (C) 2012 The Android Open Source Project
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

import static com.google.gerrit.server.plugins.PluginGuiceEnvironment.is;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.annotations.Export;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.extensions.annotations.Listen;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.UniqueAnnotations;

import org.eclipse.jgit.util.IO;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

class AutoRegisterModules {
  private static final int SKIP_ALL = ClassReader.SKIP_CODE
      | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;
  private final String pluginName;
  private final PluginGuiceEnvironment env;
  private final JarFile jarFile;
  private final ClassLoader classLoader;
  private final ModuleGenerator sshGen;
  private final ModuleGenerator httpGen;

  private Set<Class<?>> sysSingletons;
  private Map<TypeLiteral<?>, Class<?>> sysListen;

  Module sysModule;
  Module sshModule;
  Module httpModule;

  AutoRegisterModules(String pluginName,
      PluginGuiceEnvironment env,
      JarFile jarFile,
      ClassLoader classLoader) {
    this.pluginName = pluginName;
    this.env = env;
    this.jarFile = jarFile;
    this.classLoader = classLoader;
    this.sshGen = env.hasSshModule() ? env.newSshModuleGenerator() : null;
    this.httpGen = env.hasHttpModule() ? env.newHttpModuleGenerator() : null;
  }

  AutoRegisterModules discover() throws InvalidPluginException {
    sysSingletons = Sets.newHashSet();
    sysListen = Maps.newHashMap();

    if (sshGen != null) {
      sshGen.setPluginName(pluginName);
    }
    if (httpGen != null) {
      httpGen.setPluginName(pluginName);
    }

    scan();

    if (!sysSingletons.isEmpty() || !sysListen.isEmpty()) {
      sysModule = makeSystemModule();
    }
    if (sshGen != null) {
      sshModule = sshGen.create();
    }
    if (httpGen != null) {
      httpModule = httpGen.create();
    }
    return this;
  }

  private Module makeSystemModule() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        for (Class<?> clazz : sysSingletons) {
          bind(clazz).in(Scopes.SINGLETON);
        }
        for (Map.Entry<TypeLiteral<?>, Class<?>> e : sysListen.entrySet()) {
          @SuppressWarnings("unchecked")
          TypeLiteral<Object> type = (TypeLiteral<Object>) e.getKey();

          @SuppressWarnings("unchecked")
          Class<Object> impl = (Class<Object>) e.getValue();

          Annotation n = impl.getAnnotation(Export.class);
          if (n == null) {
            n = impl.getAnnotation(javax.inject.Named.class);
          }
          if (n == null) {
            n = impl.getAnnotation(com.google.inject.name.Named.class);
          }
          if (n == null) {
            n = UniqueAnnotations.create();
          }
          bind(type).annotatedWith(n).to(impl);
        }
      }
    };
  }

  private void scan() throws InvalidPluginException {
    Enumeration<JarEntry> e = jarFile.entries();
    while (e.hasMoreElements()) {
      JarEntry entry = e.nextElement();
      if (skip(entry)) {
        continue;
      }

      ClassData def = new ClassData();
      try {
        new ClassReader(read(entry)).accept(def, SKIP_ALL);
      } catch (IOException err) {
        throw new InvalidPluginException("Cannot auto-register", err);
      } catch (RuntimeException err) {
        PluginLoader.log.warn(String.format(
            "Plugin %s has invaild class file %s inside of %s",
            pluginName, entry.getName(), jarFile.getName()), err);
        continue;
      }

      if (def.exportedAsName != null) {
        if (def.isConcrete()) {
          export(def);
        } else {
          PluginLoader.log.warn(String.format(
              "Plugin %s tries to @Export(\"%s\") abstract class %s",
              pluginName, def.exportedAsName, def.className));
        }
      } else if (def.listen) {
        if (def.isConcrete()) {
          listen(def);
        } else {
          PluginLoader.log.warn(String.format(
              "Plugin %s tries to @Listen abstract class %s",
              pluginName, def.className));
        }
      }
    }
  }

  private void export(ClassData def) throws InvalidPluginException {
    Class<?> clazz;
    try {
      clazz = Class.forName(def.className, false, classLoader);
    } catch (ClassNotFoundException err) {
      throw new InvalidPluginException(String.format(
          "Cannot load %s with @Export(\"%s\")",
          def.className, def.exportedAsName), err);
    }

    Export export = clazz.getAnnotation(Export.class);
    if (export == null) {
      PluginLoader.log.warn(String.format(
          "In plugin %s asm incorrectly parsed %s with @Export(\"%s\")",
          pluginName, clazz.getName(), def.exportedAsName));
      return;
    }

    if (is("org.apache.sshd.server.Command", clazz)) {
      if (sshGen != null) {
        sshGen.export(export, clazz);
      }
    } else if (is("javax.servlet.http.HttpServlet", clazz)) {
      if (httpGen != null) {
        httpGen.export(export, clazz);
        listen(clazz, clazz);
      }
    } else {
      int cnt = sysListen.size();
      listen(clazz, clazz);
      if (cnt == sysListen.size()) {
        // If no bindings were recorded, the extension isn't recognized.
        throw new InvalidPluginException(String.format(
            "Class %s with @Export(\"%s\") not supported",
            clazz.getName(), export.value()));
      }
    }
  }

  private void listen(ClassData def) throws InvalidPluginException {
    Class<?> clazz;
    try {
      clazz = Class.forName(def.className, false, classLoader);
    } catch (ClassNotFoundException err) {
      throw new InvalidPluginException(String.format(
          "Cannot load %s with @Listen",
          def.className), err);
    }

    Listen listen = clazz.getAnnotation(Listen.class);
    if (listen != null) {
      listen(clazz, clazz);
    } else {
      PluginLoader.log.warn(String.format(
          "In plugin %s asm incorrectly parsed %s with @Listen",
          pluginName, clazz.getName()));
    }
  }

  private void listen(java.lang.reflect.Type type, Class<?> clazz)
      throws InvalidPluginException {
    while (type != null) {
      Class<?> rawType;
      if (type instanceof ParameterizedType) {
        rawType = (Class<?>) ((ParameterizedType) type).getRawType();
      } else if (type instanceof Class) {
        rawType = (Class<?>) type;
      } else {
        return;
      }

      if (rawType.getAnnotation(ExtensionPoint.class) != null) {
        TypeLiteral<?> tl = TypeLiteral.get(type);
        if (env.hasDynamicSet(tl)) {
          sysSingletons.add(clazz);
          sysListen.put(tl, clazz);
        } else if (env.hasDynamicMap(tl)) {
          if (clazz.getAnnotation(Export.class) == null) {
            throw new InvalidPluginException(String.format(
                "Class %s requires @Export(\"name\") annotation for %s",
                clazz.getName(), rawType.getName()));
          }
          sysSingletons.add(clazz);
          sysListen.put(tl, clazz);
        } else {
          throw new InvalidPluginException(String.format(
              "Cannot register %s, server does not accept %s",
              clazz.getName(), rawType.getName()));
        }
        return;
      }

      java.lang.reflect.Type[] interfaces = rawType.getGenericInterfaces();
      if (interfaces != null) {
        for (java.lang.reflect.Type i : interfaces) {
          listen(i, clazz);
        }
      }

      type = rawType.getGenericSuperclass();
    }
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

  private byte[] read(JarEntry entry) throws IOException {
    byte[] data = new byte[(int) entry.getSize()];
    InputStream in = jarFile.getInputStream(entry);
    try {
      IO.readFully(in, data, 0, data.length);
    } finally {
      in.close();
    }
    return data;
  }

  private static class ClassData implements ClassVisitor {
    private static final String EXPORT = Type.getType(Export.class).getDescriptor();
    private static final String LISTEN = Type.getType(Listen.class).getDescriptor();

    String className;
    int access;
    String exportedAsName;
    boolean listen;

    boolean isConcrete() {
      return (access & Opcodes.ACC_ABSTRACT) == 0
          && (access & Opcodes.ACC_INTERFACE) == 0;
    }

    @Override
    public void visit(int version, int access, String name, String signature,
        String superName, String[] interfaces) {
      this.className = Type.getObjectType(name).getClassName();
      this.access = access;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      if (visible && EXPORT.equals(desc)) {
        return new AbstractAnnotationVisitor() {
          @Override
          public void visit(String name, Object value) {
            exportedAsName = (String) value;
          }
        };
      }
      if (visible && LISTEN.equals(desc)) {
        listen = true;
        return null;
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

  private static abstract class AbstractAnnotationVisitor implements
      AnnotationVisitor {
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
