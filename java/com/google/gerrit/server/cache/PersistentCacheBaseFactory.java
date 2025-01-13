// Copyright (C) 2021 The Android Open Source Project
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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.GerritServerConfig;
import java.nio.file.Path;
import org.eclipse.jgit.lib.Config;

/**
 * Base class for persistent cache factory. If the cacheDir is unset, or disk limit is zero or
 * negative, it will fall back to in-memory only caches.
 */
public abstract class PersistentCacheBaseFactory implements PersistentCacheFactory {
  protected final MemoryCacheFactory memCacheFactory;
  protected final Path cacheDir;
  protected boolean diskEnabled;
  protected final Config config;

  public PersistentCacheBaseFactory(
      MemoryCacheFactory memCacheFactory,
      @GerritServerConfig Config config,
      @Nullable Path cacheDir) {
    this.cacheDir = cacheDir;
    this.diskEnabled = cacheDir != null;
    this.memCacheFactory = memCacheFactory;
    this.config = config;
  }

  protected abstract <K, V> Cache<K, V> buildImpl(PersistentCacheDef<K, V> in, long diskLimit);

  protected abstract <K, V> LoadingCache<K, V> buildImpl(
      PersistentCacheDef<K, V> in, CacheLoader<K, V> loader, long diskLimit);

  @Override
  public <K, V> Cache<K, V> build(PersistentCacheDef<K, V> in) {
    long limit = getDiskLimit(in);

    if (isInMemoryCache(limit)) {
      return memCacheFactory.build(in);
    }

    return buildImpl(in, limit);
  }

  @Override
  public <K, V> LoadingCache<K, V> build(PersistentCacheDef<K, V> in, CacheLoader<K, V> loader) {
    long limit = getDiskLimit(in);

    if (isInMemoryCache(limit)) {
      return memCacheFactory.build(in, loader);
    }

    return buildImpl(in, loader, limit);
  }

  private <K, V> long getDiskLimit(PersistentCacheDef<K, V> in) {
    return config.getLong("cache", in.configKey(), "diskLimit", in.diskLimit());
  }

  private <K, V> boolean isInMemoryCache(long diskLimit) {
    return !diskEnabled || diskLimit <= 0;
  }
}
