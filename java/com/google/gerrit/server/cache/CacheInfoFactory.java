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
import com.google.common.cache.CacheStats;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.common.CacheInfo;
import com.google.gerrit.extensions.common.CacheInfo.CacheType;
import com.google.gerrit.extensions.common.CacheInfo.EntriesInfo;
import com.google.gerrit.extensions.common.CacheInfo.HitRatioInfo;

public class CacheInfoFactory {

  public static CacheInfo create(Cache<?, ?> cache) {
    return create(null, cache);
  }

  public static CacheInfo create(String name, Cache<?, ?> cache) {
    CacheInfo cacheInfo = new CacheInfo();
    cacheInfo.name = name;

    CacheStats stat = cache.stats();

    cacheInfo.entries = new EntriesInfo();
    cacheInfo.entries.setMem(cache.size());

    cacheInfo.averageGet = duration(stat.averageLoadPenalty());

    cacheInfo.hitRatio = new HitRatioInfo();
    cacheInfo.hitRatio.setMem(stat.hitCount(), stat.requestCount());

    if (cache instanceof PersistentCache) {
      cacheInfo.type = CacheType.DISK;
      PersistentCache.DiskStats diskStats = ((PersistentCache) cache).diskStats();
      cacheInfo.entries.setDisk(diskStats.size());
      cacheInfo.entries.setSpace(diskStats.space());
      cacheInfo.hitRatio.setDisk(diskStats.hitCount(), diskStats.requestCount());
    } else {
      cacheInfo.type = CacheType.MEM;
    }
    return cacheInfo;
  }

  @Nullable
  private static String duration(double ns) {
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
