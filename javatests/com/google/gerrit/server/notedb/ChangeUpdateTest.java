package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.CommentRange;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.server.util.time.TimeUtil;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class ChangeUpdateTest extends AbstractChangeNotesTest {

  @Test
  public void bypassMaxUpdatesShouldBeTrueWhenChangingAttentionSetOnly() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    // Add to attention set
    AttentionSetUpdate attentionSetUpdate =
        AttentionSetUpdate.createForWrite(
            otherUser.getAccountId(), AttentionSetUpdate.Operation.ADD, "test");
    update.addToPlannedAttentionSetUpdates(ImmutableSet.of(attentionSetUpdate));

    update.commit();

    assertThat(update.bypassMaxUpdates()).isTrue();
  }

  @Test
  public void bypassMaxUpdatesShouldBeTrueWhenClosingChange() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    update.setStatus(Change.Status.ABANDONED);

    update.commit();

    assertThat(update.bypassMaxUpdates()).isTrue();
  }

  @Test
  public void bypassMaxUpdatesShouldBeFalseWhenNotAbandoningChangeAndNotChangingAttentionSetOnly()
      throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    update.commit();

    assertThat(update.bypassMaxUpdates()).isFalse();
  }

  @Test
  public void bypassMaxUpdatesShouldBeFalseWhenCommentsAndChangesToAttentionSetCoexist()
      throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    // Add to attention set
    AttentionSetUpdate attentionSetUpdate =
        AttentionSetUpdate.createForWrite(
            otherUser.getAccountId(), AttentionSetUpdate.Operation.ADD, "test");
    update.addToPlannedAttentionSetUpdates(ImmutableSet.of(attentionSetUpdate));

    // Add a comment
    RevCommit commit = tr.commit().message("PS2").create();
    update.putComment(
        HumanComment.Status.PUBLISHED,
        newComment(
            c.currentPatchSetId(),
            "a.txt",
            "uuid1",
            new CommentRange(1, 2, 3, 4),
            1,
            changeOwner,
            null,
            TimeUtil.nowTs(),
            "Comment",
            (short) 1,
            commit,
            false));
    update.commit();

    assertThat(update.bypassMaxUpdates()).isFalse();
  }
}
