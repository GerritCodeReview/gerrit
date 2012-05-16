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

import static java.util.concurrent.TimeUnit.DAYS;

import com.google.gerrit.extensions.annotations.Exports;
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
   * Declare a named in-memory cache.
   *
   * @param <K> type of key used to lookup entries.
   * @param <V> type of value stored by the cache.
   * @param type type literal for the cache, this literal will be used to match
   *        injection sites. Injection sites are matched by this type literal
   *        and with {@code @Named} annotations.
   * @return binding to describe the cache.
   */
  protected <K, V> InMemoryCacheBinding<K, V> core(
      TypeLiteral<Cache<K, V>> type,
      String name) {
    Key<Cache<K, V>> key = Key.get(type, Names.named(name));
    InMemoryCacheProvider<K, V> m = new InMemoryCacheProvider<K, V>(name, this);
    bind(key)
      .toProvider(m)
      .in(Scopes.SINGLETON);
    bind(new TypeLiteral<Cache<?, ?>>() {})
      .annotatedWith(Exports.named(name))
      .to(key);
    return m.memoryLimit(1024).maxAge(90, DAYS);
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
  protected <K extends Serializable, V extends Serializable> DiskCacheBinding<K, V> disk(
      TypeLiteral<Cache<K, V>> type,
      String name) {
    DiskCacheProvider<K, V> d = new DiskCacheProvider<K, V>(name, this);
    bind(type)
        .annotatedWith(Names.named(name))
        .toProvider(d)
        .in(Scopes.SINGLETON);
    return d.memoryLimit(1024).maxAge(90, DAYS).diskLimit(16384);
  }

  <K, V> Provider<EntryCreator<K, V>> getEntryCreator(
      DiskCacheProvider<K, V> cp,
      Class<? extends EntryCreator<K, V>> type) {
    Key<EntryCreator<K, V>> key = newKey();
    bind(key).to(type).in(Scopes.SINGLETON);
    return getProvider(key);
  }

  <K, V> Provider<EntryCreator<K, V>> getEntryCreator(
      InMemoryCacheProvider<K, V> cp,
      Class<? extends EntryCreator<K, V>> type) {
    Key<EntryCreator<K, V>> key = newKey();
    bind(key).to(type).in(Scopes.SINGLETON);
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
