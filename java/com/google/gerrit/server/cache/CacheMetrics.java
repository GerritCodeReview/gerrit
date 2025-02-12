// Copyright (C) 2015 The Android Open Source Project
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
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.extensions.registration.PluginName;
import com.google.gerrit.metrics.CallbackMetric;
import com.google.gerrit.metrics.CallbackMetric1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.logging.Metadata;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

@Singleton
public class CacheMetrics {
  private static final Field<String> F_NAME =
      Field.ofString("cache_name", Metadata.Builder::cacheName)
          .description("The name of the cache.")
          .build();

  @Inject
  public CacheMetrics(
      MetricMaker metrics, DynamicMap<Cache<?, ?>> cacheMap, @GerritServerConfig Config config) {
    CallbackMetric1<String, Long> memEnt =
        metrics.newCallbackMetric(
            "caches/memory_cached",
            Long.class,
            new Description("Memory entries").setGauge().setUnit("entries"),
            F_NAME);
    CallbackMetric1<String, Double> memHit =
        metrics.newCallbackMetric(
            "caches/memory_hit_ratio",
            Double.class,
            new Description("Memory hit ratio").setGauge().setUnit("percent"),
            F_NAME);
    CallbackMetric1<String, Long> memEvict =
        metrics.newCallbackMetric(
            "caches/memory_eviction_count",
            Long.class,
            new Description("Memory eviction count").setGauge().setUnit("evicted entries"),
            F_NAME);
    CallbackMetric1<String, Long> perDiskEnt =
        metrics.newCallbackMetric(
            "caches/disk_cached",
            Long.class,
            new Description("Disk entries used by persistent cache").setGauge().setUnit("entries"),
            F_NAME);
    CallbackMetric1<String, Double> perDiskHit =
        metrics.newCallbackMetric(
            "caches/disk_hit_ratio",
            Double.class,
            new Description("Disk hit ratio for persistent cache").setGauge().setUnit("percent"),
            F_NAME);
    CallbackMetric1<String, Long> perDiskInvalid =
        metrics.newCallbackMetric(
            "caches/disk_invalidated_count",
            Long.class,
            new Description("Disk entries invalidated by persistent cache")
                .setGauge()
                .setUnit("invalidated entries"),
            F_NAME);

    ImmutableSet<CallbackMetric<?>> cacheMetrics =
        ImmutableSet.of(memEnt, memHit, memEvict, perDiskEnt, perDiskHit, perDiskInvalid);

    metrics.newTrigger(
        cacheMetrics,
        () -> {
          for (Extension<Cache<?, ?>> e : cacheMap) {
            Cache<?, ?> c = e.getProvider().get();
            String name = metricNameOf(e);
            CacheStats cstats = c.stats();
            memEnt.set(name, c.size());
            memHit.set(name, cstats.hitRate() * 100);
            memEvict.set(name, cstats.evictionCount());
            if (c instanceof PersistentCache
                && config.getBoolean("cache", "enableDiskStatMetrics", false)) {
              PersistentCache.DiskStats d = ((PersistentCache) c).diskStats();
              perDiskEnt.set(name, d.size());
              perDiskHit.set(name, hitRatio(d));
              perDiskInvalid.set(name, d.invalidatedCount());
            }
          }
          cacheMetrics.forEach(CallbackMetric::prune);
        });
  }

  private static double hitRatio(PersistentCache.DiskStats d) {
    if (d.requestCount() <= 0) {
      return 100;
    }
    return ((double) d.hitCount() / d.requestCount() * 100);
  }

  private static String metricNameOf(Extension<Cache<?, ?>> e) {
    if (PluginName.GERRIT.equals(e.getPluginName())) {
      return e.getExportName();
    }
    return String.format("plugin/%s/%s", e.getPluginName(), e.getExportName());
  }
}
