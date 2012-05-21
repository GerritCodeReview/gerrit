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

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.cache.CacheBinding;
import com.google.gerrit.server.cache.MemoryCacheFactory;
import com.google.gerrit.server.cache.PersistentCacheFactory;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

import java.util.concurrent.TimeUnit;

public class DefaultCacheFactory implements MemoryCacheFactory {
  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      bind(DefaultCacheFactory.class);
      bind(MemoryCacheFactory.class).to(DefaultCacheFactory.class);
      bind(PersistentCacheFactory.class).to(H2CacheFactory.class);
      listener().to(H2CacheFactory.class);
    }
  }

  private final Config cfg;

  @Inject
  public DefaultCacheFactory(@GerritServerConfig Config config) {
    this.cfg = config;
  }

  @Override
  public <K, V> Cache<K, V> build(CacheBinding<K, V> def) {
    return create(def).build();
  }

  @Override
  public <K, V> LoadingCache<K, V> build(
      CacheBinding<K, V> def,
      CacheLoader<K, V> loader) {
    return create(def).build(loader);
  }

  @SuppressWarnings("unchecked")
  private <K, V> CacheBuilder<K, V> create(CacheBinding<K, V> def) {
    CacheBuilder<K,V> builder = newCacheBuilder();
    builder.maximumWeight(cfg.getLong(
        "cache", def.name(), "memoryLimit",
        def.maximumWeight()));
    builder.weigher((Weigher<K, V>) Objects.firstNonNull(
        def.weigher(),
        unitWeight()));

    Long age = def.expireAfterWrite(TimeUnit.SECONDS);
    if (has(def.name(), "maxAge")) {
      builder.expireAfterWrite(ConfigUtil.getTimeUnit(cfg,
          "cache", def.name(), "maxAge",
          age != null ? age : 0,
          TimeUnit.SECONDS), TimeUnit.SECONDS);
    } else if (age != null) {
      builder.expireAfterWrite(age, TimeUnit.SECONDS);
    }

    return builder;
  }

  private boolean has(String name, String var) {
    return !Strings.isNullOrEmpty(cfg.getString("cache", name, var));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static <K, V> CacheBuilder<K, V> newCacheBuilder() {
    CacheBuilder builder = CacheBuilder.newBuilder();
    return builder;
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
