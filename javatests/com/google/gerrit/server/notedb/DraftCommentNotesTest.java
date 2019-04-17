package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.util.time.TimeUtil;
import org.junit.Test;

public class DraftCommentNotesTest extends AbstractChangeNotesTest {

  @Test
  public void createAndPublishCommentInOneAction_runsDraftOperationAsynchronously()
      throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    update.setPatchSetId(c.currentPatchSetId());
    update.putComment(Status.PUBLISHED, comment(c.currentPatchSetId()));
    update.commit();

    assertThat(newNotes(c).getDraftComments(otherUserId)).isEmpty();
    assertableFanOutExecutor.assertInteractions(1);
  }

  @Test
  public void createAndPublishComment_runsPublishDraftOperationAsynchronously() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);

    update.setPatchSetId(c.currentPatchSetId());
    update.putComment(Status.DRAFT, comment(c.currentPatchSetId()));
    update.commit();
    assertThat(newNotes(c).getDraftComments(otherUserId)).hasSize(1);
    assertableFanOutExecutor.assertInteractions(0);

    update = newUpdate(c, otherUser);
    update.putComment(Status.PUBLISHED, comment(c.currentPatchSetId()));
    update.commit();

    assertThat(newNotes(c).getDraftComments(otherUserId)).isEmpty();
    assertableFanOutExecutor.assertInteractions(1);
  }

  @Test
  public void createAndDeleteDraftComment_runsDraftOperationSynchronously() throws Exception {
    Change c = newChange();

    ChangeUpdate update = newUpdate(c, otherUser);
    update.setPatchSetId(c.currentPatchSetId());
    update.putComment(Status.DRAFT, comment(c.currentPatchSetId()));
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).hasSize(1);
    assertableFanOutExecutor.assertInteractions(0);

    update = newUpdate(c, otherUser);
    update.setPatchSetId(c.currentPatchSetId());
    update.deleteComment(comment(c.currentPatchSetId()));
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).isEmpty();
    assertableFanOutExecutor.assertInteractions(0);
  }

  private Comment comment(PatchSet.Id psId) {
    return newComment(
        psId,
        "filename",
        "uuid",
        null,
        0,
        otherUser,
        null,
        TimeUtil.nowTs(),
        "comment",
        (short) 0,
        "abcd1234abcd1234abcd1234abcd1234abcd1234",
        false);
  }
}
