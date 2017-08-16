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
import com.google.inject.ProvisionException;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.util.Providers;
import com.google.inject.util.Types;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A single item that can be modified as plugins reload.
 *
 * <p>DynamicItems are always mapped as singletons in Guice. Items store a Provider internally, and
 * resolve the provider to an instance on demand. This enables registrations to decide between
 * singleton and non-singleton members. If multiple plugins try to provide the same Provider, an
 * exception is thrown.
 */
public class DynamicItem<T> {
  /** Pair of provider implementation and plugin providing it. */
  static class NamedProvider<T> {
    final Provider<T> impl;
    final String pluginName;

    NamedProvider(Provider<T> provider, String pluginName) {
      this.impl = provider;
      this.pluginName = pluginName;
    }
  }

  /**
   * Declare a singleton {@code DynamicItem<T>} with a binder.
   *
   * <p>Items must be defined in a Guice module before they can be bound:
   *
   * <pre>
   *   DynamicItem.itemOf(binder(), Interface.class);
   *   DynamicItem.bind(binder(), Interface.class).to(Impl.class);
   * </pre>
   *
   * @param binder a new binder created in the module.
   * @param member type of entry to store.
   */
  public static <T> void itemOf(Binder binder, Class<T> member) {
    itemOf(binder, TypeLiteral.get(member));
  }

  /**
   * Declare a singleton {@code DynamicItem<T>} with a binder.
   *
   * <p>Items must be defined in a Guice module before they can be bound:
   *
   * <pre>
   *   DynamicSet.itemOf(binder(), new TypeLiteral&lt;Thing&lt;Foo&gt;&gt;() {});
   * </pre>
   *
   * @param binder a new binder created in the module.
   * @param member type of entry to store.
   */
  public static <T> void itemOf(Binder binder, TypeLiteral<T> member) {
    Key<DynamicItem<T>> key = keyFor(member);
    binder.bind(key).toProvider(new DynamicItemProvider<>(member, key)).in(Scopes.SINGLETON);
  }

  /**
   * Construct a single {@code DynamicItem<T>} with a fixed value.
   *
   * <p>Primarily useful for passing {@code DynamicItem}s to constructors in tests.
   *
   * @param member type of item.
   * @param item item to store.
   */
  public static <T> DynamicItem<T> itemOf(Class<T> member, T item) {
    return new DynamicItem<>(keyFor(TypeLiteral.get(member)), Providers.of(item), "gerrit");
  }

  @SuppressWarnings("unchecked")
  private static <T> Key<DynamicItem<T>> keyFor(TypeLiteral<T> member) {
    return (Key<DynamicItem<T>>)
        Key.get(Types.newParameterizedType(DynamicItem.class, member.getType()));
  }

  /**
   * Bind one implementation as the item using a unique annotation.
   *
   * @param binder a new binder created in the module.
   * @param type type of entry to store.
   * @return a binder to continue configuring the new item.
   */
  public static <T> LinkedBindingBuilder<T> bind(Binder binder, Class<T> type) {
    return bind(binder, TypeLiteral.get(type));
  }

  /**
   * Bind one implementation as the item.
   *
   * @param binder a new binder created in the module.
   * @param type type of entry to store.
   * @return a binder to continue configuring the new item.
   */
  public static <T> LinkedBindingBuilder<T> bind(Binder binder, TypeLiteral<T> type) {
    return binder.bind(type);
  }

  private final Key<DynamicItem<T>> key;
  private final AtomicReference<NamedProvider<T>> ref;

  DynamicItem(Key<DynamicItem<T>> key, Provider<T> provider, String pluginName) {
    NamedProvider<T> in = null;
    if (provider != null) {
      in = new NamedProvider<>(provider, pluginName);
    }
    this.key = key;
    this.ref = new AtomicReference<>(in);
  }

  /**
   * Get the configured item, or null.
   *
   * @return the configured item instance; null if no implementation has been bound to the item.
   *     This is common if no plugin registered an implementation for the type.
   */
  public T get() {
    NamedProvider<T> item = ref.get();
    return item != null ? item.impl.get() : null;
  }

  /**
   * Set the element to provide.
   *
   * @param item the item to use. Must not be null.
   * @param pluginName the name of the plugin providing the item.
   * @return handle to remove the item at a later point in time.
   */
  public RegistrationHandle set(T item, String pluginName) {
    return set(Providers.of(item), pluginName);
  }

  /**
   * Set the element to provide.
   *
   * @param impl the item to add to the collection. Must not be null.
   * @param pluginName name of the source providing the implementation.
   * @return handle to remove the item at a later point in time.
   */
  public RegistrationHandle set(Provider<T> impl, String pluginName) {
    final NamedProvider<T> item = new NamedProvider<>(impl, pluginName);
    NamedProvider<T> old = null;
    while (!ref.compareAndSet(old, item)) {
      old = ref.get();
      if (old != null && !"gerrit".equals(old.pluginName)) {
        throw new ProvisionException(
            String.format(
                "%s already provided by %s, ignoring plugin %s",
                key.getTypeLiteral(), old.pluginName, pluginName));
      }
    }

    final NamedProvider<T> defaultItem = old;
    return new RegistrationHandle() {
      @Override
      public void remove() {
        ref.compareAndSet(item, defaultItem);
      }
    };
  }

  /**
   * Set the element that may be hot-replaceable in the future.
   *
   * @param key unique description from the item's Guice binding. This can be later obtained from
   *     the registration handle to facilitate matching with the new equivalent instance during a
   *     hot reload.
   * @param impl the item to set as our value right now. Must not be null.
   * @param pluginName the name of the plugin providing the item.
   * @return a handle that can remove this item later, or hot-swap the item.
   */
  public ReloadableRegistrationHandle<T> set(Key<T> key, Provider<T> impl, String pluginName) {
    final NamedProvider<T> item = new NamedProvider<>(impl, pluginName);
    NamedProvider<T> old = null;
    while (!ref.compareAndSet(old, item)) {
      old = ref.get();
      if (old != null && !"gerrit".equals(old.pluginName) && !pluginName.equals(old.pluginName)) {
        // We allow to replace:
        // 1. Gerrit core items, e.g. websession cache
        //    can be replaced by plugin implementation
        // 2. Reload of current plugin
        throw new ProvisionException(
            String.format(
                "%s already provided by %s, ignoring plugin %s",
                this.key.getTypeLiteral(), old.pluginName, pluginName));
      }
    }
    return new ReloadableHandle(key, item, old);
  }

  private class ReloadableHandle implements ReloadableRegistrationHandle<T> {
    private final Key<T> handleKey;
    private final NamedProvider<T> item;
    private final NamedProvider<T> defaultItem;

    ReloadableHandle(Key<T> handleKey, NamedProvider<T> item, NamedProvider<T> defaultItem) {
      this.handleKey = handleKey;
      this.item = item;
      this.defaultItem = defaultItem;
    }

    @Override
    public Key<T> getKey() {
      return handleKey;
    }

    @Override
    public void remove() {
      ref.compareAndSet(item, defaultItem);
    }

    @Override
    public ReloadableHandle replace(Key<T> newKey, Provider<T> newItem) {
      NamedProvider<T> n = new NamedProvider<>(newItem, item.pluginName);
      if (ref.compareAndSet(item, n)) {
        return new ReloadableHandle(newKey, n, defaultItem);
      }
      return null;
    }
  }
}
