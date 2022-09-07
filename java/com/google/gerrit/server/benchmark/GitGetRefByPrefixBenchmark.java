// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.benchmark;

import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmark to test the performance of {@link
 * org.eclipse.jgit.lib.RefDatabase#getRefsByPrefix(java.lang.String)} on five large All-Users
 * repositories.
 */
@State(Scope.Benchmark)
@Fork(
    value = 2,
    jvmArgs = {"-Xms4G", "-Xmx4G"})
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
public class GitGetRefByPrefixBenchmark {
  private static String reposDir = "/usr/local/google/home/ghareeb/google/repos/";

  /** format = repo:numDraftRefs:totalNumRefs */
  @Param({
    "repo-1.git:6476:392549",
    "repo-2.git:4200:62830",
    "repo-3.git:11122:269576",
    "repo-4.git:1138:80275",
    "repo-5.git:27191:111303"
  })
  public String param;

  @Benchmark
  public void testPerformanceGetRefsByPrefix() throws Exception {
    String[] split = param.split(":");
    String repo = split[0];
    Integer expectedNumDraftRefs = Integer.parseInt(split[1]);
    LocalDiskRepositoryManager manager =
        new LocalDiskRepositoryManager(new SitePaths(Path.of(reposDir)), new Config());
    Repository repository = manager.openRepository(Path.of(reposDir + repo));
    List<Ref> refsByPrefix =
        repository.getRefDatabase().getRefsByPrefix(RefNames.REFS_DRAFT_COMMENTS);
    if (refsByPrefix.size() != expectedNumDraftRefs) {
      throw new RuntimeException(
          String.format(
              "Failed num drafts: expected %d, got %d", expectedNumDraftRefs, refsByPrefix.size()));
    }
  }
}
