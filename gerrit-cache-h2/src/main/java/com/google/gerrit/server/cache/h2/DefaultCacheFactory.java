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

package com.google.gerrit.server.cache.h2;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.Weigher;
import com.github.benmanes.caffeine.guava.CaffeinatedGuava;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalNotification;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.cache.CacheBinding;
import com.google.gerrit.server.cache.ForwardingRemovalListener;
import com.google.gerrit.server.cache.MemoryCacheFactory;
import com.google.gerrit.server.cache.PersistentCacheFactory;
import com.google.gerrit.server.cache.h2.H2CacheImpl.ValueHolder;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

public class DefaultCacheFactory implements MemoryCacheFactory {
  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      factory(ForwardingRemovalListener.Factory.class);
      bind(DefaultCacheFactory.class);
      bind(MemoryCacheFactory.class).to(DefaultCacheFactory.class);
      bind(PersistentCacheFactory.class).to(H2CacheFactory.class);
      listener().to(H2CacheFactory.class);
    }
  }

  private final Config cfg;
  private final ForwardingRemovalListener.Factory forwardingRemovalListenerFactory;

  @Inject
  public DefaultCacheFactory(
      @GerritServerConfig Config config,
      ForwardingRemovalListener.Factory forwardingRemovalListenerFactory) {
    this.cfg = config;
    this.forwardingRemovalListenerFactory = forwardingRemovalListenerFactory;
  }

  @Override
  public <K, V> Cache<K, V> build(CacheBinding<K, V> def) {
    return CaffeinatedGuava.build(create(def, false));
  }

  @Override
  public <K, V> LoadingCache<K, V> build(CacheBinding<K, V> def, CacheLoader<K, V> loader) {
    return CaffeinatedGuava.build(create(def, false), loader);
  }

  @SuppressWarnings("unchecked")
  <K, V> Caffeine<K, V> create(CacheBinding<K, V> def, boolean unwrapValueHolder) {
    Caffeine<K, V> builder = newCacheBuilder();
    builder.recordStats();
    builder.maximumWeight(cfg.getLong("cache", def.name(), "memoryLimit", def.maximumWeight()));

    builder =
        builder.removalListener(
            new CaffeineToGuavaRemovalListener<K, V>(
                forwardingRemovalListenerFactory.create(def.name())));

    Weigher<K, V> weigher = null;
    com.google.common.cache.Weigher<K, V> guavaWeigher = def.weigher();
    if (guavaWeigher != null) {
      if (unwrapValueHolder) {
        weigher =
            (Weigher<K, V>)
                new Weigher<K, ValueHolder<V>>() {
                  @Override
                  public int weigh(K key, ValueHolder<V> value) {
                    return guavaWeigher.weigh(key, value.value);
                  }
                };
      } else {
        weigher =
            new Weigher<K, V>() {
              @Override
              public int weigh(K key, V value) {
                return guavaWeigher.weigh(key, value);
              }
            };
      }
    } else {
      weigher = Weigher.singletonWeigher();
    }
    builder.weigher(weigher);

    Long age = def.expireAfterWrite(TimeUnit.SECONDS);
    if (has(def.name(), "maxAge")) {
      builder.expireAfterWrite(
          ConfigUtil.getTimeUnit(
              cfg, "cache", def.name(), "maxAge", age != null ? age : 0, TimeUnit.SECONDS),
          TimeUnit.SECONDS);
    } else if (age != null) {
      builder.expireAfterWrite(age, TimeUnit.SECONDS);
    }

    return builder;
  }

  private boolean has(String name, String var) {
    return !Strings.isNullOrEmpty(cfg.getString("cache", name, var));
  }

  @SuppressWarnings("unchecked")
  private static <K, V> Caffeine<K, V> newCacheBuilder() {
    return (Caffeine<K, V>) Caffeine.newBuilder();
  }

  private static class CaffeineToGuavaRemovalListener<K, V> implements RemovalListener<K, V> {

    private com.google.common.cache.RemovalListener<K, V> guavalistener;

    private CaffeineToGuavaRemovalListener(
        com.google.common.cache.RemovalListener<K, V> guavalistener) {
      this.guavalistener = guavalistener;
    }

    @Override
    public void onRemoval(K key, V value, RemovalCause cause) {
      guavalistener.onRemoval(
          RemovalNotification.create(
              key, value, com.google.common.cache.RemovalCause.valueOf(cause.name())));
    }
  }
}
