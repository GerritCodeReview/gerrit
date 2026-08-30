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

package com.google.gerrit.server.schema;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.config.SitePaths;
import java.nio.file.Files;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class H2JGitLockAccountPatchReviewStoreIT {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private H2JGitLockAccountPatchReviewStore store;
  private static final Account.Id ACCOUNT = Account.id(1);
  private static final PatchSet.Id PS = PatchSet.id(Change.id(1), 1);
  private static final String FILE = "foo/bar.txt";

  @Before
  public void setUp() throws Exception {
    SitePaths sitePaths = new SitePaths(temporaryFolder.getRoot().toPath());
    Files.createDirectories(sitePaths.db_dir);
    Config cfg = new Config();
    store = new H2JGitLockAccountPatchReviewStore(cfg, sitePaths);
    store.start();
  }

  @Test
  public void markAndFindReviewed() {
    assertThat(store.findReviewed(PS, ACCOUNT)).isEmpty();

    var unused = store.markReviewed(PS, ACCOUNT, FILE);

    assertThat(store.findReviewed(PS, ACCOUNT)).isPresent();
    assertThat(store.findReviewed(PS, ACCOUNT).get().files()).containsExactly(FILE);
  }

  @Test
  public void clearReviewedFile() {
    var unused = store.markReviewed(PS, ACCOUNT, FILE);
    assertThat(store.findReviewed(PS, ACCOUNT)).isPresent();

    store.clearReviewed(PS, ACCOUNT, FILE);

    assertThat(store.findReviewed(PS, ACCOUNT)).isEmpty();
  }

  @Test
  public void clearReviewedPatchSet() {
    var unused = store.markReviewed(PS, ACCOUNT, FILE);
    assertThat(store.findReviewed(PS, ACCOUNT)).isPresent();
    assertThat(store.findReviewed(PS, ACCOUNT).get().files()).containsExactly(FILE);

    store.clearReviewed(PS);

    assertThat(store.findReviewed(PS, ACCOUNT)).isEmpty();
  }

  @Test
  public void concurrentMarksAreAllDurable() throws Exception {
    int nThreads = 8;
    int filesPerThread = 5;
    CyclicBarrier startGate = new CyclicBarrier(nThreads);

    try (ExecutorService executor = Executors.newFixedThreadPool(nThreads)) {
      ImmutableList<Future<?>> futures =
          IntStream.range(0, nThreads)
              .mapToObj(
                  threadIdx ->
                      executor.submit(
                          () -> {
                            startGate.await();
                            for (int f = 0; f < filesPerThread; f++) {
                              var unused = store.markReviewed(PS, ACCOUNT, fileName(threadIdx, f));
                            }
                            return null;
                          }))
              .collect(ImmutableList.toImmutableList());

      for (Future<?> future : futures) {
        future.get();
      }
    }

    ImmutableSet<String> expected =
        IntStream.range(0, nThreads)
            .boxed()
            .flatMap(
                threadIdx ->
                    IntStream.range(0, filesPerThread).mapToObj(f -> fileName(threadIdx, f)))
            .collect(ImmutableSet.toImmutableSet());

    assertThat(store.findReviewed(PS, ACCOUNT)).isPresent();
    assertThat(store.findReviewed(PS, ACCOUNT).get().files()).isEqualTo(expected);
  }

  private static String fileName(int threadIdx, int fileIdx) {
    return "file-" + threadIdx + "-" + fileIdx + ".txt";
  }
}
