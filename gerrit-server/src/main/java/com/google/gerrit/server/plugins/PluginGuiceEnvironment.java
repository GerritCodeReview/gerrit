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
  private final List<StartPluginListener> listeners;
  private Module sysModule;
  private Module sshModule;

  @Inject
  PluginGuiceEnvironment(Injector sysInjector, CopyConfigModule ccm) {
    this.sysInjector = sysInjector;
    this.copyConfigModule = ccm;
    this.listeners = new CopyOnWriteArrayList<StartPluginListener>();
    this.listeners.addAll(getListeners(sysInjector));
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

  public void setSshInjector(Injector sshInjector) {
    sshModule = copy(sshInjector);
    listeners.addAll(getListeners(sshInjector));
  }

  boolean hasSshModule() {
    return sshModule != null;
  }

  Module getSshModule() {
    return sshModule;
  }

  void onStartPlugin(Plugin plugin) {
    for (StartPluginListener l : listeners) {
      l.onStartPlugin(plugin);
    }
  }

  private static List<StartPluginListener> getListeners(Injector src) {
    List<Binding<StartPluginListener>> bindings =
        src.findBindingsByType(new TypeLiteral<StartPluginListener>() {});
    List<StartPluginListener> found =
        Lists.newArrayListWithCapacity(bindings.size());
    for (Binding<StartPluginListener> b : bindings) {
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
    if (type == LifecycleListener.class) {
      return false;
    }
    if (type == StartPluginListener.class) {
      return false;
    }
    if ("org.apache.sshd.server.Command".equals(type.getName())) {
      return false;
    }
    return true;
  }
}
