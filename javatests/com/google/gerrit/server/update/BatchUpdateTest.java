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

package com.google.gerrit.server.update;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmissionId;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.events.AttentionSetListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.change.AbandonOp;
import com.google.gerrit.server.change.AddReviewersOp;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.patch.DiffSummary;
import com.google.gerrit.server.patch.DiffSummaryKey;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.InMemoryTestEnvironment;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class BatchUpdateTest {
  private static final int MAX_UPDATES = 4;
  private static final int MAX_PATCH_SETS = 3;

  @Rule
  public InMemoryTestEnvironment testEnvironment =
      new InMemoryTestEnvironment(
          () -> {
            Config cfg = new Config();
            cfg.setInt("change", null, "maxFiles", 2);
            cfg.setInt("change", null, "maxPatchSets", MAX_PATCH_SETS);
            cfg.setInt("change", null, "maxUpdates", MAX_UPDATES);
            cfg.setString("index", null, "type", "fake");
            return cfg;
          });

  @Inject private BatchUpdate.Factory batchUpdateFactory;
  @Inject private ChangeInserter.Factory changeInserterFactory;
  @Inject private ChangeNotes.Factory changeNotesFactory;
  @Inject private GitRepositoryManager repoManager;
  @Inject private PatchSetInserter.Factory patchSetInserterFactory;
  @Inject private Provider<CurrentUser> user;
  @Inject private Sequences sequences;
  @Inject private AddReviewersOp.Factory addReviewersOpFactory;
  @Inject private DynamicSet<AttentionSetListener> attentionSetListeners;
  @Inject private AccountManager accountManager;
  @Inject private AuthRequest.Factory authRequestFactory;
  @Inject private InternalUser.Factory internalUserFactory;
  @Inject private AbandonOp.Factory abandonOpFactory;

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Captor ArgumentCaptor<AttentionSetListener.Event> attentionSetEventCaptor;
  @Mock private AttentionSetListener attentionSetListener;

  @Inject
  private @Named("diff_summary") Cache<DiffSummaryKey, DiffSummary> diffSummaryCache;

  private Project.NameKey project;
  private TestRepository<Repository> repo;

  @Before
  public void setUp() throws Exception {
    project = Project.nameKey("test");

    Repository inMemoryRepo = repoManager.createRepository(project);
    repo = new TestRepository<>(inMemoryRepo);
  }

  @Test
  public void addRefUpdateFromFastForwardCommit() throws Exception {
    RevCommit masterCommit = repo.branch("master").commit().create();
    RevCommit branchCommit = repo.branch("branch").commit().parent(masterCommit).create();

    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
      bu.addRepoOnlyOp(
          new RepoOnlyOp() {
            @Override
            public void updateRepo(RepoContext ctx) throws Exception {
              ctx.addRefUpdate(masterCommit.getId(), branchCommit.getId(), "refs/heads/master");
            }
          });
      bu.execute();
    }

    assertThat(repo.getRepository().exactRef("refs/heads/master").getObjectId())
        .isEqualTo(branchCommit.getId());
  }

  @Test
  public void batchUpdateThatChangeAttentionSetAsInternalUser() throws Exception {
    Change.Id id = createChangeWithUpdates(1);
    attentionSetListeners.add("test", attentionSetListener);

    Account.Id reviewer =
        accountManager.authenticate(authRequestFactory.createForUser("user")).getAccountId();

    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
      bu.addOp(
          id,
          addReviewersOpFactory.create(
              ImmutableSet.of(reviewer), ImmutableList.of(), ReviewerState.REVIEWER, false));
      bu.execute();
    }

    try (BatchUpdate bu =
        batchUpdateFactory.create(project, internalUserFactory.create(), TimeUtil.now())) {
      bu.addOp(id, abandonOpFactory.create(null, "test abandon"));
      bu.execute();
    }

    verify(attentionSetListener, times(2)).onAttentionSetChanged(attentionSetEventCaptor.capture());
    AttentionSetListener.Event event = attentionSetEventCaptor.getAllValues().get(0);
    assertThat(event.getChange()._number).isEqualTo(id.get());
    assertThat(event.usersAdded()).containsExactly(reviewer.get());
    assertThat(event.usersRemoved()).isEmpty();

    event = attentionSetEventCaptor.getAllValues().get(1);
    assertThat(event.getChange()._number).isEqualTo(id.get());
    assertThat(event.usersAdded()).isEmpty();
    assertThat(event.usersRemoved()).containsExactly(reviewer.get());
  }

  @Test
  public void cannotExceedMaxUpdates() throws Exception {
    Change.Id id = createChangeWithUpdates(MAX_UPDATES);
    ObjectId oldMetaId = getMetaId(id);
    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
      bu.addOp(id, new AddMessageOp("Excessive update"));
      ResourceConflictException thrown = assertThrows(ResourceConflictException.class, bu::execute);
      assertThat(thrown)
          .hasMessageThat()
          .contains("Change " + id + " may not exceed " + MAX_UPDATES);
    }
    assertThat(getUpdateCount(id)).isEqualTo(MAX_UPDATES);
    assertThat(getMetaId(id)).isEqualTo(oldMetaId);
  }

  @Test
  public void cannotExceedMaxUpdatesCountingMultipleChangeUpdatesInSingleBatch() throws Exception {
    Change.Id id = createChangeWithPatchSets(2);

    ObjectId oldMetaId = getMetaId(id);
    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
      bu.addOp(id, new AddMessageOp("Update on PS1", PatchSet.id(id, 1)));
      bu.addOp(id, new AddMessageOp("Update on PS2", PatchSet.id(id, 2)));
      ResourceConflictException thrown = assertThrows(ResourceConflictException.class, bu::execute);
      assertThat(thrown)
          .hasMessageThat()
          .contains("Change " + id + " may not exceed " + MAX_UPDATES);
    }
    assertThat(getUpdateCount(id)).isEqualTo(MAX_UPDATES - 1);
    assertThat(getMetaId(id)).isEqualTo(oldMetaId);
  }

  @Test
  public void attentionSetUpdateEventsFiredForSeveralChangesInSingleBatch() throws Exception {
    Change.Id id1 = createChangeWithUpdates(1);
    Change.Id id2 = createChangeWithUpdates(1);
    attentionSetListeners.add("test", attentionSetListener);

    Account.Id reviewer1 =
        accountManager.authenticate(authRequestFactory.createForUser("user1")).getAccountId();
    Account.Id reviewer2 =
        accountManager.authenticate(authRequestFactory.createForUser("user2")).getAccountId();

    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
      bu.addOp(
          id1,
          addReviewersOpFactory.create(
              ImmutableSet.of(reviewer1), ImmutableList.of(), ReviewerState.REVIEWER, false));
      bu.addOp(
          id2,
          addReviewersOpFactory.create(
              ImmutableSet.of(reviewer2), ImmutableList.of(), ReviewerState.REVIEWER, false));
      bu.execute();
    }
    verify(attentionSetListener, times(2)).onAttentionSetChanged(attentionSetEventCaptor.capture());
    AttentionSetListener.Event event1 = attentionSetEventCaptor.getAllValues().get(0);
    assertThat(event1.getChange()._number).isEqualTo(id1.get());
    assertThat(event1.usersAdded()).containsExactly(reviewer1.get());
    assertThat(event1.usersRemoved()).isEmpty();

    AttentionSetListener.Event event2 = attentionSetEventCaptor.getAllValues().get(1);
    assertThat(event2.getChange()._number).isEqualTo(id2.get());
    assertThat(event2.usersRemoved()).isEmpty();
  }

  @Test
  public void exceedingMaxUpdatesAllowedWithCompleteNoOp() throws Exception {
    Change.Id id = createChangeWithUpdates(MAX_UPDATES);
    ObjectId oldMetaId = getMetaId(id);
    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
      bu.addOp(
          id,
          new BatchUpdateOp() {
            @Override
            public boolean updateChange(ChangeContext ctx) {
              return false;
            }
          });
      bu.execute();
    }
    assertThat(getUpdateCount(id)).isEqualTo(MAX_UPDATES);
    assertThat(getMetaId(id)).isEqualTo(oldMetaId);
  }

  @Test
  public void exceedingMaxUpdatesAllowedWithNoOpAfterPopulatingUpdate() throws Exception {
    Change.Id id = createChangeWithUpdates(MAX_UPDATES);
    ObjectId oldMetaId = getMetaId(id);
    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
      bu.addOp(
          id,
          new BatchUpdateOp() {
            @Override
            public boolean updateChange(ChangeContext ctx) {
              ctx.getUpdate(ctx.getChange().currentPatchSetId()).setChangeMessage("No-op");
              return false;
            }
          });
      bu.execute();
    }
    assertThat(getUpdateCount(id)).isEqualTo(MAX_UPDATES);
    assertThat(getMetaId(id)).isEqualTo(oldMetaId);
  }

  @Test
  public void exceedingMaxUpdatesAllowedWithSubmit() throws Exception {
    Change.Id id = createChangeWithUpdates(MAX_UPDATES);
    ObjectId oldMetaId = getMetaId(id);
    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
      bu.addOp(id, new SubmitOp());
      bu.execute();
    }
    assertThat(getUpdateCount(id)).isEqualTo(MAX_UPDATES + 1);
    assertThat(getMetaId(id)).isNotEqualTo(oldMetaId);
  }

  @Test
  public void exceedingMaxUpdatesAllowedWithSubmitAfterOtherOp() throws Exception {
    Change.Id id = createChangeWithPatchSets(2);
    ObjectId oldMetaId = getMetaId(id);
    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
      bu.addOp(id, new AddMessageOp("Message on PS1", PatchSet.id(id, 1)));
      bu.addOp(id, new SubmitOp());
      bu.execute();
    }
    assertThat(getUpdateCount(id)).isEqualTo(MAX_UPDATES + 1);
    assertThat(getMetaId(id)).isNotEqualTo(oldMetaId);
  }
  // Not possible to write a variant of this test that submits first and adds a message second in
  // the same batch, since submit always comes last.

  @Test
  public void exceedingMaxUpdatesAllowedWithAbandon() throws Exception {
    Change.Id id = createChangeWithUpdates(MAX_UPDATES);
    ObjectId oldMetaId = getMetaId(id);
    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
      bu.addOp(
          id,
          new BatchUpdateOp() {
            @Override
            public boolean updateChange(ChangeContext ctx) {
              ChangeUpdate update = ctx.getUpdate(ctx.getChange().currentPatchSetId());
              update.setChangeMessage("Abandon");
              update.setStatus(Change.Status.ABANDONED);
              return true;
            }
          });
      bu.execute();
    }
    assertThat(getUpdateCount(id)).isEqualTo(MAX_UPDATES + 1);
    assertThat(getMetaId(id)).isNotEqualTo(oldMetaId);
  }

  @Test
  public void limitPatchSetCount_exceed() throws Exception {
    Change.Id changeId = createChangeWithPatchSets(MAX_PATCH_SETS);
    ObjectId oldMetaId = getMetaId(changeId);
    ChangeNotes notes = changeNotesFactory.create(project, changeId);
    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
      ObjectId commitId =
          repo.amend(notes.getCurrentPatchSet().commitId()).message("kaboom").create();
      bu.addOp(
          changeId,
          patchSetInserterFactory
              .create(notes, PatchSet.id(changeId, MAX_PATCH_SETS + 1), commitId)
              .setMessage("kaboom"));
      ResourceConflictException thrown = assertThrows(ResourceConflictException.class, bu::execute);
      assertThat(thrown)
          .hasMessageThat()
          .contains("Change " + changeId + " may not exceed " + MAX_PATCH_SETS + " patch sets");
    }
    assertThat(changeNotesFactory.create(project, changeId).getPatchSets()).hasSize(MAX_PATCH_SETS);
    assertThat(getMetaId(changeId)).isEqualTo(oldMetaId);
  }

  @Test
  public void limitFileCount_exceed() throws Exception {
    Change.Id changeId = createChangeWithUpdates(1);
    ChangeNotes notes = changeNotesFactory.create(project, changeId);

    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
      ObjectId commitId =
          repo.amend(notes.getCurrentPatchSet().commitId())
              .add("bar.txt", "bar")
              .add("baz.txt", "baz")
              .add("boom.txt", "boom")
              .message("blah")
              .create();
      bu.addOp(
          changeId,
          patchSetInserterFactory
              .create(notes, PatchSet.id(changeId, 2), commitId)
              .setMessage("blah"));
      ResourceConflictException thrown = assertThrows(ResourceConflictException.class, bu::execute);
      assertThat(thrown)
          .hasMessageThat()
          .contains("Exceeding maximum number of files per change (3 > 2)");
    }
  }

  @Test
  public void limitFileCount_cacheKeyMatches() throws Exception {
    Change.Id changeId = createChangeWithUpdates(1);
    ChangeNotes notes = changeNotesFactory.create(project, changeId);

    int cacheSizeBefore = diffSummaryCache.asMap().size();

    // We don't want to depend on the test helper used above so we perform an explicit commit here.
    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
      ObjectId commitId =
          repo.amend(notes.getCurrentPatchSet().commitId())
              .add("bar.txt", "bar")
              .add("baz.txt", "baz")
              .message("blah")
              .create();
      bu.addOp(
          changeId,
          patchSetInserterFactory
              .create(notes, PatchSet.id(changeId, 3), commitId)
              .setMessage("blah"));
      bu.execute();
    }

    // Assert that we only performed the diff computation once. This would e.g. catch
    // bugs/deviations in the computation of the cache key.
    assertThat(diffSummaryCache.asMap()).hasSize(cacheSizeBefore + 1);
  }

  private Change.Id createChangeWithUpdates(int totalUpdates) throws Exception {
    checkArgument(totalUpdates > 0);
    checkArgument(totalUpdates <= MAX_UPDATES);
    Change.Id id = Change.id(sequences.nextChangeId());
    try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
      bu.insertChange(
          changeInserterFactory.create(
              id, repo.commit().message("Change").insertChangeId().create(), "refs/heads/master"));
      bu.execute();
    }
    assertThat(getUpdateCount(id)).isEqualTo(1);
    for (int i = 2; i <= totalUpdates; i++) {
      try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
        bu.addOp(id, new AddMessageOp("Update " + i));
        bu.execute();
      }
    }
    assertThat(getUpdateCount(id)).isEqualTo(totalUpdates);
    return id;
  }

  private Change.Id createChangeWithPatchSets(int patchSets) throws Exception {
    checkArgument(patchSets >= 2);
    Change.Id id = createChangeWithUpdates(MAX_UPDATES - 2);
    ChangeNotes notes = changeNotesFactory.create(project, id);
    for (int i = 2; i <= patchSets; ++i) {
      try (BatchUpdate bu = batchUpdateFactory.create(project, user.get(), TimeUtil.now())) {
        ObjectId commitId =
            repo.amend(notes.getCurrentPatchSet().commitId()).message("PS" + i).create();
        bu.addOp(
            id,
            patchSetInserterFactory
                .create(notes, PatchSet.id(id, i), commitId)
                .setMessage("Add PS" + i));
        bu.execute();
      }
    }
    return id;
  }

  private static class AddMessageOp implements BatchUpdateOp {
    private final String message;
    @Nullable private final PatchSet.Id psId;

    AddMessageOp(String message) {
      this(message, null);
    }

    AddMessageOp(String message, PatchSet.Id psId) {
      this.message = message;
      this.psId = psId;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws Exception {
      PatchSet.Id psIdToUpdate = psId;
      if (psIdToUpdate == null) {
        psIdToUpdate = ctx.getChange().currentPatchSetId();
      } else {
        checkState(
            ctx.getNotes().getPatchSets().containsKey(psIdToUpdate),
            "%s not in %s",
            psIdToUpdate,
            ctx.getNotes().getPatchSets().keySet());
      }
      ctx.getUpdate(psIdToUpdate).setChangeMessage(message);
      return true;
    }
  }

  private int getUpdateCount(Change.Id changeId) {
    return changeNotesFactory.create(project, changeId).getUpdateCount();
  }

  private ObjectId getMetaId(Change.Id changeId) throws Exception {
    return repo.getRepository().exactRef(RefNames.changeMetaRef(changeId)).getObjectId();
  }

  private static class SubmitOp implements BatchUpdateOp {
    @Override
    public boolean updateChange(ChangeContext ctx) throws Exception {
      SubmitRecord sr = new SubmitRecord();
      sr.status = SubmitRecord.Status.OK;
      SubmitRecord.Label cr = new SubmitRecord.Label();
      cr.status = SubmitRecord.Label.Status.OK;
      cr.appliedBy = ctx.getAccountId();
      cr.label = "Code-Review";
      sr.labels = ImmutableList.of(cr);
      ChangeUpdate update = ctx.getUpdate(ctx.getChange().currentPatchSetId());
      update.merge(new SubmissionId(ctx.getChange()), ImmutableList.of(sr));
      update.setChangeMessage("Submitted");
      return true;
    }
  }
}
