package com.google.gerrit.server.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheStats;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.metrics.CallbackMetric;
import com.google.gerrit.metrics.CallbackMetric1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Set;

@Singleton
public class CacheMetrics {
  @Inject
  public CacheMetrics(MetricMaker metrics,
      final DynamicMap<Cache<?, ?>> cacheMap) {
    Field<String> F_NAME = Field.ofString("cache_name");

    final CallbackMetric1<String, Long> memEnt =
        metrics.newCallbackMetric("caches/memory_cached", Long.class,
            new Description("Memory entries").setGauge().setUnit("entries"),
            F_NAME);
    final CallbackMetric1<String, Double> memHit =
        metrics.newCallbackMetric("caches/memory_hit_ratio", Double.class,
            new Description("Memory hit ratio").setGauge().setUnit("percent"),
            F_NAME);
    final CallbackMetric1<String, Long> memEvict =
        metrics.newCallbackMetric("caches/memory_eviction_count", Long.class,
            new Description("Memory eviction count").setGauge()
                .setUnit("evicted entries"),
            F_NAME);
    final CallbackMetric1<String, Long> perDiskEnt =
        metrics.newCallbackMetric("caches/disk_cached", Long.class,
            new Description("Disk entries used by persistent cache").setGauge()
                .setUnit("entries"),
            F_NAME);
    final CallbackMetric1<String, Double> perDiskHit =
        metrics.newCallbackMetric("caches/disk_hit_ratio", Double.class,
            new Description("Disk hit ratio for persistent cache").setGauge()
                .setUnit("percent"),
            F_NAME);

    final Set<CallbackMetric<?>> cacheMetrics =
        ImmutableSet.<CallbackMetric<?>> of(memEnt, memHit, memEvict,
            perDiskEnt, perDiskHit);

    metrics.newTrigger(cacheMetrics, new Runnable() {
      @Override
      public void run() {
        for (DynamicMap.Entry<Cache<?, ?>> e : cacheMap) {
          Cache<?, ?> c = e.getProvider().get();
          String name = metricNameOf(e);
          CacheStats cstats = c.stats();
          memEnt.set(name, c.size());
          memHit.set(name, cstats.hitRate() * 100);
          memEvict.set(name, cstats.evictionCount());
          if (c instanceof PersistentCache) {
            PersistentCache.DiskStats d = ((PersistentCache) c).diskStats();
            perDiskEnt.set(name, d.size());
            perDiskHit.set(name, hitRatio(d));
          }
        }
        for (CallbackMetric<?> cbm : cacheMetrics) {
          cbm.prune();
        }
      }
    });
  }

  private static double hitRatio(PersistentCache.DiskStats d) {
    if (d.requestCount() <= 0) {
      return 100;
    }
    return ((double) d.hitCount() / d.requestCount() * 100);
  }

  private static String metricNameOf(DynamicMap.Entry<Cache<?, ?>> e) {
    if ("gerrit".equals(e.getPluginName())) {
      return e.getExportName();
    } else {
      return String.format("plugin/%s/%s", e.getPluginName(),
          e.getExportName());
    }
  }
}
