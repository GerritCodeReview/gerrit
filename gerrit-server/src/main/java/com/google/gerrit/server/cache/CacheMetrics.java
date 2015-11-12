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
  public CacheMetrics(final MetricMaker metrics,
      final DynamicMap<Cache<?, ?>> cacheMap) {
    final CallbackMetric1<String, Long> memEnt =
        metricLong(metrics, "caches/memory_cached", "Memory entries", "entries");
    final CallbackMetric1<String, Double> memHit =
        metricDouble(metrics, "caches/memory_hit_ratio", "Memory hit ratio", "percent");
    final CallbackMetric1<String, Long> memEvict =
        metricLong(metrics, "caches/memory_eviction_count", "Memory eviction count", "evicted entries");
    final CallbackMetric1<String, Long> perDiskEnt =
        metricLong(metrics, "caches/disk_cached",
            "Disk entries used by persistent cache", "entries");
    final CallbackMetric1<String, Double> perDiskHit =
        metricDouble(metrics, "caches/disk_hit_ratio",
            "Disk hit ratio for persistent cache", "percent");

    final Set<CallbackMetric<?>> cacheMetrics = ImmutableSet.<CallbackMetric<?>>of(
        memEnt, memHit, memEvict, perDiskEnt, perDiskHit);

    metrics.newTrigger(cacheMetrics, new Runnable() {
      @Override
      public void run() {
        for (DynamicMap.Entry<Cache<?, ?>> e : cacheMap) {
          Cache<?,?> c = e.getProvider().get();
          String name = metricNameOf(e);
          CacheStats cstats = c.stats();
          memEnt.set(name, c.size());
          memHit.set(name, cstats.hitRate() * 100);
          memEvict.set(name, cstats.evictionCount());
          if (c instanceof PersistentCache) {
            PersistentCache.DiskStats d =
                ((PersistentCache) c).diskStats();
            perDiskEnt.set(name, d.size());
            perDiskHit.set(name, hitRatio(d));
          }
        }
        for (CallbackMetric<?> cbm  : cacheMetrics) {
          cbm.prune();
        }
      }
    });
  }

  private static double hitRatio(PersistentCache.DiskStats d) {
    if (d.requestCount() <= 0) {
      return 100;
    }
    return ((double)d.hitCount() / d.requestCount() * 100);
  }

  private static CallbackMetric1<String, Double> metricDouble(MetricMaker metrics,
      String name, String desc, String unit) {
    return metrics.newCallbackMetric(name, Double.class,
        new Description(desc).setGauge().setUnit(unit),
        Field.ofString("cache_name"));
  }

  private static CallbackMetric1<String, Long> metricLong(MetricMaker metrics,
      String name, String desc, String unit) {
    return metrics.newCallbackMetric(name, Long.class,
        new Description(desc).setGauge().setUnit(unit),
        Field.ofString("cache_name"));
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
