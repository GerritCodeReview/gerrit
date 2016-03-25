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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.testutil.NoteDbChecker;
import com.google.gerrit.testutil.NoteDbMode;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class ChangeRebuilderIT extends AbstractDaemonTest {
  @Inject
  private NoteDbChecker checker;

  @Inject
  private AllUsersName allUsers;

  @Before
  public void setUp() {
    assume().that(NoteDbMode.readWrite()).isFalse();
    notesMigration.setAllEnabled(false);
  }

  @Test
  public void changeFields() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    gApi.changes().id(id.get()).topic(name("a-topic"));
    checker.checkChanges(id);
  }

  @Test
  public void patchSets() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    r = amendChange(r.getChangeId());
    checker.checkChanges(id);
  }

  @Test
  public void noteDbChangeState() throws Exception {
    notesMigration.setAllEnabled(true);
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();

    ObjectId changeMetaId = getMetaRef(
        project, ChangeNoteUtil.changeRefName(id));
    assertThat(db.changes().get(id).getNoteDbState()).isEqualTo(
        changeMetaId.name());

    DraftInput in = new DraftInput();
    in.line = 1;
    in.message = "comment by user";
    in.path = PushOneCommit.FILE_NAME;
    setApiUser(user);
    gApi.changes().id(id.get()).current().createDraft(in);

    ObjectId userDraftsId = getMetaRef(
        allUsers, RefNames.refsDraftComments(user.getId(), id));
    assertThat(db.changes().get(id).getNoteDbState()).isEqualTo(
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
    assertThat(db.changes().get(id).getNoteDbState()).isEqualTo(
        changeMetaId.name()
        + "," + admin.getId() + "=" + adminDraftsId.name()
        + "," + user.getId() + "=" + userDraftsId.name());

    in.message = "revised comment by admin";
    gApi.changes().id(id.get()).current().createDraft(in);

    adminDraftsId = getMetaRef(
        allUsers, RefNames.refsDraftComments(admin.getId(), id));
    assertThat(db.changes().get(id).getNoteDbState()).isEqualTo(
        changeMetaId.name()
        + "," + admin.getId() + "=" + adminDraftsId.name()
        + "," + user.getId() + "=" + userDraftsId.name());
  }

  private ObjectId getMetaRef(Project.NameKey p, String name) throws Exception {
    try (Repository repo = repoManager.openMetadataRepository(p)) {
      Ref ref = repo.exactRef(name);
      return ref != null ? ref.getObjectId() : null;
    }
  }
}
