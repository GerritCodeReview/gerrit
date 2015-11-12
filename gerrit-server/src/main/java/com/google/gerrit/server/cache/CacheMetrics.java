package com.google.gerrit.server.cache;

import com.google.gerrit.metrics.CallbackMetric1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.config.ListCaches;
import com.google.gerrit.server.config.ListCaches.CacheInfo;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class CacheMetrics {

  @Inject
  public CacheMetrics(final MetricMaker metrics,
      final ListCaches listCaches) {
    final CallbackMetric1<String, Long> callbackMetric = metrics.newCallbackMetric("mem_caches/memory",
        Long.class,
        new Description("Memory caches").setGauge().setUnit("Entries in memory"),
        Field.ofString("cache_name"));
    metrics.newTrigger(callbackMetric, new Runnable() {
      @Override
      public void run() {
        for (CacheInfo c : listCaches.getCacheInfos().values()) {
          callbackMetric.set(c.name, c.entries.mem);
        }
      }
    });
  }
}
