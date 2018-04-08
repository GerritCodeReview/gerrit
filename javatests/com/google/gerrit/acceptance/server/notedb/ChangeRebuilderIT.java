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
import static com.google.common.truth.Truth.assert_;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.reviewdb.client.RefNames.changeMetaRef;
import static com.google.gerrit.reviewdb.client.RefNames.refsDraftComments;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.git.RepoRefCache;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.notedb.ChangeBundle;
import com.google.gerrit.server.notedb.ChangeBundleReader;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NoteDbChangeState;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NoteDbUpdateManager;
import com.google.gerrit.server.notedb.TestChangeRebuilderWrapper;
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilder.NoPatchSetsException;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.testing.Util;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.restapi.change.PostReview;
import com.google.gerrit.server.restapi.change.Rebuild;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.NoteDbChecker;
import com.google.gerrit.testing.NoteDbMode;
import com.google.gerrit.testing.TestChanges;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ChangeRebuilderIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setBoolean("noteDb", null, "testRebuilderWrapper", true);

    // Disable async reindex-if-stale check after index update. This avoids
    // unintentional auto-rebuilding of the change in NoteDb during the read
    // path of the reindex-if-stale check. For the purposes of this test, we
    // want precise control over when auto-rebuilding happens.
    cfg.setBoolean("index", null, "autoReindexIfStale", false);

    // setNotesMigration tries to keep IDs in sync between ReviewDb and NoteDb, which is behavior
    // unique to this test. This gets prohibitively slow if we use the default sequence gap.
    cfg.setInt("noteDb", "changes", "initialSequenceGap", 0);

    return cfg;
  }

  @Inject private NoteDbChecker checker;

  @Inject private Rebuild rebuildHandler;

  @Inject private Provider<ReviewDb> dbProvider;

  @Inject private CommentsUtil commentsUtil;

  @Inject private Provider<PostReview> postReview;

  @Inject private TestChangeRebuilderWrapper rebuilderWrapper;

  @Inject private Sequences seq;

  @Inject private ChangeBundleReader bundleReader;

  @Inject private PatchSetInfoFactory patchSetInfoFactory;

  @Inject private PatchListCache patchListCache;

  @Before
  public void setUp() throws Exception {
    assume().that(NoteDbMode.get()).isEqualTo(NoteDbMode.OFF);
    TestTimeUtil.resetWithClockStep(1, SECONDS);
    setNotesMigration(false, false);
  }

  @After
  public void tearDown() {
    TestTimeUtil.useSystemTime();
  }

  @SuppressWarnings("deprecation")
  private void setNotesMigration(boolean writeChanges, boolean readChanges) throws Exception {
    notesMigration.setWriteChanges(writeChanges);
    notesMigration.setReadChanges(readChanges);
    db = atrScope.reopenDb().getReviewDbProvider().get();

    if (notesMigration.readChangeSequence()) {
      // Copy next ReviewDb ID to NoteDb.
      seq.getChangeIdRepoSequence().set(db.nextChangeId());
    } else {
      // Copy next NoteDb ID to ReviewDb.
      while (db.nextChangeId() < seq.getChangeIdRepoSequence().next()) {}
    }
  }

  @Test
  public void changeFields() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    gApi.changes().id(id.get()).topic(name("a-topic"));
    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void patchSets() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    r = amendChange(r.getChangeId());
    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void publishedComment() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    putComment(user, id, 1, "comment", null);
    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void publishedCommentAndReply() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    putComment(user, id, 1, "comment", null);
    Map<String, List<CommentInfo>> comments = getPublishedComments(id);
    String parentUuid = comments.get("a.txt").get(0).id;
    putComment(user, id, 1, "comment", parentUuid);
    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void patchSetWithNullGroups() throws Exception {
    Timestamp ts = TimeUtil.nowTs();
    Change c = TestChanges.newChange(project, user.getId(), seq.nextChangeId());
    c.setCreatedOn(ts);
    c.setLastUpdatedOn(ts);
    c.setReviewStarted(true);
    PatchSet ps =
        TestChanges.newPatchSet(
            c.currentPatchSetId(), "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef", user.getId());
    ps.setCreatedOn(ts);
    db.changes().insert(Collections.singleton(c));
    db.patchSets().insert(Collections.singleton(ps));

    assertThat(ps.getGroups()).isEmpty();
    checker.rebuildAndCheckChanges(c.getId());
  }

  @Test
  public void draftComment() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    putDraft(user, id, 1, "comment", null);
    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void draftAndPublishedComment() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    putDraft(user, id, 1, "draft comment", null);
    putComment(user, id, 1, "published comment", null);
    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void publishDraftComment() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    putDraft(user, id, 1, "draft comment", null);
    publishDrafts(user, id);
    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void nullAccountId() throws Exception {
    PushOneCommit.Result r = createChange();
    PatchSet.Id psId = r.getPatchSetId();
    Change.Id id = psId.getParentKey();

    // Events need to be otherwise identical for the account ID to be compared.
    ChangeMessage msg1 = insertMessage(id, psId, user.getId(), TimeUtil.nowTs(), "message 1");
    insertMessage(id, psId, null, msg1.getWrittenOn(), "message 2");

    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void nullPatchSetId() throws Exception {
    PushOneCommit.Result r = createChange();
    PatchSet.Id psId1 = r.getPatchSetId();
    Change.Id id = psId1.getParentKey();

    // Events need to be otherwise identical for the PatchSet.ID to be compared.
    ChangeMessage msg1 = insertMessage(id, null, user.getId(), TimeUtil.nowTs(), "message 1");
    insertMessage(id, null, user.getId(), msg1.getWrittenOn(), "message 2");

    PatchSet.Id psId2 = amendChange(r.getChangeId()).getPatchSetId();

    ChangeMessage msg3 = insertMessage(id, null, user.getId(), TimeUtil.nowTs(), "message 3");
    insertMessage(id, null, user.getId(), msg3.getWrittenOn(), "message 4");

    checker.rebuildAndCheckChanges(id);

    setNotesMigration(true, true);

    ChangeNotes notes = notesFactory.create(db, project, id);
    Map<String, PatchSet.Id> psIds = new HashMap<>();
    for (ChangeMessage msg : notes.getChangeMessages()) {
      PatchSet.Id psId = msg.getPatchSetId();
      assertThat(psId).named("patchset for " + msg).isNotNull();
      psIds.put(msg.getMessage(), psId);
    }
    // Patch set IDs were replaced during conversion process.
    assertThat(psIds).containsEntry("message 1", psId1);
    assertThat(psIds).containsEntry("message 2", psId1);
    assertThat(psIds).containsEntry("message 3", psId2);
    assertThat(psIds).containsEntry("message 4", psId2);
  }

  @Test
  public void noWriteToNewRef() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    checker.assertNoChangeRef(project, id);

    setNotesMigration(true, false);
    gApi.changes().id(id.get()).topic(name("a-topic"));

    // First write doesn't create the ref, but rebuilding works.
    checker.assertNoChangeRef(project, id);
    assertThat(getUnwrappedDb().changes().get(id).getNoteDbState()).isNull();
    checker.rebuildAndCheckChanges(id);

    // Now that there is a ref, writes are "turned on" for this change, and
    // NoteDb stays up to date without explicit rebuilding.
    gApi.changes().id(id.get()).topic(name("new-topic"));
    assertThat(getUnwrappedDb().changes().get(id).getNoteDbState()).isNotNull();
    checker.checkChanges(id);
  }

  @Test
  public void restApiNotFoundWhenNoteDbDisabled() throws Exception {
    PushOneCommit.Result r = createChange();
    exception.expect(ResourceNotFoundException.class);
    rebuildHandler.apply(parseChangeResource(r.getChangeId()), new Input());
  }

  @Test
  public void rebuildViaRestApi() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    setNotesMigration(true, false);

    checker.assertNoChangeRef(project, id);
    rebuildHandler.apply(parseChangeResource(r.getChangeId()), new Input());
    checker.checkChanges(id);
  }

  @Test
  public void writeToNewRefForNewChange() throws Exception {
    PushOneCommit.Result r1 = createChange();
    Change.Id id1 = r1.getPatchSetId().getParentKey();

    setNotesMigration(true, false);
    gApi.changes().id(id1.get()).topic(name("a-topic"));
    PushOneCommit.Result r2 = createChange();
    Change.Id id2 = r2.getPatchSetId().getParentKey();

    // Second change was created after NoteDb writes were turned on, so it was
    // allowed to write to a new ref.
    checker.checkChanges(id2);

    // First change was created before NoteDb writes were turned on, so its meta
    // ref doesn't exist until a manual rebuild.
    checker.assertNoChangeRef(project, id1);
    checker.rebuildAndCheckChanges(id1);
  }

  @Test
  public void noteDbChangeState() throws Exception {
    setNotesMigration(true, true);
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();

    ObjectId changeMetaId = getMetaRef(project, changeMetaRef(id));
    assertThat(getUnwrappedDb().changes().get(id).getNoteDbState()).isEqualTo(changeMetaId.name());

    putDraft(user, id, 1, "comment by user", null);
    ObjectId userDraftsId = getMetaRef(allUsers, refsDraftComments(id, user.getId()));
    assertThat(getUnwrappedDb().changes().get(id).getNoteDbState())
        .isEqualTo(changeMetaId.name() + "," + user.getId() + "=" + userDraftsId.name());

    putDraft(admin, id, 2, "comment by admin", null);
    ObjectId adminDraftsId = getMetaRef(allUsers, refsDraftComments(id, admin.getId()));
    assertThat(admin.getId().get()).isLessThan(user.getId().get());
    assertThat(getUnwrappedDb().changes().get(id).getNoteDbState())
        .isEqualTo(
            changeMetaId.name()
                + ","
                + admin.getId()
                + "="
                + adminDraftsId.name()
                + ","
                + user.getId()
                + "="
                + userDraftsId.name());

    putDraft(admin, id, 2, "revised comment by admin", null);
    adminDraftsId = getMetaRef(allUsers, refsDraftComments(id, admin.getId()));
    assertThat(getUnwrappedDb().changes().get(id).getNoteDbState())
        .isEqualTo(
            changeMetaId.name()
                + ","
                + admin.getId()
                + "="
                + adminDraftsId.name()
                + ","
                + user.getId()
                + "="
                + userDraftsId.name());
  }

  @Test
  public void rebuildAutomaticallyWhenChangeOutOfDate() throws Exception {
    setNotesMigration(true, true);

    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    assertChangeUpToDate(true, id);

    // Make a ReviewDb change behind NoteDb's back and ensure it's detected.
    setNotesMigration(false, false);
    gApi.changes().id(id.get()).topic(name("a-topic"));
    setInvalidNoteDbState(id);
    assertChangeUpToDate(false, id);

    // On next NoteDb read, the change is transparently rebuilt.
    setNotesMigration(true, true);
    assertThat(gApi.changes().id(id.get()).info().topic).isEqualTo(name("a-topic"));
    assertChangeUpToDate(true, id);

    // Check that the bundles are equal.
    ChangeBundle actual =
        ChangeBundle.fromNotes(commentsUtil, notesFactory.create(dbProvider.get(), project, id));
    ChangeBundle expected = bundleReader.fromReviewDb(getUnwrappedDb(), id);
    assertThat(actual.differencesFrom(expected)).isEmpty();
  }

  @Test
  public void rebuildAutomaticallyWithinBatchUpdate() throws Exception {
    setNotesMigration(true, true);

    PushOneCommit.Result r = createChange();
    final Change.Id id = r.getPatchSetId().getParentKey();
    assertChangeUpToDate(true, id);

    // Update ReviewDb and NoteDb, then revert the corresponding NoteDb change
    // to simulate it failing.
    NoteDbChangeState oldState = NoteDbChangeState.parse(getUnwrappedDb().changes().get(id));
    String topic = name("a-topic");
    gApi.changes().id(id.get()).topic(topic);
    try (Repository repo = repoManager.openRepository(project)) {
      new TestRepository<>(repo).update(RefNames.changeMetaRef(id), oldState.getChangeMetaId());
    }
    assertChangeUpToDate(false, id);

    // Next NoteDb read comes inside the transaction started by BatchUpdate. In
    // reality this could be caused by a failed update happening between when
    // the change is parsed by ChangesCollection and when the BatchUpdate
    // executes. We simulate it here by using BatchUpdate directly and not going
    // through an API handler.
    final String msg = "message from BatchUpdate";
    try (BatchUpdate bu =
        batchUpdateFactory.create(
            db, project, identifiedUserFactory.create(user.getId()), TimeUtil.nowTs())) {
      bu.addOp(
          id,
          new BatchUpdateOp() {
            @Override
            public boolean updateChange(ChangeContext ctx) throws OrmException {
              PatchSet.Id psId = ctx.getChange().currentPatchSetId();
              ChangeMessage cm =
                  new ChangeMessage(
                      new ChangeMessage.Key(id, ChangeUtil.messageUuid()),
                      ctx.getAccountId(),
                      ctx.getWhen(),
                      psId);
              cm.setMessage(msg);
              ctx.getDb().changeMessages().insert(Collections.singleton(cm));
              ctx.getUpdate(psId).setChangeMessage(msg);
              return true;
            }
          });
      try {
        bu.execute();
        fail("expected update to fail");
      } catch (UpdateException e) {
        assertThat(e.getMessage()).contains("cannot copy ChangeNotesState");
      }
    }

    // TODO(dborowitz): Re-enable these assertions once we fix auto-rebuilding
    // in the BatchUpdate path.
    // As an implementation detail, change wasn't actually rebuilt inside the
    // BatchUpdate transaction, but it was rebuilt during read for the
    // subsequent reindex. Thus it's impossible to actually observe an
    // out-of-date state in the caller.
    // assertChangeUpToDate(true, id);

    // Check that the bundles are equal.
    // ChangeNotes notes = notesFactory.create(dbProvider.get(), project, id);
    // ChangeBundle actual = ChangeBundle.fromNotes(commentsUtil, notes);
    // ChangeBundle expected = bundleReader.fromReviewDb(getUnwrappedDb(), id);
    // assertThat(actual.differencesFrom(expected)).isEmpty();
    // assertThat(
    //        Iterables.transform(
    //            notes.getChangeMessages(),
    //            ChangeMessage::getMessage))
    //    .contains(msg);
    // assertThat(actual.getChange().getTopic()).isEqualTo(topic);
  }

  @Test
  public void rebuildIgnoresErrorIfChangeIsUpToDateAfter() throws Exception {
    setNotesMigration(true, true);

    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    assertChangeUpToDate(true, id);

    // Make a ReviewDb change behind NoteDb's back and ensure it's detected.
    setNotesMigration(false, false);
    gApi.changes().id(id.get()).topic(name("a-topic"));
    setInvalidNoteDbState(id);
    assertChangeUpToDate(false, id);

    // Force the next rebuild attempt to fail but also rebuild the change in the
    // background.
    rebuilderWrapper.stealNextUpdate();
    setNotesMigration(true, true);
    assertThat(gApi.changes().id(id.get()).info().topic).isEqualTo(name("a-topic"));
    assertChangeUpToDate(true, id);

    // Check that the bundles are equal.
    ChangeBundle actual =
        ChangeBundle.fromNotes(commentsUtil, notesFactory.create(dbProvider.get(), project, id));
    ChangeBundle expected = bundleReader.fromReviewDb(getUnwrappedDb(), id);
    assertThat(actual.differencesFrom(expected)).isEmpty();
  }

  @Test
  public void rebuildReturnsCorrectResultEvenIfSavingToNoteDbFailed() throws Exception {
    setNotesMigration(true, true);

    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    assertChangeUpToDate(true, id);
    ObjectId oldMetaId = getMetaRef(project, changeMetaRef(id));

    // Make a ReviewDb change behind NoteDb's back.
    setNotesMigration(false, false);
    gApi.changes().id(id.get()).topic(name("a-topic"));
    setInvalidNoteDbState(id);
    assertChangeUpToDate(false, id);
    assertThat(getMetaRef(project, changeMetaRef(id))).isEqualTo(oldMetaId);

    // Force the next rebuild attempt to fail.
    rebuilderWrapper.failNextUpdate();
    setNotesMigration(true, true);
    ChangeNotes notes = notesFactory.create(dbProvider.get(), project, id);

    // Not up to date, but the actual returned state matches anyway.
    assertChangeUpToDate(false, id);
    assertThat(getMetaRef(project, changeMetaRef(id))).isEqualTo(oldMetaId);
    ChangeBundle actual = ChangeBundle.fromNotes(commentsUtil, notes);
    ChangeBundle expected = bundleReader.fromReviewDb(getUnwrappedDb(), id);
    assertThat(actual.differencesFrom(expected)).isEmpty();
    assertChangeUpToDate(false, id);

    // Another rebuild attempt succeeds
    notesFactory.create(dbProvider.get(), project, id);
    assertThat(getMetaRef(project, changeMetaRef(id))).isNotEqualTo(oldMetaId);
    assertChangeUpToDate(true, id);
  }

  @Test
  public void rebuildReturnsDraftResultWhenRebuildingInChangeNotesFails() throws Exception {
    setNotesMigration(true, true);

    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    putDraft(user, id, 1, "comment by user", null);
    assertChangeUpToDate(true, id);

    ObjectId oldMetaId = getMetaRef(allUsers, refsDraftComments(id, user.getId()));

    // Add a draft behind NoteDb's back.
    setNotesMigration(false, false);
    putDraft(user, id, 1, "second comment by user", null);
    setInvalidNoteDbState(id);
    assertDraftsUpToDate(false, id, user);
    assertThat(getMetaRef(allUsers, refsDraftComments(id, user.getId()))).isEqualTo(oldMetaId);

    // Force the next rebuild attempt to fail (in ChangeNotes).
    rebuilderWrapper.failNextUpdate();
    setNotesMigration(true, true);
    ChangeNotes notes = notesFactory.create(dbProvider.get(), project, id);
    notes.getDraftComments(user.getId());
    assertThat(getMetaRef(allUsers, refsDraftComments(id, user.getId()))).isEqualTo(oldMetaId);

    // Not up to date, but the actual returned state matches anyway.
    assertDraftsUpToDate(false, id, user);
    ChangeBundle actual = ChangeBundle.fromNotes(commentsUtil, notes);
    ChangeBundle expected = bundleReader.fromReviewDb(getUnwrappedDb(), id);
    assertThat(actual.differencesFrom(expected)).isEmpty();

    // Another rebuild attempt succeeds
    notesFactory.create(dbProvider.get(), project, id);
    assertChangeUpToDate(true, id);
    assertDraftsUpToDate(true, id, user);
    assertThat(getMetaRef(allUsers, refsDraftComments(id, user.getId()))).isNotEqualTo(oldMetaId);
  }

  @Test
  public void rebuildReturnsDraftResultWhenRebuildingInDraftCommentNotesFails() throws Exception {
    setNotesMigration(true, true);

    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    putDraft(user, id, 1, "comment by user", null);
    assertChangeUpToDate(true, id);

    ObjectId oldMetaId = getMetaRef(allUsers, refsDraftComments(id, user.getId()));

    // Add a draft behind NoteDb's back.
    setNotesMigration(false, false);
    putDraft(user, id, 1, "second comment by user", null);

    ReviewDb db = getUnwrappedDb();
    Change c = db.changes().get(id);
    // Leave change meta ID alone so DraftCommentNotes does the rebuild.
    ObjectId badSha = ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    NoteDbChangeState bogusState =
        new NoteDbChangeState(
            id,
            PrimaryStorage.REVIEW_DB,
            Optional.of(
                NoteDbChangeState.RefState.create(
                    NoteDbChangeState.parse(c).getChangeMetaId(),
                    ImmutableMap.of(user.getId(), badSha))),
            Optional.empty());
    c.setNoteDbState(bogusState.toString());
    db.changes().update(Collections.singleton(c));

    assertDraftsUpToDate(false, id, user);
    assertThat(getMetaRef(allUsers, refsDraftComments(id, user.getId()))).isEqualTo(oldMetaId);

    // Force the next rebuild attempt to fail (in DraftCommentNotes).
    rebuilderWrapper.failNextUpdate();
    setNotesMigration(true, true);
    ChangeNotes notes = notesFactory.create(dbProvider.get(), project, id);
    notes.getDraftComments(user.getId());
    assertThat(getMetaRef(allUsers, refsDraftComments(id, user.getId()))).isEqualTo(oldMetaId);

    // Not up to date, but the actual returned state matches anyway.
    assertChangeUpToDate(true, id);
    assertDraftsUpToDate(false, id, user);
    ChangeBundle actual = ChangeBundle.fromNotes(commentsUtil, notes);
    ChangeBundle expected = bundleReader.fromReviewDb(getUnwrappedDb(), id);
    assertThat(actual.differencesFrom(expected)).isEmpty();

    // Another rebuild attempt succeeds
    notesFactory.create(dbProvider.get(), project, id).getDraftComments(user.getId());
    assertChangeUpToDate(true, id);
    assertDraftsUpToDate(true, id, user);
    assertThat(getMetaRef(allUsers, refsDraftComments(id, user.getId()))).isNotEqualTo(oldMetaId);
  }

  @Test
  public void rebuildAutomaticallyWhenDraftsOutOfDate() throws Exception {
    setNotesMigration(true, true);
    setApiUser(user);

    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    putDraft(user, id, 1, "comment", null);
    assertDraftsUpToDate(true, id, user);

    // Make a ReviewDb change behind NoteDb's back and ensure it's detected.
    setNotesMigration(false, false);
    putDraft(user, id, 1, "comment", null);
    setInvalidNoteDbState(id);
    assertDraftsUpToDate(false, id, user);

    // On next NoteDb read, the drafts are transparently rebuilt.
    setNotesMigration(true, true);
    assertThat(gApi.changes().id(id.get()).current().drafts()).containsKey(PushOneCommit.FILE_NAME);
    assertDraftsUpToDate(true, id, user);
  }

  @Test
  public void pushCert() throws Exception {
    // We don't have the code in our test harness to do signed pushes, so just
    // use a hard-coded cert. This cert was actually generated by C git 2.2.0
    // (albeit not for sending to Gerrit).
    String cert =
        "certificate version 0.1\n"
            + "pusher Dave Borowitz <dborowitz@google.com> 1433954361 -0700\n"
            + "pushee git://localhost/repo.git\n"
            + "nonce 1433954361-bde756572d665bba81d8\n"
            + "\n"
            + "0000000000000000000000000000000000000000"
            + "b981a177396fb47345b7df3e4d3f854c6bea7"
            + "s/heads/master\n"
            + "-----BEGIN PGP SIGNATURE-----\n"
            + "Version: GnuPG v1\n"
            + "\n"
            + "iQEcBAABAgAGBQJVeGg5AAoJEPfTicJkUdPkUggH/RKAeI9/i/LduuiqrL/SSdIa\n"
            + "9tYaSqJKLbXz63M/AW4Sp+4u+dVCQvnAt/a35CVEnpZz6hN4Kn/tiswOWVJf4CO7\n"
            + "htNubGs5ZMwvD6sLYqKAnrM3WxV/2TbbjzjZW6Jkidz3jz/WRT4SmjGYiEO7aA+V\n"
            + "4ZdIS9f7sW5VsHHYlNThCA7vH8Uu48bUovFXyQlPTX0pToSgrWV3JnTxDNxfn3iG\n"
            + "IL0zTY/qwVCdXgFownLcs6J050xrrBWIKqfcWr3u4D2aCLyR0v+S/KArr7ulZygY\n"
            + "+SOklImn8TAZiNxhWtA6ens66IiammUkZYFv7SSzoPLFZT4dC84SmGPWgf94NoQ=\n"
            + "=XFeC\n"
            + "-----END PGP SIGNATURE-----\n";

    PushOneCommit.Result r = createChange();
    PatchSet.Id psId = r.getPatchSetId();
    Change.Id id = psId.getParentKey();

    PatchSet ps = db.patchSets().get(psId);
    ps.setPushCertificate(cert);
    db.patchSets().update(Collections.singleton(ps));
    indexer.index(db, project, id);

    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void emptyTopic() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    Change c = db.changes().get(id);
    assertThat(c.getTopic()).isNull();
    c.setTopic("");
    db.changes().update(Collections.singleton(c));

    checker.rebuildAndCheckChanges(id);

    setNotesMigration(true, true);

    // Rebuild and check was successful, but NoteDb doesn't support storing an
    // empty topic, so it comes out as null.
    ChangeNotes notes = notesFactory.create(db, project, id);
    assertThat(notes.getChange().getTopic()).isNull();
  }

  @Test
  public void commentBeforeFirstPatchSet() throws Exception {
    PushOneCommit.Result r = createChange();
    PatchSet.Id psId = r.getPatchSetId();
    Change.Id id = psId.getParentKey();

    Change c = db.changes().get(id);
    c.setCreatedOn(new Timestamp(c.getCreatedOn().getTime() - 5000));
    db.changes().update(Collections.singleton(c));
    indexer.index(db, project, id);

    ReviewInput rin = new ReviewInput();
    rin.message = "comment";

    Timestamp ts = new Timestamp(c.getCreatedOn().getTime() + 2000);
    assertThat(ts).isGreaterThan(c.getCreatedOn());
    assertThat(ts).isLessThan(db.patchSets().get(psId).getCreatedOn());
    RevisionResource revRsrc = parseCurrentRevisionResource(r.getChangeId());
    postReview.get().apply(batchUpdateFactory, revRsrc, rin, ts);

    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void commentPredatingChangeBySomeoneOtherThanOwner() throws Exception {
    PushOneCommit.Result r = createChange();
    PatchSet.Id psId = r.getPatchSetId();
    Change.Id id = psId.getParentKey();
    Change c = db.changes().get(id);

    ReviewInput rin = new ReviewInput();
    rin.message = "comment";

    Timestamp ts = new Timestamp(c.getCreatedOn().getTime() - 10000);
    RevisionResource revRsrc = parseCurrentRevisionResource(r.getChangeId());
    setApiUser(user);
    postReview.get().apply(batchUpdateFactory, revRsrc, rin, ts);

    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void noteDbUsesOriginalSubjectFromPatchSetAndIgnoresChangeField() throws Exception {
    PushOneCommit.Result r = createChange();
    String orig = r.getChange().change().getSubject();
    r =
        pushFactory
            .create(
                db,
                admin.getIdent(),
                testRepo,
                orig + " v2",
                PushOneCommit.FILE_NAME,
                "new contents",
                r.getChangeId())
            .to("refs/for/master");
    r.assertOkStatus();

    PatchSet.Id psId = r.getPatchSetId();
    Change.Id id = psId.getParentKey();
    Change c = db.changes().get(id);

    c.setCurrentPatchSet(psId, c.getSubject(), "Bogus original subject");
    db.changes().update(Collections.singleton(c));

    checker.rebuildAndCheckChanges(id);

    setNotesMigration(true, true);
    ChangeNotes notes = notesFactory.create(db, project, id);
    Change nc = notes.getChange();
    assertThat(nc.getSubject()).isEqualTo(c.getSubject());
    assertThat(nc.getSubject()).isEqualTo(orig + " v2");
    assertThat(nc.getOriginalSubject()).isNotEqualTo(c.getOriginalSubject());
    assertThat(nc.getOriginalSubject()).isEqualTo(orig);
  }

  @Test
  public void ignorePatchLineCommentsOnPatchSet0() throws Exception {
    PushOneCommit.Result r = createChange();
    Change change = r.getChange().change();
    Change.Id id = change.getId();

    PatchLineComment comment =
        new PatchLineComment(
            new PatchLineComment.Key(
                new Patch.Key(new PatchSet.Id(id, 0), PushOneCommit.FILE_NAME), "uuid"),
            0,
            user.getId(),
            null,
            TimeUtil.nowTs());
    comment.setSide((short) 1);
    comment.setMessage("message");
    comment.setStatus(PatchLineComment.Status.PUBLISHED);
    db.patchComments().insert(Collections.singleton(comment));
    indexer.index(db, change.getProject(), id);

    checker.rebuildAndCheckChanges(id);

    setNotesMigration(true, true);
    ChangeNotes notes = notesFactory.create(db, project, id);
    assertThat(notes.getComments()).isEmpty();
  }

  @Test
  public void leadingSpacesInSubject() throws Exception {
    String subj = "   " + PushOneCommit.SUBJECT;
    PushOneCommit push =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            subj,
            PushOneCommit.FILE_NAME,
            PushOneCommit.FILE_CONTENT);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();
    Change change = r.getChange().change();
    assertThat(change.getSubject()).isEqualTo(subj);
    Change.Id id = r.getPatchSetId().getParentKey();

    checker.rebuildAndCheckChanges(id);

    setNotesMigration(true, true);
    ChangeNotes notes = notesFactory.create(db, project, id);
    assertThat(notes.getChange().getSubject()).isNotEqualTo(subj);
    assertThat(notes.getChange().getSubject()).isEqualTo(PushOneCommit.SUBJECT);
  }

  @Test
  public void allTimestampsExceptUpdatedAreEqualDueToBadMigration() throws Exception {
    // https://bugs.chromium.org/p/gerrit/issues/detail?id=7397
    PushOneCommit.Result r = createChange();
    Change c = r.getChange().change();
    Change.Id id = c.getId();
    Timestamp ts = TimeUtil.nowTs();
    Timestamp origUpdated = c.getLastUpdatedOn();

    c.setCreatedOn(ts);
    assertThat(c.getCreatedOn()).isGreaterThan(c.getLastUpdatedOn());
    db.changes().update(Collections.singleton(c));

    List<ChangeMessage> cm = db.changeMessages().byChange(id).toList();
    cm.forEach(m -> m.setWrittenOn(ts));
    db.changeMessages().update(cm);

    List<PatchSet> ps = db.patchSets().byChange(id).toList();
    ps.forEach(p -> p.setCreatedOn(ts));
    db.patchSets().update(ps);

    List<PatchSetApproval> psa = db.patchSetApprovals().byChange(id).toList();
    psa.forEach(p -> p.setGranted(ts));
    db.patchSetApprovals().update(psa);

    List<PatchLineComment> plc = db.patchComments().byChange(id).toList();
    plc.forEach(p -> p.setWrittenOn(ts));
    db.patchComments().update(plc);

    checker.rebuildAndCheckChanges(id);

    setNotesMigration(true, true);
    ChangeNotes notes = notesFactory.create(db, project, id);
    assertThat(notes.getChange().getCreatedOn()).isEqualTo(origUpdated);
    assertThat(notes.getChange().getLastUpdatedOn()).isAtLeast(origUpdated);
    assertThat(notes.getPatchSets().get(new PatchSet.Id(id, 1)).getCreatedOn())
        .isEqualTo(origUpdated);
  }

  @Test
  public void createWithAutoRebuildingDisabled() throws Exception {
    ReviewDb oldDb = db;
    setNotesMigration(true, true);

    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    ChangeNotes oldNotes = notesFactory.create(db, project, id);

    // Make a ReviewDb change behind NoteDb's back.
    Change c = oldDb.changes().get(id);
    assertThat(c.getTopic()).isNull();
    String topic = name("a-topic");
    c.setTopic(topic);
    oldDb.changes().update(Collections.singleton(c));

    c = oldDb.changes().get(c.getId());
    ChangeNotes newNotes = notesFactory.createWithAutoRebuildingDisabled(c, null);
    assertThat(newNotes.getChange().getTopic()).isNotEqualTo(topic);
    assertThat(newNotes.getChange().getTopic()).isEqualTo(oldNotes.getChange().getTopic());
  }

  @Test
  public void rebuildDeletesOldDraftRefs() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    putDraft(user, id, 1, "comment", null);

    Account.Id otherAccountId = new Account.Id(user.getId().get() + 1234);
    String otherDraftRef = refsDraftComments(id, otherAccountId);

    try (Repository repo = repoManager.openRepository(allUsers);
        ObjectInserter ins = repo.newObjectInserter()) {
      ObjectId sha = ins.insert(OBJ_BLOB, "garbage data".getBytes(UTF_8));
      ins.flush();
      RefUpdate ru = repo.updateRef(otherDraftRef);
      ru.setExpectedOldObjectId(ObjectId.zeroId());
      ru.setNewObjectId(sha);
      assertThat(ru.update()).isEqualTo(RefUpdate.Result.NEW);
    }

    checker.rebuildAndCheckChanges(id);

    try (Repository repo = repoManager.openRepository(allUsers)) {
      assertThat(repo.exactRef(otherDraftRef)).isNull();
    }
  }

  @Test
  public void failWhenWritesDisabled() throws Exception {
    setNotesMigration(true, true);

    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    assertChangeUpToDate(true, id);
    assertThat(gApi.changes().id(id.get()).info().topic).isNull();

    // Turning off writes causes failure.
    setNotesMigration(false, true);
    try {
      gApi.changes().id(id.get()).topic(name("a-topic"));
      fail("Expected write to fail");
    } catch (RestApiException e) {
      assertChangesReadOnly(e);
    }

    // Update was not written.
    assertThat(gApi.changes().id(id.get()).info().topic).isNull();
    assertChangeUpToDate(true, id);
  }

  @Test
  public void rebuildWhenWritesDisabledWorksButDoesNotWrite() throws Exception {
    setNotesMigration(true, true);

    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    assertChangeUpToDate(true, id);

    // Make a ReviewDb change behind NoteDb's back and ensure it's detected.
    setNotesMigration(false, false);
    gApi.changes().id(id.get()).topic(name("a-topic"));
    setInvalidNoteDbState(id);
    assertChangeUpToDate(false, id);

    // On next NoteDb read, change is rebuilt in-memory but not stored.
    setNotesMigration(false, true);
    assertThat(gApi.changes().id(id.get()).info().topic).isEqualTo(name("a-topic"));
    assertChangeUpToDate(false, id);

    // Attempting to write directly causes failure.
    try {
      gApi.changes().id(id.get()).topic(name("other-topic"));
      fail("Expected write to fail");
    } catch (RestApiException e) {
      assertChangesReadOnly(e);
    }

    // Update was not written.
    assertThat(gApi.changes().id(id.get()).info().topic).isEqualTo(name("a-topic"));
    assertChangeUpToDate(false, id);
  }

  @Test
  public void rebuildChangeWithNoPatchSets() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    db.changes().beginTransaction(id);
    try {
      db.patchSets().delete(db.patchSets().byChange(id));
      db.commit();
    } finally {
      db.rollback();
    }

    try {
      checker.rebuildAndCheckChanges(id);
      assert_().fail("expected NoPatchSetsException");
    } catch (NoPatchSetsException e) {
      // Expected.
    }

    Change c = db.changes().get(id);
    assertThat(c.getNoteDbState()).isNull();
    checker.assertNoChangeRef(project, id);
  }

  @Test
  public void rebuildChangeWithNoEntitiesOtherThanChange() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    db.changes().beginTransaction(id);
    try {
      db.changeMessages().delete(db.changeMessages().byChange(id));
      db.patchSets().delete(db.patchSets().byChange(id));
      db.patchSetApprovals().delete(db.patchSetApprovals().byChange(id));
      db.patchComments().delete(db.patchComments().byChange(id));
      db.commit();
    } finally {
      db.rollback();
    }

    try {
      checker.rebuildAndCheckChanges(id);
      assert_().fail("expected NoPatchSetsException");
    } catch (NoPatchSetsException e) {
      // Expected.
    }

    Change c = db.changes().get(id);
    assertThat(c.getNoteDbState()).isNull();
    checker.assertNoChangeRef(project, id);
  }

  @Test
  public void rebuildEntitiesCreatedByImpersonation() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    PatchSet.Id psId = new PatchSet.Id(id, 1);
    String prefix = "/changes/" + id + "/revisions/current/";

    // For each of the entities that have a real user field, create one entity
    // without impersonation and one with.
    CommentInput ci = new CommentInput();
    ci.path = Patch.COMMIT_MSG;
    ci.side = Side.REVISION;
    ci.line = 1;
    ci.message = "comment without impersonation";
    ReviewInput ri = new ReviewInput();
    ri.label("Code-Review", -1);
    ri.message = "message without impersonation";
    ri.drafts = DraftHandling.KEEP;
    ri.comments = ImmutableMap.of(ci.path, ImmutableList.of(ci));
    userRestSession.post(prefix + "review", ri).assertOK();

    DraftInput di = new DraftInput();
    di.path = Patch.COMMIT_MSG;
    di.side = Side.REVISION;
    di.line = 1;
    di.message = "draft without impersonation";
    userRestSession.put(prefix + "drafts", di).assertCreated();

    allowRunAs();
    try {
      Header runAs = new BasicHeader("X-Gerrit-RunAs", user.id.toString());
      ci.message = "comment with impersonation";
      ri.message = "message with impersonation";
      ri.label("Code-Review", 1);
      adminRestSession.postWithHeader(prefix + "review", ri, runAs).assertOK();

      di.message = "draft with impersonation";
      adminRestSession.putWithHeader(prefix + "drafts", runAs, di).assertCreated();
    } finally {
      removeRunAs();
    }

    List<ChangeMessage> msgs =
        Ordering.natural()
            .onResultOf(ChangeMessage::getWrittenOn)
            .sortedCopy(db.changeMessages().byChange(id));
    assertThat(msgs).hasSize(3);
    assertThat(msgs.get(1).getMessage()).endsWith("message without impersonation");
    assertThat(msgs.get(1).getAuthor()).isEqualTo(user.id);
    assertThat(msgs.get(1).getRealAuthor()).isEqualTo(user.id);
    assertThat(msgs.get(2).getMessage()).endsWith("message with impersonation");
    assertThat(msgs.get(2).getAuthor()).isEqualTo(user.id);
    assertThat(msgs.get(2).getRealAuthor()).isEqualTo(admin.id);

    List<PatchSetApproval> psas = db.patchSetApprovals().byChange(id).toList();
    assertThat(psas).hasSize(1);
    assertThat(psas.get(0).getLabel()).isEqualTo("Code-Review");
    assertThat(psas.get(0).getValue()).isEqualTo(1);
    assertThat(psas.get(0).getAccountId()).isEqualTo(user.id);
    assertThat(psas.get(0).getRealAccountId()).isEqualTo(admin.id);

    Ordering<PatchLineComment> commentOrder =
        Ordering.natural().onResultOf(PatchLineComment::getWrittenOn);
    List<PatchLineComment> drafts =
        commentOrder.sortedCopy(db.patchComments().draftByPatchSetAuthor(psId, user.id));
    assertThat(drafts).hasSize(2);
    assertThat(drafts.get(0).getMessage()).isEqualTo("draft without impersonation");
    assertThat(drafts.get(0).getAuthor()).isEqualTo(user.id);
    assertThat(drafts.get(0).getRealAuthor()).isEqualTo(user.id);
    assertThat(drafts.get(1).getMessage()).isEqualTo("draft with impersonation");
    assertThat(drafts.get(1).getAuthor()).isEqualTo(user.id);
    assertThat(drafts.get(1).getRealAuthor()).isEqualTo(admin.id);

    List<PatchLineComment> pub =
        commentOrder.sortedCopy(db.patchComments().publishedByPatchSet(psId));
    assertThat(pub).hasSize(2);
    assertThat(pub.get(0).getMessage()).isEqualTo("comment without impersonation");
    assertThat(pub.get(0).getAuthor()).isEqualTo(user.id);
    assertThat(pub.get(0).getRealAuthor()).isEqualTo(user.id);
    assertThat(pub.get(1).getMessage()).isEqualTo("comment with impersonation");
    assertThat(pub.get(1).getAuthor()).isEqualTo(user.id);
    assertThat(pub.get(1).getRealAuthor()).isEqualTo(admin.id);
  }

  @Test
  public void laterEventsDependingOnEarlierPatchSetDontIntefereWithOtherPatchSets()
      throws Exception {
    PushOneCommit.Result r1 = createChange();
    ChangeData cd = r1.getChange();
    Change.Id id = cd.getId();
    amendChange(cd.change().getKey().get());
    TestTimeUtil.incrementClock(90, TimeUnit.DAYS);

    ReviewInput rin = ReviewInput.approve();
    rin.message = "Some very late message on PS1";
    gApi.changes().id(id.get()).revision(1).review(rin);

    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void ignoreChangeMessageBeyondCurrentPatchSet() throws Exception {
    PushOneCommit.Result r = createChange();
    PatchSet.Id psId1 = r.getPatchSetId();
    Change.Id id = psId1.getParentKey();
    gApi.changes().id(id.get()).current().review(ReviewInput.recommend());

    r = amendChange(r.getChangeId());
    PatchSet.Id psId2 = r.getPatchSetId();

    assertThat(db.patchSets().byChange(id)).hasSize(2);
    assertThat(db.changeMessages().byPatchSet(psId2)).hasSize(1);
    db.patchSets().deleteKeys(Collections.singleton(psId2));

    checker.rebuildAndCheckChanges(psId2.getParentKey());
    setNotesMigration(true, true);

    ChangeData cd = changeDataFactory.create(db, project, id);
    assertThat(cd.change().currentPatchSetId()).isEqualTo(psId1);
    assertThat(cd.patchSets().stream().map(ps -> ps.getId()).collect(toList()))
        .containsExactly(psId1);
    PatchSet ps = cd.currentPatchSet();
    assertThat(ps).isNotNull();
    assertThat(ps.getId()).isEqualTo(psId1);
  }

  @Test
  public void highestNumberedPatchSetIsNotCurrent() throws Exception {
    PushOneCommit.Result r1 = createChange();
    PatchSet.Id psId1 = r1.getPatchSetId();
    Change.Id id = psId1.getParentKey();
    PushOneCommit.Result r2 = amendChange(r1.getChangeId());
    PatchSet.Id psId2 = r2.getPatchSetId();

    try (BatchUpdate bu =
        batchUpdateFactory.create(
            db, project, identifiedUserFactory.create(user.getId()), TimeUtil.nowTs())) {
      bu.addOp(
          id,
          new BatchUpdateOp() {
            @Override
            public boolean updateChange(ChangeContext ctx)
                throws PatchSetInfoNotAvailableException {
              ctx.getChange()
                  .setCurrentPatchSet(patchSetInfoFactory.get(ctx.getDb(), ctx.getNotes(), psId1));
              return true;
            }
          });
      bu.execute();
    }
    ChangeNotes notes = notesFactory.create(db, project, id);
    assertThat(psUtil.byChangeAsMap(db, notes).keySet()).containsExactly(psId1, psId2);
    assertThat(notes.getChange().currentPatchSetId()).isEqualTo(psId1);

    assertThat(db.changes().get(id).currentPatchSetId()).isEqualTo(psId1);

    checker.rebuildAndCheckChanges(id);
    setNotesMigration(true, true);

    notes = notesFactory.create(db, project, id);
    assertThat(psUtil.byChangeAsMap(db, notes).keySet()).containsExactly(psId1, psId2);
    assertThat(notes.getChange().currentPatchSetId()).isEqualTo(psId1);
  }

  @Test
  public void resolveCommentsInheritsValueFromParentWhenUnspecified() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    putDraft(user, id, 1, "comment", true);
    putDraft(user, id, 1, "newComment", null);

    Map<String, List<CommentInfo>> comments = gApi.changes().id(id.get()).current().drafts();
    for (List<CommentInfo> cList : comments.values()) {
      for (CommentInfo ci : cList) {
        assertThat(ci.unresolved).isTrue();
      }
    }
  }

  @Test
  public void rebuilderRespectsReadOnlyInNoteDbChangeState() throws Exception {
    TestTimeUtil.resetWithClockStep(1, SECONDS);
    PushOneCommit.Result r = createChange();
    PatchSet.Id psId1 = r.getPatchSetId();
    Change.Id id = psId1.getParentKey();

    checker.rebuildAndCheckChanges(id);
    setNotesMigration(true, true);

    ReviewDb db = getUnwrappedDb();
    Change c = db.changes().get(id);
    NoteDbChangeState state = NoteDbChangeState.parse(c);
    Timestamp until = new Timestamp(TimeUtil.nowMs() + MILLISECONDS.convert(1, DAYS));
    state = state.withReadOnlyUntil(until);
    c.setNoteDbState(state.toString());
    db.changes().update(Collections.singleton(c));

    try {
      rebuilderWrapper.rebuild(db, id);
      fail("expected rebuild to fail");
    } catch (OrmRuntimeException e) {
      assertThat(e.getMessage()).contains("read-only until");
    }

    TestTimeUtil.setClock(new Timestamp(until.getTime() + MILLISECONDS.convert(1, SECONDS)));
    rebuilderWrapper.rebuild(db, id);
  }

  @Test
  public void commitWithCrLineEndings() throws Exception {
    PushOneCommit.Result r =
        createChange("Subject\r\rBody\r", PushOneCommit.FILE_NAME, PushOneCommit.FILE_CONTENT);
    Change c = r.getChange().change();

    // This assertion demonstrates an arguable bug in JGit's commit subject
    // parsing, and shows how this kind of data might have gotten into
    // ReviewDb. If that bug ever gets fixed upstream, this assert may start
    // failing. If that happens, this test can be rewritten to directly set the
    // subject field in ReviewDb.
    assertThat(c.getSubject()).isEqualTo("Subject\r\rBody");

    checker.rebuildAndCheckChanges(c.getId());
  }

  @Test
  public void patchSetsOutOfOrder() throws Exception {
    String id = createChange().getChangeId();
    amendChange(id);
    PushOneCommit.Result r = amendChange(id);

    ChangeData cd = r.getChange();
    PatchSet.Id psId3 = cd.change().currentPatchSetId();
    assertThat(psId3.get()).isEqualTo(3);

    PatchSet ps1 = db.patchSets().get(new PatchSet.Id(cd.getId(), 1));
    PatchSet ps3 = db.patchSets().get(psId3);
    assertThat(ps1.getCreatedOn()).isLessThan(ps3.getCreatedOn());

    // Simulate an old Gerrit bug by setting the created timestamp of the latest
    // patch set ID to the timestamp of PS1.
    ps3.setCreatedOn(ps1.getCreatedOn());
    db.patchSets().update(Collections.singleton(ps3));

    checker.rebuildAndCheckChanges(cd.getId());

    setNotesMigration(true, true);
    cd = changeDataFactory.create(db, project, cd.getId());
    assertThat(cd.change().currentPatchSetId()).isEqualTo(psId3);

    List<PatchSet> patchSets = ImmutableList.copyOf(cd.patchSets());
    assertThat(patchSets).hasSize(3);

    PatchSet newPs1 = patchSets.get(0);
    assertThat(newPs1.getId()).isEqualTo(ps1.getId());
    assertThat(newPs1.getCreatedOn()).isEqualTo(ps1.getCreatedOn());

    PatchSet newPs2 = patchSets.get(1);
    assertThat(newPs2.getCreatedOn()).isGreaterThan(newPs1.getCreatedOn());

    PatchSet newPs3 = patchSets.get(2);
    assertThat(newPs3.getId()).isEqualTo(ps3.getId());
    // Migrated with a newer timestamp than the original, to preserve ordering.
    assertThat(newPs3.getCreatedOn()).isAtLeast(newPs2.getCreatedOn());
    assertThat(newPs3.getCreatedOn()).isGreaterThan(ps1.getCreatedOn());
  }

  @Test
  public void ignoreNoteDbStateWithNoCorrespondingRefWhenWritesAndReadsDisabled() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();
    ReviewDb db = getUnwrappedDb();
    Change c = db.changes().get(id);
    c.setNoteDbState("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    db.changes().update(Collections.singleton(c));
    c = db.changes().get(id);

    String refName = RefNames.changeMetaRef(id);
    assertThat(getMetaRef(project, refName)).isNull();

    ChangeNotes notes = notesFactory.create(dbProvider.get(), project, id);
    assertThat(notes.getChange().getRowVersion()).isEqualTo(c.getRowVersion());

    notes = notesFactory.createChecked(dbProvider.get(), project, id);
    assertThat(notes.getChange().getRowVersion()).isEqualTo(c.getRowVersion());

    assertThat(getMetaRef(project, refName)).isNull();
  }

  @Test
  public void autoRebuildMissingRefWriteOnly() throws Exception {
    setNotesMigration(true, false);
    testAutoRebuildMissingRef();
  }

  @Test
  public void autoRebuildMissingRefReadWrite() throws Exception {
    setNotesMigration(true, true);
    testAutoRebuildMissingRef();
  }

  private void testAutoRebuildMissingRef() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();

    assertChangeUpToDate(true, id);
    notesFactory.createChecked(db, project, id);

    try (Repository repo = repoManager.openRepository(project)) {
      RefUpdate ru = repo.updateRef(RefNames.changeMetaRef(id));
      ru.setForceUpdate(true);
      assertThat(ru.delete()).isEqualTo(RefUpdate.Result.FORCED);
    }
    assertChangeUpToDate(false, id);

    notesFactory.createChecked(db, project, id);
    assertChangeUpToDate(true, id);
  }

  @Test
  public void missingPatchSetCommitOkForCommentsNotOnParentSide() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();

    putDraft(user, id, 1, "draft comment", null, Side.REVISION);
    putComment(user, id, 1, "published comment", null, Side.REVISION);

    ReviewDb db = getUnwrappedDb();
    PatchSet ps = db.patchSets().get(new PatchSet.Id(id, 1));
    ps.setRevision(new RevId("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    db.patchSets().update(Collections.singleton(ps));

    try {
      patchListCache.getOldId(db.changes().get(id), ps, null);
      assert_().fail("Expected PatchListNotAvailableException");
    } catch (PatchListNotAvailableException e) {
      // Expected.
    }

    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void missingPatchSetCommitOmitsCommentsOnParentSide() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();

    CommentInfo draftInfo = putDraft(user, id, 1, "draft comment", null, Side.PARENT);
    putComment(user, id, 1, "published comment", null, Side.PARENT);
    CommentInfo commentInfo =
        gApi.changes()
            .id(id.get())
            .comments()
            .values()
            .stream()
            .flatMap(List::stream)
            .findFirst()
            .get();

    ReviewDb db = getUnwrappedDb();
    PatchSet ps = db.patchSets().get(new PatchSet.Id(id, 1));
    ps.setRevision(new RevId("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    db.patchSets().update(Collections.singleton(ps));

    try {
      patchListCache.getOldId(db.changes().get(id), ps, null);
      assert_().fail("Expected PatchListNotAvailableException");
    } catch (PatchListNotAvailableException e) {
      // Expected.
    }

    checker.rebuildAndCheckChange(
        id,
        Stream.of(draftInfo.id, commentInfo.id)
            .sorted()
            .map(c -> id + ",1," + PushOneCommit.FILE_NAME + "," + c)
            .collect(
                joining(", ", "PatchLineComment.Key sets differ: [", "] only in A; [] only in B")));
  }

  private void assertChangesReadOnly(RestApiException e) throws Exception {
    Throwable cause = e.getCause();
    assertThat(cause).isInstanceOf(UpdateException.class);
    assertThat(cause.getCause()).isInstanceOf(OrmException.class);
    assertThat(cause.getCause()).hasMessageThat().isEqualTo(NoteDbUpdateManager.CHANGES_READ_ONLY);
  }

  private void setInvalidNoteDbState(Change.Id id) throws Exception {
    ReviewDb db = getUnwrappedDb();
    Change c = db.changes().get(id);
    // In reality we would have NoteDb writes enabled, which would write a real
    // state into this field. For tests however, we turn NoteDb writes off, so
    // just use a dummy state to force ChangeNotes to view the notes as
    // out-of-date.
    c.setNoteDbState("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    db.changes().update(Collections.singleton(c));
  }

  private void assertChangeUpToDate(boolean expected, Change.Id id) throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      Change c = getUnwrappedDb().changes().get(id);
      assertThat(c).isNotNull();
      assertThat(c.getNoteDbState()).isNotNull();
      NoteDbChangeState state = NoteDbChangeState.parse(c);
      assertThat(state).isNotNull();
      assertThat(state.getPrimaryStorage()).isEqualTo(PrimaryStorage.REVIEW_DB);
      assertThat(state.isChangeUpToDate(new RepoRefCache(repo))).isEqualTo(expected);
    }
  }

  private void assertDraftsUpToDate(boolean expected, Change.Id changeId, TestAccount account)
      throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      Change c = getUnwrappedDb().changes().get(changeId);
      assertThat(c).isNotNull();
      assertThat(c.getNoteDbState()).isNotNull();
      NoteDbChangeState state = NoteDbChangeState.parse(c);
      assertThat(state.areDraftsUpToDate(new RepoRefCache(repo), account.getId()))
          .isEqualTo(expected);
    }
  }

  private ObjectId getMetaRef(Project.NameKey p, String name) throws Exception {
    try (Repository repo = repoManager.openRepository(p)) {
      Ref ref = repo.exactRef(name);
      return ref != null ? ref.getObjectId() : null;
    }
  }

  private CommentInfo putDraft(
      TestAccount account, Change.Id id, int line, String msg, Boolean unresolved)
      throws Exception {
    return putDraft(account, id, line, msg, unresolved, Side.REVISION);
  }

  private CommentInfo putDraft(
      TestAccount account, Change.Id id, int line, String msg, Boolean unresolved, Side side)
      throws Exception {
    DraftInput in = new DraftInput();
    in.side = side;
    in.line = line;
    in.message = msg;
    in.path = PushOneCommit.FILE_NAME;
    in.unresolved = unresolved;
    AcceptanceTestRequestScope.Context old = setApiUser(account);
    try {
      return gApi.changes().id(id.get()).current().createDraft(in).get();
    } finally {
      atrScope.set(old);
    }
  }

  private void putComment(TestAccount account, Change.Id id, int line, String msg, String inReplyTo)
      throws Exception {
    putComment(account, id, line, msg, inReplyTo, Side.REVISION);
  }

  private void putComment(
      TestAccount account, Change.Id id, int line, String msg, String inReplyTo, Side side)
      throws Exception {
    CommentInput in = new CommentInput();
    in.side = side;
    in.line = line;
    in.message = msg;
    in.inReplyTo = inReplyTo;
    ReviewInput rin = new ReviewInput();
    rin.comments = new HashMap<>();
    rin.comments.put(PushOneCommit.FILE_NAME, ImmutableList.of(in));
    rin.drafts = ReviewInput.DraftHandling.KEEP;
    AcceptanceTestRequestScope.Context old = setApiUser(account);
    try {
      gApi.changes().id(id.get()).current().review(rin);
    } finally {
      atrScope.set(old);
    }
  }

  private void publishDrafts(TestAccount account, Change.Id id) throws Exception {
    ReviewInput rin = new ReviewInput();
    rin.drafts = ReviewInput.DraftHandling.PUBLISH_ALL_REVISIONS;
    AcceptanceTestRequestScope.Context old = setApiUser(account);
    try {
      gApi.changes().id(id.get()).current().review(rin);
    } finally {
      atrScope.set(old);
    }
  }

  private ChangeMessage insertMessage(
      Change.Id id, PatchSet.Id psId, Account.Id author, Timestamp ts, String message)
      throws Exception {
    ChangeMessage msg =
        new ChangeMessage(new ChangeMessage.Key(id, ChangeUtil.messageUuid()), author, ts, psId);
    msg.setMessage(message);
    db.changeMessages().insert(Collections.singleton(msg));

    Change c = db.changes().get(id);
    if (ts.compareTo(c.getLastUpdatedOn()) > 0) {
      c.setLastUpdatedOn(ts);
      db.changes().update(Collections.singleton(c));
    }

    return msg;
  }

  private ReviewDb getUnwrappedDb() {
    ReviewDb db = dbProvider.get();
    return ReviewDbUtil.unwrapDb(db);
  }

  private void allowRunAs() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    Util.allow(
        cfg, GlobalCapability.RUN_AS, systemGroupBackend.getGroup(REGISTERED_USERS).getUUID());
    saveProjectConfig(allProjects, cfg);
  }

  private void removeRunAs() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    Util.remove(
        cfg, GlobalCapability.RUN_AS, systemGroupBackend.getGroup(REGISTERED_USERS).getUUID());
    saveProjectConfig(allProjects, cfg);
  }

  private Map<String, List<CommentInfo>> getPublishedComments(Change.Id id) throws Exception {
    return gApi.changes().id(id.get()).current().comments();
  }
}
