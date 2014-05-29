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

import static com.google.gerrit.server.config.CacheResource.cacheNameOf;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheStats;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.common.CacheInfo;
import com.google.gerrit.extensions.common.CacheInfo.EntriesInfo;
import com.google.gerrit.extensions.common.CacheInfo.HitRatioInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.cache.PersistentCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Map;
import java.util.TreeMap;

@RequiresCapability(GlobalCapability.VIEW_CACHES)
@Singleton
public class ListCaches implements RestReadView<ConfigResource> {
  private final DynamicMap<Cache<?, ?>> cacheMap;

  @Inject
  public ListCaches(DynamicMap<Cache<?, ?>> cacheMap) {
    this.cacheMap = cacheMap;
  }

  @Override
  public Map<String, CacheInfo> apply(ConfigResource rsrc) {
    Map<String, CacheInfo> cacheInfos = new TreeMap<>();
    for (DynamicMap.Entry<Cache<?, ?>> e : cacheMap) {
      String name = cacheNameOf(e.getPluginName(), e.getExportName());
      cacheInfos.put(name,
          newCacheInfo(name, e.getProvider().get()));
    }
    return cacheInfos;
  }

  static CacheInfo newCacheInfo(Cache<?,?> cache) {
    return newCacheInfo(null, cache);
  }

  static CacheInfo newCacheInfo(String name, Cache<?,?> cache) {
    CacheInfo i = new CacheInfo();
    i.name = name;

    CacheStats stat = cache.stats();

    i.entries = new EntriesInfo();
    i.entries.setMem(cache.size());

    i.averageGet = duration(stat.averageLoadPenalty());

    i.hitRatio = new HitRatioInfo();
    i.hitRatio.setMem(stat.hitCount(), stat.requestCount());

    if (cache instanceof PersistentCache) {
      i.type = CacheInfo.CacheType.DISK;
      PersistentCache.DiskStats diskStats =
          ((PersistentCache) cache).diskStats();
      i.entries.setDisk(diskStats.size());
      i.entries.setSpace(diskStats.space());
      i.hitRatio.setDisk(diskStats.hitCount(), diskStats.requestCount());
    } else {
      i.type = CacheInfo.CacheType.MEM;
    }
    return i;
  }

  static String duration(double ns) {
    if (ns < 0.5) {
      return null;
    }
    String suffix = "ns";
    if (ns >= 1000.0) {
      ns /= 1000.0;
      suffix = "us";
    }
    if (ns >= 1000.0) {
      ns /= 1000.0;
      suffix = "ms";
    }
    if (ns >= 1000.0) {
      ns /= 1000.0;
      suffix = "s";
    }
    return String.format("%4.1f%s", ns, suffix).trim();
  }
}
