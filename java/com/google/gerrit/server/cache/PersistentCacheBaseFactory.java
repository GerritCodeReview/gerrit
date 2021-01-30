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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.lib.Config;

/**
 * Base class for persistent cache factory. If the cache.directory property is unset, or disk limit
 * is zero or negative, it will fall back to in-memory only caches.
 */
public abstract class PersistentCacheBaseFactory implements PersistentCacheFactory {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected final MemoryCacheFactory memCacheFactory;
  protected final Path cacheDir;
  protected final Config config;

  public PersistentCacheBaseFactory(
      MemoryCacheFactory memCacheFactory, @GerritServerConfig Config config, SitePaths site) {
    this.cacheDir = getCacheDir(site, config.getString("cache", null, "directory"));
    this.memCacheFactory = memCacheFactory;
    this.config = config;
  }

  protected abstract <K, V> Cache<K, V> buildImpl(
      PersistentCacheDef<K, V> in, long diskLimit, CacheBackend backend);

  protected abstract <K, V> LoadingCache<K, V> buildImpl(
      PersistentCacheDef<K, V> in, CacheLoader<K, V> loader, long diskLimit, CacheBackend backend);

  @Override
  public <K, V> Cache<K, V> build(PersistentCacheDef<K, V> in, CacheBackend backend) {
    long limit = getDiskLimit(in);

    if (isInMemoryCache(limit)) {
      return memCacheFactory.build(in, backend);
    }

    return buildImpl(in, limit, backend);
  }

  @Override
  public <K, V> LoadingCache<K, V> build(
      PersistentCacheDef<K, V> in, CacheLoader<K, V> loader, CacheBackend backend) {
    long limit = getDiskLimit(in);

    if (isInMemoryCache(limit)) {
      return memCacheFactory.build(in, loader, backend);
    }

    return buildImpl(in, loader, limit, backend);
  }

  private <K, V> long getDiskLimit(PersistentCacheDef<K, V> in) {
    return config.getLong("cache", in.configKey(), "diskLimit", in.diskLimit());
  }

  private <K, V> boolean isInMemoryCache(long diskLimit) {
    return cacheDir == null || diskLimit <= 0;
  }

  private static Path getCacheDir(SitePaths site, String name) {
    if (name == null) {
      return null;
    }
    Path loc = site.resolve(name);
    if (!Files.exists(loc)) {
      try {
        Files.createDirectories(loc);
      } catch (IOException e) {
        logger.atWarning().log("Can't create disk cache: %s", loc.toAbsolutePath());
        return null;
      }
    }
    if (!Files.isWritable(loc)) {
      logger.atWarning().log("Can't write to disk cache: %s", loc.toAbsolutePath());
      return null;
    }
    logger.atInfo().log("Enabling disk cache %s", loc.toAbsolutePath());
    return loc;
  }
}
