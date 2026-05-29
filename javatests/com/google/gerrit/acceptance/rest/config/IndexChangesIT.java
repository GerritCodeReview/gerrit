// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ChangeIndexedCounter;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.IndexedChangeQuery;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.restapi.config.IndexChanges;
import com.google.inject.Inject;
import java.util.Optional;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class IndexChangesIT extends AbstractDaemonTest {

  @Inject private ProjectOperations projectOperations;
  @Inject private ExtensionRegistry extensionRegistry;
  @Inject private ChangeIndexCollection changeIndexCollection;
  @Inject private IndexConfig indexConfig;

  @Test
  public void indexRequestFromNonAdminRejected() throws Exception {
    ChangeIndexedCounter changeIndexedCounter = new ChangeIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(changeIndexedCounter)) {
      String changeId = createChange().getChangeId();
      IndexChanges.Input in = new IndexChanges.Input(ImmutableSet.of(changeId), false);
      changeIndexedCounter.clear();
      userRestSession.post("/config/server/index.changes", in).assertForbidden();
      assertThat(changeIndexedCounter.getCount(info(changeId))).isEqualTo(0);
    }
  }

  @Test
  public void indexVisibleChange() throws Exception {
    ChangeIndexedCounter changeIndexedCounter = new ChangeIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(changeIndexedCounter)) {
      String changeId = createChange().getChangeId();
      IndexChanges.Input in = new IndexChanges.Input(ImmutableSet.of(changeId), false);
      changeIndexedCounter.clear();
      adminRestSession.post("/config/server/index.changes", in).assertOK();
      assertThat(changeIndexedCounter.getCount(info(changeId))).isEqualTo(1);
    }
  }

  @Test
  public void indexNonVisibleChange() throws Exception {
    ChangeIndexedCounter changeIndexedCounter = new ChangeIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(changeIndexedCounter)) {
      String changeId = createChange().getChangeId();
      ChangeInfo changeInfo = info(changeId);
      projectOperations
          .project(project)
          .forUpdate()
          .add(block(Permission.READ).ref("refs/heads/master").group(REGISTERED_USERS))
          .update();
      IndexChanges.Input in = new IndexChanges.Input(ImmutableSet.of(changeId), false);
      changeIndexedCounter.clear();
      adminRestSession.post("/config/server/index.changes", in).assertOK();
      assertThat(changeIndexedCounter.getCount(changeInfo)).isEqualTo(1);
    }
  }

  @Test
  public void deleteMissingChangeFromIndexByNumericId() throws Exception {
    PushOneCommit.Result result = createChange();
    Change.Id changeId = result.getChange().getId();

    assertThat(getChangeFromIndex(changeId)).isPresent();
    deleteChangeFromNoteDbWithoutUpdatingIndex(changeId);
    assertThat(getChangeFromIndex(changeId)).isPresent();

    IndexChanges.Input in =
        new IndexChanges.Input(ImmutableSet.of(String.valueOf(changeId.get())), true);
    adminRestSession.post("/config/server/index.changes", in).assertOK();

    assertThat(getChangeFromIndex(changeId)).isEmpty();
  }

  @Test
  public void deleteMissingChangeFromIndexByProjectAndNumericId() throws Exception {
    PushOneCommit.Result result = createChange();
    Change.Id changeId = result.getChange().getId();

    assertThat(getChangeFromIndex(changeId)).isPresent();
    deleteChangeFromNoteDbWithoutUpdatingIndex(changeId);
    assertThat(getChangeFromIndex(changeId)).isPresent();

    IndexChanges.Input in =
        new IndexChanges.Input(ImmutableSet.of(project.get() + "~" + changeId.get()), true);
    adminRestSession.post("/config/server/index.changes", in).assertOK();

    assertThat(getChangeFromIndex(changeId)).isEmpty();
  }

  @Test
  public void indexMultipleChanges() throws Exception {
    ChangeIndexedCounter changeIndexedCounter = new ChangeIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(changeIndexedCounter)) {
      ImmutableSet.Builder<String> changeIds = ImmutableSet.builder();
      for (int i = 0; i < 10; i++) {
        changeIds.add(createChange().getChangeId());
      }
      IndexChanges.Input in = new IndexChanges.Input(changeIds.build(), false);
      changeIndexedCounter.clear();
      adminRestSession.post("/config/server/index.changes", in).assertOK();
      for (String changeId : in.changes()) {
        assertThat(changeIndexedCounter.getCount(info(changeId))).isEqualTo(1);
      }
    }
  }

  private void deleteChangeFromNoteDbWithoutUpdatingIndex(Change.Id changeId) throws Exception {
    try (Repository repo = repoManager.openRepository(project);
        TestRepository<Repository> testRepo = new TestRepository<>(repo)) {
      testRepo.delete(RefNames.changeMetaRef(changeId));
    }
  }

  Optional<ChangeData> getChangeFromIndex(Change.Id changeId) {
    ChangeIndex idx = changeIndexCollection.getSearchIndex();
    QueryOptions opts = IndexedChangeQuery.createOptions(indexConfig, 0, 1, ImmutableSet.of());
    return idx.get(changeId, opts);
  }
}
