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
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.name.Named;
import com.google.inject.util.Providers;
import com.google.inject.util.Types;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A single item that can be modified as plugins reload.
 * <p>
 * DynamicItems are always mapped as singletons in Guice. Items store a Provider
 * internally, and resolve the provider to an instance on demand. This enables
 * registrations to decide between singleton and non-singleton members. If
 * multiple plugins try to provide the same Provider, an exception is thrown.
 */
public class DynamicItem<T> {

  /**
   * A pair of the item we wish to dynamically provide, and the plugin's name
   * while provides the item. The name is used for debugging cases where
   * multiple plugins provide the same item, which is not allowed.
   */
  class ItemAndPluginName {
    public ItemAndPluginName(Provider<T> provider, String pluginName) {
      this.provider = provider;
      this.pluginName = pluginName;
    }

    Provider<T> provider;
    String pluginName;
  }

  /**
   * Declare a singleton {@code DynamicItem<T>} with a binder.
   * <p>
   * Items must be defined in a Guice module before they can be bound:
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
   * <p>
   * Items must be defined in a Guice module before they can be bound:
   * <pre>
   *   DynamicSet.itemOf(binder(), new TypeLiteral<Thing<Foo>>() {});
   * </pre>
   *
   * @param binder a new binder created in the module.
   * @param member type of entry to store.
   */
  public static <T> void itemOf(Binder binder, TypeLiteral<T> member) {
    @SuppressWarnings("unchecked")
    Key<DynamicItem<T>> key = (Key<DynamicItem<T>>) Key.get(
        Types.newParameterizedType(DynamicItem.class, member.getType()));
    binder.bind(key)
      .toProvider(new DynamicItemProvider<T>(member, key))
      .in(Scopes.SINGLETON);
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
   * Bind one implementation as the item using a unique annotation.
   *
   * @param binder a new binder created in the module.
   * @param type type of entry to store.
   * @return a binder to continue configuring the new item.
   */
  public static <T> LinkedBindingBuilder<T> bind(Binder binder, TypeLiteral<T> type) {
    return binder.bind(type).annotatedWith(UniqueAnnotations.create());
  }

  /**
   * Bind a named implementation as the item.
   *
   * @param binder a new binder created in the module.
   * @param type type of entry to store.
   * @param name {@code @Named} annotation to apply instead of a unique
   *        annotation.
   * @return a binder to continue configuring the new item.
   */
  public static <T> LinkedBindingBuilder<T> bind(Binder binder,
      Class<T> type,
      Named name) {
    return bind(binder, TypeLiteral.get(type));
  }

  /**
   * Bind a named implementation as the item.
   *
   * @param binder a new binder created in the module.
   * @param type type of entry to store.
   * @param name {@code @Named} annotation to apply instead of a unique
   *        annotation.
   * @return a binder to continue configuring the new item.
   */
  public static <T> LinkedBindingBuilder<T> bind(Binder binder,
      TypeLiteral<T> type,
      Named name) {
    return binder.bind(type).annotatedWith(name);
  }

  private final AtomicReference<ItemAndPluginName> item;
  private final Key<DynamicItem<T>> key;

  DynamicItem(Provider<T> provider, String pluginName, Key<DynamicItem<T>> key) {
    if (provider != null) {
      item =
          new AtomicReference<ItemAndPluginName>(new ItemAndPluginName(
              provider, pluginName));
    } else {
      item = new AtomicReference<ItemAndPluginName>(null);
    }
    this.key = key;
  }

  public T get() {
    ItemAndPluginName myItem = item.get();
    return myItem != null ? myItem.provider.get() : null;
  }

  /**
   * Set the element to provide.
   *
   * @param item the item to use. Must not be null.
   * @param pluginName the name of the plugin providing the item.
   * @return handle to remove the item at a later point in time.
   */
  public RegistrationHandle set(final T item, final String pluginName) {
    return set(Providers.of(item), pluginName);
  }

  /**
   * Add one new element to the set.
   *
   * @param item the item to add to the collection. Must not be null.
   * @return handle to remove the item at a later point in time.
   */
  public RegistrationHandle set(final Provider<T> item, final String pluginName) {
    final ItemAndPluginName subItem = new ItemAndPluginName(item, pluginName);
    final AtomicReference<Provider<T>> ref =
        new AtomicReference<Provider<T>>(item);
    if (!this.item.compareAndSet(null, subItem)) {
      // We already have an item bound.
      throw new ProvisionException("Type " + this.item.get().provider
          + "\nUsing key " + this.key + "\nAlready provided by plugin: "
          + this.item.get().pluginName);
    }
    return new RegistrationHandle() {
      @Override
      public void remove() {
        ref.compareAndSet(item, null);
      }
    };
  }

  /**
   * Set the element that may be hot-replaceable in the future.
   *
   * @param key unique description from the item's Guice binding. This can be
   *        later obtained from the registration handle to facilitate matching
   *        with the new equivalent instance during a hot reload.
   * @param item the item to set as our value right now. Must not be null.
   * @param pluginName the name of the plugin providing the item.
   * @return a handle that can remove this item later, or hot-swap the item
   *         without it ever leaving the collection.
   */
  public ReloadableRegistrationHandle<T> set(Key<T> key, Provider<T> item,
      String pluginName) {
    final ItemAndPluginName tempItem = new ItemAndPluginName(item, pluginName);
    if (!this.item.compareAndSet(null, tempItem)) {
      // We already have an item bound.
      throw new ProvisionException("Type " + this.item.get().provider
          + "\nUsing key " + this.key + "\nAlready provided by plugin: "
          + this.item.get().pluginName);
    }
    return new ReloadableHandle(key, tempItem);
  }

  private class ReloadableHandle implements ReloadableRegistrationHandle<T> {
    private final Key<T> key;
    private final ItemAndPluginName item;

    ReloadableHandle(Key<T> key, ItemAndPluginName item) {
      this.key = key;
      this.item = item;
    }

    @Override
    public void remove() {
      DynamicItem.this.item.compareAndSet(item, null);
    }

    @Override
    public Key<T> getKey() {
      return key;
    }

    @Override
    public ReloadableHandle replace(Key<T> newKey, Provider<T> newItem) {
      ItemAndPluginName tempItem = new ItemAndPluginName(newItem, item.pluginName);
      if (DynamicItem.this.item.compareAndSet(item, tempItem)) {
        return new ReloadableHandle(newKey, tempItem);
      }
      return null;
    }
  }
}
