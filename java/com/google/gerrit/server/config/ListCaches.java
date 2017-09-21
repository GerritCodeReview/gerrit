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

import static com.google.gerrit.common.data.GlobalCapability.MAINTAIN_SERVER;
import static com.google.gerrit.common.data.GlobalCapability.VIEW_CACHES;
import static com.google.gerrit.server.config.CacheResource.cacheNameOf;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheStats;
import com.google.gerrit.extensions.annotations.RequiresAnyCapability;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.cache.PersistentCache;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.kohsuke.args4j.Option;

@RequiresAnyCapability({VIEW_CACHES, MAINTAIN_SERVER})
public class ListCaches implements RestReadView<ConfigResource> {
  private final DynamicMap<Cache<?, ?>> cacheMap;

  public enum OutputFormat {
    LIST,
    TEXT_LIST
  }

  @Option(name = "--format", usage = "output format")
  private OutputFormat format;

  public ListCaches setFormat(OutputFormat format) {
    this.format = format;
    return this;
  }

  @Inject
  public ListCaches(DynamicMap<Cache<?, ?>> cacheMap) {
    this.cacheMap = cacheMap;
  }

  public Map<String, CacheInfo> getCacheInfos() {
    Map<String, CacheInfo> cacheInfos = new TreeMap<>();
    for (DynamicMap.Entry<Cache<?, ?>> e : cacheMap) {
      cacheInfos.put(
          cacheNameOf(e.getPluginName(), e.getExportName()), new CacheInfo(e.getProvider().get()));
    }
    return cacheInfos;
  }

  @Override
  public Object apply(ConfigResource rsrc) {
    if (format == null) {
      return getCacheInfos();
    }
    List<String> cacheNames = new ArrayList<>();
    for (DynamicMap.Entry<Cache<?, ?>> e : cacheMap) {
      cacheNames.add(cacheNameOf(e.getPluginName(), e.getExportName()));
    }
    Collections.sort(cacheNames);

    if (OutputFormat.TEXT_LIST.equals(format)) {
      return BinaryResult.create(Joiner.on('\n').join(cacheNames))
          .base64()
          .setContentType("text/plain")
          .setCharacterEncoding(UTF_8);
    }
    return cacheNames;
  }

  public enum CacheType {
    MEM,
    DISK
  }

  public static class CacheInfo {
    public String name;
    public CacheType type;
    public EntriesInfo entries;
    public String averageGet;
    public HitRatioInfo hitRatio;

    public CacheInfo(Cache<?, ?> cache) {
      this(null, cache);
    }

    public CacheInfo(String name, Cache<?, ?> cache) {
      this.name = name;

      CacheStats stat = cache.stats();

      entries = new EntriesInfo();
      entries.setMem(cache.size());

      averageGet = duration(stat.averageLoadPenalty());

      hitRatio = new HitRatioInfo();
      hitRatio.setMem(stat.hitCount(), stat.requestCount());

      if (cache instanceof PersistentCache) {
        type = CacheType.DISK;
        PersistentCache.DiskStats diskStats = ((PersistentCache) cache).diskStats();
        entries.setDisk(diskStats.size());
        entries.setSpace(diskStats.space());
        hitRatio.setDisk(diskStats.hitCount(), diskStats.requestCount());
      } else {
        type = CacheType.MEM;
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
        suffix = "s";
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

  public static class HitRatioInfo {
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
