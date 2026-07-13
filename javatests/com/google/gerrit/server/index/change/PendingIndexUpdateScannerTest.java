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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.WorkQueue;
import java.nio.file.Files;
import java.nio.file.Path;
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
  private PendingIndexUpdate pendingIndexUpdate;
  private PendingIndexUpdate.Scanner scanner;

  @Before
  public void setUp() throws Exception {
    lenient().doNothing().when(indexer).index(any(), any());
    lenient().doNothing().when(indexer).delete(any(), any());
    SitePaths sitePaths = new SitePaths(tempDir.getRoot().toPath());
    pendingIndexUpdate = new PendingIndexUpdate(sitePaths, indexer);
    scanner = pendingIndexUpdate.new Scanner(workQueue, new Config());
  }

  @Test
  public void scannerIndexesStaleFile() throws Exception {
    pendingIndexUpdate.write(DEAD_THREAD_ID, PROJECT, CHANGE_ID, /* delete= */ false);

    scanner.run();

    verify(indexer).index(PROJECT, CHANGE_ID);
    assertThat(intentFile(DEAD_THREAD_ID, CHANGE_ID).toFile().exists()).isFalse();
  }

  @Test
  public void scannerDeletesChangeWhenOperationIsDelete() throws Exception {
    pendingIndexUpdate.write(DEAD_THREAD_ID, PROJECT, CHANGE_ID, /* delete= */ true);

    scanner.run();

    verify(indexer).delete(PROJECT, CHANGE_ID);
    assertThat(intentFile(DEAD_THREAD_ID, CHANGE_ID).toFile().exists()).isFalse();
  }

  @Test
  public void scannerSkipsIntentsForLiveThread() throws Exception {
    long liveThreadId = Thread.currentThread().getId();
    pendingIndexUpdate.write(liveThreadId, PROJECT, CHANGE_ID, /* delete= */ false);

    scanner.run();

    verify(indexer, never()).index(any(), any());
    assertThat(intentFile(liveThreadId, CHANGE_ID).toFile().exists()).isTrue();
  }

  @Test
  public void scannerSkipsMalformedFile() throws Exception {
    Path file = intentFile(DEAD_THREAD_ID, CHANGE_ID);
    Files.createDirectories(file.getParent());
    Files.writeString(file, "not-a-valid-blob");

    scanner.run();

    verify(indexer, never()).index(any(), any());
    verify(indexer, never()).delete(any(), any());
    assertThat(file.toFile().exists()).isTrue();
  }

  @Test
  public void scannerDoesNothingWhenNoPendingFiles() throws Exception {
    scanner.run();

    verify(indexer, never()).index(any(), any());
    verify(indexer, never()).delete(any(), any());
  }

  private Path intentFile(long threadId, Change.Id changeId) {
    return pendingIndexUpdate.threadDir(threadId).resolve(String.valueOf(changeId.get()));
  }
}
