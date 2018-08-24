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

package com.google.gerrit.extensions.registration;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.name.Named;
import com.google.inject.util.Providers;
import com.google.inject.util.Types;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A set of members that can be modified as plugins reload.
 *
 * <p>DynamicSets are always mapped as singletons in Guice. Sets store Providers internally, and
 * resolve the provider to an instance on demand. This enables registrations to decide between
 * singleton and non-singleton members.
 */
public class DynamicSet<T> implements Iterable<T> {
  public interface Entry<T> {
    String getPluginName();

    Provider<T> getProvider();
  }

  /**
   * Declare a singleton {@code DynamicSet<T>} with a binder.
   *
   * <p>Sets must be defined in a Guice module before they can be bound:
   *
   * <pre>
   *   DynamicSet.setOf(binder(), Interface.class);
   *   DynamicSet.bind(binder(), Interface.class).to(Impl.class);
   * </pre>
   *
   * @param binder a new binder created in the module.
   * @param member type of entry in the set.
   */
  public static <T> void setOf(Binder binder, Class<T> member) {
    binder.disableCircularProxies();
    setOf(binder, TypeLiteral.get(member));
  }

  /**
   * Declare a singleton {@code DynamicSet<T>} with a binder.
   *
   * <p>Sets must be defined in a Guice module before they can be bound:
   *
   * <pre>
   *   DynamicSet.setOf(binder(), new TypeLiteral&lt;Thing&lt;Foo&gt;&gt;() {});
   * </pre>
   *
   * @param binder a new binder created in the module.
   * @param member type of entry in the set.
   */
  public static <T> void setOf(Binder binder, TypeLiteral<T> member) {
    @SuppressWarnings("unchecked")
    Key<DynamicSet<T>> key =
        (Key<DynamicSet<T>>)
            Key.get(Types.newParameterizedType(DynamicSet.class, member.getType()));
    binder.disableCircularProxies();
    binder.bind(key).toProvider(new DynamicSetProvider<>(member)).in(Scopes.SINGLETON);
  }

  /**
   * Bind one implementation into the set using a unique annotation.
   *
   * @param binder a new binder created in the module.
   * @param type type of entries in the set.
   * @return a binder to continue configuring the new set member.
   */
  public static <T> LinkedBindingBuilder<T> bind(Binder binder, Class<T> type) {
    binder.disableCircularProxies();
    return bind(binder, TypeLiteral.get(type));
  }

  /**
   * Bind one implementation into the set using a unique annotation.
   *
   * @param binder a new binder created in the module.
   * @param type type of entries in the set.
   * @return a binder to continue configuring the new set member.
   */
  public static <T> LinkedBindingBuilder<T> bind(Binder binder, TypeLiteral<T> type) {
    binder.disableCircularProxies();
    return binder.bind(type).annotatedWith(UniqueAnnotations.create());
  }

  /**
   * Bind a named implementation into the set.
   *
   * @param binder a new binder created in the module.
   * @param type type of entries in the set.
   * @param name {@code @Named} annotation to apply instead of a unique annotation.
   * @return a binder to continue configuring the new set member.
   */
  public static <T> LinkedBindingBuilder<T> bind(Binder binder, Class<T> type, Named name) {
    binder.disableCircularProxies();
    return bind(binder, TypeLiteral.get(type));
  }

  /**
   * Bind a named implementation into the set.
   *
   * @param binder a new binder created in the module.
   * @param type type of entries in the set.
   * @param name {@code @Named} annotation to apply instead of a unique annotation.
   * @return a binder to continue configuring the new set member.
   */
  public static <T> LinkedBindingBuilder<T> bind(Binder binder, TypeLiteral<T> type, Named name) {
    binder.disableCircularProxies();
    return binder.bind(type).annotatedWith(name);
  }

  public static <T> DynamicSet<T> emptySet() {
    return new DynamicSet<>(Collections.<AtomicReference<NamedProvider<T>>>emptySet());
  }

  private final CopyOnWriteArrayList<AtomicReference<NamedProvider<T>>> items;

  DynamicSet(Collection<AtomicReference<NamedProvider<T>>> base) {
    items = new CopyOnWriteArrayList<>(base);
  }

  public DynamicSet() {
    this(Collections.emptySet());
  }

  @Override
  public Iterator<T> iterator() {
    Iterator<Entry<T>> entryIterator = entryIterator();
    return new Iterator<T>() {
      @Override
      public boolean hasNext() {
        return entryIterator.hasNext();
      }

      @Override
      public T next() {
        Entry<T> next = entryIterator.next();
        return next != null ? next.getProvider().get() : null;
      }
    };
  }

  public Iterator<Entry<T>> entryIterator() {
    final Iterator<AtomicReference<NamedProvider<T>>> itr = items.iterator();
    return new Iterator<Entry<T>>() {
      private Entry<T> next;

      @Override
      public boolean hasNext() {
        while (next == null && itr.hasNext()) {
          NamedProvider<T> p = itr.next().get();
          if (p != null) {
            try {
              next =
                  new Entry<T>() {
                    @Override
                    public String getPluginName() {
                      return p.pluginName;
                    }

                    @Override
                    public Provider<T> getProvider() {
                      return p.impl;
                    }
                  };
            } catch (RuntimeException e) {
              // TODO Log failed member of DynamicSet.
            }
          }
        }
        return next != null;
      }

      @Override
      public Entry<T> next() {
        if (hasNext()) {
          Entry<T> result = next;
          next = null;
          return result;
        }
        throw new NoSuchElementException();
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  /**
   * Returns {@code true} if this set contains the given item.
   *
   * @param item item to check whether or not it is contained.
   * @return {@code true} if this set contains the given item.
   */
  public boolean contains(T item) {
    Iterator<T> iterator = iterator();
    while (iterator.hasNext()) {
      T candidate = iterator.next();
      if (candidate == item) {
        return true;
      }
    }
    return false;
  }

  /**
   * Get the names of all running plugins supplying this type.
   *
   * @return sorted set of active plugins that supply at least one item.
   */
  public SortedSet<String> plugins() {
    SortedSet<String> r = new TreeSet<>();
    for (AtomicReference<NamedProvider<T>> item : items) {
      r.add(item.get().pluginName);
    }
    return Collections.unmodifiableSortedSet(r);
  }

  /**
   * Get the items exported by a single plugin.
   *
   * @param pluginName name of the plugin.
   * @return items exported by a plugin.
   */
  public Set<Provider<T>> byPlugin(String pluginName) {
    Set<Provider<T>> r = new HashSet<>();
    for (AtomicReference<NamedProvider<T>> item : items) {
      if (item.get().pluginName.equals(pluginName)) {
        r.add(item.get().impl);
      }
    }
    return Collections.unmodifiableSet(r);
  }

  /**
   * Add one new element to the set.
   *
   * @param item the item to add to the collection. Must not be null.
   * @return handle to remove the item at a later point in time.
   */
  public RegistrationHandle add(String pluginName, T item) {
    return add(pluginName, Providers.of(item));
  }

  /**
   * Add one new element to the set.
   *
   * @param item the item to add to the collection. Must not be null.
   * @return handle to remove the item at a later point in time.
   */
  public RegistrationHandle add(String pluginName, Provider<T> item) {
    final AtomicReference<NamedProvider<T>> ref =
        new AtomicReference<>(new NamedProvider<>(item, pluginName));
    items.add(ref);
    return new RegistrationHandle() {
      @Override
      public void remove() {
        if (ref.compareAndSet(ref.get(), null)) {
          items.remove(ref);
        }
      }
    };
  }

  /**
   * Add one new element that may be hot-replaceable in the future.
   *
   * @param pluginName unique name of the plugin providing the item.
   * @param key unique description from the item's Guice binding. This can be later obtained from
   *     the registration handle to facilitate matching with the new equivalent instance during a
   *     hot reload.
   * @param item the item to add to the collection right now. Must not be null.
   * @return a handle that can remove this item later, or hot-swap the item without it ever leaving
   *     the collection.
   */
  public ReloadableRegistrationHandle<T> add(String pluginName, Key<T> key, Provider<T> item) {
    AtomicReference<NamedProvider<T>> ref =
        new AtomicReference<>(new NamedProvider<>(item, pluginName));
    items.add(ref);
    return new ReloadableHandle(ref, key, ref.get());
  }

  private class ReloadableHandle implements ReloadableRegistrationHandle<T> {
    private final AtomicReference<NamedProvider<T>> ref;
    private final Key<T> key;
    private final NamedProvider<T> item;

    ReloadableHandle(AtomicReference<NamedProvider<T>> ref, Key<T> key, NamedProvider<T> item) {
      this.ref = ref;
      this.key = key;
      this.item = item;
    }

    @Override
    public void remove() {
      if (ref.compareAndSet(item, null)) {
        items.remove(ref);
      }
    }

    @Override
    public Key<T> getKey() {
      return key;
    }

    @Override
    public ReloadableHandle replace(Key<T> newKey, Provider<T> newItem) {
      NamedProvider<T> n = new NamedProvider<>(newItem, item.pluginName);
      if (ref.compareAndSet(item, n)) {
        return new ReloadableHandle(ref, newKey, n);
      }
      return null;
    }
  }
}
