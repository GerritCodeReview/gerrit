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
import com.google.inject.TypeLiteral;
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
  protected <K, V> UnnamedCacheBinding core(final TypeLiteral<Cache<K, V>> type) {
    return core(Key.get(type));
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
  protected <K, V> NamedCacheBinding core(final TypeLiteral<Cache<K, V>> type,
      final String name) {
    return core(Key.get(type, Names.named(name))).name(name);
  }

  private <K, V> UnnamedCacheBinding core(final Key<Cache<K, V>> key) {
    final boolean disk = false;
    final CacheProvider<K, V> b = new CacheProvider<K, V>(disk);
    bind(key).toProvider(b);
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
  protected <K extends Serializable, V extends Serializable> UnnamedCacheBinding disk(
      final TypeLiteral<Cache<K, V>> type) {
    return disk(Key.get(type));
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
  protected <K extends Serializable, V extends Serializable> NamedCacheBinding disk(
      final TypeLiteral<Cache<K, V>> type, final String name) {
    return disk(Key.get(type, Names.named(name))).name(name);
  }

  private <K, V> UnnamedCacheBinding disk(final Key<Cache<K, V>> key) {
    final boolean disk = true;
    final CacheProvider<K, V> b = new CacheProvider<K, V>(disk);
    bind(key).toProvider(b);
    return b;
  }
}
