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
import com.google.common.cache.CacheStats;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.cache.PersistentCache;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.Map;
import java.util.SortedMap;
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
    for (Map.Entry<String, Cache<?,?>> entry : getCaches().entrySet()) {
      cacheInfos.put(entry.getKey(), new CacheInfo(entry.getValue()));
    }
    return cacheInfos;
  }

  private Map<String, Cache<?, ?>> getCaches() {
    SortedMap<String, Cache<?, ?>> m = Maps.newTreeMap();

    // core caches
    for (Map.Entry<String, Provider<Cache<?, ?>>> e :
        cacheMap.byPlugin("gerrit").entrySet()) {
      m.put(cacheNameOf("gerrit", e.getKey()), e.getValue().get());
    }

    // plugin caches
    for (DynamicMap.Entry<Cache<?, ?>> e : cacheMap) {
      if (!"gerrit".equals(e.getPluginName())) {
        m.put(cacheNameOf(e.getPluginName(), e.getExportName()),
            e.getProvider().get());
      }
    }

    return m;
  }

  private static String cacheNameOf(String plugin, String name) {
    if ("gerrit".equals(plugin)) {
      return name;
    } else {
      return plugin + "-" + name;
    }
  }

  public enum CacheType {
    MEM, DISK;
  }

  public static class CacheInfo {
    public String name;
    public CacheType type;
    public EntriesInfo entries;
    public String averageGet;
    public HitRationInfo hitRatio;

    public CacheInfo(Cache<?,?> cache) {
      CacheStats stat = cache.stats();

      entries = new EntriesInfo();
      entries.setMem(cache.size());

      averageGet = duration(stat.averageLoadPenalty());

      hitRatio = new HitRationInfo();
      hitRatio.setMem(stat.hitCount(), stat.requestCount());

      if (cache instanceof PersistentCache) {
        type = CacheType.DISK;
        PersistentCache.DiskStats diskStats =
            ((PersistentCache) cache).diskStats();
        entries.setDisk(diskStats.size());
        entries.setSpace(diskStats.space());
        hitRatio.setDisk(diskStats.hitCount(), diskStats.requestCount());
      }
    }

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
        suffix = "s ";
      }
      return String.format("%4.1f%s", ns, suffix).trim();
    }
  }

  public static class EntriesInfo {
    public Long mem;
    public Long disk;
    public String space;

    public void setMem(long mem) {
      this.mem = mem != 0 ? mem : null;
    }

    public void setDisk(long disk) {
      this.disk = disk != 0 ? disk : null;
    }

    public void setSpace(double value) {
      space = bytes(value);
    }

    private static String bytes(double value) {
      value /= 1024;
      String suffix = "k";

      if (value > 1024) {
        value /= 1024;
        suffix = "m";
      }
      if (value > 1024) {
        value /= 1024;
        suffix = "g";
      }
      return String.format("%1$6.2f%2$s", value, suffix).trim();
    }
  }

  public static class HitRationInfo {
    public Integer mem;
    public Integer disk;

    public void setMem(long value, long total) {
      mem = percent(value, total);
    }

    public void setDisk(long value, long total) {
      disk = percent(value, total);
    }

    private static Integer percent(long value, long total) {
      if (total <= 0) {
        return null;
      }
      return (int) ((100 * value) / total);
    }
  }
}
