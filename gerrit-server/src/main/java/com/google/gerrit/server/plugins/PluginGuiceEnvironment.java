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

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.registration.ReloadableRegistrationHandle;
import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.UniqueAnnotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;
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

  private Map<Class<?>, DynamicSet<?>> sysSets;
  private Map<Class<?>, DynamicSet<?>> sshSets;
  private Map<Class<?>, DynamicSet<?>> httpSets;

  private Map<Class<?>, DynamicMap<?>> sysMaps;
  private Map<Class<?>, DynamicMap<?>> sshMaps;
  private Map<Class<?>, DynamicMap<?>> httpMaps;

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

    sysSets = dynamicSetsOf(sysInjector);
    sysMaps = dynamicMapsOf(sysInjector);
  }

  boolean hasDynamicSet(Class<?> type) {
    return sysSets.containsKey(type)
        || (sshSets != null && sshSets.containsKey(type))
        || (httpSets != null && httpSets.containsKey(type));
  }

  boolean hasDynamicMap(Class<?> type) {
    return sysMaps.containsKey(type)
        || (sshMaps != null && sshMaps.containsKey(type))
        || (httpMaps != null && httpMaps.containsKey(type));
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
    sshSets = dynamicSetsOf(injector);
    sshMaps = dynamicMapsOf(injector);
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
    httpSets = dynamicSetsOf(injector);
    httpMaps = dynamicMapsOf(injector);
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

    attachSet(sysSets, plugin.getSysInjector(), plugin);
    attachSet(sshSets, plugin.getSshInjector(), plugin);
    attachSet(httpSets, plugin.getHttpInjector(), plugin);

    attachMap(sysMaps, plugin.getSysInjector(), plugin);
    attachMap(sshMaps, plugin.getSshInjector(), plugin);
    attachMap(httpMaps, plugin.getHttpInjector(), plugin);
  }

  private void attachSet(Map<Class<?>, DynamicSet<?>> sets,
      @Nullable Injector src,
      Plugin plugin) {
    if (src != null && sets != null && !sets.isEmpty()) {
      for (Map.Entry<Class<?>, DynamicSet<?>> e : sets.entrySet()) {
        @SuppressWarnings("unchecked")
        DynamicSet<Object> set = (DynamicSet<Object>) e.getValue();
        for (Binding<?> b : bindings(src, e.getKey())) {
          plugin.add(set.add(b.getKey(), b.getProvider().get()));
        }
      }
    }
  }

  private void attachMap(Map<Class<?>, DynamicMap<?>> maps,
      @Nullable Injector src,
      Plugin plugin) {
    if (src != null && maps != null && !maps.isEmpty()) {
      for (Map.Entry<Class<?>, DynamicMap<?>> e : maps.entrySet()) {
        @SuppressWarnings("unchecked")
        PrivateInternals_DynamicMapImpl<Object> set =
            (PrivateInternals_DynamicMapImpl<Object>) e.getValue();
        for (Binding<?> b : bindings(src, e.getKey())) {
          plugin.add(set.put(
              plugin.getName(),
              b.getKey(),
              b.getProvider().get()));
        }
      }
    }
  }

  void onReloadPlugin(Plugin oldPlugin, Plugin newPlugin) {
    for (ReloadPluginListener l : onReload) {
      l.onReloadPlugin(oldPlugin, newPlugin);
    }

    // Index all old registrations by the raw type. These may be replaced
    // during the reattach calls below. Any that are not replaced will be
    // removed when the old plugin does its stop routine.
    ListMultimap<Class<?>, ReloadableRegistrationHandle<?>> old =
        LinkedListMultimap.create();
    for (ReloadableRegistrationHandle<?> h : oldPlugin.getReloadableHandles()) {
      old.put(h.getKey().getTypeLiteral().getRawType(), h);
    }

    reattachMap(old, sysMaps, newPlugin.getSysInjector(), newPlugin);
    reattachMap(old, sshMaps, newPlugin.getSshInjector(), newPlugin);
    reattachMap(old, httpMaps, newPlugin.getHttpInjector(), newPlugin);

    reattachSet(old, sysSets, newPlugin.getSysInjector(), newPlugin);
    reattachSet(old, sshSets, newPlugin.getSshInjector(), newPlugin);
    reattachSet(old, httpSets, newPlugin.getHttpInjector(), newPlugin);
  }

  /** Type used to declare unique annotations. Guice hides this, so extract it. */
  private static final Class<?> UNIQUE_ANNOTATION =
      UniqueAnnotations.create().getClass();

  private void reattachSet(
      ListMultimap<Class<?>, ReloadableRegistrationHandle<?>> oldHandles,
      Map<Class<?>, DynamicSet<?>> sets,
      @Nullable Injector src,
      Plugin newPlugin) {
    if (src == null || sets == null || sets.isEmpty()) {
      return;
    }

    for (Map.Entry<Class<?>, DynamicSet<?>> e : sets.entrySet()) {
      @SuppressWarnings("unchecked")
      DynamicSet<Object> set = (DynamicSet<Object>) e.getValue();

      // Index all old handles that match this DynamicSet<T> keyed by
      // annotations. Ignore the unique annotations, thereby favoring
      // the @Named annotations or some other non-unique naming.
      List<ReloadableRegistrationHandle<?>> old = oldHandles.get(e.getKey());
      Map<Annotation, ReloadableRegistrationHandle<?>> am = Maps.newHashMap();
      Iterator<ReloadableRegistrationHandle<?>> oi = old.iterator();
      while (oi.hasNext()) {
        ReloadableRegistrationHandle<?> h = oi.next();
        if (h.getKey().getAnnotation() != null
            && !UNIQUE_ANNOTATION.isInstance(h.getKey().getAnnotation())) {
          am.put(h.getKey().getAnnotation(), h);
          oi.remove();
        }
      }

      // Replace old handles with new bindings, favoring cases where there
      // is an exact match on an @Named annotation. If there is no match
      // pick any handle and replace it. We generally expect only one
      // handle of each DynamicSet type when using unique annotations, but
      // possibly multiple ones if @Named was used. Plugin authors that want
      // atomic replacement across reloads should use @Named annotations with
      // stable names that do not change across plugin versions to ensure the
      // handles are swapped correctly.
      oi = old.iterator();
      for (Binding<?> b : bindings(src, e.getKey())) {
        Key<?> key = b.getKey();
        ReloadableRegistrationHandle<?> h = am.remove(key.getAnnotation());
        if (h != null) {
          newPlugin.add(replace(h, b));
          continue;
        }

        if (oi.hasNext()) {
          h = oi.next();
          oi.remove();
          newPlugin.add(replace(h, b));
          continue;
        }

        newPlugin.add(set.add(b.getKey(), b.getProvider().get()));
      }
    }
  }

  private void reattachMap(
      ListMultimap<Class<?>, ReloadableRegistrationHandle<?>> oldHandles,
      Map<Class<?>, DynamicMap<?>> maps,
      @Nullable Injector src,
      Plugin newPlugin) {
    if (src == null || maps == null || maps.isEmpty()) {
      return;
    }

    for (Map.Entry<Class<?>, DynamicMap<?>> e : maps.entrySet()) {
      @SuppressWarnings("unchecked")
      PrivateInternals_DynamicMapImpl<Object> map =
          (PrivateInternals_DynamicMapImpl<Object>) e.getValue();
      List<ReloadableRegistrationHandle<?>> old = oldHandles.get(e.getKey());
      Map<Annotation, ReloadableRegistrationHandle<?>> am = Maps.newHashMap();
      Iterator<ReloadableRegistrationHandle<?>> oi = old.iterator();
      while (oi.hasNext()) {
        ReloadableRegistrationHandle<?> h = oi.next();
        am.put(h.getKey().getAnnotation(), h);
        oi.remove();
      }

      for (Binding<?> b : bindings(src, e.getKey())) {
        Key<?> key = b.getKey();
        ReloadableRegistrationHandle<?> h = am.get(key.getAnnotation());
        if (h != null) {
          newPlugin.add(replace(h, b));
        } else {
          newPlugin.add(map.put(
              newPlugin.getName(),
              b.getKey(),
              b.getProvider().get()));
        }
      }
    }
  }

  private static RegistrationHandle replace(
      ReloadableRegistrationHandle<?> h,
      Binding<?> b) {
    @SuppressWarnings("unchecked")
    ReloadableRegistrationHandle<Object> handle =
        (ReloadableRegistrationHandle<Object>) h;
    return handle.replace(b.getKey(), b.getProvider().get());
  }

  static <T> List<T> listeners(Injector src, Class<T> type) {
    List<Binding<T>> bindings = bindings(src, type);

    List<T> found = Lists.newArrayListWithCapacity(bindings.size());
    for (Binding<T> b : bindings) {
      found.add(b.getProvider().get());
    }
    return found;
  }

  void onRemovePlugin(Plugin plugin) {
    for (RemovePluginListener l : onRemove) {
      l.onRemovePlugin(plugin);
    }
  }

  private static <T> List<Binding<T>> bindings(Injector src, Class<T> type) {
    return src.findBindingsByType(TypeLiteral.get(type));
  }

  private static Map<Class<?>, DynamicSet<?>> dynamicSetsOf(Injector src) {
    Map<Class<?>, DynamicSet<?>> m = Maps.newHashMap();
    for (Map.Entry<Key<?>, Binding<?>> e : src.getBindings().entrySet()) {
      if (e.getKey().getTypeLiteral().getRawType() == DynamicSet.class) {
        ParameterizedType t =
            (ParameterizedType) e.getKey().getTypeLiteral().getType();
        Class<?> member = (Class<?>) t.getActualTypeArguments()[0];
        m.put(member, (DynamicSet<?>) e.getValue().getProvider().get());
      }
    }
    return m;
  }

  private static Map<Class<?>, DynamicMap<?>> dynamicMapsOf(Injector src) {
    Map<Class<?>, DynamicMap<?>> m = Maps.newHashMap();
    for (Map.Entry<Key<?>, Binding<?>> e : src.getBindings().entrySet()) {
      if (e.getKey().getTypeLiteral().getRawType() == DynamicMap.class) {
        ParameterizedType t =
            (ParameterizedType) e.getKey().getTypeLiteral().getType();
        Class<?> member = (Class<?>) t.getActualTypeArguments()[0];
        m.put(member, (DynamicMap<?>) e.getValue().getProvider().get());
      }
    }
    return m;
  }

  private static Module copy(Injector src) {
    Set<Class<?>> dynamicSetTypes = Sets.newHashSet();
    for (Map.Entry<Key<?>, Binding<?>> e : src.getBindings().entrySet()) {
      if (e.getKey().getTypeLiteral().getRawType() == DynamicSet.class) {
        ParameterizedType t =
            (ParameterizedType) e.getKey().getTypeLiteral().getType();
        dynamicSetTypes.add((Class<?>) t.getActualTypeArguments()[0]);
      }
    }

    final Map<Key<?>, Binding<?>> bindings = Maps.newLinkedHashMap();
    for (Map.Entry<Key<?>, Binding<?>> e : src.getBindings().entrySet()) {
      if (dynamicSetTypes.contains(e.getKey().getTypeLiteral().getType())) {
        continue;
      }
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
