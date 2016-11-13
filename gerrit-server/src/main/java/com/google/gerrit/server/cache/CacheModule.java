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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.google.inject.util.Types;
import java.io.Serializable;
import java.lang.reflect.Type;

/** Miniature DSL to support binding {@link Cache} instances in Guice. */
public abstract class CacheModule extends FactoryModule {
  private static final TypeLiteral<Cache<?, ?>> ANY_CACHE = new TypeLiteral<Cache<?, ?>>() {};

  /**
   * Declare a named in-memory cache.
   *
   * @param <K> type of key used to lookup entries.
   * @param <V> type of value stored by the cache.
   * @return binding to describe the cache.
   */
  protected <K, V> CacheBinding<K, V> cache(String name, Class<K> keyType, Class<V> valType) {
    return cache(name, TypeLiteral.get(keyType), TypeLiteral.get(valType));
  }

  /**
   * Declare a named in-memory cache.
   *
   * @param <K> type of key used to lookup entries.
   * @param <V> type of value stored by the cache.
   * @return binding to describe the cache.
   */
  protected <K, V> CacheBinding<K, V> cache(String name, Class<K> keyType, TypeLiteral<V> valType) {
    return cache(name, TypeLiteral.get(keyType), valType);
  }

  /**
   * Declare a named in-memory cache.
   *
   * @param <K> type of key used to lookup entries.
   * @param <V> type of value stored by the cache.
   * @return binding to describe the cache.
   */
  protected <K, V> CacheBinding<K, V> cache(
      String name, TypeLiteral<K> keyType, TypeLiteral<V> valType) {
    Type type = Types.newParameterizedType(Cache.class, keyType.getType(), valType.getType());

    @SuppressWarnings("unchecked")
    Key<Cache<K, V>> key = (Key<Cache<K, V>>) Key.get(type, Names.named(name));

    CacheProvider<K, V> m = new CacheProvider<>(this, name, keyType, valType);
    bind(key).toProvider(m).asEagerSingleton();
    bind(ANY_CACHE).annotatedWith(Exports.named(name)).to(key);
    return m.maximumWeight(1024);
  }

  <K, V> Provider<CacheLoader<K, V>> bindCacheLoader(
      CacheProvider<K, V> m, Class<? extends CacheLoader<K, V>> impl) {
    Type type =
        Types.newParameterizedType(Cache.class, m.keyType().getType(), m.valueType().getType());

    Type loadingType =
        Types.newParameterizedType(
            LoadingCache.class, m.keyType().getType(), m.valueType().getType());

    Type loaderType =
        Types.newParameterizedType(
            CacheLoader.class, m.keyType().getType(), m.valueType().getType());

    @SuppressWarnings("unchecked")
    Key<LoadingCache<K, V>> key = (Key<LoadingCache<K, V>>) Key.get(type, Names.named(m.name));

    @SuppressWarnings("unchecked")
    Key<LoadingCache<K, V>> loadingKey =
        (Key<LoadingCache<K, V>>) Key.get(loadingType, Names.named(m.name));

    @SuppressWarnings("unchecked")
    Key<CacheLoader<K, V>> loaderKey =
        (Key<CacheLoader<K, V>>) Key.get(loaderType, Names.named(m.name));

    bind(loaderKey).to(impl).in(Scopes.SINGLETON);
    bind(loadingKey).to(key);
    return getProvider(loaderKey);
  }

  <K, V> Provider<Weigher<K, V>> bindWeigher(
      CacheProvider<K, V> m, Class<? extends Weigher<K, V>> impl) {
    Type weigherType =
        Types.newParameterizedType(Weigher.class, m.keyType().getType(), m.valueType().getType());

    @SuppressWarnings("unchecked")
    Key<Weigher<K, V>> key = (Key<Weigher<K, V>>) Key.get(weigherType, Names.named(m.name));

    bind(key).to(impl).in(Scopes.SINGLETON);
    return getProvider(key);
  }

  /**
   * Declare a named in-memory/on-disk cache.
   *
   * @param <K> type of key used to lookup entries.
   * @param <V> type of value stored by the cache.
   * @return binding to describe the cache.
   */
  protected <K extends Serializable, V extends Serializable> CacheBinding<K, V> persist(
      String name, Class<K> keyType, Class<V> valType) {
    return persist(name, TypeLiteral.get(keyType), TypeLiteral.get(valType));
  }

  /**
   * Declare a named in-memory/on-disk cache.
   *
   * @param <K> type of key used to lookup entries.
   * @param <V> type of value stored by the cache.
   * @return binding to describe the cache.
   */
  protected <K extends Serializable, V extends Serializable> CacheBinding<K, V> persist(
      String name, Class<K> keyType, TypeLiteral<V> valType) {
    return persist(name, TypeLiteral.get(keyType), valType);
  }

  /**
   * Declare a named in-memory/on-disk cache.
   *
   * @param <K> type of key used to lookup entries.
   * @param <V> type of value stored by the cache.
   * @return binding to describe the cache.
   */
  protected <K extends Serializable, V extends Serializable> CacheBinding<K, V> persist(
      String name, TypeLiteral<K> keyType, TypeLiteral<V> valType) {
    return ((CacheProvider<K, V>) cache(name, keyType, valType)).persist(true);
  }
}
