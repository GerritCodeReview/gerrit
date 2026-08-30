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

package com.google.gerrit.server.index.change;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.WorkQueue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PendingIndexUpdateScannerTest {
  private static final long DEAD_THREAD_ID = Long.MAX_VALUE;
  private static final Project.NameKey PROJECT = Project.nameKey("test-project");
  private static final Change.Id CHANGE_ID = Change.id(42);

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public final TemporaryFolder tempDir = new TemporaryFolder();

  @Mock private ChangeIndexer indexer;
  @Mock private WorkQueue workQueue;
  @Mock private ScheduledExecutorService fakeQueue;

  private SitePaths sitePaths;
  private PendingIndexUpdate pendingIndexUpdate;
  private PendingIndexUpdateScanner scanner;

  @Before
  public void setUp() throws Exception {
    lenient().doNothing().when(indexer).index(any(), any());
    lenient().doNothing().when(indexer).delete(any(), any());
    // Run submitted tasks synchronously so startup recovery completes inline.
    doAnswer(
            inv -> {
              ((Runnable) inv.getArgument(0)).run();
              return null;
            })
        .when(fakeQueue)
        .submit(any(Runnable.class));
    when(workQueue.getDefaultQueue()).thenReturn(fakeQueue);
    sitePaths = new SitePaths(tempDir.getRoot().toPath());
    pendingIndexUpdate = new PendingIndexUpdate(sitePaths, indexer, recoveryConfig());
    scanner = new PendingIndexUpdateScanner(pendingIndexUpdate, workQueue, recoveryConfig());
  }

  @Test
  public void scannerIndexesStaleFile() throws Exception {
    pendingIndexUpdate.write(DEAD_THREAD_ID, PROJECT, CHANGE_ID, /* delete= */ false);

    scanner.run();

    verify(indexer).index(PROJECT, CHANGE_ID);
    assertThat(intentFile(DEAD_THREAD_ID, PROJECT, CHANGE_ID).toFile().exists()).isFalse();
  }

  @Test
  public void scannerDeletesChangeWhenOperationIsDelete() throws Exception {
    pendingIndexUpdate.write(DEAD_THREAD_ID, PROJECT, CHANGE_ID, /* delete= */ true);

    scanner.run();

    verify(indexer).delete(PROJECT, CHANGE_ID);
    assertThat(intentFile(DEAD_THREAD_ID, PROJECT, CHANGE_ID).toFile().exists()).isFalse();
  }

  @Test
  public void scannerSkipsIntentsForLiveThread() throws Exception {
    long liveThreadId = Thread.currentThread().threadId();
    pendingIndexUpdate.write(liveThreadId, PROJECT, CHANGE_ID, /* delete= */ false);

    scanner.run();

    verify(indexer, never()).index(any(), any());
    assertThat(intentFile(liveThreadId, PROJECT, CHANGE_ID).toFile().exists()).isTrue();
  }

  @Test
  public void scannerDeletesMalformedFile() throws Exception {
    Path file = intentFile(DEAD_THREAD_ID, PROJECT, CHANGE_ID);
    Files.createDirectories(file.getParent());
    Files.writeString(file, "not-a-valid-blob");

    scanner.run();

    verify(indexer, never()).index(any(), any());
    verify(indexer, never()).delete(any(), any());
    assertThat(file.toFile().exists()).isFalse();
  }

  @Test
  public void scannerDoesNothingWhenNoPendingFiles() throws Exception {
    scanner.run();

    verify(indexer, never()).index(any(), any());
    verify(indexer, never()).delete(any(), any());
  }

  @Test
  public void startRecoversPreviousProcessIntents() throws Exception {
    // Write an intent under a foreign process marker dir, simulating a previous crash.
    Path intentDir = sitePaths.data_dir.resolve("pending-index");
    Path prevThreadDir = intentDir.resolve("99999_1234567890000").resolve("1");
    Files.createDirectories(prevThreadDir);
    Files.writeString(
        prevThreadDir.resolve(pendingIndexUpdate.filename(PROJECT, CHANGE_ID)),
        "{\"project\":\"test-project\",\"changeId\":42,\"operation\":\"index\"}");

    scanner = new PendingIndexUpdateScanner(pendingIndexUpdate, workQueue, recoveryConfig());
    scanner.start();

    verify(indexer).index(PROJECT, CHANGE_ID);
    assertThat(prevThreadDir.toFile().exists()).isFalse();
  }

  @Test
  public void startCleansBuildingDir() throws Exception {
    // Leave an orphaned temp file in buildingDir as if a crash happened mid-write.
    Path buildingDir = sitePaths.data_dir.resolve("pending-index").resolve("building");
    Files.createDirectories(buildingDir);
    Path orphan = Files.createTempFile(buildingDir, null, null);

    scanner = new PendingIndexUpdateScanner(pendingIndexUpdate, workQueue, recoveryConfig());
    scanner.start();

    assertThat(orphan.toFile().exists()).isFalse();
  }

  private static Config recoveryConfig() {
    Config cfg = new Config();
    cfg.setBoolean("index", null, "staleChangeRecovery", true);
    cfg.setInt("index", "changes", "commitWithin", 0);
    return cfg;
  }

  private Path intentFile(long threadId, Project.NameKey project, Change.Id changeId) {
    return pendingIndexUpdate
        .threadDir(threadId)
        .resolve(pendingIndexUpdate.filename(project, changeId));
  }
}
