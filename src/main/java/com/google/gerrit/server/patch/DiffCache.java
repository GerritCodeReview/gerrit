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

package com.google.gerrit.server.patch;

import com.google.gerrit.server.GerritServer;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.SelfPopulatingCache;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;


/** Provides the {@link DiffCacheContent}. */
@Singleton
public class DiffCache {
  private static final String CACHE_NAME = "diff";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<DiffCacheKey, DiffCacheContent>> type =
            new TypeLiteral<Cache<DiffCacheKey, DiffCacheContent>>() {};
        disk(type, CACHE_NAME);
        bind(DiffCache.class);
      }
    };
  }

  private final SelfPopulatingCache<DiffCacheKey, DiffCacheContent> self;

  @Inject
  DiffCache(final GerritServer gs,
      @Named(CACHE_NAME) final Cache<DiffCacheKey, DiffCacheContent> raw) {
    final DiffCacheEntryFactory f = new DiffCacheEntryFactory(gs);
    self = new SelfPopulatingCache<DiffCacheKey, DiffCacheContent>(raw) {
      @Override
      protected DiffCacheContent createEntry(final DiffCacheKey key)
          throws Exception {
        return f.createEntry(key);
      }
    };
  }

  public DiffCacheContent get(final DiffCacheKey key) {
    return self.get(key);
  }

  public void put(final DiffCacheKey k, final DiffCacheContent c) {
    self.put(k, c);
  }
}
