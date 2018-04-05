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

package com.google.gerrit.server.cache.mem;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.gerrit.server.cache.CacheBinding;
import com.google.gerrit.server.cache.ForwardingRemovalListener;
import com.google.gerrit.server.cache.MemoryCacheFactory;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

class DefaultMemoryCacheFactory implements MemoryCacheFactory {
  private final Config cfg;
  private final ForwardingRemovalListener.Factory forwardingRemovalListenerFactory;

  @Inject
  DefaultMemoryCacheFactory(
      @GerritServerConfig Config config,
      ForwardingRemovalListener.Factory forwardingRemovalListenerFactory) {
    this.cfg = config;
    this.forwardingRemovalListenerFactory = forwardingRemovalListenerFactory;
  }

  @Override
  public <K, V> Cache<K, V> build(CacheBinding<K, V> def) {
    return create(def).build();
  }

  @Override
  public <K, V> LoadingCache<K, V> build(CacheBinding<K, V> def, CacheLoader<K, V> loader) {
    return create(def).build(loader);
  }

  @SuppressWarnings("unchecked")
  private <K, V> CacheBuilder<K, V> create(CacheBinding<K, V> def) {
    CacheBuilder<K, V> builder = newCacheBuilder();
    builder.recordStats();
    builder.maximumWeight(
        cfg.getLong("cache", def.configKey(), "memoryLimit", def.maximumWeight()));

    builder = builder.removalListener(forwardingRemovalListenerFactory.create(def.name()));

    Weigher<K, V> weigher = def.weigher();
    if (weigher == null) {
      weigher = unitWeight();
    }
    builder.weigher(weigher);

    Long age = def.expireAfterWrite(TimeUnit.SECONDS);
    if (has(def.configKey(), "maxAge")) {
      builder.expireAfterWrite(
          ConfigUtil.getTimeUnit(
              cfg, "cache", def.configKey(), "maxAge", age != null ? age : 0, TimeUnit.SECONDS),
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
  private static <K, V> CacheBuilder<K, V> newCacheBuilder() {
    return (CacheBuilder<K, V>) CacheBuilder.newBuilder();
  }

  private static <K, V> Weigher<K, V> unitWeight() {
    return new Weigher<K, V>() {
      @Override
      public int weigh(K key, V value) {
        return 1;
      }
    };
  }
}
