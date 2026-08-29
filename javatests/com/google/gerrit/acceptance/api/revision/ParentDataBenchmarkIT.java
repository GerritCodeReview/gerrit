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

package com.google.gerrit.acceptance.api.revision;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Stopwatch;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.index.testing.AbstractFakeIndex;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class ParentDataBenchmarkIT extends AbstractDaemonTest {
  @Inject private ChangeIndexCollection changeIndexCollection;

  private static final int NUM_PATCH_SETS = 10;
  private static final int WARMUP_ITERATIONS = 5;
  private static final int BENCHMARK_ITERATIONS = 15;

  @Test
  public void benchmarkSharedParentAcrossRevisions() throws Exception {
    PushOneCommit.Result r = createChange();
    for (int i = 2; i <= NUM_PATCH_SETS; i++) {
      amendChange(r.getChangeId());
    }

    AbstractFakeIndex<?, ?, ?> idx =
        (AbstractFakeIndex<?, ?, ?>) changeIndexCollection.getSearchIndex();

    // Warmup
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      idx.resetQueryCount();
      ChangeInfo info =
          gApi.changes()
              .id(r.getChangeId())
              .get(
                  ListChangesOption.ALL_REVISIONS,
                  ListChangesOption.ALL_COMMITS,
                  ListChangesOption.PARENTS);
      assertThat(info.revisions).hasSize(NUM_PATCH_SETS);
    }

    // Benchmark measurement
    List<Long> latenciesNanos = new ArrayList<>(BENCHMARK_ITERATIONS);
    int totalQueryCount = 0;

    for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
      idx.resetQueryCount();
      Stopwatch sw = Stopwatch.createStarted();
      ChangeInfo info =
          gApi.changes()
              .id(r.getChangeId())
              .get(
                  ListChangesOption.ALL_REVISIONS,
                  ListChangesOption.ALL_COMMITS,
                  ListChangesOption.PARENTS);
      long elapsed = sw.elapsed(TimeUnit.NANOSECONDS);
      latenciesNanos.add(elapsed);
      totalQueryCount += idx.getQueryCount();
      assertThat(info.revisions).hasSize(NUM_PATCH_SETS);
      info.revisions.values().forEach(rev -> assertThat(rev.parentsData).isNotNull());
    }

    double avgLatencyMs =
        latenciesNanos.stream().mapToLong(Long::longValue).average().orElse(0.0) / 1_000_000.0;
    double minLatencyMs =
        latenciesNanos.stream().mapToLong(Long::longValue).min().orElse(0) / 1_000_000.0;
    double maxLatencyMs =
        latenciesNanos.stream().mapToLong(Long::longValue).max().orElse(0) / 1_000_000.0;
    double avgQueries = (double) totalQueryCount / BENCHMARK_ITERATIONS;

    System.out.println("===============================================================");
    System.out.println("BENCHMARK RESULT: benchmarkSharedParentAcrossRevisions");
    System.out.println(String.format("PatchSets: %d, Iterations: %d", NUM_PATCH_SETS, BENCHMARK_ITERATIONS));
    System.out.println(String.format("Average Index Queries per Request: %.2f", avgQueries));
    System.out.println(String.format("Average Latency: %.3f ms (min: %.3f ms, max: %.3f ms)",
        avgLatencyMs, minLatencyMs, maxLatencyMs));
    System.out.println("===============================================================");
  }

  @Test
  public void benchmarkDistinctParentsAcrossRevisions() throws Exception {
    // Create base changes to serve as distinct parents
    List<RevCommit> baseCommits = new ArrayList<>();
    for (int i = 0; i < NUM_PATCH_SETS; i++) {
      PushOneCommit.Result base = createChange("Base " + i, "base_" + i + ".txt", "content " + i);
      baseCommits.add(base.getCommit());
    }

    // Create change with PS 1 based on baseCommits[0]
    testRepo.reset(baseCommits.get(0));
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(), testRepo, "Dependent Change", "dep.txt", "content 0");
    PushOneCommit.Result r = push.to("refs/for/master");
    String changeId = r.getChangeId();

    // Create PS 2..NUM_PATCH_SETS each based on a distinct baseCommit
    for (int i = 1; i < NUM_PATCH_SETS; i++) {
      testRepo.reset(baseCommits.get(i));
      PushOneCommit nextPs =
          pushFactory.create(
              admin.newIdent(), testRepo, "Dependent Change", "dep.txt", "content " + i, changeId);
      nextPs.to("refs/for/master");
    }

    AbstractFakeIndex<?, ?, ?> idx =
        (AbstractFakeIndex<?, ?, ?>) changeIndexCollection.getSearchIndex();

    // Warmup
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      idx.resetQueryCount();
      ChangeInfo info =
          gApi.changes()
              .id(changeId)
              .get(
                  ListChangesOption.ALL_REVISIONS,
                  ListChangesOption.ALL_COMMITS,
                  ListChangesOption.PARENTS);
      assertThat(info.revisions).hasSize(NUM_PATCH_SETS);
    }

    // Benchmark measurement
    List<Long> latenciesNanos = new ArrayList<>(BENCHMARK_ITERATIONS);
    int totalQueryCount = 0;

    for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
      idx.resetQueryCount();
      Stopwatch sw = Stopwatch.createStarted();
      ChangeInfo info =
          gApi.changes()
              .id(changeId)
              .get(
                  ListChangesOption.ALL_REVISIONS,
                  ListChangesOption.ALL_COMMITS,
                  ListChangesOption.PARENTS);
      long elapsed = sw.elapsed(TimeUnit.NANOSECONDS);
      latenciesNanos.add(elapsed);
      totalQueryCount += idx.getQueryCount();
      assertThat(info.revisions).hasSize(NUM_PATCH_SETS);
      info.revisions.values().forEach(rev -> assertThat(rev.parentsData).isNotNull());
    }

    double avgLatencyMs =
        latenciesNanos.stream().mapToLong(Long::longValue).average().orElse(0.0) / 1_000_000.0;
    double minLatencyMs =
        latenciesNanos.stream().mapToLong(Long::longValue).min().orElse(0) / 1_000_000.0;
    double maxLatencyMs =
        latenciesNanos.stream().mapToLong(Long::longValue).max().orElse(0) / 1_000_000.0;
    double avgQueries = (double) totalQueryCount / BENCHMARK_ITERATIONS;

    System.out.println("===============================================================");
    System.out.println("BENCHMARK RESULT: benchmarkDistinctParentsAcrossRevisions");
    System.out.println(String.format("PatchSets: %d, Iterations: %d", NUM_PATCH_SETS, BENCHMARK_ITERATIONS));
    System.out.println(String.format("Average Index Queries per Request: %.2f", avgQueries));
    System.out.println(String.format("Average Latency: %.3f ms (min: %.3f ms, max: %.3f ms)",
        avgLatencyMs, minLatencyMs, maxLatencyMs));
    System.out.println("===============================================================");
  }
}
