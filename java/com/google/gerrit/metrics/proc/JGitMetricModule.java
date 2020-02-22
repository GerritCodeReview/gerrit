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

package com.google.gerrit.metrics.proc;

import com.google.gerrit.metrics.CallbackMetric1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.logging.Metadata;
import java.util.Map;
import org.eclipse.jgit.storage.file.WindowCacheStats;

public class JGitMetricModule extends MetricModule {
  private static final long MAX_REPO_COUNT = 1000;

  @Override
  protected void configure(MetricMaker metrics) {
    metrics.newCallbackMetric(
        "jgit/block_cache/cache_used",
        Long.class,
        new Description("Bytes of memory retained in JGit block cache.")
            .setGauge()
            .setUnit(Units.BYTES),
        WindowCacheStats.getStats()::getOpenByteCount);

    metrics.newCallbackMetric(
        "jgit/block_cache/open_files",
        Long.class,
        new Description("File handles held open by JGit block cache.").setGauge().setUnit("fds"),
        WindowCacheStats.getStats()::getOpenFileCount);

    metrics.newCallbackMetric(
        "jgit/block_cache/avg_load_time",
        Double.class,
        new Description("Average time to load a cache entry for JGit block cache.")
            .setGauge()
            .setUnit(Units.NANOSECONDS),
        WindowCacheStats.getStats()::getAverageLoadTime);

    metrics.newCallbackMetric(
        "jgit/block_cache/eviction_count",
        Long.class,
        new Description("Cache evictions for JGit block cache.").setGauge(),
        WindowCacheStats.getStats()::getEvictionCount);

    metrics.newCallbackMetric(
        "jgit/block_cache/eviction_ratio",
        Double.class,
        new Description("Cache eviction ratio for JGit block cache.").setGauge(),
        WindowCacheStats.getStats()::getEvictionRatio);

    metrics.newCallbackMetric(
        "jgit/block_cache/hit_count",
        Long.class,
        new Description("Cache hits for JGit block cache.").setGauge(),
        WindowCacheStats.getStats()::getHitCount);

    metrics.newCallbackMetric(
        "jgit/block_cache/hit_ratio",
        Double.class,
        new Description("Cache hit ratio for JGit block cache.").setGauge(),
        WindowCacheStats.getStats()::getHitRatio);

    metrics.newCallbackMetric(
        "jgit/block_cache/load_failure_count",
        Long.class,
        new Description("Failed cache loads for JGit block cache.").setGauge(),
        WindowCacheStats.getStats()::getLoadFailureCount);

    metrics.newCallbackMetric(
        "jgit/block_cache/load_failure_ratio",
        Double.class,
        new Description("Failed cache load ratio for JGit block cache.").setGauge(),
        WindowCacheStats.getStats()::getLoadFailureRatio);

    metrics.newCallbackMetric(
        "jgit/block_cache/load_success_count",
        Long.class,
        new Description("Successfull cache loads for JGit block cache.").setGauge(),
        WindowCacheStats.getStats()::getLoadSuccessCount);

    metrics.newCallbackMetric(
        "jgit/block_cache/miss_count",
        Long.class,
        new Description("Cache misses for JGit block cache.").setGauge(),
        WindowCacheStats.getStats()::getMissCount);

    metrics.newCallbackMetric(
        "jgit/block_cache/miss_ratio",
        Double.class,
        new Description("Cache miss ratio for JGit block cache.").setGauge(),
        WindowCacheStats.getStats()::getMissRatio);

    CallbackMetric1<String, Long> repoEnt =
        metrics.newCallbackMetric(
            "jgit/block_cache/cache_used_per_repository",
            Long.class,
            new Description(
                    "Bytes of memory retained per repository for the top repositories "
                        + "having most data in the cache.")
                .setGauge()
                .setUnit("byte"),
            Field.ofString("repository_name", Metadata.Builder::projectName).build());
    metrics.newTrigger(
        repoEnt,
        () -> {
          Map<String, Long> cacheMap = WindowCacheStats.getStats().getOpenByteCountPerRepository();
          if (cacheMap.isEmpty()) {
            repoEnt.forceCreate("");
          } else {
            cacheMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(MAX_REPO_COUNT)
                .forEach(e -> repoEnt.set(e.getKey(), e.getValue()));
            repoEnt.prune();
          }
        });
  }
}
