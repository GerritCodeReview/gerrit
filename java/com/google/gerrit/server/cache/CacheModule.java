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
import com.google.gerrit.server.cache.serialize.JavaCacheSerializer;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.util.Types;
import java.lang.reflect.Type;

/** Miniature DSL to support binding {@link Cache} instances in Guice. */
public abstract class CacheModule extends FactoryModule {
  public static final String MEMORY_MODULE = "cache-memory";
  public static final String PERSISTENT_MODULE = "cache-persistent";

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
    CacheProvider<K, V> m = new CacheProvider<>(this, name, keyType, valType);
    bindCache(m, name, keyType, valType);
    return m;
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
  protected <K, V> PersistentCacheBinding<K, V> persist(
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
  protected <K, V> PersistentCacheBinding<K, V> persist(
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
  protected <K, V> PersistentCacheBinding<K, V> persist(
      String name, TypeLiteral<K> keyType, TypeLiteral<V> valType) {
    PersistentCacheProvider<K, V> m = new PersistentCacheProvider<>(this, name, keyType, valType);
    bindCache(m, name, keyType, valType);

    Type cacheDefType =
        Types.newParameterizedType(PersistentCacheDef.class, keyType.getType(), valType.getType());
    @SuppressWarnings("unchecked")
    Key<PersistentCacheDef<K, V>> cacheDefKey =
        (Key<PersistentCacheDef<K, V>>) Key.get(cacheDefType, Names.named(name));
    bind(cacheDefKey).toInstance(m);

    // TODO(dborowitz): Once default Java serialization is removed, leave no default.
    return m.version(0)
        .keySerializer(new JavaCacheSerializer<>())
        .valueSerializer(new JavaCacheSerializer<>());
  }

  private <K, V> void bindCache(
      CacheProvider<K, V> m, String name, TypeLiteral<K> keyType, TypeLiteral<V> valType) {
    Type type = Types.newParameterizedType(Cache.class, keyType.getType(), valType.getType());
    Named named = Names.named(name);

    @SuppressWarnings("unchecked")
    Key<Cache<K, V>> key = (Key<Cache<K, V>>) Key.get(type, named);
    bind(key).toProvider(m).asEagerSingleton();
    bind(ANY_CACHE).annotatedWith(Exports.named(name)).to(key);

    Type cacheDefType =
        Types.newParameterizedType(CacheDef.class, keyType.getType(), valType.getType());
    @SuppressWarnings("unchecked")
    Key<CacheDef<K, V>> cacheDefKey = (Key<CacheDef<K, V>>) Key.get(cacheDefType, named);
    bind(cacheDefKey).toInstance(m);

    m.maximumWeight(1024);
  }
}
