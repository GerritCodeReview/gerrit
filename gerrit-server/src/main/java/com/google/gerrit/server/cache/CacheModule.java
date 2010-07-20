// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.cache;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.name.Names;

import java.io.Serializable;

/**
 * Miniature DSL to support binding {@link Cache} instances in Guice.
 */
public abstract class CacheModule extends AbstractModule {
  /**
   * Declare an unnamed in-memory cache.
   *
   * @param <K> type of key used to lookup entries.
   * @param <V> type of value stored by the cache.
   * @param type type literal for the cache, this literal will be used to match
   *        injection sites.
   * @return binding to describe the cache. Caller must set at least the name on
   *         the returned binding.
   */
  protected <K, V> UnnamedCacheBinding<K, V> core(
      final TypeLiteral<Cache<K, V>> type) {
    return core(Key.get(type), type);
  }

  /**
   * Declare a named in-memory cache.
   *
   * @param <K> type of key used to lookup entries.
   * @param <V> type of value stored by the cache.
   * @param type type literal for the cache, this literal will be used to match
   *        injection sites. Injection sites are matched by this type literal
   *        and with {@code @Named} annotations.
   * @return binding to describe the cache.
   */
  protected <K, V> NamedCacheBinding<K, V> core(
      final TypeLiteral<Cache<K, V>> type, final String name) {
    return core(Key.get(type, Names.named(name)), type).name(name);
  }

  private <K, V> UnnamedCacheBinding<K, V> core(final Key<Cache<K, V>> key,
      final TypeLiteral<Cache<K, V>> type) {
    final boolean disk = false;
    final CacheProvider<K, V> b = new CacheProvider<K, V>(disk, this, type);
    bind(key).toProvider(b).in(Scopes.SINGLETON);
    return b;
  }

  /**
   * Declare an unnamed in-memory/on-disk cache.
   *
   * @param <K> type of key used to find entries, must be {@link Serializable}.
   * @param <V> type of value stored by the cache, must be {@link Serializable}.
   * @param type type literal for the cache, this literal will be used to match
   *        injection sites. Injection sites are matched by this type literal
   *        and with {@code @Named} annotations.
   * @return binding to describe the cache. Caller must set at least the name on
   *         the returned binding.
   */
  protected <K extends Serializable, V extends Serializable> UnnamedCacheBinding<K, V> disk(
      final TypeLiteral<Cache<K, V>> type) {
    return disk(Key.get(type), type);
  }

  /**
   * Declare a named in-memory/on-disk cache.
   *
   * @param <K> type of key used to find entries, must be {@link Serializable}.
   * @param <V> type of value stored by the cache, must be {@link Serializable}.
   * @param type type literal for the cache, this literal will be used to match
   *        injection sites. Injection sites are matched by this type literal
   *        and with {@code @Named} annotations.
   * @return binding to describe the cache.
   */
  protected <K extends Serializable, V extends Serializable> NamedCacheBinding<K, V> disk(
      final TypeLiteral<Cache<K, V>> type, final String name) {
    return disk(Key.get(type, Names.named(name)), type).name(name);
  }

  private <K, V> UnnamedCacheBinding<K, V> disk(final Key<Cache<K, V>> key,
      final TypeLiteral<Cache<K, V>> type) {
    final boolean disk = true;
    final CacheProvider<K, V> b = new CacheProvider<K, V>(disk, this, type);
    bind(key).toProvider(b).in(Scopes.SINGLETON);
    return b;
  }

  <K, V> Provider<EntryCreator<K, V>> getEntryCreator(CacheProvider<K, V> cp,
      Class<? extends EntryCreator<K, V>> type) {
    Key<EntryCreator<K, V>> key = newKey();
    bind(key).to(type).in(Scopes.SINGLETON);
    return getProvider(key);
  }

  <V> Provider<V> getValueProvider(Class<V> type) {
    Key<V> key = Key.get(type, UniqueAnnotations.create());
    bind(key).to(type);
    return getProvider(key);
  }

  @SuppressWarnings("unchecked")
  private static <K, V> Key<EntryCreator<K, V>> newKey() {
    return (Key<EntryCreator<K, V>>) newKeyImpl();
  }

  private static Key<?> newKeyImpl() {
    return Key.get(EntryCreator.class, UniqueAnnotations.create());
  }
}
