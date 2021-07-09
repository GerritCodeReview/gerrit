// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.update;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.eclipse.jgit.transport.ReceiveCommand.Type.CREATE;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment.Status;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.RobotComment;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Tests for {@link BatchOpsExecutor} */
public class BatchOpsExecutorTest {

  @Rule public InMemoryTestEnvironment testEnvironment = new InMemoryTestEnvironment();

  @Inject private BatchOpsExecutor.Factory batchOpsExecutorFactory;
  @Inject private CommentsUtil commentsUtil;
  @Inject private AccountManager accountManager;

  @Inject protected BatchUpdate.Factory batchUpdateFactory;
  @Inject protected ChangeInserter.Factory changeInserterFactory;
  @Inject protected ChangeNotes.Factory changeNotesFactory;
  @Inject protected GitRepositoryManager repoManager;
  @Inject protected Provider<CurrentUser> user;
  @Inject protected Sequences sequences;

  protected Project.NameKey project;
  protected TestRepository<Repository> repo;
  private Account.Id admin;
  private Account.Id otherUser;

  @Before
  public void setUp() throws Exception {
    project = Project.nameKey("test");
    Repository inMemoryRepo = repoManager.createRepository(project);
    repo = new TestRepository<>(inMemoryRepo);

    admin = accountManager.authenticate(AuthRequest.forUser("admin")).getAccountId();
    otherUser = accountManager.authenticate(AuthRequest.forUser("test-user")).getAccountId();
  }

  @Test
  public void repoOnlyOpBatch_singleRefInBatch_canExecuteAfterClear() throws Exception {
    RevCommit masterCommit = repo.branch("master").commit().create();
    List<String> firstBatch = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      firstBatch.add("refs/heads/first-batch-branch" + i);
    }
    List<String> secondBatch = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      secondBatch.add("refs/heads/second-batch-branch" + i);
    }
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      for (String ref : firstBatch) {
        batchOpsExecutor.addRepoOnlyOpBatch(new CreateRefsRepoOnlyOp(ref, masterCommit));
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(firstBatch.size());
      batchOpsExecutor.execute();
      assertExecutedWithAllOK(batchOpsExecutor, firstBatch, firstBatch.size(), masterCommit);
      batchOpsExecutor.clear();
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(0);
      assertThat(batchOpsExecutor.getRefUpdates()).isEmpty();
      for (String ref : secondBatch) {
        batchOpsExecutor.addRepoOnlyOpBatch(new CreateRefsRepoOnlyOp(ref, masterCommit));
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(secondBatch.size());
      batchOpsExecutor.execute();

      assertExecutedWithAllOK(batchOpsExecutor, secondBatch, secondBatch.size(), masterCommit);
    }
  }

  @Test
  public void repoOnlyOpBatch_singleRefInBatch_notCleared_executeFails() throws Exception {
    RevCommit masterCommit = repo.branch("master").commit().create();
    List<String> refs = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      refs.add("refs/heads/branch" + i);
    }
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      for (String ref : refs) {
        batchOpsExecutor.addRepoOnlyOpBatch(new CreateRefsRepoOnlyOp(ref, masterCommit));
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refs.size());
      batchOpsExecutor.execute();
      assertExecutedWithAllOK(batchOpsExecutor, refs, refs.size(), masterCommit);
      IllegalStateException thrown =
          assertThrows(
              IllegalStateException.class,
              () ->
                  batchOpsExecutor.addRepoOnlyOpBatch(
                      new CreateRefsRepoOnlyOp("refs/heads/master", ObjectId.zeroId())));
      assertThat(thrown).hasMessageThat().contains("update already executed");
    }
  }

  @Test
  public void repoOnlyOpBatch_batchUpdateMultipleRefsInBatch_allCounted() throws Exception {
    RevCommit masterCommit = repo.branch("master").commit().create();
    List<String> refs = new ArrayList<>();
    int numberOfRefs = 10;
    int refsInFirstOp = 3;
    for (int i = 0; i < numberOfRefs; i++) {
      refs.add("refs/heads/branch" + i);
    }

    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      batchOpsExecutor.addRepoOnlyOpBatch(
          new CreateRefsRepoOnlyOp(refs.subList(0, refsInFirstOp), masterCommit));
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refsInFirstOp);
      batchOpsExecutor.addRepoOnlyOpBatch(
          new CreateRefsRepoOnlyOp(refs.subList(refsInFirstOp, refs.size()), masterCommit));
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(numberOfRefs);
      batchOpsExecutor.execute();
      assertExecutedWithAllOK(batchOpsExecutor, refs, refs.size(), masterCommit);
      batchOpsExecutor.clear();
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(0);
      assertThat(batchOpsExecutor.getRefUpdates()).isEmpty();
    }
  }

  @Test
  public void repoOnlyOpBatch_failedOp_notCounted() throws Exception {
    RevCommit masterCommit = repo.branch("master").commit().create();
    int numberOfRefs = 10;
    int refsPreFail = 3;
    List<String> refs = new ArrayList<>();
    for (int i = 0; i < numberOfRefs; i++) {
      refs.add("refs/heads/branch" + i);
    }
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      for (int i = 0; i < refsPreFail; i++) {
        batchOpsExecutor.addRepoOnlyOpBatch(new CreateRefsRepoOnlyOp(refs.get(i), masterCommit));
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refsPreFail);
      assertThrows(
          BadRequestException.class,
          () ->
              batchOpsExecutor.addRepoOnlyOpBatch(
                  new ThrowingUpdateRepoOnly(
                      refs.subList(refsPreFail, refs.size()), masterCommit)));
      // The caller is free to either retry this update or ignore and keep adding to the batch, only
      // added updates are counted and ref updates applied during the execution.
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refsPreFail);
      for (int i = refsPreFail; i < refs.size(); i++) {
        batchOpsExecutor.addRepoOnlyOpBatch(new CreateRefsRepoOnlyOp(refs.get(i), masterCommit));
      }
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refs.size());
      batchOpsExecutor.execute();
      assertExecutedWithAllOK(batchOpsExecutor, refs, refs.size(), masterCommit);
    }
  }

  @Test
  public void repoOnlyOpBatch_atomic_singleRefFails_allRefsAborted() throws Exception {
    RevCommit masterCommit = repo.branch("master").commit().create();
    RevCommit branchCommit = repo.branch("branch").commit().parent(masterCommit).create();
    List<String> refs = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      refs.add("refs/heads/branch" + i);
    }
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      for (String ref : refs) {
        batchOpsExecutor.addRepoOnlyOpBatch(new CreateRefsRepoOnlyOp(ref, masterCommit));
      }
      batchOpsExecutor.addRepoOnlyOpBatch(
          new CreateRefsRepoOnlyOp("refs/heads/master", branchCommit));
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refs.size() + 1);
      UpdateException thrown = assertThrows(UpdateException.class, batchOpsExecutor::execute);
      assertThat(thrown).hasMessageThat().contains("Update aborted with one or more lock failures");
      assertThat(batchOpsExecutor.isExecuted()).isTrue();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refs.size() + 1);
      Map<String, ReceiveCommand> refUpdates = batchOpsExecutor.getRefUpdates();
      assertThat(refUpdates).hasSize(refs.size() + 1);
      for (String ref : refs) {
        ReceiveCommand cmd = refUpdates.get(ref);
        assertThat(cmd).isNotNull();
        assertThat(cmd.getResult()).isEqualTo(Result.REJECTED_OTHER_REASON);
        assertThat(repo.getRepository().exactRef(ref)).isNull();
      }
      assertThat(refUpdates.get("refs/heads/master").getResult()).isEqualTo(Result.LOCK_FAILURE);
      assertThat(repo.getRepository().exactRef("refs/heads/master").getObjectId())
          .isEqualTo(masterCommit.getId());
      batchOpsExecutor.clear();
      for (String ref : refs) {
        batchOpsExecutor.addRepoOnlyOpBatch(new CreateRefsRepoOnlyOp(ref, masterCommit));
      }
      batchOpsExecutor.execute();
      assertExecutedWithAllOK(batchOpsExecutor, refs, refs.size(), masterCommit);
    }
  }

  @Test
  public void repoOnlyOpBatch_nonAtomic_refsUpdated() throws Exception {
    RevCommit masterCommit = repo.branch("master").commit().create();
    RevCommit branchCommit = repo.branch("branch").commit().parent(masterCommit).create();
    List<String> firstBatch = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      firstBatch.add("refs/heads/first-batch-branch" + i);
    }
    List<String> secondBatch = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      secondBatch.add("refs/heads/second-batch-branch" + i);
    }
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(
            project, user.get(), TimeUtil.nowTs(), /*nonAtomic=*/ true)) {
      for (String ref : firstBatch) {
        batchOpsExecutor.addRepoOnlyOpBatch(new CreateRefsRepoOnlyOp(ref, masterCommit));
      }
      batchOpsExecutor.addRepoOnlyOpBatch(
          new CreateRefsRepoOnlyOp("refs/heads/master", branchCommit));
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(firstBatch.size() + 1);
      UpdateException thrown = assertThrows(UpdateException.class, batchOpsExecutor::execute);
      assertThat(thrown).hasMessageThat().contains("Update failed");
      assertThat(batchOpsExecutor.isExecuted()).isTrue();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(firstBatch.size() + 1);
      Map<String, ReceiveCommand> refUpdates = batchOpsExecutor.getRefUpdates();
      assertThat(refUpdates).hasSize(firstBatch.size() + 1);
      // Refs were updated, non-atomic execution succeeds
      for (String ref : firstBatch) {
        ReceiveCommand cmd = refUpdates.get(ref);
        assertThat(cmd).isNotNull();
        assertThat(cmd.getResult()).isEqualTo(Result.OK);
        assertThat(repo.getRepository().exactRef(ref).getObjectId())
            .isEqualTo(masterCommit.getId());
      }
      // Failed ref reported
      assertThat(refUpdates.get("refs/heads/master").getResult()).isEqualTo(Result.LOCK_FAILURE);
      assertThat(repo.getRepository().exactRef("refs/heads/master").getObjectId())
          .isEqualTo(masterCommit.getId());
      batchOpsExecutor.clear();
      // Executor can be used for another batch
      batchOpsExecutor.addRepoOnlyOpBatch(new CreateRefsRepoOnlyOp(secondBatch, masterCommit));
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(secondBatch.size());
      batchOpsExecutor.execute();
      assertExecutedWithAllOK(batchOpsExecutor, secondBatch, secondBatch.size(), masterCommit);
    }
  }

  @Test
  public void repoOnlyOpBatch_nonAtomic_forSameChange_batchFails() throws Exception {
    RevCommit masterCommit = repo.branch("master").commit().create();

    List<String> refs = new ArrayList<>();
    refs.add("refs/heads/master");
    refs.add("refs/changes/01/1/meta");
    refs.add("refs/changes/01/1/1");
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(
            project, user.get(), TimeUtil.nowTs(), /*nonAtomic=*/ true)) {
      batchOpsExecutor.addRepoOnlyOpBatch(new CreateRefsRepoOnlyOp(refs, masterCommit));
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refs.size());
      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, batchOpsExecutor::execute);
      assertThat(thrown)
          .hasMessageThat()
          .contains("non-atomic batch ref update only allows one ref per change");
      assertThat(batchOpsExecutor.isExecuted()).isTrue();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refs.size());
      Map<String, ReceiveCommand> refUpdates = batchOpsExecutor.getRefUpdates();
      assertThat(refUpdates).hasSize(refs.size());
      for (String ref : refs) {
        ReceiveCommand cmd = refUpdates.get(ref);
        assertThat(cmd).isNotNull();
        assertThat(cmd.getResult()).isEqualTo(Result.NOT_ATTEMPTED);
      }

      batchOpsExecutor.clear();

      // Attempt the same with updates in different ops
      for (String ref : refs) {
        batchOpsExecutor.addRepoOnlyOpBatch(new CreateRefsRepoOnlyOp(ref, masterCommit));
      }
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refs.size());
      thrown = assertThrows(IllegalArgumentException.class, batchOpsExecutor::execute);
      assertThat(thrown)
          .hasMessageThat()
          .contains("non-atomic batch ref update only allows one ref per change");
      assertThat(batchOpsExecutor.isExecuted()).isTrue();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refs.size());
      refUpdates = batchOpsExecutor.getRefUpdates();
      assertThat(refUpdates).hasSize(refs.size());
      for (String ref : refs) {
        ReceiveCommand cmd = refUpdates.get(ref);
        assertThat(cmd).isNotNull();
        assertThat(cmd.getResult()).isEqualTo(Result.NOT_ATTEMPTED);
      }
    }
  }

  @Test
  public void changeUpdateOpBatch_canExecuteAfterClear() throws Exception {
    Map<Change.Id, ObjectId> firstBatch = new HashMap<>();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChange();
      firstBatch.put(changeId, getMetaId(changeId).get());
    }
    Map<Change.Id, ObjectId> secondBatch = new HashMap<>();
    for (int i = 0; i < 5; i++) {
      Change.Id changeId = createChange();
      secondBatch.put(changeId, getMetaId(changeId).get());
    }

    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      Map<String, Optional<ObjectId>> expectedRefs = new HashMap<>();
      for (Change.Id changeId : firstBatch.keySet()) {
        expectedRefs.put("refs/heads/change-branch" + changeId.get(), Optional.empty());
        expectedRefs.put(RefNames.changeMetaRef(changeId), Optional.of(firstBatch.get(changeId)));
        batchOpsExecutor.addOpBatch(
            changeId,
            new PostChangeMessageUpdateOp(
                "refs/heads/change-branch" + changeId.get(), getMetaId(changeId).get()));
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(2 * firstBatch.size());
      batchOpsExecutor.execute();
      assertExecutedWithOK(batchOpsExecutor, expectedRefs, expectedRefs.size());
      batchOpsExecutor.clear();
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(0);
      assertThat(batchOpsExecutor.getRefUpdates()).isEmpty();
      expectedRefs = new HashMap<>();
      for (Change.Id changeId : secondBatch.keySet()) {
        expectedRefs.put("refs/heads/change-branch" + changeId.get(), Optional.empty());
        expectedRefs.put(RefNames.changeMetaRef(changeId), Optional.of(secondBatch.get(changeId)));
        batchOpsExecutor.addOpBatch(
            changeId,
            new PostChangeMessageUpdateOp(
                "refs/heads/change-branch" + changeId.get(), getMetaId(changeId).get()));
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(2 * secondBatch.size());
      batchOpsExecutor.execute();

      assertExecutedWithOK(batchOpsExecutor, expectedRefs, expectedRefs.size());
    }
  }

  @Test
  public void changeUpdateOpBatch_notCleared_executeFails() throws Exception {
    Map<Change.Id, ObjectId> changes = new HashMap<>();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChange();
      changes.put(changeId, getMetaId(changeId).get());
    }

    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      Map<String, Optional<ObjectId>> expectedRefs = new HashMap<>();
      for (Change.Id changeId : changes.keySet()) {
        expectedRefs.put("refs/heads/change-branch" + changeId.get(), Optional.empty());
        expectedRefs.put(RefNames.changeMetaRef(changeId), Optional.of(changes.get(changeId)));
        batchOpsExecutor.addOpBatch(
            changeId,
            new PostChangeMessageUpdateOp(
                "refs/heads/change-branch" + changeId.get(), getMetaId(changeId).get()));
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(2 * changes.size());
      batchOpsExecutor.execute();
      assertExecutedWithOK(batchOpsExecutor, expectedRefs, expectedRefs.size());

      IllegalStateException thrown =
          assertThrows(
              IllegalStateException.class,
              () -> batchOpsExecutor.addOpBatch(createChange(), new PostChangeMessageUpdateOp()));
      assertThat(thrown).hasMessageThat().contains("update already executed");
    }
  }

  @Test
  public void changeUpdateOpBatch_addOpBatch_forSameChangeFails() throws Exception {
    List<Change.Id> changes = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChange();
      changes.add(changeId);
    }

    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      for (Change.Id changeId : changes) {
        batchOpsExecutor.addOpBatch(changeId, new PostChangeMessageUpdateOp());
      }

      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(changes.size());
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () -> batchOpsExecutor.addOpBatch(changes.get(0), new PostChangeMessageUpdateOp()));
      assertThat(thrown)
          .hasMessageThat()
          .contains(String.format("ops for change %s already added to this batch", changes.get(0)));
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(changes.size());
    }
  }

  @Test
  public void changeUpdateOpBatch_addOpsBatch_forSameChangeFails() throws Exception {
    List<Change.Id> changes = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChange();
      changes.add(changeId);
    }

    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      for (Change.Id changeId : changes) {
        batchOpsExecutor.addOpsBatch(
            changeId,
            ImmutableList.of(
                new CreateRefsOnlyBatchUpdateOp(
                    RefNames.patchSetRef(PatchSet.id(changeId, 2)), getMetaId(changeId).get()),
                new PostChangeMessageUpdateOp()));
      }
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  batchOpsExecutor.addOpsBatch(
                      changes.get(0),
                      ImmutableList.of(
                          new CreateRefsOnlyBatchUpdateOp(
                              RefNames.patchSetRef(PatchSet.id(changes.get(0), 2)),
                              getMetaId(changes.get(0)).get()),
                          new PostChangeMessageUpdateOp())));
      assertThat(thrown)
          .hasMessageThat()
          .contains(String.format("ops for change %s already added to this batch", changes.get(0)));
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(2 * changes.size());
    }
  }

  @Test
  public void changeUpdateOpBatch_failedOp_notCounted() throws Exception {
    int numberOfRefs = 10;
    int changesPreFail = 3;
    List<Change.Id> changes = new ArrayList<>();
    for (int i = 0; i < numberOfRefs; i++) {
      Change.Id changeId = createChange();
      changes.add(changeId);
    }
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      Map<String, Optional<ObjectId>> expectedRefs = new HashMap<>();
      for (int i = 0; i < changesPreFail; i++) {
        expectedRefs.put("refs/heads/change-branch" + changes.get(i), Optional.empty());
        expectedRefs.put(RefNames.changeMetaRef(changes.get(i)), getMetaId(changes.get(i)));
        batchOpsExecutor.addOpsBatch(
            changes.get(i),
            ImmutableList.of(
                new CreateRefsOnlyBatchUpdateOp(
                    "refs/heads/change-branch" + changes.get(i), getMetaId(changes.get(i)).get()),
                new PostChangeMessageUpdateOp()));
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(2 * changesPreFail);
      assertThrows(
          BadRequestException.class,
          () ->
              batchOpsExecutor.addOpsBatch(
                  Iterables.getLast(changes),
                  ImmutableList.of(
                      new CreateRefsOnlyBatchUpdateOp(
                          "refs/heads/change-branch" + changes.get(changesPreFail),
                          getMetaId(changes.get(changesPreFail)).get()),
                      new ThrowingPostChangeMessageOp())));
      // The caller is free to either retry this update or ignore and keep adding to the batch, only
      // added updates are counted and corresponding refs updated during the execution.
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(2 * changesPreFail);

      for (int i = changesPreFail; i < changes.size(); i++) {
        expectedRefs.put("refs/heads/change-branch" + changes.get(i), Optional.empty());
        expectedRefs.put(RefNames.changeMetaRef(changes.get(i)), getMetaId(changes.get(i)));
        batchOpsExecutor.addOpsBatch(
            changes.get(i),
            ImmutableList.of(
                new CreateRefsOnlyBatchUpdateOp(
                    "refs/heads/change-branch" + changes.get(i), getMetaId(changes.get(i)).get()),
                new PostChangeMessageUpdateOp()));
      }
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(2 * changes.size());
      batchOpsExecutor.execute();
      assertExecutedWithOK(batchOpsExecutor, expectedRefs, expectedRefs.size());
    }
  }

  @Test
  public void changeUpdateOpBatch_singleOps_allCounted() throws Exception {

    ImmutableMap.Builder<Change.Id, Collection<BatchUpdateOp>> ops = ImmutableMap.builder();
    ImmutableMap.Builder<String, Optional<ObjectId>> expectedRefs = ImmutableMap.builder();
    RevCommit masterCommit = repo.branch("master").commit().create();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChange();
      ops.put(
          changeId,
          ImmutableList.of(
              new PostChangeMessageUpdateOp(
                  "refs/heads/change-branch" + changeId.get(), masterCommit)));
      expectedRefs
          .put(RefNames.changeMetaRef(changeId), getMetaId(changeId))
          .put("refs/heads/change-branch" + changeId.get(), Optional.empty());
    }
    testAllRefsCounted(ops.build(), ImmutableList.of(), expectedRefs.build());

    ops = ImmutableMap.builder();
    expectedRefs = ImmutableMap.builder();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChange();
      ops.put(changeId, ImmutableList.of(new AddRobotCommentOp()));
      expectedRefs.put(RefNames.robotCommentsRef(changeId), Optional.empty());
    }
    testAllRefsCounted(ops.build(), ImmutableList.of(), expectedRefs.build());

    ops = ImmutableMap.builder();
    expectedRefs = ImmutableMap.builder();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChangeWithUpdates();
      ops.put(changeId, ImmutableList.of(new DeleteChangeOp()));
      expectedRefs.put(RefNames.changeMetaRef(changeId), getMetaId(changeId));
    }
    testAllRefsCounted(ops.build(), ImmutableList.of(), expectedRefs.build());

    ops = ImmutableMap.builder();
    expectedRefs = ImmutableMap.builder();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChangeWithUpdates();
      ops.put(changeId, ImmutableList.of(new RewriteChangeMessageOp()));
      expectedRefs.put(RefNames.changeMetaRef(changeId), getMetaId(changeId));
    }
    testAllRefsCounted(ops.build(), ImmutableList.of(), expectedRefs.build());
  }

  @Test
  public void changeUpdateOpBatch_multipleOps_allCounted() throws Exception {

    ImmutableMap.Builder<Change.Id, Collection<BatchUpdateOp>> ops = ImmutableMap.builder();
    ImmutableMap.Builder<String, Optional<ObjectId>> expectedRefs = ImmutableMap.builder();
    RevCommit masterCommit = repo.branch("master").commit().create();

    // Rewrite comments and update some refs at the same time
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChangeWithUpdates();
      ops.put(
          changeId,
          ImmutableList.of(
              new CreateRefsOnlyBatchUpdateOp(
                  "refs/heads/change-branch" + changeId.get(), masterCommit),
              new RewriteCommentOp()));
      expectedRefs
          .put(RefNames.changeMetaRef(changeId), getMetaId(changeId))
          .put("refs/heads/change-branch" + changeId.get(), Optional.empty());
    }
    testAllRefsCounted(ops.build(), ImmutableList.of(), expectedRefs.build());
    ops = ImmutableMap.builder();
    expectedRefs = ImmutableMap.builder();

    // Drafts are in All-Users and are not counted, so update goes through without results
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChange();
      ops.put(changeId, ImmutableList.of(new AddRobotCommentOp(), new PublishAllAddNewDraftOp()));
      expectedRefs.put(RefNames.robotCommentsRef(changeId), Optional.empty());
    }
    testAllRefsCounted(ops.build(), ImmutableList.of(), expectedRefs.build());
  }

  @Test
  public void changeUpdateOpBatch_withDistinctUpdate_allCounted() throws Exception {

    ImmutableMap.Builder<Change.Id, Collection<BatchUpdateOp>> ops = ImmutableMap.builder();
    ImmutableMap.Builder<String, Optional<ObjectId>> expectedRefs = ImmutableMap.builder();

    // Distinct updates result in separate commits in NotedDb, but there is the meta-ref is only
    // updated once.
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChange();
      ops.put(
          changeId,
          ImmutableList.of(new PostChangeMessageAsDistinctUpdateOp(), new AddRobotCommentOp()));
      expectedRefs
          .put(RefNames.changeMetaRef(changeId), getMetaId(changeId))
          .put(RefNames.robotCommentsRef(changeId), Optional.empty());
    }
    testAllRefsCounted(ops.build(), ImmutableList.of(), expectedRefs.build());
  }

  @Test
  public void changeUpdateOpBatch_withRepoOnlyOps_allCounted() throws Exception {

    ImmutableMap.Builder<Change.Id, Collection<BatchUpdateOp>> ops = ImmutableMap.builder();
    ImmutableMap.Builder<String, Optional<ObjectId>> expectedRefs = ImmutableMap.builder();
    RevCommit masterCommit = repo.branch("master").commit().create();

    // Rewrite comments and update some refs at the same time.
    ImmutableList.Builder<RepoOnlyOp> repoOnlyOps = ImmutableList.builder();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChangeWithUpdates();
      ops.put(changeId, ImmutableList.of(new RewriteCommentOp()));
      expectedRefs
          .put(RefNames.changeMetaRef(changeId), getMetaId(changeId))
          .put("refs/heads/change-branch" + changeId.get(), Optional.empty());
      repoOnlyOps.add(
          new CreateRefsRepoOnlyOp("refs/heads/change-branch" + changeId.get(), masterCommit));
    }

    testAllRefsCounted(ops.build(), repoOnlyOps.build(), expectedRefs.build());
  }

  public void testAllRefsCounted(
      Map<Change.Id, Collection<BatchUpdateOp>> ops,
      Collection<RepoOnlyOp> repoOnlyOps,
      Map<String, Optional<ObjectId>> expectedRefs)
      throws Exception {
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      for (RepoOnlyOp repoOnlyOp : repoOnlyOps) {
        batchOpsExecutor.addRepoOnlyOpBatch(repoOnlyOp);
      }
      for (Change.Id changeId : ops.keySet()) {
        batchOpsExecutor.addOpsBatch(changeId, ops.get(changeId));
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(expectedRefs.size());
      batchOpsExecutor.execute();
      assertExecutedWithOK(batchOpsExecutor, expectedRefs, expectedRefs.size());
      batchOpsExecutor.clear();
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(0);
      assertThat(batchOpsExecutor.getRefUpdates()).isEmpty();
    }
  }

  @Test
  public void changeUpdateOpBatch_atomic_singleRefFails_allRefsAborted() throws Exception {
    Map<Change.Id, ObjectId> changes = new HashMap<>();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChange();
      changes.put(changeId, getMetaId(changeId).get());
    }
    Change.Id failedChangeId = createChange();
    repo.branch("refs/heads/change-branch" + failedChangeId).commit().create();
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      Map<String, Optional<ObjectId>> expectedRefs = new HashMap<>();
      for (Change.Id changeId : changes.keySet()) {
        expectedRefs.put("refs/heads/change-branch" + changeId.get(), Optional.empty());
        expectedRefs.put(RefNames.changeMetaRef(changeId), Optional.of(changes.get(changeId)));
        batchOpsExecutor.addOpBatch(
            changeId,
            new PostChangeMessageUpdateOp(
                "refs/heads/change-branch" + changeId.get(), getMetaId(changeId).get()));
      }
      expectedRefs.put(RefNames.changeMetaRef(failedChangeId), getMetaId(failedChangeId));
      batchOpsExecutor.addOpBatch(
          failedChangeId,
          new PostChangeMessageUpdateOp(
              "refs/heads/change-branch" + failedChangeId.get(), getMetaId(failedChangeId).get()));

      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(2 * changes.size() + 2);
      UpdateException thrown = assertThrows(UpdateException.class, batchOpsExecutor::execute);
      assertThat(thrown).hasMessageThat().contains("Update aborted with one or more lock failures");

      assertExecutedWithFailResult(
          batchOpsExecutor, expectedRefs, 2 * changes.size() + 2, Result.REJECTED_OTHER_REASON);

      ReceiveCommand cmd =
          batchOpsExecutor.getRefUpdates().get("refs/heads/change-branch" + failedChangeId.get());
      assertThat(cmd.getResult()).isEqualTo(Result.LOCK_FAILURE);
      batchOpsExecutor.clear();
      expectedRefs = new HashMap<>();
      for (Change.Id changeId : changes.keySet()) {
        if (changeId.equals(failedChangeId)) {
          continue;
        }
        expectedRefs.put("refs/heads/change-branch" + changeId.get(), Optional.empty());
        expectedRefs.put(RefNames.changeMetaRef(changeId), Optional.of(changes.get(changeId)));
        batchOpsExecutor.addOpBatch(
            changeId,
            new PostChangeMessageUpdateOp(
                "refs/heads/change-branch" + changeId.get(), getMetaId(changeId).get()));
      }
      batchOpsExecutor.execute();
      assertExecutedWithOK(batchOpsExecutor, expectedRefs, expectedRefs.size());
    }
  }

  @Test
  public void changeUpdateOpBatch_nonAtomic_singleRefFails_refsUpdated() throws Exception {
    Map<Change.Id, ObjectId> changes = new HashMap<>();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChange();
      changes.put(changeId, getMetaId(changeId).get());
    }
    Change.Id failedChangeId = createChange();
    repo.branch("refs/heads/change-branch" + failedChangeId).commit().create();
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(
            project, user.get(), TimeUtil.nowTs(), /*nonAtomic=*/ true)) {
      Map<String, Optional<ObjectId>> expectedRefs = new HashMap<>();
      for (Change.Id changeId : changes.keySet()) {
        expectedRefs.put("refs/heads/change-branch" + changeId.get(), Optional.empty());
        expectedRefs.put(RefNames.changeMetaRef(changeId), Optional.of(changes.get(changeId)));
        batchOpsExecutor.addOpBatch(
            changeId,
            new PostChangeMessageUpdateOp(
                "refs/heads/change-branch" + changeId.get(), getMetaId(changeId).get()));
      }
      expectedRefs.put(RefNames.changeMetaRef(failedChangeId), getMetaId(failedChangeId));
      batchOpsExecutor.addOpBatch(
          failedChangeId,
          new PostChangeMessageUpdateOp(
              "refs/heads/change-branch" + failedChangeId.get(), getMetaId(failedChangeId).get()));

      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(2 * changes.size() + 2);
      UpdateException thrown = assertThrows(UpdateException.class, batchOpsExecutor::execute);
      assertThat(thrown).hasMessageThat().contains("Update failed");

      assertExecutedWithOK(batchOpsExecutor, expectedRefs, 2 * changes.size() + 2);

      ReceiveCommand cmd =
          batchOpsExecutor.getRefUpdates().get("refs/heads/change-branch" + failedChangeId.get());
      assertThat(cmd.getResult()).isEqualTo(Result.LOCK_FAILURE);
    }
  }

  @Test
  public void changeUpdateOpBatch_nonAtomic_forSameChange_fails() throws Exception {
    Change.Id changeId = createChangeWithUpdates();

    String expectedFailure =
        "non-atomic batch ref update only allows one ref per change"; // Refs in different
    // BatchUpdateOp
    ImmutableList<BatchUpdateOp> ops =
        ImmutableList.of(new AddRobotCommentOp(), new PostChangeMessageUpdateOp());
    Map<String, Optional<ObjectId>> expectedRefs =
        ImmutableMap.of(
            RefNames.changeMetaRef(changeId),
            getMetaId(changeId),
            RefNames.robotCommentsRef(changeId),
            Optional.empty());
    testNonAtomicChangeUpdateOpForSameChangeFails(
        changeId, ops, ImmutableList.of(), expectedRefs, expectedFailure);

    // Refs same BatchUpdateOp
    ops =
        ImmutableList.of(
            new PostChangeMessageUpdateOp(
                RefNames.patchSetRef(PatchSet.id(changeId, 2)), getMetaId(changeId).get()));
    expectedRefs =
        ImmutableMap.of(
            RefNames.changeMetaRef(changeId),
            getMetaId(changeId),
            RefNames.patchSetRef(PatchSet.id(changeId, 2)),
            Optional.empty());

    testNonAtomicChangeUpdateOpForSameChangeFails(
        changeId, ops, ImmutableList.of(), expectedRefs, expectedFailure);
    // Refs added by BatchUpdateOp#updateRepo
    ops =
        ImmutableList.of(
            new CreateRefsOnlyBatchUpdateOp(
                RefNames.patchSetRef(PatchSet.id(changeId, 2)), getMetaId(changeId).get()),
            new AddRobotCommentOp());
    expectedRefs =
        ImmutableMap.of(
            RefNames.patchSetRef(PatchSet.id(changeId, 2)),
            Optional.empty(),
            RefNames.robotCommentsRef(changeId),
            Optional.empty());
    testNonAtomicChangeUpdateOpForSameChangeFails(
        changeId, ops, ImmutableList.of(), expectedRefs, expectedFailure);

    // Refs added as RepoOnlyOp#updateRepo
    expectedRefs =
        ImmutableMap.of(
            RefNames.patchSetRef(PatchSet.id(changeId, 2)),
            Optional.empty(),
            RefNames.robotCommentsRef(changeId),
            Optional.empty());
    testNonAtomicChangeUpdateOpForSameChangeFails(
        changeId,
        ImmutableList.of(new AddRobotCommentOp()),
        ImmutableList.of(
            new CreateRefsRepoOnlyOp(
                RefNames.patchSetRef(PatchSet.id(changeId, 2)), getMetaId(changeId).get())),
        expectedRefs,
        expectedFailure);

    // Delete change also deletes all user comments in All-Users repo
    expectedRefs = ImmutableMap.of(RefNames.changeMetaRef(changeId), getMetaId(changeId));
    expectedFailure =
        "attempted non-atomic batch ref update of changeRepo and allUsersRepo at the same time";
    testNonAtomicChangeUpdateOpForSameChangeFails(
        changeId,
        ImmutableList.of(new DeleteChangeOp()),
        ImmutableList.of(),
        expectedRefs,
        expectedFailure);
  }

  public void testNonAtomicChangeUpdateOpForSameChangeFails(
      Change.Id changeId,
      Collection<BatchUpdateOp> ops,
      Collection<RepoOnlyOp> repoOnlyOps,
      Map<String, Optional<ObjectId>> expectedRefs,
      String expectedMessage)
      throws Exception {
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(
            project, user.get(), TimeUtil.nowTs(), /*nonAtomic=*/ true)) {
      batchOpsExecutor.addOpsBatch(changeId, ops);
      for (RepoOnlyOp repoOnlyOp : repoOnlyOps) {
        batchOpsExecutor.addRepoOnlyOpBatch(repoOnlyOp);
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(expectedRefs.size());

      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, batchOpsExecutor::execute);
      assertThat(thrown).hasMessageThat().contains(expectedMessage);

      assertExecutedWithFailResult(
          batchOpsExecutor, expectedRefs, expectedRefs.size(), Result.NOT_ATTEMPTED);

      batchOpsExecutor.clear();
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(0);
      assertThat(batchOpsExecutor.getRefUpdates()).isEmpty();
    }
  }

  private void assertExecutedWithAllOK(
      BatchOpsExecutor batchOpsExecutor,
      Collection<String> updatedRefs,
      int expectedUpdateSize,
      ObjectId targetCommit)
      throws IOException {
    assertThat(batchOpsExecutor.isExecuted()).isTrue();
    assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(expectedUpdateSize);
    Map<String, ReceiveCommand> refUpdates = batchOpsExecutor.getRefUpdates();
    assertThat(refUpdates).hasSize(expectedUpdateSize);
    for (String ref : updatedRefs) {
      ReceiveCommand cmd = refUpdates.get(ref);
      assertThat(cmd).isNotNull();
      assertThat(cmd.getResult()).isEqualTo(Result.OK);
      assertThat(repo.getRepository().exactRef(ref).getObjectId()).isEqualTo(targetCommit);
    }
  }

  /**
   * Asserts the execution results for successfully updated refs.
   *
   * @param batchOpsExecutor {@link BatchOpsExecutor} that updated the refs.
   * @param expectedRefs map of successfully updates refs to their pre-update tips
   * @param expectedUpdateSize expected number of executed ref updates.
   * @throws IOException if refs can not be extracted.
   */
  private void assertExecutedWithOK(
      BatchOpsExecutor batchOpsExecutor,
      Map<String, Optional<ObjectId>> expectedRefs,
      int expectedUpdateSize)
      throws IOException {
    assertThat(batchOpsExecutor.isExecuted()).isTrue();
    assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(expectedUpdateSize);
    Map<String, ReceiveCommand> refUpdates = batchOpsExecutor.getRefUpdates();
    assertThat(refUpdates).hasSize(expectedUpdateSize);
    for (Map.Entry<String, Optional<ObjectId>> expectedRef : expectedRefs.entrySet()) {
      ReceiveCommand cmd = refUpdates.get(expectedRef.getKey());
      assertWithMessage("expected update for " + expectedRef.getKey()).that(cmd).isNotNull();
      assertWithMessage("expected OK result for " + expectedRef.getKey())
          .that(cmd.getResult())
          .isEqualTo(Result.OK);
      Ref newRef = repo.getRepository().exactRef(expectedRef.getKey());
      // Assert update went through
      if (expectedRef.getValue().isPresent()) {
        switch (cmd.getType()) {
          case UPDATE:
            assertThat(newRef.getObjectId()).isNotEqualTo(expectedRef.getValue().get());
            break;
          case DELETE:
            assertThat(newRef).isNull();
            break;
          default:
            fail();
        }
      } else {
        assertThat(cmd.getType()).isEqualTo(CREATE);
        assertThat(newRef).isNotNull();
      }
    }
  }

  /**
   * Asserts the execution results for refs, that have failed to update.
   *
   * @param batchOpsExecutor {@link BatchOpsExecutor} that updated the refs.
   * @param expectedRefs map of successfully updates refs to their pre-update tips.
   * @param expectedUpdateSize expected number of executed ref updates.
   * @param failResult expected failed status of the execution.
   * @throws IOException if refs can not be extracted.
   */
  private void assertExecutedWithFailResult(
      BatchOpsExecutor batchOpsExecutor,
      Map<String, Optional<ObjectId>> expectedRefs,
      int expectedUpdateSize,
      Result failResult)
      throws IOException {
    assertThat(batchOpsExecutor.isExecuted()).isTrue();
    assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(expectedUpdateSize);
    Map<String, ReceiveCommand> refUpdates = batchOpsExecutor.getRefUpdates();
    assertThat(refUpdates).hasSize(expectedUpdateSize);
    for (Map.Entry<String, Optional<ObjectId>> expectedRef : expectedRefs.entrySet()) {
      ReceiveCommand cmd = refUpdates.get(expectedRef.getKey());
      assertWithMessage("expected update for " + expectedRef.getKey()).that(cmd).isNotNull();
      assertWithMessage(
              String.format("expected %s result for %s", failResult, expectedRef.getKey()))
          .that(cmd.getResult())
          .isEqualTo(failResult);
      // Assert refs remains the same
      if (expectedRef.getValue().isPresent()) {
        assertThat(repo.getRepository().exactRef(expectedRef.getKey()).getObjectId())
            .isEqualTo(expectedRef.getValue().get());
      } else {
        assertThat(repo.getRepository().exactRef(expectedRef.getKey())).isNull();
      }
    }
  }

  protected Change.Id createChangeWithUpdates() throws Exception {
    Change.Id id = Change.id(sequences.nextChangeId());
    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.nowTs())) {
      bu.insertChange(
          changeInserterFactory.create(
              id, repo.commit().message("Change").insertChangeId().create(), "refs/heads/master"));
      bu.addOp(id, new PostChangeMessageUpdateOp());
      bu.execute();
    }
    addCommentsAsUser(id, "admin");
    addCommentsAsUser(id, "test-user");

    assertThat(changeNotesFactory.create(project, id).getDraftComments(otherUser).values())
        .hasSize(1);
    assertThat(changeNotesFactory.create(project, id).getDraftComments(admin).values()).hasSize(1);
    assertThat(changeNotesFactory.create(project, id).getHumanComments().values()).hasSize(2);
    return id;
  }

  protected Change.Id createChange() throws Exception {
    Change.Id id = Change.id(sequences.nextChangeId());
    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.nowTs())) {
      bu.insertChange(
          changeInserterFactory.create(
              id, repo.commit().message("Change").insertChangeId().create(), "refs/heads/master"));
      bu.execute();
    }
    return id;
  }

  public void addCommentsAsUser(Change.Id changeId, String username)
      throws AccountException, IOException, RestApiException, UpdateException {
    Account.Id accountId =
        accountManager.authenticate(AuthRequest.forUser(username)).getAccountId();
    testEnvironment.setApiUser(accountId);
    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.nowTs())) {
      bu.addOp(changeId, new PublishAllAddNewDraftOp());
      bu.execute();
    }
    assertThat(changeNotesFactory.create(project, changeId).getDraftComments(accountId).values())
        .hasSize(1);
    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.nowTs())) {
      bu.addOp(changeId, new PublishAllAddNewDraftOp());
      bu.execute();
    }
    assertThat(changeNotesFactory.create(project, changeId).getDraftComments(accountId).values())
        .hasSize(1);
  }

  private Optional<ObjectId> getMetaId(Change.Id changeId) throws Exception {
    return Optional.ofNullable(repo.getRepository().exactRef(RefNames.changeMetaRef(changeId)))
        .map(Ref::getObjectId);
  }

  /**
   * Test {@link BatchUpdateOp} and {@link RepoOnlyOp} that are used to test possible usage
   * scenarios of {@link BatchOpsExecutor}.
   */
  private static class CreateRefsRepoOnlyOp implements RepoOnlyOp {
    protected final List<String> refs;

    protected final ObjectId tip;

    CreateRefsRepoOnlyOp(List<String> refs, ObjectId tip) {
      this.refs = refs;
      this.tip = tip;
    }

    CreateRefsRepoOnlyOp(String ref, ObjectId tip) {
      this(ImmutableList.of(ref), tip);
    }

    @Override
    public void updateRepo(RepoContext ctx) throws Exception {
      for (String ref : refs) {
        ctx.addRefUpdate(ObjectId.zeroId(), tip, ref);
      }
    }
  }

  private static class ThrowingUpdateRepoOnly extends CreateRefsRepoOnlyOp {

    ThrowingUpdateRepoOnly(List<String> refs, ObjectId tip) {
      super(refs, tip);
    }

    @Override
    public void updateRepo(RepoContext ctx) throws Exception {
      super.updateRepo(ctx);
      throw new BadRequestException("Always throwing update");
    }
  }

  private static class CreateRefsOnlyBatchUpdateOp extends CreateRefsRepoOnlyOp
      implements BatchUpdateOp {

    CreateRefsOnlyBatchUpdateOp(List<String> refs, ObjectId tip) {
      super(refs, tip);
    }

    CreateRefsOnlyBatchUpdateOp(String ref, ObjectId tip) {
      this(ImmutableList.of(ref), tip);
    }

    @Override
    public boolean updateChange(ChangeContext ctx) {
      return false;
    }
  }

  private static class PostChangeMessageUpdateOp extends CreateRefsRepoOnlyOp
      implements BatchUpdateOp {

    PostChangeMessageUpdateOp() {
      super(ImmutableList.of(), ObjectId.zeroId());
    }

    PostChangeMessageUpdateOp(List<String> refs, ObjectId tip) {
      super(refs, tip);
    }

    PostChangeMessageUpdateOp(String ref, ObjectId tip) {
      this(ImmutableList.of(ref), tip);
    }

    @Override
    public boolean updateChange(ChangeContext ctx) {
      ctx.getUpdate(ctx.getChange().currentPatchSetId())
          .setChangeMessage("Change message in update");
      return true;
    }
  }

  private static class ThrowingPostChangeMessageOp implements BatchUpdateOp {
    @Override
    public boolean updateChange(ChangeContext ctx) throws BadRequestException {
      ctx.getUpdate(ctx.getChange().currentPatchSetId())
          .setChangeMessage("Change message in throwing update");
      throw new BadRequestException("Always throwing update");
    }
  }

  private static class PostChangeMessageAsDistinctUpdateOp implements BatchUpdateOp {
    @Override
    public boolean updateChange(ChangeContext ctx) {
      ctx.getDistinctUpdate(ctx.getChange().currentPatchSetId())
          .setChangeMessage("Change message as distinct update");
      ctx.getDistinctUpdate(ctx.getChange().currentPatchSetId())
          .setChangeMessage("Another change message as distinct update");
      return true;
    }
  }

  private class PublishAllAddNewDraftOp implements BatchUpdateOp {
    @Override
    public boolean updateChange(ChangeContext ctx) {
      for (HumanComment comment : ctx.getNotes().getDraftComments(ctx.getAccountId()).values()) {
        ctx.getUpdate(ctx.getChange().currentPatchSetId()).putComment(Status.PUBLISHED, comment);
      }

      // Put some draft updates. Draft update should not be counted towards the batch size, those
      // are in All-Users repository.
      HumanComment newComment =
          commentsUtil.newHumanComment(
              ctx.getNotes(),
              ctx.getUser(),
              TimeUtil.nowTs(),
              Patch.PATCHSET_LEVEL,
              ctx.getChange().currentPatchSetId(),
              (short) 0,
              "Comment message",
              null,
              null);
      commentsUtil.setCommentCommitId(
          newComment, ctx.getChange(), ctx.getNotes().getCurrentPatchSet());
      ctx.getUpdate(ctx.getChange().currentPatchSetId())
          .putComment(HumanComment.Status.DRAFT, newComment);
      return true;
    }
  }

  private class AddRobotCommentOp implements BatchUpdateOp {
    @Override
    public boolean updateChange(ChangeContext ctx) {
      RobotComment robotComment =
          commentsUtil.newRobotComment(
              ctx,
              Patch.PATCHSET_LEVEL,
              ctx.getChange().currentPatchSetId(),
              (short) 0,
              "Comment",
              "robot",
              "1");
      commentsUtil.setCommentCommitId(
          robotComment, ctx.getChange(), ctx.getNotes().getCurrentPatchSet());
      ctx.getUpdate(ctx.getChange().currentPatchSetId()).putRobotComment(robotComment);
      return true;
    }
  }

  private static class RewriteChangeMessageOp implements BatchUpdateOp {
    @Override
    public boolean updateChange(ChangeContext ctx) throws IOException {
      ctx.getUpdate(ctx.getChange().currentPatchSetId())
          .deleteChangeMessageByRewritingHistory(
              Iterables.getLast(ctx.getNotes().getChangeMessages()).getKey().uuid(), "Deleted");
      return true;
    }
  }

  private static class RewriteCommentOp implements BatchUpdateOp {
    @Override
    public boolean updateChange(ChangeContext ctx) throws IOException {
      ctx.getUpdate(ctx.getChange().currentPatchSetId())
          .deleteCommentByRewritingHistory(
              Iterables.getLast(ctx.getNotes().getCommentKeys()).uuid, "Deleted");
      return true;
    }
  }

  private static class DeleteChangeOp implements BatchUpdateOp {
    @Override
    public boolean updateChange(ChangeContext ctx) throws IOException {
      ctx.deleteChange();
      return true;
    }
  }
}
