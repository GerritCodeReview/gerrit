// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.config;

import com.google.common.cache.Cache;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;

public class CacheResource extends ConfigResource {
  public static final TypeLiteral<RestView<CacheResource>> CACHE_KIND =
      new TypeLiteral<RestView<CacheResource>>() {};

  private final String name;
  private final Provider<Cache<?, ?>> cacheProvider;

  public CacheResource(String pluginName, String cacheName, Provider<Cache<?, ?>> cacheProvider) {
    this.name = cacheNameOf(pluginName, cacheName);
    this.cacheProvider = cacheProvider;
  }

  public CacheResource(String pluginName, String cacheName, Cache<?, ?> cache) {
    this(
        pluginName,
        cacheName,
        new Provider<Cache<?, ?>>() {
          @Override
          public Cache<?, ?> get() {
            return cache;
          }
        });
  }

  public String getName() {
    return name;
  }

  public Cache<?, ?> getCache() {
    return cacheProvider.get();
  }

  public static String cacheNameOf(String plugin, String name) {
    if ("gerrit".equals(plugin)) {
      return name;
    }
    return plugin + "-" + name;
  }
}
