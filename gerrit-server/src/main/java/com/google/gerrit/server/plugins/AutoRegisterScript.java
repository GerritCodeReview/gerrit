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
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.util.Map;
import java.util.Set;

class AutoRegisterScript {
  private final String pluginName;
  private final PluginGuiceEnvironment env;
  private final ModuleGenerator sshGen;
  private final ModuleGenerator httpGen;

  private Set<Class<?>> sysSingletons;
  private Multimap<TypeLiteral<?>, Class<?>> sysListen;
  private final Class<?> scriptClass;
  private ScriptingPlugin scriptPlugin;

  Module sysModule;
  Module sshModule;
  Module httpModule;

  AutoRegisterScript(Class<?> scriptClass, ScriptingPlugin scriptPlugin) {
    this.scriptClass = scriptClass;
    this.scriptPlugin = scriptPlugin;
    this.pluginName = scriptPlugin.getName();
    this.env = scriptPlugin.getGuiceEnvironment();
    this.sshGen = env.hasSshModule() ? env.newSshModuleGenerator() : null;
    this.httpGen = env.hasHttpModule() ? env.newHttpModuleGenerator() : null;
  }

  void register() throws InvalidPluginException {
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
      scriptPlugin.setSysInjector(Guice.createInjector(env.getSysModule(),
          sysModule));
    }
    if (sshGen != null) {
      sshModule = sshGen.create();
      scriptPlugin.setSshInjector(Guice.createInjector(env.getSysModule(),
          env.getSshModule(), sshModule));
    }
    if (httpGen != null) {
      httpModule = httpGen.create();
      scriptPlugin.setHttpInjector(Guice.createInjector(env.getSysModule(),
          env.getSysModule(), httpModule));
    }
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
    export(scriptClass.getAnnotation(Export.class));
    listen(scriptClass.getAnnotation(Listen.class));
  }

  private void export(Export export) throws InvalidPluginException {
    if (export == null) {
      return;
    }

    if (is("org.apache.sshd.server.Command", scriptClass)) {
      if (sshGen != null) {
        sshGen.export(export, scriptClass);
      }
    } else if (is("javax.servlet.http.HttpServlet", scriptClass)) {
      if (httpGen != null) {
        httpGen.export(export, scriptClass);
        listen(scriptClass, scriptClass);
      }
    } else {
      int cnt = sysListen.size();
      listen(scriptClass, scriptClass);
      if (cnt == sysListen.size()) {
        // If no bindings were recorded, the extension isn't recognized.
        throw new InvalidPluginException(String.format(
            "Class %s with @Export(\"%s\") not supported",
            scriptClass.getName(), export.value()));
      }
    }
  }

  private void listen(Listen listen) throws InvalidPluginException {
    if (listen == null) {
      return;
    }

    listen(scriptClass, scriptClass);
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
              "Cannot register %s, server does not accept %s", clazz.getName(),
              rawType.getName()));
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
