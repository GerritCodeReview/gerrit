package com.google.gerrit.server.update;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.junit.Test;

public class BatchOpsExecutorTest extends BatchUpdateTest {

  @Inject private BatchOpsExecutor.Factory batchOpsExecutorFactory;

  @Test
  public void batchUpdateSingleRefInBatch_canExecuteAfterClear() throws Exception {
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
        batchOpsExecutor.addRepoOnlyOp(new UpdateRepoOnlyRefs(masterCommit, ref));
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
        batchOpsExecutor.addRepoOnlyOp(new UpdateRepoOnlyRefs(masterCommit, ref));
      }
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(secondBatch.size());
      batchOpsExecutor.execute();

      assertExecutedWithAllOK(batchOpsExecutor, secondBatch, secondBatch.size(), masterCommit);
    }
  }

  @Test
  public void batchUpdateSingleRefInBatch_notCleared_executeFails() throws Exception {
    RevCommit masterCommit = repo.branch("master").commit().create();
    List<String> refs = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      refs.add("refs/heads/branch" + i);
    }
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      for (String ref : refs) {
        batchOpsExecutor.addRepoOnlyOp(new UpdateRepoOnlyRefs(masterCommit, ref));
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
                      new UpdateRepoOnlyRefs(ObjectId.zeroId(), "refs/heads/master")));
      assertThat(thrown)
          .hasMessageThat()
          .contains("update already executed, start new update or call clear()");
    }
  }

  @Test
  public void batchUpdateMultipleRefsInBatch_allCounted() throws Exception {
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
          new UpdateRepoOnlyRefs(
              masterCommit, refs.subList(0, refsInFirstOp).toArray(new String[] {})));
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refsInFirstOp);
      batchOpsExecutor.addRepoOnlyOp(
          new UpdateRepoOnlyRefs(
              masterCommit, refs.subList(refsInFirstOp, refs.size()).toArray(new String[] {})));
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
  public void batchUpdate_failedOp_notCounted() throws Exception {
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
        batchOpsExecutor.addRepoOnlyOp(new UpdateRepoOnlyRefs(masterCommit, refs.get(i)));
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
        batchOpsExecutor.addRepoOnlyOp(new UpdateRepoOnlyRefs(masterCommit, refs.get(i)));
      }
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(refs.size());
      batchOpsExecutor.execute();
      assertExecutedWithAllOK(batchOpsExecutor, refs, refs.size(), masterCommit);
    }
  }

  @Test
  public void batchUpdate_atomic_allRefsAborted() throws Exception {
    RevCommit masterCommit = repo.branch("master").commit().create();
    RevCommit branchCommit = repo.branch("branch").commit().parent(masterCommit).create();
    List<String> refs = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      refs.add("refs/heads/branch" + i);
    }
    try (BatchOpsExecutor batchOpsExecutor =
        batchOpsExecutorFactory.create(project, user.get(), TimeUtil.nowTs())) {
      for (String ref : refs) {
        batchOpsExecutor.addRepoOnlyOp(new UpdateRepoOnlyRefs(masterCommit, ref));
      }
      batchOpsExecutor.addRepoOnlyOp(new UpdateRepoOnlyRefs(branchCommit, "refs/heads/master"));
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
        batchOpsExecutor.addRepoOnlyOp(new UpdateRepoOnlyRefs(masterCommit, ref));
      }
      batchOpsExecutor.execute();
      assertExecutedWithAllOK(batchOpsExecutor, refs, refs.size(), masterCommit);
    }
  }

  @Test
  public void batchUpdate_nonAtomic_refsUpdated() throws Exception {
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
        batchOpsExecutor.addRepoOnlyOp(new UpdateRepoOnlyRefs(masterCommit, ref));
      }
      batchOpsExecutor.addRepoOnlyOp(new UpdateRepoOnlyRefs(branchCommit, "refs/heads/master"));
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
      batchOpsExecutor.addRepoOnlyOp(
          new UpdateRepoOnlyRefs(masterCommit, secondBatch.toArray(new String[] {})));
      assertThat(batchOpsExecutor.isExecuted()).isFalse();
      assertThat(batchOpsExecutor.refsInUpdate()).isEqualTo(secondBatch.size());
      batchOpsExecutor.execute();
      assertExecutedWithAllOK(batchOpsExecutor, secondBatch, secondBatch.size(), masterCommit);
    }
  }

  private void assertExecutedWithAllOK(
      BatchOpsExecutor batchOpsExecutor,
      List<String> updatedRefs,
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

  // non-atomic te
  // non-atomic test
  //
  /*
    @Test
    public void batchChangesUpdateWithBatchRefSize() throws Exception {
      int sizeOfBatch = 6;
      int numberOfChanges = 10;
      Map<Change.Id, ObjectId> changeIdsToMeta = new HashMap<>();
      for (int i = 0; i < numberOfChanges; i++) {
        Change.Id changeId = createChangeWithUpdates(1);
        changeIdsToMeta.put(changeId, getMetaId(changeId));
      }

      BatchUpdate bu = null;
      List<ImmutableMap<String, Result>> updateResults = new ArrayList<>();
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        if (bu == null) {
          bu = batchUpdateFactory.create(project, user.get(), TimeUtil.nowTs());
        }
        bu.addOp(changeId, new UpdateChangeOp());
        if (bu.refs >= sizeOfBatch) {
          bu.execute();
          updateResults.add(
              bu.getRefUpdates().entrySet().stream()
                  .collect(
                      ImmutableMap.toImmutableMap(x -> x.getKey(), x -> x.getValue().getResult())));
          bu.close();
          bu = null;
        }
      }
      if (bu != null) {
        bu.execute();
        updateResults.add(
            bu.getRefUpdates().entrySet().stream()
                .collect(
                    ImmutableMap.toImmutableMap(x -> x.getKey(), x -> x.getValue().getResult())));
        bu.close();
        bu = null;
      }
      assertThat(updateResults).hasSize(2);
      assertThat(updateResults.get(0).values()).hasSize(6);
      assertThat(updateResults.get(1).values()).hasSize(4);
      for (Map.Entry<Change.Id, ObjectId> changeIdAndOldMeta : changeIdsToMeta.entrySet()) {
        // all changes were updated
        assertThat(getMetaId(changeIdAndOldMeta.getKey()))
            .isNotEqualTo(changeIdAndOldMeta.getValue());
      }
    }

    @Test
    public void batchUpdateWithBatchRefSizeWithFailure() throws Exception {
      RevCommit masterCommit = repo.branch("master").commit().create();
      int sizeOfBatch = 6;
      int numberOfChanges = 10;
      Map<Change.Id, ObjectId> changeIdsToMeta = new HashMap<>();
      for (int i = 0; i < numberOfChanges; i++) {
        Change.Id changeId = createChangeWithUpdates(1);
        changeIdsToMeta.put(changeId, getMetaId(changeId));
      }

      BatchUpdate bu = null;
      List<ImmutableMap<String, Result>> updateResults = new ArrayList<>();
      List<ImmutableMap<String, String>> updateMessages = new ArrayList<>();
      for (Change.Id changeId : changeIdsToMeta.keySet()) {
        if (bu == null) {
          bu = batchUpdateFactory.create(project, user.get(), TimeUtil.nowTs());
        }
        bu.addRepoOnlyOp(new UpdateRepoOnly(RefNames.changeMetaRef(changeId), masterCommit.getId()));
        if (bu.refsInUpdate() >= sizeOfBatch) {
          try {
            bu.execute();
          } catch (Exception e) {
            // log
          }
          updateResults.add(
              bu.getRefUpdates().entrySet().stream()
                  .collect(
                      ImmutableMap.toImmutableMap(x -> x.getKey(), x -> x.getValue().getResult())));
          updateMessages.add(
              bu.getRefUpdates().entrySet().stream()
                  .collect(
                      ImmutableMap.toImmutableMap(
                          x -> x.getKey(),
                          x ->
                              x.getValue().getMessage() != null
                                  ? x.getValue().getMessage()
                                  : "empty")));
          bu.close();
          bu = null;
        }
      }
      if (bu != null) {
        try {
          bu.execute();
        } catch (Exception e) {
          // log
        }
        updateResults.add(
            bu.getRefUpdates().entrySet().stream()
                .collect(
                    ImmutableMap.toImmutableMap(x -> x.getKey(), x -> x.getValue().getResult())));
        bu.close();
        bu = null;
      }
      ImmutableMap.Builder<String, Result> expectedResults = ImmutableMap.builder();
      for (Map.Entry<Change.Id, ObjectId> changeIdAndOldMeta : changeIdsToMeta.entrySet()) {
        expectedResults.put(RefNames.changeMetaRef(changeIdAndOldMeta.getKey()), Result.OK);
      }
      assertThat(updateResults).hasSize(2);
      assertThat(updateResults.get(0).values()).hasSize(6);
      assertThat(updateResults.get(1).values()).hasSize(4);
      assertThat(updateMessages).isEmpty();
      assertThat(updateResults.get(0).values()).isEqualTo(expectedResults);
      for (Map.Entry<Change.Id, ObjectId> changeIdAndOldMeta : changeIdsToMeta.entrySet()) {
        // all changes were updated
        assertThat(repo.getRepository().exactRef(RefNames.changeMetaRef(changeIdAndOldMeta.getKey())))
            .isEqualTo(null);
      }
    }
  */
  private static class UpdateRepoOnly implements RepoOnlyOp {
    private final String ref;

    private final ObjectId oldTip;

    UpdateRepoOnly(String ref, ObjectId oldTip) {
      this.ref = ref;
      this.oldTip = oldTip;
    }

    @Override
    public void updateRepo(RepoContext ctx) throws IOException {
      ctx.addRefUpdate(oldTip, ObjectId.zeroId(), ref);
    }
  }

  private static class ThrowingUpdateRepoOnly implements RepoOnlyOp {

    @Override
    public void updateRepo(RepoContext ctx) throws IOException {
      throw new IllegalStateException("Alwas throwing update");
    }
  }

  private static class UpdateRepoOnlyRefs implements RepoOnlyOp {
    private final String[] refs;

    private final ObjectId tip;

    UpdateRepoOnlyRefs(ObjectId tip, String... refs) {
      this.refs = refs;
      this.tip = tip;
    }

    @Override
    public void updateRepo(RepoContext ctx) throws IOException {
      for (String ref : refs) {
        ctx.addRefUpdate(ObjectId.zeroId(), tip, ref);
      }
    }
  }

  private static class UpdateChangeOp implements BatchUpdateOp {
    @Override
    public boolean updateChange(ChangeContext ctx) throws IOException {
      ctx.getUpdate(ctx.getChange().currentPatchSetId())
          .setChangeMessage("First change message in update");
      ctx.getDistinctUpdate(ctx.getChange().currentPatchSetId())
          .setChangeMessage("Second change message in update");
      return true;
    }
  }
}
