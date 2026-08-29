// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.index.query;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.PaginationType;
import com.google.gerrit.index.QueryOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

/**
 * Performance benchmark test verifying the latency, RPC count, and throughput improvements of the
 * {@link PaginatingSource} optimization (b/554474587).
 */
public class PaginatingSourceBenchmarkTest {

  /**
   * Reference implementation of the legacy PaginatingSource containing the pre-fix condition
   * ({@code r.size() <= limit}) to allow exact side-by-side benchmarking against the same dataset
   * and simulated backend.
   */
  private static class LegacyPaginatingSource<T> extends FilteredSource<T> {
    LegacyPaginatingSource(DataSource<T> source, int start, IndexConfig indexConfig) {
      super(source, start, indexConfig);
    }

    @Override
    public ResultSet<T> read() {
      ResultSet<T> resultSet = source.read();
      return new LazyResultSet<>(
          () -> {
            List<T> r = new ArrayList<>();
            T last = null;
            int pageResultSize = 0;
            for (T data : buffer(resultSet)) {
              if (!isMatchable() || match(data)) {
                r.add(data);
              }
              last = data;
              pageResultSize++;
            }

            if (last != null && source instanceof Paginated) {
              @SuppressWarnings("unchecked")
              Paginated<T> p = (Paginated<T>) source;
              QueryOptions opts = p.getOptions();
              final int limit = opts.limit();
              int pageSize = opts.pageSize();
              int pageSizeMultiplier = opts.pageSizeMultiplier();
              Object searchAfter = resultSet.searchAfter();
              int nextStart = pageResultSize;
              // Legacy condition: <= limit and > limit
              while (pageResultSize == pageSize && r.size() <= limit) {
                pageSize = getNextPageSize(pageSize, pageSizeMultiplier);
                ResultSet<T> next =
                    indexConfig.paginationType().equals(PaginationType.SEARCH_AFTER)
                        ? p.restart(searchAfter, pageSize)
                        : p.restart(nextStart, pageSize);
                pageResultSize = 0;
                for (T data : buffer(next)) {
                  if (match(data)) {
                    r.add(data);
                  }
                  pageResultSize++;
                  if (r.size() > limit) {
                    break;
                  }
                }
                nextStart += pageResultSize;
                searchAfter = next.searchAfter();
              }
            }

            if (start >= r.size()) {
              return ImmutableList.of();
            } else if (start > 0) {
              return ImmutableList.copyOf(r.subList(start, r.size()));
            }
            return ImmutableList.copyOf(r);
          });
    }

    private int getNextPageSize(int pageSize, int pageSizeMultiplier) {
      return pageSize * pageSizeMultiplier;
    }
  }

  private static class BenchmarkIndexSource implements DataSource<String>, Paginated<String> {
    private final List<String> indexData;
    private final QueryOptions options;
    private final long simulatedRpcLatencyNanos;
    private int rpcCount = 0;

    BenchmarkIndexSource(
        List<String> indexData, QueryOptions options, long simulatedRpcLatencyNanos) {
      this.indexData = indexData;
      this.options = options;
      this.simulatedRpcLatencyNanos = simulatedRpcLatencyNanos;
    }

    @Override
    public QueryOptions getOptions() {
      return options;
    }

    @Override
    public ResultSet<String> read() {
      return executeQuery(0, options.pageSize());
    }

    @Override
    public ResultSet<String> restart(int start) {
      return restart(start, options.pageSize());
    }

    @Override
    public ResultSet<String> restart(int start, int pageSize) {
      return executeQuery(start, pageSize);
    }

    @Override
    public ResultSet<String> restart(Object searchAfter, int pageSize) {
      int start = searchAfter == null ? 0 : ((Integer) searchAfter) + 1;
      return executeQuery(start, pageSize);
    }

    private ResultSet<String> executeQuery(int start, int pageSize) {
      rpcCount++;
      simulateLatency();
      if (start >= indexData.size()) {
        return new ListResultSet<>(ImmutableList.of());
      }
      int end = Math.min(start + pageSize, indexData.size());
      List<String> slice = new ArrayList<>(indexData.subList(start, end));
      return new ListResultSet<String>(slice) {
        @Override
        public Object searchAfter() {
          return end - 1;
        }
      };
    }

    private void simulateLatency() {
      if (simulatedRpcLatencyNanos <= 0) {
        return;
      }
      long end = System.nanoTime() + simulatedRpcLatencyNanos;
      while (System.nanoTime() < end) {
        // Spin wait to simulate server round-trip without sleeping thread
      }
    }

    @Override
    public int getCardinality() {
      return indexData.size();
    }

    @Override
    public ResultSet<FieldBundle> readRaw() {
      throw new UnsupportedOperationException();
    }
  }

  private static QueryOptions createOptions(IndexConfig config, int limit) {
    // Caller limit + 1 probe row, matching QueryProcessor.java
    int probeLimit = limit + 1;
    return QueryOptions.create(
        config,
        0,
        null,
        probeLimit,
        config.pageSizeMultiplier(),
        probeLimit,
        /* allowIncompleteResults= */ false,
        ImmutableSet.of());
  }

  @Test
  public void benchmarkStandardChangeQueryPerformance() {
    Config gitConfig = new Config();
    gitConfig.setString("index", null, "paginationType", "OFFSET");
    IndexConfig indexConfig = IndexConfig.fromConfig(gitConfig).build();

    List<String> dataset =
        IntStream.range(0, 500).mapToObj(i -> "change-" + i).collect(Collectors.toList());

    int[] requestedLimits = {25, 50, 100};
    // 500 microseconds simulated latency per RPC for microbenchmark
    long simulatedRpcNanos = TimeUnit.MICROSECONDS.toNanos(500);

    System.out.println(
        "==========================================================================================================================");
    System.out.println(
        "                         GERRIT QUERY PERFORMANCE BENCHMARK (b/554474587)                 "
            + "                               ");
    System.out.println(
        "==========================================================================================================================");
    System.out.printf(
        "%-10s | %-12s | %-12s | %-14s | %-12s | %-12s | %-12s%n",
        "Limit",
        "RPCs (Before)",
        "RPCs (After)",
        "RPC Reduction",
        "Time (Before)",
        "Time (After)",
        "Speedup");
    System.out.println(
        "-----------+--------------+--------------+----------------+--------------+--------------+-------------");

    for (int requestedLimit : requestedLimits) {
      QueryOptions options = createOptions(indexConfig, requestedLimit);

      // 1. Run Legacy (Before)
      BenchmarkIndexSource legacySource =
          new BenchmarkIndexSource(dataset, options, simulatedRpcNanos);
      LegacyPaginatingSource<String> legacyPaginating =
          new LegacyPaginatingSource<>(legacySource, 0, indexConfig);

      long startBefore = System.nanoTime();
      ImmutableList<String> legacyResults = legacyPaginating.read().toList();
      long durationBefore = System.nanoTime() - startBefore;

      // 2. Run Optimized (After)
      BenchmarkIndexSource optimizedSource =
          new BenchmarkIndexSource(dataset, options, simulatedRpcNanos);
      PaginatingSource<String> optimizedPaginating =
          new PaginatingSource<>(optimizedSource, 0, indexConfig);

      long startAfter = System.nanoTime();
      ImmutableList<String> optimizedResults = optimizedPaginating.read().toList();
      long durationAfter = System.nanoTime() - startAfter;

      // Validate correctness: both must deliver identical results
      assertThat(optimizedResults.size()).isEqualTo(requestedLimit + 1);
      assertThat(legacyResults.size()).isAtLeast(requestedLimit + 1);
      assertThat(optimizedResults.subList(0, requestedLimit))
          .containsExactlyElementsIn(legacyResults.subList(0, requestedLimit))
          .inOrder();

      // Validate performance invariants
      assertThat(legacySource.rpcCount).isEqualTo(2); // Before: issued redundant secondary query
      assertThat(optimizedSource.rpcCount).isEqualTo(1); // After: eliminated redundant query!

      double rpcReduction =
          ((double) (legacySource.rpcCount - optimizedSource.rpcCount) / legacySource.rpcCount)
              * 100.0;
      double speedup = ((double) durationBefore / durationAfter);

      System.out.printf(
          "n=%-8d | %-12d | %-12d | %-13.1f%% | %-10.2fms | %-10.2fms | %-11.2fx%n",
          requestedLimit,
          legacySource.rpcCount,
          optimizedSource.rpcCount,
          rpcReduction,
          durationBefore / 1_000_000.0,
          durationAfter / 1_000_000.0,
          speedup);
    }
    System.out.println(
        "==========================================================================================================================");
  }

  @Test
  public void benchmarkThroughputAndCpuOverhead() {
    Config gitConfig = new Config();
    gitConfig.setString("index", null, "paginationType", "OFFSET");
    IndexConfig indexConfig = IndexConfig.fromConfig(gitConfig).build();

    List<String> dataset =
        IntStream.range(0, 100).mapToObj(i -> "change-" + i).collect(Collectors.toList());
    QueryOptions options = createOptions(indexConfig, 25);

    int iterations = 10_000;

    // Warm-up
    for (int i = 0; i < 1_000; i++) {
      BenchmarkIndexSource src = new BenchmarkIndexSource(dataset, options, 0);
      var unused1 = new PaginatingSource<>(src, 0, indexConfig).read().toList();
      BenchmarkIndexSource legSrc = new BenchmarkIndexSource(dataset, options, 0);
      var unused2 = new LegacyPaginatingSource<>(legSrc, 0, indexConfig).read().toList();
    }

    // Benchmark Legacy (Before)
    long beforeRpcTotal = 0;
    long startLegacy = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      BenchmarkIndexSource src = new BenchmarkIndexSource(dataset, options, 0);
      var unused = new LegacyPaginatingSource<>(src, 0, indexConfig).read().toList();
      beforeRpcTotal += src.rpcCount;
    }
    long totalTimeLegacy = System.nanoTime() - startLegacy;

    // Benchmark Optimized (After)
    long afterRpcTotal = 0;
    long startOptimized = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      BenchmarkIndexSource src = new BenchmarkIndexSource(dataset, options, 0);
      var unused = new PaginatingSource<>(src, 0, indexConfig).read().toList();
      afterRpcTotal += src.rpcCount;
    }
    long totalTimeOptimized = System.nanoTime() - startOptimized;

    System.out.println("\n[THROUGHPUT BENCHMARK - 10,000 Iterations]");
    System.out.printf(
        "  Legacy (Before)   : %d total queries/RPCs, %.2f ms total (%.2f ns/op)%n",
        beforeRpcTotal, totalTimeLegacy / 1_000_000.0, (double) totalTimeLegacy / iterations);
    System.out.printf(
        "  Optimized (After) : %d total queries/RPCs, %.2f ms total (%.2f ns/op)%n",
        afterRpcTotal, totalTimeOptimized / 1_000_000.0, (double) totalTimeOptimized / iterations);
    System.out.printf(
        "  RPC Reduction     : %.1f%% (Saved %d backend index queries)%n",
        ((double) (beforeRpcTotal - afterRpcTotal) / beforeRpcTotal) * 100.0,
        beforeRpcTotal - afterRpcTotal);
    System.out.printf(
        "  Throughput Gain   : %.2fx faster%n", (double) totalTimeLegacy / totalTimeOptimized);

    assertThat(afterRpcTotal).isEqualTo(iterations);
    assertThat(beforeRpcTotal).isEqualTo(iterations * 2);
  }

  public static void main(String[] args) {
    PaginatingSourceBenchmarkTest bench = new PaginatingSourceBenchmarkTest();
    bench.benchmarkStandardChangeQueryPerformance();
    bench.benchmarkThroughputAndCpuOverhead();
  }
}
