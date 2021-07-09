package com.google.gerrit.server.update;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment.Status;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.RobotComment;
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
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

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
        batchOpsExecutor.addRepoOnlyOp(new CreateRefsRepoOnlyOp(ref, masterCommit));
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
        batchOpsExecutor.addRepoOnlyOp(new CreateRefsRepoOnlyOp(ref, masterCommit));
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
        batchOpsExecutor.addRepoOnlyOp(new CreateRefsRepoOnlyOp(ref, masterCommit));
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refs.size());
      batchOpsExecutor.execute();
      assertExecutedWithAllOK(batchOpsExecutor, refs, refs.size(), masterCommit);
      IllegalStateException thrown =
          assertThrows(
              IllegalStateException.class,
              () ->
                  batchOpsExecutor.addRepoOnlyOp(
                      new CreateRefsRepoOnlyOp("refs/heads/master", ObjectId.zeroId())));
      assertThat(thrown)
          .hasMessageThat()
          .contains("update already executed, start new update or call clear()");
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
      batchOpsExecutor.addRepoOnlyOp(
          new CreateRefsRepoOnlyOp(refs.subList(0, refsInFirstOp), masterCommit));
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refsInFirstOp);
      batchOpsExecutor.addRepoOnlyOp(
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
        batchOpsExecutor.addRepoOnlyOp(new CreateRefsRepoOnlyOp(refs.get(i), masterCommit));
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refsPreFail);
      assertThrows(
          IllegalStateException.class,
          () -> batchOpsExecutor.addRepoOnlyOp(new ThrowingUpdateRepoOnly()));
      // the caller is free to either retry this update or ignore and keep adding to the batch, only
      // added updates are counted
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refsPreFail);
      for (int i = refsPreFail; i < refs.size(); i++) {
        batchOpsExecutor.addRepoOnlyOp(new CreateRefsRepoOnlyOp(refs.get(i), masterCommit));
      }
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refs.size());
      batchOpsExecutor.execute();
      assertExecutedWithAllOK(batchOpsExecutor, refs, refs.size(), masterCommit);
    }
  }

  @Test
  public void repoOnlyOpBatch_atomic_allRefsAborted() throws Exception {
    RevCommit masterCommit = repo.branch("master").commit().create();
    RevCommit branchCommit = repo.branch("branch").commit().parent(masterCommit).create();
    List<String> refs = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      refs.add("refs/heads/branch" + i);
    }
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      for (String ref : refs) {
        batchOpsExecutor.addRepoOnlyOp(new CreateRefsRepoOnlyOp(ref, masterCommit));
      }
      batchOpsExecutor.addRepoOnlyOp(new CreateRefsRepoOnlyOp("refs/heads/master", branchCommit));
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refs.size() + 1);
      UpdateException thrown =
          assertThrows(UpdateException.class, () -> batchOpsExecutor.execute());
      assertThat(thrown.getMessage()).contains("Update aborted with one or more lock failures");
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
        batchOpsExecutor.addRepoOnlyOp(new CreateRefsRepoOnlyOp(ref, masterCommit));
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
        batchOpsExecutor.addRepoOnlyOp(new CreateRefsRepoOnlyOp(ref, masterCommit));
      }
      batchOpsExecutor.addRepoOnlyOp(new CreateRefsRepoOnlyOp("refs/heads/master", branchCommit));
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(firstBatch.size() + 1);
      UpdateException thrown =
          assertThrows(UpdateException.class, () -> batchOpsExecutor.execute());
      assertThat(thrown.getMessage()).contains("Update failed");
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
      batchOpsExecutor.addRepoOnlyOp(new CreateRefsRepoOnlyOp(secondBatch, masterCommit));
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
      batchOpsExecutor.addRepoOnlyOp(new CreateRefsRepoOnlyOp(refs, masterCommit));
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refs.size());
      IllegalArgumentException thrown =
          assertThrows(IllegalArgumentException.class, () -> batchOpsExecutor.execute());
      assertThat(thrown.getMessage())
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
        batchOpsExecutor.addRepoOnlyOp(new CreateRefsRepoOnlyOp(ref, masterCommit));
      }
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refs.size());
      thrown = assertThrows(IllegalArgumentException.class, () -> batchOpsExecutor.execute());
      assertThat(thrown.getMessage())
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
  public void changeUpdateOpBatch_repoOnlyWithChangeUpdate_allCounted() throws Exception {
    RevCommit masterCommit = repo.branch("master").commit().create();
    Map<Change.Id, ObjectId> changeIdsToMeta = new HashMap<>();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChangeWithUpdates();
      changeIdsToMeta.put(changeId, getMetaId(changeId));
    }
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        batchOpsExecutor.addOp(
            changeId,
            new PostChangeMessageUpdateOp(
                "refs/heads/change-branch" + changeId.get(), masterCommit));
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(2 * changeIdsToMeta.size());
      batchOpsExecutor.execute();
      assertThat(batchOpsExecutor.isExecuted()).isTrue();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(2 * changeIdsToMeta.size());
      Map<String, ReceiveCommand> refUpdates = batchOpsExecutor.getRefUpdates();
      assertThat(refUpdates).hasSize(2 * changeIdsToMeta.size());
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        String repoOnlyUpdateRef = "refs/heads/change-branch" + changeId.get();
        ReceiveCommand cmd = refUpdates.get(repoOnlyUpdateRef);
        assertThat(cmd).isNotNull();
        assertThat(cmd.getResult()).isEqualTo(Result.OK);
        assertThat(repo.getRepository().exactRef(repoOnlyUpdateRef).getObjectId())
            .isEqualTo(masterCommit);
      }
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        String metaRef = RefNames.changeMetaRef(changeId);
        ReceiveCommand cmd = refUpdates.get(metaRef);
        assertThat(cmd).isNotNull();
        assertThat(cmd.getResult()).isEqualTo(Result.OK);
        assertThat(repo.getRepository().exactRef(metaRef).getObjectId())
            .isNotEqualTo(changeIdsToMeta.get(changeId));
      }
      batchOpsExecutor.clear();
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(0);
      assertThat(batchOpsExecutor.getRefUpdates()).isEmpty();
    }
  }

  @Test
  public void changeUpdateOpBatch_robotCommentUpdate_allCounted() throws Exception {
    RevCommit masterCommit = repo.branch("master").commit().create();
    Map<Change.Id, ObjectId> changeIdsToMeta = new HashMap<>();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChangeWithUpdates();
      changeIdsToMeta.put(changeId, getMetaId(changeId));
    }
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        batchOpsExecutor.addOp(changeId, new AddRobotCommentOp());
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(changeIdsToMeta.size());
      batchOpsExecutor.execute();
      assertThat(batchOpsExecutor.isExecuted()).isTrue();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(changeIdsToMeta.size());
      Map<String, ReceiveCommand> refUpdates = batchOpsExecutor.getRefUpdates();
      assertThat(refUpdates).hasSize(changeIdsToMeta.size());
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        String robotCommentRef = RefNames.robotCommentsRef(changeId);
        ReceiveCommand cmd = refUpdates.get(robotCommentRef);
        assertThat(cmd).isNotNull();
        assertThat(cmd.getResult()).isEqualTo(Result.OK);
        assertThat(repo.getRepository().exactRef(robotCommentRef)).isNotNull();
      }
      batchOpsExecutor.clear();
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(0);
      assertThat(batchOpsExecutor.getRefUpdates()).isEmpty();
    }
  }

  @Test
  public void changeUpdateOpBatch_draftCommentUpdate_notCounted() throws Exception {
    RevCommit masterCommit = repo.branch("master").commit().create();
    Map<Change.Id, ObjectId> changeIdsToMeta = new HashMap<>();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChangeWithUpdates();
      changeIdsToMeta.put(changeId, getMetaId(changeId));
    }
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        batchOpsExecutor.addOp(changeId, new PublishAllAddNewDraftOp());
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(changeIdsToMeta.size());
      batchOpsExecutor.execute();
      assertThat(batchOpsExecutor.isExecuted()).isTrue();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(changeIdsToMeta.size());
      Map<String, ReceiveCommand> refUpdates = batchOpsExecutor.getRefUpdates();
      assertThat(refUpdates).hasSize(changeIdsToMeta.size());
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        String metaRef = RefNames.changeMetaRef(changeId);
        ReceiveCommand cmd = refUpdates.get(metaRef);
        assertThat(cmd).isNotNull();
        assertThat(cmd.getResult()).isEqualTo(Result.OK);
        assertThat(repo.getRepository().exactRef(metaRef).getObjectId())
            .isNotEqualTo(changeIdsToMeta.get(changeId));
      }
      batchOpsExecutor.clear();
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(0);
      assertThat(batchOpsExecutor.getRefUpdates()).isEmpty();
    }
  }

  @Test
  public void changeUpdateOpBatch_deleteChange_counted() throws Exception {
    Map<Change.Id, ObjectId> changeIdsToMeta = new HashMap<>();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChangeWithUpdates();
      changeIdsToMeta.put(changeId, getMetaId(changeId));
    }
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        batchOpsExecutor.addOp(changeId, new DeleteChangeOp());
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(changeIdsToMeta.size());
      batchOpsExecutor.execute();
      assertThat(batchOpsExecutor.isExecuted()).isTrue();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(changeIdsToMeta.size());
      Map<String, ReceiveCommand> refUpdates = batchOpsExecutor.getRefUpdates();
      assertThat(refUpdates).hasSize(changeIdsToMeta.size());
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        String metaRef = RefNames.changeMetaRef(changeId);
        ReceiveCommand cmd = refUpdates.get(metaRef);
        assertThat(cmd).isNotNull();
        assertThat(cmd.getResult()).isEqualTo(Result.OK);
        assertThat(repo.getRepository().exactRef(metaRef)).isNull();
      }
      batchOpsExecutor.clear();
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(0);
      assertThat(batchOpsExecutor.getRefUpdates()).isEmpty();
    }
  }

  @Test
  public void changeUpdateOpBatch_nonAtomic_forChangeRepoAndAllUsers_fails() throws Exception {
    Map<Change.Id, ObjectId> changeIdsToMeta = new HashMap<>();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChangeWithUpdates();
      changeIdsToMeta.put(changeId, getMetaId(changeId));
    }
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(
            project, user.get(), TimeUtil.nowTs(), /*nonAtomic=*/ true)) {
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        batchOpsExecutor.addOp(changeId, new DeleteChangeOp());
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(changeIdsToMeta.size());
      batchOpsExecutor.execute();
      assertThat(batchOpsExecutor.isExecuted()).isTrue();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(changeIdsToMeta.size());
      Map<String, ReceiveCommand> refUpdates = batchOpsExecutor.getRefUpdates();
      assertThat(refUpdates).hasSize(changeIdsToMeta.size());
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        String metaRef = RefNames.changeMetaRef(changeId);
        ReceiveCommand cmd = refUpdates.get(metaRef);
        assertThat(cmd).isNotNull();
        assertThat(cmd.getResult()).isEqualTo(Result.OK);
        assertThat(repo.getRepository().exactRef(metaRef)).isNull();
      }
      batchOpsExecutor.clear();
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(0);
      assertThat(batchOpsExecutor.getRefUpdates()).isEmpty();
    }
  }

  @Test
  public void changeUpdateOpBatch_nonAtomic_forAllUsers_fails() throws Exception {
    Map<Change.Id, ObjectId> changeIdsToMeta = new HashMap<>();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChangeWithUpdates();
      changeIdsToMeta.put(changeId, getMetaId(changeId));
    }
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(
            project, user.get(), TimeUtil.nowTs(), /*nonAtomic=*/ true)) {
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        batchOpsExecutor.addOp(changeId, new PublishAllDraftsOp());
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(changeIdsToMeta.size());
      batchOpsExecutor.execute();
      assertThat(batchOpsExecutor.isExecuted()).isTrue();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(changeIdsToMeta.size());
      Map<String, ReceiveCommand> refUpdates = batchOpsExecutor.getRefUpdates();
      assertThat(refUpdates).hasSize(changeIdsToMeta.size());
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        String metaRef = RefNames.changeMetaRef(changeId);
        ReceiveCommand cmd = refUpdates.get(metaRef);
        assertThat(cmd).isNotNull();
        assertThat(cmd.getResult()).isEqualTo(Result.OK);
        assertThat(repo.getRepository().exactRef(metaRef)).isNull();
      }
      batchOpsExecutor.clear();
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(0);
      assertThat(batchOpsExecutor.getRefUpdates()).isEmpty();
    }
  }

  @Test
  public void changeUpdateOpBatch_rewriteCommitMessage_counted() throws Exception {
    Map<Change.Id, ObjectId> changeIdsToMeta = new HashMap<>();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChangeWithUpdates();
      changeIdsToMeta.put(changeId, getMetaId(changeId));
    }
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        batchOpsExecutor.addOp(changeId, new RewriteChangeMessageOp());
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(changeIdsToMeta.size());
      batchOpsExecutor.execute();
      assertThat(batchOpsExecutor.isExecuted()).isTrue();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(changeIdsToMeta.size());
      Map<String, ReceiveCommand> refUpdates = batchOpsExecutor.getRefUpdates();
      assertThat(refUpdates).hasSize(changeIdsToMeta.size());
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        String metaRef = RefNames.changeMetaRef(changeId);
        ReceiveCommand cmd = refUpdates.get(metaRef);
        assertThat(cmd).isNotNull();
        assertThat(cmd.getResult()).isEqualTo(Result.OK);
        assertThat(repo.getRepository().exactRef(metaRef).getObjectId())
            .isNotEqualTo(changeIdsToMeta.get(changeId));
      }
      batchOpsExecutor.clear();
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(0);
      assertThat(batchOpsExecutor.getRefUpdates()).isEmpty();
    }
  }

  @Test
  public void changeUpdateOpBatch_rewriteComment_counted() throws Exception {
    Map<Change.Id, ObjectId> changeIdsToMeta = new HashMap<>();
    for (int i = 0; i < 10; i++) {
      Change.Id changeId = createChangeWithUpdates();
      changeIdsToMeta.put(changeId, getMetaId(changeId));
    }
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        batchOpsExecutor.addOp(changeId, new RewriteCommentOp());
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(changeIdsToMeta.size());
      batchOpsExecutor.execute();
      assertThat(batchOpsExecutor.isExecuted()).isTrue();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(changeIdsToMeta.size());
      Map<String, ReceiveCommand> refUpdates = batchOpsExecutor.getRefUpdates();
      assertThat(refUpdates).hasSize(changeIdsToMeta.size());
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        String metaRef = RefNames.changeMetaRef(changeId);
        ReceiveCommand cmd = refUpdates.get(metaRef);
        assertThat(cmd).isNotNull();
        assertThat(cmd.getResult()).isEqualTo(Result.OK);
        assertThat(repo.getRepository().exactRef(metaRef).getObjectId())
            .isNotEqualTo(changeIdsToMeta.get(changeId));
      }
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

  private void assertChangeUpdatesExecutedWithAllOK(
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

  protected Change.Id createChangeWithUpdates() throws Exception {
    Change.Id id = Change.id(sequences.nextChangeId());
    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.nowTs())) {
      bu.insertChange(
          changeInserterFactory.create(
              id, repo.commit().message("Change").insertChangeId().create(), "refs/heads/master"));
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

  private ObjectId getMetaId(Change.Id changeId) throws Exception {
    return repo.getRepository().exactRef(RefNames.changeMetaRef(changeId)).getObjectId();
  }

  private static class ThrowingUpdateRepoOnly implements RepoOnlyOp {

    @Override
    public void updateRepo(RepoContext ctx) throws IOException {
      throw new IllegalStateException("Alwas throwing update");
    }
  }

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
    public void updateRepo(RepoContext ctx) throws IOException {
      for (String ref : refs) {
        ctx.addRefUpdate(ObjectId.zeroId(), tip, ref);
      }
    }
  }

  private static class PostChangeMessageUpdateOp extends CreateRefsRepoOnlyOp
      implements BatchUpdateOp {

    PostChangeMessageUpdateOp(List<String> refs, ObjectId tip) {
      super(refs, tip);
    }

    PostChangeMessageUpdateOp(String ref, ObjectId tip) {
      this(ImmutableList.of(ref), tip);
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws IOException, AccountException {
      ctx.getUpdate(ctx.getChange().currentPatchSetId())
          .setChangeMessage("First change message in update");
      ctx.getDistinctUpdate(ctx.getChange().currentPatchSetId())
          .setChangeMessage("Second change message in update");

      return true;
    }
  }

  private static class PostChangeMessageAsDistinctUpdateOp implements BatchUpdateOp {
    @Override
    public boolean updateChange(ChangeContext ctx) throws IOException, AccountException {
      ctx.getDistinctUpdate(ctx.getChange().currentPatchSetId())
          .setChangeMessage("Change message as distinct update");
      return true;
    }
  }

  private class PublishAllAddNewDraftOp implements BatchUpdateOp {
    @Override
    public boolean updateChange(ChangeContext ctx) throws IOException, AccountException {
      for (HumanComment comment : ctx.getNotes().getDraftComments(ctx.getAccountId()).values()) {
        ctx.getUpdate(ctx.getChange().currentPatchSetId()).putComment(Status.PUBLISHED, comment);
      }

      // Put some draft updates. Draft update should not be counted, this is in All-Users repository
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

  private class PublishAllDraftsOp implements BatchUpdateOp {
    @Override
    public boolean updateChange(ChangeContext ctx) throws IOException, AccountException {
      for (HumanComment comment : ctx.getNotes().getDraftComments(admin).values()) {
        ctx.getUpdate(ctx.getChange().currentPatchSetId()).putComment(Status.PUBLISHED, comment);
      }
      for (HumanComment comment : ctx.getNotes().getDraftComments(otherUser).values()) {
        ctx.getUpdate(ctx.getChange().currentPatchSetId()).putComment(Status.PUBLISHED, comment);
      }
      return true;
    }
  }

  private class AddRobotCommentOp implements BatchUpdateOp {
    @Override
    public boolean updateChange(ChangeContext ctx) throws IOException, AccountException {
      // Robot comments
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
