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

package com.google.gerrit.server.cache;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.cache.CacheBuilder;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

import java.util.concurrent.TimeUnit;

class GuavaCacheFactory implements InMemoryCachePool {
  private final Config config;

  @Inject
  GuavaCacheFactory(@GerritServerConfig Config config) {
    this.config = config;
  }

  public <K, V> com.google.common.cache.Cache<K, V> findCache(
      String name,
      long maximumSize,
      long expiresAfterWrite) {
    CacheBuilder<K, V> builder = newCacheBuilder();

    long size = config.getLong("cache", name, "memoryLimit", maximumSize);
    builder.maximumSize(size);

    long expires = lookupMaxAgeSeconds(name, expiresAfterWrite);
    if (0 < expires) {
      builder.expireAfterWrite(expires, TimeUnit.SECONDS);
    }

    return builder.build();
  }

  private long lookupMaxAgeSeconds(String name, long maxAge) {
    return MINUTES.toSeconds(ConfigUtil.getTimeUnit(config,
        "cache", name, "maxAge",
        SECONDS.toMinutes(maxAge), MINUTES));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static <K, V> CacheBuilder<K, V> newCacheBuilder() {
    CacheBuilder builder = CacheBuilder.newBuilder();
    return builder;
  }
}
