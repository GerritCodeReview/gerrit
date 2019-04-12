// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.notedb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.extensions.client.ListChangesOption.MESSAGES;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateListener;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

public class NoteDbOnlyIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    // Avoid spurious timeouts during intentional retries due to overloaded test machines.
    cfg.setString("retry", null, "timeout", Integer.MAX_VALUE + "s");
    return cfg;
  }

  @Inject private RetryHelper retryHelper;

  @Test
  public void updateChangeFailureRollsBackRefUpdate() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();

    String master = "refs/heads/master";
    String backup = "refs/backup/master";
    ObjectId master1 = getRef(master).get();
    assertThat(getRef(backup)).isEmpty();

    // Toy op that copies the value of refs/heads/master to refs/backup/master.
    BatchUpdateOp backupMasterOp =
        new BatchUpdateOp() {
          ObjectId newId;

          @Override
          public void updateRepo(RepoContext ctx) throws IOException {
            ObjectId oldId = ctx.getRepoView().getRef(backup).orElse(ObjectId.zeroId());
            newId = ctx.getRepoView().getRef(master).get();
            ctx.addRefUpdate(oldId, newId, backup);
          }

          @Override
          public boolean updateChange(ChangeContext ctx) {
            ctx.getUpdate(ctx.getChange().currentPatchSetId())
                .setChangeMessage("Backed up master branch to " + newId.name());
            return true;
          }
        };

    try (BatchUpdate bu = newBatchUpdate(batchUpdateFactory)) {
      bu.addOp(id, backupMasterOp);
      bu.execute();
    }

    // Ensure backupMasterOp worked.
    assertThat(getRef(backup)).hasValue(master1);
    assertThat(getMessages(id)).contains("Backed up master branch to " + master1.name());

    // Advance master by submitting the change.
    gApi.changes().id(id.get()).current().review(ReviewInput.approve());
    gApi.changes().id(id.get()).current().submit();
    ObjectId master2 = getRef(master).get();
    assertThat(master2).isNotEqualTo(master1);
    int msgCount = getMessages(id).size();

    try (BatchUpdate bu = newBatchUpdate(batchUpdateFactory)) {
      // This time, we attempt to back up master, but we fail during updateChange.
      bu.addOp(id, backupMasterOp);
      String msg = "Change is bad";
      bu.addOp(
          id,
          new BatchUpdateOp() {
            @Override
            public boolean updateChange(ChangeContext ctx) throws ResourceConflictException {
              throw new ResourceConflictException(msg);
            }
          });
      try {
        bu.execute();
        fail("expected ResourceConflictException");
      } catch (ResourceConflictException e) {
        assertThat(e).hasMessageThat().isEqualTo(msg);
      }
    }

    // If updateChange hadn't failed, backup would have been updated to master2.
    assertThat(getRef(backup)).hasValue(master1);
    assertThat(getMessages(id)).hasSize(msgCount);
  }

  @Test
  public void retryOnLockFailureWithAtomicUpdates() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();
    String master = "refs/heads/master";
    ObjectId initial;
    try (Repository repo = repoManager.openRepository(project)) {
      ensureAtomicTransactions(repo);
      initial = repo.exactRef(master).getObjectId();
    }

    AtomicInteger updateRepoCalledCount = new AtomicInteger();
    AtomicInteger updateChangeCalledCount = new AtomicInteger();
    AtomicInteger afterUpdateReposCalledCount = new AtomicInteger();

    String result =
        retryHelper.execute(
            batchUpdateFactory -> {
              try (BatchUpdate bu = newBatchUpdate(batchUpdateFactory)) {
                bu.addOp(
                    id,
                    new UpdateRefAndAddMessageOp(updateRepoCalledCount, updateChangeCalledCount));
                bu.execute(new ConcurrentWritingListener(afterUpdateReposCalledCount));
              }
              return "Done";
            });

    assertThat(result).isEqualTo("Done");
    assertThat(updateRepoCalledCount.get()).isEqualTo(2);
    assertThat(afterUpdateReposCalledCount.get()).isEqualTo(2);
    assertThat(updateChangeCalledCount.get()).isEqualTo(2);

    List<String> messages = getMessages(id);
    assertThat(Iterables.getLast(messages)).isEqualTo(UpdateRefAndAddMessageOp.CHANGE_MESSAGE);
    assertThat(Collections.frequency(messages, UpdateRefAndAddMessageOp.CHANGE_MESSAGE))
        .isEqualTo(1);

    try (Repository repo = repoManager.openRepository(project)) {
      // Op lost the race, so the other writer's commit happened first. Then op retried and wrote
      // its commit with the other writer's commit as parent.
      assertThat(commitMessages(repo, initial, repo.exactRef(master).getObjectId()))
          .containsExactly(
              ConcurrentWritingListener.MSG_PREFIX + "1", UpdateRefAndAddMessageOp.COMMIT_MESSAGE)
          .inOrder();
    }
  }

  @Test
  public void missingChange() throws Exception {
    Change.Id changeId = new Change.Id(1234567);
    assertNoSuchChangeException(() -> notesFactory.create(project, changeId));
    assertNoSuchChangeException(() -> notesFactory.createChecked(project, changeId));
  }

  private void assertNoSuchChangeException(Callable<?> callable) throws Exception {
    try {
      callable.call();
      fail("expected NoSuchChangeException");
    } catch (NoSuchChangeException e) {
      // Expected.
    }
  }

  private class ConcurrentWritingListener implements BatchUpdateListener {
    static final String MSG_PREFIX = "Other writer ";

    private final AtomicInteger calledCount;

    private ConcurrentWritingListener(AtomicInteger calledCount) {
      this.calledCount = calledCount;
    }

    @Override
    public void afterUpdateRepos() throws Exception {
      // Reopen repo and update ref, to simulate a concurrent write in another
      // thread. Only do this the first time the listener is called.
      if (calledCount.getAndIncrement() > 0) {
        return;
      }
      try (Repository repo = repoManager.openRepository(project);
          RevWalk rw = new RevWalk(repo);
          ObjectInserter ins = repo.newObjectInserter()) {
        String master = "refs/heads/master";
        ObjectId oldId = repo.exactRef(master).getObjectId();
        ObjectId newId = newCommit(rw, ins, oldId, MSG_PREFIX + calledCount.get());
        ins.flush();
        RefUpdate ru = repo.updateRef(master);
        ru.setExpectedOldObjectId(oldId);
        ru.setNewObjectId(newId);
        assertThat(ru.update(rw)).isEqualTo(RefUpdate.Result.FAST_FORWARD);
      }
    }
  }

  private class UpdateRefAndAddMessageOp implements BatchUpdateOp {
    static final String COMMIT_MESSAGE = "A commit";
    static final String CHANGE_MESSAGE = "A change message";

    private final AtomicInteger updateRepoCalledCount;
    private final AtomicInteger updateChangeCalledCount;

    private UpdateRefAndAddMessageOp(
        AtomicInteger updateRepoCalledCount, AtomicInteger updateChangeCalledCount) {
      this.updateRepoCalledCount = updateRepoCalledCount;
      this.updateChangeCalledCount = updateChangeCalledCount;
    }

    @Override
    public void updateRepo(RepoContext ctx) throws Exception {
      String master = "refs/heads/master";
      ObjectId oldId = ctx.getRepoView().getRef(master).get();
      ObjectId newId = newCommit(ctx.getRevWalk(), ctx.getInserter(), oldId, COMMIT_MESSAGE);
      ctx.addRefUpdate(oldId, newId, master);
      updateRepoCalledCount.incrementAndGet();
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws Exception {
      ctx.getUpdate(ctx.getChange().currentPatchSetId()).setChangeMessage(CHANGE_MESSAGE);
      updateChangeCalledCount.incrementAndGet();
      return true;
    }
  }

  private ObjectId newCommit(RevWalk rw, ObjectInserter ins, ObjectId parent, String msg)
      throws IOException {
    PersonIdent ident = serverIdent.get();
    CommitBuilder cb = new CommitBuilder();
    cb.setParentId(parent);
    cb.setTreeId(rw.parseCommit(parent).getTree());
    cb.setMessage(msg);
    cb.setAuthor(ident);
    cb.setCommitter(ident);
    return ins.insert(Constants.OBJ_COMMIT, cb.build());
  }

  private BatchUpdate newBatchUpdate(BatchUpdate.Factory buf) {
    return buf.create(project, identifiedUserFactory.create(user.id()), TimeUtil.nowTs());
  }

  private Optional<ObjectId> getRef(String name) throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      return Optional.ofNullable(repo.exactRef(name)).map(Ref::getObjectId);
    }
  }

  private List<String> getMessages(Change.Id id) throws Exception {
    return gApi.changes().id(id.get()).get(MESSAGES).messages.stream()
        .map(m -> m.message)
        .collect(toList());
  }

  private static List<String> commitMessages(
      Repository repo, ObjectId fromExclusive, ObjectId toInclusive) throws Exception {
    try (RevWalk rw = new RevWalk(repo)) {
      rw.markStart(rw.parseCommit(toInclusive));
      rw.markUninteresting(rw.parseCommit(fromExclusive));
      rw.sort(RevSort.REVERSE);
      rw.setRetainBody(true);
      return Streams.stream(rw).map(RevCommit::getShortMessage).collect(toList());
    }
  }

  private void ensureAtomicTransactions(Repository repo) throws Exception {
    if (repo instanceof InMemoryRepository) {
      ((InMemoryRepository) repo).setPerformsAtomicTransactions(true);
    } else {
      assertThat(repo.getRefDatabase().performsAtomicTransactions())
          .named("performsAtomicTransactions on %s", repo)
          .isTrue();
    }
  }
}
