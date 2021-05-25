package com.google.gerrit.server.notedb;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class ChangeUpdateTest extends AbstractChangeNotesTest {

  @Test
  public void bypassMaxUpdatesShouldBeTrueWhenChangingAttentionSetOnly() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    AttentionSetUpdate attentionSetUpdate =
        AttentionSetUpdate.createForWrite(otherUser.getAccountId(), AttentionSetUpdate.Operation.ADD, "test");
    update.addToPlannedAttentionSetUpdates(
        ImmutableSet.of(attentionSetUpdate));
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
  public void bypassMaxUpdatesShouldBeFalseWhenNotAbandoningChangeAndNotChangingAttentionSetOnly() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.commit();

    assertThat(update.bypassMaxUpdates()).isTrue();
  }

  @Test
  public void bypassMaxUpdatesShouldBeFalseWhenCommentsAndChangesToAttentionSetCoexist() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    AttentionSetUpdate attentionSetUpdate =
        AttentionSetUpdate.createForWrite(otherUser.getAccountId(), AttentionSetUpdate.Operation.ADD, "test");
    update.addToPlannedAttentionSetUpdates(
        ImmutableSet.of(attentionSetUpdate));
    update.setChangeMessage("Random comment");
    update.commit();

    assertThat(update.bypassMaxUpdates()).isFalse();
  }
}
