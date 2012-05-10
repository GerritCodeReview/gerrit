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
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.name.Named;
import com.google.inject.util.Types;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A set of members that can be modified as plugins reload.
 * <p>
 * DynamicSets are always mapped as singletons in Guice, and only may contain
 * singletons, as providers are resolved to an instance before the member is
 * added to the set.
 */
public class DynamicSet<T> implements Iterable<T> {
  /**
   * Declare a singleton {@code DynamicSet<T>} with a binder.
   * <p>
   * Sets must be defined in a Guice module before they can be bound:
   * <pre>
   *   DynamicSet.setOf(binder(), Interface.class);
   *   DynamicSet.bind(binder(), Interface.class).to(Impl.class);
   * </pre>
   *
   * @param binder a new binder created in the module.
   * @param member type of entry in the set.
   */
  public static <T> void setOf(Binder binder, Class<T> member) {
    setOf(binder, TypeLiteral.get(member));
  }

  /**
   * Declare a singleton {@code DynamicSet<T>} with a binder.
   * <p>
   * Sets must be defined in a Guice module before they can be bound:
   * <pre>
   *   DynamicSet.setOf(binder(), new TypeLiteral<Thing<Foo>>() {});
   * </pre>
   *
   * @param binder a new binder created in the module.
   * @param member type of entry in the set.
   */
  public static <T> void setOf(Binder binder, TypeLiteral<T> member) {
    @SuppressWarnings("unchecked")
    Key<DynamicSet<T>> key = (Key<DynamicSet<T>>) Key.get(
        Types.newParameterizedType(DynamicSet.class, member.getType()));
    binder.bind(key)
      .toProvider(new DynamicSetProvider<T>(member))
      .in(Scopes.SINGLETON);
  }

  /**
   * Bind one implementation into the set using a unique annotation.
   *
   * @param binder a new binder created in the module.
   * @param type type of entries in the set.
   * @return a binder to continue configuring the new set member.
   */
  public static <T> LinkedBindingBuilder<T> bind(Binder binder, Class<T> type) {
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
    return binder.bind(type).annotatedWith(UniqueAnnotations.create());
  }

  /**
   * Bind a named implementation into the set.
   *
   * @param binder a new binder created in the module.
   * @param type type of entries in the set.
   * @param name {@code @Named} annotation to apply instead of a unique
   *        annotation.
   * @return a binder to continue configuring the new set member.
   */
  public static <T> LinkedBindingBuilder<T> bind(Binder binder,
      Class<T> type,
      Named name) {
    return bind(binder, TypeLiteral.get(type));
  }

  /**
   * Bind a named implementation into the set.
   *
   * @param binder a new binder created in the module.
   * @param type type of entries in the set.
   * @param name {@code @Named} annotation to apply instead of a unique
   *        annotation.
   * @return a binder to continue configuring the new set member.
   */
  public static <T> LinkedBindingBuilder<T> bind(Binder binder,
      TypeLiteral<T> type,
      Named name) {
    return binder.bind(type).annotatedWith(name);
  }

  private final CopyOnWriteArrayList<AtomicReference<T>> items;

  DynamicSet(Collection<AtomicReference<T>> base) {
    items = new CopyOnWriteArrayList<AtomicReference<T>>(base);
  }

  @Override
  public Iterator<T> iterator() {
    final Iterator<AtomicReference<T>> itr = items.iterator();
    return new Iterator<T>() {
      private T next;

      @Override
      public boolean hasNext() {
        while (next == null && itr.hasNext()) {
          next = itr.next().get();
        }
        return next != null;
      }

      @Override
      public T next() {
        if (hasNext()) {
          T result = next;
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
   * Add one new element to the set.
   *
   * @param item the item to add to the collection. Must not be null.
   * @return handle to remove the item at a later point in time.
   */
  public RegistrationHandle add(final T item) {
    final AtomicReference<T> ref = new AtomicReference<T>(item);
    items.add(ref);
    return new RegistrationHandle() {
      @Override
      public void remove() {
        if (ref.compareAndSet(item, null)) {
          items.remove(ref);
        }
      }
    };
  }

  /**
   * Add one new element that may be hot-replaceable in the future.
   *
   * @param key unique description from the item's Guice binding. This can be
   *        later obtained from the registration handle to facilitate matching
   *        with the new equivalent instance during a hot reload.
   * @param item the item to add to the collection right now. Must not be null.
   * @return a handle that can remove this item later, or hot-swap the item
   *         without it ever leaving the collection.
   */
  public ReloadableRegistrationHandle<T> add(Key<T> key, T item) {
    AtomicReference<T> ref = new AtomicReference<T>(item);
    items.add(ref);
    return new ReloadableHandle(ref, key, item);
  }

  private class ReloadableHandle implements ReloadableRegistrationHandle<T> {
    private final AtomicReference<T> ref;
    private final Key<T> key;
    private final T item;

    ReloadableHandle(AtomicReference<T> ref, Key<T> key, T item) {
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
    public ReloadableHandle replace(Key<T> newKey, T newItem) {
      if (ref.compareAndSet(item, newItem)) {
        return new ReloadableHandle(ref, newKey, newItem);
      }
      return null;
    }
  }
}
