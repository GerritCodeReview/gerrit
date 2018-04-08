// Copyright (C) 2016 The Android Open Source Project
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
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.formatTime;
import static com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage.REVIEW_DB;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.git.RepoRefCache;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.notedb.ChangeBundle;
import com.google.gerrit.server.notedb.ChangeBundleReader;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NoteDbChangeState;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.PrimaryStorageMigrator;
import com.google.gerrit.server.notedb.TestChangeRebuilderWrapper;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.NoteDbMode;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.inject.Inject;
import com.google.inject.util.Providers;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class NoteDbPrimaryIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setString("notedb", null, "concurrentWriterTimeout", "0s");
    cfg.setString("notedb", null, "primaryStorageMigrationTimeout", "1d");
    cfg.setBoolean("noteDb", null, "testRebuilderWrapper", true);
    return cfg;
  }

  @Inject private ChangeBundleReader bundleReader;
  @Inject private CommentsUtil commentsUtil;
  @Inject private TestChangeRebuilderWrapper rebuilderWrapper;
  @Inject private ChangeNotes.Factory changeNotesFactory;
  @Inject private ChangeUpdate.Factory updateFactory;
  @Inject private InternalUser.Factory internalUserFactory;
  @Inject private RetryHelper retryHelper;

  private PrimaryStorageMigrator migrator;

  @Before
  public void setUp() throws Exception {
    assume().that(NoteDbMode.get()).isEqualTo(NoteDbMode.READ_WRITE);
    db = ReviewDbUtil.unwrapDb(db);
    TestTimeUtil.resetWithClockStep(1, SECONDS);
    migrator = newMigrator(null);
  }

  private PrimaryStorageMigrator newMigrator(
      @Nullable Retryer<NoteDbChangeState> ensureRebuiltRetryer) {
    return new PrimaryStorageMigrator(
        cfg,
        Providers.of(db),
        repoManager,
        allUsers,
        rebuilderWrapper,
        ensureRebuiltRetryer,
        changeNotesFactory,
        queryProvider,
        updateFactory,
        internalUserFactory,
        retryHelper);
  }

  @After
  public void tearDown() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void updateChange() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();
    setNoteDbPrimary(id);

    gApi.changes().id(id.get()).current().review(ReviewInput.approve());
    gApi.changes().id(id.get()).current().submit();

    ChangeInfo info = gApi.changes().id(id.get()).get();
    assertThat(info.status).isEqualTo(ChangeStatus.MERGED);
    ApprovalInfo approval = Iterables.getOnlyElement(info.labels.get("Code-Review").all);
    assertThat(approval._accountId).isEqualTo(admin.id.get());
    assertThat(approval.value).isEqualTo(2);
    assertThat(info.messages).hasSize(3);
    assertThat(Iterables.getLast(info.messages).message)
        .isEqualTo("Change has been successfully merged by " + admin.fullName);

    ChangeNotes notes = notesFactory.create(db, project, id);
    assertThat(notes.getChange().getStatus()).isEqualTo(Change.Status.MERGED);
    assertThat(notes.getChange().getNoteDbState())
        .isEqualTo(NoteDbChangeState.NOTE_DB_PRIMARY_STATE);

    // Writes weren't reflected in ReviewDb.
    assertThat(db.changes().get(id).getStatus()).isEqualTo(Change.Status.NEW);
    assertThat(db.patchSetApprovals().byChange(id)).isEmpty();
    assertThat(db.changeMessages().byChange(id)).hasSize(1);
  }

  @Test
  public void deleteDraftComment() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();
    setNoteDbPrimary(id);

    DraftInput din = new DraftInput();
    din.path = PushOneCommit.FILE_NAME;
    din.line = 1;
    din.message = "A comment";
    gApi.changes().id(id.get()).current().createDraft(din);

    CommentInfo di =
        Iterables.getOnlyElement(
            gApi.changes().id(id.get()).current().drafts().get(PushOneCommit.FILE_NAME));
    assertThat(di.message).isEqualTo(din.message);

    assertThat(db.patchComments().draftByChangeFileAuthor(id, din.path, admin.id)).isEmpty();

    gApi.changes().id(id.get()).current().draft(di.id).delete();
    assertThat(gApi.changes().id(id.get()).current().drafts()).isEmpty();
  }

  @Test
  public void deleteVote() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();
    setNoteDbPrimary(id);

    gApi.changes().id(id.get()).current().review(ReviewInput.approve());
    List<ApprovalInfo> approvals = gApi.changes().id(id.get()).get().labels.get("Code-Review").all;
    assertThat(approvals).hasSize(1);
    assertThat(approvals.get(0).value).isEqualTo(2);

    gApi.changes().id(id.get()).reviewer(admin.id.toString()).deleteVote("Code-Review");

    approvals = gApi.changes().id(id.get()).get().labels.get("Code-Review").all;
    assertThat(approvals).hasSize(1);
    assertThat(approvals.get(0).value).isEqualTo(0);
  }

  @Test
  public void deleteVoteViaReview() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();
    setNoteDbPrimary(id);

    gApi.changes().id(id.get()).current().review(ReviewInput.approve());
    List<ApprovalInfo> approvals = gApi.changes().id(id.get()).get().labels.get("Code-Review").all;
    assertThat(approvals).hasSize(1);
    assertThat(approvals.get(0).value).isEqualTo(2);

    gApi.changes().id(id.get()).current().review(ReviewInput.noScore());

    approvals = gApi.changes().id(id.get()).get().labels.get("Code-Review").all;
    assertThat(approvals).hasSize(1);
    assertThat(approvals.get(0).value).isEqualTo(0);
  }

  @Test
  public void deleteReviewer() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();
    setNoteDbPrimary(id);

    gApi.changes().id(id.get()).addReviewer(user.id.toString());
    assertThat(getReviewers(id)).containsExactly(user.id);
    gApi.changes().id(id.get()).reviewer(user.id.toString()).remove();
    assertThat(getReviewers(id)).isEmpty();
  }

  @Test
  public void readOnlyReviewDb() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();
    testReadOnly(id);
  }

  @Test
  public void readOnlyNoteDb() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();
    setNoteDbPrimary(id);
    testReadOnly(id);
  }

  private void testReadOnly(Change.Id id) throws Exception {
    Timestamp before = TimeUtil.nowTs();
    Timestamp until = new Timestamp(before.getTime() + 1000 * 3600);

    // Set read-only.
    Change c = db.changes().get(id);
    assertThat(c).named("change " + id).isNotNull();
    NoteDbChangeState state = NoteDbChangeState.parse(c);
    state = state.withReadOnlyUntil(until);
    c.setNoteDbState(state.toString());
    db.changes().update(Collections.singleton(c));

    assertThat(gApi.changes().id(id.get()).get().subject).isEqualTo(PushOneCommit.SUBJECT);
    assertThat(gApi.changes().id(id.get()).get().topic).isNull();
    try {
      gApi.changes().id(id.get()).topic("a-topic");
      fail("expected read-only exception");
    } catch (RestApiException e) {
      Optional<Throwable> oe =
          Throwables.getCausalChain(e)
              .stream()
              .filter(x -> x instanceof OrmRuntimeException)
              .findFirst();
      assertThat(oe).named("OrmRuntimeException in causal chain of " + e).isPresent();
      assertThat(oe.get().getMessage()).contains("read-only");
    }
    assertThat(gApi.changes().id(id.get()).get().topic).isNull();

    TestTimeUtil.setClock(new Timestamp(until.getTime() + 1000));
    assertThat(gApi.changes().id(id.get()).get().subject).isEqualTo(PushOneCommit.SUBJECT);
    gApi.changes().id(id.get()).topic("a-topic");
    assertThat(gApi.changes().id(id.get()).get().topic).isEqualTo("a-topic");
  }

  @Test
  public void migrateToNoteDb() throws Exception {
    testMigrateToNoteDb(createChange().getChange().getId());
  }

  @Test
  public void migrateToNoteDbWithRebuildingFirst() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();

    Change c = db.changes().get(id);
    c.setNoteDbState("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    db.changes().update(Collections.singleton(c));
    testMigrateToNoteDb(id);
  }

  private void testMigrateToNoteDb(Change.Id id) throws Exception {
    assertThat(PrimaryStorage.of(db.changes().get(id))).isEqualTo(PrimaryStorage.REVIEW_DB);
    migrator.migrateToNoteDbPrimary(id);
    assertNoteDbPrimary(id);

    gApi.changes().id(id.get()).topic("a-topic");
    assertThat(gApi.changes().id(id.get()).get().topic).isEqualTo("a-topic");
    assertThat(db.changes().get(id).getTopic()).isNull();
  }

  @Test
  public void migrateToNoteDbFailsRebuildingOnceAndRetries() throws Exception {
    Change.Id id = createChange().getChange().getId();

    Change c = db.changes().get(id);
    c.setNoteDbState("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    db.changes().update(Collections.singleton(c));
    rebuilderWrapper.failNextUpdate();

    migrator =
        newMigrator(
            RetryerBuilder.<NoteDbChangeState>newBuilder()
                .retryIfException()
                .withStopStrategy(StopStrategies.neverStop())
                .build());
    migrator.migrateToNoteDbPrimary(id);
    assertNoteDbPrimary(id);
  }

  @Test
  public void migrateToNoteDbFailsRebuildingAndStops() throws Exception {
    Change.Id id = createChange().getChange().getId();

    Change c = db.changes().get(id);
    c.setNoteDbState("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    db.changes().update(Collections.singleton(c));
    rebuilderWrapper.failNextUpdate();

    migrator =
        newMigrator(
            RetryerBuilder.<NoteDbChangeState>newBuilder()
                .retryIfException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(1))
                .build());
    exception.expect(OrmException.class);
    exception.expectMessage("Retrying failed");
    migrator.migrateToNoteDbPrimary(id);
  }

  @Test
  public void migrateToNoteDbMissingOldState() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();

    Change c = db.changes().get(id);
    c.setNoteDbState(null);
    db.changes().update(Collections.singleton(c));

    exception.expect(OrmRuntimeException.class);
    exception.expectMessage("no note_db_state");
    migrator.migrateToNoteDbPrimary(id);
  }

  @Test
  public void migrateToNoteDbLeaseExpires() throws Exception {
    TestTimeUtil.resetWithClockStep(2, DAYS);
    exception.expect(OrmRuntimeException.class);
    exception.expectMessage("read-only lease");
    migrator.migrateToNoteDbPrimary(createChange().getChange().getId());
  }

  @Test
  public void migrateToNoteDbAlreadyReadOnly() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();

    Change c = db.changes().get(id);
    NoteDbChangeState state = NoteDbChangeState.parse(c);
    Timestamp until = new Timestamp(TimeUtil.nowMs() + MILLISECONDS.convert(1, DAYS));
    state = state.withReadOnlyUntil(until);
    c.setNoteDbState(state.toString());
    db.changes().update(Collections.singleton(c));

    exception.expect(OrmRuntimeException.class);
    exception.expectMessage("read-only until " + until);
    migrator.migrateToNoteDbPrimary(id);
  }

  @Test
  public void migrateToNoteDbAlreadyMigrated() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();

    assertThat(PrimaryStorage.of(db.changes().get(id))).isEqualTo(PrimaryStorage.REVIEW_DB);
    migrator.migrateToNoteDbPrimary(id);
    assertNoteDbPrimary(id);

    migrator.migrateToNoteDbPrimary(id);
    assertNoteDbPrimary(id);
  }

  @Test
  public void rebuildReviewDb() throws Exception {
    Change c = createChange().getChange().change();
    Change.Id id = c.getId();

    CommentInput cin = new CommentInput();
    cin.line = 1;
    cin.message = "Published comment";
    ReviewInput rin = ReviewInput.approve();
    rin.comments = ImmutableMap.of(PushOneCommit.FILE_NAME, ImmutableList.of(cin));
    gApi.changes().id(id.get()).current().review(ReviewInput.approve());

    DraftInput din = new DraftInput();
    din.path = PushOneCommit.FILE_NAME;
    din.line = 1;
    din.message = "Draft comment";
    gApi.changes().id(id.get()).current().createDraft(din);
    gApi.changes().id(id.get()).current().review(ReviewInput.approve());
    gApi.changes().id(id.get()).current().createDraft(din);

    assertThat(db.changeMessages().byChange(id)).isNotEmpty();
    assertThat(db.patchSets().byChange(id)).isNotEmpty();
    assertThat(db.patchSetApprovals().byChange(id)).isNotEmpty();
    assertThat(db.patchComments().byChange(id)).isNotEmpty();

    ChangeBundle noteDbBundle =
        ChangeBundle.fromNotes(commentsUtil, notesFactory.create(db, project, id));

    setNoteDbPrimary(id);

    db.changeMessages().delete(db.changeMessages().byChange(id));
    db.patchSets().delete(db.patchSets().byChange(id));
    db.patchSetApprovals().delete(db.patchSetApprovals().byChange(id));
    db.patchComments().delete(db.patchComments().byChange(id));
    ChangeMessage bogusMessage =
        ChangeMessagesUtil.newMessage(
            c.currentPatchSetId(),
            identifiedUserFactory.create(admin.getId()),
            TimeUtil.nowTs(),
            "some message",
            null);
    db.changeMessages().insert(Collections.singleton(bogusMessage));

    rebuilderWrapper.rebuildReviewDb(db, project, id);

    assertThat(db.changeMessages().byChange(id)).isNotEmpty();
    assertThat(db.patchSets().byChange(id)).isNotEmpty();
    assertThat(db.patchSetApprovals().byChange(id)).isNotEmpty();
    assertThat(db.patchComments().byChange(id)).isNotEmpty();

    ChangeBundle reviewDbBundle = bundleReader.fromReviewDb(ReviewDbUtil.unwrapDb(db), id);
    assertThat(reviewDbBundle.differencesFrom(noteDbBundle)).isEmpty();
  }

  @Test
  public void migrateBackToReviewDbPrimary() throws Exception {
    Change c = createChange().getChange().change();
    Change.Id id = c.getId();

    migrator.migrateToNoteDbPrimary(id);
    assertNoteDbPrimary(id);

    gApi.changes().id(id.get()).topic("new-topic");
    assertThat(gApi.changes().id(id.get()).topic()).isEqualTo("new-topic");
    assertThat(db.changes().get(id).getTopic()).isNotEqualTo("new-topic");

    migrator.migrateToReviewDbPrimary(id, null);
    ObjectId metaId;
    try (Repository repo = repoManager.openRepository(c.getProject());
        RevWalk rw = new RevWalk(repo)) {
      metaId = repo.exactRef(RefNames.changeMetaRef(id)).getObjectId();
      RevCommit commit = rw.parseCommit(metaId);
      rw.parseBody(commit);
      assertThat(commit.getFullMessage())
          .contains("Read-only-until: " + formatTime(serverIdent.get(), new Timestamp(0)));
    }
    NoteDbChangeState state = NoteDbChangeState.parse(db.changes().get(id));
    assertThat(state.getPrimaryStorage()).isEqualTo(PrimaryStorage.REVIEW_DB);
    assertThat(state.getChangeMetaId()).isEqualTo(metaId);
    assertThat(gApi.changes().id(id.get()).topic()).isEqualTo("new-topic");
    assertThat(db.changes().get(id).getTopic()).isEqualTo("new-topic");

    ChangeNotes notes = notesFactory.create(db, project, id);
    assertThat(notes.getRevision()).isEqualTo(metaId); // No rebuilding, change was up to date.
    assertThat(notes.getReadOnlyUntil()).isNotNull();

    gApi.changes().id(id.get()).topic("reviewdb-topic");
    assertThat(db.changes().get(id).getTopic()).isEqualTo("reviewdb-topic");
  }

  private void setNoteDbPrimary(Change.Id id) throws Exception {
    Change c = db.changes().get(id);
    assertThat(c).named("change " + id).isNotNull();
    NoteDbChangeState state = NoteDbChangeState.parse(c);
    assertThat(state.getPrimaryStorage()).named("storage of " + id).isEqualTo(REVIEW_DB);

    try (Repository changeRepo = repoManager.openRepository(c.getProject());
        Repository allUsersRepo = repoManager.openRepository(allUsers)) {
      assertThat(state.isUpToDate(new RepoRefCache(changeRepo), new RepoRefCache(allUsersRepo)))
          .named("change " + id + " up to date")
          .isTrue();
    }

    c.setNoteDbState(NoteDbChangeState.NOTE_DB_PRIMARY_STATE);
    db.changes().update(Collections.singleton(c));
  }

  private void assertNoteDbPrimary(Change.Id id) throws Exception {
    assertThat(PrimaryStorage.of(db.changes().get(id))).isEqualTo(PrimaryStorage.NOTE_DB);
  }

  private List<Account.Id> getReviewers(Change.Id id) throws Exception {
    return gApi.changes()
        .id(id.get())
        .get()
        .reviewers
        .values()
        .stream()
        .flatMap(Collection::stream)
        .map(a -> new Account.Id(a._accountId))
        .collect(toList());
  }
}
