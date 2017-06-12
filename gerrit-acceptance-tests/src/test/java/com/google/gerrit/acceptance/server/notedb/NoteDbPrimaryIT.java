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
import static com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage.REVIEW_DB;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.RepoRefCache;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NoteDbChangeState;
import com.google.gerrit.testutil.NoteDbMode;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class NoteDbPrimaryIT extends AbstractDaemonTest {
  @Inject private AllUsersName allUsers;

  @Before
  public void setUp() throws Exception {
    assume().that(NoteDbMode.get()).isEqualTo(NoteDbMode.READ_WRITE);
    db = ReviewDbUtil.unwrapDb(db);
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
