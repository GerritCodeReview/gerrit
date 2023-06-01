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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.metrics.CallbackMetric1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.storage.file.WindowCacheStats;

public class JGitMetricModule extends MetricModule {
  private static final long MAX_REPO_COUNT = 1000;
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final Optional<WindowCacheStats> snapshotWindowCacheStats;
  protected static final String CACHE_USED_PER_REPO_METRIC_NAME =
      "jgit/block_cache/cache_used_per_repository";

  public JGitMetricModule() {
    this.snapshotWindowCacheStats = Optional.empty();
  }

  @VisibleForTesting
  protected JGitMetricModule(WindowCacheStats windowCacheStats) {
    this.snapshotWindowCacheStats = Optional.of(windowCacheStats);
  }

  private WindowCacheStats windowCacheStats() {
    return snapshotWindowCacheStats.orElse(WindowCacheStats.getStats());
  }

  @Override
  protected void configure(MetricMaker metrics) {
    metrics.newCallbackMetric(
        "jgit/block_cache/cache_used",
        Long.class,
        new Description("Bytes of memory retained in JGit block cache.")
            .setGauge()
            .setUnit(Units.BYTES),
        new Supplier<Long>() {
          @Override
          public Long get() {
            return windowCacheStats().getOpenByteCount();
          }
        });

    metrics.newCallbackMetric(
        "jgit/block_cache/open_files",
        Long.class,
        new Description("File handles held open by JGit block cache.").setGauge().setUnit("fds"),
        new Supplier<Long>() {
          @Override
          public Long get() {
            return windowCacheStats().getOpenFileCount();
          }
        });

    metrics.newCallbackMetric(
        "jgit/block_cache/avg_load_time",
        Double.class,
        new Description("Average time to load a cache entry for JGit block cache.")
            .setGauge()
            .setUnit(Units.NANOSECONDS),
        new Supplier<Double>() {
          @Override
          public Double get() {
            return windowCacheStats().getAverageLoadTime();
          }
        });

    metrics.newCallbackMetric(
        "jgit/block_cache/total_load_time",
        Long.class,
        new Description("Total time to load cache entries for JGit block cache.").setGauge(),
        new Supplier<Long>() {
          @Override
          public Long get() {
            return windowCacheStats().getTotalLoadTime();
          }
        });

    metrics.newCallbackMetric(
        "jgit/block_cache/eviction_count",
        Long.class,
        new Description("Cache evictions for JGit block cache.").setGauge(),
        new Supplier<Long>() {
          @Override
          public Long get() {
            return windowCacheStats().getEvictionCount();
          }
        });

    metrics.newCallbackMetric(
        "jgit/block_cache/eviction_ratio",
        Double.class,
        new Description("Cache eviction ratio for JGit block cache.").setGauge(),
        new Supplier<Double>() {
          @Override
          public Double get() {
            return windowCacheStats().getEvictionRatio();
          }
        });

    metrics.newCallbackMetric(
        "jgit/block_cache/hit_count",
        Long.class,
        new Description("Cache hits for JGit block cache.").setGauge(),
        new Supplier<Long>() {
          @Override
          public Long get() {
            return windowCacheStats().getHitCount();
          }
        });

    metrics.newCallbackMetric(
        "jgit/block_cache/hit_ratio",
        Double.class,
        new Description("Cache hit ratio for JGit block cache.").setGauge(),
        new Supplier<Double>() {
          @Override
          public Double get() {
            return windowCacheStats().getHitRatio();
          }
        });

    metrics.newCallbackMetric(
        "jgit/block_cache/load_failure_count",
        Long.class,
        new Description("Failed cache loads for JGit block cache.").setGauge(),
        new Supplier<Long>() {
          @Override
          public Long get() {
            return windowCacheStats().getLoadFailureCount();
          }
        });

    metrics.newCallbackMetric(
        "jgit/block_cache/load_failure_ratio",
        Double.class,
        new Description("Failed cache load ratio for JGit block cache.").setGauge(),
        new Supplier<Double>() {
          @Override
          public Double get() {
            return windowCacheStats().getLoadFailureRatio();
          }
        });

    metrics.newCallbackMetric(
        "jgit/block_cache/load_success_count",
        Long.class,
        new Description("Successfull cache loads for JGit block cache.").setGauge(),
        new Supplier<Long>() {
          @Override
          public Long get() {
            return windowCacheStats().getLoadSuccessCount();
          }
        });
    metrics.newCallbackMetric(
        "jgit/block_cache/miss_count",
        Long.class,
        new Description("Cache misses for JGit block cache.").setGauge(),
        new Supplier<Long>() {
          @Override
          public Long get() {
            return windowCacheStats().getMissCount();
          }
        });

    metrics.newCallbackMetric(
        "jgit/block_cache/miss_ratio",
        Double.class,
        new Description("Cache miss ratio for JGit block cache.").setGauge(),
        new Supplier<Double>() {
          @Override
          public Double get() {
            return windowCacheStats().getMissRatio();
          }
        });

    CallbackMetric1<String, Long> repoEnt =
        metrics.newCallbackMetric(
            CACHE_USED_PER_REPO_METRIC_NAME,
            Long.class,
            new Description(
                    "Bytes of memory retained per repository for the top repositories "
                        + "having most data in the cache.")
                .setGauge()
                .setUnit("byte"),
            Field.ofString("repository_name"));
    metrics.newTrigger(
        repoEnt,
        () -> {
          Map<String, Long> cacheMap = windowCacheStats().getOpenByteCountPerRepository();
          if (cacheMap.isEmpty()) {
            repoEnt.forceCreate("");
          } else {
            cacheMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(MAX_REPO_COUNT)
                .forEach(
                    e -> {
                      try {
                        repoEnt.set(e.getKey(), e.getValue());
                      } catch (IllegalArgumentException ex) {
                        logger.atSevere().withCause(ex).log(
                            "Could not trigger cache_used_per_repository metric for repo %s",
                            e.getKey());
                      }
                    });
            repoEnt.prune();
          }
        });
  }
}
