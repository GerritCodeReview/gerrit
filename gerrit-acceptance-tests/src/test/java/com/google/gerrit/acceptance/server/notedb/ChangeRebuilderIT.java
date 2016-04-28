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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.change.Rebuild;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.notedb.ChangeBundle;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NoteDbChangeState;
import com.google.gerrit.server.schema.DisabledChangesReviewDbWrapper;
import com.google.gerrit.testutil.NoteDbChecker;
import com.google.gerrit.testutil.NoteDbMode;
import com.google.gerrit.testutil.TestChanges;
import com.google.gerrit.testutil.TestTimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
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

  @Before
  public void setUp() {
    assume().that(NoteDbMode.readWrite()).isFalse();
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
    notesMigration.setAllEnabled(false);
  }

  @After
  public void tearDown() {
    TestTimeUtil.useSystemTime();
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

    notesMigration.setWriteChanges(true);
    notesMigration.setReadChanges(true);

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

    notesMigration.setWriteChanges(true);
    gApi.changes().id(id.get()).topic(name("a-topic"));

    // First write doesn't create the ref, but rebuilding works.
    checker.assertNoChangeRef(project, id);
    assertThat(unwrapDb().changes().get(id).getNoteDbState()).isNull();
    checker.rebuildAndCheckChanges(id);

    // Now that there is a ref, writes are "turned on" for this change, and
    // NoteDb stays up to date without explicit rebuilding.
    gApi.changes().id(id.get()).topic(name("new-topic"));
    assertThat(unwrapDb().changes().get(id).getNoteDbState()).isNotNull();
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
    notesMigration.setWriteChanges(true);

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

    notesMigration.setWriteChanges(true);
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
    notesMigration.setAllEnabled(true);
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();

    ObjectId changeMetaId = getMetaRef(
        project, ChangeNoteUtil.changeRefName(id));
    assertThat(unwrapDb().changes().get(id).getNoteDbState()).isEqualTo(
        changeMetaId.name());

    putDraft(user, id, 1, "comment by user");
    ObjectId userDraftsId = getMetaRef(
        allUsers, RefNames.refsDraftComments(user.getId(), id));
    assertThat(unwrapDb().changes().get(id).getNoteDbState()).isEqualTo(
        changeMetaId.name()
        + "," + user.getId() + "=" + userDraftsId.name());

    putDraft(admin, id, 2, "comment by admin");
    ObjectId adminDraftsId = getMetaRef(
        allUsers, RefNames.refsDraftComments(admin.getId(), id));
    assertThat(admin.getId().get()).isLessThan(user.getId().get());
    assertThat(unwrapDb().changes().get(id).getNoteDbState()).isEqualTo(
        changeMetaId.name()
        + "," + admin.getId() + "=" + adminDraftsId.name()
        + "," + user.getId() + "=" + userDraftsId.name());

    putDraft(admin, id, 2, "revised comment by admin");
    adminDraftsId = getMetaRef(
        allUsers, RefNames.refsDraftComments(admin.getId(), id));
    assertThat(unwrapDb().changes().get(id).getNoteDbState()).isEqualTo(
        changeMetaId.name()
        + "," + admin.getId() + "=" + adminDraftsId.name()
        + "," + user.getId() + "=" + userDraftsId.name());
  }

  @Test
  public void rebuildAutomaticallyWhenChangeOutOfDate() throws Exception {
    notesMigration.setAllEnabled(true);

    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    assertChangeUpToDate(true, id);

    // Make a ReviewDb change behind NoteDb's back and ensure it's detected.
    notesMigration.setAllEnabled(false);
    gApi.changes().id(id.get()).topic(name("a-topic"));
    setInvalidNoteDbState(id);
    assertChangeUpToDate(false, id);

    // On next NoteDb read, the change is transparently rebuilt.
    notesMigration.setAllEnabled(true);
    assertThat(gApi.changes().id(id.get()).info().topic)
        .isEqualTo(name("a-topic"));
    assertChangeUpToDate(true, id);

    // Check that the bundles are equal.
    ChangeBundle actual = ChangeBundle.fromNotes(
        plcUtil, notesFactory.create(dbProvider.get(), project, id));
    ChangeBundle expected = ChangeBundle.fromReviewDb(unwrapDb(), id);
    assertThat(actual.differencesFrom(expected)).isEmpty();
  }

  @Test
  public void rebuildAutomaticallyWhenDraftsOutOfDate() throws Exception {
    notesMigration.setAllEnabled(true);
    setApiUser(user);

    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    putDraft(user, id, 1, "comment");
    assertDraftsUpToDate(true, id, user);

    // Make a ReviewDb change behind NoteDb's back and ensure it's detected.
    notesMigration.setAllEnabled(false);
    putDraft(user, id, 1, "comment");
    setInvalidNoteDbState(id);
    assertDraftsUpToDate(false, id, user);

    // On next NoteDb read, the drafts are transparently rebuilt.
    notesMigration.setAllEnabled(true);
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

    notesMigration.setWriteChanges(true);
    notesMigration.setReadChanges(true);

    // Rebuild and check was successful, but NoteDb doesn't support storing an
    // empty topic, so it comes out as null.
    ChangeNotes notes = notesFactory.create(db, project, id);
    assertThat(notes.getChange().getTopic()).isNull();
  }

  private void setInvalidNoteDbState(Change.Id id) throws Exception {
    ReviewDb db = unwrapDb();
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
      Change c = unwrapDb().changes().get(id);
      assertThat(c).isNotNull();
      assertThat(c.getNoteDbState()).isNotNull();
      assertThat(NoteDbChangeState.parse(c).isChangeUpToDate(repo))
          .isEqualTo(expected);
    }
  }

  private void assertDraftsUpToDate(boolean expected, Change.Id changeId,
      TestAccount account) throws Exception {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      Change c = unwrapDb().changes().get(changeId);
      assertThat(c).isNotNull();
      assertThat(c.getNoteDbState()).isNotNull();
      NoteDbChangeState state = NoteDbChangeState.parse(c);
      assertThat(state.areDraftsUpToDate(repo, account.getId()))
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

  private ReviewDb unwrapDb() {
    ReviewDb db = dbProvider.get();
    if (db instanceof DisabledChangesReviewDbWrapper) {
      db = ((DisabledChangesReviewDbWrapper) db).unsafeGetDelegate();
    }
    return db;
  }
}
