package com.google.gerrit.server.cache;

import com.google.common.cache.Cache;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.metrics.CallbackMetric;
import com.google.gerrit.metrics.CallbackMetric1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.HashSet;

@Singleton
public class CacheMetrics {

  @Inject
  public CacheMetrics(final MetricMaker metrics,
      final DynamicMap<Cache<?, ?>> cacheMap) {
    final CallbackMetric1<String, Long> memEnt =
        metric(metrics, "caches/memory/entries", "Memory entries", "Entries");
    final CallbackMetric1<String, Long> memHit = metric(metrics,
        "caches/memory/hit_ratio", "Memory hit ratio", "Hit ratio");
    final CallbackMetric1<String, Long> perMemEnt =
        metric(metrics, "caches/persistent/memory/entries",
            "Memory entries used by persistent cache", "Entries");
    final CallbackMetric1<String, Long> perMemHit =
        metric(metrics, "caches/persistent/memory/hit_ratio",
            "Memory hit ratio for persistent cache", "Hit ratio");
    final CallbackMetric1<String, Long> perDiskEnt =
        metric(metrics, "caches/persistent/disk/entries",
            "Disk entries used by persistent cache", "Entries");
    final CallbackMetric1<String, Long> perDiskHit =
        metric(metrics, "caches/persistent/disk/hit_ratio",
            "Disk hit ratio for persistent cache", "Hit ratio");

    final HashSet<CallbackMetric<?>> cacheMetrics = new HashSet<>();
    cacheMetrics.add(memEnt);
    cacheMetrics.add(memHit);
    cacheMetrics.add(perMemEnt);
    cacheMetrics.add(perMemHit);
    cacheMetrics.add(perDiskEnt);
    cacheMetrics.add(perDiskHit);

    metrics.newTrigger(cacheMetrics, new Runnable() {
      @Override
      public void run() {
        for (DynamicMap.Entry<Cache<?, ?>> e : cacheMap) {
          Cache<?,?> c = e.getProvider().get();
          String name = metricNameOf(e);
          if (c instanceof PersistentCache) {
            perMemEnt.set(name, c.size());
            perMemHit.set(name, Math.round(c.stats().hitRate() * 100));
            PersistentCache.DiskStats d =
                ((PersistentCache) c).diskStats();
            perDiskEnt.set(name, d.size());
            perDiskHit.set(name, hitRatio(d));
          } else {
            memEnt.set(name, c.size());
            memHit.set(name, Math.round(c.stats().hitRate() * 100));
          }
        }
        for (CallbackMetric<?> cbm  : cacheMetrics) {
          cbm.prune();
        }
      }
    });
  }

  private static long hitRatio(PersistentCache.DiskStats d) {
    if (d.requestCount() <= 0) {
      return 100;
    }
    return Math.round(d.hitCount() / d.requestCount() * 100);
  }

  private static CallbackMetric1<String, Long> metric(MetricMaker metrics,
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
