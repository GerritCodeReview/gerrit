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
import static com.google.gerrit.testing.TestActionRefUpdateContext.openTestRefUpdateContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PendingIndexUpdateScannerTest {
  private static final AllProjectsName ALL_PROJECTS = new AllProjectsName("All-Projects");
  private static final Project.NameKey PROJECT = Project.nameKey("test-project");
  private static final Change.Id CHANGE_ID = Change.id(42);
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Mock private ChangeIndexer indexer;
  private GitRepositoryManager repoManager;
  private PendingIndexUpdateScanner scanner;
  private RefUpdateContext testRefUpdateContext;

  @Before
  public void setUp() throws Exception {
    repoManager = new InMemoryRepositoryManager();
    var unused = repoManager.createRepository(ALL_PROJECTS);
    lenient().when(indexer.indexAsync(any(), any())).thenReturn(Futures.immediateFuture(null));
    lenient().when(indexer.deleteAsync(any(), any())).thenReturn(Futures.immediateFuture(null));
    scanner = new PendingIndexUpdateScanner(ALL_PROJECTS, repoManager, indexer);
    testRefUpdateContext = openTestRefUpdateContext();
  }

  @After
  public void tearDown() {
    testRefUpdateContext.close();
  }

  @Test
  public void scannerIndexesStaleRef() throws Exception {
    writeIntentRef(PROJECT.get(), CHANGE_ID.get(), "index", twoMinutesAgo());

    scanner.run();

    verify(indexer).indexAsync(PROJECT, CHANGE_ID);
    assertThat(pendingRef(CHANGE_ID)).isNull();
  }

  @Test
  public void scannerDeletesChangeWhenOperationIsDelete() throws Exception {
    writeIntentRef(PROJECT.get(), CHANGE_ID.get(), "delete", twoMinutesAgo());

    scanner.run();

    verify(indexer).deleteAsync(PROJECT, CHANGE_ID);
    assertThat(pendingRef(CHANGE_ID)).isNull();
  }

  @Test
  public void scannerSkipsRefsWithinGracePeriod() throws Exception {
    writeIntentRef(PROJECT.get(), CHANGE_ID.get(), "index", System.currentTimeMillis());

    scanner.run();

    verify(indexer, never()).indexAsync(any(), any());
    assertThat(pendingRef(CHANGE_ID)).isNotNull();
  }

  @Test
  public void scannerSkipsMalformedBlob() throws Exception {
    writeRawRef(
        RefNames.pendingIndexRef(CHANGE_ID), "not-a-valid-blob".getBytes(StandardCharsets.UTF_8));

    scanner.run();

    verify(indexer, never()).indexAsync(any(), any());
    verify(indexer, never()).deleteAsync(any(), any());
    assertThat(pendingRef(CHANGE_ID)).isNotNull();
  }

  @Test
  public void scannerLeavesRefOnIndexerFailure() throws Exception {
    writeIntentRef(PROJECT.get(), CHANGE_ID.get(), "index", twoMinutesAgo());
    when(indexer.indexAsync(any(), any()))
        .thenReturn(Futures.immediateFailedFuture(new RuntimeException("index failure")));

    scanner.run();

    assertThat(pendingRef(CHANGE_ID)).isNotNull();
  }

  @Test
  public void scannerDoesNothingWhenNoPendingRefs() throws Exception {
    scanner.run();

    verify(indexer, never()).indexAsync(any(), any());
    verify(indexer, never()).deleteAsync(any(), any());
  }

  private static long twoMinutesAgo() {
    return System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2);
  }

  private void writeIntentRef(String project, int changeId, String op, long timestamp)
      throws Exception {
    String blob = project + "\t" + changeId + "\t" + op + "\t" + timestamp;
    writeRawRef(
        RefNames.pendingIndexRef(Change.id(changeId)), blob.getBytes(StandardCharsets.UTF_8));
  }

  private void writeRawRef(String refName, byte[] content) throws Exception {
    try (Repository repo = repoManager.openRepository(ALL_PROJECTS);
        ObjectInserter ins = repo.newObjectInserter()) {
      ObjectId blobId = ins.insert(Constants.OBJ_BLOB, content);
      ins.flush();
      RefUpdate ru = repo.updateRef(refName);
      ru.setNewObjectId(blobId);
      ru.setForceUpdate(true);
      ru.update();
    }
  }

  private Ref pendingRef(Change.Id id) throws Exception {
    try (Repository repo = repoManager.openRepository(ALL_PROJECTS)) {
      return repo.exactRef(RefNames.pendingIndexRef(id));
    }
  }
}
