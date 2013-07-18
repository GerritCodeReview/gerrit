// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.common.secure;

import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.annotations.Export;

import org.eclipse.jgit.util.IO;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class SecureStoreLoadUtil {
  public static class Metadata {
    public final File jarFile;
    private final String className;
    public final String storeName;

    public Metadata(String storeName, String className, File jarFile) {
      this.jarFile = jarFile;
      this.className = className;
      this.storeName = storeName;
    }

    public Class<? extends SecureStore> load() {
      return load(jarFile);
    }

    @SuppressWarnings("unchecked")
    public Class<? extends SecureStore> load(File pluginFile) {
      try {
        URL[] pluginJarUrls = new URL[] {pluginFile.toURI().toURL()};
        ClassLoader currentCL = Thread.currentThread().getContextClassLoader();
        final URLClassLoader newClassLoader =
            new URLClassLoader(pluginJarUrls, currentCL);
        Thread.currentThread().setContextClassLoader(newClassLoader);
        return (Class<? extends SecureStore>) newClassLoader.loadClass(className);
      } catch (Exception e) {
        throw new SecureStoreException(String.format(
            "Cannot load secure store implementation for %s", storeName), e);
      }
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this).add("storeName", storeName)
          .add("className", className).add("file", jarFile).toString();
    }

    @Override
    public int hashCode() {
      return storeName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Metadata && storeName.hashCode() == obj.hashCode();
    }
  }

  private static final String EXPORT = Type.getType(Export.class)
      .getDescriptor();
  private static final String SECURE_STORE_NAME = SecureStore.class.getName()
      .replaceAll("\\.", "/");

  public static Set<SecureStoreLoadUtil.Metadata> discover(File pluginFile)
      throws IOException {
    JarFile jarFile = new JarFile(pluginFile);
    Enumeration<JarEntry> entries = jarFile.entries();
    Set<SecureStoreLoadUtil.Metadata> resutl = Sets.newHashSet();
    while (entries.hasMoreElements()) {
      JarEntry entry = entries.nextElement();
      if (!entry.getName().endsWith(".class")) {
        continue;
      }
      SecureStoreVisitor visitor = new SecureStoreVisitor();
      new ClassReader(read(jarFile, entry)).accept(visitor,
          ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
              | ClassReader.SKIP_FRAMES);
      if (visitor.isSecureStore) {
        resutl.add(new Metadata(visitor.exportedAsName, visitor.storeClassName,
            pluginFile));
      }
    }
    return resutl;
  }

  private static byte[] read(JarFile jarFile, JarEntry entry) throws IOException {
    byte[] data = new byte[(int) entry.getSize()];
    InputStream in = jarFile.getInputStream(entry);
    try {
      IO.readFully(in, data, 0, data.length);
    } finally {
      in.close();
    }
    return data;
  }

  private static class SecureStoreVisitor extends ClassVisitor {
    String exportedAsName;
    String storeClassName;
    boolean isSecureStore;

    public SecureStoreVisitor() {
      super(Opcodes.ASM4);
    }

    @Override
    public void visit(int version, int access, String name, String signature,
        String superName, String[] interfaces) {
      storeClassName = name.replaceAll("/", ".");
      isSecureStore = Arrays.asList(interfaces).contains(SECURE_STORE_NAME);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      if (!isSecureStore) {
        return null;
      }
      if (visible && EXPORT.equals(desc)) {
        return new AnnotationVisitor(Opcodes.ASM4) {
          @Override
          public void visit(String name, Object value) {
            exportedAsName = (String) value;
          }
        };
      }
      return null;
    }
  }
}
