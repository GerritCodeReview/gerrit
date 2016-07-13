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
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.reviewdb.client.RefNames.changeMetaRef;
import static com.google.gerrit.reviewdb.client.RefNames.refsDraftComments;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.fail;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.Rebuild;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.RepoRefCache;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.notedb.ChangeBundle;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NoteDbChangeState;
import com.google.gerrit.server.notedb.NoteDbUpdateManager;
import com.google.gerrit.server.notedb.TestChangeRebuilderWrapper;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gerrit.testutil.NoteDbChecker;
import com.google.gerrit.testutil.NoteDbMode;
import com.google.gerrit.testutil.TestChanges;
import com.google.gerrit.testutil.TestTimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ChangeRebuilderIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setBoolean("noteDb", null, "testRebuilderWrapper", true);
    return cfg;
  }

  @Inject
  private AllUsersName allUsers;

  @Inject
  private NoteDbChecker checker;

  @Inject
  private Rebuild rebuildHandler;

  @Inject
  private Provider<ReviewDb> dbProvider;

  @Inject
  private PatchLineCommentsUtil plcUtil;

  @Inject
  private Provider<PostReview> postReview;

  @Inject
  private TestChangeRebuilderWrapper rebuilderWrapper;

  @Inject
  private BatchUpdate.Factory batchUpdateFactory;

  @Before
  public void setUp() {
    assume().that(NoteDbMode.readWrite()).isFalse();
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
    setNotesMigration(false, false);
  }

  @After
  public void tearDown() {
    TestTimeUtil.useSystemTime();
  }

  private void setNotesMigration(boolean writeChanges, boolean readChanges) {
    notesMigration.setWriteChanges(writeChanges);
    notesMigration.setReadChanges(readChanges);
    db = atrScope.reopenDb().getReviewDbProvider().get();
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
    putComment(user, id, 1, "comment");
    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void patchSetWithNullGroups() throws Exception {
    Timestamp ts = TimeUtil.nowTs();
    @SuppressWarnings("deprecation")
    Change c = TestChanges.newChange(project, user.getId(), db.nextChangeId());
    c.setCreatedOn(ts);
    c.setLastUpdatedOn(ts);
    PatchSet ps = TestChanges.newPatchSet(
        c.currentPatchSetId(), "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
        user.getId());
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
    putDraft(user, id, 1, "comment");
    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void draftAndPublishedComment() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    putDraft(user, id, 1, "draft comment");
    putComment(user, id, 1, "published comment");
    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void publishDraftComment() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    putDraft(user, id, 1, "draft comment");
    publishDrafts(user, id);
    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void nullAccountId() throws Exception {
    PushOneCommit.Result r = createChange();
    PatchSet.Id psId = r.getPatchSetId();
    Change.Id id = psId.getParentKey();

    // Events need to be otherwise identical for the account ID to be compared.
    ChangeMessage msg1 =
        insertMessage(id, psId, user.getId(), TimeUtil.nowTs(), "message 1");
    insertMessage(id, psId, null, msg1.getWrittenOn(), "message 2");

    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void nullPatchSetId() throws Exception {
    PushOneCommit.Result r = createChange();
    PatchSet.Id psId1 = r.getPatchSetId();
    Change.Id id = psId1.getParentKey();

    // Events need to be otherwise identical for the PatchSet.ID to be compared.
    ChangeMessage msg1 =
        insertMessage(id, null, user.getId(), TimeUtil.nowTs(), "message 1");
    insertMessage(id, null, user.getId(), msg1.getWrittenOn(), "message 2");

    PatchSet.Id psId2 = amendChange(r.getChangeId()).getPatchSetId();

    ChangeMessage msg3 =
        insertMessage(id, null, user.getId(), TimeUtil.nowTs(), "message 3");
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
    rebuildHandler.apply(
        parseChangeResource(r.getChangeId()),
        new Rebuild.Input());
  }

  @Test
  public void rebuildViaRestApi() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    setNotesMigration(true, false);

    checker.assertNoChangeRef(project, id);
    rebuildHandler.apply(
        parseChangeResource(r.getChangeId()),
        new Rebuild.Input());
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
    assertThat(getUnwrappedDb().changes().get(id).getNoteDbState()).isEqualTo(
        changeMetaId.name());

    putDraft(user, id, 1, "comment by user");
    ObjectId userDraftsId = getMetaRef(
        allUsers, refsDraftComments(id, user.getId()));
    assertThat(getUnwrappedDb().changes().get(id).getNoteDbState()).isEqualTo(
        changeMetaId.name()
        + "," + user.getId() + "=" + userDraftsId.name());

    putDraft(admin, id, 2, "comment by admin");
    ObjectId adminDraftsId = getMetaRef(
        allUsers, refsDraftComments(id, admin.getId()));
    assertThat(admin.getId().get()).isLessThan(user.getId().get());
    assertThat(getUnwrappedDb().changes().get(id).getNoteDbState()).isEqualTo(
        changeMetaId.name()
        + "," + admin.getId() + "=" + adminDraftsId.name()
        + "," + user.getId() + "=" + userDraftsId.name());

    putDraft(admin, id, 2, "revised comment by admin");
    adminDraftsId = getMetaRef(
        allUsers, refsDraftComments(id, admin.getId()));
    assertThat(getUnwrappedDb().changes().get(id).getNoteDbState()).isEqualTo(
        changeMetaId.name()
        + "," + admin.getId() + "=" + adminDraftsId.name()
        + "," + user.getId() + "=" + userDraftsId.name());
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
    assertThat(gApi.changes().id(id.get()).info().topic)
        .isEqualTo(name("a-topic"));
    assertChangeUpToDate(true, id);

    // Check that the bundles are equal.
    ChangeBundle actual = ChangeBundle.fromNotes(
        plcUtil, notesFactory.create(dbProvider.get(), project, id));
    ChangeBundle expected = ChangeBundle.fromReviewDb(getUnwrappedDb(), id);
    assertThat(actual.differencesFrom(expected)).isEmpty();
  }

  @Test
  public void rebuildAutomaticallyWithinBatchUpdate() throws Exception {
    setNotesMigration(true, true);

    PushOneCommit.Result r = createChange();
    final Change.Id id = r.getPatchSetId().getParentKey();
    assertChangeUpToDate(true, id);

    // Make a ReviewDb change behind NoteDb's back and ensure it's detected.
    setNotesMigration(false, false);
    gApi.changes().id(id.get()).topic(name("a-topic"));
    setInvalidNoteDbState(id);
    assertChangeUpToDate(false, id);

    // Next NoteDb read comes inside the transaction started by BatchUpdate. In
    // reality this could be caused by a failed update happening between when
    // the change is parsed by ChangesCollection and when the BatchUpdate
    // executes. We simulate it here by using BatchUpdate directly and not going
    // through an API handler.
    setNotesMigration(true, true);
    final String msg = "message from BatchUpdate";
    try (BatchUpdate bu = batchUpdateFactory.create(db, project,
          identifiedUserFactory.create(user.getId()), TimeUtil.nowTs())) {
      bu.addOp(id, new BatchUpdate.Op() {
        @Override
        public boolean updateChange(ChangeContext ctx) throws OrmException {
          PatchSet.Id psId = ctx.getChange().currentPatchSetId();
          ChangeMessage cm = new ChangeMessage(
              new ChangeMessage.Key(id, ChangeUtil.messageUUID(ctx.getDb())),
                  ctx.getAccountId(), ctx.getWhen(), psId);
          cm.setMessage(msg);
          ctx.getDb().changeMessages().insert(Collections.singleton(cm));
          ctx.getUpdate(psId).setChangeMessage(msg);
          return true;
        }
      });
      bu.execute();
    }
    // As an implementation detail, change wasn't actually rebuilt inside the
    // BatchUpdate transaction, but it was rebuilt during read for the
    // subsequent reindex. Thus it's impossible to actually observe an
    // out-of-date state in the caller.
    assertChangeUpToDate(true, id);

    // Check that the bundles are equal.
    ChangeNotes notes = notesFactory.create(dbProvider.get(), project, id);
    ChangeBundle actual = ChangeBundle.fromNotes(plcUtil, notes);
    ChangeBundle expected = ChangeBundle.fromReviewDb(getUnwrappedDb(), id);
    assertThat(actual.differencesFrom(expected)).isEmpty();
    assertThat(
            Iterables.transform(
                notes.getChangeMessages(),
                new Function<ChangeMessage, String>() {
                  @Override
                  public String apply(ChangeMessage in) {
                    return in.getMessage();
                  }
                }))
        .contains(msg);
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
    assertThat(gApi.changes().id(id.get()).info().topic)
        .isEqualTo(name("a-topic"));
    assertChangeUpToDate(true, id);

    // Check that the bundles are equal.
    ChangeBundle actual = ChangeBundle.fromNotes(
        plcUtil, notesFactory.create(dbProvider.get(), project, id));
    ChangeBundle expected = ChangeBundle.fromReviewDb(getUnwrappedDb(), id);
    assertThat(actual.differencesFrom(expected)).isEmpty();
  }

  @Test
  public void rebuildReturnsCorrectResultEvenIfSavingToNoteDbFailed()
      throws Exception {
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
    ChangeBundle actual = ChangeBundle.fromNotes(plcUtil, notes);
    ChangeBundle expected = ChangeBundle.fromReviewDb(getUnwrappedDb(), id);
    assertThat(actual.differencesFrom(expected)).isEmpty();
    assertChangeUpToDate(false, id);

    // Another rebuild attempt succeeds
    notesFactory.create(dbProvider.get(), project, id);
    assertThat(getMetaRef(project, changeMetaRef(id))).isNotEqualTo(oldMetaId);
    assertChangeUpToDate(true, id);
  }

  @Test
  public void rebuildReturnsDraftResultWhenRebuildingInChangeNotesFails()
      throws Exception {
    setNotesMigration(true, true);

    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    putDraft(user, id, 1, "comment by user");
    assertChangeUpToDate(true, id);

    ObjectId oldMetaId =
        getMetaRef(allUsers, refsDraftComments(id, user.getId()));

    // Add a draft behind NoteDb's back.
    setNotesMigration(false, false);
    putDraft(user, id, 1, "second comment by user");
    setInvalidNoteDbState(id);
    assertDraftsUpToDate(false, id, user);
    assertThat(getMetaRef(allUsers, refsDraftComments(id, user.getId())))
        .isEqualTo(oldMetaId);

    // Force the next rebuild attempt to fail (in ChangeNotes).
    rebuilderWrapper.failNextUpdate();
    setNotesMigration(true, true);
    ChangeNotes notes = notesFactory.create(dbProvider.get(), project, id);
    notes.getDraftComments(user.getId());
    assertThat(getMetaRef(allUsers, refsDraftComments(id, user.getId())))
        .isEqualTo(oldMetaId);

    // Not up to date, but the actual returned state matches anyway.
    assertDraftsUpToDate(false, id, user);
    ChangeBundle actual = ChangeBundle.fromNotes(plcUtil, notes);
    ChangeBundle expected = ChangeBundle.fromReviewDb(getUnwrappedDb(), id);
    assertThat(actual.differencesFrom(expected)).isEmpty();

    // Another rebuild attempt succeeds
    notesFactory.create(dbProvider.get(), project, id);
    assertChangeUpToDate(true, id);
    assertDraftsUpToDate(true, id, user);
    assertThat(getMetaRef(allUsers, refsDraftComments(id, user.getId())))
        .isNotEqualTo(oldMetaId);
  }

  @Test
  public void rebuildReturnsDraftResultWhenRebuildingInDraftCommentNotesFails()
      throws Exception {
    setNotesMigration(true, true);

    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    putDraft(user, id, 1, "comment by user");
    assertChangeUpToDate(true, id);

    ObjectId oldMetaId =
        getMetaRef(allUsers, refsDraftComments(id, user.getId()));

    // Add a draft behind NoteDb's back.
    setNotesMigration(false, false);
    putDraft(user, id, 1, "second comment by user");

    ReviewDb db = getUnwrappedDb();
    Change c = db.changes().get(id);
    // Leave change meta ID alone so DraftCommentNotes does the rebuild.
    NoteDbChangeState bogusState = new NoteDbChangeState(
        id, NoteDbChangeState.parse(c).getChangeMetaId(),
        ImmutableMap.<Account.Id, ObjectId>of(
            user.getId(),
            ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef")));
    c.setNoteDbState(bogusState.toString());
    db.changes().update(Collections.singleton(c));

    assertDraftsUpToDate(false, id, user);
    assertThat(getMetaRef(allUsers, refsDraftComments(id, user.getId())))
        .isEqualTo(oldMetaId);

    // Force the next rebuild attempt to fail (in DraftCommentNotes).
    rebuilderWrapper.failNextUpdate();
    setNotesMigration(true, true);
    ChangeNotes notes = notesFactory.create(dbProvider.get(), project, id);
    notes.getDraftComments(user.getId());
    assertThat(getMetaRef(allUsers, refsDraftComments(id, user.getId())))
        .isEqualTo(oldMetaId);

    // Not up to date, but the actual returned state matches anyway.
    assertChangeUpToDate(true, id);
    assertDraftsUpToDate(false, id, user);
    ChangeBundle actual = ChangeBundle.fromNotes(plcUtil, notes);
    ChangeBundle expected = ChangeBundle.fromReviewDb(getUnwrappedDb(), id);
    assertThat(actual.differencesFrom(expected)).isEmpty();

    // Another rebuild attempt succeeds
    notesFactory.create(dbProvider.get(), project, id)
        .getDraftComments(user.getId());
    assertChangeUpToDate(true, id);
    assertDraftsUpToDate(true, id, user);
    assertThat(getMetaRef(allUsers, refsDraftComments(id, user.getId())))
        .isNotEqualTo(oldMetaId);
  }

  @Test
  public void rebuildAutomaticallyWhenDraftsOutOfDate() throws Exception {
    setNotesMigration(true, true);
    setApiUser(user);

    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    putDraft(user, id, 1, "comment");
    assertDraftsUpToDate(true, id, user);

    // Make a ReviewDb change behind NoteDb's back and ensure it's detected.
    setNotesMigration(false, false);
    putDraft(user, id, 1, "comment");
    setInvalidNoteDbState(id);
    assertDraftsUpToDate(false, id, user);

    // On next NoteDb read, the drafts are transparently rebuilt.
    setNotesMigration(true, true);
    assertThat(gApi.changes().id(id.get()).current().drafts())
        .containsKey(PushOneCommit.FILE_NAME);
    assertDraftsUpToDate(true, id, user);
  }

  @Test
  public void pushCert() throws Exception {
    // We don't have the code in our test harness to do signed pushes, so just
    // use a hard-coded cert. This cert was actually generated by C git 2.2.0
    // (albeit not for sending to Gerrit).
    String cert = "certificate version 0.1\n"
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
    RevisionResource revRsrc = parseCurrentRevisionResource(r.getChangeId());
    postReview.get().apply(revRsrc, rin, ts);

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
    postReview.get().apply(revRsrc, rin, ts);

    checker.rebuildAndCheckChanges(id);
  }

  @Test
  public void noteDbUsesOriginalSubjectFromPatchSetAndIgnoresChangeField()
      throws Exception {
    PushOneCommit.Result r = createChange();
    String orig = r.getChange().change().getSubject();
    r = pushFactory.create(
            db, admin.getIdent(), testRepo, orig + " v2",
            PushOneCommit.FILE_NAME, "new contents", r.getChangeId())
        .to("refs/heads/master");
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
  public void deleteDraftPS1WithNoOtherEntities() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r = push.to("refs/drafts/master");
    push = pushFactory.create(db, admin.getIdent(), testRepo,
        PushOneCommit.SUBJECT, "b.txt", "4711", r.getChangeId());
    r = push.to("refs/drafts/master");
    PatchSet.Id psId = r.getPatchSetId();
    Change.Id id = psId.getParentKey();

    gApi.changes().id(r.getChangeId()).revision(1).delete();

    checker.rebuildAndCheckChanges(id);

    setNotesMigration(true, true);
    ChangeNotes notes = notesFactory.create(db, project, id);
    assertThat(notes.getPatchSets().keySet()).containsExactly(psId);
  }

  @Test
  public void ignorePatchLineCommentsOnPatchSet0() throws Exception {
    PushOneCommit.Result r = createChange();
    Change change = r.getChange().change();
    Change.Id id = change.getId();

    PatchLineComment comment = new PatchLineComment(
        new PatchLineComment.Key(
            new Patch.Key(new PatchSet.Id(id, 0), PushOneCommit.FILE_NAME),
            "uuid"),
        0, user.getId(), null, TimeUtil.nowTs());
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
  public void skipPatchSetsGreaterThanCurrentPatchSet() throws Exception {
    PushOneCommit.Result r = createChange();
    Change change = r.getChange().change();
    Change.Id id = change.getId();

    PatchSet badPs =
        new PatchSet(new PatchSet.Id(id, change.currentPatchSetId().get() + 1));
    badPs.setCreatedOn(TimeUtil.nowTs());
    badPs.setUploader(new Account.Id(12345));
    badPs.setRevision(new RevId("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
    db.patchSets().insert(Collections.singleton(badPs));
    indexer.index(db, change.getProject(), id);

    checker.rebuildAndCheckChanges(id);

    setNotesMigration(true, true);
    ChangeNotes notes = notesFactory.create(db, project, id);
    assertThat(notes.getPatchSets().keySet())
        .containsExactly(change.currentPatchSetId());
  }

  @Test
  public void leadingSpacesInSubject() throws Exception {
    String subj = "   " + PushOneCommit.SUBJECT;
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo,
        subj, PushOneCommit.FILE_NAME, PushOneCommit.FILE_CONTENT);
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
    ChangeNotes newNotes =
        notesFactory.createWithAutoRebuildingDisabled(c, null);
    assertThat(newNotes.getChange().getTopic()).isNotEqualTo(topic);
    assertThat(newNotes.getChange().getTopic())
        .isEqualTo(oldNotes.getChange().getTopic());
  }

  @Test
  public void rebuildDeletesOldDraftRefs() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    putDraft(user, id, 1, "comment");

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
    assertThat(gApi.changes().id(id.get()).info().topic)
        .isEqualTo(name("a-topic"));
    assertChangeUpToDate(false, id);

    // Attempting to write directly causes failure.
    try {
      gApi.changes().id(id.get()).topic(name("other-topic"));
      fail("Expected write to fail");
    } catch (RestApiException e) {
      assertChangesReadOnly(e);
    }

    // Update was not written.
    assertThat(gApi.changes().id(id.get()).info().topic)
        .isEqualTo(name("a-topic"));
    assertChangeUpToDate(false, id);
  }

  private void assertChangesReadOnly(RestApiException e) throws Exception {
    Throwable cause = e.getCause();
    assertThat(cause).isInstanceOf(UpdateException.class);
    assertThat(cause.getCause()).isInstanceOf(OrmException.class);
    assertThat(cause.getCause())
        .hasMessage(NoteDbUpdateManager.CHANGES_READ_ONLY);
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

  private void assertChangeUpToDate(boolean expected, Change.Id id)
      throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      Change c = getUnwrappedDb().changes().get(id);
      assertThat(c).isNotNull();
      assertThat(c.getNoteDbState()).isNotNull();
      assertThat(NoteDbChangeState.parse(c).isChangeUpToDate(
              new RepoRefCache(repo)))
          .isEqualTo(expected);
    }
  }

  private void assertDraftsUpToDate(boolean expected, Change.Id changeId,
      TestAccount account) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      Change c = getUnwrappedDb().changes().get(changeId);
      assertThat(c).isNotNull();
      assertThat(c.getNoteDbState()).isNotNull();
      NoteDbChangeState state = NoteDbChangeState.parse(c);
      assertThat(state.areDraftsUpToDate(
              new RepoRefCache(repo), account.getId()))
          .isEqualTo(expected);
    }
  }

  private ObjectId getMetaRef(Project.NameKey p, String name) throws Exception {
    try (Repository repo = repoManager.openRepository(p)) {
      Ref ref = repo.exactRef(name);
      return ref != null ? ref.getObjectId() : null;
    }
  }

  private void putDraft(TestAccount account, Change.Id id, int line, String msg)
      throws Exception {
    DraftInput in = new DraftInput();
    in.line = line;
    in.message = msg;
    in.path = PushOneCommit.FILE_NAME;
    AcceptanceTestRequestScope.Context old = setApiUser(account);
    try {
      gApi.changes().id(id.get()).current().createDraft(in);
    } finally {
      atrScope.set(old);
    }
  }

  private void putComment(TestAccount account, Change.Id id, int line, String msg)
      throws Exception {
    CommentInput in = new CommentInput();
    in.line = line;
    in.message = msg;
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

  private void publishDrafts(TestAccount account, Change.Id id)
      throws Exception {
    ReviewInput rin = new ReviewInput();
    rin.drafts = ReviewInput.DraftHandling.PUBLISH_ALL_REVISIONS;
    AcceptanceTestRequestScope.Context old = setApiUser(account);
    try {
      gApi.changes().id(id.get()).current().review(rin);
    } finally {
      atrScope.set(old);
    }
  }

  private ChangeMessage insertMessage(Change.Id id, PatchSet.Id psId,
      Account.Id author, Timestamp ts, String message) throws Exception {
    ChangeMessage msg = new ChangeMessage(
        new ChangeMessage.Key(id, ChangeUtil.messageUUID(db)),
        author, ts, psId);
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
}
