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
import com.google.inject.Inject;
import com.google.inject.Singleton;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;

/** Provides the {@link DiffCacheContent}. */
@Singleton
public class DiffCache {
  private final SelfPopulatingCache self;

  @Inject
  DiffCache(final CacheManager mgr, final GerritServer gs) {
    final Cache dc = mgr.getCache("diff");
    self = new SelfPopulatingCache(dc, new DiffCacheEntryFactory(gs));
    mgr.replaceCacheWithDecoratedCache(dc, self);
  }

  public String getName() {
    return self.getName();
  }

  public Element get(final DiffCacheKey key) {
    return self.get(key);
  }

  public void put(final DiffCacheKey k, final DiffCacheContent c) {
    self.put(new Element(k, c));
  }

  public void flush() throws IllegalStateException, CacheException {
    self.flush();
  }
}
