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
import static com.google.gerrit.entities.RefNames.changeMetaRef;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.TestActionRefUpdateContext.openTestRefUpdateContext;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ChangeIndexedCounter;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.change.IndexedChangeQuery;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeNumberVirtualIdAlgorithm;
import com.google.gerrit.server.restapi.config.IndexChanges;
import com.google.inject.Inject;
import java.util.Optional;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

public class IndexChangesIT extends AbstractDaemonTest {
  private static final String TEST_CHANGE_NUM = "1";
  private static final String TEST_CHANGE_ID = "I8350971af868ee34b17fc8703aa9ef40c03f5ec5";
  private static final boolean PRESERVE_MISSING = false;
  private static final boolean DELETE_MISSING = true;

  @Inject private ProjectOperations projectOperations;
  @Inject private ExtensionRegistry extensionRegistry;
  @Inject private ChangeIndexCollection changeIndexCollection;
  @Inject private IndexConfig indexConfig;
  @Inject private ChangeNumberVirtualIdAlgorithm changeNumberVirtualIdAlgorithm;

  @Test
  public void indexRequestFromNonAdminRejected() throws Exception {
    ChangeIndexedCounter changeIndexedCounter = new ChangeIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(changeIndexedCounter)) {
      PushOneCommit.Result change = createChange();
      changeIndexedCounter.clear();
      userRestSession
          .post("/config/server/index.changes", indexChangesInput(change.getChange().getId()))
          .assertForbidden();
      assertThat(changeIndexedCounter.getCount(info(change.getChangeId()))).isEqualTo(0);
    }
  }

  @Test
  public void indexVisibleChange() throws Exception {
    ChangeIndexedCounter changeIndexedCounter = new ChangeIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(changeIndexedCounter)) {
      PushOneCommit.Result change = createChange();
      changeIndexedCounter.clear();
      adminRestSession
          .post("/config/server/index.changes", indexChangesInput(change.getChange().getId()))
          .assertOK();
      assertThat(changeIndexedCounter.getCount(info(change.getChangeId()))).isEqualTo(1);
    }
  }

  @Test
  public void indexChangeNotInIndex() throws Exception {
    PushOneCommit.Result change = createChange();
    Change.Id changeId = change.getChange().getId();

    assertThat(getChangeFromIndex(changeId)).isPresent();
    indexer.delete(changeId);
    assertThat(getChangeFromIndex(changeId)).isEmpty();

    adminRestSession.post("/config/server/index.changes", indexChangesInput(changeId)).assertOK();
    assertThat(getChangeFromIndex(changeId)).isPresent();
  }

  @Test
  public void indexNonVisibleChange() throws Exception {
    ChangeIndexedCounter changeIndexedCounter = new ChangeIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(changeIndexedCounter)) {
      String changeId = projectAndChangeNumId(project, createChange().getChange().getId());
      ChangeInfo changeInfo = info(changeId);
      projectOperations
          .project(project)
          .forUpdate()
          .add(block(Permission.READ).ref("refs/heads/master").group(REGISTERED_USERS))
          .update();
      changeIndexedCounter.clear();
      adminRestSession.post("/config/server/index.changes", indexChangesInput(changeId)).assertOK();
      assertThat(changeIndexedCounter.getCount(changeInfo)).isEqualTo(1);
    }
  }

  @Test
  public void indexChangeWithPlainNumericIdAccepted() throws Exception {
    Change.Id changeId = createChange().getChange().getId();
    adminRestSession
        .post("/config/server/index.changes", indexChangesInput(String.valueOf(changeId.get())))
        .assertOK();
  }

  @Test
  public void deleteMissingChangeFromIndexWithPlainNumericIdRejected() throws Exception {
    adminRestSession
        .post("/config/server/index.changes", indexChangesInput(TEST_CHANGE_NUM, DELETE_MISSING))
        .assertBadRequest();
  }

  @Test
  public void indexChangeWithTripletIdAccepted() throws Exception {
    String changeId = createChange().getChangeId();
    adminRestSession
        .post("/config/server/index.changes", indexChangesInput(project + "~master~" + changeId))
        .assertOK();
  }

  @Test
  public void deleteMissingChangeFromIndexWithTripletIdRejected() throws Exception {
    adminRestSession
        .post(
            "/config/server/index.changes",
            indexChangesInput(project + "~master~" + TEST_CHANGE_ID, DELETE_MISSING))
        .assertBadRequest();
  }

  @Test
  public void deleteMissingChangeFromIndexByProjectAndNumericId() throws Exception {
    PushOneCommit.Result result = createChange();
    Change.Id changeId = result.getChange().getId();

    assertThat(getChangeFromIndex(changeId)).isPresent();
    deleteChangeFromNoteDbWithoutUpdatingIndex(changeId);
    assertThat(getChangeFromIndex(changeId)).isPresent();

    adminRestSession
        .post(
            "/config/server/index.changes",
            indexChangesInput(projectAndChangeNumId(project, changeId), DELETE_MISSING))
        .assertOK();

    assertThat(getChangeFromIndex(changeId)).isEmpty();
  }

  @Test
  public void indexMultipleChanges() throws Exception {
    ChangeIndexedCounter changeIndexedCounter = new ChangeIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(changeIndexedCounter)) {
      ImmutableSet.Builder<String> changeIds = ImmutableSet.builder();
      for (int i = 0; i < 10; i++) {
        changeIds.add(projectAndChangeNumId(project, createChange().getChange().getId()));
      }
      IndexChanges.Input in = new IndexChanges.Input(changeIds.build(), PRESERVE_MISSING);
      changeIndexedCounter.clear();
      adminRestSession.post("/config/server/index.changes", in).assertOK();
      for (String changeId : in.changes()) {
        assertThat(changeIndexedCounter.getCount(info(changeId))).isEqualTo(1);
      }
    }
  }

  @Test
  @GerritConfig(name = "gerrit.importedServerId", value = "imported-server-id")
  public void deleteMissingImportedChangeFromIndex() throws Exception {
    PushOneCommit.Result result = createImportedChange();
    Change.Id changeId = result.getChange().getId();
    Change.Id virtualId =
        changeNumberVirtualIdAlgorithm.apply(() -> "imported-server-id", changeId);

    assertThat(getChangeFromIndex(virtualId)).isPresent();
    deleteChangeFromNoteDbWithoutUpdatingIndex(changeId);
    assertThat(getChangeFromIndex(virtualId)).isPresent();

    adminRestSession
        .post("/config/server/index.changes", indexChangesInput(changeId, PRESERVE_MISSING))
        .assertOK();
    assertThat(getChangeFromIndex(virtualId)).isPresent();

    adminRestSession
        .post("/config/server/index.changes", indexChangesInput(changeId, DELETE_MISSING))
        .assertOK();
    assertThat(getChangeFromIndex(virtualId)).isEmpty();
  }

  private IndexChanges.Input indexChangesInput(String changeId) {
    return new IndexChanges.Input(ImmutableSet.of(changeId), PRESERVE_MISSING);
  }

  private IndexChanges.Input indexChangesInput(Change.Id changeId) {
    return indexChangesInput(projectAndChangeNumId(project, changeId));
  }

  private IndexChanges.Input indexChangesInput(String changeId, boolean deleteMissing) {
    return new IndexChanges.Input(ImmutableSet.of(changeId), deleteMissing);
  }

  private IndexChanges.Input indexChangesInput(Change.Id changeId, boolean deleteMissing) {
    return indexChangesInput(projectAndChangeNumId(project, changeId), deleteMissing);
  }

  private PushOneCommit.Result createImportedChange() throws Exception {
    PushOneCommit.Result change = createChange();
    Change.Id changeId = change.getChange().getId();
    String metaRef = changeMetaRef(changeId);

    try (Repository repo = repoManager.openRepository(project);
        ObjectInserter inserter = repo.newObjectInserter();
        ObjectReader reader = repo.newObjectReader();
        RevWalk revWalk = new RevWalk(reader);
        var ignored = openTestRefUpdateContext()) {

      Ref ref = repo.getRefDatabase().exactRef(metaRef);
      RevCommit tip = revWalk.parseCommit(ref.getObjectId());

      CommitBuilder commit = new CommitBuilder();
      commit.setTreeId(tip.getTree());
      commit.setAuthor(
          new PersonIdent("Gerrit User " + admin.id(), admin.id() + "@imported-server-id"));
      commit.setCommitter(new PersonIdent("Gerrit Code Review", admin.email()));
      commit.setMessage(tip.getFullMessage());

      ObjectId commitId = inserter.insert(commit);
      inserter.flush();

      RefUpdate refUpdate = repo.updateRef(metaRef);
      refUpdate.setNewObjectId(commitId);
      refUpdate.forceUpdate();
    }

    // Re-index after rewriting the meta-ref so the index reflects the imported serverId,
    // ensuring the virtualId in the index matches what the API will compute at delete time.
    indexer.delete(changeId);
    indexer.index(project, changeId);

    return change;
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

  String projectAndChangeNumId(Project.NameKey project, Change.Id changeNum) {
    return project + "~" + changeNum;
  }
}
