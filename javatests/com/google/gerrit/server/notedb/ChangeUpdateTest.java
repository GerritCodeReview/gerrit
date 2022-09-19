// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.CommentRange;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.util.time.TimeUtil;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class ChangeUpdateTest extends AbstractChangeNotesTest {

  @Test
  public void bypassMaxUpdatesShouldBeTrueWhenChangingAttentionSetOnly() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    addToAttentionSet(update);
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

    addToAttentionSet(update);
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
            TimeUtil.now(),
            "Comment",
            (short) 1,
            commit,
            false));
    update.commit();

    assertThat(update.bypassMaxUpdates()).isFalse();
  }

  @Test
  public void bypassMaxUpdatesShouldBeFalseWhenReviewersAndChangesToAttentionSetCoexist()
      throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    addToAttentionSet(update);
    update.putReviewer(otherUserId, ReviewerStateInternal.REVIEWER);
    update.commit();

    assertThat(update.bypassMaxUpdates()).isFalse();
  }

  @Test
  public void bypassMaxUpdatesShouldBeFalseWhenReviewersByEmailAndChangesToAttentionSetCoexist()
      throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    addToAttentionSet(update);
    update.putReviewerByEmail(Address.create("anyEmail@mail.com"), ReviewerStateInternal.REVIEWER);
    update.commit();

    assertThat(update.bypassMaxUpdates()).isFalse();
  }

  @Test
  public void bypassMaxUpdatesShouldBeFalseWhenWIPAndChangesToAttentionSetCoexist()
      throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    addToAttentionSet(update);
    update.setWorkInProgress(true);
    update.commit();

    assertThat(update.bypassMaxUpdates()).isFalse();
  }

  @Test
  public void bypassMaxUpdatesShouldBeFalseWhenNonWIPAndChangesToAttentionSetCoexist()
      throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    addToAttentionSet(update);
    update.setWorkInProgress(false);
    update.commit();

    assertThat(update.bypassMaxUpdates()).isFalse();
  }

  @Test
  public void bypassMaxUpdatesShouldBeTrueWhenAbandoningAndChangesToAttentionSetCoexist()
      throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    update.setStatus(Change.Status.ABANDONED);
    addToAttentionSet(update);
    update.commit();

    assertThat(update.bypassMaxUpdates()).isTrue();
  }

  @Test
  public void bypassMaxUpdatesShouldBeFalseWhenVotingAndChangesToAttentionSetCoexist()
      throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);

    update.putApproval("Code-Review", (short) 1);
    addToAttentionSet(update);
    update.commit();

    assertThat(update.bypassMaxUpdates()).isFalse();
  }

  @Test
  public void bypassMaxUpdatesShouldBeFalseWhenDeletingVotesAndChangesToAttentionSetCoexist()
      throws Exception {
    Change c = newChange();
    ChangeUpdate updateWithVote = newUpdate(c, changeOwner);
    updateWithVote.putApproval("Code-Review", (short) 1);
    updateWithVote.commit();

    updateWithVote.removeApproval("Code-Review");
    addToAttentionSet(updateWithVote);

    assertThat(updateWithVote.bypassMaxUpdates()).isFalse();
  }

  @Test
  public void commitChangeUpdateWithoutTouchingAttentionSet() throws Exception {
    Change c = newChangeWithEmptyAttentionSet();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) 1);
    update.commit();

    assertThat(update.getAttentionSetUpdates()).isEmpty();
  }

  @Test
  public void nonCommittedChangeUpdateReturnsEmptyAttentionSetUpdates() throws Exception {
    Change c = newChangeWithEmptyAttentionSet();

    ChangeUpdate update = newUpdate(c, changeOwner);
    addToAttentionSet(update, otherUser);

    assertThat(update.getAttentionSetUpdates()).isEmpty();
  }

  @Test
  public void committedChangeUpdateReturnsAttentionSetUpdates() throws Exception {
    Change c = newChangeWithEmptyAttentionSet();
    ChangeUpdate update = newUpdate(c, changeOwner);
    AttentionSetUpdate attentionSetUpdate = addToAttentionSet(update, otherUser);
    update.commit();

    assertThat(update.getAttentionSetUpdates()).containsExactly(attentionSetUpdate);
  }

  @Test
  public void committedChangeUpdateReturnsMultipleAttentionSetUpdates() throws Exception {
    Change c = newChangeWithEmptyAttentionSet();
    ChangeUpdate update = newUpdate(c, changeOwner);
    AttentionSetUpdate attentionSetUpdate1 = addToAttentionSet(update, otherUser);
    AttentionSetUpdate attentionSetUpdate2 = addToAttentionSet(update, changeOwner);
    update.commit();

    assertThat(update.getAttentionSetUpdates())
        .containsExactly(attentionSetUpdate1, attentionSetUpdate2);
  }

  @Test
  public void changeUpdateDoesntReturnAttentionSetUpdateForUserAlreadyAddedInAttentionSet()
      throws Exception {
    Change c = newChangeWithEmptyAttentionSet();
    ChangeUpdate update1 = newUpdate(c, changeOwner);
    addToAttentionSet(update1, otherUser);
    update1.commit();

    ChangeUpdate update2 = newUpdate(c, changeOwner);
    addToAttentionSet(update2, otherUser);
    update2.commit();

    assertThat(update2.getAttentionSetUpdates()).isEmpty();
  }

  /**
   * Creates a change with an empty attention set
   *
   * <p>Method ensures that changeOwner and otherUser can be added to the attention set later. (only
   * users active on the change can be added to the attention set - see {@link
   * ChangeUpdate#isActiveOnChange})
   */
  private Change newChangeWithEmptyAttentionSet() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(otherUser.getAccountId(), ReviewerStateInternal.CC);
    update.commit();
    return c;
  }

  @CanIgnoreReturnValue
  private AttentionSetUpdate addToAttentionSet(ChangeUpdate update) {
    return addToAttentionSet(update, otherUser);
  }

  @CanIgnoreReturnValue
  private AttentionSetUpdate addToAttentionSet(ChangeUpdate update, IdentifiedUser user) {
    AttentionSetUpdate attentionSetUpdate =
        AttentionSetUpdate.createForWrite(
            user.getAccountId(), AttentionSetUpdate.Operation.ADD, "test");
    update.addToPlannedAttentionSetUpdates(ImmutableSet.of(attentionSetUpdate));
    return attentionSetUpdate;
  }
}
