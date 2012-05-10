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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.inject.Inject;

/**
 * Tracks Guice bindings that should be exposed to loaded plugins.
 * <p>
 * This is an internal implementation detail of how the main server is able to
 * export its explicit Guice bindings to tightly coupled plugins, giving them
 * access to singletons and request scoped resources just like any core code.
 */
@Singleton
public class PluginGuiceEnvironment {
  private final Injector sysInjector;
  private final CopyConfigModule copyConfigModule;
  private final List<StartPluginListener> onStart;
  private final List<ReloadPluginListener> onReload;
  private final List<RemovePluginListener> onRemove;
  private Module sysModule;
  private Module sshModule;
  private Module httpModule;

  private Provider<ModuleGenerator> sshGen;
  private Provider<ModuleGenerator> httpGen;

  @Inject
  PluginGuiceEnvironment(Injector sysInjector, CopyConfigModule ccm) {
    this.sysInjector = sysInjector;
    this.copyConfigModule = ccm;

    onStart = new CopyOnWriteArrayList<StartPluginListener>();
    onStart.addAll(listeners(sysInjector, StartPluginListener.class));

    onReload = new CopyOnWriteArrayList<ReloadPluginListener>();
    onReload.addAll(listeners(sysInjector, ReloadPluginListener.class));

    onRemove  = new CopyOnWriteArrayList<RemovePluginListener>();
    onRemove.addAll(listeners(sysInjector, RemovePluginListener.class));
  }

  Module getSysModule() {
    return sysModule;
  }

  public void setCfgInjector(Injector cfgInjector) {
    final Module cm = copy(cfgInjector);
    final Module sm = copy(sysInjector);
    sysModule = new AbstractModule() {
      @Override
      protected void configure() {
        install(copyConfigModule);
        install(cm);
        install(sm);
      }
    };
  }

  public void setSshInjector(Injector injector) {
    sshModule = copy(injector);
    sshGen = injector.getProvider(ModuleGenerator.class);
    onStart.addAll(listeners(injector, StartPluginListener.class));
    onReload.addAll(listeners(injector, ReloadPluginListener.class));
  }

  boolean hasSshModule() {
    return sshModule != null;
  }

  Module getSshModule() {
    return sshModule;
  }

  ModuleGenerator newSshModuleGenerator() {
    return sshGen.get();
  }

  public void setHttpInjector(Injector injector) {
    httpModule = copy(injector);
    httpGen = injector.getProvider(ModuleGenerator.class);
    onStart.addAll(listeners(injector, StartPluginListener.class));
    onReload.addAll(listeners(injector, ReloadPluginListener.class));
  }

  boolean hasHttpModule() {
    return httpModule != null;
  }

  Module getHttpModule() {
    return httpModule;
  }

  ModuleGenerator newHttpModuleGenerator() {
    return httpGen.get();
  }

  void onStartPlugin(Plugin plugin) {
    for (StartPluginListener l : onStart) {
      l.onStartPlugin(plugin);
    }
  }

  void onReloadPlugin(Plugin oldPlugin, Plugin newPlugin) {
    for (ReloadPluginListener l : onReload) {
      l.onReloadPlugin(oldPlugin, newPlugin);
    }
  }

  void onRemovePlugin(Plugin plugin) {
    for (RemovePluginListener l : onRemove) {
      l.onRemovePlugin(plugin);
    }
  }
  private static <T> List<T> listeners(Injector src, Class<T> type) {
    List<Binding<T>> bindings = src.findBindingsByType(TypeLiteral.get(type));
    List<T> found = Lists.newArrayListWithCapacity(bindings.size());
    for (Binding<T> b : bindings) {
      found.add(b.getProvider().get());
    }
    return found;
  }

  private static Module copy(Injector src) {
    final Map<Key<?>, Binding<?>> bindings = Maps.newLinkedHashMap();
    for (Map.Entry<Key<?>, Binding<?>> e : src.getBindings().entrySet()) {
      if (shouldCopy(e.getKey())) {
        bindings.put(e.getKey(), e.getValue());
      }
    }
    bindings.remove(Key.get(Injector.class));
    bindings.remove(Key.get(java.util.logging.Logger.class));

    return new AbstractModule() {
      @SuppressWarnings("unchecked")
      @Override
      protected void configure() {
        for (Map.Entry<Key<?>, Binding<?>> e : bindings.entrySet()) {
          Key<Object> k = (Key<Object>) e.getKey();
          Binding<Object> b = (Binding<Object>) e.getValue();
          bind(k).toProvider(b.getProvider());
        }
      }
    };
  }

  private static boolean shouldCopy(Key<?> key) {
    Class<?> type = key.getTypeLiteral().getRawType();
    if (LifecycleListener.class.isAssignableFrom(type)) {
      return false;
    }
    if (StartPluginListener.class.isAssignableFrom(type)) {
      return false;
    }

    if (type.getName().startsWith("com.google.inject.")) {
      return false;
    }

    if (is("org.apache.sshd.server.Command", type)) {
      return false;
    }

    if (is("javax.servlet.Filter", type)) {
      return false;
    }
    if (is("javax.servlet.ServletContext", type)) {
      return false;
    }
    if (is("javax.servlet.ServletRequest", type)) {
      return false;
    }
    if (is("javax.servlet.ServletResponse", type)) {
      return false;
    }
    if (is("javax.servlet.http.HttpServlet", type)) {
      return false;
    }
    if (is("javax.servlet.http.HttpServletRequest", type)) {
      return false;
    }
    if (is("javax.servlet.http.HttpServletResponse", type)) {
      return false;
    }
    if (is("javax.servlet.http.HttpSession", type)) {
      return false;
    }
    if (Map.class.isAssignableFrom(type)
        && key.getAnnotationType() != null
        && "com.google.inject.servlet.RequestParameters"
            .equals(key.getAnnotationType().getName())) {
      return false;
    }
    if (type.getName().startsWith("com.google.gerrit.httpd.GitOverHttpServlet$")) {
      return false;
    }
    return true;
  }

  static boolean is(String name, Class<?> type) {
    while (type != null) {
      if (name.equals(type.getName())) {
        return true;
      }

      Class<?>[] interfaces = type.getInterfaces();
      if (interfaces != null) {
        for (Class<?> i : interfaces) {
          if (is(name, i)) {
            return true;
          }
        }
      }

      type = type.getSuperclass();
    }
    return false;
  }
}
