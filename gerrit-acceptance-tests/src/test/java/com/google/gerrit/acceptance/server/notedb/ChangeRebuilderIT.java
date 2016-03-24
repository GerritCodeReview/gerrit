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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.change.Rebuild;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.notedb.ChangeBundle;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.notedb.NoteDbChangeState;
import com.google.gerrit.server.schema.DisabledChangesReviewDbWrapper;
import com.google.gerrit.testutil.NoteDbChecker;
import com.google.gerrit.testutil.NoteDbMode;
import com.google.gerrit.testutil.TestTimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
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
  public void noWriteToNewRef() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    checker.assertNoChangeRef(project, id);

    notesMigration.setWriteChanges(true);
    gApi.changes().id(id.get()).topic(name("a-topic"));

    // First write doesn't create the ref, but rebuilding works.
    checker.assertNoChangeRef(project, id);
    assertThat(db.changes().get(id).getNoteDbState()).isNull();
    checker.rebuildAndCheckChanges(id);

    // Now that there is a ref, writes are "turned on" for this change, and
    // NoteDb stays up to date without explicit rebuilding.
    gApi.changes().id(id.get()).topic(name("new-topic"));
    assertThat(db.changes().get(id).getNoteDbState()).isNotNull();
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

    DraftInput in = new DraftInput();
    in.line = 1;
    in.message = "comment by user";
    in.path = PushOneCommit.FILE_NAME;
    setApiUser(user);
    gApi.changes().id(id.get()).current().createDraft(in);

    ObjectId userDraftsId = getMetaRef(
        allUsers, RefNames.refsDraftComments(user.getId(), id));
    assertThat(unwrapDb().changes().get(id).getNoteDbState()).isEqualTo(
        changeMetaId.name()
        + "," + user.getId() + "=" + userDraftsId.name());

    in = new DraftInput();
    in.line = 2;
    in.message = "comment by admin";
    in.path = PushOneCommit.FILE_NAME;
    setApiUser(admin);
    gApi.changes().id(id.get()).current().createDraft(in);

    ObjectId adminDraftsId = getMetaRef(
        allUsers, RefNames.refsDraftComments(admin.getId(), id));
    assertThat(admin.getId().get()).isLessThan(user.getId().get());
    assertThat(unwrapDb().changes().get(id).getNoteDbState()).isEqualTo(
        changeMetaId.name()
        + "," + admin.getId() + "=" + adminDraftsId.name()
        + "," + user.getId() + "=" + userDraftsId.name());

    in.message = "revised comment by admin";
    gApi.changes().id(id.get()).current().createDraft(in);

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
    assertUpToDate(true, id);

    // Make a ReviewDb change behind NoteDb's back and ensure it's detected.
    notesMigration.setAllEnabled(false);
    gApi.changes().id(id.get()).topic(name("a-topic"));
    setInvalidNoteDbState(id);
    assertUpToDate(false, id);

    // On next NoteDb read, the change is transparently rebuilt.
    notesMigration.setAllEnabled(true);
    assertThat(gApi.changes().id(id.get()).info().topic)
        .isEqualTo(name("a-topic"));
    assertUpToDate(true, id);

    // Check that the bundles are equal.
    ChangeBundle actual = ChangeBundle.fromNotes(
        plcUtil, notesFactory.create(dbProvider.get(), project, id));
    ChangeBundle expected = ChangeBundle.fromReviewDb(unwrapDb(), id);
    assertThat(actual.differencesFrom(expected)).isEmpty();
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

  private void assertUpToDate(boolean expected, Change.Id id) throws Exception {
    try (Repository repo = repoManager.openMetadataRepository(project)) {
      Change c = unwrapDb().changes().get(id);
      assertThat(c).isNotNull();
      assertThat(c.getNoteDbState()).isNotNull();
      assertThat(NoteDbChangeState.parse(c).isChangeUpToDate(repo))
          .isEqualTo(expected);
    }
  }

  private ObjectId getMetaRef(Project.NameKey p, String name) throws Exception {
    try (Repository repo = repoManager.openMetadataRepository(p)) {
      Ref ref = repo.exactRef(name);
      return ref != null ? ref.getObjectId() : null;
    }
  }

  private ReviewDb unwrapDb() {
    ReviewDb db = dbProvider.get();
    if (db instanceof DisabledChangesReviewDbWrapper) {
      db = ((DisabledChangesReviewDbWrapper) db).unsafeGetDelegate();
    }
    return db;
  }
}
