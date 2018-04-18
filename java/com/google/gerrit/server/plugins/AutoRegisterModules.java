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

import static com.google.gerrit.extensions.webui.JavaScriptPlugin.STATIC_INIT_JS;
import static com.google.gerrit.server.plugins.AutoRegisterUtil.calculateBindAnnotation;
import static com.google.gerrit.server.plugins.PluginGuiceEnvironment.is;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.extensions.annotations.Export;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.extensions.annotations.Listen;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.webui.JavaScriptPlugin;
import com.google.gerrit.extensions.webui.WebUiPlugin;
import com.google.gerrit.server.plugins.PluginContentScanner.ExtensionMetaData;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class AutoRegisterModules {
  private static final Logger log = LoggerFactory.getLogger(AutoRegisterModules.class);

  private final String pluginName;
  private final PluginGuiceEnvironment env;
  private final PluginContentScanner scanner;
  private final ClassLoader classLoader;
  private final ModuleGenerator sshGen;
  private final ModuleGenerator httpGen;

  private Set<Class<?>> sysSingletons;
  private ListMultimap<TypeLiteral<?>, Class<?>> sysListen;
  private String initJs;

  Module sysModule;
  Module sshModule;
  Module httpModule;

  AutoRegisterModules(
      String pluginName,
      PluginGuiceEnvironment env,
      PluginContentScanner scanner,
      ClassLoader classLoader) {
    this.pluginName = pluginName;
    this.env = env;
    this.scanner = scanner;
    this.classLoader = classLoader;
    this.sshGen = env.hasSshModule() ? env.newSshModuleGenerator() : new ModuleGenerator.NOP();
    this.httpGen = env.hasHttpModule() ? env.newHttpModuleGenerator() : new ModuleGenerator.NOP();
  }

  AutoRegisterModules discover() throws InvalidPluginException {
    sysSingletons = new HashSet<>();
    sysListen = LinkedListMultimap.create();
    initJs = null;

    sshGen.setPluginName(pluginName);
    httpGen.setPluginName(pluginName);

    scan();

    if (!sysSingletons.isEmpty() || !sysListen.isEmpty() || initJs != null) {
      sysModule = makeSystemModule();
    }
    sshModule = sshGen.create();
    httpModule = httpGen.create();
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
        if (initJs != null) {
          DynamicSet.bind(binder(), WebUiPlugin.class).toInstance(WebUiPlugin.js(initJs));
        }
      }
    };
  }

  private void scan() throws InvalidPluginException {
    Map<Class<? extends Annotation>, Iterable<ExtensionMetaData>> extensions =
        scanner.scan(pluginName, Arrays.asList(Export.class, Listen.class));
    for (ExtensionMetaData export : extensions.get(Export.class)) {
      export(export);
    }
    for (ExtensionMetaData listener : extensions.get(Listen.class)) {
      listen(listener);
    }
    if (env.hasHttpModule()) {
      exportInitJs();
    }
  }

  private void exportInitJs() {
    try {
      if (scanner.getEntry(STATIC_INIT_JS).isPresent()) {
        initJs = STATIC_INIT_JS;
      }
    } catch (IOException e) {
      log.warn(
          String.format(
              "Cannot access %s from plugin %s: "
                  + "JavaScript auto-discovered plugin will not be registered",
              STATIC_INIT_JS, pluginName),
          e);
    }
  }

  private void export(ExtensionMetaData def) throws InvalidPluginException {
    Class<?> clazz;
    try {
      clazz = Class.forName(def.className, false, classLoader);
    } catch (ClassNotFoundException err) {
      throw new InvalidPluginException(
          String.format("Cannot load %s with @Export(\"%s\")", def.className, def.annotationValue),
          err);
    }

    Export export = clazz.getAnnotation(Export.class);
    if (export == null) {
      log.warn(
          String.format(
              "In plugin %s asm incorrectly parsed %s with @Export(\"%s\")",
              pluginName, clazz.getName(), def.annotationValue));
      return;
    }

    if (is("org.apache.sshd.server.Command", clazz)) {
      sshGen.export(export, clazz);
    } else if (is("javax.servlet.http.HttpServlet", clazz)) {
      httpGen.export(export, clazz);
      listen(clazz, clazz);
    } else {
      int cnt = sysListen.size();
      listen(clazz, clazz);
      if (cnt == sysListen.size()) {
        // If no bindings were recorded, the extension isn't recognized.
        throw new InvalidPluginException(
            String.format(
                "Class %s with @Export(\"%s\") not supported", clazz.getName(), export.value()));
      }
    }
  }

  private void listen(ExtensionMetaData def) throws InvalidPluginException {
    Class<?> clazz;
    try {
      clazz = Class.forName(def.className, false, classLoader);
    } catch (ClassNotFoundException err) {
      throw new InvalidPluginException(
          String.format("Cannot load %s with @Listen", def.className), err);
    }

    Listen listen = clazz.getAnnotation(Listen.class);
    if (listen != null) {
      listen(clazz, clazz);
    } else {
      log.warn(
          String.format(
              "In plugin %s asm incorrectly parsed %s with @Listen", pluginName, clazz.getName()));
    }
  }

  private void listen(java.lang.reflect.Type type, Class<?> clazz) throws InvalidPluginException {
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
            throw new InvalidPluginException(
                String.format(
                    "Class %s requires @Export(\"name\") annotation for %s",
                    clazz.getName(), rawType.getName()));
          }
          sysSingletons.add(clazz);
          sysListen.put(tl, clazz);
          httpGen.listen(tl, clazz);
          sshGen.listen(tl, clazz);
        } else {
          throw new InvalidPluginException(
              String.format(
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
