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
import static com.google.gerrit.common.TimeUtil.roundToSecond;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeRebuilder;
import com.google.inject.Inject;

import org.junit.Before;
import org.junit.Test;

import java.util.Map;

public class ChangeRebuilderIT extends AbstractDaemonTest {
  @Inject
  private ChangeRebuilder rebuilder;

  @Before
  public void setUp() {
    notesMigration.setAllEnabled(false);
  }

  @Test
  public void changeFields() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    gApi.changes().id(id.get()).topic(name("a-topic"));
    Change old = db.changes().get(id);

    rebuild(id);

    assertChangeEqual(old, notesFactory.create(db, project, id).getChange());
  }

  @Test
  public void patchSets() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getPatchSetId().getParentKey();
    r = amendChange(r.getChangeId());

    PatchSet ps1 = db.patchSets().get(new PatchSet.Id(id, 1));
    PatchSet ps2 = db.patchSets().get(new PatchSet.Id(id, 2));

    rebuild(id);

    ChangeNotes notes = notesFactory.create(db, project, id);
    Map<PatchSet.Id, PatchSet> patchSets = notes.getPatchSets();
    assertThat(patchSets.keySet()).containsExactly(ps1.getId(), ps2.getId())
        .inOrder();

    assertPatchSetEqual(ps1, patchSets.get(ps1.getId()));
    assertPatchSetEqual(ps2, patchSets.get(ps2.getId()));
  }

  private void rebuild(Change.Id... changeIds) throws Exception {
    notesMigration.setWriteChanges(true);
    for (Change.Id id : changeIds) {
      rebuilder.rebuild(db, id);
    }
    notesMigration.setReadChanges(true);
  }

  private static void assertChangeEqual(Change expectedReviewDb,
      Change actualNoteDb) {
    assertThat(actualNoteDb.getId()).isEqualTo(expectedReviewDb.getId());
    assertThat(actualNoteDb.getKey()).isEqualTo(expectedReviewDb.getKey());

    // TODO(dborowitz): actualNoteDb's timestamps should come from notedb, currently
    // they're read from reviewdb.
    assertThat(roundToSecond(actualNoteDb.getCreatedOn()))
        .isEqualTo(roundToSecond(expectedReviewDb.getCreatedOn()));
    assertThat(roundToSecond(actualNoteDb.getLastUpdatedOn()))
        .isEqualTo(roundToSecond(expectedReviewDb.getLastUpdatedOn()));
    assertThat(actualNoteDb.getOwner()).isEqualTo(expectedReviewDb.getOwner());
    assertThat(actualNoteDb.getDest()).isEqualTo(expectedReviewDb.getDest());
    assertThat(actualNoteDb.getStatus())
        .isEqualTo(expectedReviewDb.getStatus());
    assertThat(actualNoteDb.currentPatchSetId())
        .isEqualTo(expectedReviewDb.currentPatchSetId());
    assertThat(actualNoteDb.getSubject())
        .isEqualTo(expectedReviewDb.getSubject());
    assertThat(actualNoteDb.getTopic()).isEqualTo(expectedReviewDb.getTopic());
    assertThat(actualNoteDb.getOriginalSubject())
        .isEqualTo(expectedReviewDb.getOriginalSubject());
    assertThat(actualNoteDb.getSubmissionId())
        .isEqualTo(expectedReviewDb.getSubmissionId());
  }

  private static void assertPatchSetEqual(PatchSet expectedReviewDb,
      PatchSet actualNoteDb) {
    assertThat(actualNoteDb.getId()).isEqualTo(expectedReviewDb.getId());
    assertThat(actualNoteDb.getRevision())
        .isEqualTo(expectedReviewDb.getRevision());
    assertThat(actualNoteDb.getUploader())
        .isEqualTo(expectedReviewDb.getUploader());
    assertThat(actualNoteDb.getCreatedOn())
        .isEqualTo(roundToSecond(expectedReviewDb.getCreatedOn()));
    assertThat(actualNoteDb.isDraft()).isEqualTo(expectedReviewDb.isDraft());
    assertThat(actualNoteDb.getGroups())
        .isEqualTo(expectedReviewDb.getGroups());
    assertThat(actualNoteDb.getPushCertificate())
        .isEqualTo(expectedReviewDb.getPushCertificate());
  }
}
