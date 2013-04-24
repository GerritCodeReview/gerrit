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

import static com.google.gerrit.extensions.registration.PrivateInternals_DynamicTypes.dynamicItemsOf;
import static com.google.gerrit.extensions.registration.PrivateInternals_DynamicTypes.dynamicMapsOf;
import static com.google.gerrit.extensions.registration.PrivateInternals_DynamicTypes.dynamicSetsOf;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicTypes;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.registration.ReloadableRegistrationHandle;
import com.google.gerrit.extensions.systemstatus.ServerInformation;
import com.google.gerrit.server.PluginUser;
import com.google.gerrit.server.util.PluginRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Guice;
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
  private final ServerInformation srvInfo;
  private final ThreadLocalRequestContext local;
  private final PluginUser.Factory userFactory;
  private final CopyConfigModule copyConfigModule;
  private final Set<Key<?>> copyConfigKeys;
  private final List<StartPluginListener> onStart;
  private final List<ReloadPluginListener> onReload;

  private Module sysModule;
  private Module sshModule;
  private Module httpModule;

  private Provider<ModuleGenerator> sshGen;
  private Provider<ModuleGenerator> httpGen;

  private Map<TypeLiteral<?>, DynamicItem<?>> sysItems;

  private Map<TypeLiteral<?>, DynamicSet<?>> sysSets;
  private Map<TypeLiteral<?>, DynamicSet<?>> sshSets;
  private Map<TypeLiteral<?>, DynamicSet<?>> httpSets;

  private Map<TypeLiteral<?>, DynamicMap<?>> sysMaps;
  private Map<TypeLiteral<?>, DynamicMap<?>> sshMaps;
  private Map<TypeLiteral<?>, DynamicMap<?>> httpMaps;

  @Inject
  PluginGuiceEnvironment(
      Injector sysInjector,
      ThreadLocalRequestContext local,
      PluginUser.Factory userFactory,
      ServerInformation srvInfo,
      CopyConfigModule ccm) {
    this.sysInjector = sysInjector;
    this.srvInfo = srvInfo;
    this.local = local;
    this.userFactory = userFactory;
    this.copyConfigModule = ccm;
    this.copyConfigKeys = Guice.createInjector(ccm).getAllBindings().keySet();

    onStart = new CopyOnWriteArrayList<StartPluginListener>();
    onStart.addAll(listeners(sysInjector, StartPluginListener.class));

    onReload = new CopyOnWriteArrayList<ReloadPluginListener>();
    onReload.addAll(listeners(sysInjector, ReloadPluginListener.class));

    sysItems = dynamicItemsOf(sysInjector);
    sysSets = dynamicSetsOf(sysInjector);
    sysMaps = dynamicMapsOf(sysInjector);
  }

  ServerInformation getServerInformation() {
    return srvInfo;
  }

  boolean hasDynamicItem(TypeLiteral<?> type) {
    return sysItems.containsKey(type);
  }

  boolean hasDynamicSet(TypeLiteral<?> type) {
    return sysSets.containsKey(type)
        || (sshSets != null && sshSets.containsKey(type))
        || (httpSets != null && httpSets.containsKey(type));
  }

  boolean hasDynamicMap(TypeLiteral<?> type) {
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

  RequestContext enter(Plugin plugin) {
    return local.setContext(new PluginRequestContext(
        userFactory.create(plugin.getName())));
  }

  void exit(RequestContext old) {
    local.setContext(old);
  }

  void onStartPlugin(Plugin plugin) {
    for (StartPluginListener l : onStart) {
      l.onStartPlugin(plugin);
    }

    RequestContext oldContext = enter(plugin);
    try {
      attachItem(sysItems, plugin.getSysInjector(), plugin);

      attachSet(sysSets, plugin.getSysInjector(), plugin);
      attachSet(sshSets, plugin.getSshInjector(), plugin);
      attachSet(httpSets, plugin.getHttpInjector(), plugin);

      attachMap(sysMaps, plugin.getSysInjector(), plugin);
      attachMap(sshMaps, plugin.getSshInjector(), plugin);
      attachMap(httpMaps, plugin.getHttpInjector(), plugin);
    } finally {
      exit(oldContext);
    }
  }

  private void attachItem(Map<TypeLiteral<?>, DynamicItem<?>> items,
      @Nullable Injector src,
      Plugin plugin) {
    for (RegistrationHandle h : PrivateInternals_DynamicTypes
        .attachItems(src, items, plugin.getName())) {
      plugin.add(h);
    }
  }

  private void attachSet(Map<TypeLiteral<?>, DynamicSet<?>> sets,
      @Nullable Injector src,
      Plugin plugin) {
    for (RegistrationHandle h : PrivateInternals_DynamicTypes
        .attachSets(src, sets)) {
      plugin.add(h);
    }
  }

  private void attachMap(Map<TypeLiteral<?>, DynamicMap<?>> maps,
      @Nullable Injector src,
      Plugin plugin) {
    for (RegistrationHandle h : PrivateInternals_DynamicTypes
        .attachMaps(src, plugin.getName(), maps)) {
      plugin.add(h);
    }
  }

  void onReloadPlugin(Plugin oldPlugin, Plugin newPlugin) {
    for (ReloadPluginListener l : onReload) {
      l.onReloadPlugin(oldPlugin, newPlugin);
    }

    // Index all old registrations by the raw type. These may be replaced
    // during the reattach calls below. Any that are not replaced will be
    // removed when the old plugin does its stop routine.
    ListMultimap<TypeLiteral<?>, ReloadableRegistrationHandle<?>> old =
        LinkedListMultimap.create();
    for (ReloadableRegistrationHandle<?> h : oldPlugin.getReloadableHandles()) {
      old.put(h.getKey().getTypeLiteral(), h);
    }

    RequestContext oldContext = enter(newPlugin);
    try {
      reattachMap(old, sysMaps, newPlugin.getSysInjector(), newPlugin);
      reattachMap(old, sshMaps, newPlugin.getSshInjector(), newPlugin);
      reattachMap(old, httpMaps, newPlugin.getHttpInjector(), newPlugin);

      reattachSet(old, sysSets, newPlugin.getSysInjector(), newPlugin);
      reattachSet(old, sshSets, newPlugin.getSshInjector(), newPlugin);
      reattachSet(old, httpSets, newPlugin.getHttpInjector(), newPlugin);

      reattachItem(old, sysItems, newPlugin.getSysInjector(), newPlugin);
    } finally {
      exit(oldContext);
    }
  }

  private void reattachMap(
      ListMultimap<TypeLiteral<?>, ReloadableRegistrationHandle<?>> oldHandles,
      Map<TypeLiteral<?>, DynamicMap<?>> maps,
      @Nullable Injector src,
      Plugin newPlugin) {
    if (src == null || maps == null || maps.isEmpty()) {
      return;
    }

    for (Map.Entry<TypeLiteral<?>, DynamicMap<?>> e : maps.entrySet()) {
      @SuppressWarnings("unchecked")
      TypeLiteral<Object> type = (TypeLiteral<Object>) e.getKey();

      @SuppressWarnings("unchecked")
      PrivateInternals_DynamicMapImpl<Object> map =
          (PrivateInternals_DynamicMapImpl<Object>) e.getValue();

      Map<Annotation, ReloadableRegistrationHandle<?>> am = Maps.newHashMap();
      for (ReloadableRegistrationHandle<?> h : oldHandles.get(type)) {
        Annotation a = h.getKey().getAnnotation();
        if (a != null && !UNIQUE_ANNOTATION.isInstance(a)) {
          am.put(a, h);
        }
      }

      for (Binding<?> binding : bindings(src, e.getKey())) {
        @SuppressWarnings("unchecked")
        Binding<Object> b = (Binding<Object>) binding;
        Key<Object> key = b.getKey();
        if (key.getAnnotation() == null) {
          continue;
        }

        @SuppressWarnings("unchecked")
        ReloadableRegistrationHandle<Object> h =
            (ReloadableRegistrationHandle<Object>) am.remove(key.getAnnotation());
        if (h != null) {
          replace(newPlugin, h, b);
          oldHandles.remove(type, h);
        } else {
          newPlugin.add(map.put(
              newPlugin.getName(),
              b.getKey(),
              b.getProvider()));
        }
      }
    }
  }

  /** Type used to declare unique annotations. Guice hides this, so extract it. */
  private static final Class<?> UNIQUE_ANNOTATION =
      UniqueAnnotations.create().getClass();

  private void reattachSet(
      ListMultimap<TypeLiteral<?>, ReloadableRegistrationHandle<?>> oldHandles,
      Map<TypeLiteral<?>, DynamicSet<?>> sets,
      @Nullable Injector src,
      Plugin newPlugin) {
    if (src == null || sets == null || sets.isEmpty()) {
      return;
    }

    for (Map.Entry<TypeLiteral<?>, DynamicSet<?>> e : sets.entrySet()) {
      @SuppressWarnings("unchecked")
      TypeLiteral<Object> type = (TypeLiteral<Object>) e.getKey();

      @SuppressWarnings("unchecked")
      DynamicSet<Object> set = (DynamicSet<Object>) e.getValue();

      // Index all old handles that match this DynamicSet<T> keyed by
      // annotations. Ignore the unique annotations, thereby favoring
      // the @Named annotations or some other non-unique naming.
      Map<Annotation, ReloadableRegistrationHandle<?>> am = Maps.newHashMap();
      List<ReloadableRegistrationHandle<?>> old = oldHandles.get(type);
      Iterator<ReloadableRegistrationHandle<?>> oi = old.iterator();
      while (oi.hasNext()) {
        ReloadableRegistrationHandle<?> h = oi.next();
        Annotation a = h.getKey().getAnnotation();
        if (a != null && !UNIQUE_ANNOTATION.isInstance(a)) {
          am.put(a, h);
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
      for (Binding<?> binding : bindings(src, type)) {
        @SuppressWarnings("unchecked")
        Binding<Object> b = (Binding<Object>) binding;
        Key<Object> key = b.getKey();
        if (key.getAnnotation() == null) {
          continue;
        }

        @SuppressWarnings("unchecked")
        ReloadableRegistrationHandle<Object> h1 =
            (ReloadableRegistrationHandle<Object>) am.remove(key.getAnnotation());
        if (h1 != null) {
          replace(newPlugin, h1, b);
        } else if (oi.hasNext()) {
          @SuppressWarnings("unchecked")
          ReloadableRegistrationHandle<Object> h2 =
            (ReloadableRegistrationHandle<Object>) oi.next();
          oi.remove();
          replace(newPlugin, h2, b);
        } else {
          newPlugin.add(set.add(b.getKey(), b.getProvider()));
        }
      }
    }
  }
  private void reattachItem(
      ListMultimap<TypeLiteral<?>, ReloadableRegistrationHandle<?>> oldHandles,
      Map<TypeLiteral<?>, DynamicItem<?>> items,
      @Nullable Injector src,
      Plugin newPlugin) {
    if (src == null || items == null || items.isEmpty()) {
      return;
    }

    for (Map.Entry<TypeLiteral<?>, DynamicItem<?>> e : items.entrySet()) {
      @SuppressWarnings("unchecked")
      TypeLiteral<Object> type = (TypeLiteral<Object>) e.getKey();

      @SuppressWarnings("unchecked")
      DynamicItem<Object> item = (DynamicItem<Object>) e.getValue();

      Iterator<ReloadableRegistrationHandle<?>> oi =
          oldHandles.get(type).iterator();

      for (Binding<?> binding : bindings(src, type)) {
        @SuppressWarnings("unchecked")
        Binding<Object> b = (Binding<Object>) binding;
        if (oi.hasNext()) {
          @SuppressWarnings("unchecked")
          ReloadableRegistrationHandle<Object> h =
            (ReloadableRegistrationHandle<Object>) oi.next();
          oi.remove();
          replace(newPlugin, h, b);
        } else {
          newPlugin.add(item.set(b.getKey(), b.getProvider(),
              newPlugin.getName()));
        }
      }
    }
  }

  private static <T> void replace(Plugin newPlugin,
      ReloadableRegistrationHandle<T> h, Binding<T> b) {
    RegistrationHandle n = h.replace(b.getKey(), b.getProvider());
    if (n != null){
      newPlugin.add(n);
    }
  }

  static <T> List<T> listeners(Injector src, Class<T> type) {
    List<Binding<T>> bindings = bindings(src, TypeLiteral.get(type));
    int cnt = bindings != null ? bindings.size() : 0;
    List<T> found = Lists.newArrayListWithCapacity(cnt);
    if (bindings != null) {
      for (Binding<T> b : bindings) {
        found.add(b.getProvider().get());
      }
    }
    return found;
  }

  private static <T> List<Binding<T>> bindings(Injector src, TypeLiteral<T> type) {
    return src.findBindingsByType(type);
  }

  private Module copy(Injector src) {
    Set<TypeLiteral<?>> dynamicTypes = Sets.newHashSet();
    for (Map.Entry<Key<?>, Binding<?>> e : src.getBindings().entrySet()) {
      TypeLiteral<?> type = e.getKey().getTypeLiteral();
      if (type.getRawType() == DynamicSet.class
          || type.getRawType() == DynamicMap.class) {
        ParameterizedType t = (ParameterizedType) type.getType();
        dynamicTypes.add(TypeLiteral.get(t.getActualTypeArguments()[0]));
      }
    }

    final Map<Key<?>, Binding<?>> bindings = Maps.newLinkedHashMap();
    for (Map.Entry<Key<?>, Binding<?>> e : src.getBindings().entrySet()) {
      if (dynamicTypes.contains(e.getKey().getTypeLiteral())
          && e.getKey().getAnnotation() != null) {
        // A type used in DynamicSet or DynamicMap that has an annotation
        // must be picked up by the set/map itself. A type used in either
        // but without an annotation may be magic glue implementing F and
        // using DynamicSet<F> or DynamicMap<F> internally. That should be
        // exported to plugins.
        continue;
      } else if (shouldCopy(e.getKey())) {
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

  private boolean shouldCopy(Key<?> key) {
    if (copyConfigKeys.contains(key)) {
      return false;
    }
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
