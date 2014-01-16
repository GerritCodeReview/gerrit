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

import static com.google.gerrit.server.plugins.AutoRegisterUtil.calculateBindAnnotation;
import static com.google.gerrit.server.plugins.PluginGuiceEnvironment.is;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.annotations.Export;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.extensions.annotations.Listen;
import com.google.gerrit.server.plugins.JarScanner.ExtensionMetaData;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

class AutoRegisterModules {
  private final String pluginName;
  private final PluginGuiceEnvironment env;
  private final JarFile jarFile;
  private final ClassLoader classLoader;
  private final ModuleGenerator sshGen;
  private final ModuleGenerator httpGen;

  private Set<Class<?>> sysSingletons;
  private Multimap<TypeLiteral<?>, Class<?>> sysListen;

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
    sysListen = LinkedListMultimap.create();

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
        for (Map.Entry<TypeLiteral<?>, Class<?>> e : sysListen.entries()) {
          @SuppressWarnings("unchecked")
          TypeLiteral<Object> type = (TypeLiteral<Object>) e.getKey();

          @SuppressWarnings("unchecked")
          Class<Object> impl = (Class<Object>) e.getValue();

          Annotation n = calculateBindAnnotation(impl);
          bind(type).annotatedWith(n).to(impl);
        }
      }
    };
  }

  private void scan() throws InvalidPluginException {
    Map<Class<? extends Annotation>, Iterable<ExtensionMetaData>> extensions =
        JarScanner.scan(jarFile, pluginName, Arrays.asList(Export.class, Listen.class));
    for (ExtensionMetaData export : extensions.get(Export.class)) {
      export(export);
    }
    for (ExtensionMetaData listener : extensions.get(Listen.class)) {
      listen(listener);
    }
  }

  private void export(ExtensionMetaData def) throws InvalidPluginException {
    Class<?> clazz;
    try {
      clazz = Class.forName(def.getClassName(), false, classLoader);
    } catch (ClassNotFoundException err) {
      throw new InvalidPluginException(String.format(
          "Cannot load %s with @Export(\"%s\")",
          def.getClassName(), def.getAnnotationValue()), err);
    }

    Export export = clazz.getAnnotation(Export.class);
    if (export == null) {
      PluginLoader.log.warn(String.format(
          "In plugin %s asm incorrectly parsed %s with @Export(\"%s\")",
          pluginName, clazz.getName(), def.getAnnotationValue()));
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

  private void listen(ExtensionMetaData def) throws InvalidPluginException {
    Class<?> clazz;
    try {
      clazz = Class.forName(def.getClassName(), false, classLoader);
    } catch (ClassNotFoundException err) {
      throw new InvalidPluginException(String.format(
          "Cannot load %s with @Listen",
          def.getClassName()), err);
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
        if (env.hasDynamicItem(tl)) {
          sysSingletons.add(clazz);
          sysListen.put(tl, clazz);
          httpGen.listen(tl, clazz);
          sshGen.listen(tl, clazz);
        } else if (env.hasDynamicSet(tl)) {
          sysSingletons.add(clazz);
          sysListen.put(tl, clazz);
          httpGen.listen(tl, clazz);
          sshGen.listen(tl, clazz);
        } else if (env.hasDynamicMap(tl)) {
          if (clazz.getAnnotation(Export.class) == null) {
            throw new InvalidPluginException(String.format(
                "Class %s requires @Export(\"name\") annotation for %s",
                clazz.getName(), rawType.getName()));
          }
          sysSingletons.add(clazz);
          sysListen.put(tl, clazz);
          httpGen.listen(tl, clazz);
          sshGen.listen(tl, clazz);
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
}
