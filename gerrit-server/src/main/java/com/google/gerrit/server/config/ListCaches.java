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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheStats;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.common.CacheInfo;
import com.google.gerrit.extensions.common.CacheInfo.CacheType;
import com.google.gerrit.extensions.common.CacheInfo.EntriesInfo;
import com.google.gerrit.extensions.common.CacheInfo.HitRatioInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.cache.PersistentCache;
import com.google.inject.Inject;

import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RequiresCapability(GlobalCapability.VIEW_CACHES)
public class ListCaches implements RestReadView<ConfigResource> {
  private final DynamicMap<Cache<?, ?>> cacheMap;

  public static enum OutputFormat {
    LIST, TEXT_LIST;
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

  @Override
  public Object apply(ConfigResource rsrc) {
    if (format == null) {
      Map<String, CacheInfo> cacheInfos = new TreeMap<>();
      for (DynamicMap.Entry<Cache<?, ?>> e : cacheMap) {
        String name = cacheNameOf(e.getPluginName(), e.getExportName());
        cacheInfos.put(name, newCacheInfo(name, e.getProvider().get()));
      }
      return cacheInfos;
    } else {
      List<String> cacheNames = new ArrayList<>();
      for (DynamicMap.Entry<Cache<?, ?>> e : cacheMap) {
        cacheNames.add(cacheNameOf(e.getPluginName(), e.getExportName()));
      }
      Collections.sort(cacheNames);

      if (OutputFormat.TEXT_LIST.equals(format)) {
        return BinaryResult.create(Joiner.on('\n').join(cacheNames))
            .base64()
            .setContentType("text/plain")
            .setCharacterEncoding(UTF_8.name());
      } else {
        return cacheNames;
      }
    }
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
      i.type = CacheType.DISK;
      PersistentCache.DiskStats diskStats =
          ((PersistentCache) cache).diskStats();
      i.entries.setDisk(diskStats.size());
      i.entries.setSpace(diskStats.space());
      i.hitRatio.setDisk(diskStats.hitCount(), diskStats.requestCount());
    } else {
      i. type = CacheType.MEM;
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
